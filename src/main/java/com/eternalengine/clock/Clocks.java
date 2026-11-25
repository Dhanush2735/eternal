package com.eternalengine.clock;

import com.eternalengine.player.Permissions;
import com.eternalengine.player.Permissions.Action;
import com.eternalengine.player.Permissions.Actor;
import com.eternalengine.player.Permissions.Decision;
import com.eternalengine.player.Permissions.Vote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Time for several players: different years, timelines and universes at once.</b> A11.
 *
 * <h2>Why this class exists</h2>
 *
 * The survey found one calendar for the whole world: {@code SimulationClockManager} holds a single year and
 * {@code PlayerDataManager} hands that one year to everybody at login. That is the right clock for ONE world,
 * and it stays. What it cannot answer is the plan's question: how can players be in different years at once?
 *
 * <h2>Decision 3, which is the whole design</h2>
 *
 * <blockquote>One shared date and timeline per world; universes are separate dimensions, so players can be in
 * different universes. Time travel moves the world's date <b>for everyone</b>, with a vote or operator
 * permission when several people play. Because Terra++ builds each chunk for one year, two players cannot see
 * two years in the same place.</blockquote>
 *
 * So the answer is not "give every player a private clock". The answer is:
 *
 * <ol>
 *   <li><b>One world has one date and one timeline.</b> Everybody in it is in that year. There is no lawful
 *       private year inside a shared world, and this class refuses to make one.</li>
 *   <li><b>Different years at once means different worlds.</b> A universe is a separate dimension with its own
 *       date and its own branch. Two players in two universes are in two years, lawfully, and neither moves
 *       the other.</li>
 *   <li><b>Moving a world's date moves everyone in it</b>, and takes their vote or an operator
 *       ({@link Permissions}). The vote is of the people moved, not of the server.</li>
 * </ol>
 *
 * <h2>What this class does not decide</h2>
 *
 * <p><em>How</em> a date moves (only {@code /ee date} goes back; the clock follows Minecraft days forward) is
 * F9's business and turn 64's rule. This class only decides <em>who may ask</em>, and holds the standings
 * afterwards. {@code SimulationClockManager} remains the clock of a live world; this class is the arbitration
 * and the table around it.
 */
public final class Clocks {

    /** The whole project's span: 3000 BC to AD 3000, as the user chose. No world's date leaves it. */
    public static final int EARLIEST = -3000;
    public static final int LATEST = 3000;

    /**
     * <b>One universe: a separate dimension with its own date and its own timeline.</b> The players set is
     * everybody standing in it right now.
     */
    public static final class World {
        private final String universe;
        private int year;
        private String branch;
        private final List<String> players = new ArrayList<>();

        private World(String universe, int year, String branch) {
            this.universe = universe;
            this.year = year;
            this.branch = branch;
        }

        public String universe() {
            return universe;
        }

        public int year() {
            return year;
        }

        public String branch() {
            return branch;
        }

        public List<String> players() {
            return List.copyOf(players);
        }
    }

    /** <b>Where somebody stands.</b> One player, one world, at that world's year: that is the rule. */
    public record Standing(String player, String universe, int year, String branch) {
        public String line() {
            return player + " · " + universe + " · " + year + " · " + branch;
        }
    }

    private final Map<String, World> worlds = new LinkedHashMap<>();
    private final Map<String, String> where = new LinkedHashMap<>();

    private Clocks() {
    }

    public static Clocks empty() {
        return new Clocks();
    }

    /**
     * <b>Open a world.</b> A universe that exists is a place a player can enter; opening one moves nobody, so
     * anyone may. A second universe under one name is refused: two worlds with one name is a question.
     */
    public Decision openWorld(String universe, int year, String branch) {
        if (universe == null || universe.trim().isEmpty()) {
            return Decision.no("a world with no name is nowhere");
        }
        if (worlds.containsKey(key(universe))) {
            return Decision.no("the universe " + universe + " already exists; two worlds with one name is a "
                    + "question, not a world");
        }
        String why = yearOk(year);
        if (why != null) {
            return Decision.no(why);
        }
        if (branch == null || branch.trim().isEmpty()) {
            return Decision.no("a world must say which timeline it is on");
        }
        worlds.put(key(universe), new World(universe.trim(), year, branch.trim()));
        return Decision.yes(universe.trim() + " is open in " + year + " on " + branch.trim());
    }

