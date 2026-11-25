package com.eternalengine.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>The final test in the real game.</b> Step 60. A session script as a register.
 *
 * <h2>Why a register and not a checklist file alone</h2>
 *
 * The plan's own words (line 461): "this sandbox cannot show a real Terra++ world, the game's
 * picture and true multiplayer. Look, feel and real speed are checked in your final test (step 60,
 * your rule)." The run needs your game. What this sandbox can do is build the test so nothing is
 * forgotten and nothing is invented:
 *
 * <ul>
 *   <li><b>every test names its source</b> (the known-problem number, the system, the plan row);
 *       a test with no source is not a test;</li>
 *   <li><b>coverage is computed, not claimed:</b> every era band and continent of the world-wide
 *       test set (§9, turn 96: "no place or year comes first"), the timeline's two ends, empty
 *       land, a world sample of 400 points in 7 years, a year past 2026, and the four look checks
 *       the plan defers to this step (problems 11, 14, 19, 30);</li>
 *   <li><b>the run is not claimed:</b> until a real session reports, nothing here says "passed in
 *       game". The results are step 61's input.</li>
 * </ul>
 *
 * <p>{@code eternal/FINAL_TEST.md} is the session script you follow, written from these tests; the
 * probe holds the two in agreement.
 */
public final class LiveTest {

    /** The phases of one session, in the order to run them. */
    public enum Phase {
        PRE_FLIGHT("pre-flight: the sandbox must already be green"),
        ERA_ROUTE("the era route: every continent, every era, the timeline's ends"),
        EMPTY_LAND("empty land: nobody lives here"),
        WORLD_SAMPLE("the world sample: 400 points in 7 years"),
        LIVE_LOOKS("the looks only a real screen has"),
        PERFORMANCE("performance: measure before you tune"),
        GUARDRAILS("guardrails: the rules the game must hold"),
        MULTIPLAYER("multiplayer: the world answers the same to everyone");

        private final String words;

