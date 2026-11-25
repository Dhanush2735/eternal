package com.eternalengine.player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Who may move time, change history or use the admin commands, when several people play.</b> A16.
 *
 * <h2>Why this class exists</h2>
 *
 * Every command in the mod gates on one blunt test: Minecraft's operator level 2. That answers "is this
 * person an operator" and nothing else. It cannot answer the question the plan actually asks: <em>this act
 * moves other people's world, do their wishes count?</em>
 *
 * <p>Step 48 built the other axis already. {@code timeline.PlayerRoles} says what a person is in the world
 * (a merchant, a ruler) and {@code timeline.Agency} says which of the ten ways to change history their
 * standing allows. That axis is about being somebody <b>in</b> the history. This class is about the server:
 * who may move the shared date, switch the shared timeline, restore a backup, or run admin commands. A change
 * to history that lasts needs <b>both</b> axes to say yes, and this class never answers the other one.
 *
 * <h2>The rule, from decision 3</h2>
 *
 * <blockquote>One shared date and timeline per world; time travel moves the world's date <b>for everyone</b>,
 * by the same allowed path as {@code /ee date}, <b>with a vote or operator permission when several people
 * play</b>.</blockquote>
 *
 * So there are exactly three kinds of act, and the class says which kind every action is:
 *
 * <ol>
 *   <li><b>Acts that move only you.</b> Entering another universe, claiming what you stand on, taking a copy
 *       before a change. Anyone may, any time. Their wishes are the only ones involved.</li>
 *   <li><b>Acts that move the shared world.</b> The date, the timeline, a split. Alone, you are the only one
 *       moved and you may. With several people moved, it takes <b>their vote</b> (a strict majority of the
 *       people it moves, not of whoever is online) or an operator.</li>
 *   <li><b>Acts that rewrite what is already recorded.</b> Restoring a backup, admin commands, editing the
 *       record itself, editing another's property. Operators only, alone or not: a stranger alone on a server
 *       must not be able to rewrite the past of the world they are about to leave.</li>
 * </ol>
 *
 * <h2>What a vote is</h2>
 *
 * <p>A vote names the act, the world, and the people it moves. Only those people may cast. A vote passes when
 * more than half of the people it moves say yes; anything less is refused and says how short it fell. An
 * operator may always act without a vote, and may close a vote by hand, and both leave the reason in the
 * record of the vote. Nothing here decides by a show of hands among bystanders.
 */
public final class Permissions {

    /** <b>The acts this class judges.</b> Each one names what it moves, in words a player can read. */
    public enum Action {
        ENTER_UNIVERSE("enter another universe", Kind.ONLY_YOU),
        CLAIM_PROPERTY("claim what you stand on", Kind.ONLY_YOU),
        TAKE_BACKUP("take a copy before a change", Kind.ONLY_YOU),
        MOVE_WORLD_TIME("move the shared date", Kind.MOVES_EVERYONE),
        MOVE_WORLD_BRANCH("switch the shared timeline", Kind.MOVES_EVERYONE),
        SPLIT_TIMELINE("split the shared timeline", Kind.MOVES_EVERYONE),
        RESTORE_BACKUP("put back a copy taken before a change", Kind.REWRITES),
        USE_ADMIN("use the admin commands", Kind.REWRITES),
        CHANGE_HISTORY("edit the record of what happened", Kind.REWRITES),
        EDIT_ANY_PROPERTY("edit another player's property", Kind.REWRITES),
        VOTE("say yes or no in a vote", Kind.ONLY_YOU);

        /** What an act moves. See the class doc: the three kinds are the whole rule. */
        public enum Kind {
            ONLY_YOU, MOVES_EVERYONE, REWRITES
        }

        private final String words;
        private final Kind kind;

        Action(String words, Kind kind) {
            this.words = words;
            this.kind = kind;
        }

        public String words() {
            return words;
        }

        public Kind kind() {
            return kind;
        }
    }

    /** <b>How Minecraft knows you.</b> Operator level 2 and everyone else; nothing finer lives here. */
    public enum Rank {
        OPERATOR("an operator"),
        PLAYER("a player");

        private final String words;