    /**
     * <b>A player enters a universe.</b> It moves only them, so anyone may, any time. One player is in one
     * world: entering another world is leaving this one.
     */
    public Decision join(String player, String universe) {
        if (player == null || player.trim().isEmpty()) {
            return Decision.no("nobody cannot enter anywhere");
        }
        World w = worlds.get(key(universe == null ? "" : universe));
        if (w == null) {
            return Decision.no("there is no universe called " + universe + " here: " + names());
        }
        String was = where.get(key(player));
        if (was != null && was.equals(key(universe))) {
            return Decision.no(player + " is already in " + w.universe());
        }
        if (was != null) {
            worlds.get(was).players.removeIf(n -> n.equalsIgnoreCase(player.trim()));
        }
        w.players.add(player.trim());
        where.put(key(player), key(universe));
        return Decision.yes(player.trim() + " enters " + w.universe() + ", where it is " + w.year()
                + (was == null ? "" : ", leaving " + worlds.get(was).universe()));
    }

    /**
     * <b>Move a world's shared date.</b> It moves everyone standing in that world, so it takes their vote or
     * an operator ({@link Permissions}). The vote, if given, must be a vote this class can recognise: the same
     * act, the same world, already passed. A player cannot move a world they are not in: their say is in its
     * vote, not in a private order.
     */
    public Decision moveDate(Actor by, String universe, int toYear, Vote authority, String why) {
        World w = worlds.get(key(universe == null ? "" : universe));
        if (w == null) {
            return Decision.no("there is no universe called " + universe + " here: " + names());
        }
        String yearWhy = yearOk(toYear);
        if (yearWhy != null) {
            return Decision.no(yearWhy);
        }
        if (w.year == toYear) {
            return Decision.no(w.universe() + " is already in " + toYear);
        }
        Decision grant = authorityFor(by, Action.MOVE_WORLD_TIME, w, authority);
        if (!grant.allowed()) {
            return grant;
        }
        int from = w.year;
        w.year = toYear;
        String reason = why == null || why.trim().isEmpty() ? "" : " (" + why.trim() + ")";
        return Decision.yes(w.universe() + " moves from " + from + " to " + toYear + " for its "
                + w.players.size() + " people, " + grant.why() + reason);
    }

    /**
     * <b>Switch a world's shared timeline.</b> The same rule as the date: it moves everyone in the world, so
     * it takes their vote or an operator.
     */
    public Decision moveBranch(Actor by, String universe, String toBranch, Vote authority, String why) {
        World w = worlds.get(key(universe == null ? "" : universe));
        if (w == null) {
            return Decision.no("there is no universe called " + universe + " here: " + names());
        }
        if (toBranch == null || toBranch.trim().isEmpty()) {
            return Decision.no("switch to what timeline? none was named");
        }
        if (w.branch.equalsIgnoreCase(toBranch.trim())) {
            return Decision.no(w.universe() + " is already on " + toBranch.trim());
        }
        Decision grant = authorityFor(by, Action.MOVE_WORLD_BRANCH, w, authority);
        if (!grant.allowed()) {
            return grant;
        }
        String from = w.branch;
        w.branch = toBranch.trim();
        String reason = why == null || why.trim().isEmpty() ? "" : " (" + why.trim() + ")";
        return Decision.yes(w.universe() + " switches from " + from + " to " + w.branch + " for its "
                + w.players.size() + " people, " + grant.why() + reason);
    }

    /**
     * <b>A private year in a shared world.</b> Always refused, and the refusal is the point of the class:
     * two players cannot see two years in the same place, so the ways to a different year are a different
     * universe or a moved shared date, never a private clock.
     */
    public Decision jumpSelf(Actor by, String player, int toYear) {
        World w = null;
        String u = where.get(key(player == null ? "" : player));
        if (u != null) {
            w = worlds.get(u);
        }
        return Decision.no("you cannot be in " + toYear + " while " + (w == null ? "the world" : w.universe())
                + " is in " + (w == null ? "?" : w.year()) + ": one world has one date. Enter another "
                + "universe, or ask to move the shared date (" + Permissions.ruleLine() + ")");
    }

