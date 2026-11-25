package com.eternalengine.core;

/**
 * <b>Far away means numbers only; drawing crowds by distance.</b> I4 and I5.
 *
 * <h2>Why this class exists</h2>
 *
 * I4: "far away means numbers only: people, vehicles, armies". I5: "drawing crowds on the client: detail
 * by distance, hiding what cannot be seen". Two readers of one fact: <b>how far away is it</b>. The
 * simulation side decides whether a thing is a body or a count; the draw side decides how much of it is
 * drawn and when it is hidden entirely. Both lines are in blocks (the unit the client and the spawner
 * measure in), and both are <b>game defaults named as such</b>, like {@code MAX_SIDE} in step 52.
 *
 * <h2>What the far side is</h2>
 *
 * <p>Past the numbers line, people, vehicles and armies are <b>counts</b>: the population engine
 * remembers how many ("places remember their people") and nothing is ticked, spawned or drawn as a body.
 * That is the whole of I4: not a degradation of the entity, its replacement by the number it came from.
 */
public final class Distances {

    /** Full detail: every figure drawn, every entity ticked. */
    public static final double NEAR = 24.0;

    /** Every second figure drawn. */
    public static final double REDUCED = 64.0;

    /** Past this, people, vehicles and armies are numbers only (I4). */
    public static final double NUMBERS_ONLY = 128.0;

    /** Past this - or past the client's own view, whichever is nearer - nothing is drawn (I5). */
    public static final double HIDDEN = 160.0;

    /** <b>How a thing is simulated.</b> */
    public enum Sim {
        ENTITIES("entities: people, vehicles and armies are here in person"),
        NUMBERS("numbers only: people, vehicles and armies are counts, and the population engine "
                + "remembers them");

        private final String words;

        Sim(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>How a thing is drawn.</b> */
    public enum Detail {
        FULL("full: every figure"),
        REDUCED("reduced: every second figure"),
        CROWD_ONLY("crowd only: a cluster, not figures"),
        HIDDEN("hidden: this cannot be seen from here, so nothing is drawn");

        private final String words;

        Detail(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /**
     * <b>One place at one distance.</b> How it simulates, how it draws, or every reason the question
     * itself is refused.
     */
    public record Level(double blocks, double viewDistance, Sim sim, Detail draw, String refused) {

        public Level {
            refused = refused == null ? "" : refused.trim();
        }

        public boolean ok() {
            return sim != null;
        }

        public String line() {
            return ok() ? blocks + " blocks away: simulates as " + sim.words() + "; draws " + draw.words()
                    : "refused: " + refused;
        }
    }

    private Distances() {
    }

    /**
     * <b>What is this, at this distance?</b> The view distance is the client's own (the server's view
     * distance config); the draw side hides at whichever comes first, this class's line or the view's.
     * Refused: a distance below none ("a distance below none is no distance") and a view distance below
     * none or zero ("hiding everything is the client's to say, and it cannot be asked for here").
     */
    public static Level at(double blocks, double viewDistance) {
        if (blocks < 0) {
            return new Level(blocks, viewDistance, null, null,
                    "a distance below none is no distance: " + blocks);
        }
        if (viewDistance <= 0) {
            return new Level(blocks, viewDistance, null, null,
                    "a view distance of " + viewDistance + " hides everything, and hiding everything "
                            + "is the client's to say: give the view distance");
        }
        Sim sim = blocks <= NUMBERS_ONLY ? Sim.ENTITIES : Sim.NUMBERS;
        double far = Math.min(HIDDEN, viewDistance);
        Detail draw = blocks <= NEAR ? Detail.FULL
                : blocks <= REDUCED ? Detail.REDUCED
                : blocks <= far ? Detail.CROWD_ONLY
                : Detail.HIDDEN;
        return new Level(blocks, viewDistance, sim, draw, "");
    }

    /** What is simulated at this distance. Refused distances come back null. */
    public static Sim simulateAs(double blocks) {
        return blocks < 0 ? null : blocks <= NUMBERS_ONLY ? Sim.ENTITIES : Sim.NUMBERS;
    }

    /** What is drawn at this distance, against the client's view. Refused distances come back null. */
    public static Detail draw(double blocks, double viewDistance) {
        Level l = at(blocks, viewDistance);
        return l.ok() ? l.draw() : null;
    }

    public static String ruleLine() {
        return "One distance table, two readers: past " + (int) NUMBERS_ONLY + " blocks people, vehicles "
                + "and armies are numbers only; the draw thins at " + (int) NEAR + " and " + (int) REDUCED
                + " and hides at " + (int) HIDDEN + " or the client's own view, whichever is nearer. "
                + "The lines are game defaults and say so.";
    }
}
