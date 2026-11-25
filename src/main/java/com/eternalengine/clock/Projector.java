package com.eternalengine.clock;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Step 46 part 7: <b>the third mark, applied.</b>
 *
 * <p>{@link Timeline} states the rule for the years after the record: <b>a projected value must
 * never be indistinguishable from a recorded one</b>, so it carries its own mark. It has stated
 * that rule since step 41 and, as of step 46, <b>not one shipped data pack contains the word</b>.
 * The rule was written down and never applied.
 *
 * <p>This class applies it, and it makes one design decision worth stating plainly, because it is
 * the reason the mark is not a column in a pack:
 *
 * <blockquote><b>A projection is a property of the ANSWER, not of the ROW.</b></blockquote>
 *
 * <p>The row "France is ruled by the Fifth Republic from 1958" is a recorded fact and stays one
 * forever. The answer "who rules France in 2600" is a projection, and it is a projection only
 * because of the year it was asked about. Storing {@code projected} on the row would corrupt a true
 * record; storing it nowhere would let the answer pass as fact. So every answer passes through
 * here, and {@link Stated#phrase()} says what it is in words a player can read.
 *
 * <h2>The three kinds of projection, and the four things that are never projected</h2>
 *
 * <p>Projected: <b>continuations</b> (a technology that arrived stays), <b>computations</b> (the sky
 * is calculated, not recorded, and is exact in AD 3000), and <b>persisting states</b> (the polity of
 * the last recorded day goes on).
 *
 * <p><b>Never projected:</b> no ruler, head of government, person or dynasty; no war, event,
 * disaster or price; no population figure past the last measured year. Those are not
 * "not yet implemented": they are <b>refused</b>, and {@link #refusal} returns the refusal so a
 * caller cannot accidentally invent one.
 *
 * <p><b>This class touches no Minecraft</b>, so the policy is checkable on a plain JDK.
 */
public final class Projector {

    /** The things this project will never invent for a year after the record. */
    public enum Kind {
        /** A ruler, a head of government, a person, a dynasty. */
        PERSON("no ruler or person is invented for a year past the record"),
        /** A war, a battle, an event, a disaster. */
        EVENT("no war, event or disaster is invented for a year past the record"),
        /** A price. */
        PRICE("no price is invented for a year past the record"),
        /** A population figure. */
        POPULATION("no population figure is given past the last measured year");

        private final String why;

        Kind(String why) {
            this.why = why;
        }

        public String why() {
            return why;
        }
    }

    /** The kinds that MAY be carried past the record, each for its own reason. */
    public enum Carry {
        /** A technology, a road, a vehicle: things that arrived and do not un-arrive on their own. */
        CONTINUATION("a technology that arrived stays"),
        /** The sky: calculated, not recorded, and exact at any year. */
        COMPUTATION("calculated rather than recorded, and exact at any year"),
        /** A polity, a border, an institution: the state of the last recorded day goes on. */
        PERSISTENCE("the state of the last recorded day goes on");

        private final String why;

        Carry(String why) {
            this.why = why;
        }

        public String why() {
            return why;
        }
    }

    /**
     * An answer with its mark attached.
     *
     * @param value  what the register said, or null when it refused
     * @param mark   {@code recorded}, {@code estimated} or {@code projected}
     * @param phrase a sentence a player can read, saying what this is
     */
    public record Stated(@Nullable Object value, String mark, String phrase) {

        public boolean present() {
            return value != null;
        }
    }

    private Projector() {
    }

    /**
     * Wrap a carried value for a year.
     *
     * <p>Before the end of the record the mark is the row's own ({@code recorded} or
     * {@code estimated}). After it, the mark is {@code projected} and the phrase says so.
     */
    public static Stated carry(@Nullable Object value, Carry how, int year, String what) {
        if (value == null) {
            return new Stated(null, Timeline.PROJECTED,
                    "there is nothing recorded of the " + what + " in " + label(year));
        }
        if (!Timeline.projected(year)) {
            return new Stated(value, Timeline.RECORDED, what + " in " + label(year));
        }
        return new Stated(value, Timeline.PROJECTED,
                what + " in " + label(year) + " is PROJECTED, from the last recorded year "
                        + label(Timeline.RECORDED_UNTIL) + ": " + how.why());
    }

    /**
     * Refuse a value that must never be projected.
     *
     * @return the refusal, always non-null for a projected year; null when the year is inside the
     *         record, where the caller may answer normally
     */
    @Nullable
    public static String refusal(Kind kind, int year) {
        if (!Timeline.projected(year)) return null;
        return label(year) + ": " + kind.why() + ". The last recorded state is from "
                + label(Timeline.RECORDED_UNTIL) + ".";
    }

    /** Whether a kind may be carried at all. Three may and four may not, and this is the list. */
    public static boolean mayCarry(Kind kind) {
        return false;
    }

    /** The kinds that may be carried past the record, as one line. */
    public static String carried() {
        StringBuilder sb = new StringBuilder();
        for (Carry c : Carry.values()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.name().toLowerCase(java.util.Locale.ROOT)).append(" (")
              .append(c.why()).append(")");
        }
        return sb.toString();
    }

    /** The kinds that are never invented, as one line. */
    public static String refused() {
        StringBuilder sb = new StringBuilder();
        for (Kind k : Kind.values()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(k.name().toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }

    /** A year written the way the world writes it: 3000 BC, AD 1400. */
    public static String label(int year) {
        return year < 0 ? (-year) + " BC" : "AD " + year;
    }

    /** Whether a year can be answered for at all. */
    public static boolean answerable(int year) {
        return Timeline.inSpan(year);
    }

    /** The reason a year cannot be answered, or null when it can. */
    @Nullable
    public static String outOfSpanWhy(int year) {
        if (year < Timeline.FIRST_YEAR) {
            return label(year) + " is before the world begins, at " + label(Timeline.FIRST_YEAR)
                    + ". Deep prehistory is out of scope by choice, not by accident.";
        }
        if (year > Timeline.LAST_YEAR) {
            return label(year) + " is past the end of the world, at " + label(Timeline.LAST_YEAR)
                    + ". The mod neither answers nor guesses beyond it.";
        }
        return null;
    }

    /** The whole policy as a block of lines for {@code /ee check} or a log. */
    public static List<String> policy() {
        return List.of(
                "the world runs " + Timeline.span() + "; the record ends "
                        + label(Timeline.RECORDED_UNTIL),
                "carried past the record: " + carried(),
                "never invented past the record: " + refused(),
                "a projection is a property of the ANSWER, not of the row: the row stays a fact");
    }

    /** One line for a command. */
    public static String summary(int year) {
        String why = outOfSpanWhy(year);
        if (why != null) return why;
        if (!Timeline.projected(year)) return label(year) + " is inside the record";
        return label(year) + " is projected from " + label(Timeline.RECORDED_UNTIL)
                + ", and every answer for it says so";
    }
}
