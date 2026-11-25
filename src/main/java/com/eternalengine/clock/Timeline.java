package com.eternalengine.clock;

/**
 * <b>The one span of the world's time</b> — 3000 BC to AD 3000 — and the line inside it where the
 * record stops.
 *
 * <p>The project has a standing instruction to keep <b>one shared date and one shared
 * timeline</b>, and until now the ends of that timeline were scattered: {@code -3000} in one
 * class, {@code 2026} in another, a {@code LAST_YEAR} per system, and a dozen javadocs each
 * stating a slightly different span. This class is the single place that says how long the world
 * runs and where the records end.
 *
 * <h2>Two ends, and the difference between them matters</h2>
 *
 * <table>
 *   <caption>The two ends of the timeline</caption>
 *   <tr><td>{@link #FIRST_YEAR} = −3000</td><td>3000 BC. The world begins here. This is a
 *       <i>choice</i>: deep prehistory is out of scope.</td></tr>
 *   <tr><td>{@link #RECORDED_UNTIL} = 2026</td><td><b>The last year any record reaches.</b>
 *       Everything after this is {@link #projected}.</td></tr>
 *   <tr><td>{@link #LAST_YEAR} = 3000</td><td>AD 3000. The world ends here. The mod answers
 *       every year up to this one.</td></tr>
 * </table>
 *
 * <h2>What "projected" means, and why it is marked</h2>
 *
 * <p>The user asked for the timeline to run to AD 3000 and for the years after the record to be
 * filled in. That request has a hazard: <b>a projected value that looks like a recorded one is a
 * lie</b>, and this project has spent forty-five steps marking every number
 * {@code recorded} or {@code estimated} so that nobody is fooled. So the future gets a
 * <b>third mark of its own</b>, and every projection carries it.
 *
 * <p><b>What is projected:</b>
 * <ul>
 *   <li><b>Continuations.</b> A technology that reached a place in 1850 is still there in 2500 —
 *       the technology atlas has always extrapolated past its last recorded year, and it was doing
 *       so silently. It now says so.</li>
 *   <li><b>Computations.</b> The sky is not recorded, it is calculated. Eclipses, comets and star
 *       positions are exact in AD 3000 and are marked as computed, not projected.</li>
 *   <li><b>Persisting states.</b> The polity ruling a square on the last recorded day is taken to
 *       go on ruling it. This is what {@code RulerRegister} already did for any year past its
 *       data; it now names that as a projection instead of presenting it as fact.</li>
 * </ul>
 *
 * <p><b>What is NOT projected, and never will be:</b>
 * <ul>
 *   <li><b>No ruler, no head of government, no person and no dynasty is invented</b> for a year
 *       after the record.</li>
 *   <li><b>No war, event, disaster or price.</b> There are no records of them; writing them would
 *       be writing fiction into a register of facts.</li>
 *   <li><b>No population figure</b> past the last measured year.</li>
 * </ul>
 *
 * <p>So a player standing in Hyderabad in AD 2500 is told who ruled there on the last recorded
 * day, that this is a projection, and that nothing is recorded of the years between. That is the
 * truth, and the mod prefers it to a confident invention.
 */
public final class Timeline {

    /** 3000 BC: the first year the world answers for. */
    public static final int FIRST_YEAR = -3000;

    /** AD 3000: the last year the world answers for. */
    public static final int LAST_YEAR = 3000;

    /**
     * The last year any record reaches — the year the data was built. Everything after this is a
     * projection and is marked as one.
     */
    public static final int RECORDED_UNTIL = 2026;

    /** The three marks a value can carry, in the order a reader should trust them. */
    public static final String RECORDED = "recorded";

    public static final String ESTIMATED = "estimated";

    /** A value carried past the end of the record. Never indistinguishable from a recorded one. */
    public static final String PROJECTED = "projected";

    private Timeline() {
    }

    /** Whether this year is inside the world's span at all. */
    public static boolean inSpan(int year) {
        return year >= FIRST_YEAR && year <= LAST_YEAR;
    }

    /**
     * Whether this year is past the end of the record, so that any answer given for it is a
     * projection.
     */
    public static boolean projected(int year) {
        return year > RECORDED_UNTIL;
    }

    /** The mark an answer for this year carries. */
    public static String markFor(int year) {
        return projected(year) ? PROJECTED : RECORDED;
    }

    /**
     * The year to read the record at: any year past the record is read as the last recorded one.
     *
     * <p>This is what {@code RulerRegister} and {@code LeaderRegister} have always done. It is
     * named here so the behaviour is one behaviour, described once.
     */
    public static int readAt(int year) {
        if (year < FIRST_YEAR) return FIRST_YEAR;
        return year > RECORDED_UNTIL ? RECORDED_UNTIL : year;
    }

    /** The world's span, as one line for a log or a command. */
    public static String span() {
        return "3000 BC to AD " + LAST_YEAR;
    }

    /** The span as a pair, for callers that need the numbers. */
    public static int[] spanYears() {
        return new int[]{FIRST_YEAR, LAST_YEAR};
    }

    /**
     * A short note for a command or a chat line, saying that a year is past the record.
     *
     * @return the note, or null when the year is recorded
     */
    public static String projectionNote(int year) {
        if (!projected(year)) return null;
        return "nothing is recorded after AD " + RECORDED_UNTIL
                + ", so this is a projection: what is known of AD " + RECORDED_UNTIL
                + " is taken to go on";
    }

    /** "2500" or "300 BC", in the mod's own convention. */
    public static String label(int year) {
        return year < 0 ? (-year) + " BC" : "AD " + year;
    }

    /** How many years the projection covers — the size of the gap being filled. */
    public static int projectedYears() {
        return LAST_YEAR - RECORDED_UNTIL;
    }
}
