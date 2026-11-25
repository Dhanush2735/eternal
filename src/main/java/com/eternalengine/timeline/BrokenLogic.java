package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>Broken logic within a universe.</b> H4.
 *
 * <h2>Why this class exists</h2>
 *
 * H4 lists seven ways a ruptured universe can be logically wrong: time runs backwards, eras mixed, events
 * loop or come out of order, effects before causes, doubles of famous people, money values upside down,
 * scrambled languages. The resource is <b>rule sets per universe</b>: not a Minecraft flag, not data, but a
 * set of rules saying which of the seven this universe obeys.
 *
 * <p>So this class is two honest things, like {@link BrokenPhysics} before it:
 *
 * <ol>
 *   <li>a <b>rule set</b> ({@link Spec}): which kinds are on, in plain words, each naming <b>what it
 *       bends</b> (the reading order, the era tags, the year order, the cause graph, the person register,
 *       the money values, the words themselves);</li>
 *   <li>a <b>check</b>: a reported set compared key by key, where a universe that reports nothing is
 *       <b>not a pass</b>.</li>
 * </ol>
 *
 * <h2>Proved or declared</h2>
 *
 * <p>Three of the seven can be <b>proved from a dated list with cause links</b> - out of order, effects
 * before causes, doubles - and four cannot. {@link #scan} proves what it can and, for the rest, says in
 * plain words that it found none proves nothing: a scan is never evidence that a universe's money is
 * the right way up. Silence about the declared four is the only honest silence there is.
 */
public final class BrokenLogic {

    /** <b>The seven kinds, and what each bends.</b> */
    public enum Kind {
        TIME_BACKWARDS("time runs backwards", "the reading order of the record", false),
        ERAS_MIXED("eras mixed", "the era a line belongs to", false),
        OUT_OF_ORDER("events loop or come out of order", "the year order of the record", true),
        EFFECTS_BEFORE_CAUSES("effects before causes", "the cause graph", true),
        DOUBLES("doubles of famous people", "the person register", true),
        MONEY_UPSIDE_DOWN("money values upside down", "the money service's values", false),
        SCRAMBLED_LANGUAGES("scrambled languages", "the words themselves", false);

        private final String words;
        private final String bends;
        private final boolean provable;

        Kind(String words, String bends, boolean provable) {
            this.words = words;
            this.bends = bends;
            this.provable = provable;
        }

        public String words() {
            return words;
        }

        /** What this kind bends: the honest "applied to what". */
        public String bends() {
            return bends;
        }

        /**
         * Whether {@link #scan} can prove this kind from dated entries. The four false ones are
         * declarations only, and a scan must never be read as evidence about them.
         */
        public boolean provable() {
            return provable;
        }

        public String line() {
            return words + " · bends " + bends + " · " + (provable
                    ? "provable from dated entries"
                    : "declared only: a scan found none proves nothing");
        }
    }

    /** <b>One universe's rule set.</b> The kinds it obeys, and why. */
    public record Spec(Set<Kind> kinds, String why) {

        public Spec {
            kinds = kinds == null || kinds.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(kinds));
            why = why == null ? "" : why.trim();
        }

        public boolean has(Kind k) {
            return kinds.contains(k);
        }

        public String describe() {
            if (kinds.isEmpty()) {
                return "the logic of the unbroken world";
            }
            List<String> parts = new ArrayList<>();
            for (Kind k : Kind.values()) {
                if (kinds.contains(k)) {
                    parts.add(k.words() + " (bends " + k.bends() + ")");
                }
            }
            return String.join("; ", parts);
        }
    }

    /** <b>What a universe reports its logic to be.</b> */
    public record Reported(Set<Kind> kinds, String source) {

        public Reported {
            kinds = kinds == null || kinds.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(kinds));
            source = source == null ? "" : source.trim();
        }
    }

    /** <b>One disagreement between the rule set and the universe.</b> */
    public record Difference(String what, String asked, String made) {
        public String line() {
            return what + ": asked for " + asked + ", made " + made;
        }
    }

    /** <b>The outcome of making a rule set.</b> Either one, or every reason there is none. */
    public record Made(Spec spec, List<String> refused) {
        public boolean ok() {
            return spec != null;
        }
    }

    /** <b>One dated entry for a scan</b>: its id, its year, the name it carries, and the ids of its causes. */
    public record Entry(String id, int year, String name, List<String> causes) {

        public Entry {
            id = id == null ? "" : id.trim();
            name = name == null ? "" : name.trim();
            causes = causes == null ? List.of() : List.copyOf(causes);
        }
    }

    /** <b>Proof of one kind</b>: the kind and the lines that show it. */
    public record Proof(Kind kind, List<String> lines) {
    }

    /**
     * <b>What a scan found.</b> Three lists, and the third is the point: the kinds no scan can prove,
     * always named, so silence is never mistaken for evidence.
     */
    public record Scan(List<Proof> proved, List<Kind> foundNone, List<Kind> notProvable) {

        public String line() {
            List<String> out = new ArrayList<>();
            for (Proof p : proved) {
                out.add(p.kind().words() + ": " + String.join("; ", p.lines()));
            }
            for (Kind k : foundNone) {
                out.add(k.words() + ": no proof in these entries");
            }
            for (Kind k : notProvable) {
                out.add(k.words() + ": not provable from entries at all (declared only)");
            }
            return out.isEmpty() ? "no entries to scan" : String.join(" | ", out);
        }
    }

    private BrokenLogic() {
    }

    /** The unbroken world: all seven kinds off. */
    public static Spec earth() {
        return new Spec(EnumSet.noneOf(Kind.class), "the logic of the unbroken world");
    }

    /**
     * <b>Make a rule set, or say why not.</b> A rule set that breaks nothing is not a broken rule set (the
     * unbroken world is Earth's logic), one rule named twice is one rule, and a rule set without a why is
     * a rule nobody can argue with.
     */
    public static Made make(List<Kind> kinds, String why) {
        List<String> refused = new ArrayList<>();
        if (kinds == null || kinds.isEmpty()) {
            refused.add("a rule set that breaks nothing is not a broken rule set: the unbroken world is "
                    + "Earth's logic, and this is a new one");
        }
        Set<Kind> seen = new LinkedHashSet<>();
        if (kinds != null) {
            for (Kind k : kinds) {
                if (k == null) {
                    refused.add("a rule that is not one of the seven kinds is not a rule");
                    break;
                }
                if (!seen.add(k)) {
                    refused.add("one rule named twice is one rule: " + k.words());
                }
            }
        }
        if (why == null || why.trim().isEmpty()) {
            refused.add("a rule set without a why is a rule nobody can argue with: say what broke the "
                    + "logic");
        }
        if (!refused.isEmpty()) {
            return new Made(null, refused);
        }
        return new Made(new Spec(EnumSet.copyOf(seen), why), List.of());
    }

    /** <b>What is broken, in words that name what each kind bends.</b> Empty for the unbroken world. */
    public static List<String> changesFromEarth(Spec s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (Kind k : Kind.values()) {
            if (s.has(k)) {
                out.add(k.words() + " · bends " + k.bends());
            }
        }
        return out;
    }

    /** What one rule set does that the other does not. */
    public static List<String> changes(Spec from, Spec to) {
        List<String> out = new ArrayList<>();
        if (to == null) {
            return out;
        }
        for (Kind k : Kind.values()) {
            boolean was = from != null && from.has(k);
            boolean now = to.has(k);
            if (!was && now) {
                out.add("added " + k.words() + " · bends " + k.bends());
            } else if (was && !now) {
                out.add("put " + k.words() + " straight");
            }
        }
        return out;
    }

    /**
     * <b>Check a rule set against what a universe reports.</b> Key by key over all seven kinds. A universe
     * that reports nothing is not a pass: it is "nothing was reported".
     */
    public static List<Difference> diff(Spec asked, Reported made) {
        List<Difference> out = new ArrayList<>();
        if (asked == null) {
            out.add(new Difference("(the rule set)", "a logic", "nothing"));
            return out;
        }
        if (made == null) {
            out.add(new Difference("(the report)", asked.describe(), "nothing was reported"));
            return out;
        }
        for (Kind k : Kind.values()) {
            boolean want = asked.has(k);
            boolean got = made.kinds().contains(k);
            if (want != got) {
                out.add(new Difference(k.words(), want ? "on" : "off", got ? "on" : "off"));
            }
        }
        return out;
    }

    /**
     * <b>Scan dated entries for what can be proved.</b> Entries are (id, year, name, cause ids). The scan
     * proves out-of-order, effects-before-causes and doubles where the entries show them, says "no proof"
     * where they do not, and for the four declared-only kinds it says it cannot speak at all.
     */
    public static Scan scan(List<Entry> entries) {
        List<String> orderProofs = new ArrayList<>();
        List<String> causeProofs = new ArrayList<>();
        List<String> doubleProofs = new ArrayList<>();

        List<Entry> list = new ArrayList<>();
        for (Entry e : entries == null ? List.<Entry>of() : entries) {
            if (e != null) {
                list.add(e);
            }
        }
        if (list.isEmpty()) {
            return new Scan(List.of(), List.of(), List.of());
        }
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (e == null) {
                continue;
            }
            // out of order: the book's order and the years disagree, or one event appears twice (a loop)
            if (i > 0) {
                Entry prev = list.get(i - 1);
                if (prev != null && prev.year() > e.year()) {
                    orderProofs.add("entry " + e.id() + " (" + e.year() + ") comes after " + prev.id()
                            + " (" + prev.year() + "): the book's order and the years disagree");
                }
            }
            for (int j = 0; j < i; j++) {
                Entry prev = list.get(j);
                if (prev != null && prev.id().equals(e.id()) && !e.id().isEmpty()) {
                    orderProofs.add("entry " + e.id() + " appears twice: the record loops");
                }
                if (prev != null && !prev.name().isEmpty() && prev.name().equalsIgnoreCase(e.name())) {
                    doubleProofs.add("two entries named " + e.name() + " (" + prev.id() + ", " + e.id()
                            + "): a double, or one person recorded twice");
                }
            }
            // effects before causes: a cause dated after its effect, or a cause that loops back
            for (String c : e.causes()) {
                for (Entry other : list) {
                    if (other != null && other.id().equals(c)) {
                        if (other.year() > e.year()) {
                            causeProofs.add("effect " + e.id() + " (" + e.year() + ") is caused by "
                                    + other.id() + " (" + other.year() + "), which has not happened yet");
                        }
                        if (other.causes().contains(e.id()) && !e.id().isEmpty()) {
                            causeProofs.add(e.id() + " and " + other.id() + " cause each other: the cause "
                                    + "graph loops");
                        }
                    }
                }
            }
        }

        List<Proof> proved = new ArrayList<>();
        List<Kind> foundNone = new ArrayList<>();
        for (Kind k : Kind.values()) {
            if (!k.provable()) {
                continue;
            }
            List<String> lines = k == Kind.OUT_OF_ORDER ? orderProofs
                    : k == Kind.EFFECTS_BEFORE_CAUSES ? causeProofs : doubleProofs;
            if (lines.isEmpty()) {
                foundNone.add(k);
            } else {
                proved.add(new Proof(k, List.copyOf(lines)));
            }
        }
        List<Kind> notProvable = new ArrayList<>();
        for (Kind k : Kind.values()) {
            if (!k.provable()) {
                notProvable.add(k);
            }
        }
        return new Scan(proved, foundNone, notProvable);
    }

    public static String ruleLine() {
        return "A broken logic is a rule set of seven kinds, and every kind names what it bends. Three "
                + "kinds can be proved from dated entries and four can only be declared; a scan that "
                + "finds none proves nothing about the four.";
    }
}