        Rank(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>Who is asking.</b> A name for the record and a rank for the rule. */
    public record Actor(String name, Rank rank) {

        public static Actor of(String name, boolean operator) {
            return new Actor(name, operator ? Rank.OPERATOR : Rank.PLAYER);
        }

        public boolean isOperator() {
            return rank == Rank.OPERATOR;
        }
    }

    /** <b>An answer, in words.</b> Every answer of this class carries why, or it is not an answer. */
    public record Decision(boolean allowed, String why) {

        public static Decision yes(String why) {
            return new Decision(true, why);
        }

        public static Decision no(String why) {
            return new Decision(false, why);
        }
    }

    /**
     * <b>May this person do this act by themselves?</b> The count is how many people the act would move;
     * for acts that move only you it is ignored. This is the question before any vote is opened.
     */
    public static Decision mayAlone(Actor actor, Action action, int peopleMoved) {
        if (actor == null || action == null) {
            return Decision.no("there is nobody asking, or no act named");
        }
        switch (action.kind()) {
            case ONLY_YOU:
                return Decision.yes(actor.name() + " may " + action.words() + ": it moves nobody else");
            case REWRITES:
                if (actor.isOperator()) {
                    return Decision.yes(actor.name() + " is " + Rank.OPERATOR.words()
                            + " and may " + action.words());
                }
                return Decision.no(action.words().charAt(0) + "" + action.words().substring(1)
                        + " is for operators only: it rewrites what is already recorded, and "
                        + actor.name() + " is " + actor.rank().words());
            case MOVES_EVERYONE:
                if (actor.isOperator()) {
                    return Decision.yes(actor.name() + " is " + Rank.OPERATOR.words() + " and may "
                            + action.words() + " without asking");
                }
                if (peopleMoved <= 1) {
                    return Decision.yes(actor.name() + " may " + action.words()
                            + " alone: nobody else is moved by it");
                }
                return Decision.no(action.words() + " moves " + peopleMoved + " people, so it takes their vote"
                        + " or an operator, and " + actor.name() + " is " + actor.rank().words());
            default:
                return Decision.no("the act is of a kind this class has no rule for");
        }
    }

    /**
     * <b>A vote of the people an act would move.</b> Not of whoever is online: of the people the act moves,
     * because the question is their world being altered, not their opinion.
     */
    public static final class Vote {
        private final Action action;
        private final String world;
        private final Actor proposer;
        private final Set<String> affected = new LinkedHashSet<>();
        private final Map<String, Boolean> cast = new LinkedHashMap<>();
        private final List<String> log = new ArrayList<>();
        private boolean closed;
        private boolean passed;
        private String outcome = "";

        private Vote(Action action, String world, Actor proposer, List<String> affected) {
            this.action = action;
            this.world = world;
            this.proposer = proposer;
            this.affected.addAll(affected);
            log.add(proposer.name() + " asked to " + action.words() + " in " + world + ", which moves "
                    + affected.size() + " people: " + String.join(", ", affected));
        }

        /**
         * <b>The outcome of opening a vote.</b> Either a vote, or the reason there is none: a vote that needs
         * no vote is not a vote, and a vote of nobody decides nothing.
         */
        public record Opened(Vote vote, String refused) {
            public boolean ok() {
                return vote != null;
            }
        }

        /**
         * <b>Open a vote.</b> Refused if the act needs no vote (it moves only the proposer, or the proposer is
         * an operator who may simply act), if there is nobody moved by it, or if the same name is listed twice.
         */
        public static Opened open(Action action, String world, Actor proposer, List<String> affected) {
            if (action == null || proposer == null) {
                return new Opened(null, "there is no act or no proposer to open a vote for");
            }
            if (affected == null || affected.isEmpty()) {
                return new Opened(null, "a vote of nobody decides nothing: name the people the act moves");
            }
            Set<String> names = new LinkedHashSet<>();
            for (String n : affected) {
                if (n == null || n.trim().isEmpty()) {
                    return new Opened(null, "the list of people moved holds an empty name");
                }
                if (!names.add(n.trim().toLowerCase(java.util.Locale.ROOT))) {
                    return new Opened(null, n + " is listed twice among the people this act moves");
                }
            }
            if (action.kind() == Action.Kind.ONLY_YOU) {
                return new Opened(null, action.words() + " moves nobody but " + proposer.name()
                        + ", so there is nothing to vote about");
            }
            if (action.kind() == Action.Kind.REWRITES) {
                return new Opened(null, action.words()
                        + " is for operators only; a vote cannot hand out what rank withholds");
            }
            if (proposer.isOperator()) {
                return new Opened(null, proposer.name() + " is " + Rank.OPERATOR.words()
                        + " and may " + action.words() + " without a vote");
            }
            if (names.size() == 1 && names.contains(proposer.name().trim().toLowerCase(java.util.Locale.ROOT))) {
                return new Opened(null, action.words() + " moves nobody but " + proposer.name()
                        + ", so they may simply do it");
            }
            List<String> list = new ArrayList<>();
            for (String n : affected) {
                list.add(n.trim());
            }
            return new Opened(new Vote(action, world, proposer, list), "");
        }

        /** <b>Cast one vote.</b> Only the people moved may vote, once each; a second word from one mouth is
         * refused and the first stands. */
        public Decision cast(Actor voter, boolean yes) {
            if (closed) {
                return Decision.no("this vote is closed: " + outcome);
            }
            if (voter == null) {
                return Decision.no("nobody cannot vote");
            }
            String key = voter.name().trim().toLowerCase(java.util.Locale.ROOT);
            boolean moved = false;
            for (String n : affected) {
                if (n.trim().toLowerCase(java.util.Locale.ROOT).equals(key)) {
                    moved = true;
                }
            }
            if (!moved) {
                return Decision.no(voter.name() + " is not moved by this act, and only the "
                        + affected.size() + " people it moves may vote: " + String.join(", ", affected));
            }
            if (cast.containsKey(key)) {
                return Decision.no(voter.name() + " has already voted "
                        + (cast.get(key) ? "yes" : "no") + " and one person has one word");
            }
            cast.put(key, yes);
            log.add(voter.name() + " said " + (yes ? "yes" : "no"));
            return Decision.yes(voter.name() + " said " + (yes ? "yes" : "no") + " ("
                    + cast.size() + " of " + affected.size() + " have voted)");
        }

        /**
         * <b>Close the vote and count it.</b> Refused while somebody moved has not spoken, unless an operator
         * closes it. Passes only on a strict majority of the people it moves: an abstention is not a yes, and
         * a tie is not a majority.
         */
        public Decision close(Actor by) {
            if (closed) {
                return Decision.no("this vote is already closed: " + outcome);
            }
            boolean operatorClose = by != null && by.isOperator();
            if (cast.size() < affected.size() && !operatorClose) {
                return Decision.no((affected.size() - cast.size()) + " of the " + affected.size()
                        + " people this moves have not spoken; wait for them or let an operator close it");
            }
            int yes = 0;
            for (boolean v : cast.values()) {
                if (v) {
                    yes++;
                }
            }
            closed = true;
            int half = affected.size() / 2;
            passed = yes > half;
            if (operatorClose && cast.size() < affected.size()) {
                outcome = by.name() + ", " + Rank.OPERATOR.words() + ", closed it early with " + yes
                        + " of " + affected.size() + " yes";
                passed = true;
                log.add(outcome);
                return Decision.yes(outcome);
            }
            if (passed) {
                outcome = yes + " of " + affected.size() + " said yes, which is more than half of the "
                        + affected.size() + " people it moves";
            } else {
                outcome = yes + " of " + affected.size() + " said yes, which is not more than half of the "
                        + affected.size() + " people it moves";
            }
            log.add(outcome);
            return new Decision(passed, outcome);
        }

        /** <b>An operator ends it.</b> Always allowed for an operator; the reason is kept in the vote's log. */
        public Decision override(Actor by, String why) {
            if (by == null || !by.isOperator()) {
                return Decision.no((by == null ? "nobody" : by.name()) + " is not "
                        + Rank.OPERATOR.words() + " and cannot close a vote by hand");
            }
            if (closed) {
                return Decision.no("this vote is already closed: " + outcome);
            }
            closed = true;
            passed = true;
            outcome = by.name() + ", " + Rank.OPERATOR.words() + ", did it by hand: " + why;
            log.add(outcome);
            return Decision.yes(outcome);
        }

        public boolean isClosed() {
            return closed;
        }

        public boolean passed() {
            return closed && passed;
        }

        public String outcome() {
            return closed ? outcome : "still open: " + cast.size() + " of " + affected.size() + " have voted";
        }

        public List<String> log() {
            return List.copyOf(log);
        }

        public Action action() {
            return action;
        }

        public String world() {
            return world;
        }

        public List<String> affected() {
            return List.copyOf(affected);
        }
    }

    /** The three kinds of act, as a table for the book or an admin screen. */
    public static List<String> table() {
        List<String> out = new ArrayList<>();
        out.add("moves only you, anyone may, any time:");
        for (Action a : Action.values()) {
            if (a.kind() == Action.Kind.ONLY_YOU) {
                out.add("  " + a.words());
            }
        }
        out.add("moves the shared world: alone you may, with several it takes their vote or an operator:");
        for (Action a : Action.values()) {
            if (a.kind() == Action.Kind.MOVES_EVERYONE) {
                out.add("  " + a.words());
            }
        }
        out.add("rewrites what is recorded: operators only, alone or not:");
        for (Action a : Action.values()) {
            if (a.kind() == Action.Kind.REWRITES) {
                out.add("  " + a.words());
            }
        }
        return out;
    }

    public static String ruleLine() {
        return "An act that moves only you is yours; an act that moves the shared world takes the vote of the "
                + "people it moves or an operator; an act that rewrites what is recorded is for operators only.";
    }
}
