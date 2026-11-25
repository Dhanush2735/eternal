package com.eternalengine.clock;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * <b>F9: how a player reaches another year.</b> One door, and one clock.
 *
 * <h2>The rule this class is built around</h2>
 *
 * <p>There is <b>one date for the world</b>. Not one per player, not one per place, not one per dimension.
 * That was decided long before this class existed (turn 64: only {@code /ee date} moves the clock, and
 * decision 3), and everything else in this project leans on it: the town pack answers for a year, the
 * technology atlas answers for a year, the sky is computed for a year, and the chronicler writes lines in
 * one year at a time. A per-player clock would break all of it at once, because two players standing in
 * the same village in different years would need two different villages.
 *
 * <p>So <b>a player does not travel in time. The world does.</b> When the clock moves, everybody moves
 * with it, and nobody is duplicated, so there is no paradox to resolve and no second copy of anybody to
 * meet.
 *
 * <h2>The two ways the clock moves, and who may use them</h2>
 *
 * <table>
 *   <tr><th>how</th><th>who</th><th>which direction</th></tr>
 *   <tr><td>it runs</td><td>nobody, it just does</td><td>forward only, at the speed the server is set to</td></tr>
 *   <tr><td>{@code /ee date <year> <month> <day>}</td><td>an operator, permission 2</td><td>either</td></tr>
 * </table>
 *
 * <p>A player who wants to be in another year <b>lives through it</b>: the clock runs, or they find a
 * story that takes them there. That is not a limitation to be worked around, it is the only version of
 * this that is not a second game.
 *
 * <h2>What going back means, which is the part worth saying carefully</h2>
 *
 * <p>Going back does not un-happen anything. A recorded fact is recorded: the town pack says Tenochtitlan
 * held 212,500 people in 1500 and it will say so again when the clock returns to 1500. What going back
 * does is <b>fork the world</b>: from the moment of the jump, anything the player changes is theirs and is
 * marked {@link com.eternalengine.timeline.Provenance#AUTHORED}, which is exactly the rule the chronicler
 * already enforces on the recorded side of the seam.
 *
 * <p>Crossing the seam (AD 2026) is therefore a change of authority rather than a change of scenery:
 * before it, the packs are the record and the player's changes are annotations on it; after it, the
 * chronicle <b>is</b> the record, because there is nothing else.
 *
 * <p><b>This class touches no Minecraft</b>, so the policy can be checked on a plain JDK, in the same way
 * the guardrail's {@link Projector} can.
 */
public final class TimeTravel {

    /** Who is asking to move the clock. */
    public enum Who {
        /** The clock running on its own. Only ever forward. */
        CLOCK("the clock"),
        /** A player with no command: may not move it at all. */
        PLAYER("a player"),
        /** An operator with {@code /ee date}: may move it either way. */
        OPERATOR("an operator");

        private final String words;

        Who(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /**
     * What a jump does to the world, which is not always the same thing. The first run of the part 5
     * probe found this enum wrong: it had two names for one case (coming back to recorded years) and no
     * name at all for going back inside this world's own unwritten history.
     */
    public enum Effect {
        /** Nothing at all: the same year. */
        STAYS,
        /** Forward through recorded years: the packs still answer. */
        FORWARD_RECORDED,
        /** Forward past the seam: the chronicle becomes the only record. */
        FORWARD_UNWRITTEN,
        /** Back to a year the packs own: the record resumes, and the world forks. */
        BACK_INTO_RECORD,
        /** Back inside this world's own history, where nothing was ever recorded: no fork. */
        BACK_UNWRITTEN
    }

    /** The answer, and the sentence a player reads. */
    public record Verdict(boolean allowed, Effect effect, int from, int to, boolean forks,
                          String because) {
        public String words() {
            return (allowed ? "yes: " : "no: ") + because;
        }

        /** True when the jump crosses the seam in either direction. */
        public boolean crossesSeam() {
            boolean fromRecorded = from <= Timeline.RECORDED_UNTIL;
            boolean toRecorded = to <= Timeline.RECORDED_UNTIL;
            return fromRecorded != toRecorded;
        }

        /** True when a jump leaves years that nobody lived through, and wrote nothing about. */
        public boolean leavesGap() {
            return crossesSeam();
        }
    }

    private TimeTravel() {
    }

    // ------------------------------------------------------------------ the judgement

    /** What a jump from one year to another does to the world. */
    public static Effect effectOf(int from, int to) {
        if (from == to) return Effect.STAYS;
        boolean toRecorded = to <= Timeline.RECORDED_UNTIL;
        if (to > from) return toRecorded ? Effect.FORWARD_RECORDED : Effect.FORWARD_UNWRITTEN;
        return toRecorded ? Effect.BACK_INTO_RECORD : Effect.BACK_UNWRITTEN;
    }

    /**
     * May the clock be moved? The order of the checks is the order of what cannot be argued with: the
     * world's own span, then who is asking, then the direction, and only then what it means.
     */
    public static Verdict judge(int from, int to, Who who) {
        if (who == null) return no(Effect.STAYS, "nobody asked");
        if (!Timeline.inSpan(to)) {
            return no(effectOf(from, to), "this world runs from " + Timeline.FIRST_YEAR + " to AD "
                    + Timeline.LAST_YEAR + ", and " + to + " is outside it. There is no pack for it and "
                    + "no sky, no towns and no people, so it would be an empty room with a date on it.");
        }
        Effect effect = effectOf(from, to);
        if (effect == Effect.STAYS) {
            return new Verdict(true, effect, from, to, false, "it is already " + to);
        }
        if (who == Who.PLAYER) {
            return no(effect, "a player cannot move the clock. It moves forward on its own, and going "
                    + "back is an operator's command, because one date serves every player in the world "
                    + "and one player cannot move it for everybody.");
        }
        if (who == Who.CLOCK && to < from) {
            return no(effect, "the clock only runs forward. Nothing in this world turns it back by "
                    + "itself, and a clock that could would be a second author of history.");
        }
        boolean forks = effect == Effect.BACK_INTO_RECORD;
        return new Verdict(true, effect, from, to, forks, plain(effect, from, to));
    }

    private static Verdict no(Effect effect, String why) {
        return new Verdict(false, effect, 0, 0, false, why);
    }

    /** The sentence for an allowed jump. It says what changes for the player, which is the useful part. */
    public static String plain(Effect effect, int from, int to) {
        int years = Math.abs(to - from);
        return switch (effect) {
            case STAYS -> "it is already " + to;
            case FORWARD_RECORDED -> "forward " + years + " year" + (years == 1 ? "" : "s") + ", still "
                    + "inside the records: the packs answer for " + to + " as they did for " + from
                    + ", and nothing needs writing down.";
            case FORWARD_UNWRITTEN -> "forward " + years + " year" + (years == 1 ? "" : "s") + ", past "
                    + Timeline.RECORDED_UNTIL + ". Nothing is recorded there, so from now on this world "
                    + "writes its own history and the chronicle is the only record there is.";
            case BACK_INTO_RECORD -> "back " + years + " year" + (years == 1 ? "" : "s") + ", into the "
                    + "record. The packs answer again for " + to + ", nothing you saw is undone and "
                    + "nobody is un-met: this world forks, and every change you make from here is "
                    + "marked as yours.";
            case BACK_UNWRITTEN -> "back " + years + " year" + (years == 1 ? "" : "s") + ", inside this "
                    + "world's own history. Nothing is recorded there either way, so nothing forks: "
                    + "you are only going back over what this world already lived.";
        };
    }

    // ------------------------------------------------------------------ what the player is told they can do

    /**
     * The honest list of ways to be in another year, longest-winded first, because the slow way is the
     * way the game is meant to be played.
     */
    public static List<String> doorways() {
        List<String> out = new ArrayList<>();
        out.add("let the clock run: it moves forward on its own, and a year takes whatever the server is "
                + "set to");
        out.add("travel: a ship, a road or a caravan gets you somewhere else in the year you are in, "
                + "which is not time travel but is most of what people mean by it");
        out.add("read and be told: the chronicle, the knowledge cards and the gazetteer all answer for "
                + "another year without moving anybody");
        out.add("/ee date <year> <month> <day>, if you are an operator: the only door that moves the "
                + "clock, and it moves it for everyone");
        return out;
    }

    /** The refusals, each one a thing somebody will ask for. */
    public enum Refused {
        A_CLOCK_PER_PLAYER("Two players in different years would need two different villages at the same "
                + "coordinates, and this project has one pack per place and year, so one of them would "
                + "be standing in a place the data does not describe."),
        TWO_YEARS_IN_ONE_PLACE("A town, a ruler, a price and a crowd all answer for one year at a time. A "
                + "second year in the same square would be a second world."),
        UNDOING_A_DEATH("Going back does not un-happen a death. It forks the world, and the person is "
                + "alive in the fork and dead in the one you left."),
        A_FREE_MACHINE("The time machine item is a prop. It prints the year and points at the one door, "
                + "and it is not a second door, because two doors to the same clock is how a save "
                + "corrupts."),
        MEETING_YOURSELF("Nobody is duplicated, so there is nobody to meet. The world moved, not you.");

        private final String why;

        Refused(String why) {
            this.why = why;
        }

        public String why() {
            return why;
        }
    }

    /** One line, for a screen or a chat message, that says what the clock is and who may move it. */
    public static String ruleLine(int year) {
        return "It is " + Timeline.label(year) + " for everyone. The clock runs forward; "
                + "/ee date moves it, and only an operator may do that.";
    }

    /**
     * What the chronicle should know about a jump, or {@code null} when there is nothing to say. A jump
     * is not a change to history, so this deliberately returns <b>nothing to record</b>: the recorder
     * writes what the world did, and the clock is not the world. The year gap is the caller's business.
     */
    @Nullable
    public static String chronicleNote(Effect effect) {
        return null;    // stated once, for all five cases: the clock is not the world
    }

    /**
     * True when a jump crosses the seam, and therefore leaves years that nobody lived through and wrote
     * nothing about. Inside either side of the seam a jump leaves no such gap.
     */
    public static boolean leavesGap(Effect effect) {
        return effect == Effect.FORWARD_UNWRITTEN || effect == Effect.BACK_INTO_RECORD;
    }
}
