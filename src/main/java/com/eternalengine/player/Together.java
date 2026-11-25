package com.eternalengine.player;

import com.eternalengine.player.Permissions.Action;
import com.eternalengine.player.Permissions.Actor;
import com.eternalengine.player.Permissions.Decision;
import com.eternalengine.player.Permissions.Rank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Several players: shared or separate timelines, and who may change history.</b> F7.
 *
 * <h2>Why this class exists</h2>
 *
 * The mod knows how to run a world for one date and one timeline (step 52's {@link Clocks}) and who may ask
 * what ({@link Permissions}). It does not answer the question F7 asks, which is about the <b>pair</b>: do these
 * two players share a world or not, and when they both want to change history, whose word is enough?
 *
 * <h2>Shared or separate</h2>
 *
 * <p>Two players share a world when they stand on the same universe <b>and</b> the same timeline. Then an act
 * by either touches both, their property books are one book, and a vote about that world is theirs to make.
 * Any other pairing is separate: neither one's acts touch the other's world, and there is nothing to
 * arbitrate. There is no third state and no partial sharing: a half-shared world is a world where one player's
 * build vanishes under another's, which is the bug this rule exists to prevent.
 *
 * <h2>Who may change history: two axes, both must say yes</h2>
 *
 * <p>Step 48 built the in-world axis: {@code timeline.Agency} judges the ten ways to change history against a
 * player's standing in the world (a sharecropper cannot start a war). {@link Permissions} built the server
 * axis: operator, votes, what moves the shared world. A change to history that lasts needs <b>both</b>. This
 * class is the place they meet: it takes each axis's answer and says yes only when both do, naming whichever
 * one said no. It never re-derives either answer, because neither axis is its business.
 *
 * <h2>Two people changing the same thing</h2>
 *
 * <p>When players share a world and both act on one thing (one block, one deed, one name in the record), the
 * class keeps the <b>first</b> touch and refuses the second, naming who is already there. The world is not
 * asked to pick between them; the players settle it or an operator forces it, and a forced change records who
 * forced it and why. This is the same discipline as block edits in a shared Minecraft world: first come, first
 * served, and the refusals are in words.
 */
public final class Together {

    /** <b>One player's standing.</b> The world they stand in, and what each axis calls them. */
    public record Player(String name, String universe, String branch, int year, Rank rank,
                         String inWorldStanding) {

        public Player {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("a player with no name is nobody");
            }
            name = name.trim();
        }

        public String line() {
            return name + " · " + universe + "/" + branch + " · " + year + " · " + rank.words()
                    + " · " + (inWorldStanding == null || inWorldStanding.isEmpty()
                    ? "nothing in particular" : inWorldStanding);
        }
    }

    /** <b>Whether two players share a world.</b> And why, every time, in words. */
    public record Pair(Player a, Player b, boolean share, String why) {
        public String line() {
            return a.name() + " and " + b.name() + ": " + why;
        }
    }

    /**
     * <b>One touch of one thing in one world.</b> The class keeps these while they are live, so a second
     * hand on the same thing can be refused by name.
     */
    public record Touch(String who, String universe, String branch, String what, int year, String why) {
        public String line() {
            return who + " · " + universe + "/" + branch + " · " + what + " · " + year
                    + (why == null || why.isEmpty() ? "" : " · " + why);
        }
    }

    private final List<Touch> live = new ArrayList<>();

    public static Together empty() {
        return new Together();
    }

    // ------------------------------------------------------------------ shared or separate

    /** Do these two share a world? The question F7 is built on. */
    public static Pair share(Player a, Player b) {
        if (a == null || b == null) {
            return new Pair(a, b, false, "one of them is nobody, and nobody shares nothing");
        }
        if (a.name().equalsIgnoreCase(b.name())) {
            return new Pair(a, b, true, a.name() + " is one person, and one person agrees with themselves");
        }
        boolean sameUniverse = a.universe().equalsIgnoreCase(b.universe());
        boolean sameBranch = a.branch().equalsIgnoreCase(b.branch());
        if (sameUniverse && sameBranch) {
            return new Pair(a, b, true, a.name() + " and " + b.name() + " share " + a.universe() + "/"
                    + a.branch() + " in " + a.year() + ": an act by either touches both");
        }
        if (sameUniverse) {
            return new Pair(a, b, false, a.name() + " is on " + a.branch() + " and " + b.name() + " on "
                    + b.branch() + ": separate timelines of one universe, and neither's acts touch the other");
        }
        return new Pair(a, b, false, a.name() + " is in " + a.universe() + " and " + b.name() + " in "
                + b.universe() + ": separate worlds, and neither's acts touch the other");
    }

    /**
     * <b>Who must agree before this world moves?</b> The roll call of a vote: the people it moves, nobody
     * else. {@link Permissions} decides what the vote must reach; this list is who counts.
     */
    public static List<Player> rollCall(List<Player> players, String universe) {
        List<Player> out = new ArrayList<>();
        if (players == null || universe == null) {
            return out;
        }
        for (Player p : players) {
            if (p != null && p.universe().equalsIgnoreCase(universe.trim())) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the two axes

    /**
     * <b>Who may change history.</b> Both axes must say yes: the in-world one (may their standing attempt
     * this change?) and the server one (may they alter this shared world?). This class names whichever said
     * no and never invents a third opinion.
     */
    public static Decision mayChangeHistory(Player who, Decision inWorldAxis, Decision serverAxis) {
        if (who == null) {
            return Decision.no("nobody cannot change history");
        }
        if (inWorldAxis == null || serverAxis == null) {
            return Decision.no("a question with one axis unanswered is not answered here: both the standing "
                    + "in the world and the right on the server must have their say");
        }
        if (inWorldAxis.allowed() && serverAxis.allowed()) {
            return Decision.yes(who.name() + " may change history: " + inWorldAxis.why() + "; "
                    + serverAxis.why());
        }
        List<String> nos = new ArrayList<>();
        if (!inWorldAxis.allowed()) {
            nos.add("the world says no (" + inWorldAxis.why() + ")");
        }
        if (!serverAxis.allowed()) {
            nos.add("the server says no (" + serverAxis.why() + ")");
        }
        return Decision.no(who.name() + " may not change history: " + String.join("; ", nos));
    }

    // ------------------------------------------------------------------ the same thing, two hands

    /**
     * <b>Put a hand on one thing.</b> Refused while somebody else's hand is already on it, naming them. The
     * world does not pick between two players; the second is told who is first and what they are doing.
     */
    public Decision touch(Actor by, String universe, String branch, String what, int year, String why) {
        if (by == null || by.name() == null || by.name().trim().isEmpty()) {
            return Decision.no("nobody cannot touch anything");
        }
        if (universe == null || universe.trim().isEmpty() || branch == null || branch.trim().isEmpty()) {
            return Decision.no("say which world the thing is in: a universe and a timeline");
        }
        if (what == null || what.trim().isEmpty()) {
            return Decision.no("say which thing is being touched");
        }
        Touch found = find(universe, branch, what);
        if (found != null && !found.who().equalsIgnoreCase(by.name().trim())) {
            return Decision.no(found.who() + " is already on " + what.trim() + " in " + universe.trim()
                    + "/" + branch.trim() + " (" + found.line() + "); settle it between you or ask an "
                    + "operator to force it");
        }
        if (found != null) {
            return Decision.yes(by.name() + " is already on " + what.trim() + " and carries on ("
                    + found.line() + ")");
        }
        live.add(new Touch(by.name().trim(), universe.trim(), branch.trim(), what.trim(), year,
                why == null ? "" : why.trim()));
        return Decision.yes(by.name() + " takes " + what.trim() + " in " + universe.trim() + "/"
                + branch.trim());
    }

    /** <b>Take your hand off.</b> The one who touched it may; so may an operator. */
    public Decision settle(Actor by, String universe, String branch, String what) {
        Touch found = find(universe, branch, what);
        if (found == null) {
            return Decision.no("nothing called " + (what == null ? "?" : what.trim()) + " is being touched "
                    + "in " + (universe == null ? "?" : universe.trim()) + "/"
                    + (branch == null ? "?" : branch.trim()));
        }
        if (by == null || (by.name() == null || !by.name().trim().equalsIgnoreCase(found.who()))
                && (by == null || !by.isOperator())) {
            return Decision.no(found.who() + " is on " + found.what() + "; only they or an operator can "
                    + "settle it");
        }
        live.remove(found);
        return Decision.yes((by == null ? "?" : by.name()) + " settles " + found.what() + " in "
                + found.universe() + "/" + found.branch());
    }

    /**
     * <b>An operator forces it.</b> This is the one way the world picks between two players, and it says who
     * picked and why, because a force that leaves no name behind is a force nobody can question later.
     */
    public Decision force(Actor by, String universe, String branch, String what, String why) {
        if (by == null || !by.isOperator()) {
            return Decision.no((by == null ? "nobody" : by.name()) + " is not " + Rank.OPERATOR.words()
                    + " and cannot force a settle");
        }
        Touch found = find(universe, branch, what);
        if (found == null) {
            return Decision.no("nothing called " + (what == null ? "?" : what.trim()) + " is being touched "
                    + "to force");
        }
        if (why == null || why.trim().isEmpty()) {
            return Decision.no("a forced settle must say why: whose hand is being lifted and on what grounds");
        }
        live.remove(found);
        return Decision.yes(by.name() + " forces " + found.who() + " off " + found.what() + " in "
                + found.universe() + "/" + found.branch() + ": " + why.trim());
    }

    /** Everything with a live hand on it, in the order the hands were laid. */
    public List<Touch> live() {
        return List.copyOf(live);
    }

    /** The page a group of players reads: who stands where, and who is on what. */
    public static List<String> page(List<Player> players) {
        List<String> out = new ArrayList<>();
        out.add(players == null ? "0 players" : players.size() + (players.size() == 1
                ? " player" : " players"));
        if (players != null) {
            for (Player p : players) {
                out.add("  " + p.line());
            }
            for (int i = 0; i < players.size(); i++) {
                for (int j = i + 1; j < players.size(); j++) {
                    out.add("  " + share(players.get(i), players.get(j)).line());
                }
            }
        }
        return out;
    }

    public static String ruleLine() {
        return "Two players share a world or they do not: one universe, one timeline. Changing history takes "
                + "both axes saying yes; two hands on one thing keep the first and refuse the second by name; "
                + "and only an operator forces it, on the record.";
    }

    // ------------------------------------------------------------------ internals

    private Touch find(String universe, String branch, String what) {
        if (universe == null || branch == null || what == null) {
            return null;
        }
        for (Touch t : live) {
            if (t.universe().equalsIgnoreCase(universe.trim())
                    && t.branch().equalsIgnoreCase(branch.trim())
                    && t.what().equalsIgnoreCase(what.trim())) {
                return t;
            }
        }
        return null;
    }
}
