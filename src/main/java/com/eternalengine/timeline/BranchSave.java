package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * <b>G8: a branch saved as its differences from the branch it came from.</b>
 *
 * <h2>Why a delta and not a copy</h2>
 *
 * <p>The world's mutable state is a map of strings, set from 51 places under 20 prefixes ({@code event.},
 * {@code dynasty.}, {@code climate.}, {@code tech.} and the rest), plus the year and the active branch. Two
 * branches of one world share almost all of it: a fork in 1857 does not change what the technology atlas says, it
 * changes a handful of flags and the pins that stopped happening. Saving every branch whole means writing the
 * same two thousand flags once per branch, and a player who makes twenty branches writes the world twenty-one
 * times.
 *
 * <p>So the <b>root saves everything, once</b>, and every branch after it saves<b> only what it changed, only what
 * it removed, and its own record</b>. Restoring a branch means applying the chain of differences from the root
 * down.
 *
 * <h2>The risk, and the check that answers it</h2>
 *
 * <p>A delta save can be wrong in a way a full save cannot: a missing removal leaves a flag that should be gone,
 * and nothing about the file looks broken. So the one thing this class exists to make possible is
 * {@link #diff}, which compares a restored chain against a <b>full</b> snapshot and names the keys that differ
 * <i>and the branch whose delta introduced each one</i>. A save system that cannot be checked against the thing
 * it is a compression of is a save system that will lose a world quietly.
 *
 * <h2>What is not in here</h2>
 *
 * <p>Block edits. What the player built is {@code buildings/PlayerEdits}, kept per branch with its own chain, and
 * duplicating it here would give two records of the same thing that could disagree. The seam is
 * {@link Snapshot#flags()}: this class never reads a Minecraft class, and {@code WorldStateManager} adapts to it by
 * handing over its flag map, its year and its active branch.
 */
public final class BranchSave {

    /**
     * A whole state at one instant: every flag, the year, and which branch the world was showing.
     *
     * <p>Copied on construction, sorted, and unmodifiable, because the whole failure mode of a save system is an
     * object that changes after it was saved.
     */
    public record Snapshot(Map<String, String> flags, int year, String activeBranch) {

        public Snapshot {
            Map<String, String> copy = new TreeMap<>();
            if (flags != null) {
                for (Map.Entry<String, String> e : flags.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        copy.put(e.getKey(), e.getValue());
                    }
                }
            }
            flags = java.util.Collections.unmodifiableMap(copy);
            activeBranch = activeBranch == null ? "main" : activeBranch;
        }

        public static Snapshot of(Map<String, String> flags, int year, String branch) {
            return new Snapshot(flags, year, branch);
        }

        /** The same flags, without the ones named. Used to build the state a branch would have had. */
        public Snapshot minus(Set<String> keys) {
            Map<String, String> copy = new TreeMap<>(flags);
            copy.keySet().removeAll(keys);
            return new Snapshot(copy, year, activeBranch);
        }

        public int flagCount() {
            return flags.size();
        }

        /** Roughly how many characters this state is, for the "is the delta cheaper" measurement. */
        public long bytes() {
            long n = 0;
            for (Map.Entry<String, String> e : flags.entrySet()) {
                n += e.getKey().length() + e.getValue().length() + 4L;   // key, value, quotes, colon, comma
            }
            return n;
        }
    }

    /**
     * One branch's difference from its parent.
     *
     * @param branchId      the branch this is
     * @param parentId      the branch it was saved against, or null for the root
     * @param divergenceYear the year it split, or the year the root starts
     * @param changed       flags whose value is different from the parent's
     * @param removed       flags the parent had and this branch does not
     * @param suppressedPins pins that do not happen in this branch
     * @param log           the one-line summary the book shows
     */
    public record Delta(String branchId, String parentId, int divergenceYear, Map<String, String> changed,
                        Set<String> removed, List<String> suppressedPins, String log) {

        public Delta {
            changed = java.util.Collections.unmodifiableMap(new TreeMap<>(changed == null ? Map.of() : changed));
            removed = java.util.Collections.unmodifiableSet(new TreeSet<>(removed == null ? Set.of() : removed));
            suppressedPins = List.copyOf(suppressedPins == null ? List.of() : suppressedPins);
            log = log == null ? "" : log;
            if (branchId == null || branchId.isBlank()) {
                throw new IllegalArgumentException("a saved branch needs an id");
            }
        }

        public boolean isRoot() {
            return parentId == null || parentId.isBlank();
        }

        /** How many flags this branch owns, one way or another. */
        public int touched() {
            return changed.size() + removed.size();
        }

        public long bytes() {
            long n = 0;
            for (Map.Entry<String, String> e : changed.entrySet()) {
                n += e.getKey().length() + e.getValue().length() + 4L;
            }
            for (String k : removed) {
                n += k.length() + 12L;    // the key and the word that says it is gone
            }
            n += 64L;                      // the id, the parent, the year, the log line
            for (String p : suppressedPins) {
                n += p.length() + 3L;
            }
            return n;
        }

        /** Apply this difference to the state the parent left behind. */
        public Snapshot applyTo(Snapshot parent) {
            Map<String, String> flags = new TreeMap<>(parent.flags());
            for (String k : removed) {
                flags.remove(k);
            }
            flags.putAll(changed);
            return new Snapshot(flags, divergenceYear, branchId);
        }
    }

    /** What restoring a chain produced, and what it could not do. */
    public record Restored(Snapshot snapshot, List<String> applied, List<String> refused) {

        public Restored {
            applied = List.copyOf(applied == null ? List.of() : applied);
            refused = List.copyOf(refused == null ? List.of() : refused);
        }

        public boolean complete() {
            return refused.isEmpty();
        }

        public String line() {
            if (!complete()) {
                return "Restored " + applied.size() + " branches and stopped: " + String.join("; ", refused);
            }
            return "Restored " + applied.size() + " branch" + (applied.size() == 1 ? "" : "es")
                    + " from the root down, ending at " + snapshot.activeBranch() + " in " + snapshot.year()
                    + " with " + snapshot.flagCount() + " flags.";
        }
    }

    /** One key that a restored chain and a full save disagree about, and where the disagreement came in. */
    public record Difference(String key, String restored, String expected, String introducedBy) {

        public String line() {
            String r = restored == null ? "(absent)" : restored;
            String e = expected == null ? "(absent)" : expected;
            return key + ": restored " + r + ", saved " + e
                    + (introducedBy == null ? " (and no branch in the chain is to blame, so the difference is "
                    + "between the root and the full save)" : " (first appears after the delta for " + introducedBy + ")");
        }
    }

    private final Map<String, Delta> deltas = new LinkedHashMap<>();

    private BranchSave() {
    }

    public static BranchSave empty() {
        return new BranchSave();
    }

    /**
     * A save read back from a file: the deltas in whatever order they were written, which is not necessarily the
     * order they can be applied in. Kept separate from the builder because reading is not writing: the builder
     * refuses a parent it has never seen, and a reader has to accept a file that is broken in that way and then
     * report it at {@link #chain}, where the player can be told.
     */
    public static BranchSave of(Delta... deltas) {
        BranchSave s = new BranchSave();
        for (Delta d : deltas) {
            if (d == null) {
                continue;
            }
            if (s.deltas.containsKey(d.branchId())) {
                throw new IllegalArgumentException("two saves for the same branch: " + d.branchId());
            }
            s.deltas.put(d.branchId(), d);
        }
        return s;
    }

    /** The size of every delta in this save, for the "is this cheaper" measurement. */
    public long deltaBytes() {
        long n = 0;
        for (Delta d : deltas.values()) {
            n += d.bytes();
        }
        return n;
    }

    /**
     * Save the root: everything, once.
     *
     * @param full  the whole state at the moment the world's own history begins
     * @param log   the one-line summary for the book
     */
    public static BranchSave root(Snapshot full, String log) {
        BranchSave s = new BranchSave();
        s.deltas.put(full.activeBranch(), new Delta(full.activeBranch(), null, full.year(), full.flags(),
                Set.of(), List.of(), log));
        return s;
    }

    /**
     * Save a branch as the difference between the state its parent had and the state it has.
     *
     * <p>The two snapshots are the caller's: {@code parentView} is what the world looked like on the parent branch
     * at the moment of the split, and {@code here} is what it looks like now on the new branch. Nothing is inferred
     * from the flags themselves, because a flag that happens to equal its parent's value is not a flag this branch
     * changed, and writing it down would make every branch heavier than the difference it is.
     */
    public BranchSave branch(Snapshot parentView, Snapshot here, int divergenceYear, List<String> suppressedPins,
                             String log) {
        if (parentView == null || here == null) {
            throw new IllegalArgumentException("a branch needs the parent's state and its own");
        }
        String parent = parentView.activeBranch();
        if (!deltas.containsKey(parent)) {
            throw new IllegalStateException("no save for " + parent + ": a branch cannot be saved against a "
                    + "parent that was never saved, because the difference would be a difference from nothing");
        }
        String id = here.activeBranch();
        if (id.equals(parent)) {
            throw new IllegalArgumentException("a branch cannot be its own parent");
        }
        Map<String, String> changed = new TreeMap<>();
        for (Map.Entry<String, String> e : here.flags().entrySet()) {
            String was = parentView.flags().get(e.getKey());
            if (was == null || !was.equals(e.getValue())) {
                changed.put(e.getKey(), e.getValue());
            }
        }
        Set<String> removed = new TreeSet<>(parentView.flags().keySet());
        removed.removeAll(here.flags().keySet());

        BranchSave s = new BranchSave();
        s.deltas.putAll(deltas);
        s.deltas.put(id, new Delta(id, parent, divergenceYear, changed, removed, suppressedPins, log));
        return s;
    }

    public boolean has(String branchId) {
        return deltas.containsKey(branchId);
    }

    public Delta get(String branchId) {
        return deltas.get(branchId);
    }

    public int size() {
        return deltas.size();
    }

    public List<String> ids() {
        return new ArrayList<>(deltas.keySet());
    }

    /** Every branch that named {@code parentId} as its parent. The tree is in the parent ids, not in this map. */
    public List<String> childrenOf(String parentId) {
        List<String> out = new ArrayList<>();
        for (Delta d : deltas.values()) {
            if (Objects.equals(d.parentId(), parentId)) {
                out.add(d.branchId());
            }
        }
        return out;
    }

    /**
     * The chain from the root down to a branch, root first. Refuses rather than guesses: a missing ancestor, or a
     * parent pointer that loops, is a broken save, and a restore that proceeded anyway would produce a world that
     * looks fine and is not the one that was saved.
     */
    public Restored chain(String branchId) {
        List<String> refused = new ArrayList<>();
        List<Delta> back = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        String at = branchId;
        while (at != null) {
            Delta d = deltas.get(at);
            if (d == null) {
                refused.add("there is no save for " + at);
                return new Restored(null, names(back), refused);
            }
            if (!seen.add(at)) {
                refused.add("the save loops: " + String.join(" -> ", seen) + " -> " + at);
                return new Restored(null, names(back), refused);
            }
            back.add(d);
            if (d.isRoot()) {
                break;
            }
            at = d.parentId();
        }
        // Root first, which is the only order a difference can be applied in.
        java.util.Collections.reverse(back);
        Snapshot state = null;
        List<String> applied = new ArrayList<>();
        for (Delta d : back) {
            if (state == null) {
                if (!d.isRoot()) {
                    refused.add("the chain does not reach a root: it starts at " + d.branchId()
                            + ", whose parent " + d.parentId() + " is not saved");
                    return new Restored(null, names(back), refused);
                }
                state = new Snapshot(d.changed(), d.divergenceYear(), d.branchId());
            } else {
                state = d.applyTo(state);
            }
            applied.add(d.branchId());
        }
        return new Restored(state, applied, refused);
    }

    private static List<String> names(List<Delta> ds) {
        List<String> out = new ArrayList<>();
        for (Delta d : ds) {
            out.add(d.branchId());
        }
        return out;
    }

    /** Restore a branch, or null where the save cannot be trusted. */
    public Snapshot restore(String branchId) {
        Restored r = chain(branchId);
        return r.complete() ? r.snapshot() : null;
    }

    /**
     * <b>The check that makes a delta save worth having.</b> Compare what the chain restores to what a full save of
     * the same state says, key by key, and name the branch whose delta first introduced each disagreement.
     *
     * <p>An empty list means the compression is lossless for this branch. It does not mean the state is right; it
     * means the cheap copy says the same thing as the expensive one.
     */
    public List<Difference> diff(String branchId, Snapshot expected) {
        Restored r = chain(branchId);
        if (!r.complete()) {
            List<Difference> out = new ArrayList<>();
            for (String why : r.refused()) {
                out.add(new Difference("(the chain)", "refused", "restored", why));
            }
            return out;
        }
        List<Difference> out = new ArrayList<>();
        Set<String> keys = new TreeSet<>(r.snapshot().flags().keySet());
        keys.addAll(expected.flags().keySet());
        for (String k : keys) {
            String got = r.snapshot().flags().get(k);
            String want = expected.flags().get(k);
            if (!Objects.equals(got, want)) {
                out.add(new Difference(k, got, want, blame(branchId, k, want)));
            }
        }
        // The year a chain restores to is the branch's own divergence year, because that is the year the delta
        // was saved with: a branch at rest is a branch at the moment it split. A full save of the same branch
        // taken later in time has the same flags and a later clock, and that is not a disagreement about the
        // branch. What IS a disagreement is a "full save" dated before the branch existed: no state of this
        // branch can be earlier than the split that made it, so that one is reported.
        if (expected.year() < r.snapshot().year()) {
            out.add(new Difference("(the year)", String.valueOf(r.snapshot().year()),
                    String.valueOf(expected.year()), branchId));
        }
        if (!r.snapshot().activeBranch().equals(expected.activeBranch())) {
            out.add(new Difference("(the active branch)", r.snapshot().activeBranch(), expected.activeBranch(),
                    branchId));
        }
        return out;
    }

    /**
     * Which delta in the chain first leaves {@code key} at a value other than {@code want}. The blame matters more
     * than the difference: it says which branch's save to look at.
     */
    private String blame(String branchId, String key, String want) {
        Restored r = chain(branchId);
        if (!r.complete()) {
            return null;
        }
        List<Delta> ds = new ArrayList<>();
        for (String id : r.applied()) {
            ds.add(deltas.get(id));
        }
        for (Delta d : ds) {
            if (d.changed().containsKey(key) || d.removed().contains(key)) {
                String sets = d.changed().get(key);
                if (!Objects.equals(sets, want)) {
                    return d.branchId();
                }
            }
        }
        return null;
    }

    /** Roughly what the whole save costs, and what saving everything whole would have cost instead. */
    public String cost(Snapshot fullPerBranch) {
        long deltas_ = deltaBytes();
        long whole = fullPerBranch.bytes() * deltas.size();
        if (whole == 0) {
            return "nothing saved yet";
        }
        double pct = 100.0 * deltas_ / whole;
        return String.format(java.util.Locale.ROOT,
                "%d branches in %d bytes, against %d bytes if every branch were saved whole: %.1f%% of the cost.",
                deltas.size(), deltas_, whole, pct);
    }

    /** One line for a screen: how this world's branches are stored. */
    public String ruleLine() {
        int branches = 0;
        for (Delta d : deltas.values()) {
            if (!d.isRoot()) branches++;
        }
        return "The world is saved once and every branch after it saves only what it changed: "
                + deltas.size() + " save" + (deltas.size() == 1 ? "" : "s") + " here, " + branches
                + " of them branches. A branch cannot be restored without the branch it came from.";
    }
}
