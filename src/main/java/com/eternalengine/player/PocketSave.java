package com.eternalengine.player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * <b>Saves per timeline and universe: people, purses and notes.</b> A10's remaining half.
 *
 * <h2>Why this class exists</h2>
 *
 * Step 51 saved the WORLD's flags as the difference from a branch's parent. That is A10's "world state" half.
 * The row also names <b>people, purses and notes</b>, and the survey found none of them saved per timeline or
 * universe: a purse lives in a trade screen, a family is known once a note exists, and neither is keyed by
 * where and when it stands. A branch whose world restores but whose people do not is a half-restored world.
 *
 * <h2>The same discipline as step 51, one layer down</h2>
 *
 * <p>A pocket is keyed by <b>(universe, branch)</b> and is saved as the difference from its parent pocket,
 * because two universes may hold the same branch name and mean different worlds. The root saves whole; a child
 * saves only what it changed. Restore walks the chain root-down and <b>refuses a missing parent or a loop
 * rather than guessing</b>, and {@link #diff} checks the cheap copy key by key against a full one, naming the
 * pocket that introduced each disagreement.
 *
 * <h2>The three payloads have three rules</h2>
 *
 * <ol>
 *   <li><b>People</b> are recorded once per pocket and read without doubting: the world may end a life (a
 *       removal is a fact a branch may carry) and may not rewrite a birth, the same rule as
 *       {@link WhoYouAre}.</li>
 *   <li><b>Purses</b> cannot go below zero. A spend that would is <b>refused</b>, never clamped: the coins a
 *       player does not have must not silently appear.</li>
 *   <li><b>Notes</b> are written once. Appending is the only edit; changing what a note says is refused,
 *       because a note is the one thing in the mod a player owns the words of.</li>
 * </ol>
 */
public final class PocketSave {

    /** <b>Where a pocket stands.</b> Universe first, because one branch name can be two worlds. */
    public record Key(String universe, String branch) implements Comparable<Key> {

        public Key {
            if (universe == null || universe.trim().isEmpty() || branch == null || branch.trim().isEmpty()) {
                throw new IllegalArgumentException("a pocket key is a universe and a branch, both named");
            }
            universe = universe.trim();
            branch = branch.trim();
        }

        public String id() {
            return universe + "/" + branch;
        }

        @Override
        public int compareTo(Key o) {
            return id().compareToIgnoreCase(o.id());
        }

        @Override
        public String toString() {
            return id();
        }
    }

    /**
     * <b>What a pocket holds.</b> Immutable: every change returns a new pocket, so a save taken before a spend
     * still holds what it held.
     */
    public static final class Pocket {
        private final Map<String, String> people;
        private final Map<String, Integer> purses;
        private final Map<String, List<String>> notes;

        private Pocket(Map<String, String> people, Map<String, Integer> purses,
                       Map<String, List<String>> notes) {
            this.people = people;
            this.purses = purses;
            this.notes = notes;
        }

        /** Build a pocket from whatever the caller holds. Names are trimmed; lists are copied. */
        public static Pocket of(Map<String, String> people, Map<String, Integer> purses,
                                Map<String, List<String>> notes) {
            Map<String, String> p = new TreeMap<>();
            if (people != null) {
                for (Map.Entry<String, String> e : people.entrySet()) {
                    if (e.getKey() != null && !e.getKey().trim().isEmpty()) {
                        p.put(e.getKey().trim(), e.getValue() == null ? "" : e.getValue());
                    }
                }
            }
            Map<String, Integer> s = new TreeMap<>();
            if (purses != null) {
                for (Map.Entry<String, Integer> e : purses.entrySet()) {
                    if (e.getKey() != null && !e.getKey().trim().isEmpty()) {
                        s.put(e.getKey().trim(), e.getValue() == null ? 0 : e.getValue());
                    }
                }
            }
            Map<String, List<String>> n = new TreeMap<>();
            if (notes != null) {
                for (Map.Entry<String, List<String>> e : notes.entrySet()) {
                    if (e.getKey() != null && !e.getKey().trim().isEmpty()) {
                        List<String> copy = new ArrayList<>();
                        if (e.getValue() != null) {
                            for (String line : e.getValue()) {
                                if (line != null) {
                                    copy.add(line);
                                }
                            }
                        }
                        n.put(e.getKey().trim(), copy);
                    }
                }
            }
            return new Pocket(ordered(p), ordered(s), notesCopy(n));
        }

        public static Pocket empty() {
            return of(null, null, null);
        }

        public Map<String, String> people() {
            return people;
        }

        public Map<String, Integer> purses() {
            return purses;
        }

        public Map<String, List<String>> notes() {
            return notes;
        }

        /** Record or re-record what the world knows of a person. This is a state line, not a birth. */
        public Pocket putPerson(String name, String state) {
            if (name == null || name.trim().isEmpty()) {
                return this;
            }
            Map<String, String> p = new TreeMap<>(people);
            p.put(name.trim(), state == null ? "" : state);
            return of(p, purses, notes);
        }

        /** The world ends a life, or stops holding a person. A fact a branch may carry. */
        public Pocket removePerson(String name) {
            if (name == null) {
                return this;
            }
            Map<String, String> p = new TreeMap<>(people);
            p.keySet().removeIf(k -> k.equalsIgnoreCase(name.trim()));
            return of(p, purses, notes);
        }

        /** Coins a player has come by. Counts are whole and may be negative only below, in spend. */
        public Pocket earn(String who, int coins) {
            if (who == null || who.trim().isEmpty() || coins == 0) {
                return this;
            }
            Map<String, Integer> s = new TreeMap<>(purses);
            s.merge(who.trim(), coins, Integer::sum);
            return of(people, s, notes);
        }

        /**
         * <b>Spend coins.</b> A purse that would go below zero is <b>refused</b>, not clamped: the coins a
         * player does not have must not silently appear.
         */
        public Spent spend(String who, int coins) {
            if (who == null || who.trim().isEmpty()) {
                return new Spent(this, "there is nobody to spend anything");
            }
            if (coins <= 0) {
                return new Spent(this, "spending " + coins + " is not spending");
            }
            String key = find(who.trim());
            int have = key == null ? 0 : purses.get(key);
            if (have < coins) {
                return new Spent(this, who.trim() + " has " + have + " and cannot spend " + coins);
            }
            Map<String, Integer> s = new TreeMap<>(purses);
            if (have == coins) {
                s.remove(key);
            } else {
                s.put(key, have - coins);
            }
            return new Spent(of(people, s, notes), "");
        }

        /** A note is written once. Appending is the only edit this class knows. */
        public Pocket writeNote(String who, String note) {
            if (who == null || who.trim().isEmpty() || note == null || note.trim().isEmpty()) {
                return this;
            }
            Map<String, List<String>> n = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : notes.entrySet()) {
                n.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            String key = findIn(n, who.trim());
            if (key == null) {
                key = who.trim();
                n.put(key, new ArrayList<>());
            }
            n.get(key).add(note.trim());
            return of(people, purses, n);
        }

        /** Changing what a note says is always refused: a note is its writer's own words. */
        public Refused editNote(String who, int which, String to) {
            return new Refused("a note is written once; note " + which + " of "
                    + (who == null ? "nobody" : who.trim()) + " cannot be rewritten to \"" + to + "\"");
        }

        public int coinsOf(String who) {
            String key = find(who == null ? "" : who.trim());
            return key == null ? 0 : purses.get(key);
        }

        public List<String> notesOf(String who) {
            String key = findIn(notes, who == null ? "" : who.trim());
            return key == null ? List.of() : notes.get(key);
        }

        public String personLine(String name) {
            for (Map.Entry<String, String> e : people.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name == null ? "" : name.trim())) {
                    return e.getKey() + " · " + e.getValue();
                }
            }
            return null;
        }

        /** The three sections, named with their sizes, for a screen or a probe. */
        public String line() {
            return people.size() + " people, " + purses.size() + " purses, " + countNotes() + " notes";
        }

        int countNotes() {
            int n = 0;
            for (List<String> l : notes.values()) {
                n += l.size();
            }
            return n;
        }

        /** A rough cost in bytes, the same rough count step 51 uses, for comparing a delta to a whole. */
        public int bytes() {
            int b = 0;
            for (Map.Entry<String, String> e : people.entrySet()) {
                b += e.getKey().length() + e.getValue().length() + 2;
            }
            for (Map.Entry<String, Integer> e : purses.entrySet()) {
                b += e.getKey().length() + 8;
            }
            for (Map.Entry<String, List<String>> e : notes.entrySet()) {
                for (String l : e.getValue()) {
                    b += e.getKey().length() + l.length() + 2;
                }
            }
            return b;
        }

        private String find(String name) {
            for (String k : purses.keySet()) {
                if (k.equalsIgnoreCase(name)) {
                    return k;
                }
            }
            return null;
        }

        private static String findIn(Map<String, List<String>> m, String name) {
            for (String k : m.keySet()) {
                if (k.equalsIgnoreCase(name)) {
                    return k;
                }
            }
            return null;
        }

        private static Map<String, List<String>> notesCopy(Map<String, List<String>> n) {
            Map<String, List<String>> out = new TreeMap<>();
            for (Map.Entry<String, List<String>> e : n.entrySet()) {
                out.put(e.getKey(), List.copyOf(e.getValue()));
            }
            return ordered(out);
        }

        /** Sorted and unmodifiable: a pocket reads the same way every time. */
        private static <T> Map<String, T> ordered(Map<String, T> m) {
            return java.util.Collections.unmodifiableMap(new TreeMap<>(m));
        }
    }

    /** <b>A spend's outcome.</b> A pocket, and either no reason (it happened) or one reason (it did not). */
    public record Spent(Pocket pocket, String refused) {
        public boolean ok() {
            return refused.isEmpty();
        }
    }

    /** <b>A refusal, said once.</b> */
    public record Refused(String why) {
    }

    /**
     * <b>One pocket's difference from its parent.</b> A parent of null is the root, saved whole. Everything
     * else is what this pocket changed, took away, or wrote down.
     */
    public record Delta(Key key, Key parent,
                        Map<String, String> peopleChanged, Set<String> peopleGone,
                        Map<String, Integer> purseChanged, Set<String> purseGone,
                        Map<String, List<String>> notesAdded, Set<String> notesGone,
                        String log) {

        /** What this delta does to a parent's pocket to make this one. */
        public Pocket applyTo(Pocket parent) {
            Pocket p = parent == null ? Pocket.empty() : parent;
            for (String gone : peopleGone) {
                p = p.removePerson(gone);
            }
            for (Map.Entry<String, String> e : peopleChanged.entrySet()) {
                p = p.putPerson(e.getKey(), e.getValue());
            }
            for (String gone : purseGone) {
                Map<String, Integer> s = new TreeMap<>(p.purses());
                s.keySet().removeIf(k -> k.equalsIgnoreCase(gone));
                p = Pocket.of(p.people(), s, p.notes());
            }
            for (Map.Entry<String, Integer> e : purseChanged.entrySet()) {
                Map<String, Integer> s = new TreeMap<>(p.purses());
                String key = null;
                for (String k : s.keySet()) {
                    if (k.equalsIgnoreCase(e.getKey())) {
                        key = k;
                    }
                }
                if (key != null) {
                    s.remove(key);
                }
                s.put(e.getKey(), e.getValue());
                p = Pocket.of(p.people(), s, p.notes());
            }
            for (String gone : notesGone) {
                Map<String, List<String>> n = new LinkedHashMap<>(p.notes());
                n.keySet().removeIf(k -> k.equalsIgnoreCase(gone));
                p = Pocket.of(p.people(), p.purses(), n);
            }
            for (Map.Entry<String, List<String>> e : notesAdded.entrySet()) {
                // Append what is new; a delta written before this rule could still hold a whole list, and the
                // reader must not lose notes to it, so a delta longer than a note is a list that replaces.
                Map<String, List<String>> n = new LinkedHashMap<>(p.notes());
                String key = null;
                for (String k : n.keySet()) {
                    if (k.equalsIgnoreCase(e.getKey())) {
                        key = k;
                    }
                }
                List<String> had = key == null ? List.of() : n.get(key);
                List<String> out = new ArrayList<>(had);
                if (e.getValue().size() == 1 || had.isEmpty()) {
                    out.addAll(e.getValue());
                } else if (isSuffix(had, e.getValue())) {
                    out.addAll(e.getValue().subList(0, e.getValue().size() - suffixLen(had, e.getValue())));
                } else {
                    out = new ArrayList<>(e.getValue());
                }
                n.remove(key);
                n.put(e.getKey(), out);
                p = Pocket.of(p.people(), p.purses(), n);
            }
            return p;
        }

        /** The names this delta touches, in one list: what a reader must check first. */
        public List<String> touched() {
            List<String> out = new ArrayList<>();
            out.addAll(peopleChanged.keySet());
            out.addAll(peopleGone);
            out.addAll(purseChanged.keySet());
            out.addAll(purseGone);
            out.addAll(notesAdded.keySet());
            out.addAll(notesGone);
            return out;
        }

        /** A rough cost in bytes of what this delta holds. */
        public int bytes() {
            int b = key.id().length() + log.length();
            for (Map.Entry<String, String> e : peopleChanged.entrySet()) {
                b += e.getKey().length() + e.getValue().length() + 2;
            }
            for (Map.Entry<String, Integer> e : purseChanged.entrySet()) {
                b += e.getKey().length() + 8;
            }
            for (Map.Entry<String, List<String>> e : notesAdded.entrySet()) {
                b += e.getKey().length();
                for (String l : e.getValue()) {
                    b += l.length() + 2;
                }
            }
            b += (peopleGone.size() + purseGone.size() + notesGone.size()) * 8;
            return b;
        }
    }

    /** <b>A restore's outcome.</b> The pocket, the chain applied to get it, and every refusal. */
    public record Restored(Pocket pocket, List<Key> applied, List<String> refused) {
        public boolean ok() {
            return pocket != null;
        }
    }

    /** <b>One disagreement between a cheap save and a full one.</b> */
    public record Difference(String section, String name, String expected, String actual, Key key) {
        public String line() {
            return section + " " + name + ": the cheap save says \"" + actual + "\", the full one says \""
                    + expected + "\" (" + key + ")";
        }
    }

    private final Map<Key, Delta> deltas = new LinkedHashMap<>();

    private PocketSave() {
    }

    public static PocketSave empty() {
        return new PocketSave();
    }

    /**
     * <b>The reader.</b> Takes deltas as they stand in a file, including broken ones, because a save read
     * from disk is not checked by the builder and this class must still refuse to restore it.
     */
    public static PocketSave of(Delta... into) {
        PocketSave s = new PocketSave();
        if (into != null) {
            for (Delta d : into) {
                if (d != null) {
                    s.deltas.put(d.key(), d);
                }
            }
        }
        return s;
    }

    /** The root pocket, saved whole. A second root under one key is refused. */
    public PocketSave root(Key key, Pocket whole, String log) {
        if (deltas.containsKey(key)) {
            throw new IllegalArgumentException("a pocket for " + key + " is already saved");
        }
        if (whole == null) {
            throw new IllegalArgumentException("a root pocket must hold something: use Pocket.empty() for "
                    + "nothing at all");
        }
        deltas.put(key, new Delta(key, null, Map.copyOf(whole.people()),
                Set.of(), Map.copyOf(whole.purses()), Set.of(), notesMap(whole), Set.of(),
                log == null ? "" : log));
        return this;
    }

    /**
     * <b>Save a pocket as the difference from its parent.</b> The parent must already be saved (a child with
     * no parent is a broken chain, and this refuses to make one), and the key must be new.
     */
    public PocketSave branch(Key parent, Key here, Pocket whole, String log) {
        if (parent != null && parent.equals(here)) {
            throw new IllegalArgumentException("a pocket cannot be its own parent");
        }
        if (deltas.containsKey(here)) {
            throw new IllegalArgumentException("a pocket for " + here + " is already saved");
        }
        if (parent != null && !deltas.containsKey(parent)) {
            throw new IllegalArgumentException("there is no pocket for the parent " + parent
                    + "; a child with no parent is a broken chain");
        }
        Restored parentView = chain(parent);
        Pocket base = parentView.ok() ? parentView.pocket() : Pocket.empty();
        Map<String, String> peopleChanged = new TreeMap<>();
        Set<String> peopleGone = new TreeSet<>();
        for (Map.Entry<String, String> e : whole.people().entrySet()) {
            String before = find(base.people(), e.getKey());
            if (before == null || !base.people().get(before).equals(e.getValue())) {
                peopleChanged.put(e.getKey(), e.getValue());
            }
        }
        for (String k : base.people().keySet()) {
            if (find(whole.people(), k) == null) {
                peopleGone.add(k);
            }
        }
        Map<String, Integer> purseChanged = new TreeMap<>();
        Set<String> purseGone = new TreeSet<>();
        for (Map.Entry<String, Integer> e : whole.purses().entrySet()) {
            String before = find(base.purses(), e.getKey());
            if (before == null || !base.purses().get(before).equals(e.getValue())) {
                purseChanged.put(e.getKey(), e.getValue());
            }
        }
        for (String k : base.purses().keySet()) {
            if (find(whole.purses(), k) == null) {
                purseGone.add(k);
            }
        }
        Map<String, List<String>> notesAdded = new TreeMap<>();
        Set<String> notesGone = new TreeSet<>();
        // Notes are written once and only ever appended to (their rule, below), so a delta holds the NEW
        // notes of each writer and applies them by appending. A list that is not its parent's list plus
        // something new is stored whole and replaces: it should not happen, and diff() is the safety net.
        for (Map.Entry<String, List<String>> e : whole.notes().entrySet()) {
            String before = find(base.notes(), e.getKey());
            List<String> oldList = before == null ? List.of() : base.notes().get(before);
            if (oldList.equals(e.getValue())) {
                continue;
            }
            int common = 0;
            while (common < oldList.size() && common < e.getValue().size()
                    && oldList.get(common).equals(e.getValue().get(common))) {
                common++;
            }
            if (common == oldList.size()) {
                notesAdded.put(e.getKey(), List.copyOf(e.getValue().subList(common, e.getValue().size())));
            } else {
                notesAdded.put(e.getKey(), List.copyOf(e.getValue()));
            }
        }
        for (String k : base.notes().keySet()) {
            if (find(whole.notes(), k) == null) {
                notesGone.add(k);
            }
        }
        deltas.put(here, new Delta(here, parent, peopleChanged, peopleGone, purseChanged, purseGone,
                notesAdded, notesGone, log == null ? "" : log));
        return this;
    }

    /**
     * <b>Restore a pocket from its chain.</b> Root-down, refusing a missing parent or a loop by name rather
     * than guessing: a half-restored pocket is worse than none.
     */
    public Restored chain(Key key) {
        List<Key> path = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        Key at = key;
        while (at != null) {
            if (path.contains(at)) {
                refused.add("the chain loops at " + at + "; a pocket cannot be its own ancestor");
                return new Restored(null, path, refused);
            }
            Delta d = deltas.get(at);
            if (d == null) {
                refused.add("there is no save for " + at + "; the chain cannot pass through a missing pocket");
                return new Restored(null, path, refused);
            }
            path.add(0, at);
            at = d.parent();
        }
        Pocket p = Pocket.empty();
        for (Key k : path) {
            p = deltas.get(k).applyTo(p);
        }
        return new Restored(p, path, refused);
    }

    /**
     * <b>The pocket whose delta last touched a name.</b> The blame for a disagreement, walked down the chain:
     * the last one to write the name is the one that made it wrong.
     */
    public Key blame(List<Key> chain, String name) {
        Key found = null;
        for (Key k : chain) {
            Delta d = deltas.get(k);
            if (d == null) {
                continue;
            }
            for (String t : d.touched()) {
                if (t.equalsIgnoreCase(name.trim())) {
                    found = k;
                }
            }
        }
        // Nothing ever wrote this name, so the pocket disagrees with the very first save: blame the root.
        return found != null ? found : (chain.isEmpty() ? null : chain.get(0));
    }

    /**
     * <b>Check a cheap save against a full one.</b> Every name of every section, key by key, naming the
     * pocket whose delta introduced each disagreement.
     */
    public List<Difference> diff(Key key, Pocket expected) {
        Restored r = chain(key);
        List<Difference> out = new ArrayList<>();
        if (!r.ok()) {
            out.add(new Difference("(the chain)", key.id(), "a pocket", "nothing: " + r.refused(), key));
            return out;
        }
        Pocket got = r.pocket();
        for (Map.Entry<String, String> e : expected.people().entrySet()) {
            String have = find(got.people(), e.getKey());
            String actual = have == null ? null : got.people().get(have);
            if (actual == null || !actual.equals(e.getValue())) {
                out.add(new Difference("person", e.getKey(), e.getValue(),
                        actual == null ? "no such person" : actual, blame(r.applied(), e.getKey())));
            }
        }
        for (String k : got.people().keySet()) {
            if (find(expected.people(), k) == null) {
                out.add(new Difference("person", k, "no such person", got.people().get(k),
                        blame(r.applied(), k)));
            }
        }
        for (Map.Entry<String, Integer> e : expected.purses().entrySet()) {
            String have = find(got.purses(), e.getKey());
            Integer actual = have == null ? null : got.purses().get(have);
            if (actual == null || !actual.equals(e.getValue())) {
                out.add(new Difference("purse", e.getKey(), String.valueOf(e.getValue()),
                        actual == null ? "no such purse" : String.valueOf(actual),
                        blame(r.applied(), e.getKey())));
            }
        }
        for (String k : got.purses().keySet()) {
            if (find(expected.purses(), k) == null) {
                out.add(new Difference("purse", k, "no such purse", String.valueOf(got.purses().get(k)),
                        blame(r.applied(), k)));
            }
        }
        for (Map.Entry<String, List<String>> e : expected.notes().entrySet()) {
            String have = find(got.notes(), e.getKey());
            List<String> actual = have == null ? null : got.notes().get(have);
            if (actual == null || !actual.equals(e.getValue())) {
                out.add(new Difference("notes", e.getKey(), String.valueOf(e.getValue()),
                        actual == null ? "no such notes" : String.valueOf(actual),
                        blame(r.applied(), e.getKey())));
            }
        }
        for (String k : got.notes().keySet()) {
            if (find(expected.notes(), k) == null) {
                out.add(new Difference("notes", k, "no such notes", String.valueOf(got.notes().get(k)),
                        blame(r.applied(), k)));
            }
        }
        return out;
    }

    /** Every key saved, in save order. */
    public List<Key> keys() {
        return List.copyOf(deltas.keySet());
    }

    public Delta get(Key key) {
        return deltas.get(key);
    }

    /** How much the whole save costs against saving every pocket whole. Step 51's measurement, again. */
    public String cost() {
        int deltaBytes = 0;
        int fullBytes = 0;
        for (Delta d : deltas.values()) {
            deltaBytes += d.bytes();
            Restored r = chain(d.key());
            if (r.ok()) {
                fullBytes += r.pocket().bytes();
            }
        }
        if (fullBytes == 0) {
            return "nothing is saved";
        }
        return deltaBytes + " bytes of deltas against " + fullBytes + " bytes if every pocket were saved "
                + "whole: " + Math.round(100.0 * deltaBytes / fullBytes) + "% of the cost";
    }

    public static String ruleLine() {
        return "A pocket saves the difference from its parent pocket; a purse cannot go below zero; a note is "
                + "written once; and a chain with a missing parent or a loop restores nothing and says why.";
    }

    // ------------------------------------------------------------------ internals

    private static Map<String, List<String>> notesMap(Pocket whole) {
        Map<String, List<String>> out = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : whole.notes().entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return out;
    }

    private static boolean isSuffix(List<String> had, List<String> delta) {
        return suffixLen(had, delta) > 0;
    }

    /** How many of the delta's last lines already stand in the parent's list (for old whole-list deltas). */
    private static int suffixLen(List<String> had, List<String> delta) {
        int n = 0;
        while (n < had.size() && n < delta.size()
                && had.get(had.size() - 1 - n).equals(delta.get(delta.size() - 1 - n))) {
            n++;
        }
        return n;
    }

    private static <T> String find(Map<String, T> m, String name) {
        for (String k : m.keySet()) {
            if (k.equalsIgnoreCase(name)) {
                return k;
            }
        }
        return null;
    }
}
