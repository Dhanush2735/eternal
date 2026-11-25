package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.eternalengine.player.InWorldInterventionDetector;

/**
 * <b>Who can do what.</b> F1's ten ways, judged against the world a player is actually standing in.
 *
 * <h2>Why this class exists</h2>
 *
 * Step 48 part 2 said what a change <em>is</em> and what it <em>costs</em> in actions. It did not say
 * whether the player standing in front of it can do it at all. A sharecropper in a village of two
 * hundred cannot start a war, and not because it costs nine actions: because there is no army within
 * a hundred miles and no way to get one this year.
 *
 * <p>So every way is judged against a {@link Situation}, which is the four or five things that
 * actually decide it: how many people are here, what standing the player has among them, how many
 * follow them, whether there is a workshop, and how much money they carry. Nothing else is consulted,
 * because nothing else in this project is measured well enough to consult.
 *
 * <h2>The one list this class does not own</h2>
 *
 * <p>The rule that a player may stop a battle and may not stop an earthquake is not written here. It
 * is already written in {@link InWorldInterventionDetector#STOPPABLE_KINDS} and
 * {@link InWorldInterventionDetector#REFUSED_KINDS}, which is the class that has been weighing real
 * player actions since the branch machinery was built. This class <b>reads those sets</b> rather than
 * copying them, so there is one list of what a person can interrupt and not two that drift apart.
 */
public final class Agency {

    /** How much of a say the player has among the people here. Ordered, so "at least" works. */
    public enum Standing {
        /** Passing through. Nobody here knows the name. */
        OUTSIDER("a stranger passing through"),
        /** Lives here long enough to be known. */
        RESIDENT("a resident, known by name"),
        /** Has money, a household, or a name people act on. */
        NOTABLE("a notable of the place"),
        /** Holds an office: a captain, a magistrate, a priest. */
        OFFICE("an officer of the place"),
        /** Holds the place. */
        RULER("its ruler");

        private final String words;

        Standing(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }

        public boolean atLeast(Standing other) {
            return ordinal() >= other.ordinal();
        }
    }

    /**
     * The world as it bears on this player. Deliberately small: five fields, each of them something
     * the pack already knows or the simulation already counts.
     */
    public record Situation(int year, String place, long peopleHere, Standing standing, int followers,
                            boolean workshop, long purse) {
        public Situation {
            place = place == null || place.isEmpty() ? "here" : place;
            standing = standing == null ? Standing.OUTSIDER : standing;
            peopleHere = Math.max(0L, peopleHere);
            followers = Math.max(0, followers);
            purse = Math.max(0L, purse);
        }

        /** A stranger who just arrived. The floor, and the situation most players start in. */
        public static Situation newcomer(int year, String place, long peopleHere) {
            return new Situation(year, place, peopleHere, Standing.OUTSIDER, 0, false, 0L);
        }
    }

    /** How a way answers. */
    public enum Answer {
        /** Possible now. */
        YES,
        /** Possible after getting something: followers, money, an office. */
        NEEDS,
        /** Not possible for this player here, whatever they collect. */
        NO
    }

    /** An answer with the sentence that explains it, because a bare no is useless in a world. */
    public record Verdict(Answer answer, String because) {
        public boolean allowed() {
            return answer == Answer.YES;
        }

        public String words() {
            return switch (answer) {
                case YES -> "yes: " + because;
                case NEEDS -> "not yet: " + because;
                case NO -> "no: " + because;
            };
        }
    }

    private Agency() {
    }

    // ------------------------------------------------------------------ the rules