        Phase(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /**
     * <b>One test.</b> Where and when, what to do, what good looks like, and where it comes from.
     */
    public record Test(String id, Phase phase, String where, String when, String doThis,
                       String goodLooks, String from) {

        public Test {
            id = id == null ? "" : id.trim();
            where = where == null ? "" : where.trim();
            when = when == null ? "" : when.trim();
            doThis = doThis == null ? "" : doThis.trim();
            goodLooks = goodLooks == null ? "" : goodLooks.trim();
            from = from == null ? "" : from.trim();
        }

        public String line() {
            return id + " [" + phase.name() + "] " + where + " " + when + " - " + doThis
                    + " - good: " + goodLooks + " - from: " + from;
        }
    }

    /** <b>What the register covers,</b> computed from the tests, never typed in by hand. */
    public record Coverage(Set<String> eraBands, Set<String> continents, int namedPlaceYears,
                           List<String> deferredProblems, boolean timelineEnds, boolean projectedYear) {
    }

    /** One named stop of the world-wide test set (§9). */
    private record Stop(String where, String when, String continent, String eraBand, String extra) {
    }

    private static final List<Stop> STOPS = List.of(
            new Stop("Ur", "2500 BC", "Asia", "before 1000 BC", ""),
            new Stop("Giza", "2500 BC", "Africa", "before 1000 BC", ""),
            new Stop("Mohenjo-daro", "2500 BC", "Asia", "before 1000 BC", ""),
            new Stop("Anyang", "1200 BC", "Asia", "before 1000 BC", ""),
            new Stop("Athens", "430 BC", "Europe", "1000 BC to AD 500", ""),
            new Stop("Pataliputra", "300 BC", "Asia", "1000 BC to AD 500", ""),
            new Stop("Rome", "AD 100", "Europe", "1000 BC to AD 500",
                    "the empire: aqueducts, paved roads and the Roman mile"),
            new Stop("Teotihuacan", "AD 500", "North America", "1000 BC to AD 500", ""),
            new Stop("Chang'an", "750", "Asia", "500 to 1500", ""),
            new Stop("Baghdad", "900", "Asia", "500 to 1500", ""),
            new Stop("Greenland's Norse farms", "1000", "North America", "500 to 1500",
                    "the known limit: the Norse farms may answer nothing (problem 81); say so, do not invent"),
            new Stop("Cahokia", "1100", "North America", "500 to 1500",
                    "the known limit: the region population floor may show nothing (problem 73)"),
            new Stop("Angkor", "1150", "Asia", "500 to 1500", ""),
            new Stop("Great Zimbabwe", "1300", "Africa", "500 to 1500", ""),
            new Stop("Timbuktu", "1350", "Africa", "500 to 1500", ""),
            new Stop("the Sahara", "1000", "Africa", "500 to 1500", "and empty land nearby"),
            new Stop("Tenochtitlan", "1500", "North America", "1500 to 1800",
                    "before contact: no iron, horses, carts or printed books"),
            new Stop("Cusco", "1500", "South America", "1500 to 1800",
                    "before contact: no iron, horses, carts or printed books"),
            new Stop("the Amazon", "1500", "South America", "1500 to 1800", ""),
            new Stop("Vijayanagara", "1520", "Asia", "1500 to 1800", ""),
            new Stop("Delhi", "1650", "Asia", "1500 to 1800", ""),
            new Stop("Edo", "1700", "Asia", "1500 to 1800", "the town is called Edo, not Tokyo"),
            new Stop("London", "1700", "Europe", "1500 to 1800", ""),
            new Stop("Paris", "1789", "Europe", "1500 to 1800", ""),
            new Stop("Sydney", "1850", "Oceania", "1800 to now", ""),
            new Stop("New York", "1920", "North America", "1800 to now", ""),
            new Stop("Hyderabad", "1948", "Asia", "1800 to now",
                    "the Dominion of India, ruled by George VI; rupees; the nizam's city"),
            new Stop("Lagos", "1960", "Africa", "1800 to now", ""),
            new Stop("Moscow", "1980", "Europe", "1800 to now", ""),
            new Stop("Sao Paulo", "2020", "South America", "1800 to now", ""),
            new Stop("Siberia", "1900", "Asia", "1800 to now", ""));

    private LiveTest() {
    }

    /** <b>All the tests,</b> in the order the phases are run. */
    public static List<Test> tests() {
        List<Test> out = new ArrayList<>();
        int n = 0;

        // ---- pre-flight: what this sandbox already proved ----
        out.add(new Test("P" + (++n), Phase.PRE_FLIGHT, "the sandbox", "before the session",
                "run worldfill/sweep_all.sh, the syntax gate and the manifest build; rebuild the mod",
                "every probe is green (101 as shipped), no syntax errors, the manifest matches the data",
                "turn 91's rule (tested in the sandbox first); worldfill/sweep_all.sh; step 26's manifest"));
        out.add(new Test("P" + (++n), Phase.PRE_FLIGHT, "your server", "before the session",
                "run /ee check all on the server, as console and as a player",
                "every check passes or names a known limit; no file is created in the world folder",
                "worldfill/TEST_CHECKLIST.md tradition (steps 11-26)"));

        // ---- the era route: the world-wide test set, §9 ----
        n = 0;
        for (Stop stop : STOPS) {
            String good = "talk, dress, money, buildings and the town's name all belong to "
                    + stop.when() + (stop.extra().isEmpty() ? "" : "; " + stop.extra());
            out.add(new Test("R" + (++n), Phase.ERA_ROUTE, stop.where(), stop.when(),
                    "walk the place, talk to the people, look at the buildings, the dress and the "
                            + "money, and ask the place card what is here",
                    good,
                    "world-wide test set (SYSTEMS_PLAN part 9, turn 96): " + stop.continent()
                            + ", " + stop.eraBand()));
        }
        out.add(new Test("R" + (++n), Phase.ERA_ROUTE, "the record's edge", "3000 BC",
                "ask the date, the place and the people at the very start of the timeline",
                "the world answers for 3000 BC; anything older is marked before the record, never invented",
                "the timeline runs 3000 BC to AD 3000 (the user's standing rule)"));
        out.add(new Test("R" + (++n), Phase.ERA_ROUTE, "the record's edge", "AD 3000",
                "ask the date, the place and the people at the far end of the timeline",
                "everything past 2026 is marked projected; no ruler, person, dynasty, war, event, "
                        + "disaster, price or population figure is invented",
                "the AD 3000 guardrail (turn 110 and the standing rules)"));

        // ---- empty land ----
        n = 0;
        out.add(new Test("X" + (++n), Phase.EMPTY_LAND, "a desert, an ice sheet and a forest", "any year",
                "stand in each and ask who lives here; walk through it looking for people",
                "\"nobody lives here\" is the answer and no crowd ever appears",
                "world-wide test set (part 9): empty land counts too"));

        // ---- the world sample ----
        n = 0;
        out.add(new Test("S" + (++n), Phase.WORLD_SAMPLE, "400 random land points", "each of 2500 BC, 500 BC, AD 1, 1000, 1500, 1900, 2000",
                "ask what is here at every point and record the answer",
                "the report counts recorded, estimated and missing answers; estimates are marked "
                        + "estimates and nothing empty is invented",
                "world-wide test set (part 9), as in step 4's probe"));

        // ---- the looks only a real screen has ----
        n = 0;
        out.add(new Test("L" + (++n), Phase.LIVE_LOOKS, "a map with Xaero's", "2026",
                "use the \"Add to Xaero's map\" link on a place card",
                "the place is added to your map at the right coordinates",
                "known problem 11 (deferred to your final test, step 60)"));
        out.add(new Test("L" + (++n), Phase.LIVE_LOOKS, "any town", "any year",
                "end a talk and watch the person leave",
                "\"... has left.\", the walking away and the turning to face you all look right on a screen",
                "known problem 14 (deferred to your final test, step 60)"));
        out.add(new Test("L" + (++n), Phase.LIVE_LOOKS, "a village selling books", "any year",
                "buy the history book and look at every form",
                "the 13 item pictures draw correctly and the welcome line reads right",
                "known problem 19 (deferred to your final test, step 60)"));
        out.add(new Test("L" + (++n), Phase.LIVE_LOOKS, "a merchant", "any year",
                "open the trade and change screens",
                "Minecraft's trade screen shows the goods and the money with the game's own look",
                "known problem 30 (deferred to your final test, step 60); J1's screens"));
        out.add(new Test("L" + (++n), Phase.LIVE_LOOKS, "the whole session", "the whole session",
                "watch look, feel and real speed while you walk the route",
                "nothing jitters, the pictures hold up close, and the game feels like Minecraft",
                "the plan's line 461: look, feel and real speed are checked in your final test"));

        // ---- performance: measure before you tune ----
        n = 0;
        out.add(new Test("F" + (++n), Phase.PERFORMANCE, "a busy town", "30 minutes",
                "walk the 30-minute test walk with Spark recording",
                "the tick stays inside its 10 ms budget, no system blows it alone, the numbers match "
                        + "step 57's walk within a factor of a few",
                "I2 (the long test walk); step 57's PerfBudget; step 58's pack order"));
        out.add(new Test("F" + (++n), Phase.PERFORMANCE, "a packed square", "crowd on",
                "stand in a packed square with Entity Culling on and off",
                "the crowd thins only where buildings hide it, and never flickers at the edges",
                "step 58 CHECK_LIVE: Entity Culling owes this test (PerformanceMods)"));
        out.add(new Test("F" + (++n), Phase.PERFORMANCE, "crowd to market", "crowd on",
                "walk from a crowd into a market with Embeddium installed",
                "figures and nameplates both appear, at the same distances as without it",
                "step 58 CHECK_LIVE: Embeddium owes this test (PerformanceMods)"));
        out.add(new Test("F" + (++n), Phase.PERFORMANCE, "any town", "a time jump",
                "take a time jump with Canary installed and watch the rebuild wait",
                "the wait reaches \"the wait is over\", never stalls, never says more chunks than "
                        + "were queued",
                "step 58 CHECK_LIVE: Canary owes this test (PerformanceMods)"));
        out.add(new Test("F" + (++n), Phase.PERFORMANCE, "a tall view", "crowd on",
                "look at the crowd from 24, 64, 128 and past 160 blocks",
                "the draw thins at 24 and 64, people become counts at 128, and hide at 160 or your "
                        + "client's view, whichever is nearer",
                "step 57 (I4, I5): the draw budgets, live"));

        // ---- guardrails ----
        n = 0;
        out.add(new Test("G" + (++n), Phase.GUARDRAILS, "two players", "any year",
                "ask the date from two players in different places",
                "one shared date and one timeline: both see the same day; different years means "
                        + "different universes, never one world",
                "decision 3 (one shared date and timeline per world)"));
        out.add(new Test("G" + (++n), Phase.GUARDRAILS, "any place", "2100 and 2500",
                "ask who rules, who lives here, what things cost and what happened",
                "every answer past 2026 carries the projected mark; no ruler, head of government, "
                        + "person, dynasty, war, event, disaster or price exists; no population "
                        + "figure past the last measured year",
                "the AD 3000 guardrail (turn 110; projections carry their own third mark)"));
        out.add(new Test("G" + (++n), Phase.GUARDRAILS, "the purse", "any year",
                "try to spend more than you have",
                "the purse cannot go below zero: the trade refuses and says why",
                "step 23a's rule (a purse cannot go below zero)"));
        out.add(new Test("G" + (++n), Phase.GUARDRAILS, "the timeline", "two universes",
                "write a note, branch a timeline, then travel both ways",
                "notes are written once; a branch is a difference from its parent; nobody stands in "
                        + "two universes at once; the way back is booked before the way out",
                "steps 51-56 (the branch tree, Passage, the tickets)"));
        out.add(new Test("G" + (++n), Phase.GUARDRAILS, "a fixed point", "a fixed point's year",
                "try to change a fixed point, then rebuild it",
                "it refuses and names the safe way out; no dice; forcing breaks the point and the "
                        + "break is handed over with its why; a player is never erased",
                "steps 53 and 56 (fixed points, decision 7 policy OFF)"));
        out.add(new Test("G" + (++n), Phase.GUARDRAILS, "a night outside town", "any year",
                "wait outside a town at night",
                "no monsters, no raids, no wandering traders in history",
                "known problem 9 (fixed in step 21: verify it stays fixed)"));

        // ---- multiplayer ----
        n = 0;
        out.add(new Test("M" + (++n), Phase.MULTIPLAYER, "two players, two towns", "any year",
                "ask the world the same questions from both",
                "the world answers the same to everyone: one shared world, one date, one record",
                "the plan's line 461: true multiplayer cannot be shown in the sandbox"));
        out.add(new Test("M" + (++n), Phase.MULTIPLAYER, "roles and tickets", "any year",
                "try to move time as a player who may not, then travel with a ticket and back",
                "a person moves time only if they may; the return runs only along your own ticket",
                "step 52 (roles); step 56 (Passage: the way back is booked before the way out)"));
        return out;
    }

    /** The ordered walk: what to visit, in era order. */
    public static List<String> route() {
        List<String> out = new ArrayList<>();
        for (Test test : tests()) {
            if (test.phase() == Phase.ERA_ROUTE) {
                out.add(test.when() + " at " + test.where() + ": " + test.goodLooks());
            }
        }
        return out;
    }

    /** The whole protocol, grouped by phase. */
    public static List<String> page() {
        List<String> out = new ArrayList<>();
        out.add("The final test in the real game (" + tests().size() + " tests). "
                + "The run is yours: this register names what to run, what good looks like, and "
                + "where every test comes from. Until a session reports, nothing is claimed as "
                + "passed.");
        for (Phase phase : Phase.values()) {
            out.add("");
            out.add("== " + phase.words() + " ==");
            for (Test test : tests()) {
                if (test.phase() == phase) {
                    out.add(test.line());
                }
            }
        }
        return out;
    }

    /** What the register covers, computed from the tests themselves. */
    public static Coverage coverage() {
        Set<String> eras = new LinkedHashSet<>();
        Set<String> continents = new LinkedHashSet<>();
        int named = 0;
        List<String> deferred = new ArrayList<>();
        boolean ends = false;
        boolean projected = false;
        for (Test test : tests()) {
            if (test.phase() == Phase.ERA_ROUTE) {
                if (test.from().contains("world-wide test set")) {
                    named++;
                    // "world-wide test set (SYSTEMS_PLAN part 9, turn 96): Asia, before 1000 BC"
                    String tail = test.from().substring(test.from().indexOf(':') + 1).trim();
                    String[] parts = tail.split(", ");
                    if (parts.length == 2) {
                        continents.add(parts[0]);
                        eras.add(parts[1]);
                    }
                }
                if (test.when().equals("3000 BC") || test.when().equals("AD 3000")) {
                    ends = true;
                }
            }
            if (test.phase() == Phase.GUARDRAILS && test.when().contains("2100")) {
                projected = true;
            }
            if (test.from().contains("problem ")) {
                String after = test.from().substring(test.from().indexOf("problem ") + 8);
                int i = 0;
                while (i < after.length() && Character.isDigit(after.charAt(i))) {
                    i++;
                }
                deferred.add(after.substring(0, i));
            }
        }
        return new Coverage(eras, continents, named, deferred, ends, projected);
    }

    public static String ruleLine() {
        return "Every test names its source, coverage is computed from the tests ("
                + coverage().namedPlaceYears() + " named place-years over "
                + coverage().continents().size() + " continents and " + coverage().eraBands().size()
                + " era bands, the timeline's ends, empty land, the world sample, the four deferred "
                + "looks, performance and the guardrails), and the run is never claimed before it "
                + "happens: until a real session reports, nothing says passed in game.";
    }
}
