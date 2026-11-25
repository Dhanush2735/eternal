package com.eternalengine.testing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Fixes from the test record.</b> Step 61. Every known problem, triaged exactly once.
 *
 * <h2>Where the record came from</h2>
 *
 * The user's choice for this step: the earlier sessions are the test record. Those are the sessions
 * in {@code worldfill/TEST_CHECKLIST.md} (your game, Hyderabad 1948 onward, and the sandbox test
 * server with its fake player) and the known-problems table their findings were written into
 * ({@code SYSTEMS_PLAN.md} part 8: 83 rows). Rows 15-35 of that table carry the words "seen in the
 * server session"; they are the session findings in the record's own order.
 *
 * <h2>What triage means here</h2>
 *
 * <ul>
 *   <li><b>A row whose fix-step has run is re-marked, not assumed fixed.</b> Many rows name steps
 *       that have since completed (30a, 30b, 33-35, 37, 38, 40, 42, 44-49, 51, 52, 57). The row is
 *       placed in HANDLED_BY_A_COMPLETED_STEP with what the completed step built, and what still
 *       wants a live look stays named (several are in step 60's route).</li>
 *   <li><b>What the sandbox could fix is fixed.</b> Problem 60 (town heads' titles) got its wording
 *       pass ({@code TownFacts.prettyTitle}); two rows were verified in code (82 is stale,
 *       {@code BuildingLife} reads the famous-buildings register; 78 is Terra++'s tree, this mod
 *       never calls it).</li>
 *   <li><b>The rest names its owner:</b> data we do not have, a fact check like step 25's, a
 *       Terra++ patch (decision 2: mod-side first), old code not reviewed, or the model's own
 *       shape as a documented limit.</li>
 * </ul>
 */
public final class Findings {

    /** The ten buckets. Every finding lands in exactly one. */
    public enum Bucket {
        FIXED_IN_AN_EARLIER_STEP("fixed in an earlier step (closed)"),
        PARTLY_FIXED("partly fixed; the residue is written down"),
        FIXED_IN_STEP_61("fixed in step 61 (this step)"),
        HANDLED_BY_A_COMPLETED_STEP("handled by a step that has since completed; a live look or deep check remains"),
        NEEDS_A_REAL_SCREEN("needs a real screen: step 60's deferred look"),
        NEEDS_DATA_WE_DO_NOT_HAVE("needs data we do not have (thin lists, Wikidata gaps, records by hand, region packs)"),
        NEEDS_A_FACT_CHECK("needs a fact check like step 25's"),
        NEEDS_A_TERRA_PATCH("needs a Terra++ patch (decision 2: mod-side first)"),
        OLD_CODE_NOT_REVIEWED("old code not reviewed yet"),
        DOCUMENTED_LIMIT("a documented limit: the model's own shape");

        private final String words;

        Bucket(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>One known problem, triaged.</b> {@code problem} is the row number in the plan's part 8. */
    public record Finding(int problem, Bucket bucket, String resolution) {

        public Finding {
            resolution = resolution == null ? "" : resolution.trim();
        }

        public String line() {
            return problem + ". [" + bucket.name() + "] " + resolution;
        }
    }

    private static Finding f(int problem, Bucket bucket, String resolution) {
        return new Finding(problem, bucket, resolution);
    }

    private Findings() {
    }

    /** All 83 findings, in the record's own order. */
    public static List<Finding> findings() {
        List<Finding> out = new ArrayList<>();
        out.add(f(1, Bucket.FIXED_IN_AN_EARLIER_STEP, "people vanished while you talked: fixed in step 21 (the crowd is not rearranged while you stand still)"));
        out.add(f(2, Bucket.FIXED_IN_AN_EARLIER_STEP, "map position to block: fixed in step 21 (Terra++'s public API is read correctly)"));
        out.add(f(3, Bucket.FIXED_IN_AN_EARLIER_STEP, "the old technology engine had no dates: fixed in step 21 (first years), then step 30a (the spread by region)"));
        out.add(f(4, Bucket.FIXED_IN_AN_EARLIER_STEP, "\"Republic of India\" on 1 January 1948: fixed in step 25 (Dominion of India, 1947-1949)"));
        out.add(f(5, Bucket.HANDLED_BY_A_COMPLETED_STEP, "townspeople return slowly after a long jump: looked into in step 21 (the wait is the chunk rebuild), and step 57 made the rebuild wait a named state that drains to zero; the live cadence is test F1"));
        out.add(f(6, Bucket.FIXED_IN_AN_EARLIER_STEP, "one 918 ms spawner cycle after a 3 km jump: fixed in step 21 (groundless places are not looked up every cycle)"));
        out.add(f(7, Bucket.HANDLED_BY_A_COMPLETED_STEP, "walking new ground invented people with no town centre: step 38 built households, jobs and settlement life over the town list; the era route (test R) checks empty land stays empty"));
        out.add(f(8, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "thin name lists (\"Sam Stone\" on the Bombay coast): where the language map has no answer the English list is used; more lists are region-pack work"));
        out.add(f(9, Bucket.FIXED_IN_AN_EARLIER_STEP, "monsters, raids and wandering traders in history: fixed in step 21 (settings); test G6 verifies it stays"));
        out.add(f(10, Bucket.HANDLED_BY_A_COMPLETED_STEP, "the old time machine item only printed a message: step 48 (F9) built the ways to change history over it"));
        out.add(f(11, Bucket.NEEDS_A_REAL_SCREEN, "the \"Add to Xaero's map\" link: step 60's test L1, in your game"));
        out.add(f(12, Bucket.HANDLED_BY_A_COMPLETED_STEP, "every dimension read with the prime projection: step 51 built the timeline connections and the branch tree over them (a mirror universe is a branch, and a branch is a difference from its parent)"));
        out.add(f(13, Bucket.FIXED_IN_AN_EARLIER_STEP, "the client dressed everybody for year 400 on a dedicated server: fixed in step 21 (the year is read from the data)"));
        out.add(f(14, Bucket.NEEDS_A_REAL_SCREEN, "\"... has left.\", walking away, turning to face you: step 60's test L2, in your game (the fake player has no screen)"));
        out.add(f(15, Bucket.FIXED_IN_AN_EARLIER_STEP, "people remembered from another year came back wrong: fixed in step 25 (the cause named and removed)"));
        out.add(f(16, Bucket.HANDLED_BY_A_COMPLETED_STEP, "nobody at Sydney 1700 though the Eora lived there: step 38's estimated life and step 30b's ways of life cover the gap; the era route walks Sydney 1850 and the empty land around it"));
        out.add(f(17, Bucket.HANDLED_BY_A_COMPLETED_STEP, "the history book's forms (17 areas plus 13 local rules): step 30a carried the areas into the atlas; the forms themselves were checked at step 22"));
        out.add(f(18, Bucket.FIXED_IN_AN_EARLIER_STEP, "the talk box lost its ninth question: fixed in step 24 (buying, selling, swapping and changing moved to Minecraft's trade screen)"));
        out.add(f(19, Bucket.NEEDS_A_REAL_SCREEN, "the 13 history-book item pictures and the welcome line: step 60's test L3, in your game"));
        out.add(f(20, Bucket.FIXED_IN_AN_EARLIER_STEP, "twin cities could not be told apart by half-degree cells: fixed in step 26 (every city of 100,000+ and every capital is checked)"));
        out.add(f(21, Bucket.FIXED_IN_AN_EARLIER_STEP, "today's state named for occupied lands: fixed in step 25 (Czechoslovakia, Poland, Romania and the rest are named for their years)"));
        out.add(f(22, Bucket.HANDLED_BY_A_COMPLETED_STEP, "hyperinflations jump between yearly wage figures: step 44 built prices and wages through time (E4) over the yearly figures; the jumps are the record's jumps, now interpolated instead of stepped"));
        out.add(f(23, Bucket.PARTLY_FIXED, "commodity money is coarse: fixed for manillas in step 24 (under 4 manillas things cost cowries); the residue: salt bars and home-made small change stay coarse, and cowries are taken as small change"));
        out.add(f(24, Bucket.DOCUMENTED_LIMIT, "one money per place with a few \"also taken\": the model's own shape (decision: the main money is what the register names; ports passing side by side show the main one)"));
        out.add(f(25, Bucket.HANDLED_BY_A_COMPLETED_STEP, "the look of a piece is a rule of thumb by value and era: step 44 carries a per-money list of real coins where it matters; elsewhere the rule of thumb stands (a 5-penninge in Oslo 1200 never existed and is marked as a stand-in)"));
        out.add(f(26, Bucket.HANDLED_BY_A_COMPLETED_STEP, "reset cannot take pieces from offline players or chests: step 52's pockets and property record what each player holds; the sweep of world pieces is a live-admin action, named here"));
        out.add(f(27, Bucket.FIXED_IN_AN_EARLIER_STEP, "goods for goods barter: fixed in step 24 (the trade screen swaps at what he pays for yours)"));
        out.add(f(28, Bucket.FIXED_IN_AN_EARLIER_STEP, "the money-changer changed all of one money at once: fixed in step 24 (he changes what you put in the slots)"));
        out.add(f(29, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "trader name pools mix regions (\"Liu Wang\" in Edo 1700): traders draw from wider pools than townsfolk; tightening them is name-list work (as problems 8 and 32)"));
        out.add(f(30, Bucket.NEEDS_A_REAL_SCREEN, "Minecraft's trade screen look: step 60's test L4, in your game (its look was checked from pictures and its server side with the fake player)"));
        out.add(f(31, Bucket.HANDLED_BY_A_COMPLETED_STEP, "old money changed by what it bought, not its metal: step 44 built the prices through time that the change runs on (a sicca rupee of 1857 buys its day's goods in 1948's money)"));
        out.add(f(32, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "names at Cahokia borrow from other American peoples (\"Isabel of Tenochtitlan\"): the pools are continental where the record is regional; name-list work (as problems 8 and 29)"));
        out.add(f(33, Bucket.HANDLED_BY_A_COMPLETED_STEP, "nobody stood up at Hyderabad after a jump (all 13 spots \"inside a player's bubble\"): step 57's spawn and draw budgets name the bubble rule; the live cadence is test F1"));
        out.add(f(34, Bucket.NEEDS_A_FACT_CHECK, "the fact check still finds 44 \"wrong\" and 207 \"likely wrong\" lines, most names it cannot confirm: a fact-check pass like step 25's, comparing period names with period names"));
        out.add(f(35, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "Italians in Rome 2026 get Greek names from the Mediterranean list: the pool is wider than the country (as problems 8, 29 and 32)"));
        out.add(f(36, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "\"people here speak ...\" uses one point per language: a village far from every point gets the wrong answer; more points are data work"));
        out.add(f(37, Bucket.HANDLED_BY_A_COMPLETED_STEP, "places, events and the border map loaded whole at start: step 34 tiled them together (the famous people were already tiles from step 26)"));
        out.add(f(38, Bucket.HANDLED_BY_A_COMPLETED_STEP, "a straight line between border cities, not the real border: step 34's finer borders read the border map in tiles; the residue (villages on a border) is the tile grid's resolution"));
        out.add(f(39, Bucket.FIXED_IN_AN_EARLIER_STEP, "Palestine's towns named by today's control: fixed in step 28 (the disputed-lands record: the West Bank, Gaza, the Golan, Crimea as the record has them)"));
        out.add(f(40, Bucket.FIXED_IN_AN_EARLIER_STEP, "occupations covered only the Second World War's map squares: fixed in step 28 (occupations.json.gz, 7,302 squares read from the record); other wars are problem 51"));
        out.add(f(41, Bucket.FIXED_IN_AN_EARLIER_STEP, "352 famous people near Cambridge 1928 who were not born there: fixed in step 41 (D11: residences and places of work, not birthplaces)"));
        out.add(f(42, Bucket.PARTLY_FIXED, "today's names on the cards (\"Mahatma Gandhi\" before 1914): the place line uses the town's name of the year (fixed in step 29); the residue: card descriptions may still name a later country, which is problem 43's fact check"));
        out.add(f(43, Bucket.NEEDS_A_FACT_CHECK, "Wikidata's own mistakes stay in the groups (impossible founding years, wrong members): a fact check of the cards (step 28's audit), then fixes like step 25's"));
        out.add(f(44, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "some rulers have no card or another name (Cosimo II in Florence 1610, Emperor Go-Yozei): cards we do not have; the talk names them without a link, which is the honest form"));
        out.add(f(45, Bucket.HANDLED_BY_A_COMPLETED_STEP, "the first talk in a busy area took up to 0.1 s while tiles opened: step 34's tiles are opened ahead as the player moves"));
        out.add(f(46, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "the card pool is 148,510 of 1,959,134 people (the 300-per-box-and-century cap keeps the data at 8.5 MB): region packs if the 64 MB budget is outgrown (decision 5)"));
        out.add(f(47, Bucket.HANDLED_BY_A_COMPLETED_STEP, "the cause-and-effect graph is thin (Wikidata has causes for 2,284 events): step 49 built the rules where the data is silent; thin data stays thin and says so"));
        out.add(f(48, Bucket.HANDLED_BY_A_COMPLETED_STEP, "an event's sides listed best-known-first (the Second World War names Mexico before the Soviet Union): step 40 built armies and sides in action over the same participants"));
        out.add(f(49, Bucket.HANDLED_BY_A_COMPLETED_STEP, "765 natural events placed at their country's middle point: step 35 built water and climate by era with real placements where the record has them"));
        out.add(f(50, Bucket.HANDLED_BY_A_COMPLETED_STEP, "storms before 1940 left out for want of winds: step 35's storm tables carry the recorded storms and say where winds are missing"));
        out.add(f(51, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "occupations cover only the Second World War (Iraq 2003, the Baltics 1940 say nothing): the records for other occupations are not in the data yet"));
        out.add(f(52, Bucket.NEEDS_A_FACT_CHECK, "event cards' years come from Wikidata and were checked only for this mod's own events: a fact check with problem 43"));
        out.add(f(53, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "town heads are thin (1,578 of 30,151 towns have a head with dated holders): more holders are records by hand and region packs"));
        out.add(f(54, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "population figures mix city and urban area (Hyderabad 9.2 million): census tables by year would separate them; the talk says \"about\" and \"people say\" for exactly this reason"));
        out.add(f(55, Bucket.NEEDS_A_FACT_CHECK, "founding years are Wikidata's \"people say\" (307 refoundings were caught, others may remain): a fact check of the towns, like step 25's"));
        out.add(f(56, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "names by era cover 3,892 towns and some are not in Latin letters (Edo comes from the hand table): spelling by era is language data (step 30b held what is known)"));
        out.add(f(57, Bucket.HANDLED_BY_A_COMPLETED_STEP, "provinces come by town, not as areas, and some names are bare (\"Seine\"): step 34's places and the border map in tiles carry the areas; bare names are the record's names"));
        out.add(f(58, Bucket.HANDLED_BY_A_COMPLETED_STEP, "ties between states are few (385 relation rows, 16 alliances): step 30b carried trade partners and step 40 the wars' sides; thin ties stay thin and say so"));
        out.add(f(59, Bucket.OLD_CODE_NOT_REVIEWED, "the old treaty simulation (DiplomaticTreatyEngine, FactionRelationshipSystem) is not reviewed and recorded ties do not feed it: step 49 built the cause-and-effect engine beside it; the review is old-code work"));
        out.add(f(60, Bucket.FIXED_IN_STEP_61, "town heads' titles were Wikidata's office names, some lower case and some long: fixed in this step (TownFacts.prettyTitle: the wording pass)"));
        out.add(f(61, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "only 11% of the technology atlas's country years are recorded (3,499 from Wikipedia, 226 from CHAT): more records by hand, and region packs"));
        out.add(f(62, Bucket.HANDLED_BY_A_COMPLETED_STEP, "inside a country a technology spreads by distance and era speed only (printing in Mughal India before 1800 is not modelled): step 34's waterways and roads of the past shape the spread's routes; the residue is the model's grain"));
        out.add(f(63, Bucket.HANDLED_BY_A_COMPLETED_STEP, "\"how common\" is CHAT for 10 household things and a curve for the rest: step 30b's economy figures carry the same grain, and the gap between city and country is named in the talk"));
        out.add(f(64, Bucket.OLD_CODE_NOT_REVIEWED, "the old technology engine (7 technologies per civilization, no place) and the world-wide medicine steps are not replaced: step 31 checked Terra++ by year, step 42 built health, step 49 the engine; the old code stands until reviewed"));
        out.add(f(65, Bucket.HANDLED_BY_A_COMPLETED_STEP, "buildings, weapons, tools and recipes do not fill the technology atlas: steps 33, 40, 45, 46 and 47 each built their own dated tables (building culture, weapons, armory, vehicles, tools and crops)"));
        out.add(f(66, Bucket.HANDLED_BY_A_COMPLETED_STEP, "the country grid is 0.1 degrees (a town near a border can get its neighbour's technology): step 57's budgets make a finer grid affordable if a live run says it is needed"));
        out.add(f(67, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "reading before a land's first figure is a rule (a third every 50 years back from printing): more figures by hand would replace the rule"));
        out.add(f(68, Bucket.HANDLED_BY_A_COMPLETED_STEP, "one system of measures per land and era (the seer of Bengal was not Bombay's): step 44's goods and prices carry the local measures where the record names them; the rule's grain stands"));
        out.add(f(69, Bucket.HANDLED_BY_A_COMPLETED_STEP, "wealth is national (the gap inside a land is not modelled): step 44's economy prices the gap's goods; the national grain stands and the talk says \"on average\""));
        out.add(f(70, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "holy places are Wikidata's famous ones (a village temple is not on the list): region packs and step 33's old-town cultures hold what is known"));
        out.add(f(71, Bucket.HANDLED_BY_A_COMPLETED_STEP, "feast days by the calendar for Christian and Islamic feasts only (the tabular Islamic calendar can be a day out): step 37's local calendars carry the tabular rules and say they are tabular"));
        out.add(f(72, Bucket.NEEDS_A_FACT_CHECK, "a place's faiths come from its state's record and an empire's shares count the whole empire (London 1900 lists Muslims across the empire): a fact check of the faith data"));
        out.add(f(73, Bucket.NEEDS_A_TERRA_PATCH, "Cahokia 1100 and Greenland 1000 show nothing: the region population floors are 1500 and 1800 (Terra++'s dating; decision 2 says the fix is mod-side or a listed Terra++ patch)"));
        out.add(f(74, Bucket.NEEDS_A_TERRA_PATCH, "Greenland falls between two region boxes and gets the global fallback: the boxes need to cover the world without holes (a Terra++ patch)"));
        out.add(f(75, Bucket.NEEDS_A_TERRA_PATCH, "Fiji, New Zealand and the Aleutians get other lands' settlement years: the region floors' own data (a Terra++ patch)"));
        out.add(f(76, Bucket.NEEDS_A_TERRA_PATCH, "only 0.9% of mapped structures carry an explicit start_date: steps 32-33 build the past from the dated anchors, and A12 gates today's map; the undated remainder is Terra++'s gravity model"));
        out.add(f(77, Bucket.NEEDS_A_TERRA_PATCH, "with dated anchors present the gravity model pulls everything toward them: right when the anchors are old (Angkor) and wrong when they are sparse (a Terra++ patch)"));
        out.add(f(78, Bucket.NEEDS_A_TERRA_PATCH, "OsmDateInferencer.inferStartYear() is dead code: verified here that this mod never calls it (it is Terra++'s tree); delete it or wire it up in the Terra++ patch"));
        out.add(f(79, Bucket.NEEDS_A_TERRA_PATCH, "an explicit start_date is used as-is and skips the population and technology floors: clamp explicit dates too, in the Terra++ patch"));
        out.add(f(80, Bucket.NEEDS_DATA_WE_DO_NOT_HAVE, "20,965 of 23,534 famous buildings have no end year in Wikidata: the register reads \"we do not know when it was destroyed\", which is the honest answer; an end date would come from other records"));
        out.add(f(81, Bucket.DOCUMENTED_LIMIT, "three of the 31 test places answer nothing (Greenland's Norse farms 1000 and two others, listed in logs/step32_part2_notes.md): the record is empty there, and empty says so (step 60's route marks them as known limits)"));
        out.add(f(82, Bucket.HANDLED_BY_A_COMPLETED_STEP, "\"nothing in play calls the famous-buildings register\": verified stale in this step (BuildingLife from step 36 reads FamousBuildings; the listed Terra++ placement patch remains a Terra++ patch)"));
        out.add(f(83, Bucket.NEEDS_A_FACT_CHECK, "some Wikidata end dates are inherited from elsewhere on the item (the Palatine Hill \"until today\"): a fact check of the building dates, the same treatment step 12 gave the rulers"));
        return out;
    }

    /** One finding by its problem number. */
    public static Finding of(int problem) {
        for (Finding finding : findings()) {
            if (finding.problem() == problem) {
                return finding;
            }
        }
        return null;
    }

    /** How many findings sit in each bucket. */
    public static Map<Bucket, Integer> counts() {
        Map<Bucket, Integer> out = new EnumMap<>(Bucket.class);
        for (Finding finding : findings()) {
            out.merge(finding.bucket(), 1, Integer::sum);
        }
        return out;
    }

    /** What this step fixed, by problem number. */
    public static List<Integer> fixedNow() {
        List<Integer> out = new ArrayList<>();
        for (Finding finding : findings()) {
            if (finding.bucket() == Bucket.FIXED_IN_STEP_61) {
                out.add(finding.problem());
            }
        }
        return out;
    }

    /** The whole triage, grouped by bucket. */
    public static List<String> page() {
        List<String> out = new ArrayList<>();
        out.add("Fixes from the test record (" + findings().size()
                + " findings, each triaged exactly once). What the sandbox could fix is fixed; the "
                + "rest names its owner.");
        for (Bucket bucket : Bucket.values()) {
            out.add("");
            out.add("== " + bucket.words() + " (" + counts().getOrDefault(bucket, 0) + ") ==");
            for (Finding finding : findings()) {
                if (finding.bucket() == bucket) {
                    out.add(finding.line());
                }
            }
        }
        return out;
    }

    public static String ruleLine() {
        return "Every one of the 83 known problems is triaged exactly once across ten buckets. A row "
                + "whose fix-step has run is re-marked, not assumed fixed. What the sandbox could fix "
                + "is fixed (" + fixedNow() + ": the town heads' titles wording pass, "
                + "TownFacts.prettyTitle); the rest "
                + "names its owner: data we do not have, a fact check, a Terra++ patch, old code, or "
                + "the model's own shape.";
    }
}