    /** How many people must be within reach for a way to be possible at all, whatever else is true. */
    public static long peopleNeeded(ChangeRecord.Way way) {
        return switch (way) {
            case KILL_OR_SAVE_A_PERSON -> 1L;        // you and them
            case MARRY -> 2L;                        // you and them
            case TELL_PEOPLE_THE_FUTURE -> 2L;       // somebody to tell
            case FOUND_A_TOWN -> 20L;                // twenty who will stay
            case SABOTAGE_OR_EMPTY_A_PLACE -> 50L;    // something to sabotage
            case TRADE_OR_SABOTAGE_GOODS -> 50L;      // a market
            case STOP_AN_EVENT -> 200L;               // a side to take
            case START_OR_ESCALATE_A_WAR -> 1000L;    // two sides, so a thousand people between them
            case TAKE_THE_CROWN -> 1000L;             // a crown needs a country of some kind
            case BRING_AN_INVENTION_EARLY -> 2000L;   // a workshop needs a town to sell to
        };
    }

    /** Followers required, if the way is about moving people. */
    public static int followersNeeded(ChangeRecord.Way way) {
        return switch (way) {
            case FOUND_A_TOWN -> 20;
            case START_OR_ESCALATE_A_WAR -> 50;
            case TAKE_THE_CROWN -> 100;
            case STOP_AN_EVENT, SABOTAGE_OR_EMPTY_A_PLACE -> 5;
            default -> 0;
        };
    }

    /** The least standing the player must have. */
    public static Standing standingNeeded(ChangeRecord.Way way) {
        return switch (way) {
            case KILL_OR_SAVE_A_PERSON, TELL_PEOPLE_THE_FUTURE, TRADE_OR_SABOTAGE_GOODS ->
                    Standing.OUTSIDER;
            case MARRY, FOUND_A_TOWN, BRING_AN_INVENTION_EARLY -> Standing.RESIDENT;
            case STOP_AN_EVENT, SABOTAGE_OR_EMPTY_A_PLACE -> Standing.RESIDENT;
            case START_OR_ESCALATE_A_WAR -> Standing.NOTABLE;
            case TAKE_THE_CROWN -> Standing.OFFICE;
        };
    }

    /** Money needed, in the purse the player carries. */
    public static long purseNeeded(ChangeRecord.Way way) {
        return switch (way) {
            case TRADE_OR_SABOTAGE_GOODS -> 1L;
            case BRING_AN_INVENTION_EARLY -> 20L;
            case FOUND_A_TOWN -> 10L;
            case STOP_AN_EVENT -> 5L;
            default -> 0L;
        };
    }

    /** True if the way cannot be done without a workshop of some kind. */
    public static boolean needsWorkshop(ChangeRecord.Way way) {
        return way == ChangeRecord.Way.BRING_AN_INVENTION_EARLY;
    }

    // ------------------------------------------------------------------ the judgement

    /**
     * Judge one way against one situation. The order matters and is deliberate: the reasons that
     * cannot be argued with come first, so the answer says <b>no</b> to a player in an empty valley
     * rather than "not yet, collect fifty followers".
     */
    public static Verdict judge(ChangeRecord.Way way, Situation s) {
        if (way == null || s == null) return new Verdict(Answer.NO, "no way and no world");

        long people = peopleNeeded(way);
        if (s.peopleHere() < peopleHere(way)) {
            return new Verdict(Answer.NO, "this needs about " + people + " people within reach and "
                    + s.place() + " has " + s.peopleHere() + " in " + s.year()
                    + ". That is not a rule, it is arithmetic.");
        }
        if (!s.standing().atLeast(standingNeeded(way))) {
            return new Verdict(Answer.NEEDS, "a stranger cannot do this here; it wants "
                    + standingNeeded(way).words() + ", and you are " + s.standing().words());
        }
        if (s.followers() < followersNeeded(way)) {
            return new Verdict(Answer.NEEDS, "it wants " + followersNeeded(way)
                    + " people who will come when you call, and " + s.followers() + " will");
        }
        if (needsWorkshop(way) && !s.workshop()) {
            return new Verdict(Answer.NEEDS, "it wants a workshop: somebody who can make the thing, "
                    + "not only somebody who has heard of it");
        }
        if (s.purse() < purseNeeded(way)) {
            return new Verdict(Answer.NEEDS, "it costs money you do not have: " + purseNeeded(way)
                    + " and you carry " + s.purse());
        }
        return new Verdict(Answer.YES, "everything it needs is here: " + s.peopleHere() + " people, "
                + s.standing().words() + (s.followers() > 0 ? ", " + s.followers() + " behind you" : "")
                + ", " + ChangeRecord.costFor(way.what(), way.how()) + " actions of work");
    }

