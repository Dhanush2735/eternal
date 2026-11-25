package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * <b>What a change to history is, said once instead of nine times.</b>
 *
 * <h2>Why this class exists</h2>
 *
 * Step 48 names nine or ten ways a player can change history: stop an event, kill or save a person, win
 * or lose a battle, take the crown, found a town, bring an invention early, tell people the future,
 * trade, sabotage, marry. Written as ten systems that is ten places to drift apart, ten permission
 * checks that will disagree, and ten vocabularies for the chronicle to learn.
 *
 * <p>So the ways are not written down as ways. Every one of them is a point in a grid of two
 * questions: <b>what was changed</b> (an event, a person, a place, a knowledge, a good) and <b>how</b>
 * (removed, added, earlier, later, bigger, smaller). That is {@link What} and {@link How}, thirty
 * cells, and each cell carries its own price and its own gate.
 *
 * <h2>The two things the grid cannot say, and why that matters</h2>
 *
 * <p>The guardrail refuses four inventions past the seam: a ruler, a war, a price and a population
 * figure. Three of the four are refused here <b>by having no cell at all</b>. There is no {@code What}
 * for a number of people, so a census cannot be expressed; there is no {@code What} for money, so a
 * price cannot be expressed. The fourth, a ruler or a war that nobody made, is expressible as a
 * {@code PERSON} or an {@code EVENT} and is refused one layer up, by provenance: an act marked
 * {@link Provenance#MODELLED} is not history, and before the seam it is refused outright.
 *
 * <p>That is the useful property: a refusal that is a <b>missing word</b> cannot be forgotten by a
 * caller, because there is nothing to call.
 *
 * <h2>Not a simulation</h2>
 *
 * <p>Nothing here works out what follows from a change. It says what a change is, what it costs, who
 * may make it and how it is recorded. Consequences are step 49.
 */
public final class ChangeRecord {

    /** <b>What was changed.</b> Five kinds, and no more. */
    public enum What {
        /** Something that happened: a battle, a plague, a coronation, a fire. */
        EVENT("an event"),
        /** One person's fate. */
        PERSON("a person"),
        /** A place: founded, abandoned, walled, burnt. */
        PLACE("a place"),
        /** A technique, a text, an idea. F1's "bring an invention early" lives here. */
        KNOWLEDGE("a knowledge"),
        /** A thing that is bought and sold. */
        GOOD("a good");

        private final String words;

        What(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>How it was changed.</b> Six verbs. */
    public enum How {
        REMOVE("removed"),
        ADD("added"),
        EARLIER("made earlier"),
        LATER("made later"),
        BIGGER("made bigger"),
        SMALLER("made smaller");

        private final String words;

        How(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /**
     * One change somebody made. Everything a chronicle line needs, plus <b>who</b> did it, which is
     * the only field a recorded event does not have.
     */
    public record Act(int year, What what, How how, String subject, String place, int magnitude,
                      Provenance provenance, String actor) {
        public Act {
            subject = subject == null ? "" : subject;
            place = place == null ? "" : place;
            actor = actor == null ? "" : actor;
        }

        public String words() {
            return what.words() + " " + how.words() + ": " + subject + " at " + place;
        }
    }

    /** Cells in the grid, which is the whole vocabulary of change. */
    public static final int CELLS = What.values().length * How.values().length;

    /**
     * Cells that no verb fills, with the reason. A hole with a reason is a decision; a hole without
     * one is an oversight.
     */
    @Nullable
    public static String holeFor(What what, How how) {
        if (what == What.PERSON && (how == How.EARLIER || how == How.LATER)) {
            return "A person is born when they are born. You can save them, kill them or marry them, "
                    + "and you cannot move their birth, so this cell is empty.";
        }
        return null;
    }

    public static boolean expressible(@Nullable What what, @Nullable How how) {
        return what != null && how != null && holeFor(what, how) == null;
    }

    /** How many cells a verb fills. Part 2's own headline number. */
    public static int filledCells() {
        int n = 0;
        for (What w : What.values()) {
            for (How h : How.values()) {
                if (expressible(w, h)) n++;
            }
        }
        return n;
    }

    /**
     * The price of a change, in separate real actions. Calibrated against
     * {@code InWorldInterventionDetector}, which asks for six units of force over three actions to
     * stop a battle and counts a broken block as a quarter: this number is the <b>count of actions</b>
     * and that one is their weight, so a costly change here is a long change there.
     *
     * @return the number of actions, or -1 for a hole
     */
    public static int costFor(What what, How how) {
        if (!expressible(what, how)) return -1;
        return switch (what) {
            case EVENT -> switch (how) {
                case ADD -> 9;          // starting a war costs more than stopping one
                case REMOVE -> 6;
                case BIGGER -> 8;
                case SMALLER, EARLIER, LATER -> 5;
            };
            case PERSON -> switch (how) {
                case ADD, REMOVE -> 3;  // one person is the smallest thing you can change
                case BIGGER, SMALLER -> 4; // their standing, their household
                case EARLIER, LATER -> -1;
            };
            case PLACE -> switch (how) {
                case ADD -> 5;          // found a town
                case REMOVE -> 9;       // empty one
                case BIGGER, SMALLER -> 6;
                case EARLIER, LATER -> 4;
            };
            case KNOWLEDGE -> switch (how) {
                case ADD -> 4;
                case REMOVE -> 6;       // losing a technique is harder than learning it
                case EARLIER, LATER -> 7; // moving a discovery is the most expensive thing here
                case BIGGER, SMALLER -> 5;
            };
            case GOOD -> switch (how) {
                case ADD, REMOVE, SMALLER -> 4;
                case BIGGER -> 5;
                case EARLIER, LATER -> 5;
            };
        };
    }

    /**
     * What a player must already have before the change is even possible: F5's roles, arriving early
     * as a gate rather than as a permission system. Empty means anybody could do it.
     */
    public static String gateFor(What what, How how) {
        if (!expressible(what, how)) return "nothing can open this";
        return switch (what) {
            case EVENT -> switch (how) {
                case ADD, BIGGER -> "an army or a following";
                case REMOVE, SMALLER -> "a side in it";
                case EARLIER, LATER -> "a side in it";
            };
            case PERSON -> "your own two hands";
            case PLACE -> switch (how) {
                case ADD -> "twenty people who will stay";
                case REMOVE, BIGGER, SMALLER -> "a claim on it";
                case EARLIER, LATER -> "a claim on it";
            };
            case KNOWLEDGE -> switch (how) {
                case ADD, EARLIER -> "the thing itself, or a workshop to make it";
                case REMOVE, SMALLER -> "an authority that forbids it";
                case LATER -> "an authority that forbids it";
                case BIGGER -> "a workshop";
            };
            case GOOD -> switch (how) {
                case ADD, BIGGER -> "a purse and a route";
                case REMOVE, SMALLER -> "a purse and a route";
                case EARLIER, LATER -> "a purse and a route";
            };
        };
    }

    /**
     * The chronicle's own kind for this cell, so a change made by a player is recorded in the same
     * vocabulary as everything else that happens. {@code null} for a hole.
     */
    @Nullable
    public static Chronicler.Kind kindFor(What what, How how) {
        if (!expressible(what, how)) return null;
        return switch (what) {
            case EVENT -> Chronicler.Kind.WAR;
            case PERSON -> Chronicler.Kind.PERSON;
            case PLACE -> how == How.ADD || how == How.REMOVE
                    ? Chronicler.Kind.SETTLEMENT : Chronicler.Kind.BUILDING;
            case KNOWLEDGE -> Chronicler.Kind.TECHNOLOGY;
            case GOOD -> Chronicler.Kind.TRADE;
        };
    }

    /**
     * The magnitude that puts an act exactly at its kind's threshold, so a probe can ask "does this
     * become a line" without inventing numbers for every kind.
     */
    public static int sampleMagnitude(What what, How how) {
        Chronicler.Kind kind = kindFor(what, how);
        return kind == null ? 0 : kind.threshold();
    }

    /** Turn an act into the chronicler's own record, so one recorder serves the whole world. */
    @Nullable
    public static Chronicler.Change toChange(@Nullable Act act) {
        if (act == null) return null;
        Chronicler.Kind kind = kindFor(act.what(), act.how());
        if (kind == null) return null;
        return new Chronicler.Change(act.year(), kind, act.subject(), act.place(),
                act.magnitude(), act.provenance());
    }

    /**
     * F1's ways, each one written as a point in the grid. This is the whole of F1: ten rows, and if a
     * row cannot be placed in the grid it is not a way to change history.
     */
    public enum Way {
        STOP_AN_EVENT(What.EVENT, How.REMOVE,
                "Be in the way of it. The detector already weighs this and refuses to count clicks."),
        START_OR_ESCALATE_A_WAR(What.EVENT, How.BIGGER,
                "The most expensive thing here, and the one a player usually cannot reach alone."),
        KILL_OR_SAVE_A_PERSON(What.PERSON, How.REMOVE,
                "One person. The smallest change, and the only one that is ever personal."),
        MARRY(What.PERSON, How.ADD,
                "A tie between two people, which is a claim on a household and sometimes a crown."),
        TAKE_THE_CROWN(What.PERSON, How.BIGGER,
                "Not a new person but a person made bigger: what VipTakeoverSystem already reaches for."),
        FOUND_A_TOWN(What.PLACE, How.ADD,
                "Twenty people who will stay. In the register this is a settlement line in the year it "
                        + "happens, which is what the chronicle was built to write."),
        SABOTAGE_OR_EMPTY_A_PLACE(What.PLACE, How.SMALLER,
                "A place made smaller, which is cheaper than emptying it and rarer than founding one."),
        BRING_AN_INVENTION_EARLY(What.KNOWLEDGE, How.EARLIER,
                "The most expensive row, because it needs the thing itself and a world that can use it."),
        TELL_PEOPLE_THE_FUTURE(What.KNOWLEDGE, How.ADD,
                "Knowledge added without the workshop: what a player who has been there can do."),
        TRADE_OR_SABOTAGE_GOODS(What.GOOD, How.BIGGER,
                "The only way that is already playable: MarketLedger records it today.");

        private final What what;
        private final How how;
        private final String plain;

        Way(What what, How how, String plain) {
            this.what = what;
            this.how = how;
            this.plain = plain;
        }

        public What what() {
            return what;
        }

        public How how() {
            return how;
        }

        public String plain() {
            return plain;
        }

        public int cost() {
            return costFor(what, how);
        }

        public String gate() {
            return gateFor(what, how);
        }

        public Chronicler.Kind kind() {
            return kindFor(what, how);
        }
    }

    /**
     * The four inventions the guardrail refuses, and how each is refused. Three of them have no word
     * in this vocabulary, which is the strongest form of refusal there is.
     */
    public enum Refused {
        A_POPULATION_FIGURE("There is no What for a number of people. The grid changes things, and a "
                + "count of people is not a thing: a player may make a town grow and may not set its "
                + "census."),
        A_PRICE("There is no What for money. A good can be moved, made cheaper to reach or kept out, "
                + "and the price that results is a computation, which is why every price in this "
                + "project comes from a recorded table or is refused."),
        A_WAR_NOBODY_CAUSED("Expressible as an EVENT, and refused by provenance: a battle marked "
                + "MODELLED is not history, and before the seam the chronicler refuses it outright."),
        A_RULER_NOBODY_MADE("Expressible as a PERSON made bigger, and refused the same way. A ruler a "
                + "player puts on a throne is AUTHORED and is recorded; a ruler the engine supposes for "
                + "a year nobody wrote down is MODELLED and is not.");

        private final String why;

        Refused(String why) {
            this.why = why;
        }

        public String why() {
            return why;
        }

        /** True only for the two that the vocabulary itself cannot name. */
        public boolean hasNoWord() {
            return this == A_POPULATION_FIGURE || this == A_PRICE;
        }
    }

    /** Every way, for a screen or a probe to walk. */
    public static List<Way> ways() {
        List<Way> out = new ArrayList<>();
        for (Way w : Way.values()) out.add(w);
        return out;
    }

    /** The grid as text, thirty lines, holes included and named. */
    public static List<String> rows() {
        List<String> out = new ArrayList<>();
        for (What w : What.values()) {
            for (How h : How.values()) {
                String hole = holeFor(w, h);
                if (hole != null) {
                    out.add(String.format(Locale.ROOT, "%-10s %-8s -  %s", w, h, hole));
                } else {
                    out.add(String.format(Locale.ROOT, "%-10s %-8s %2d actions, gate: %s, recorded as %s",
                            w, h, costFor(w, h), gateFor(w, h), kindFor(w, h).words()));
                }
            }
        }
        return out;
    }
}