    // ------------------------------------------------------------------ queries

    /** Where a player stands, or null if they are nowhere. */
    public Standing standingOf(String player) {
        String u = where.get(key(player == null ? "" : player));
        if (u == null) {
            return null;
        }
        World w = worlds.get(u);
        for (String n : w.players) {
            if (n.equalsIgnoreCase(player.trim())) {
                return new Standing(n, w.universe(), w.year(), w.branch);
            }
        }
        return null;
    }

    /** The world's shared year, or null if there is no such world. */
    public Integer yearOf(String universe) {
        World w = worlds.get(key(universe == null ? "" : universe));
        return w == null ? null : w.year;
    }

    /** The world's shared timeline, or null if there is no such world. */
    public String branchOf(String universe) {
        World w = worlds.get(key(universe == null ? "" : universe));
        return w == null ? null : w.branch;
    }

    /** How many people a change here would move. The number a vote is about. */
    public int peopleIn(String universe) {
        World w = worlds.get(key(universe == null ? "" : universe));
        return w == null ? 0 : w.players.size();
    }

    public List<String> universes() {
        List<String> out = new ArrayList<>();
        for (World w : worlds.values()) {
            out.add(w.universe());
        }
        return out;
    }

    /** Everybody's standing, in the order they arrived. */
    public List<Standing> standings() {
        List<Standing> out = new ArrayList<>();
        for (World w : worlds.values()) {
            for (String p : w.players) {
                out.add(new Standing(p, w.universe(), w.year(), w.branch));
            }
        }
        return out;
    }

    /** The table, as a player reads it: who is where, in what year, on what timeline. */
    public List<String> table() {
        List<String> out = new ArrayList<>();
        for (World w : worlds.values()) {
            out.add(w.universe() + " · " + w.year + " · " + w.branch + " · " + w.players.size()
                    + (w.players.size() == 1 ? " person" : " people"));
            for (String p : w.players) {
                out.add("  " + p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ internals

    /**
     * The one place this class consults {@link Permissions}: alone, or by a vote it can recognise. A vote for
     * another act or another world is refused here rather than stretched to fit.
     */
    private Decision authorityFor(Actor by, Action action, World w, Vote authority) {
        // 1. A passed vote of the people it moves is the authority whoever executes it. A vote for another
        //    act or another world is refused here rather than stretched to fit.
        if (authority != null) {
            if (authority.action() != action) {
                return Decision.no("the vote is about " + authority.action().words() + ", not " + action.words());
            }
            if (!key(authority.world()).equals(key(w.universe))) {
                return Decision.no("the vote is about " + authority.world() + ", not " + w.universe());
            }
            if (!authority.passed()) {
                return Decision.no("the vote has not passed: " + authority.outcome());
            }
            return Decision.yes("by the vote: " + authority.outcome());
        }
        // 2. Without a vote, a player may only move a world they stand in: their say about anywhere else is
        //    its people's vote, not a private order. An operator runs the server and may move any of its
        //    worlds.
        if (by != null && !by.isOperator()) {
            boolean standing = false;
            for (String p : w.players) {
                if (p.equalsIgnoreCase(by.name().trim())) {
                    standing = true;
                }
            }
            if (!standing) {
                return Decision.no(by.name() + " does not stand in " + w.universe() + " and cannot move it "
                        + "alone; enter it, or put the question to the " + w.players.size()
                        + " people it moves");
            }
        }
        // 3. The ordinary rule from Permissions: alone you may, with several moved it takes their vote (which
        //    was not offered, so this is a refusal) or an operator.
        Decision alone = Permissions.mayAlone(by, action, w.players.size());
        if (alone.allowed()) {
            return alone;
        }
        return Decision.no(alone.why() + "; no vote was offered");
    }

    private static String yearOk(int year) {
        if (year < EARLIEST) {
            return "the project's record runs from 3000 BC, and " + year + " is before it";
        }
        if (year > LATEST) {
            return "the project's record runs to AD 3000, and " + year + " is after it";
        }
        return null;
    }

    private List<String> names() {
        return universes();
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