    /** The people floor for a way, kept in one place so the message and the test cannot disagree. */
    public static long peopleHere(ChangeRecord.Way way) {
        return peopleNeeded(way);
    }

    /** Every way that can be done right now. */
    public static List<ChangeRecord.Way> open(Situation s) {
        List<ChangeRecord.Way> out = new ArrayList<>();
        for (ChangeRecord.Way w : ChangeRecord.Way.values()) {
            if (judge(w, s).allowed()) out.add(w);
        }
        return out;
    }

    /** Every way that is only waiting for something, with what it is waiting for implied by its reason. */
    public static List<ChangeRecord.Way> withinReach(Situation s) {
        List<ChangeRecord.Way> out = new ArrayList<>();
        for (ChangeRecord.Way w : ChangeRecord.Way.values()) {
            if (judge(w, s).answer() == Answer.NEEDS) out.add(w);
        }
        return out;
    }

    /** Every way that is impossible here, whatever the player collects. */
    public static List<ChangeRecord.Way> impossible(Situation s) {
        List<ChangeRecord.Way> out = new ArrayList<>();
        for (ChangeRecord.Way w : ChangeRecord.Way.values()) {
            if (judge(w, s).answer() == Answer.NO) out.add(w);
        }
        return out;
    }

    /** The ways that cost the fewest actions, among those that are open. */
    public static List<ChangeRecord.Way> cheapest(Situation s) {
        List<ChangeRecord.Way> open = open(s);
        open.sort((a, b) -> Integer.compare(a.cost(), b.cost()));
        return open;
    }

    // ------------------------------------------------------------------ what nobody can do

    /**
     * The events a player may try to stop or start: the detector's own list, read and not copied.
     * Anything not in this set cannot be touched, and that includes every kind in
     * {@link InWorldInterventionDetector#REFUSED_KINDS}.
     */
    public static Set<String> interruptible() {
        return new LinkedHashSet<>(InWorldInterventionDetector.STOPPABLE_KINDS);
    }

    /** The events nobody may touch. Kept as a reading of the detector, for the same reason. */
    public static Set<String> untouchable() {
        return new LinkedHashSet<>(InWorldInterventionDetector.REFUSED_KINDS);
    }

    /**
     * May a player change an event of this kind? A kind that is neither interruptible nor refused is
     * <b>no</b>: the list is the permission, so a kind nobody wrote down is not silently allowed.
     */
    public static Verdict judgeEventKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return new Verdict(Answer.NO, "no kind of event, so nothing to change");
        }
        String k = kind.toLowerCase(Locale.ROOT);
        if (untouchable().contains(k)) {
            return new Verdict(Answer.NO, "a " + k + " is not something a person causes, so a person "
                    + "cannot stop it either. The weather and the ground are not players.");
        }
        if (interruptible().contains(k)) {
            return new Verdict(Answer.YES, "a " + k + " is people doing something, and people can be "
                    + "argued with, bought, blocked or fought");
        }
        return new Verdict(Answer.NO, "nothing in this project knows a \"" + k + "\" as something a "
                + "player can change, and an unlisted kind is not a permission");
    }

    /** The two lists as one line, for a screen or a log. */
    public static String refusalLine() {
        return "You can touch " + String.join(", ", interruptible()) + ". You cannot touch "
                + String.join(", ", untouchable()) + ".";
    }
}
