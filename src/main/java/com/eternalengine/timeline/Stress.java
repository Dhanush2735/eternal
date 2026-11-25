package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * <b>One paradox counter, shared with Terra++.</b> H2.
 *
 * <h2>Why this class exists</h2>
 *
 * H2 asks for one counter that events, people and things add to, shared with Terra++ whose broken dated
 * buildings add to it too. Today Terra++ counts buildings on its side and this mod counts nothing: there is no
 * number anywhere that says how strained a timeline is.
 *
 * <h2>What this class is, and what it deliberately is not</h2>
 *
 * <p>It is <b>one number</b>, kept in named parts ({@link Source}: events, people, things, buildings), where
 * every addition carries who, when, what and why, and where {@link #repair} can bring it down again (H8's
 * arithmetic lives here; H8's screen is step 56). It is <b>not</b> a verdict: no threshold, no erasure, no
 * consequence. The old campaign's countdown-to-erasure fantasy is switched off (decision 7), and the honest
 * part of it - pressure that accumulates and can be relieved - is exactly this counter. What the number MEANS
 * to the game is policy, and this class has none.
 *
 * <h2>The seam to Terra++</h2>
 *
 * <p>Buildings reach the counter through one narrow {@link Bridge}, the same discipline as
 * {@code terrabridge.BranchWorld}: with no Terra++, {@link #absent()} reports zero and <b>says it is not
 * connected</b>, so a timeline with no bridge is never mistook for a timeline with no building stress.
 *
 * <h2>Repairs clamp, and say so</h2>
 *
 * <p>A repair larger than the stress on the books brings the total to zero and reports the part that was
 * unused. This is the one place the project clamps rather than refuses: a purse is a balance a player owns and
 * cannot overspend, but "the fort is rebuilt" is a fact of the world and refusing it because the counter is
 * already low would be the wrong way to be honest. The words say what happened either way.
 */
public final class Stress {

    /** <b>Who is adding to the counter.</b> The sources H2 names, plus buildings from Terra++. */
    public enum Source {
        EVENT("event"),
        PERSON("person"),
        THING("thing"),
        BUILDING("building");

        private final String words;

        Source(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>One addition or repair on the books.</b> Amounts are positive; a repair is recorded as one. */
    public record Entry(Source source, String who, int year, String what, int amount, String why,
                        boolean repair) {

        public String line() {
            return (repair ? "repair" : "add") + " · " + amount + " · " + source.words() + " · " + what
                    + " · " + who + " · " + year + (why == null || why.isEmpty() ? "" : " · " + why);
        }
    }

    /**
     * <b>The way Terra++ reaches this counter.</b> Two methods, like every seam in this project: what it adds,
     * and what it is.
     */
    public interface Bridge {
        /** How much broken dated buildings are straining the timeline, right now. */
        int buildingStress();

        /** What this bridge is, for the report. */
        String describe();
    }

    /** <b>The bridge that is not there.</b> Reports zero and says so, rather than pretending. */
    public static Bridge absent() {
        return new Bridge() {
            @Override
            public int buildingStress() {
                return 0;
            }

            @Override
            public String describe() {
                return "Terra++ is not connected, so no broken buildings are counted here";
            }
        };
    }

    private final List<Entry> books = new ArrayList<>();
    private Bridge bridge = absent();
    private int added;
    private int repaired;

    private Stress() {
    }

    public static Stress empty() {
        return new Stress();
    }

    /** Connect the Terra++ side. A second connection replaces the first and the report says which. */
    public Stress connect(Bridge b) {
        this.bridge = b == null ? absent() : b;
        return this;
    }

    /** <b>What the bridge adds.</b> */
    public int buildingStress() {
        return Math.max(0, bridge.buildingStress());
    }

    public String bridgeLine() {
        return bridge.describe();
    }

    /**
     * <b>The outcome of an entry.</b> Either the row on the books, or the words why there is none.
     */
    public record Changed(Entry entry, String refused) {
        public boolean ok() {
            return entry != null;
        }
    }

    /**
     * <b>Add to the counter.</b> Every addition names its source, whose hand, the year, what and why: an
     * unexplained rise is a number nobody can question later. Amounts below one are refused.
     */
    public Changed add(Source source, String who, int year, String what, int amount, String why) {
        if (source == null) {
            return new Changed(null, "an addition with no source is not on the books: say event, person, "
                    + "thing or building");
        }
        if (amount < 1) {
            return new Changed(null, "adding " + amount + " adds nothing");
        }
        if (why == null || why.trim().isEmpty()) {
            return new Changed(null, "an addition must say why: an unexplained rise is a number nobody can "
                    + "question later");
        }
        Entry e = new Entry(source, who == null ? "nobody" : who.trim(), year,
                what == null ? "" : what.trim(), amount, why.trim(), false);
        books.add(e);
        added += amount;
        return new Changed(e, "");
    }

    /** <b>A paradox on the books.</b> Each kind names its source: a grandfather is a person's problem. */
    public Changed add(Paradox.Found p) {
        if (p == null) {
            return new Changed(null, "there is no paradox to put on the books");
        }
        Source s;
        switch (p.kind()) {
            case GRANDFATHER:
            case MEETING_SELF:
                s = Source.PERSON;
                break;
            case CAUSAL_LOOP:
                s = Source.THING;
                break;
            default:
                s = Source.EVENT;
                break;
        }
        return add(s, p.who(), p.year(), p.what(), p.weight(), p.kind().words() + ": " + p.why());
    }

    /**
     * <b>Repair.</b> H8's arithmetic: rebuilding what was broken lowers the stress. A repair larger than the
     * books brings the total to zero and the entry says how much of it was unused.
     */
    public Changed repair(String who, int year, String what, int amount, String why) {
        if (amount < 1) {
            return new Changed(null, "repairing " + amount + " repairs nothing");
        }
        if (why == null || why.trim().isEmpty()) {
            return new Changed(null, "a repair must say what was rebuilt");
        }
        int before = total();
        int used = Math.min(amount, before);
        int unused = amount - used;
        Entry e = new Entry(Source.EVENT, who == null ? "nobody" : who.trim(), year,
                what == null ? "" : what.trim(), amount,
                why.trim() + (unused > 0 ? " (" + unused + " of the repair was unused: the counter was at "
                        + before + ")" : ""), true);
        books.add(e);
        repaired += used;
        return new Changed(e, "");
    }

    /** <b>The one number.</b> What the books say plus what Terra++'s buildings add, never below zero. */
    public int total() {
        return Math.max(0, added - repaired + buildingStress());
    }

    /** The total without the bridge: what this mod's own sources have done. */
    public int ownTotal() {
        return Math.max(0, added - repaired);
    }

    public int added() {
        return added;
    }

    public int repaired() {
        return repaired;
    }

    /** The counter in its named parts. */
    public Map<Source, Integer> bySource() {
        Map<Source, Integer> out = new EnumMap<>(Source.class);
        for (Source s : Source.values()) {
            out.put(s, 0);
        }
        for (Entry e : books) {
            int now = out.get(e.source());
            out.put(e.source(), now + (e.repair() ? -e.amount() : e.amount()));
        }
        out.put(Source.BUILDING, out.get(Source.BUILDING) + buildingStress());
        for (Source s : Source.values()) {
            out.put(s, Math.max(0, out.get(s)));
        }
        return out;
    }

    public List<Entry> books() {
        return List.copyOf(books);
    }

    /** The report: the number, its parts, and what the bridge is. */
    public List<String> report() {
        List<String> out = new ArrayList<>();
        out.add("stress " + total() + " (" + ownTotal() + " of this mod's own, " + buildingStress()
                + " from buildings)");
        Map<Source, Integer> parts = bySource();
        for (Source s : Source.values()) {
            out.add("  " + s.words() + ": " + parts.get(s));
        }
        out.add("  " + bridge.describe());
        return out;
    }

    /** The books, in order. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (Entry e : books) {
            out.add("  " + e.line());
        }
        return out;
    }

    public static String ruleLine() {
        return "One counter, named parts, every addition explained, and repair brings it down. It counts and "
                + "reports: no threshold decides anything here, and with no Terra++ the building part is "
                + "reported as not counted, not as zero.";
    }
}
