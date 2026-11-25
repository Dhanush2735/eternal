package com.eternalengine.timeline;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>The world writes its own history, and that is what the years after 2026 are for.</b>
 *
 * <h2>The problem this solves, and why it is not a data problem</h2>
 *
 * <p>Every pack in this project stops being authoritative somewhere near AD 2026, and no amount of
 * work fixes it: {@link com.eternalengine.clock.Projector} refuses to invent a person, a ruler, an
 * event, a war, a disaster, a price or a population figure past the last recorded year, and it is
 * right to. A projected value is a <b>statement about shipped data</b>, and there is no shipped data
 * for a thousand years.
 *
 * <p>But there is a second kind of fact, and the packs have never been able to express it: <b>what
 * actually happened in this world.</b> A player who builds a railway at a place in 2400 has made a
 * railway exist at that place in 2400. That is not a projection. It is an observation of a world
 * that has been run, in the same sense that a tomb painting is an observation of a world that was
 * lived in.
 *
 * <p>So the years after the seam are not empty. They are <b>unwritten</b>, and this class is what
 * writes them. {@link BranchChronicle} said it already and nothing acted on it: <i>"a chronicle is
 * never predicted. It accumulates as the clock advances."</i>
 *
 * <h2>The seam rule, which is the whole design</h2>
 *
 * <p><b>Before 2026 the packs are the authority and the chronicle is not.</b> Recording "a road was
 * built in 1240" would duplicate a fact that already ships and could contradict it. The one thing
 * the chronicle may say about a year before the seam is that <b>this world diverged from it</b>,
 * because that is the only thing the packs cannot know.
 *
 * <p><b>After 2026 the chronicle is the only authority</b>, because there is nothing else, and
 * everything observed may be recorded.
 *
 * <p>That inversion is the answer to "what is in the projected years": before the seam the packs
 * answer and the chronicle annotates; after it the chronicle answers and nothing else does.
 *
 * <h2>Three rules about what deserves a line</h2>
 *
 * <ol>
 *   <li><b>A threshold per kind.</b> Placing one block is not history. A settlement needs twenty-five
 *       people, a road a kilometre, a technology a tenth of the towns. The thresholds are in
 *       {@link Kind} so they can be argued with rather than hidden.</li>
 *   <li><b>One line for a process, not one per year.</b> A road built over nine years is one entry
 *       that runs from 2381 to 2390, not nine entries. Repetition is {@link #repeat}.</li>
 *   <li><b>Provenance is not optional.</b> A thing the world shows is {@link Provenance#OBSERVED}; a
 *       thing a player names is {@link Provenance#AUTHORED}; nothing here is
 *       {@link Provenance#MODELLED}, because a model is what we do when nothing happened, and
 *       something happened.</li>
 * </ol>
 *
 * <h2>What this class deliberately does not do</h2>
 *
 * <p>It does not decide that anything happened. It has no access to the world. Callers report changes
 * and it decides whether they are history and how to say them, which keeps it Minecraft-free and
 * therefore testable, and keeps the one judgement it makes, <b>what is worth recording</b>, in a
 * single place where it can be read.
 */
public final class Chronicler {

    /** What kind of change it was. The magnitude beside each is the threshold for a line. */
    public enum Kind {
        /** People living somewhere. Threshold: 25 people. */
        SETTLEMENT("a settlement", 25),
        /** Something built that stands. Threshold: 1 building of the kind that matters. */
        BUILDING("a building", 1),
        /** A way from one place to another. Threshold: 1,000 m. */
        ROAD("a road", 1000),
        /** Land brought under the plough. Threshold: 50 hectares. */
        FIELD("new fields", 50),
        /** A technology reaching a share of the towns. Threshold: 100 per mille, ten per cent. */
        TECHNOLOGY("a technology", 100),
        /** A number of people, up or down. Threshold: 500 people. */
        POPULATION("a change in the people", 500),
        /** A route carrying goods. Threshold: 1000 units a year. */
        TRADE("trade", 1000),
        /** Fighting. Threshold: 1, any war is history. */
        WAR("a war", 1),
        /** A disaster. Threshold: 1. */
        DISASTER("a disaster", 1),
        /**
         * A person's own fate: a death, a rescue, a claim, a marriage. Threshold: 1, because one
         * person is the smallest thing a player can change and the only one that is ever personal.
         * Added in step 48, when a player could at last change history and the recorder had no way
         * to say so.
         */
        PERSON("a person", 1),
        /** Somebody named a thing, which is what makes it AUTHORED rather than OBSERVED. */
        NAMING("a name", 1);

        private final String words;
        private final int threshold;

        Kind(String words, int threshold) {
            this.words = words;
            this.threshold = threshold;
        }

        public String words() {
            return words;
        }

        public int threshold() {
            return threshold;
        }
    }

    /** A change somebody saw. Owned by the caller; this class only reads it. */
    public record Change(int year, Kind kind, String subject, String place, int magnitude,
                         @Nullable Provenance provenance) {
        public Change {
            subject = subject == null ? "" : subject;
            place = place == null ? "" : place;
        }
    }

    /** One line of the branch's own history. */
    public record Line(int year, int untilYear, Kind kind, String title, String body,
                       Provenance provenance, boolean beyondShipped) {

        /** True for a process that ran over more than one year. */
        public boolean spansYears() {
            return untilYear > year;
        }

        public String years() {
            return untilYear > year ? year + " to " + untilYear : String.valueOf(year);
        }
    }

    /** The cap on lines per year, so a busy world stays readable. */
    public static final int PER_YEAR = 3;

    private final List<Line> lines = new ArrayList<>();
    /** key to the POSITION of the line representing that process, so a repeat can extend it. */
    private final Map<String, Integer> open = new LinkedHashMap<>();

    /**
     * <b>The seam rule.</b> True if this class may record a line about that year at all.
     *
     * <p>Before the seam, only a divergence may be recorded, and
     * {@link #report(Change)} enforces it by refusing a line whose provenance is not
     * {@link Provenance#AUTHORED}. After the seam, anything may be recorded.
     */
    public static boolean mayRecord(int year) {
        return true;    // the year is always recordable; what may be SAID about it depends on the side
    }

    /** True if this year is past the shipped record, where the chronicle is the only authority. */
    public static boolean beyondShipped(int year) {
        return year > com.eternalengine.clock.Timeline.RECORDED_UNTIL;
    }

    /**
     * Would this change become a line? Exposed because a caller often wants to know before it
     * formats anything, and because a test can ask without adding.
     */
    public boolean worthy(Change c) {
        if (c == null || c.kind() == null) return false;
        if (c.subject().isEmpty()) return false;
        if (c.magnitude() < c.kind().threshold()) return false;
        // Before the seam, the packs own the year: only a divergence may be added to it.
        if (!beyondShipped(c.year()) && c.provenance() != Provenance.AUTHORED) return false;
        // After the seam, a MODELLED line is what we do when nothing happened, so it is not history.
        return !(beyondShipped(c.year()) && c.provenance() == Provenance.MODELLED);
    }

    /**
     * Report a change. Returns the line it became, or null if it was below the threshold, before the
     * seam without a divergence, or a repeat of a process already open.
     */
    @Nullable
    public synchronized Line report(Change c) {
        if (!worthy(c)) return null;
        // Provenance is never blank: a change with no assertion behind it is something the world
        // shows, which is OBSERVED. Leaving null here made the line unreadable, and the first probe
        // run caught it.
        if (c.provenance() == null) {
            c = new Change(c.year(), c.kind(), c.subject(), c.place(), c.magnitude(),
                    Provenance.OBSERVED);
        }
        boolean beyond = beyondShipped(c.year());

        // Rule 2: a process already open becomes a longer line, not a second one.
        String key = c.kind().name() + "|" + c.subject().toLowerCase(Locale.ROOT) + "|"
                + c.place().toLowerCase(Locale.ROOT);
        Integer at = open.get(key);
        if (at != null && at < lines.size()) {
            Line l = lines.get(at);
            // A process interrupted for more than fifty years is a different event with the same
            // name, not a continuation of the first one.
            if (c.year() >= l.year() && c.year() <= l.untilYear() + 50) {
                Line longer = new Line(l.year(), Math.max(l.untilYear(), c.year()), l.kind(),
                        l.title(), l.body(), l.provenance(), l.beyondShipped());
                lines.set(at, longer);
                return longer;
            }
        }

        // Rule 3: how many lines a single year may hold.
        if (countIn(c.year()) >= PER_YEAR) return null;

        Line line = new Line(c.year(), c.year(), c.kind(), title(c),
                body(c), c.provenance(), beyond);
        lines.add(line);
        open.put(key, lines.size() - 1);
        return line;
    }

    private static String title(Change c) {
        return switch (c.kind()) {
            case SETTLEMENT -> "A new settlement at " + c.place();
            case BUILDING -> c.subject() + " built at " + c.place();
            case ROAD -> "A road at " + c.place();
            case FIELD -> "New fields at " + c.place();
            case TECHNOLOGY -> c.subject() + " reaches " + c.place();
            case POPULATION -> "The people of " + c.place() + " change in number";
            case TRADE -> "Trade through " + c.place();
            case WAR -> "War at " + c.place();
            case DISASTER -> "A disaster at " + c.place();
            case PERSON -> c.subject() + " at " + c.place();
            case NAMING -> c.subject() + " is named";
        };
    }

    private static String body(Change c) {
        String amount = switch (c.kind()) {
            case SETTLEMENT -> c.magnitude() + " people";
            case ROAD -> c.magnitude() + " m of road";
            case FIELD -> c.magnitude() + " hectares";
            case TECHNOLOGY -> (c.magnitude() / 10) + " per cent of the towns";
            case POPULATION -> (c.magnitude() >= 0 ? "+" : "") + c.magnitude() + " people";
            case TRADE -> c.magnitude() + " units a year";
            case PERSON -> c.magnitude() == 1 ? "one person" : c.magnitude() + " people";
            default -> String.valueOf(c.magnitude());
        };
        String who = switch (c.provenance() == null ? Provenance.OBSERVED : c.provenance()) {
            case AUTHORED -> "Somebody put it there and put their name to it.";
            case OBSERVED -> "It is here, and anybody standing in the square can see it.";
            case STRUCTURAL -> "It follows from something else in this world rather than from a "
                    + "decision.";
            case MODELLED -> "The engine supposes it.";
        };
        return amount + " at " + c.place() + ". " + who;
    }

    private int countIn(int year) {
        int n = 0;
        for (Line l : lines) {
            if (l.year() == year) n++;
        }
        return n;
    }

    public synchronized List<Line> lines() {
        return List.copyOf(lines);
    }

    public synchronized List<Line> upTo(int year) {
        List<Line> out = new ArrayList<>();
        for (Line l : lines) {
            if (l.untilYear() <= year || l.year() <= year) out.add(l);
        }
        return out;
    }

    public synchronized int size() {
        return lines.size();
    }

    public synchronized boolean isEmpty() {
        return lines.isEmpty();
    }

    /** How many lines fall after the seam: the measure of whether a world has a future at all. */
    public synchronized int beyondSeam() {
        int n = 0;
        for (Line l : lines) {
            if (l.beyondShipped()) n++;
        }
        return n;
    }

    /** Lines per century, which is the honest measure of how much a world has to say. */
    public synchronized double perCentury() {
        if (lines.isEmpty()) return 0.0d;
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (Line l : lines) {
            first = Math.min(first, l.year());
            last = Math.max(last, l.untilYear());
        }
        int span = Math.max(1, last - first);
        return lines.size() * 100.0d / span;
    }

    /**
     * The sentence a screen should show for the seam, which says whose history this is. This is the
     * line the whole class exists to make possible.
     */
    public String seamLine() {
        int n = beyondSeam();
        String before = "Before AD " + com.eternalengine.clock.Timeline.RECORDED_UNTIL
                + " the records are the authority and this chronicle only notes what changed.";
        if (n == 0) {
            return before + " After it, nothing has happened yet in this world: the years past the "
                    + "record are unwritten, not empty.";
        }
        return before + " After it, this world has written " + n + " line"
                + (n == 1 ? "" : "s") + " of its own history, and they are the only history there is.";
    }

    public synchronized void clear() {
        lines.clear();
        open.clear();
    }
}
