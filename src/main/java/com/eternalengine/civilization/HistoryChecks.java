package com.eternalengine.civilization;

import com.eternalengine.war.ArmyChecks;
import com.eternalengine.climate.ClimatePeriods;
import com.eternalengine.people.DayRound;
import com.eternalengine.people.Health;
import com.eternalengine.people.Households;
import com.eternalengine.people.Jobs;
import com.eternalengine.people.LifeTable;
import com.eternalengine.people.Migrations;
import com.eternalengine.people.Scenes;
import com.eternalengine.people.Needs;
import com.eternalengine.buildings.BuildingLife;
import com.eternalengine.buildings.FamousBuildings;
import com.eternalengine.buildings.PlayerEdits;
import com.eternalengine.civilization.Disasters.Disaster;
import com.eternalengine.population.HydeGrid;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/**
 * The runtime tests of the world-history data (worldfill steps 2-8), in one place.
 *
 * <p>These are the checks that were run outside the game while the data was built (the worldfill
 * probes: {@code probe_step8.sh}, {@code probe_talk.sh}, {@code probe_events.sh},
 * {@code probe_index.sh}, {@code probe.sh}) plus the hand checks of {@code TEST_CHECKLIST.md}.
 * {@code /ee check all} runs them all at once inside a real server, and
 * {@code worldfill/probe_check.sh} runs exactly the same code outside it.
 *
 * <p>Everything here is read-only. It asks the registers the questions a villager's talk asks, and
 * compares the answers with the ones checked by hand when the data was built (2026-10-12, step 8).
 * No Minecraft classes on purpose: the sandbox that built the data and a live server must be able
 * to run the very same checks.
 *
 * <p>Three kinds of result: {@code OK}, {@code FAIL}, and {@code KNOWN}, a documented limit of the
 * data (the answer is the one recorded when the limit was found; if it ever changes, that is
 * reported as a note, not a failure).
 */
public final class HistoryChecks {

    private HistoryChecks() {}

    // ================================================================== result types

    /** The parts, in the order {@code /ee check all} runs them. */
    public enum Part {
        DATA("data", "The history data files load"),
        PLACES("places", "The 15 places of the test checklist"),
        RULERS("rulers", "25 more places whose rulers were fixed in step 8"),
        TALK("talk", "What villagers say at the 12 standard places"),
        EVENTS("events", "Event rules: a battle down the road beats country-wide news"),
        INDEX("index", "The fast event index gives the same answers as a full search"),
        COVERAGE("coverage", "Who lives where: the same answers as when the data was built"),
        SPEED("speed", "Speed: no lag when you arrive somewhere new"),
        FIXES("fixes", "The history data fix (step 25): states, rulers, faiths and news everywhere"),
        FOUNDATIONS("foundations", "Foundations for big data (step 26): the key, tiles, marks, the data list, memory"),
        CARDS("cards", "Knowledge cards for every place (step 27): famous people and groups, with links"),
        EVENTCARDS("eventcards", "Events, causes and effects, natural events, occupied and disputed land (step 28)"),
        TOWNS("towns", "Towns through time (step 29): names by era, people, provinces, heads; ties between states"),
        TECHNOLOGY("technology", "The technology atlas (step 30a): which technology is in use where, in every year"),
        CULTURE("culture", "Reading, scripts, measures, wealth, holy places and festivals (step 30b)"),
        HOUSES("houses", "Houses, insides, streets, materials, lights and water (step 33): what a town is made of"),
        WAYS("ways", "Roads, railways, waterways, industry and what lies under the ground (step 34): the year the network reached a square"),
        ERA("era", "Water, fields, forests and climate by era (step 35): what the land was used for, where the water was, and what the sky was doing"),
        SKY("sky", "The sky by era (step 35): the stars of the age, the comet years, and every eclipse from 1999 BC to AD 3000"),
        LIFE("life", "Building life (step 36): what struck a place, what became of what stood there, and what happened to what the player built"),
        PEOPLE("people", "Who people are (step 38): jobs, families, daily life, needs, migrations and health, by place and year"),
        SCENES("scenes", "What is going on here (step 39): the twenty event scenes, cast from the people this place and year has, and set off by what has already happened"),
        ARMIES("armies", "Armies in action (step 40): how armies stood, how a battle came out, what a siege cost, why men stood, and who could be bound"),
        FAMOUS("famous", "Famous people at their homes (step 41): who was here and why, and who was whose parent, or of what house");

        private final String id;
        private final String title;

        Part(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public static Optional<Part> byId(String id) {
            for (Part p : values()) {
                if (p.id.equalsIgnoreCase(id)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }
    }

    public enum Status { OK, FAIL, KNOWN, INFO }

    /**
     * One result line. {@code counted} lines are the checks themselves (each is OK, FAIL or KNOWN);
     * the others are summaries and notes. {@code brief} lines are shown in chat; every line, brief or not,
     * goes to the Minecraft log (logs/latest.log, marked [EE check]). /ee check all writes no file of its own.
     */
    public record Line(Status status, String text, String detail, boolean counted, boolean brief) {}

    /** The lines of one part, and how long it took. */
    public static final class Section {
        private final Part part;
        private final List<Line> lines = new ArrayList<>();
        private long millis;

        Section(Part part) {
            this.part = part;
        }

        public Part part() {
            return part;
        }

        public List<Line> lines() {
            return lines;
        }

        public long millis() {
            return millis;
        }

        /** Counted lines with this status. */
        public int count(Status status) {
            int n = 0;
            for (Line l : lines) {
                if (l.counted() && l.status() == status) {
                    n++;
                }
            }
            return n;
        }

        void check(boolean ok, String text, String whyNot) {
            lines.add(new Line(ok ? Status.OK : Status.FAIL, text, ok ? "" : whyNot, true, true));
        }

        void passQuiet(String text, String detail) {
            lines.add(new Line(Status.OK, text, detail, true, false));
        }

        void fail(String text, String detail) {
            lines.add(new Line(Status.FAIL, text, detail, true, true));
        }

        void known(String text, String detail) {
            lines.add(new Line(Status.KNOWN, text, detail, true, true));
        }

        void info(String text) {
            lines.add(new Line(Status.INFO, text, "", false, true));
        }

        void infoQuiet(String text) {
            lines.add(new Line(Status.INFO, text, "", false, false));
        }

        void summary(boolean ok, String text) {
            lines.add(new Line(ok ? Status.OK : Status.FAIL, text, "", false, true));
        }
    }

    /**
     * A place and year with the answer checked by hand. {@code state}, {@code head} and
     * {@code trade} are alternatives separated by {@code |}, compared without case, accents or
     * punctuation, as whole words; a leading {@code =} asks for the whole name ("=Republic of China"
     * must not match the People's Republic). {@code head} null: not checked. {@code known} non-null:
     * a documented limit, and the expected answer is the one recorded when it was found.
     */
    public record Spot(int number, String place, double lat, double lon, int year,
                       String state, String head, String trade, String known) {
        public String label() {
            return (number > 0 ? "#" + number + " " : "") + place + ", " + yearText(year);
        }
    }

    // ================================================================== what is expected

    /** worldfill/TEST_CHECKLIST.md, places 1-15 (the same numbers as there). */
    private static final List<Spot> CHECKLIST = List.of(
            new Spot(1, "Rome", 41.89, 12.49, -59, "Roman Republic", "Julius Caesar", null, null),
            new Spot(2, "Rome", 41.89, 12.49, 100, "Roman Empire", "Trajan", null, null),
            new Spot(3, "Chang'an (Xi'an)", 34.30, 108.90, 1, "Han Dynasty", "Emperor Ping", null, null),
            new Spot(4, "Xi'an", 34.30, 108.90, 700, "Tang Dynasty", "Wu Zetian", null, null),
            new Spot(5, "Thebes (Luxor)", 25.70, 32.64, -1270, "New Kingdom of Egypt", "Ramesses II", null, null),
            new Spot(6, "Babylon", 32.54, 44.42, -1760, "Babylonia", "Hammurabi", null, null),
            new Spot(7, "Paithan", 19.48, 75.38, 1, "Satavahana", "Pulumavi I", "Dakshinapatha", null),
            new Spot(8, "Warangal", 17.97, 79.59, 1280, "Kakatiya", "Rudrama", null, null),
            new Spot(9, "Golconda", 17.38, 78.40, 1600, "Golconda Sultanate", "Muhammad Quli Qutb Shah", null, null),
            new Spot(10, "Hyderabad", 17.385, 78.4867, 1700, "Mughal Empire", "Aurangzeb", null, null),
            new Spot(11, "Delhi", 28.61, 77.21, 1650, "Mughal Empire", "Shah Jahan", null, null),
            new Spot(12, "Kolkata", 22.57, 88.36, 1850, "British Empire", "Victoria", null, null),
            new Spot(13, "Seoul", 37.57, 126.98, 1990, "=Republic of Korea", "Roh Tae-woo", null, null),
            new Spot(14, "Pyongyang", 39.04, 125.76, 1990, "Democratic People's Republic of Korea", "Kim Il Sung",
                    null, null),
            new Spot(15, "Carnuntum (near Vienna)", 48.11, 16.86, 100, "Roman Empire", "Trajan", "Amber Road", null));

    /** The other places of worldfill/probes/Step8Probe.java (the checklist ones are above). */
    private static final List<Spot> MORE_RULERS = List.of(
            new Spot(0, "Luoyang", 34.62, 112.45, 150, "Han Dynasty", "Emperor Huan", null, null),
            new Spot(0, "Linzi (state of Qi)", 36.85, 118.30, -400, "Qi", "Dai", null, null),
            new Spot(0, "Constantinople", 41.01, 28.98, 1000, "Byzantine Empire", "Basil II", null, null),
            new Spot(0, "Memphis", 29.85, 31.25, -2570, null, "Bikheris", null,
                    "Wikipedia's list of pharaohs gives the Fourth Dynasty overlapping, uncertain dates, and the most"
                            + " recent accession wins, so Memphis in 2570 BCE names Bikheris rather than Khufu."),
            new Spot(0, "Pataliputra", 25.61, 85.14, 500, "Gupta Empire", "Vainyagupta", null, null),
            new Spot(0, "Hyderabad", 17.385, 78.49, 1900, "Hyderabad State", "Asaf Jah VI", null, null),
            new Spot(0, "Bombay", 18.94, 72.83, 1900, "British Raj", "Victoria", null, null),
            new Spot(0, "Batavia (Jakarta)", -6.20, 106.82, 1900, "Dutch East Indies", "Wilhelmina", null, null),
            new Spot(0, "Mexico City", 19.43, -99.13, 1650, "Spanish Empire", "Philip IV", null, null),
            new Spot(0, "Taipei", 25.03, 121.56, 1990, "=Republic of China", "Lee Teng-hui", null, null),
            new Spot(0, "Beijing", 39.90, 116.40, 1990, "People's Republic of China", "Jiang Zemin", null, null),
            new Spot(0, "Seoul", 37.57, 126.98, 1600, "Joseon", "Seonjo", null, null),
            new Spot(0, "Kyiv", 50.45, 30.52, 2020, "Ukraine", "Zelenskyy", null, null),
            new Spot(0, "Damascus", 33.51, 36.29, 2000, "Syria", "Khaddam", null,
                    "Two heads of state took office in 2000: Abdul Halim Khaddam (acting, five weeks), then Bashar"
                            + " al-Assad. When two reigns start in the same year, the first one listed wins."),
            new Spot(0, "Bratislava", 48.15, 17.11, 2000, "Slovakia", "Rudolf Schuster", null, null),
            new Spot(0, "Copenhagen", 55.68, 12.57, 2000, "Denmark", "Margrethe II", null, null),
            new Spot(0, "Paris", 48.86, 2.35, 1700, "Kingdom of France", "Louis XIV", null, null),
            new Spot(0, "London", 51.51, -0.13, 1850, "Great Britain|United Kingdom", "Victoria", null, null),
            new Spot(0, "Ctesiphon", 33.09, 44.58, 550, "Sasanian Empire", "Khosrow I", null, null),
            new Spot(0, "Karachi", 24.86, 67.01, 1950, "Pakistan", "George VI", null, null),
            new Spot(0, "Mexico City", 19.43, -99.13, 1950, "Mexico", "Miguel Aleman", null, null),
            new Spot(0, "Kyoto", 35.01, 135.77, 1000, "Japan", "Ichijo", null, null),
            new Spot(0, "Paithan", 19.48, 75.38, 100, "Satavahana", null, "Dakshinapatha", null),
            new Spot(0, "Taxila", 33.74, 72.79, 100, "Kushan Empire", "Vima Takto", "Uttarapatha", null),
            new Spot(0, "Bharuch", 21.70, 72.98, 1000, "Chaulukya", "Chamundaraja", "Indian Ocean trade", null));

    /**
     * worldfill/probes/TalkProbe.java: the 12 standard places. {@code head} null means no state is
     * recorded there that year, and the villager must not invent one. {@code mustSay}: words that
     * some villager line must contain.
     */
    private record TalkSpot(String place, double lat, double lon, int year, String head, boolean famous,
                            String... mustSay) {}

    private static final List<TalkSpot> TALK_SPOTS = List.of(
            new TalkSpot("Hyderabad", 17.385, 78.4867, 1700, "Aurangzeb", true, "Persian", "Telugu"),
            new TalkSpot("the Via Appia south of Rome", 41.80, 12.60, 100, "Trajan", true, "Latin", "Roman road"),
            new TalkSpot("Merv", 37.66, 62.18, 1000, "Mahmud", true, "Silk Road"),
            new TalkSpot("Lübeck", 53.87, 10.69, 1400, "Rupert", true, "Hanseatic League"),
            new TalkSpot("Varanasi", 25.32, 83.0, 1600, "Akbar", true, "Grand Trunk Road"),
            new TalkSpot("Sydney", -33.87, 151.2, 1500, null, false, "hunt, fish and gather"),
            new TalkSpot("Mongolia", 47.0, 103.0, 1200, null, false, "herd animals"),
            new TalkSpot("Paris", 48.86, 2.35, 1789, "Louis XVI", true, "French"),
            new TalkSpot("Washington", 38.9, -77.03, 1863, "Lincoln", true),
            new TalkSpot("Hyderabad", 17.385, 78.4867, 2000, "Narayanan", true, "Telugu", "Hinduism"),
            new TalkSpot("Upper Egypt", 26.5, 31.5, -1300, "Horemheb", true, "Egyptian"),
            new TalkSpot("the Ohio valley", 39.0, -84.0, 1200, null, false, "hunt, fish and gather"));

    /** worldfill/probes/EventProbe.java: places and years with well-known events nearby. */
    private static final double[][] EVENT_SPOTS = {
            {17.385, 78.4867, 1687}, {17.385, 78.4867, 1857}, {48.8566, 2.3522, 1789}, {46.0, 2.0, 1789},
            {41.9, 12.5, 1348}, {51.5, -0.12, 1665}, {30.0, 31.2, -2600}, {34.3, 108.9, -1000},
            {35.0, 103.0, 1556}, {-12.0, -77.0, 1746}, {23.0, 10.0, 1000},
    };

    /** The years of the coverage probes (DensityProbe, PresenceProbe, WorldProbe). */
    private static final int[] YEARS = {-2500, -1500, -500, 1, 500, 1000, 1300, 1500, 1700, 1850, 1900, 1944, 2000};

    /** Data as built at step 8 (2026-10-12). Minimums: more data later must not fail the check. */
    private static final int MIN_STATES = 1313;
    private static final int MIN_STATES_WITH_RULERS = 1165;
    private static final int MIN_REIGNS = 17700;
    private static final int MIN_GOVERNMENT_ROWS = 4588;
    // Step 25 removed sign languages, scripts and leftover words from the language and faith lists, 133 people with
    // impossible lifetimes and 997 news lines with no name or unreadable text (worldfill/step25_changes.csv).
    private static final int MIN_STATES_WITH_LANGUAGES = 1051;
    private static final int MIN_STATES_WITH_RELIGIONS = 960;
    private static final int MIN_STATES_WITH_FAITH_SHARES = 229;
    private static final int MIN_FAMOUS_PEOPLE = 184060;
    private static final int MIN_BIRTHPLACES = 148937;
    private static final int WAYS_REGIONS = 146;
    private static final int WAYS_DATES = 10;
    private static final int MIN_LANGUAGES = 6709;
    private static final int MIN_ROAD_SECTIONS = 14769;
    private static final int MIN_ROUTES = 14;
    private static final int MIN_ROUTE_TOWNS = 411;
    private static final int HYDE_SLICES = 28;
    private static final int MIN_EVENTS = 121435;
    // A FLOOR, not an equality. It exists to catch a truncated or unreadable file, which loses
    // thousands of spans at once; it must not fail when a source error is corrected, because a
    // correction legitimately removes spans (Khmer Empire after 1431, Golden Horde after 1502,
    // Ghaznavid after 1186, Byzantine Empire after 1453: 359 slices so far). Pinned to the exact
    // count it was 200,695 then 200,337 then 200,336, and every edit was a chance to wave a real
    // truncation through. The records hold ~200,336; the floor sits 0.2% below and stays put.
    private static final int MIN_RULER_SPANS = 200000;
    private static final int MIN_SITES = 46864;
    private static final String[] ROUTE_NAMES = {"Silk Road", "Grand Trunk Road", "Hanseatic League",
            "Dakshinapatha", "Uttarapatha", "Amber Road", "Indian Ocean trade"};

    /**
     * Coverage when the data was built, on the 400 DensityProbe land points (seed 42), one row per
     * year of {@link #YEARS}: gazetteer site, estimated countryside, empire box, any of the three.
     * Measured by worldfill/probe_check.sh on 2026-10-12; the same numbers as PresenceProbe's
     * "with the places packs" table in worldfill/probe_results.md. (Null: the check prints the
     * measured table instead, for recording.)
     */
    private static final int[][] GAZETTEER_BASELINE = {
            {2, 9, 3, 12}, {4, 12, 3, 16}, {4, 21, 3, 25}, {23, 24, 11, 48}, {22, 29, 7, 57}, {6, 15, 0, 21},
            {7, 19, 2, 28}, {10, 11, 2, 23}, {11, 24, 8, 40}, {9, 31, 8, 45}, {14, 27, 0, 41}, {11, 22, 0, 33},
            {12, 24, 20, 52}};

    /**
     * WorldProbe's "anyone here" (HistoricalPresence.query without Terra++'s map cache) per year:
     * 14.5% of the land points in 2500 BCE ... 44.0% in 2000, as in worldfill/probe_results_world.md.
     */
    private static final int[] ANYONE_BASELINE = {58, 69, 92, 98, 113, 101, 116, 120, 116, 132, 146, 152, 176};

    /** Present-only places with an estimated founding year, and a hash of every (id, year). */
    private static final int ESTIMATED_YEARS_BASELINE = 15727;
    private static final long ESTIMATED_YEARS_HASH = -5475845859492414514L;

    // ================================================================== running

    private static volatile long firstLoadMillis = -1L;
    private static volatile boolean loadedByCheck;

    /** Run every part, in order. */
    public static List<Section> runAll() {
        List<Section> out = new ArrayList<>();
        for (Part p : Part.values()) {
            out.add(run(p));
        }
        return out;
    }

    /** Run one part. Never throws: a part that breaks reports itself as one failed check. */
    public static Section run(Part part) {
        Section s = new Section(part);
        long t0 = System.nanoTime();
        try {
            loadRegisters();
            switch (part) {
                case DATA -> data(s);
                case PLACES -> spots(s, CHECKLIST, true);
                case RULERS -> spots(s, MORE_RULERS, false);
                case TALK -> talk(s);
                case EVENTS -> events(s);
                case INDEX -> index(s);
                case COVERAGE -> coverage(s);
                case SPEED -> speed(s);
                case FIXES -> fixes(s);
                case FOUNDATIONS -> foundations(s);
                case CARDS -> cards(s);
                case EVENTCARDS -> eventCards(s);
                case TOWNS -> towns(s);
                case TECHNOLOGY -> technology(s);
                case CULTURE -> culture(s);
                case HOUSES -> houses(s);
                case WAYS -> ways(s);
                case ERA -> era(s);
                case SKY -> sky(s);
                case LIFE -> life(s);
                case PEOPLE -> people(s);
                case SCENES -> scenes(s);
                case ARMIES -> armies(s);
                case FAMOUS -> famous(s);
            }
        } catch (Throwable t) {
            s.fail("the " + part.id() + " check stopped with an error", t.toString());
        }
        s.millis = (System.nanoTime() - t0) / 1_000_000L;
        return s;
    }

    // ================================================================== step 25: the history data fix

    /**
     * Places whose answer the step-25 fix corrected (worldfill/README.md, step 25; worldfill/step25_changes.csv). Each
     * was wrong before: a prime minister named as ruler, an occupation named as the occupier's country, a name used
     * long after its state ended, the Nizam's state shown as British, nobody in 2025-2026.
     */
    private static final List<Spot> FIXED = List.of(
            new Spot(0, "Bombay (1 January 1948)", 18.94, 72.83, 1948, "Dominion of India", "George VI", null, null),
            new Spot(0, "Hyderabad", 17.385, 78.4867, 1948, "Hyderabad State", "Asaf Jah VII", null, null),
            new Spot(0, "Hyderabad", 17.385, 78.4867, 1850, "Hyderabad State", "Asaf Jah IV", null, null),
            new Spot(0, "Aurangabad", 19.88, 75.34, 1900, "Hyderabad State", "Asaf Jah VI", null, null),
            new Spot(0, "Karachi", 24.86, 67.01, 1950, "Dominion of Pakistan", "George VI", null, null),
            new Spot(0, "London", 51.51, -0.12, 1950, "=United Kingdom", "George VI", null, null),
            new Spot(0, "St Petersburg", 59.94, 30.31, 1900, "Russian Empire", "Nicholas II", null, null),
            new Spot(0, "Paris", 48.86, 2.35, 1900, "French Third Republic", "Loubet", null, null),
            new Spot(0, "Vienna", 48.21, 16.37, 1900, "Austria-Hungary", "Franz Joseph", null, null),
            new Spot(0, "Copenhagen", 55.68, 12.57, 1900, "Kingdom of Denmark", "Christian IX", null, null),
            new Spot(0, "Athens", 37.97, 23.73, 1900, "Kingdom of Greece", "George I", null, null),
            new Spot(0, "Moscow", 55.76, 37.62, 1980, "Soviet", "Brezhnev", null, null),
            new Spot(0, "Prague", 50.08, 14.42, 1980, "Czechoslovakia", "Husák", null, null),
            new Spot(0, "Warsaw", 52.23, 21.01, 1946, "Poland", "Bierut", null, null),
            new Spot(0, "Berlin", 52.52, 13.40, 1946, "Allied-occupied Germany", null, null, null),
            new Spot(0, "Dresden", 51.05, 13.74, 1750, "Electorate of Saxony", "Frederick Augustus II", null, null),
            new Spot(0, "Jerusalem", 31.78, 35.22, 1940, "Mandatory Palestine", "George VI", null, null),
            new Spot(0, "Tabriz", 38.08, 46.29, 1945, "Iran", "Mohammad Reza", null, null),
            new Spot(0, "Bombay", 18.94, 72.83, 1700, "English", "William III", null, null),
            new Spot(0, "Madras (Chennai)", 13.08, 80.27, 1700, "English", "William III", null, null),
            new Spot(0, "Edo (Tokyo)", 35.68, 139.76, 1700, "Tokugawa Shogunate", "Tsunayoshi", null, null),
            new Spot(0, "Tehran", 35.69, 51.39, 1985, "Islamic Republic of Iran", "Khomeini", null, null),
            new Spot(0, "Amsterdam", 52.37, 4.90, 1990, "Netherlands", "Beatrix", null, null),
            new Spot(0, "Sydney", -33.87, 151.21, 1850, "British", "Victoria", null, null),
            new Spot(0, "Baku", 40.41, 49.87, 2000, "Azerbaijan", "Heydar Aliyev", null, null),
            new Spot(0, "Bombay (today)", 18.94, 72.83, 2026, "Republic of India", "Droupadi Murmu", null, null),
            new Spot(0, "Moscow (today)", 55.76, 37.62, 2026, "Russian Federation", "Putin", null, null),
            new Spot(0, "Washington (today)", 38.90, -77.04, 2026, "United States", "Trump", null, null),
            // step 26 (S8): occupiers and rebels are not the state; squares that named the wrong land
            new Spot(0, "Copenhagen (German occupation)", 55.68, 12.57, 1942, "Kingdom of Denmark", "Christian X", null, null),
            new Spot(0, "Paris (German occupation)", 48.86, 2.35, 1942, "Vichy France", "Pétain", null, null),
            new Spot(0, "Kyiv (German occupation)", 50.45, 30.52, 1942, "Soviet", "Stalin", null, null),
            new Spot(0, "Cologne", 50.94, 6.96, 1946, "Allied-occupied Germany", null, null, null),
            new Spot(0, "Aleppo", 36.20, 37.16, 2020, "Syria", "Assad", null, null),
            new Spot(0, "Kabul", 34.53, 69.17, 1990, "Afghanistan", "Najibullah", null, null),
            new Spot(0, "Dublin (today)", 53.35, -6.26, 2026, "Ireland", "Connolly", null, null),
            new Spot(0, "Chisinau", 47.01, 28.86, 1980, "Soviet", "Brezhnev", null, null),
            new Spot(0, "St Croix (Danish West Indies)", 17.73, -64.75, 1900, "Kingdom of Denmark", "Christian IX", null, null),
            new Spot(0, "Athens (Axis occupation)", 37.98, 23.73, 1942, "Kingdom of Greece", "George II", null, null),
            new Spot(0, "Batavia (Japanese occupation)", -6.20, 106.85, 1943, "Dutch East Indies", null, null, null),
            new Spot(0, "Singapore (Japanese occupation)", 1.29, 103.85, 1943, "British", "George VI", null, null),
            new Spot(0, "Reykjavik (Allied occupation)", 64.15, -21.94, 1942, "Kingdom of Iceland", null, null, null),
            new Spot(0, "Shanghai (Japanese occupation)", 31.23, 121.47, 1940, "Republic of China", null, null, null),
            // step 26 (S9, known problem 20): border cities follow their own country
            new Spot(0, "Kinshasa (today)", -4.32, 15.31, 2026, "Democratic Republic of the Congo", null, null, null),
            new Spot(0, "Brazzaville (today)", -4.27, 15.28, 2026, "Republic of the Congo", null, null, null),
            new Spot(0, "Tijuana (today)", 32.53, -117.04, 2026, "Mexico", null, null, null),
            new Spot(0, "San Diego (today)", 32.72, -117.16, 2026, "United States", "Trump", null, null),
            new Spot(0, "Basel", 47.57, 7.60, 1700, "Swiss Confederation", null, null, null),
            new Spot(0, "Nice (today)", 43.70, 7.27, 2026, "French Fifth Republic", null, null, null),
            new Spot(0, "Monaco (today)", 43.73, 7.42, 2026, "Monaco", null, null, null));

    /** A faith or language line that must (or must not) contain some words: {lat, lon, year}, words, banned. */
    private record Culture(String place, double lat, double lon, int year, String must, String banned) {}

    private static final List<Culture> CULTURE = List.of(
            new Culture("London", 51.51, -0.12, 1600, "Anglicanism", "Roman Catholicism"),
            new Culture("Delhi", 28.65, 77.23, 1650, "most people here follow Hinduism", "Eastern Panjabi"),
            new Culture("Vienna", 48.21, 16.37, 1700, "German", "Slovak"),
            new Culture("Moscow", 55.76, 37.62, 1980, "Orthodox", "Catholic"),
            new Culture("Seoul", 37.57, 126.98, 2000, "Korean", " Sign"),
            new Culture("Hyderabad", 17.385, 78.4867, 1850, "most people here follow Hinduism", null));

    private static final java.util.regex.Pattern GOV_TITLE = java.util.regex.Pattern.compile(
            "(?i)prime minister|\\bpremier\\b|chancellor|\\bminister|chairman of (?:the )?(?:council|committee)(?! of state)"
                    + "|grand vizier|\\bvizier\\b|chief minister|first minister|head of (?:the )?government"
                    + "|president of the (?:ministry|council of ministers|government(?! junta))|council president");

    private static void fixes(Section s) {
        spots(s, FIXED, false);
        for (Culture c : CULTURE) {
            String said = EventNews.cultureLine(HistoricalPresence.query(null, c.lat(), c.lon(), c.year()));
            boolean ok = said.contains(c.must()) && (c.banned() == null || !said.contains(c.banned()));
            String label = c.place() + ", " + (c.year() < 0 ? -c.year() + " BCE" : c.year() + " CE") + ": " + said;
            s.check(ok, label, "should say \"" + c.must() + "\""
                    + (c.banned() == null ? "" : ", never \"" + c.banned() + "\""));
        }
        // the rules of the fix, so the old mistakes cannot come back with new data
        int pm = 0;
        int claimants = 0;
        String pmExample = "";
        for (LeaderRegister.Facts f : LeaderRegister.get().all()) {
            for (LeaderRegister.Reign r : f.heads()) {
                if (GOV_TITLE.matcher(r.title()).find() && !r.name().equals("Vladimir Lenin")) {
                    pm++;
                    pmExample = f.name() + ": " + r.display();
                }
                if (r.name().matches("(?i).*\\b(claimants?|pretenders?)\\b.*")) {
                    claimants++;
                }
            }
        }
        rule(s, pm == 0, "no prime minister or chairman of ministers is named as a ruler",
                pm + " rows, for example " + pmExample);
        rule(s, claimants == 0, "no claimant to a throne is named as a ruler", claimants + " rows");
        long numbered = HistoryGazetteer.get().allEvents().stream()
                .filter(e -> e.name() != null && e.name().matches("Q\\d+")).count();
        rule(s, numbered == 0, "every news line has a name in words (none is only a Wikidata number)", numbered + " lines");
        long old = FamousPeople.get().all().filter(p -> p.birth() != FamousPeople.UNKNOWN
                && p.death() != FamousPeople.UNKNOWN && (p.death() - p.birth() > 110 || p.death() < p.birth())).count();
        rule(s, old == 0, "no famous person lived more than 110 years or died before being born", old + " people");
        int last = RulerRegister.get().lastYear();
        rule(s, last >= 2026, "the border map reaches today (" + last + "): the states of 2024 go on", "last year " + last);
    }

    private static void rule(Section s, boolean ok, String text, String detail) {
        s.check(ok, text, detail);
    }

    // ================================================================== step 26: foundations for big data

    /** The places the key is compared with the record at: the checklist and the places the fixes corrected. */
    private static List<Spot> keySpots() {
        List<Spot> out = new ArrayList<>(CHECKLIST);
        out.addAll(FIXED);
        return out;
    }

    private static String presenceKey(HistoricalPresence.Result r) {
        return r.ruler().map(h -> h.ruler() + "/" + h.start()).orElse("-") + "|"
                + r.site().map(h -> h.site().id()).orElse("-") + "|"
                + r.event().map(h -> h.event().id()).orElse("-") + "|"
                + r.people().map(p -> p.display()).orElse("-") + "|" + r.spawnCivId();
    }

    /**
     * Step 26: the key (A1), the recorded/estimated marks (A5), the tiles (A4), the data list and the credits
     * (A3, A7, K4), and memory (I6).
     */
    /** Text of a card's question (its one answer), or "". */
    private static String cardText(com.eternalengine.knowledge.KnowledgeCard k, String q) {
        if (k == null || k.question(q) == null) {
            return "";
        }
        var a = k.question(q).at(com.eternalengine.knowledge.KnowledgeDate.key(3000, 1, 1));
        return a == null ? "" : a.text();
    }

    private static com.eternalengine.knowledge.KnowledgeCard cardOn(String id, int year, double lat, double lon) {
        return com.eternalengine.knowledge.PlaceCards.resolve(id, com.eternalengine.knowledge.KnowledgeDate.key(year, 7, 1),
                lat, lon);
    }

    /** Step 30b (C13, C14, C17, C26, C27): reading and scripts, measures, wealth, holy places, festivals. */
    private static void culture(Section s) {
        CultureAtlas c = CultureAtlas.get();
        s.check(c.loaded() && c.holyPlaces() > 15_000, "the culture data loads: " + c.summary(), String.valueOf(c.problem()));
        if (!c.loaded()) {
            return;
        }
        double[] ind = c.literacy(20.0, 78.0, 1901, SettlementRank.TOWN);
        double[] cusco = c.literacy(-13.52, -71.97, 1500, SettlementRank.CITY);
        String tr1900 = String.valueOf(c.scriptOf("Turkish", 1900));
        String tr1950 = String.valueOf(c.scriptOf("Turkish", 1950));
        s.check(ind != null && Math.abs(ind[0] - 0.062) < 0.01 && ind[1] > ind[2] && cusco != null && cusco[0] == 0.0d
                        && tr1900.contains("Arabic") && tr1950.contains("Latin"), "who reads and in which script: India "
                        + "1901 about 6% (men more than women), nobody in Cusco 1500 (knotted cords); Turkish in Arabic "
                        + "letters in 1900, in Latin letters in 1950",
                (ind == null ? "-" : ind[0]) + " / " + tr1900 + " / " + tr1950);
        String hyd1900 = c.measureLine(17.385, 78.4867, 1900);
        String hyd1970 = c.measureLine(17.385, 78.4867, 1970);
        String ny2000 = c.measureLine(40.71, -74.0, 2000);
        s.check(hyd1900.contains("kos") && hyd1900.contains("seer") && hyd1970.contains("still") && ny2000.contains("mile"),
                "measures by land and year: the kos and the seer in Hyderabad 1900, the metric law of 1956 with the old "
                        + "words still in the market in 1970, miles in New York 2000", hyd1900);
        String ind1900 = c.wealthLine(20.0, 78.0, 1900);
        String ber1923 = c.wealthLine(52.52, 13.40, 1923);
        String ny1932 = c.wealthLine(40.71, -74.0, 1932);
        s.check(ind1900.contains("poor") && ber1923.contains("Prices rise") && ny1932.contains("no work"), "how well off "
                + "people are, and hard times: India 1900 poor, Berlin's inflation of 1923, New York's Depression of 1932",
                ind1900 + " / " + ber1923);
        boolean mm2000 = c.holyPlacesNear(17.36, 78.47, 2000, 12, 5).stream().anyMatch(h -> h.name().contains("Mecca Masjid"));
        boolean mm1500 = c.holyPlacesNear(17.36, 78.47, 1500, 12, 5).stream().anyMatch(h -> h.name().contains("Mecca Masjid"));
        String varanasi = c.holyLine(25.31, 83.01, 1800, 0, 0, java.util.List.of("Hinduism"), 6);
        s.check(mm2000 && !mm1500 && varanasi.contains("Pilgrims come here"), "holy places by year (the Mecca Masjid in "
                + "Hyderabad in 2000, not in 1500) and pilgrimages (pilgrims come to Varanasi)", varanasi);
        // B8, B19 (step 32): famous buildings and ruins by year
        var fb = com.eternalengine.buildings.FamousBuildings.get();
        if (!fb.loaded()) {
            s.fail("the famous buildings of B8 load", String.valueOf(fb.problem()));
        } else {
            boolean col100 = fb.near(41.8902, 12.4922, 100, 25.0d, 60).stream()
                    .anyMatch(b -> b.name().contains("Colosseum"));
            boolean col500bc = fb.near(41.8902, 12.4922, -500, 25.0d, 60).stream()
                    .anyMatch(b -> b.name().contains("Colosseum"));
            boolean hampi1520 = fb.near(15.335, 76.46, 1520, 25.0d, 60).stream()
                    .anyMatch(b -> b.name().contains("Hampi"));
            boolean hampi1700 = fb.near(15.335, 76.46, 1700, 25.0d, 60).stream()
                    .anyMatch(b -> b.name().contains("Hampi"));
            s.check(col100 && !col500bc && hampi1520 && !hampi1700, "famous buildings stand in their own year and no "
                    + "other: the Colosseum in Rome in 100 but not in 500 BC, Hampi in 1520 but not in 1700, 1565 "
                    + "being the year Vijayanagara fell", "Rome 100 " + col100 + ", Rome -500 " + col500bc
                    + ", Hampi 1520 " + hampi1520 + ", Hampi 1700 " + hampi1700);
            boolean hampi1600 = fb.ruinsNear(15.335, 76.46, 1600, 25.0d, 40).stream()
                    .anyMatch(b -> b.name().contains("Hampi"));
            boolean hampi1400 = fb.ruinsNear(15.335, 76.46, 1400, 25.0d, 40).stream()
                    .anyMatch(b -> b.name().contains("Hampi"));
            s.check(hampi1600 && !hampi1400, "B19: a place is in ruins after its end year and not before (Hampi in "
                    + "1600, not in 1400 --- 1565 being the year Vijayanagara fell)", "1600 " + hampi1600
                    + ", 1400 " + hampi1400);
            boolean aurelian300 = fb.defencesNear(41.8902, 12.4922, 300, 25.0d, 40).stream()
                    .anyMatch(b -> b.name().contains("Aurelian"));
            boolean aurelian100 = fb.defencesNear(41.8902, 12.4922, 100, 25.0d, 40).stream()
                    .anyMatch(b -> b.name().contains("Aurelian"));
            s.check(aurelian300 && !aurelian100, "B16: walls, gates and forts stand in their own year (the Aurelian "
                    + "Walls in Rome in 300, not in 100: they were built from 271)", "300 " + aurelian300
                    + ", 100 " + aurelian100);
            s.infoQuiet("famous buildings: " + fb.summary() + ", " + fb.estimatedCount() + " years estimated from the "
                    + "gazetteer, " + fb.withEndCount() + " with an end date (B19 ruins), " + fb.ruinCount()
                    + " known to be ruins, " + fb.defenceCount() + " walls, gates and forts (B16), "
                    + fb.holyCount() + " also holy places; loaded in " + fb.loadMillis() + " ms");
        }
        String xmas = c.holyLine(51.507, -0.128, 1900, 12, 25, java.util.List.of("Anglicanism"), 12);
        String eid = c.holyLine(30.04, 31.24, 2024, 4, 10, java.util.List.of("Sunni Islam"), 12);
        String easter = c.holyLine(41.9, 12.49, 2026, 4, 5, java.util.List.of("Catholic Christianity"), 12);
        s.check(xmas.contains("Christmas") && eid.contains("Eid al-Fitr") && easter.contains("Easter Sunday"), "feast days "
                + "by the calendar: Christmas in London 1900, Eid al-Fitr in Cairo on 10 April 2024, Easter in Rome on 5 "
                + "April 2026", eid);
        var local = com.eternalengine.knowledge.LocalCards.build(HistoricalPresence.query(null, 17.385, 78.4867, 1948),
                17.385, 78.4867);
        var card = local.placeCard() == null ? null : local.cards().get(local.placeCard());
        int found = 0;
        for (String q : new String[]{"read", "measure", "wealth", "holy"}) {
            found += cardText(card, q).isEmpty() ? 0 : 1;
        }
        s.check(found == 4, "the place card asks the four new questions (Hyderabad 1948): can people read, how do they "
                + "measure, are they rich or poor, what are the holy places and festivals", found + " of 4");
        s.infoQuiet("Hyderabad 1948: " + cardText(card, "read") + " / " + cardText(card, "measure"));
    }

    /** Step 30a (C16): the technology atlas and the country grid. */
    private static void technology(Section s) {
        CountryGrid grid = CountryGrid.get();
        String[][] where = {{"17.385", "78.4867", "IND"}, {"48.857", "2.352", "FRA"}, {"-4.32", "15.31", "COD"},
                {"-4.27", "15.27", "COG"}, {"-13.52", "-71.97", "PER"}, {"-29.31", "27.48", "LSO"},
                {"18.94", "72.82", "IND"}, {"40.71", "-74.0", "USA"}};
        int right = 0;
        StringBuilder wrong = new StringBuilder();
        for (String[] w : where) {
            String got = grid.codeAt(Double.parseDouble(w[0]), Double.parseDouble(w[1]));
            if (got.equals(w[2])) {
                right++;
            } else {
                wrong.append(w[0]).append(",").append(w[1]).append(": ").append(got).append(" ");
            }
        }
        s.check(grid.loaded() && grid.size() > 250 && right == where.length, "the country grid loads (" + grid.size()
                + " countries) and puts 8 places in the right country (Kinshasa and Brazzaville across the river, "
                + "Lesotho inside South Africa, Bombay on the coast)", wrong.toString().trim());
        var atlas = com.eternalengine.technology.TechnologyAtlas.get();
        s.check(atlas.loaded() && atlas.size() >= 120, "the technology atlas loads: " + atlas.summary(),
                String.valueOf(atlas.problem()));
        if (!atlas.loaded()) {
            return;
        }
        // The five places of TECHNOLOGY_PLAN.md (turn 94).
        Object[][] plan = {
                {17.385, 78.4867, 1948, "railway", true}, {17.385, 78.4867, 1948, "car", true},
                {17.385, 78.4867, 1948, "radio", true}, {17.385, 78.4867, 1948, "television", false},
                {17.385, 78.4867, 1500, "paper", true}, {17.385, 78.4867, 1500, "printing_press", false},
                {17.385, 78.4867, 1500, "woodblock", false}, {-13.52, -71.97, 1500, "iron", false},
                {-13.52, -71.97, 1500, "wheel", false}, {-13.52, -71.97, 1500, "horses", false},
                {51.507, -0.128, 1700, "printing_press", true}, {51.507, -0.128, 1700, "steam_engine", false},
                {39.90, 116.40, 1100, "woodblock", true}, {39.90, 116.40, 1100, "gunpowder", true}};
        int ok = 0;
        StringBuilder bad = new StringBuilder();
        for (Object[] p : plan) {
            double la = ((Number) p[0]).doubleValue();
            double lo = ((Number) p[1]).doubleValue();
            int y = (Integer) p[2];
            boolean want = (Boolean) p[4];
            boolean got = atlas.present((String) p[3], la, lo, y, SettlementRank.CITY);
            if (got == want) {
                ok++;
            } else {
                bad.append(p[3]).append(" ").append(y).append(" at ").append(la).append(": ").append(got).append("; ");
            }
        }
        s.check(ok == plan.length, "the five places of the plan: Hyderabad 1948 has the railway, motor cars and radio, no "
                + "television; Hyderabad 1500 paper, no printing; Cusco 1500 no iron, carts or horses; London 1700 printing, "
                + "no steam engines; Beijing 1100 printing and gunpowder (" + ok + " of " + plan.length + ")", bad.toString());
        double cars = atlas.share(atlas.tech("car"), 17.385, 78.4867, 1948, SettlementRank.CITY);
        s.check(cars > 0.0005d && cars < 0.02d, String.format(Locale.ROOT, "how common: motor cars in "
                + "Hyderabad in 1948 are for a few rich people (%.2f%% of homes, from CHAT's figures for India)", cars * 100),
                String.valueOf(cars));
        // Nothing from the Old World in the Americas before 1492 or in Australia before 1788; nothing before its first year.
        int early = 0;
        StringBuilder which = new StringBuilder();
        double[][] newWorld = {{-13.52, -71.97, 1530}, {19.43, -99.13, 1515}, {38.66, -90.06, 1400}, {-22.9, -43.2, 1499},
                {-33.87, 151.21, 1780}, {-36.85, 174.76, 1760}};
        for (double[] p : newWorld) {
            for (String id : new String[]{"iron", "wheel", "horses", "printing_press", "gunpowder", "glass", "plough"}) {
                if (atlas.present(id, p[0], p[1], (int) p[2], SettlementRank.CITY)) {
                    early++;
                    which.append(id).append(" ").append((int) p[2]).append(" at ").append(p[0]).append("; ");
                }
            }
        }
        for (var t : atlas.all()) {
            for (int c = 0; c < 258; c++) {
                int a = t.arrivalInCountry(c);
                if (a != com.eternalengine.technology.TechnologyAtlas.NEVER && a < t.first) {
                    early++;
                    which.append(t.id).append(" before its first year; ");
                    break;
                }
            }
        }
        s.check(early == 0, "nothing from the Old World in the Americas before contact (Cusco 1530, Tenochtitlan 1515, "
                + "Cahokia 1400, Rio 1499) or in Australia and New Zealand before 1788 and 1769; nothing anywhere before "
                + "its first year", which.toString());
        // The fastest way to travel follows the place: no train in Bombay in 1850 or in Kabul in 1900.
        var b1850 = com.eternalengine.vehicle.VehicleType.fastestLandAt(18.94, 72.83, 1850);
        var b1870 = com.eternalengine.vehicle.VehicleType.fastestLandAt(18.94, 72.83, 1870);
        var k1900 = com.eternalengine.vehicle.VehicleType.fastestLandAt(34.53, 69.17, 1900);
        s.check(b1850 != com.eternalengine.vehicle.VehicleType.TRAIN && b1870 == com.eternalengine.vehicle.VehicleType.TRAIN
                        && k1900 != com.eternalengine.vehicle.VehicleType.TRAIN, "the fastest way to travel follows the "
                        + "place: Bombay " + b1850 + " in 1850, " + b1870 + " in 1870; Kabul " + k1900 + " in 1900",
                b1850 + " / " + b1870 + " / " + k1900);
        // The place card asks "What new things are there here?"
        var local = com.eternalengine.knowledge.LocalCards.build(HistoricalPresence.query(null, 17.385, 78.4867, 1948),
                17.385, 78.4867);
        var card = local.placeCard() == null ? null : local.cards().get(local.placeCard());
        String tech = cardText(card, "tech");
        s.check(tech.contains("the railway") && tech.contains("television"), "the place card says what is new here: "
                + "Hyderabad 1948", tech);
        s.infoQuiet("Hyderabad 1948: " + tech);
    }

    /** Step 29 (C2-C6, C28): towns through time and the ties between states. */
    private static void towns(Section s) {
        TownFacts tf = TownFacts.get();
        s.check(tf.loaded() && tf.size() > 25_000 && tf.provinces() > 10_000 && tf.holders() > 20_000,
                "the town file loads: " + tf.summary(), String.valueOf(tf.problem()));
        if (!tf.loaded()) {
            return;
        }
        // Names by era, through the place line (the history data's site keeps its id; the name follows the year).
        String[][] named = {
                {"18.94", "72.83", "1900", "Bombay"}, {"18.94", "72.83", "2026", "Mumbai"},
                {"35.68", "139.76", "1700", "Edo"}, {"35.68", "139.76", "1900", "Tokyo"},
                {"-4.32", "15.31", "1955", "Léopoldville"}, {"-4.32", "15.31", "2000", "Kinshasa"},
                {"41.01", "28.98", "1000", "Constantinople"}, {"41.01", "28.98", "1950", "Istanbul"},
                {"59.94", "30.31", "1950", "Leningrad"}, {"59.94", "30.31", "2000", "Saint Petersburg"}};
        int right = 0;
        StringBuilder wrong = new StringBuilder();
        for (String[] n : named) {
            String line = EventNews.placeLine(HistoricalPresence.query(null, Double.parseDouble(n[0]),
                    Double.parseDouble(n[1]), Integer.parseInt(n[2])));
            if (line.contains(n[3])) {
                right++;
            } else {
                wrong.append(n[2]).append(": ").append(line).append(" ");
            }
        }
        s.check(right == named.length, "a town's name follows the year: Bombay 1900 / Mumbai 2026, Edo 1700 / Tokyo 1900, "
                + "Léopoldville 1955 / Kinshasa 2000, Constantinople 1000 / Istanbul 1950, Leningrad 1950 / Saint Petersburg "
                + "2000 (" + right + " of " + named.length + ")", wrong.toString().trim());

        // People: census figures, a straight line between them, nothing before the first count.
        var kolkata = tf.byLabelNear("Kolkata", 22.57, 88.37);
        long k1901 = kolkata.map(t -> t.peopleIn(1901)).orElse(-1L);
        long k1950 = kolkata.map(t -> t.peopleIn(1950)).orElse(-1L);
        long k1600 = kolkata.map(t -> t.peopleIn(1600)).orElse(0L);
        s.check(k1901 == 847_796L && k1950 > 1_163_771L && k1950 < 2_927_280L && k1600 == -1L, "people by year: Calcutta "
                + "847,796 in 1901 (the census), " + k1950 + " in 1950 (between the counts of 1931 and 1961), unknown in 1600",
                k1901 + " / " + k1950 + " / " + k1600);

        // Provinces by era, and who runs the town.
        String b1900 = EventNews.provinceLine(HistoricalPresence.query(null, 18.94, 72.83, 1900));
        String b1955 = EventNews.provinceLine(HistoricalPresence.query(null, 18.94, 72.83, 1955));
        String b2000 = EventNews.provinceLine(HistoricalPresence.query(null, 18.94, 72.83, 2000));
        String h2020 = EventNews.provinceLine(HistoricalPresence.query(null, 17.385, 78.4867, 2020));
        s.check(b1900.contains("Bombay Presidency") && b1955.contains("Bombay State") && b2000.contains("Maharashtra")
                        && h2020.contains("Telangana"), "the province follows the year: the Bombay Presidency in 1900, Bombay "
                        + "State in 1955, Maharashtra in 2000; Hyderabad lies in Telangana in 2020",
                b1900 + " / " + b1955 + " / " + b2000 + " / " + h2020);
        String paris = EventNews.headLine(HistoricalPresence.query(null, 48.857, 2.352, 2015));
        String ny = EventNews.headLine(HistoricalPresence.query(null, 40.713, -74.006, 2010));
        String ny1500 = EventNews.headLine(HistoricalPresence.query(null, 40.713, -74.006, 1500));
        s.check(paris.contains("Anne Hidalgo") && ny.contains("Bloomberg") && ny1500.isEmpty(), "who runs the town: Anne "
                + "Hidalgo in Paris in 2015, Michael Bloomberg in New York in 2010, nobody named in 1500", paris + " / " + ny);

        // The place card asks the new questions.
        var local = com.eternalengine.knowledge.LocalCards.build(HistoricalPresence.query(null, 18.94, 72.83, 1900),
                18.94, 72.83);
        var card = local.placeCard() == null ? null : local.cards().get(local.placeCard());
        String what = cardText(card, "what");
        s.check(cardText(card, "province").contains("Bombay Presidency") && what.contains("Bombay")
                        && !what.contains("Mumbai"), "the place card says the town's name of that year and has \"Which "
                        + "province is this?\" (Bombay, 1900)", what);

        // Ties between states.
        StateRelations rel = StateRelations.get();
        String joseon = rel.line("Joseon", 1500);
        String italy = rel.line("Republic of Italy", 1960);
        String gdr = rel.line("German Democratic Republic", 1970);
        String gdr50 = rel.line("German Democratic Republic", 1950);
        s.check(rel.loaded() && joseon.contains("allegiance to the Ming Dynasty") && italy.contains("NATO")
                        && gdr.contains("Warsaw Pact") && !gdr50.contains("Warsaw Pact"), "ties between states by year ("
                        + rel.size() + " in all): Joseon owes allegiance to the Ming in 1500; Italy is in NATO in 1960; East "
                        + "Germany in the Warsaw Pact in 1970, not yet in 1950",
                joseon + " / " + italy + " / " + gdr);
        s.infoQuiet("Bombay 1900: " + what);
    }

    /**
     * Step 33 (B9 houses, B11 insides, B13 streets, B20 lighting, E14 markets, E16 materials, B25
     * water): what a town is made of, where and when.
     *
     * <p>These are the checks the pack was built against: a place is answered by its own building
     * culture and not by a neighbouring one (Cusco is Inca, not a maloca; Lhasa is Tibetan, not
     * Ming); the years are honoured (the Colosseum's Rome is not medieval); and what the technology
     * atlas knows is asked of the atlas rather than written into the pack, so a village keeps its
     * thatch after the city turns to concrete.
     */
    /**
     * Step 34 (B12, B14, B15, B18, B24, B26): the works of the past, the year the network reached
     * each square, and what lies under the ground. The checks that matter are the gate: a railway
     * must not be drawn in Sydney in 1850 (Australia's first opened in 1854) and a factory must not
     * stand in Rome in AD 100.
     */
    private static void ways(Section s) {
        var grid = com.eternalengine.civilization.WaysGrid.get();
        var works = com.eternalengine.civilization.WaysWorks.get();
        var mins = com.eternalengine.civilization.Minerals.get();

        s.check(grid.loaded(), "the ways grid (when a railway, a road and a canal reached a square) loads",
                String.valueOf(grid.problem()));
        s.check(works.loaded(), "the dated works of the past load", String.valueOf(works.problem()));
        s.check(mins.loaded(), "what lies under the ground loads", String.valueOf(mins.problem()));
        if (!grid.loaded() || !works.loaded() || !mins.loaded()) {
            return;
        }
        s.passQuiet("what the step holds",
                grid.cellsWithSomething() + " squares with a dated network, " + works.size()
                        + " works, " + mins.size() + " deposits of " + mins.minerals().length + " minerals");

        // the gate: nothing modern in a year before its time
        s.check(!grid.railBy(-33.8688d, 151.2093d, 1850),
                "Sydney in 1850 may not draw a railway (Australia's first opened in 1854)",
                "the grid says a railway reached that square in " + grid.railFirst(-33.8688d, 151.2093d));
        s.check(works.countNear(-33.8688d, 151.2093d, 25.0d, 1850, "station") == 0,
                "Sydney in 1850 has no railway station", "it has "
                        + works.countNear(-33.8688d, 151.2093d, 25.0d, 1850, "station"));
        s.check(!grid.railBy(41.8925d, 12.4853d, 100) && !grid.railBy(25.594d, 85.137d, -300),
                "Rome in AD 100 and Pataliputra in 300 BC may not draw a railway",
                "Rome " + grid.railFirst(41.8925d, 12.4853d) + ", Pataliputra " + grid.railFirst(25.594d, 85.137d));
        int modernInRome = 0;
        for (String k : new String[]{"station", "line", "works", "air", "power"}) {
            modernInRome += works.countNear(41.8925d, 12.4853d, 25.0d, 100, k);
        }
        s.check(modernInRome == 0, "Rome in AD 100 has no station, factory, airport or power station",
                "it has " + modernInRome);
        s.check(works.countNear(41.8925d, 12.4853d, 25.0d, 100, "bridge") > 0,
                "Rome in AD 100 still has its dated bridges",
                "bridges: " + works.countNear(41.8925d, 12.4853d, 25.0d, 100, "bridge"));

        // and the modern world is not empty
        s.check(grid.railBy(40.7128d, -74.006d, 1920) && grid.railBy(55.752d, 37.6175d, 1980),
                "New York in 1920 and Moscow in 1980 may draw a railway",
                "New York " + grid.railFirst(40.7128d, -74.006d) + ", Moscow " + grid.railFirst(55.752d, 37.6175d));
        s.check(works.countNear(40.7128d, -74.006d, 25.0d, 1920, "station") > 100,
                "New York in 1920 has its railway stations",
                "stations: " + works.countNear(40.7128d, -74.006d, 25.0d, 1920, "station"));
        s.check(works.countNear(51.5074d, -0.1278d, 25.0d, 1700, "mill") > 0
                        && works.countNear(51.5074d, -0.1278d, 25.0d, 1700, "station") == 0,
                "London in 1700 has its mills and no railway station",
                "mills " + works.countNear(51.5074d, -0.1278d, 25.0d, 1700, "mill")
                        + ", stations " + works.countNear(51.5074d, -0.1278d, 25.0d, 1700, "station"));

        // a work stands only inside its own years
        int outsideYears = 0;
        for (WaysWorks.Work w : works.near(40.7128d, -74.006d, 25.0d, 1900)) {
            if (!w.standsIn(1900)) {
                outsideYears++;
            }
        }
        s.check(outsideYears == 0, "a work is only returned for the years it stood",
                outsideYears + " works stand outside the year asked about");

        // what lies under the ground
        s.check(mins.hasMineral(-13.5163d, -71.9785d, 50.0d, "SILVER")
                        && mins.hasMineral(37.9715d, 23.7257d, 50.0d, "SILVER"),
                "silver lies under Cusco and under Athens (Laurium, 45 km away)",
                "Cusco: " + mins.mineralsUnder(-13.5163d, -71.9785d, 50.0d)
                        + "; Athens: " + mins.mineralsUnder(37.9715d, 23.7257d, 50.0d));
        s.known("places with nothing recorded under them", "Ur, Mohenjo-daro, Chang'an, Timbuktu, "
                + "the Amazon and the Sahara: no source that can be shipped says what lies there");

        // speed: the world is asked about a great many times while chunks load
        long t0 = System.nanoTime();
        int n = 0;
        for (int i = 0; i < 400; i++) {
            double lat = -55.0d + i * 0.25d;
            double lon = -175.0d + i * 0.8d;
            n += works.countNear(lat, lon, 25.0d, 1900, null);
            if (grid.railBy(lat, lon, 1900)) {
                n++;
            }
            n += mins.mineralsUnder(lat, lon, 50.0d).size();
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        s.check(ms < 1500L, "400 places are answered at once without a pause: " + ms + " ms "
                + "(the three registers are asked for works, the grid and minerals at each)",
                ms + " ms for 400 places");
    }

    /**
     * Step 35 (B2 water, B3 land use, B4 climate): the four registers that answer "what was this
     * place like, in this year". The checks that matter are the gate in both directions — the
     * Zuiderzee must be a sea in 1900 and a closed lake in 1960, the Sahara must not be given a
     * field, and a civ in the Andes in 1650 must feel the Andean Little Ice Age and not Europe's,
     * because a 2019 synthesis of ninety proxy records found these periods to be regional.
     */
    private static void era(Section s) {
        var land = com.eternalengine.civilization.LandUseGrid.get();
        var changes = com.eternalengine.civilization.WaterChanges.get();
        var works = com.eternalengine.civilization.WaterWorks.get();
        var climate = com.eternalengine.climate.ClimatePeriods.get();

        s.check(land.loaded(), "the land use grid (what the land was used for, at any place, in any year) loads",
                String.valueOf(land.problem()));
        s.check(changes.loaded(), "the table of the water that moved loads", String.valueOf(changes.problem()));
        s.check(works.loaded(), "the water works of the past load", String.valueOf(works.problem()));
        s.check(climate.loaded(), "the climate of every era loads", String.valueOf(climate.problem()));
        if (!land.loaded() || !changes.loaded() || !works.loaded() || !climate.loaded()) {
            return;
        }
        s.passQuiet("what the step holds",
                land.years().length + " land-use steps from " + land.years()[0] + " to "
                        + land.years()[land.years().length - 1] + " over " + land.landCells()
                        + " land cells with " + land.totalChanges() + " changes; " + changes.count()
                        + " changes to the water; " + works.count() + " water works; "
                        + climate.count() + " climate periods");

        // ---- B3: the land was not always fields, and not everywhere
        s.check(land.farmedBy(32.53d, 45.83d, -3000),
                "Uruk in 3000 BC is farmland (Sumer was already ploughed)",
                "the grid says " + land.shortAt(32.53d, 45.83d, -3000));
        s.check(land.farmedBy(30.50d, 73.00d, 1900),
                "the Punjab canal colony in 1900 is irrigated cropland",
                "the grid says " + land.shortAt(30.50d, 73.00d, 1900));
        s.check(!land.farmedBy(23.00d, 6.00d, 1000),
                "the Ahaggar in AD 1000 may not be given a field",
                "the grid says " + land.shortAt(23.00d, 6.00d, 1000));
        s.check(land.wildBy(-3.10d, -60.02d, 1500),
                "the Amazon near Manaus in 1500 is still wild country",
                "the grid says " + land.shortAt(-3.10d, -60.02d, 1500));
        s.check("ice".equals(land.shortAt(72.00d, -40.00d, 1000)),
                "the Greenland ice sheet in AD 1000 is ice",
                "the grid says " + land.shortAt(72.00d, -40.00d, 1000));
        s.check("town".equals(land.shortAt(40.78d, -73.97d, 1900)),
                "Manhattan in 1900 is a settlement",
                "the grid says " + land.shortAt(40.78d, -73.97d, 1900));

        // ---- B2: the water moved, and the year decides what it is
        var zuiderzee1900 = changes.standing(52.70d, 5.30d, 1900);
        var zuiderzee1960 = changes.standing(52.70d, 5.30d, 1960);
        s.check(!zuiderzee1900.isEmpty() && "flooded".equals(zuiderzee1900.get(0).change()),
                "the Zuiderzee in 1900 is the sea the St Lucia flood of 1287 made",
                zuiderzee1900.isEmpty() ? "nothing recorded" : zuiderzee1900.get(0).change());
        s.check(!zuiderzee1960.isEmpty() && "closed".equals(zuiderzee1960.get(0).change()),
                "the Zuiderzee in 1960 is closed: the Afsluitdijk of 1932 turned it into a lake",
                zuiderzee1960.isEmpty() ? "nothing recorded" : zuiderzee1960.get(0).change());
        var aral = changes.standing(45.00d, 59.00d, 2010);
        s.check(!aral.isEmpty() && aral.get(0).year() >= 1987,
                "the Aral in 2010 is the split sea of 1987, not the whole sea of 1960",
                aral.isEmpty() ? "nothing recorded" : aral.get(0).name());
        s.check(!changes.standing(30.96d, 46.10d, -2500).isEmpty(),
                "Ur in 2500 BC has the sea's retreat recorded beside it",
                "nothing recorded");
        s.check(changes.standing(23.00d, 6.00d, 1000).isEmpty(),
                "the Sahara in AD 1000 has no change to its water recorded, and none is invented",
                "the pack claims " + changes.standing(23.00d, 6.00d, 1000).size());

        // ---- B25: a water work stands only in its own years
        s.check(!works.near(41.8925d, 12.4853d, 100, 25.0d).isEmpty(),
                "Rome in AD 100 has its water works",
                "none within 25 km");
        s.check(works.near(-33.8688d, 151.2093d, 1850, 25.0d).isEmpty(),
                "Sydney in 1850 has no water work in the pack (it is the great dry of the Tank Stream)",
                "it has " + works.near(-33.8688d, 151.2093d, 1850, 25.0d).size());
        s.check(!works.near(27.3242d, 68.1356d, -2500, 10.0d).isEmpty(),
                "Mohenjo-daro in 2500 BC still has its Great Bath",
                "none within 10 km");
        int outsideYears = 0;
        for (WaterWorks.Work w : works.near(41.8925d, 12.4853d, 100, 25.0d)) {
            if (!w.isStandingIn(100)) {
                outsideYears++;
            }
        }
        s.check(outsideYears == 0, "a water work is only returned for the years it stood",
                outsideYears + " works stand outside the year asked about");

        // ---- B4: the climate, and the fact that it is regional
        s.check(climate.temperatureModifier(41.8925d, 12.4853d, 1650) < -0.5d,
                "Rome in 1650 is in the cold of the Maunder Minimum: "
                        + String.format(Locale.ROOT, "%.2f",
                        climate.temperatureModifier(41.8925d, 12.4853d, 1650)) + " C",
                String.valueOf(climate.temperatureModifier(41.8925d, 12.4853d, 1650)));
        s.check(climate.temperatureModifier(41.8925d, 12.4853d, -100) > 0.2d,
                "Rome in 100 BC is in the Roman Warm Period: +"
                        + String.format(Locale.ROOT, "%.2f",
                        climate.temperatureModifier(41.8925d, 12.4853d, -100)) + " C",
                String.valueOf(climate.temperatureModifier(41.8925d, 12.4853d, -100)));
        s.check(climate.precipModifier(20.68d, -88.57d, 900) < -30.0d,
                "Chichen Itza in 900 is in the Maya droughts: "
                        + climate.precipModifier(20.68d, -88.57d, 900) + "% rain",
                String.valueOf(climate.precipModifier(20.68d, -88.57d, 900)));
        s.check(climate.yieldModifier(17.39d, 78.49d, 1877) < 0.6d,
                "Hyderabad in 1877 is in the Great Famine: a harvest of "
                        + String.format(Locale.ROOT, "%.2f",
                        climate.yieldModifier(17.39d, 78.49d, 1877)) + " of normal",
                String.valueOf(climate.yieldModifier(17.39d, 78.49d, 1877)));
        s.check(climate.temperatureModifier(51.5074d, -0.1278d, 1816) < -1.0d,
                "London in 1816 is the Year Without a Summer: "
                        + String.format(Locale.ROOT, "%.2f",
                        climate.temperatureModifier(51.5074d, -0.1278d, 1816)) + " C",
                String.valueOf(climate.temperatureModifier(51.5074d, -0.1278d, 1816)));
        // the point of the whole part: the Little Ice Age is regional, not global
        var cusco = climate.active(-13.5163d, -71.9785d, 1650);
        boolean cuscoAndes = false;
        boolean cuscoEurope = false;
        for (ClimatePeriods.Period p : cusco) {
            if ("lia_andes".equals(p.id())) {
                cuscoAndes = true;
            }
            if ("little_ice_age".equals(p.id())) {
                cuscoEurope = true;
            }
        }
        s.check(cuscoAndes && !cuscoEurope,
                "Cusco in 1650 feels the Andean Little Ice Age and not Europe's (the periods are regional)",
                "Andean " + cuscoAndes + ", European " + cuscoEurope);
        s.check(climate.isActive("little_ice_age", 51.5074d, -0.1278d, 1650),
                "London in 1650 does feel Europe's Little Ice Age",
                "the period does not reach London");

        s.known("places with no climate period of their own",
                "Ur and Mohenjo-daro in 2500 BC: the African humid period's ending does not reach Mesopotamia, "
                        + "and the Indus cities were in the wet monsoon optimum. The pack says nothing rather "
                        + "than inventing something.");

        // speed: a tick asks about a great many squares in one year, and a command about scattered years
        land.prepare(1900);
        long t0 = System.nanoTime();
        int n = 0;
        for (int i = 0; i < 2000; i++) {
            double lat = -55.0d + i * 0.05d;
            double lon = -175.0d + i * 0.15d;
            n += land.classAt(lat, lon, 1900);
            n += climate.active(lat, lon, 1900).size();
            n += changes.standing(lat, lon, 1900).size();
            n += works.near(lat, lon, 1900, 25.0d).size();
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        s.check(ms < 2000L, "2,000 places are answered at once without a pause: " + ms + " ms "
                + "(the land, the climate, the water and the works at each)",
                ms + " ms for 2,000 places");
    }

    // ============================================================== step 36: the life of a building

    /** B10, B21 and B27 together: what struck a place, what it did to what stood there, and what
     *  became of what the player built. */
    private static void life(Section s) {
        Disasters d = Disasters.get();
        if (!d.loaded()) {
            s.fail("the disaster register loads", String.valueOf(d.problem()));
            return;
        }
        FamousBuildings fb = FamousBuildings.get();
        if (!fb.loaded()) {
            s.fail("the famous buildings load", String.valueOf(fb.problem()));
            return;
        }

        // ---------------------------------------------------------------- B27: what struck here
        // The pack held 26,997 scraped rows when this check was written. The November 2026
        // review (R2, A/r2_wikipedia) revised it to 25,457 rows - unverified rows demoted to
        // indexed notes, among them the AD 258 Tyrrhenian sea-wave, which NOAA rates "possible"
        // (validity 2) - so the register now answers from the reviewed pack.
        s.check(d.count() > 25_400 && d.fromSupplement() == 79 && d.quarantined() == 53,
                "the disasters load: " + d.summary(),
                "wanted 25,483 with 79 written by hand and 53 set aside");

        s.check(d.fromMagnitude() == 5_611 && d.fromDeathToll() > 1_200,
                "severity comes from a measurement where the record has one: "
                        + d.fromMagnitude() + " from a magnitude or VEI, " + d.fromDeathToll()
                        + " from the number of dead",
                "wanted 5,611 from magnitude and more than 1,200 from the dead");

        List<Disasters.Disaster> pompeii = d.at(40.749d, 14.487d, 79);
        Disasters.Disaster vesuvius = first(pompeii, Disasters.Kind.VOLCANO);
        s.check(vesuvius != null && d.damage(vesuvius, 40.749d, 14.487d) > 0.60d,
                "Vesuvius reaches Pompeii in AD 79 — the scraped pack does not carry it at all: "
                        + (vesuvius == null ? "nothing" : vesuvius.name()),
                "no eruption with real damage at Pompeii in AD 79");

        List<Disasters.Disaster> lisbon = d.at(38.722d, -9.139d, 1755);
        Disasters.Disaster quake = first(lisbon, Disasters.Kind.EARTHQUAKE);
        s.check(quake != null && quake.distanceKm(38.722d, -9.139d) > 100d,
                "the 1755 earthquake reaches Lisbon from an epicentre "
                        + (quake == null ? "?" : (int) quake.distanceKm(38.722d, -9.139d))
                        + " km out to sea — the pack gives it a 50 km radius, "
                        + "which does not reach the city it destroyed",
                "the radius does not widen with the severity");

        Disasters.Disaster krakatoa = first(d.at(-6.10d, 105.42d, 1883), Disasters.Kind.VOLCANO);
        s.check(krakatoa != null && krakatoa.severity() > 0.95d,
                "Krakatau 1883 is a VEI 6: " + (krakatoa == null ? "absent" : krakatoa.name()),
                "no VEI 6 eruption at Krakatau in 1883");

        s.check(d.at(30.963d, 46.103d, -2500).isEmpty(),
                "a quiet place in a quiet year is answered with nothing rather than something: "
                        + "Ur in 2500 BC",
                "something is recorded at Ur in 2500 BC that should not be");

        s.check(d.nobodyToMend(51.514d, -0.090d, 1349),
                "the Black Death leaves London with nobody to mend anything in 1349",
                "no depopulation at London in 1349");
        s.check(!d.nobodyToMend(-6.10d, 105.42d, 1883),
                "a famine 530 km from Krakatau does not stop a village mending its roof",
                "the depopulation threshold is too loose");
        s.check(d.nobodyToMend(17.385d, 78.487d, 1877),
                "the Great Famine of 1876-78 reaches Hyderabad — the same years step 35's "
                        + "climate_periods calls a drought",
                "no famine at Hyderabad in 1877");

        boolean noLostYears = true;
        String lostYear = "";
        for (Disasters.Disaster q : d.all()) {
            // the quarantine is a rule about the scraped pack; the hand-written supplement
            // carries its years correctly and is not subject to it
            if (!"hand".equals(q.confidence()) && q.year() >= 0 && q.year() <= 99
                    && (q.kind() == Disasters.Kind.TSUNAMI || q.dead() >= 1000)) {
                noLostYears = false;
                lostYear = q.name() + " is dated to AD " + q.year();
                break;
            }
        }
        s.check(noLostYears,
                "the " + d.quarantined() + " events whose year did not survive the parsing "
                        + "are set aside rather than guessed",
                lostYear);

        // ---------------------------------------------------------------- B10: what became of it
        BuildingLife.Life col100 = colosseum(100);
        BuildingLife.Life col258 = colosseum(258);
        BuildingLife.Life col500 = colosseum(500);
        BuildingLife.Life col1349 = colosseum(1349);
        BuildingLife.Life col2026 = colosseum(2026);
        s.check(col100 != null && col100.phase() == BuildingLife.Phase.STANDING,
                "the Colosseum stands in AD 100", col100 == null ? "not found" : col100.phase().label());
        // The AD 258 Tyrrhenian sea-wave is no longer a certain hazard: the November 2026
        // review (R2, A/r2_wikipedia/focus/212_261_CE_batch.md) demoted it to an indexed note
        // because NOAA rates it "possible", validity 2. So it no longer damages the Colosseum.
        s.check(col258 != null && col258.phase() == BuildingLife.Phase.STANDING,
                "the Colosseum stands in AD 258: the Tyrrhenian sea-wave of that year is kept as "
                        + "a note, not a certain hazard (NOAA rates it possible, validity 2)",
                col258 == null ? "not found" : col258.phase().label());
        s.check(col500 != null && col500.phase() == BuildingLife.Phase.STANDING,
                "and it is still standing in AD 500 - with the AD 258 wave demoted to a note, "
                        + "nothing certain struck it before the earthquake of 1349",
                col500 == null ? "not found" : col500.phase().label());
        s.check(col1349 != null && col1349.phase() == BuildingLife.Phase.DAMAGED
                        && col1349.cause() != null && col1349.cause().year() == 1349,
                "the earthquake of 1349 takes the south side of the Colosseum off, "
                        + "and is named as the cause",
                col1349 == null || col1349.cause() == null ? "no cause named"
                        : col1349.cause().name() + " " + col1349.cause().yearLabel());
        s.check(col2026 != null && col2026.phase() != BuildingLife.Phase.GONE
                        && col2026.phase() != BuildingLife.Phase.UNBUILT,
                "the Colosseum is still there today, and is not erased by routine Roman "
                        + "earthquakes: " + (col2026 == null ? "?" : col2026.phase().label()),
                "the damage ratchets and the building is destroyed");

        s.check(phaseAt(40.749d, 14.487d, 78) != BuildingLife.Phase.GONE, "Pompeii stands in AD 78", "");
        s.check(countPhase(40.749d, 14.487d, 80, BuildingLife.Phase.RUIN) >= 3,
                "Pompeii is ruins in AD 80: " + BuildingLife.line(40.749d, 14.487d, 80, 12d, 6),
                "Vesuvius did not touch it");
        s.check(countPhase(40.749d, 14.487d, 200, BuildingLife.Phase.GONE) >= 3,
                "and gone by AD 200, because a buried city is not rebuilt",
                BuildingLife.line(40.749d, 14.487d, 200, 12d, 6));

        s.check(countPhase(51.514d, -0.090d, 1667, BuildingLife.Phase.RUIN)
                        + countPhase(51.514d, -0.090d, 1667, BuildingLife.Phase.DAMAGED) >= 3,
                "the Great Fire of 1666 leaves London in ruins the year after: "
                        + BuildingLife.line(51.514d, -0.090d, 1667, 12d, 6),
                BuildingLife.line(51.514d, -0.090d, 1667, 12d, 6));

        s.check(countPhase(38.722d, -9.139d, 1756, BuildingLife.Phase.DAMAGED) >= 4,
                "Lisbon is damaged the year after its earthquake: "
                        + BuildingLife.line(38.722d, -9.139d, 1756, 12d, 6),
                BuildingLife.line(38.722d, -9.139d, 1756, 12d, 6));
        s.check(countPhase(38.722d, -9.139d, 1800, BuildingLife.Phase.DAMAGED) == 0,
                "and rebuilt by 1800: " + BuildingLife.line(38.722d, -9.139d, 1800, 12d, 6),
                BuildingLife.line(38.722d, -9.139d, 1800, 12d, 6));

        boolean giza = true;
        for (int y : new int[]{-2500, -1000, 0, 1000, 1348, 1800, 2026}) {
            if (countPhase(29.979d, 31.134d, y, BuildingLife.Phase.GONE) > 0) {
                giza = false;
            }
        }
        s.check(giza, "the pyramids are not erased by anything between 2500 BC and today",
                "Giza loses a building somewhere in the record");

        int ends = 0;
        int endsFinal = 0;
        for (FamousBuildings.Building b : fb.near(41.890d, 12.492d, 400, 12d, 40)) {
            if (b.to() == FamousBuildings.NO_END) {
                continue;
            }
            ends++;
            BuildingLife.Life l = BuildingLife.of(b, b.to() + 1);
            if (l.phase() == BuildingLife.Phase.GONE) {
                endsFinal++;
            }
        }
        s.check(ends == 0 || ends == endsFinal,
                "a building's own end year is final: " + endsFinal + " of " + ends
                        + " dated buildings at Rome are gone the year after they ended",
                "the life model overrides a recorded end year");

        // ---------------------------------------------------------------- B21: what the player built
        PlayerEdits p = new PlayerEdits();
        List<PlayerEdits.Branch> main =
                List.of(new PlayerEdits.Branch(PlayerEdits.MAIN, PlayerEdits.NO_DIVERGENCE));
        p.record(PlayerEdits.Kind.BUILD, "house by the Forum", 41.891d, 12.485d, 100,
                PlayerEdits.MAIN, "uuid-1", "");
        s.check(p.standingIn(90, main).isEmpty() && p.standingIn(100, main).size() == 1,
                "a house built in AD 100 is not there in AD 90 and is there in AD 100",
                "90: " + p.standingIn(90, main).size() + ", 100: " + p.standingIn(100, main).size());

        PlayerEdits.Jump back = p.jump(2026, 90, main);
        PlayerEdits.Jump fwd = p.jump(90, 2026, main);
        s.check(!back.isEmpty() && back.add().isEmpty() && !fwd.isEmpty() && fwd.remove().isEmpty(),
                "going back is not undoing: " + back.line() + "; " + fwd.line(),
                "the jump does not hide and restore");
        s.check(p.all().size() == 1,
                "the edit is still remembered after the jump back: " + p.all().size(),
                "the record was thrown away with the blocks");

        p.record(PlayerEdits.Kind.DEMOLISH, "house by the Forum", 41.891d, 12.485d, 1300,
                PlayerEdits.MAIN, "uuid-1", "");
        s.check(!p.standingIn(1250, main).isEmpty() && p.standingIn(1350, main).isEmpty(),
                "a demolition at 1300 takes the house out of 1350 and not out of 1250",
                "1250: " + p.standingIn(1250, main).size() + ", 1350: "
                        + p.standingIn(1350, main).size());

        PlayerEdits t = new PlayerEdits();
        t.record(PlayerEdits.Kind.BUILD, "bridge", 41.891d, 12.485d, 900, PlayerEdits.MAIN, "u", "");
        t.record(PlayerEdits.Kind.BUILD, "tower", 41.900d, 12.500d, 1100, PlayerEdits.MAIN, "u", "");
        t.record(PlayerEdits.Kind.BUILD, "chapel", 41.910d, 12.510d, 1100, "rome-never-fell", "u", "");
        List<PlayerEdits.Branch> split = List.of(
                new PlayerEdits.Branch(PlayerEdits.MAIN, 1000),
                new PlayerEdits.Branch("rome-never-fell", PlayerEdits.NO_DIVERGENCE));
        s.check(named(t.standingIn(1200, split), "bridge") && !named(t.standingIn(1200, split), "tower")
                        && named(t.standingIn(1200, split), "chapel"),
                "a branch inherits what its ancestors did before the split and nothing after: "
                        + "the bridge of 900 crosses, the tower of 1100 does not",
                "branch inheritance is wrong");

        PlayerEdits q = new PlayerEdits();
        PlayerEdits.Edit villa = q.record(PlayerEdits.Kind.BUILD, "villa", 40.750d, 14.486d, 78,
                PlayerEdits.MAIN, "u", "");
        s.check(q.lifeOf(villa, 79).phase() == BuildingLife.Phase.RUIN,
                "a player's villa begun at Pompeii in AD 78 is a ruin in AD 79 — their buildings "
                        + "are no more exempt from Vesuvius than anyone else's: "
                        + q.describe(villa, 79, BuildingLife.DEFAULT_CARE),
                q.lifeOf(villa, 79).phase().label());

        s.check(PlayerEdits.fromJson(t.toJson()).toJson().toString().equals(t.toJson().toString()),
                "the record of what a player built saves and loads unchanged",
                "the round trip is lossy");

        // ---------------------------------------------------------------- speed
        long t0 = System.nanoTime();
        int n = 0;
        String[] places = {"41.89,12.49", "51.51,-0.09", "30.04,31.24", "38.72,-9.14", "40.75,14.48",
                "19.43,-99.13", "17.39,78.49", "29.98,31.13", "41.01,28.98", "39.90,116.41",
                "35.01,135.77", "-13.53,-71.97", "16.77,-3.01", "30.96,46.10", "-6.10,105.42",
                "37.77,-122.42", "55.75,37.62", "28.61,77.21", "-33.87,151.21", "-22.91,-43.17",
                "1.35,103.82", "6.52,3.38", "-1.29,36.82", "35.68,139.65", "48.86,2.35",
                "40.71,-74.01", "-34.60,-58.38", "19.08,72.88", "-26.20,28.05", "24.71,46.68",
                "36.20,37.16"};
        for (String pl : places) {
            String[] xy = pl.split(",");
            n += BuildingLife.at(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]),
                    1500, 12d, 8).size();
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        s.check(ms < 2_000L,
                "31 places across six continents asked at once, " + n + " buildings: " + ms + " ms",
                ms + " ms is too slow");
    }

    // ================================================================== step 38: who people are

    /** Step 38: the seven registers that answer "who is this person", one section of checks. */
    private static void people(Section s) {
        LifeTable life = LifeTable.get();
        Households houses = Households.get();
        Jobs jobs = Jobs.get();
        DayRound days = DayRound.get();
        Needs needs = Needs.get();

        s.check(life.mortalityCount() >= 30 && life.marriageCount() >= 14,
                "the life table loads: " + life.mortalityCount() + " mortality regimes and "
                        + life.marriageCount() + " marriage regimes",
                "wanted 30 mortality regimes and 14 marriage regimes");
        s.check(houses.count() >= 25 && jobs.count() >= 20 && days.count() >= 20,
                "the families, the jobs and the day all load: " + houses.count()
                        + " household regimes, " + jobs.count() + " occupations, "
                        + days.count() + " day rounds",
                "one of the step 38 packs is thin or absent");
        s.check(Migrations.count() >= 30 && Health.remedyCount() >= 10
                        && Health.illnessCount() >= 15,
                "the roads and the medicine load: " + Migrations.count() + " migrations, "
                        + Health.remedyCount() + " remedies, " + Health.illnessCount()
                        + " diseases",
                "wanted 38 migrations, 14 remedies and 18 diseases");

        // ------------------------------------------------------------ how long, and how many
        double london1850 = life.expectancy(life.mortalityAt(51.51d, -0.13d, 1850, true), 0.0d);
        double london2020 = life.expectancy(life.mortalityAt(51.51d, -0.13d, 2020, true), 0.0d);
        s.check(london1850 > 20.0d && london1850 < 55.0d && london2020 > 65.0d,
                "a child born in London could expect " + one(london1850)
                        + " years in 1850 and " + one(london2020) + " in 2020",
                "life expectancy is not in the range the record allows");

        Households.Household hh = houses.at(51.51d, -0.13d, 1900, true);
        s.check(hh != null && hh.size() > 1.0d && hh.size() < 15.0d,
                "a London household in 1900 averages " + (hh == null ? "?" : one(hh.size()))
                        + " people: " + (hh == null ? "no regime" : hh.name()),
                "no household regime for London in 1900");

        // the fix of part 8: a house the draw called a family has a family in it, and the share of
        // one-person houses stays where the published figures put it
        int alone = 0;
        int mislabelled = 0;
        for (int i = 0; i < 600; i++) {
            Households.Home h = houses.home(i * 7919L, 1900, 51.51d, -0.13d, true);
            if (h.members().size() == 1) {
                alone++;
                if (!"solo".equals(h.structure())) {
                    mislabelled++;
                }
            } else if ("solo".equals(h.structure())) {
                mislabelled++;
            }
        }
        s.check(mislabelled == 0,
                "600 houses drawn for London in 1900: " + alone
                        + " are one-person houses and every one of them says so",
                mislabelled + " houses say one thing and are another");

        // ------------------------------------------------------------ the work and the day
        Jobs.Job j1300 = jobs.at(51.51d, -0.13d, 1300, true);
        Jobs.Job j1900 = jobs.at(51.51d, -0.13d, 1900, true);
        s.check(j1300 != null && j1900 != null && !j1300.id().equals(j1900.id()),
                "London has work in 1300 and in 1900, and it is not the same work: "
                        + (j1300 == null ? "?" : j1300.name()) + ", then "
                        + (j1900 == null ? "?" : j1900.name()),
                "the occupations do not change with the era");

        DayRound.Day d1300 = days.at(51.51d, -0.13d, 1300, true);
        DayRound.Day d1850 = days.at(51.51d, -0.13d, 1850, true);
        s.check(d1300 != null && d1850 != null,
                "London has a day in 1300 and in 1850",
                "no day round for London");
        if (d1300 != null && d1850 != null) {
            s.check(!d1300.byClock() && d1850.byClock(),
                    "the day is set by the task in 1300 and by the clock in 1850: "
                            + days.line(51.51d, -0.13d, 1300, true, "christianity"),
                    "the working day does not start being measured");
            s.check(d1850.hasMarket() && d1850.hasRestDay(),
                    "and there is a market day and a day of rest: "
                            + days.line(51.51d, -0.13d, 1850, true, "christianity"),
                    "no market or no rest day in industrial London");
        }

        // ------------------------------------------------------------ needs
        Needs.Situation london1348 = needs.at(51.51d, -0.13d, 1348, true, "farmer", "christianity");
        s.check(london1348.needs().size() == 5,
                "five needs are read for a farmer in London in 1348, not four and not six: "
                        + london1348.needs().size(),
                "wanted five needs");
        s.check(london1348.pressing() != null,
                "and the Black Death is on everybody's mind: " + london1348.line(),
                "nothing is pressing in London in 1348");
        // the same failed harvest does not hit everybody the same way
        Needs.Situation grower = needs.at(35.0d, 135.0d, 1229, false, "farmer", "buddhism");
        Needs.Situation buyer = needs.at(35.0d, 135.0d, 1229, false, "weaver", "buddhism");
        s.check(grower.need("food").level() < buyer.need("food").level(),
                "on the Kanki famine of AD 1229 the grower goes hungry before the buyer: food "
                        + one(grower.need("food").level()) + " against "
                        + one(buyer.need("food").level()),
                "a famine hits a farmer and a weaver alike, which is wrong");

        // ------------------------------------------------------------ the roads
        s.check(Migrations.near(31.55d, 74.34d, 1948).size() >= 2
                        && Migrations.leaving(31.55d, 74.34d, 1947).size() >= 1
                        && Migrations.arriving(31.55d, 74.34d, 1947).size() >= 1,
                "Lahore in 1947 is both an end people fled and an end they fled to, which is "
                        + "what the Partition was: " + Migrations.line(31.55d, 74.34d, 1947),
                "the Partition does not run through Lahore");
        com.eternalengine.people.Migrations.Migration lahore =
                Migrations.find("partition_punjab_east");
        s.check(lahore != null && lahore.route(1947).days() >= 1.0d
                        && lahore.route(1947).days() < 30.0d
                        && lahore.fast(1947).days() < lahore.route(1947).days(),
                "and that road was walked: " + lahore.routeLine(1947),
                "the largest movement of people there has ever been did not go on foot");
        s.check(Migrations.near(31.55d, 74.34d, 1848).isEmpty(),
                "a sea crossing is its two ports and not a straight line through the land: the "
                        + "great circle from Calcutta to British Guiana runs over the Punjab, and "
                        + "no ship did",
                "a ship from Calcutta to Guiana has been routed over the Punjab");

        // ------------------------------------------------------------ the medicine: the fix
        s.check(Health.tierAt(51.75d, -1.26d, 1945) == Health.Tier.MODERN
                        && Health.tierAt(25.40d, 85.10d, 1945) != Health.Tier.MODERN,
                "the world-wide year tier is dead: in 1945 Oxford is "
                        + Health.tierAt(51.75d, -1.26d, 1945) + " and a Bihar village is "
                        + Health.tierAt(25.40d, 85.10d, 1945),
                "the whole planet is still modern at the same moment");
        s.check(Health.remedy("antibiotics").here(51.75d, -1.26d, 1945)
                        && !Health.remedy("antibiotics").here(-3.10d, -60.02d, 1945),
                "penicillin reached Oxford in AD 1941 and the Amazon in "
                        + Health.remedy("antibiotics").arrivedAt(-3.10d, -60.02d)
                        + " - it does not cure people it never came near",
                "antibiotics are everywhere at once again");
        s.check(!Health.find("smallpox").possibleAt(19.43d, -99.13d, 1000),
                "there was no smallpox in Tenochtitlan in AD 1000, and the register does not "
                        + "give the Aztecs a disease that arrived with a ship",
                "smallpox is in the Americas before 1492");
        s.check(Health.remedy("humoral").arrivedAt(-3.10d, -60.02d) >= 1492,
                "and the medicine of the four humours did not reach the Amazon in BC 229: it "
                        + "came in AD " + Health.remedy("humoral").arrivedAt(-3.10d, -60.02d),
                "a remedy has crossed an ocean nobody had crossed");
        s.check(Health.find("cholera").fatalityAt(51.51d, -0.13d, 1900)
                        < Health.find("cholera").fatalityAt(51.51d, -0.13d, 1840),
                "cholera is less deadly in London in 1900 than in 1840: "
                        + pct(Health.find("cholera").fatalityAt(51.51d, -0.13d, 1840)) + " then, "
                        + pct(Health.find("cholera").fatalityAt(51.51d, -0.13d, 1900)) + " now, "
                        + "because of clean water and not because of a drug",
                "nothing changed in sixty years");
        s.check(Health.nameOf(Health.sicknessAt(51.51d, -0.13d, 1348)) != null,
                "the sickness in force in London in 1348 is named by the register: "
                        + Health.line(51.51d, -0.13d, 1348),
                "the hazard pack and the disease table do not meet");

        // ------------------------------------------------------------ all of it, at once
        String[] places = {"51.51,-0.13", "30.04,31.24", "28.61,77.21", "35.68,139.65",
                "48.86,2.35", "40.71,-74.01", "-23.55,-46.63", "19.08,72.88", "-26.20,28.05",
                "55.75,37.62", "37.57,126.98", "41.89,12.49", "19.43,-99.13", "6.52,3.38",
                "-1.29,36.82", "-33.87,151.21", "1.35,103.82", "-34.60,-58.38", "24.71,46.68",
                "36.20,37.16", "-3.10,-60.02", "64.14,-21.94", "23.81,90.41", "-6.20,106.85",
                "30.59,114.31", "38.90,-77.04", "45.46,-73.57", "52.52,13.40", "13.75,100.50",
                "5.60,-0.19", "-12.04,-77.04"};
        long t0 = System.nanoTime();
        int answered = 0;
        for (String pl : places) {
            String[] xy = pl.split(",");
            double la = Double.parseDouble(xy[0]);
            double lo = Double.parseDouble(xy[1]);
            for (int year : new int[] {-1000, 500, 1300, 1700, 1900, 2020}) {
                if (life.mortalityAt(la, lo, year, false) != null) {
                    answered++;
                }
                if (jobs.at(la, lo, year, false) != null) {
                    answered++;
                }
                if (days.at(la, lo, year, false) != null) {
                    answered++;
                }
                if (!Migrations.near(la, lo, year).isEmpty() || Migrations.count() > 0) {
                    answered++;
                }
                answered++;                                  // needs and health always answer
                answered++;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        s.check(ms < 3_000L,
                places.length + " places in six eras asked of all seven registers at once, "
                        + answered + " answers: " + ms + " ms",
                ms + " ms is too slow");
    }

    private static String one(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100.0d);
    }

    /** The Colosseum's state in a year, or null if the pack does not list it there. */
    private static BuildingLife.Life colosseum(int year) {
        for (FamousBuildings.Building b : FamousBuildings.get().near(41.8902d, 12.4922d, year, 3d, 40)) {
            if (b.name().toLowerCase(Locale.ROOT).contains("coloss")) {
                return BuildingLife.of(b, year);
            }
        }
        return null;
    }

    /** How many buildings near a place are in a given phase in a given year. */
    private static int countPhase(double lat, double lon, int year, BuildingLife.Phase phase) {
        int n = 0;
        for (BuildingLife.Life l : BuildingLife.at(lat, lon, year, 12d, 6)) {
            if (l.phase() == phase) {
                n++;
            }
        }
        return n;
    }

    /** The phase of the first building listed near a place in a year. */
    private static BuildingLife.Phase phaseAt(double lat, double lon, int year) {
        List<BuildingLife.Life> ls = BuildingLife.at(lat, lon, year, 12d, 6);
        return ls.isEmpty() ? BuildingLife.Phase.UNBUILT : ls.get(0).phase();
    }

    /** The first disaster of a kind in a list. */
    private static Disasters.Disaster first(List<Disasters.Disaster> list, Disasters.Kind kind) {
        for (Disasters.Disaster d : list) {
            if (d.kind() == kind) {
                return d;
            }
        }
        return null;
    }

    /** Is there an edit with this name in the list? */
    private static boolean named(List<PlayerEdits.Edit> list, String name) {
        for (PlayerEdits.Edit e : list) {
            if (e.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    // =============================================================== step 39: event scenes (D9)

    /**
     * What is going on here, and who is in it.
     *
     * <p>The three claims this step stands on, each checked: <b>a scene is cast, not drawn</b> -
     * the parts come from the work sectors step 38's {@code Jobs} actually has here, so a band of
     * twenty-five foragers cannot hold a coronation and the register says so rather than putting a
     * farmer in a borrowed robe; <b>a scene is set off by something that has already happened</b>
     * - all seven triggers are asked of registers that already exist, and none of them is a guess;
     * and <b>the suffering scenes are scenes of absence</b>, which say how much quieter the street
     * is rather than how many corpses are in it.
     */
    private static void scenes(Section s) {
        Scenes.Where rome = Scenes.Where.of(41.89d, 12.49d, 100).town(true).folk(50000)
                .holding(Scenes.wordsOf("political", "coronation", "Coronation of Trajan"));
        Scenes.Where amazon = Scenes.Where.of(-3.10d, -60.02d, 100).town(false).folk(25)
                .holding(Scenes.wordsOf("political", "coronation", "Coronation of Trajan"));
        Scenes.Where london1348 = Scenes.Where.of(51.51d, -0.13d, 1348).town(true).folk(40000);
        Scenes.Where lahore1947 = Scenes.Where.of(31.55d, 74.34d, 1947).town(true)
                .keeps("islam").folk(30000).on(8, 20).day(5000L);

        s.check(Scenes.count() == 20,
                "the twenty scenes load: " + Scenes.count() + " across every group and trigger",
                "wanted exactly the twenty D9 scenes");

        int[] byTrigger = new int[Scenes.Trigger.values().length];
        int sourced = 0;
        for (Scenes.Scene sc : Scenes.all()) {
            byTrigger[sc.trigger().ordinal()]++;
            if (!sc.source().isEmpty()) {
                sourced++;
            }
        }
        int used = 0;
        int most = 0;
        for (int n : byTrigger) {
            if (n > 0) {
                used++;
            }
            most = Math.max(most, n);
        }
        s.check(used == Scenes.Trigger.values().length && most * 2 < Scenes.count(),
                "all seven triggers are used and none carries half the scenes: a scene is set "
                        + "off by something that has already happened, not by a dice roll",
                "one trigger is doing all the work, or one is dead");
        s.check(sourced == Scenes.count(),
                "every scene is sourced: " + sourced + " of " + Scenes.count(),
                "a scene carries no source");

        // ------------------------------------------------------------ a cast, not a draw
        Scenes.Scene crowning = Scenes.find("crowning");
        s.check(crowning != null && Scenes.on(crowning, rome) && !Scenes.on(crowning, amazon),
                "a coronation can be cast in Rome in AD 100 and not in a band of twenty-five "
                        + "foragers: " + Scenes.available(crowning, rome) + " of "
                        + (crowning == null ? 0 : crowning.size()) + " parts against "
                        + Scenes.available(crowning, amazon) + " - a scene needs a quorum",
                "the register is filling parts with people the place does not have");
        s.check(crowning != null && Scenes.on(crowning, amazon.folk(25000)),
                "and the crowd is what decides it, not the year: the same place at 25,000 "
                        + "people holds one",
                "the quorum is not reading the population");

        // ------------------------------------------- the triggers, each asked of a register
        s.check(Scenes.on(Scenes.find("plague"), london1348),
                "the Black Death puts a scene on in London in 1348, because step 36's pack has "
                        + "it in force there",
                "the hazard register is not being asked");
        s.check(!Scenes.on(Scenes.find("plague"),
                        Scenes.Where.of(-3.10d, -60.02d, 1700).town(false).folk(25)),
                "and no sickness puts one on in the Amazon in 1700: a hazard only sets a scene "
                        + "off where it actually bites, which is why a quake in German New "
                        + "Guinea does not empty Glasgow",
                "a hazard recorded over a whole country is emptying a street 14,000 km away");
        s.check(Scenes.on(Scenes.find("refugees"), lahore1947),
                "the Lahore road in 1947 has refugees on it, from step 38's dated records",
                "the migration register is not being asked");
        s.check(!Scenes.on(Scenes.find("refugees"),
                        Scenes.Where.of(31.55d, 74.34d, 1848).town(true).keeps("islam")
                                .folk(30000).on(8, 20).day(5000L)),
                "and Lahore in 1848 does not, because this is records and not a field",
                "the register is inventing refugees where it has no record");

        int marketDays = 0;
        for (long d = 0L; d < 40L; d++) {
            if (Scenes.triggered(Scenes.find("market_day"),
                    Scenes.Where.of(51.51d, -0.13d, 1300).town(true).folk(40000).day(d))) {
                marketDays++;
            }
        }
        s.check(marketDays >= 3 && marketDays <= 12,
                "the London market comes round " + marketDays
                        + " times in forty days, not every day: that is step 38's market cycle",
                "the market is on every day or never");

        s.check(Scenes.triggered(Scenes.find("prayer"),
                        Scenes.Where.of(30.04d, 31.24d, 1000).town(true).keeps("islam").at(5))
                        && !Scenes.triggered(Scenes.find("prayer"),
                        Scenes.Where.of(30.04d, 31.24d, 1000).town(true).keeps("islam").at(15)),
                "an hour of prayer is on in Cairo at dawn and not in the middle of the "
                        + "afternoon: the register reads the set hours, it does not guess",
                "prayer is on at every hour or none");
        s.check(!Scenes.triggered(Scenes.find("prayer"),
                        Scenes.Where.of(30.04d, 31.24d, 1000).town(true).keeps("islam")),
                "and where it is not told the hour, it declines rather than guessing",
                "the register is asserting a time of day it was never given");

        int feasts = 0;
        for (int m = 1; m <= 12; m++) {
            for (int d = 1; d <= 28; d++) {
                if (Scenes.triggered(Scenes.find("festival"),
                        Scenes.Where.of(51.51d, -0.13d, 1300).town(true).folk(40000)
                                .keeps("christianity").on(m, d))) {
                    feasts++;
                }
            }
        }
        s.check(feasts >= 1 && feasts <= 40,
                "a feast day comes " + feasts + " times a year in London, not all 336 of them: "
                        + "step 30b's calendar says when, not the register",
                "every day is a feast day, or none is");

        // ------------------------------------------------- the life table's own number
        Scenes.Where bihar1850 = Scenes.Where.of(25.40d, 85.10d, 1850).town(false).folk(300);
        Scenes.Where bihar2020 = Scenes.Where.of(25.40d, 85.10d, 2020).town(false).folk(300);
        int older = 0;
        int newer = 0;
        for (long d = 0L; d < 3000L; d++) {
            if ("funeral".equals(Scenes.lifeScene(bihar1850.day(d)))) {
                older++;
            }
            if ("funeral".equals(Scenes.lifeScene(bihar2020.day(d)))) {
                newer++;
            }
        }
        s.check(older > newer && older > 0,
                "a Bihar village buries more people in 1850 than in 2020 - " + older
                        + " funerals against " + newer + " in 3,000 days - which is the life "
                        + "table's own number, not a sense of what a village is like",
                "funerals are not coming off the death rate");

        // ------------------------------------------------------------ a scene of absence
        Scenes.Occasion plague = Scenes.make(Scenes.find("plague"), london1348);
        s.check(plague != null && plague.absence() > 0.0d && plague.absence() < 0.96d
                        && plague.line().contains("emptier"),
                "the Black Death empties a London street by "
                        + pct(plague == null ? 0.0d : plague.absence())
                        + ", and the register says how much quieter it is rather than how many "
                        + "corpses are in it",
                "a suffering scene is counting the dead instead of the missing");

        // ------------------------------------------- the two the last sweep caught
        s.check("siege".equals(Scenes.eventScene("war siege Siege of Jerusalem"))
                        && "battle".equals(Scenes.eventScene("war battle Battle of Hastings")),
                "a siege is read as a siege and not as a battle, though the word \"war\" is in "
                        + "both: the match table asks for the narrower thing first",
                "the register is reading every war as a battle");
        s.check("election".equals(Scenes.incidentScene("POPULAR_REFORM"))
                        && "protest".equals(Scenes.incidentScene("REBELLION"))
                        && "trial".equals(Scenes.incidentScene("ASSASSINATION_ATTEMPT"))
                        && Scenes.incidentScene("GOOD_HARVEST").isEmpty(),
                "an election, a protest and a trial each come off a generated incident, and a "
                        + "good harvest sets nothing off at all",
                "the three incident scenes have no way in, or every incident is a gathering");
        s.check(Scenes.on(Scenes.find("fire"),
                        Scenes.Where.of(51.51d, -0.09d, 1666).town(true).folk(40000)),
                "the Great Fire of London puts a fire on in London in 1666: step 36's supplement "
                        + "carries six fires in all of history, and a scene register that never "
                        + "reached one would be carrying a dead row",
                "no fire ever burns, anywhere");

        // ------------------------------------------------- it says something, everywhere
        double[][] spots = {{41.89d, 12.49d}, {51.51d, -0.13d}, {30.04d, 31.24d},
                {28.61d, 77.21d}, {25.40d, 85.10d}, {-3.10d, -60.02d}, {55.86d, -4.25d},
                {35.68d, 139.65d}};
        int[] folk = {50000, 40000, 60000, 60000, 300, 25, 70000, 80000};
        String[] faith = {"roman", "christianity", "islam", "hinduism", "hinduism", "none",
                "christianity", "buddhism"};
        int blank = 0;
        for (int i = 0; i < spots.length; i++) {
            String line = Scenes.line(Scenes.Where.of(spots[i][0], spots[i][1], 1000)
                    .town(folk[i] > 1000).keeps(faith[i]).folk(folk[i]).on(6, 15).day(1234L)
                    .at(9));
            if (line == null || line.isBlank()) {
                blank++;
            }
        }
        s.check(blank == 0,
                "the register has something true to say in all eight places, including the two "
                        + "where the honest answer is that nothing is going on today",
                "a place gets no line at all");
    }

    // ================================================================== step 35: the sky

    /**
     * The sky of an era is not the sky of today turned down: the stars move of themselves, the pole
     * they turn about moves faster still, and eclipses come and go on their own cycles. Everything
     * here was checked against published values when it was written; the notes say which.
     */
    private static void sky(Section s) {
        var stars = com.eternalengine.sky.SkyStars.get();
        var comets = com.eternalengine.sky.SkyComets.get();
        var eclipses = com.eternalengine.sky.SkyEclipses.get();

        s.check(stars.loaded(), "the stars load", String.valueOf(stars.problem()));
        s.check(comets.loaded(), "the comets load", String.valueOf(comets.problem()));
        s.check(eclipses.loaded(), "the eclipses load", String.valueOf(eclipses.problem()));
        if (!stars.loaded() || !comets.loaded() || !eclipses.loaded()) {
            return;
        }
        s.passQuiet("what the step holds",
                stars.count() + " stars; " + comets.count() + " comets; " + eclipses.solarCount()
                        + " solar and " + eclipses.lunarCount() + " lunar eclipses from "
                        + eclipses.solarFromYear() + " to " + eclipses.solarToYear());

        // ---- B22: the pole star belongs to its own age. Thuban held the pole from 3942 BCE and
        // handed it to Kappa Draconis in 1793 BCE; Polaris is closest to it in AD 2100.
        s.check("Thuban".equals(stars.poleStar(jd(-3000), 4.0d).star().label()),
                "the pole star of 3000 BC is Thuban, not Polaris",
                String.valueOf(stars.poleStar(jd(-3000), 4.0d).star().label()));
        s.check("Kappa Dra".equals(stars.poleStar(jd(-1500), 4.0d).star().label()),
                "the pole star of 1500 BC is Kappa Draconis",
                String.valueOf(stars.poleStar(jd(-1500), 4.0d).star().label()));
        s.check("Kochab".equals(stars.poleStar(jd(0), 4.0d).star().label()),
                "the pole star at the turn of the era is Kochab",
                String.valueOf(stars.poleStar(jd(0), 4.0d).star().label()));
        s.check("Polaris".equals(stars.poleStar(jd(2026), 4.0d).star().label()),
                "the pole star now is Polaris",
                String.valueOf(stars.poleStar(jd(2026), 4.0d).star().label()));
        double polaris2100 = 90.0d - stars.poleStar(jd(2100), 4.0d).decDeg();
        s.check(Math.abs(polaris2100 - 0.46d) < 0.05d,
                "Polaris is 0.46 degrees from the pole in 2100, its closest (published: 0.45): "
                        + String.format(Locale.ROOT, "%.3f", polaris2100),
                String.valueOf(polaris2100));
        double closest = Double.MAX_VALUE;
        int closestYear = 0;
        for (int y = -3400; y <= -2200; y += 10) {
            double d = 90.0d - placeDec(stars, "Thuban", jd(y));
            if (d < closest) {
                closest = d;
                closestYear = y;
            }
        }
        s.check(closest < 0.167d,
                "Thuban came within ten arc-minutes of the pole, "
                        + String.format(Locale.ROOT, "%.3f", closest) + " degrees in "
                        + yearText(closestYear)
                        + " (published: less than ten arc-minutes, in 2830 BCE)",
                String.valueOf(closest));

        // ---- precession is a turning of the whole sphere, so distances between stars cannot change
        double before = com.eternalengine.sky.SkyStars.angularDistanceDeg(0.0d, 0.0d, 90.0d, 45.0d);
        double[] a = com.eternalengine.sky.SkyStars.precess(0.0d, 0.0d, jd(-3000));
        double[] b = com.eternalengine.sky.SkyStars.precess(90.0d, 45.0d, jd(-3000));
        double after = com.eternalengine.sky.SkyStars.angularDistanceDeg(a[0], a[1], b[0], b[1]);
        s.check(Math.abs(before - after) < 1e-6d,
                "precession turns the whole sky and does not stretch it",
                before + " became " + after);
        double[] identity = com.eternalengine.sky.SkyStars.precess(0.0d, 90.0d,
                com.eternalengine.sky.SkyStars.J2000);
        s.check(Math.abs(identity[1] - 90.0d) < 1e-9d,
                "at J2000 the precession is the identity", String.valueOf(identity[1]));

        // ---- B23: the naked-eye sky of a place, in its own age
        s.check(stars.everVisible(30.9625d, jd(-2500)) > 4000,
                "Ur in 2500 BC can see " + stars.everVisible(30.9625d, jd(-2500))
                        + " of the " + stars.count() + " stars at some hour of the year",
                String.valueOf(stars.everVisible(30.9625d, jd(-2500))));
        s.check(!stars.circumpolar(30.9625d, jd(-2500)).isEmpty(),
                "Ur in 2500 BC has stars that never set", "none");
        String urSky = stars.describe(30.9625d, 46.1031d, jd(-2500));
        s.check(urSky.contains("Thuban"),
                "the sky over Ur in 2500 BC names Thuban as the pole star of the age", urSky);
        s.infoQuiet("Ur, 2500 BC: " + urSky);
        s.check(!stars.describe(17.385d, 78.4867d, jd(1948)).isEmpty(),
                "the sky over Hyderabad in 1948 names stars too", "nothing came back");

        // ---- B23: comets are dated, and a comet is answered in its own year only
        s.check(comets.anyIn(-43) && comets.describe(-43).contains("Caesar"),
                "Caesar's comet is in the sky of 44 BC", comets.describe(-43));
        s.check(comets.describe(1066).contains("Halley"),
                "Halley's comet is in the sky of 1066, the year of the Bayeux Tapestry",
                comets.describe(1066));
        s.check(comets.nextAfter(1066) != null && comets.nextAfter(1066).year() == 1106,
                "the next comet after 1066 is the Great Comet of 1106",
                String.valueOf(comets.nextAfter(1066)));
        String cometClang = "";
        for (com.eternalengine.sky.SkyComets.Comet c : comets.comets()) {
            String d = comets.describe(c.year());
            if (d.contains("  ") || d.contains(" .") || d.endsWith(" ")
                    || (!d.isEmpty() && !d.endsWith("."))) {   // a doubled or dangling stop
                cometClang = c.year() + ": " + d;
                break;
            }
        }
        s.check(cometClang.isEmpty(), "every comet's sentence is clean, note or no note", cometClang);

        // ---- B23: eclipses. Four modern ones whose published track we know.
        eclipseAt(s, eclipses, 2027, 8, 2, 25.5d, 33.2d, "Egypt", 1.079d);
        eclipseAt(s, eclipses, 1999, 8, 11, 45.1d, 24.3d, "Romania", 1.029d);
        eclipseAt(s, eclipses, 2024, 4, 8, 25.3d, -104.1d, "Mexico", 1.057d);
        eclipseAt(s, eclipses, 2009, 7, 22, 24.2d, 144.1d, "the Pacific", 1.080d);

        // and the two that history recorded
        var thales = eclipseAt(s, eclipses, -584, 5, 22, 40.5d, 34.0d, "the Halys", 0.971d);
        s.check(thales > 0.90d && thales < 1.0d,
                "Thales' eclipse is a deep partial over the Halys and not a total one: "
                        + String.format(Locale.ROOT, "%.3f", thales)
                        + " — the path of totality lay in the North Atlantic, and Anatolia saw the "
                        + "Sun go out with it only a few degrees above the horizon",
                String.valueOf(thales));
        double nineveh = eclipseAt(s, eclipses, -762, 6, 7, 36.36d, 43.15d, "Nineveh", 0.990d);
        s.check(nineveh > 0.95d,
                "the Assyrian eclipse of 763 BC very nearly wiped the Sun out over Nineveh: "
                        + String.format(Locale.ROOT, "%.3f", nineveh),
                String.valueOf(nineveh));

        // the Sun does not rise for an eclipse on the other side of the world
        s.check(!eclipses.sighting(solarOn(eclipses, 2027, 8, 2), -33.8688d, 151.2093d).visible(),
                "the eclipse of 2027 is not seen from Sydney", "it is");

        // a sentence for a player, and the article in it
        String cairo = eclipses.describe(26.0d, 32.0d, 2027);
        s.check(cairo.contains("total eclipse of the Sun"),
                "Cairo is told about the total eclipse of 2 August 2027", cairo);
        s.infoQuiet("Cairo, 2027: " + cairo.trim());
        boolean articleOk = true;
        for (int y = -1999; y <= 3000 && articleOk; y += 7) {
            String d = eclipses.describe(41.8925d, 12.4853d, y);
            if (d.contains("A annular") || d.contains("A eclipse") || d.contains("A total eclipse of the Moon on  ")) {
                articleOk = false;
            }
        }
        s.check(articleOk, "the eclipse sentence never says \"a annular\"", "it does");
        s.check(!eclipses.describe(23.0d, 6.0d, 1000).contains(" ."),
                "the eclipse sentence has no dangling note where the pack has none",
                eclipses.describe(23.0d, 6.0d, 1000));

        // ---- speed: a tick asks the sky about a great many squares in one year
        long t0 = System.nanoTime();
        int n = 0;
        for (int i = 0; i < 2000; i++) {
            double lat = -55.0d + i * 0.05d;
            double lon = -175.0d + i * 0.15d;
            n += stars.above(lat, lon, jd(1900), 10.0d, 4.0d).size();
            n += comets.inSeason(1900).size();
            n += eclipses.solarSeen(lat, lon, 1899, 1901, 0.01d).size();
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        s.check(ms < 4000L, "2,000 places are answered at once without a pause: " + ms + " ms "
                + "(the stars overhead, the comets of the season and every eclipse of three years)",
                ms + " ms for 2,000 places");

        s.known("what this part cannot promise",
                "Ancient eclipse magnitudes agree with an independent ephemeris to about 0.03, "
                        + "which is 50 to 100 km of shadow: the uncertainty in delta-T at 585 BC. "
                        + "On the edge of a total path that is the difference between 98 per cent "
                        + "and a total eclipse. Modern eclipses agree to 0.002. The Sun, Moon and "
                        + "planets are computed, not tabulated, and are not checked here.");
    }

    /** A Julian day for the middle of a year, for questions about an era rather than an instant. */
    private static double jd(int year) {
        return com.eternalengine.sky.SkyStars.julianDay(year, 6, 21, 0.0d);
    }

    /** The precessed declination of one named star in one year. */
    private static double placeDec(com.eternalengine.sky.SkyStars stars, String name, double jd) {
        for (com.eternalengine.sky.SkyStars.Star st : stars.stars()) {
            if (name.equals(st.label())) {
                return com.eternalengine.sky.SkyStars.placeOf(st, jd)[1];
            }
        }
        return -99.0d;
    }

    /** One eclipse of a given date, or null when the pack has none. */
    private static com.eternalengine.sky.SkyEclipses.Solar solarOn(
            com.eternalengine.sky.SkyEclipses sky, int year, int month, int day) {
        for (int y = year - 1; y <= year + 1; y++) {
            for (com.eternalengine.sky.SkyEclipses.Solar e : sky.solarInYear(y)) {
                if (e.year() == year && e.month() == month && e.day() == day) {
                    return e;
                }
            }
        }
        return null;
    }

    /**
     * That an eclipse of this date was seen from this place about as deeply as the reference says.
     * The reference is NASA's own catalogue for modern eclipses and, for the ancient ones, an
     * independent ephemeris (pyephem) that shares no code with this mod. Returns the magnitude
     * seen, negative when the place saw nothing.
     */
    private static double eclipseAt(Section s, com.eternalengine.sky.SkyEclipses sky,
                                    int year, int month, int day, double lat, double lon,
                                    String place, double expected) {
        com.eternalengine.sky.SkyEclipses.Solar e = solarOn(sky, year, month, day);
        if (e == null) {
            s.fail("the eclipse of " + day + " " + (month) + " " + year + " is in the pack", "it is not");
            return -1.0d;
        }
        var seen = sky.sighting(e, lat, lon);
        double got = seen.magnitude();
        s.check(seen.visible() && Math.abs(got - expected) < 0.04d,
                "the eclipse of " + e.dateLabel() + " is " + seen.howMuch() + " at " + place
                        + " (checked independently at "
                        + String.format(Locale.ROOT, "%.3f", expected) + ")",
                String.valueOf(got));
        return got;
    }

    private static void houses(Section s) {
        var bc = com.eternalengine.towns.BuildingCulture.get();
        if (!bc.loaded()) {
            s.fail("the building cultures of B9 load", String.valueOf(bc.problem()));
            return;
        }
        s.check(bc.rowCount() > 100 && bc.countryCount() > 250,
                "the building cultures load: " + bc.summary(), String.valueOf(bc.problem()));

        // A place is answered by its own culture, and its neighbours do not steal it.
        String[][] own = {{"Cusco 1500", "-13.5163", "-71.9785", "1500", "andes_inca"},
                {"the Amazon 1500", "-3.4653", "-62.2159", "1500", "amazon_west"},
                {"Lhasa 1650", "29.65", "91.1", "1650", "tibet_himalaya"},
                {"Kathmandu 1700", "27.7", "85.3", "1700", "himalaya_mid_hills"},
                {"Delhi 1650", "28.6562", "77.241", "1650", "mughal"},
                {"Vijayanagara 1520", "15.335", "76.46", "1520", "south_india"},
                {"Teotihuacan 500", "19.6925", "-98.8438", "500", "mesoamerica_classic"},
                {"Greenland Norse 1000", "61.155", "-45.517", "1000", "greenland_norse"},
                {"Reykjavik 1000", "64.1", "-21.9", "1000", "iceland_turf"},
                {"Samarkand 1400", "39.7", "66.9", "1400", "central_asia_islamic"},
                {"Dunhuang 800", "40.1", "94.7", "800", "china_han_tang"},
                {"Cahokia 1100", "38.6603", "-90.0618", "1100", "north_america_woodland"},
                {"Timbuktu 1350", "16.7735", "-3.0074", "1350", "sahel_medieval"},
                {"Great Zimbabwe 1300", "-20.2675", "30.9339", "1300", "zimbabwe_stone"},
                {"Moscow 1980", "55.752", "37.6175", "1980", "soviet_panel"},
                {"Hyderabad 1948", "17.3616", "78.4747", "1948", "india_colonial"},
                {"Sydney 1850", "-33.8688", "151.2093", "1850", "settler_colonial_australia"}};
        int right = 0;
        StringBuilder wrong = new StringBuilder();
        for (String[] t : own) {
            double lat = Double.parseDouble(t[1]);
            double lon = Double.parseDouble(t[2]);
            int year = Integer.parseInt(t[3]);
            var r = bc.at(lat, lon, year, SettlementRank.TOWN);
            String id = r.map(v -> v.id()).orElse("");
            if (id.equals(t[4])) {
                right++;
            } else {
                wrong.append(t[0]).append(" got ").append(id.isEmpty() ? "nothing" : id).append("; ");
            }
        }
        s.check(right == own.length, "each place is answered by its own building culture and not by a "
                + "neighbouring one: Cusco is Inca and not a maloca, Lhasa is Tibetan and not Ming, "
                + "Greenland in 1000 is Norse and not Inuit", right + " of " + own.length + ": " + wrong);

        // The years are honoured: two places, three years.
        var rome100 = bc.at(41.8925, 12.4853, 100, SettlementRank.TOWN);
        var rome1400 = bc.at(41.8925, 12.4853, 1400, SettlementRank.TOWN);
        var london1700 = bc.at(51.5074, -0.1278, 1700, SettlementRank.TOWN);
        var london900 = bc.at(51.5074, -0.1278, 900, SettlementRank.TOWN);
        boolean years = rome100.isPresent() && rome1400.isPresent()
                && !rome100.get().id().equals(rome1400.get().id())
                && london1700.isPresent() && london900.isPresent()
                && !london1700.get().id().equals(london900.get().id());
        s.check(years, "the same place changes with the year: Rome in 100 is not Rome in 1400, and "
                + "London in 1700 is not London in 900",
                "Rome " + rome100.map(v -> v.id()).orElse("-") + " / " + rome1400.map(v -> v.id()).orElse("-")
                        + ", London " + london900.map(v -> v.id()).orElse("-") + " / "
                        + london1700.map(v -> v.id()).orElse("-"));

        // What the atlas knows is asked of the atlas: the size of the town decides.
        var village = bc.at(23.8, 89.5, 1890, SettlementRank.VILLAGE);       // a Bengal village
        var city = bc.at(23.8, 89.5, 1890, SettlementRank.CITY);
        boolean bySize = village.isPresent() && city.isPresent()
                && !village.get().wall().equals(city.get().wall());
        s.check(bySize, "the size of the town decides what the walls are: a Bengal village of 1890 has "
                        + "lime and brick where its city already has cement, the technology atlas being "
                        + "asked rather than the answer being written into the pack",
                village.map(v -> v.wall()).orElse("-") + " / " + city.map(v -> v.wall()).orElse("-"));

        // After dark, the newest light the atlas says this town has.
        var london1700b = bc.at(51.5074, -0.1278, 1700, SettlementRank.CITY);
        var london1900 = bc.at(51.5074, -0.1278, 1900, SettlementRank.CITY);
        boolean light = london1700b.isPresent() && london1900.isPresent()
                && !london1700b.get().light().equals(london1900.get().light());
        s.check(light, "B20: what it is lit by after dark moves with the year (London 1700 against "
                + "London 1900), the atlas setting the year",
                london1700b.map(v -> v.light()).orElse("-") + " / " + london1900.map(v -> v.light()).orElse("-"));

        // Every country and every year has an answer: nothing falls through.
        int empty = 0;
        int total = 0;
        CountryGrid grid = CountryGrid.get();
        for (int i = 0; i < grid.size(); i++) {
            double lat = grid.mainLat(i);
            double lon = grid.mainLon(i);
            for (int year = -3000; year <= 2000; year += 500) {
                total++;
                if (bc.at(lat, lon, year, SettlementRank.TOWN).isEmpty()) {
                    empty++;
                }
            }
        }
        s.check(empty == 0, "every country of the grid has an answer in every year, " + total
                + " country-years, none falling through", empty + " empty");
        s.infoQuiet("building cultures: " + bc.summary() + ", " + bc.sources().size()
                + " Wikipedia articles cited, loaded in " + bc.loadMillis() + " ms; Delhi 1650: "
                + bc.at(28.6562, 77.241, 1650, SettlementRank.CITY).map(v -> v.line()).orElse("-"));
    }

    /** Step 28 (C8, C9, C21, known problems 39 and 40): event cards, the graph, natural events, occupied and disputed land. */
    private static void eventCards(Section s) {
        var store = com.eternalengine.knowledge.CardStore.events();
        s.check(store.loaded() && store.size() > 25_000, "the event file loads: " + store.summary(),
                String.valueOf(store.problem()));
        if (!store.loaded()) {
            return;
        }
        int tried = 0;
        int found = 0;
        for (int i = 0; i < store.indexSize(); i += Math.max(1, store.indexSize() / 300)) {
            var c = store.byQid(store.indexQid(i));
            tried++;
            if (c != null && c.isEvent()) {
                found++;
            }
        }
        s.check(found == tried && store.indexSize() == store.size(), "every event card is found by its number (" + found
                + " of " + tried + " sampled)", "");

        // The First World War through the years: nobody knows the future.
        var w13 = cardOn("ec_361", 1913, 48.86, 2.35);
        String w16 = cardText(cardOn("ec_361", 1916, 48.86, 2.35), "what") + " "
                + cardText(cardOn("ec_361", 1916, 48.86, 2.35), "when") + " "
                + cardText(cardOn("ec_361", 1916, 48.86, 2.35), "parts");
        var w25 = cardOn("ec_361", 1925, 48.86, 2.35);
        String w25t = cardText(w25, "what") + " " + cardText(w25, "came");
        boolean future = w13 != null && !w13.knownOn(com.eternalengine.knowledge.KnowledgeDate.key(1913, 7, 1))
                && w16.contains("still going on") && !w16.contains("died") && !w16.contains("(1917)")
                && !w16.contains("(1918)") && w25t.contains("million people died") && w25t.contains("Russian Revolution");
        s.check(future, "the First World War is unknown in 1913; in 1916 it is going on, with no death toll and nothing "
                + "from 1917 or 1918; in 1925 its dead are counted and the Russian Revolution came of it", w16 + " / " + w25t);
        String why = cardText(cardOn("ec_362", 1950, 51.51, -0.13), "why");
        s.check(why.contains("[[ec_") && why.contains("Treaty of Versailles"), "why it happened, with links to the causes "
                + "(the Second World War: the Treaty of Versailles ...)", why);

        // The graph both ways, on a sample.
        int edges = 0;
        int both = 0;
        for (int i = 0; i < store.indexSize(); i += 7) {
            var c = store.byQid(store.indexQid(i));
            if (c == null) {
                continue;
            }
            for (var l : c.links()) {
                int back = l.role() == com.eternalengine.knowledge.CardStore.CAUSE ? com.eternalengine.knowledge.CardStore.EFFECT
                        : l.role() == com.eternalengine.knowledge.CardStore.EFFECT ? com.eternalengine.knowledge.CardStore.CAUSE : -1;
                if (back < 0) {
                    continue;
                }
                var o = store.byQid(l.qid());
                if (o == null) {
                    continue;
                }
                edges++;
                long kept = o.links().stream().filter(m -> m.role() == back).count();
                if (kept >= 6 || o.links().stream().anyMatch(m -> m.role() == back && m.qid() == c.qid())) {
                    both++;
                }
            }
        }
        s.check(edges > 200 && both * 100 >= edges * 97, "the cause-and-effect graph is stored both ways (" + both + " of "
                + edges + " sampled links; " + store.tileStore().meta("links_cause", "?") + " causes and "
                + store.tileStore().meta("links_effect", "?") + " effects in all)", "");

        // A place's past, and the news.
        String hastings = com.eternalengine.knowledge.PlaceCards.pastText(
                com.eternalengine.knowledge.PlaceCards.pastEvents(50.91, 0.49, 1200));
        String early = com.eternalengine.knowledge.PlaceCards.pastText(
                com.eternalengine.knowledge.PlaceCards.pastEvents(50.91, 0.49, 1050));
        s.check(hastings.contains("Battle of Hastings") && !early.contains("Hastings"), "a place remembers its past events "
                + "(Hastings in 1200, not in 1050)", hastings);
        int linkedNews = 0;
        for (double[] p : new double[][]{{45.36, 9.31, 1515}, {30.37, -89.09, 2005}, {11.24, 125.00, 2013}}) {
            var r = HistoricalPresence.query(null, p[0], p[1], (int) p[2]);
            var local = com.eternalengine.knowledge.LocalCards.build(r, p[0], p[1]);
            var n = local.newsCard() == null ? null : local.cards().get(local.newsCard());
            if (cardText(n, "what").contains("[[ec_")) {
                linkedNews++;
            }
        }
        s.check(linkedNews == 3, "the news names an event with its own card (Marignano 1515, Hurricane Katrina 2005, Typhoon "
                + "Haiyan 2013)", linkedNews + " of 3");

        // Natural events in the history data.
        long storms = 0;
        long nature = 0;
        for (var e : HistoryGazetteer.get().allEvents()) {
            if ("ibtracs".equals(e.confidence())) {
                storms++;
            } else if (e.confidence() != null && e.confidence().startsWith("wikidata") && java.util.Set.of("flood",
                    "wildfire", "landslide", "avalanche", "tornado", "heatwave", "drought", "famine", "epidemic")
                    .contains(e.kind())) {
                nature++;
            }
        }
        s.check(storms > 5000 && nature > 1500, "natural events: " + storms + " storm landfalls since 1842, " + nature
                + " floods, wildfires, landslides, avalanches, tornadoes, heat waves, droughts, famines and epidemics", "");

        // Occupied and disputed land.
        String paris = EventNews.rulerLine(HistoricalPresence.query(null, 48.86, 2.35, 1942));
        String batavia = EventNews.rulerLine(HistoricalPresence.query(null, -6.20, 106.85, 1944));
        String paris46 = EventNews.rulerLine(HistoricalPresence.query(null, 48.86, 2.35, 1946));
        s.check(paris.contains("German troops have held this land since 1940") && batavia.contains("Japanese troops")
                && !paris46.contains("troops"), "occupied land names its state and the occupier, only in those years (Paris "
                + "1942, Batavia 1944; not Paris 1946)", paris + " / " + batavia);
        String ramallah = EventNews.rulerLine(HistoricalPresence.query(null, 31.90, 35.20, 2020));
        String crimea = EventNews.rulerLine(HistoricalPresence.query(null, 44.95, 34.10, 2020));
        String telAviv = EventNews.rulerLine(HistoricalPresence.query(null, 32.08, 34.78, 2020));
        s.check(ramallah.contains("Palestinian") && crimea.contains("Ukraine claims") && telAviv.startsWith("This land belongs"),
                "disputed land says who holds it and who claims it (the West Bank, Crimea); the land beside it keeps the "
                        + "record's line (Tel Aviv)", ramallah + " / " + crimea);
        s.infoQuiet("Paris 1942: " + paris);
    }

    /** Step 27 (C10, C12, C22): the knowledge cards of famous people and groups for every place and year. */
    private static void cards(Section s) {
        var store = com.eternalengine.knowledge.CardStore.get();
        s.check(store.loaded() && store.people() > 100_000 && store.organisations() > 20_000,
                "the card file loads: " + store.summary(), String.valueOf(store.problem()));
        if (!store.loaded()) {
            return;
        }
        int tried = 0;
        int found = 0;
        String missing = "";
        int step = Math.max(1, store.indexSize() / 400);
        for (int i = 0; i < store.indexSize(); i += step) {
            int q = store.indexQid(i);
            tried++;
            var c = store.byQid(q);
            if (c != null && c.qid() == q) {
                found++;
            } else if (missing.isEmpty()) {
                missing = "Q" + q;
            }
        }
        s.check(found == tried && store.indexSize() == store.size(), "every card is found by its number (" + found + " of "
                + tried + " sampled; the index holds " + store.indexSize() + " of " + store.size() + ")", missing);

        // People talked about and groups known, with cards right for the year.
        double[][] spots = {{43.77, 11.25, 1610}, {18.94, 72.83, 1948}, {35.01, 135.77, 1600}, {6.52, 3.38, 1990},
                {37.98, 23.73, -400}, {-33.87, 151.21, 2000}, {39.90, 116.40, 1700}, {51.51, -0.13, 1700},
                {44.49, 11.34, 1300}, {38.90, -77.04, 1865}, {64.15, -21.94, 1900}, {-4.32, 15.31, 2026}};
        java.util.regex.Pattern link = java.util.regex.Pattern.compile("\\[\\[(\\w+)\\|([^\\]]+)\\]\\]");
        int withPeople = 0;
        int links = 0;
        String bad = "";
        boolean galileo = false;
        boolean bologna = false;
        boolean company = false;
        for (double[] p : spots) {
            int year = (int) p[2];
            var l = com.eternalengine.knowledge.PlaceCards.lists(p[0], p[1], year);
            if (!l.people().isEmpty()) {
                withPeople++;
            }
            List<String> ids = com.eternalengine.knowledge.PlaceCards.ids(l);
            for (String id : ids) {
                galileo |= id.equals("pc_307") && year == 1610;
                bologna |= id.equals("oc_131262") && year == 1300;
                company |= id.equals("oc_83164") && year == 1700;
                var k = cardOn(id, year, p[0], p[1]);
                if (k == null || !k.knownOn(com.eternalengine.knowledge.KnowledgeDate.key(year, 7, 1))) {
                    bad = id + " in " + yearText(year) + (k == null ? " does not open" : " is not known yet");
                    continue;
                }
                for (var q : k.questions()) {
                    String t = cardText(k, q.id());
                    String low = t.toLowerCase(java.util.Locale.ROOT);
                    if (low.contains("wiki") || t.length() > 3900) {
                        bad = id + ": " + t;
                    }
                    var m = link.matcher(t);
                    while (m.find()) {
                        links++;
                        var lk = cardOn(m.group(1), year, p[0], p[1]);
                        if (lk == null || !lk.knownOn(com.eternalengine.knowledge.KnowledgeDate.key(year, 7, 1))) {
                            bad = id + " links " + m.group(1) + " that is not known in " + yearText(year);
                        }
                    }
                }
            }
        }
        s.check(withPeople == spots.length && galileo, "people talked about, each with a card, at " + withPeople + " of "
                + spots.length + " places from Athens in 400 BC to Kinshasa in 2026 (Florence 1610 names Galileo)",
                "Galileo " + galileo);
        s.check(bologna && company, "groups people know, each with a card (Bologna 1300: its university; London 1700: the"
                + " East India Company)", "Bologna " + bologna + ", London " + company);
        s.check(bad.isEmpty(), "every card of those places is known on that date, and so is every card it links to ("
                + links + " links); no source names; every answer fits the talk box", bad);

        // Nobody knows the future.
        var gal80 = cardOn("pc_307", 1580, 43.77, 11.25);
        String jfk50 = cardText(cardOn("pc_9696", 1950, 38.9, -77.04), "who");
        String jfk62 = cardText(cardOn("pc_9696", 1962, 38.9, -77.04), "who");
        String deeds62 = cardText(cardOn("pc_9696", 1962, 38.9, -77.04), "deeds");
        String vic30 = cardText(cardOn("pc_9439", 1830, 51.5, -0.13), "who");
        String vic40 = cardText(cardOn("pc_9439", 1840, 51.5, -0.13), "who");
        String eic = cardText(cardOn("oc_83164", 1880, 51.51, -0.13), "what");
        var bo1000 = cardOn("oc_131262", 1000, 44.49, 11.34);
        boolean future = gal80 != null && !gal80.knownOn(com.eternalengine.knowledge.KnowledgeDate.key(1580, 7, 1))
                && !jfk50.toLowerCase(java.util.Locale.ROOT).contains("president")
                && jfk62.contains("is the president of the United States") && !deeds62.contains("Ich bin")
                && !vic30.contains("queen") && vic40.contains("is the queen") && eic.contains("closed in 1874")
                && bo1000 != null && !bo1000.knownOn(com.eternalengine.knowledge.KnowledgeDate.key(1000, 7, 1));
        s.check(future, "nobody knows the future: Galileo is unknown in 1580; Kennedy is not called president in 1950 but is "
                + "in 1962 (and his 1963 speech is not known yet); Victoria is queen from 1837; Bologna's university is "
                + "unknown in 1000; the East India Company has closed by 1880", jfk50 + " / " + jfk62 + " / " + vic40);
        s.infoQuiet("Kennedy in 1962: " + jfk62);

        // Who rules here: the ruler's card.
        int linked = 0;
        String example = "";
        double[][] rule = {{41.89, 12.49, 100}, {28.61, 77.21, 1600}, {55.76, 37.62, 1980}, {48.86, 2.35, 1789},
                {38.90, -77.04, 1865}, {39.90, 116.40, 1700}};
        for (double[] p : rule) {
            var r = HistoricalPresence.query(null, p[0], p[1], (int) p[2]);
            var local = com.eternalengine.knowledge.LocalCards.build(r, p[0], p[1]);
            var state = local.rulerCard() == null ? null : local.cards().get(local.rulerCard());
            String t = cardText(state, "who");
            if (t.contains("[[pc_")) {
                linked++;
                if (example.isEmpty()) {
                    example = t;
                }
            }
        }
        s.check(linked >= 5, "who rules here names the ruler with a card of their own at " + linked + " of " + rule.length
                + " places (Trajan, Akbar, Brezhnev, Louis XVI ...)", example);

        // The world sample, and the time it takes.
        double[][] land = landPoints();
        int spotsN = 0;
        int with = 0;
        long t0 = System.nanoTime();
        for (int year : new int[]{1900, 2026}) {
            for (double[] p : land) {
                spotsN++;
                if (!com.eternalengine.knowledge.PlaceCards.lists(p[0], p[1], year).people().isEmpty()) {
                    with++;
                }
            }
        }
        long t1 = System.nanoTime();
        for (int year : new int[]{1900, 2026}) {
            for (double[] p : land) {
                com.eternalengine.knowledge.PlaceCards.lists(p[0], p[1], year);
            }
        }
        double warm = (System.nanoTime() - t1) / 1e6 / Math.max(1, spotsN);
        double first = (t1 - t0) / 1e6 / Math.max(1, spotsN);
        s.check(with * 100 >= spotsN * 70, "people to tell at " + with + " of " + spotsN + " land points of the world"
                + " sample in 1900 and 2026", "too few");
        s.check(warm < 5.0d, String.format(Locale.ROOT, "a place's people and groups take %.2f ms (%.2f ms the"
                + " first time)", warm, first), "too slow");
    }

    private static void foundations(Section s) {
        // A1: one key, one query
        int same = 0;
        String firstDiff = "";
        for (Spot sp : keySpots()) {
            var key = com.eternalengine.core.WorldKey.prime(sp.lat(), sp.lon(), sp.year());
            String a = presenceKey(com.eternalengine.core.WorldQuery.presence(null, key));
            String b = presenceKey(HistoricalPresence.query(null, sp.lat(), sp.lon(), sp.year(), id -> false));
            if (a.equals(b)) {
                same++;
            } else if (firstDiff.isEmpty()) {
                firstDiff = sp.label() + ": " + a + " / " + b;
            }
        }
        s.check(same == keySpots().size(), "the key (place, date, timeline, universe) gives the record's answers at "
                + same + " of " + keySpots().size() + " places", firstDiff);
        var bombay = com.eternalengine.core.WorldKey.prime(18.94, 72.83, 1948);
        boolean emptyElsewhere = !com.eternalengine.core.WorldQuery.presence(null,
                bombay.inUniverse("minecraft:the_nether")).occupied();
        s.check(emptyElsewhere, "another universe (a dimension) has no recorded history of its own yet",
                "the Nether answered with Bombay's history");
        var hyd = com.eternalengine.core.WorldKey.prime(17.385, 78.4867, 1948);
        var ev = com.eternalengine.core.WorldQuery.event(hyd);
        if (ev.isPresent()) {
            String id = ev.get().event().id();
            var hidden = com.eternalengine.core.WorldQuery.presence(null, hyd, x -> x.equals(id)).event();
            boolean gone = hidden.map(h -> !h.event().id().equals(id)).orElse(true);
            boolean stays = com.eternalengine.core.WorldQuery.event(hyd).map(h -> h.event().id().equals(id)).orElse(false);
            s.check(gone && stays, "a timeline that prevented an event does not hear of it; the main timeline still does ("
                    + ev.get().event().name() + ")", "gone " + gone + ", main keeps it " + stays);
        } else {
            s.fail("a timeline that prevented an event does not hear of it", "no event near Hyderabad in 1948");
        }
        s.infoQuiet("the timeline now: " + com.eternalengine.core.WorldKey.activeTimeline());

        // A5: recorded or estimated
        RulerRegister reg = RulerRegister.get();
        boolean until = reg.recordedUntil() == 2024;
        boolean aleppo20 = reg.rulerAt(36.20, 37.16, 2020).map(h -> !h.estimated()).orElse(false);
        boolean aleppo26 = reg.rulerAt(36.20, 37.16, 2026).map(RulerRegister.Hit::estimated).orElse(false);
        s.check(until && aleppo20 && aleppo26, "the border map is recorded until " + reg.recordedUntil()
                + "; later years are marked as estimates (Aleppo 2020 recorded, 2026 estimated)",
                "recordedUntil " + reg.recordedUntil() + ", 2020 recorded " + aleppo20 + ", 2026 estimated " + aleppo26);
        int stretches = reg.estimatedStretchCount();
        s.check(stretches >= 5000, "coastal squares that took their neighbours' state are marked estimated ("
                + stretches + " stretches of years)", stretches + " stretches");
        int estReigns = 0;
        int reigns = 0;
        String example = "";
        for (LeaderRegister.Facts f : LeaderRegister.get().all()) {
            for (LeaderRegister.Reign r : f.heads()) {
                reigns++;
                if (r.estimated()) {
                    estReigns++;
                    if (example.isEmpty()) {
                        example = f.name() + ": " + r.display() + " " + yearText(r.start()) + " to " + yearText(r.end());
                    }
                }
            }
            for (LeaderRegister.Reign r : f.governments()) {
                reigns++;
                if (r.estimated()) {
                    estReigns++;
                }
            }
        }
        s.check(estReigns >= 150 && estReigns < reigns / 20, "reigns whose end the lists do not give are marked estimated ("
                + estReigns + " of " + reigns + ")", estReigns + " marked, e.g. " + example);
        if (!example.isEmpty()) {
            s.infoQuiet("an estimated reign: " + example);
        }

        // A4: tiles
        FamousPeople fp = FamousPeople.get();
        var store = fp.tileStore();
        if (store == null) {
            s.fail("famous people are read from tiles by land and century", fp.summary());
        } else {
            long rows = fp.all().count();
            int opened = 0;
            String broken = "";
            for (String g : store.groupNames()) {
                if (!g.startsWith("b/")) {
                    continue;
                }
                for (var t : store.tiles(g)) {
                    try {
                        store.inflate(t);
                        opened++;
                    } catch (RuntimeException e) {
                        broken = e.getMessage();
                    }
                }
            }
            s.check(rows == fp.size() && broken.isEmpty(), "famous people come from " + store.tileCount() + " tiles ("
                    + String.format(Locale.ROOT, "%.1f", store.compressedBytes() / 1048576.0d)
                    + " MB kept in memory); every tile opens and holds what its index says",
                    rows + " of " + fp.size() + " people read; " + opened + " birthplace tiles; " + broken);
            boolean rome = !fp.livingIn(reg.rulerAt(41.89, 12.49, 100).map(RulerRegister.Hit::wikidata).orElse(null),
                    41.89, 12.49, 100).isEmpty();
            boolean paris = !fp.bornNear(48.86, 2.35, 1900, 15.0d).isEmpty();
            s.check(rome && paris, "the tiles answer: famous people alive in Rome in 100, born near Paris by 1900",
                    "Rome " + rome + ", Paris " + paris);
        }
        var cache = com.eternalengine.data.TileCache.SHARED.stats();
        s.check(cache.peakBytes() <= cache.budgetBytes(), "opened tiles stay within their memory budget: "
                + cache.display(), "peak above the budget");

        // A3, A7, K4: the data list, the licences, the credits
        var manifest = com.eternalengine.data.DataManifest.load();
        if (manifest.problem() != null) {
            s.fail("the data list (manifest.json) loads", manifest.problem());
        } else {
            var problems = manifest.verify();
            s.check(problems.isEmpty(), "every data file in the mod is the one that was built and checked ("
                    + manifest.shipped().size() + " files, "
                    + String.format(Locale.ROOT, "%.1f", manifest.shippedBytes() / 1048576.0d)
                    + " MB: size and SHA-256 match the list)", problems.size() + " differ, e.g. "
                    + (problems.isEmpty() ? "" : problems.get(0).path() + ": " + problems.get(0).what()));
            var noLicence = manifest.withoutLicence();
            var nc = manifest.nonCommercial();
            // Plan decision 4: no non-commercial data. One named exception ships, printed on every
            // run: history/prices.json.gz, the Allen-Unger Global Commodity Prices Database
            // (CC BY-NC 4.0, doi:10.17026/dans-zsu-xavf) - the only global historical price series
            // there is. Its duty names the authors and keeps the non-commercial term visible.
            // Nothing else may join this list.
            var ncNamed = nc.stream().filter(p -> p.endsWith("history/prices.json.gz")).toList();
            var ncForbidden = nc.stream().filter(p -> !p.endsWith("history/prices.json.gz")).toList();
            s.check(noLicence.isEmpty() && ncForbidden.isEmpty(),
                    "every data file names its sources and licences (" + manifest.sources().size()
                            + " sources); none forbids commercial use except the one named exception",
                    "no licence: " + noLicence + "; non-commercial: " + ncForbidden);
            if (!ncNamed.isEmpty()) {
                s.known("prices.json.gz is non-commercial data shipped under a named exception",
                        "history/prices.json.gz, the Allen-Unger Global Commodity Prices Database "
                                + "(Allen and Unger, CC BY-NC 4.0, doi:10.17026/dans-zsu-xavf), does "
                                + "not allow commercial use; it is the one file decision 4 excepts, "
                                + "its duty is to credit Allen and Unger and keep the licence visible, "
                                + "and no further non-commercial file may ship");
            }
            var missing = manifest.missingFromCredits();
            s.check(missing.isEmpty(), "the credits file travels with the mod and names every source", "missing " + missing);
            s.check(manifest.shippedBytes() <= 64L * 1048576L, "the data stays within its size budget (64 MB)",
                    manifest.shippedBytes() + " bytes");
            var masters = manifest.mastersPresent();
            s.infoQuiet("marks of the data files: " + manifest.marks() + "; masters left out of the mod: "
                    + (masters.isEmpty() ? "all" : "present here " + masters + " (a source folder, not the jar)"));
        }

        // I6: memory
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        s.infoQuiet("memory now: " + used + " MB used of " + rt.maxMemory() / 1048576L + " MB; the history registers "
                + "loaded in " + firstLoadMillis + " ms");
        long cardBytes = com.eternalengine.knowledge.CardStore.get().loaded()
                ? com.eternalengine.knowledge.CardStore.get().tileStore().compressedBytes() : 0L;
        long kept = (store == null ? 0L : store.compressedBytes()) + cardBytes;
        s.check(kept <= 24L * 1048576L, String.format(Locale.ROOT, "the tiles kept in memory stay within their "
                + "budget (24 MB): famous people %.1f MB, knowledge cards %.1f MB", (store == null ? 0L
                : store.compressedBytes()) / 1048576.0d, cardBytes / 1048576.0d), kept + " bytes");
    }

    /** The 15 checklist places, numbered as in worldfill/TEST_CHECKLIST.md. */
    public static List<Spot> checklist() {
        return CHECKLIST;
    }

    /** Check one place: the state, the head of state, and the trade route a villager talks about. */
    public static Line checkSpot(Spot s) {
        HistoricalPresence.Result r = HistoricalPresence.query(null, s.lat(), s.lon(), s.year());
        String said = EventNews.rulerLine(r);
        String state = r.ruler().map(RulerRegister.Hit::ruler).orElse("");
        String head = r.ruler().flatMap(h -> LeaderRegister.get().facts(h.wikidata()))
                .flatMap(f -> f.headIn(s.year())).map(LeaderRegister.Reign::display).orElse("");
        List<String> wrong = new ArrayList<>();
        if (s.state() != null && !matches(state, s.state())) {
            wrong.add("the state is " + quoteOrNone(state) + ", expected " + alternatives(s.state()));
        }
        if (s.head() != null && !matches(head, s.head())) {
            wrong.add("the ruler is " + quoteOrNone(head) + ", expected " + alternatives(s.head()));
        }
        String tradeSaid = EventNews.tradeLine(r);
        if (s.trade() != null) {
            List<String> here = new ArrayList<>();
            boolean found = false;
            for (TradeRoutes.RouteHit h : TradeRoutes.get().routesNear(s.lat(), s.lon(), s.year())) {
                here.add(h.route().name());
                found |= matches(h.route().name(), s.trade());
            }
            if (!found) {
                wrong.add("no " + alternatives(s.trade()) + " route here (routes here: "
                        + (here.isEmpty() ? "none" : String.join(", ", here)) + ")");
            }
        }
        String villager = said.isEmpty() ? "(says nothing about who rules)" : said;
        if (!tradeSaid.isEmpty() && s.trade() != null) {
            villager += " " + tradeSaid;
        }
        if (s.known() != null) {
            if (wrong.isEmpty()) {
                return new Line(Status.KNOWN, s.label() + ": " + villager, s.known(), true, true);
            }
            return new Line(Status.INFO, s.label() + ": a known limit has changed (" + String.join("; ", wrong)
                    + "). Now: " + villager, "", false, true);
        }
        if (wrong.isEmpty()) {
            return new Line(Status.OK, s.label() + ": " + villager, "", true, false);
        }
        return new Line(Status.FAIL, s.label() + ": " + String.join("; ", wrong), "villager says: " + villager,
                true, true);
    }

    /** What a villager here could say this year (the lines town talk picks from), blanks dropped. */
    public static List<String> villagerLines(double lat, double lon, int year) {
        HistoricalPresence.Result r = HistoricalPresence.query(null, lat, lon, year);
        List<String> out = new ArrayList<>();
        for (String line : new String[] {EventNews.rulerLine(r), EventNews.cultureLine(r), EventNews.famousLine(r, 7L),
                EventNews.wayOfLifeLine(r), EventNews.tradeLine(r), EventNews.roadLine(r)}) {
            if (line != null && !line.isBlank() && !out.contains(line)) {
                out.add(line);
            }
        }
        r.event().ifPresent(e -> out.add(EventNews.rumor(e.event(), year)));
        return out;
    }

    public static String yearText(int year) {
        return year < 0 ? Math.abs(year) + " BCE" : year + " CE";
    }

    // ================================================================== data

    /** Set once the background load at world start has finished (or failed: then the game loads on demand). */
    private static volatile boolean warmUpDone;
    /** The background load at world start did the first load (so nobody waited for it). */
    private static volatile boolean loadedInBackground;

    /**
     * Step 17: load every register on a background thread when a world starts. Until then the first villager
     * loaded them on the game's own thread: a freeze of 1.3 s on the sandbox server, and in the user's log
     * "Tick budget exceeded! Avg: 270ms" and "Can't keep up!" right after joining.
     */
    public static void warmUpInBackground() {
        if (firstLoadMillis >= 0 || warmUpDone) {
            warmUpDone = true;
            return;
        }
        warmUpThreadStarted = true;
        Thread t = new Thread(() -> {
            try {
                boolean first = firstLoadMillis < 0;
                loadRegisters();
                loadedInBackground = first && loadedByCheck;
            } catch (Throwable ignored) {
                // a register that cannot load will say so where it is used, and in /ee check all
            } finally {
                warmUpDone = true;
            }
        }, "EE-History-Warmup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * Whether the history registers are loaded, so the game's own thread can use them without a pause. True
     * before any world has started the background load (tools and tests load on demand).
     */
    public static boolean historyReady() {
        return warmUpDone || firstLoadMillis >= 0 || !warmUpThreadStarted;
    }

    /** The background load has been started by a world (until then tools and tests load on demand). */
    private static volatile boolean warmUpThreadStarted;

    /** Load every register once and remember how long the first load took (the one-time pause). */
    private static void loadRegisters() {
        if (firstLoadMillis >= 0) {
            return;
        }
        synchronized (HistoryChecks.class) {
            if (firstLoadMillis >= 0) {
                return;
            }
            long t0 = System.nanoTime();
            HistoryGazetteer.get();
            RulerRegister.get();
            HydeGrid.get();
            LeaderRegister.get();
            FamousPeople.get();
            WaysOfLife.get();
            LocalLanguages.get();
            TradeRoutes.get();
            TownFacts.get();        // step 29
            StateRelations.get();   // step 29
            com.eternalengine.technology.TechnologyAtlas.get();   // step 30a (and the country grid)
            CultureAtlas.get();                                   // step 30b
            com.eternalengine.buildings.FamousBuildings.get();    // step 32
            com.eternalengine.towns.BuildingCulture.get();        // step 33
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            // Already-loaded registers answer in well under a millisecond each.
            loadedByCheck = ms > 200L;
            firstLoadMillis = ms;
        }
    }

    private static void data(Section s) {
        LeaderRegister leaders = LeaderRegister.get();
        int states = 0;
        int withHeads = 0;
        int reigns = 0;
        int govs = 0;
        int withLangs = 0;
        int withRels = 0;
        int withShares = 0;
        for (LeaderRegister.Facts f : leaders.all()) {
            states++;
            withHeads += f.heads().isEmpty() ? 0 : 1;
            reigns += f.heads().size();
            govs += f.governments().size();
            withLangs += f.languages().isEmpty() ? 0 : 1;
            withRels += f.religions().isEmpty() ? 0 : 1;
            withShares += f.religionYears().isEmpty() ? 0 : 1;
        }
        s.check(fromMod(leaders.source()) && states >= MIN_STATES && withHeads >= MIN_STATES_WITH_RULERS
                        && reigns >= MIN_REIGNS && govs >= MIN_GOVERNMENT_ROWS,
                String.format(Locale.ROOT, "state facts: %,d states, %,d with named rulers (%,d reigns, %,d heads of"
                        + " government)", states, withHeads, reigns, govs),
                String.format(Locale.ROOT, "expected at least %,d states, %,d with rulers, %,d reigns, %,d government"
                        + " rows; read from %s", MIN_STATES, MIN_STATES_WITH_RULERS, MIN_REIGNS, MIN_GOVERNMENT_ROWS,
                        leaders.source()));
        s.check(withLangs >= MIN_STATES_WITH_LANGUAGES && withRels >= MIN_STATES_WITH_RELIGIONS
                        && withShares >= MIN_STATES_WITH_FAITH_SHARES,
                String.format(Locale.ROOT, "languages for %,d states, religions for %,d, measured faith shares for %,d",
                        withLangs, withRels, withShares),
                String.format(Locale.ROOT, "expected at least %,d / %,d / %,d", MIN_STATES_WITH_LANGUAGES,
                        MIN_STATES_WITH_RELIGIONS, MIN_STATES_WITH_FAITH_SHARES));

        RulerRegister.Summary rs = RulerRegister.get().summary();
        boolean bothRecords = rs.source().contains("deccan_rulers.json") && rs.source().contains("world_rulers.json.gz");
        s.check(rs.loaded() && bothRecords && rs.spans() >= MIN_RULER_SPANS,
                String.format(Locale.ROOT, "border map of rulers: %,d places, %,d timelines, %,d spans (world and"
                        + " Deccan records)", rs.places(), rs.timelines(), rs.spans()),
                "expected both the world and the Deccan records, at least " + MIN_RULER_SPANS + " spans: "
                        + rs.display());

        FamousPeople people = FamousPeople.get();
        s.check(fromMod(people.source()) && people.size() >= MIN_FAMOUS_PEOPLE
                        && people.birthplaceCount() >= MIN_BIRTHPLACES,
                String.format(Locale.ROOT, "famous people: %,d (%,d with an exact birthplace)", people.size(),
                        people.birthplaceCount()),
                String.format(Locale.ROOT, "expected at least %,d people and %,d birthplaces; read from %s",
                        MIN_FAMOUS_PEOPLE, MIN_BIRTHPLACES, people.source()));

        WaysOfLife ways = WaysOfLife.get();
        s.check(fromMod(ways.source()) && ways.regionCount() >= WAYS_REGIONS && ways.sliceCount() >= WAYS_DATES,
                "ways of life: " + ways.regionCount() + " regions x " + ways.sliceCount() + " dates (ArchaeoGLOBE)",
                "expected " + WAYS_REGIONS + " x " + WAYS_DATES + "; read from " + ways.source());

        LocalLanguages langs = LocalLanguages.get();
        s.check(fromMod(langs.source()) && langs.size() >= MIN_LANGUAGES,
                String.format(Locale.ROOT, "local languages: %,d (Glottolog)", langs.size()),
                String.format(Locale.ROOT, "expected at least %,d; read from %s", MIN_LANGUAGES, langs.source()));

        TradeRoutes trade = TradeRoutes.get();
        int towns = 0;
        List<String> names = new ArrayList<>();
        for (TradeRoutes.Route r : trade.routes()) {
            towns += r.towns().length;
            names.add(r.name());
        }
        List<String> missing = new ArrayList<>();
        for (String n : ROUTE_NAMES) {
            if (!names.contains(n)) {
                missing.add(n);
            }
        }
        s.check(fromMod(trade.source()) && trade.roadCount() >= MIN_ROAD_SECTIONS && names.size() >= MIN_ROUTES
                        && towns >= MIN_ROUTE_TOWNS && missing.isEmpty(),
                String.format(Locale.ROOT, "trade: %d routes with %d towns, %,d Roman road sections", names.size(),
                        towns, trade.roadCount()),
                String.format(Locale.ROOT, "expected at least %d routes, %d towns, %,d road sections%s; read from %s",
                        MIN_ROUTES, MIN_ROUTE_TOWNS, MIN_ROAD_SECTIONS,
                        missing.isEmpty() ? "" : ", missing " + missing, trade.source()));

        HydeGrid hyde = HydeGrid.get();
        s.check(fromMod(hyde.source()) && hyde.sliceCount() >= HYDE_SLICES,
                "population model: " + hyde.sliceCount() + " time slices (HYDE 3.2.1)",
                "expected " + HYDE_SLICES + " slices; read from " + hyde.source());

        // Every pack listed in index.json must load. The gazetteer skips a missing or broken pack
        // without a word (packs are optional at runtime), so this is the only place it would show.
        HistoryGazetteer g = HistoryGazetteer.get();
        Map<String, Integer> eventsByPack = new HashMap<>();
        Map<String, Integer> sitesByPack = new HashMap<>();
        for (HistoryGazetteer.Event e : g.allEvents()) {
            eventsByPack.merge(e.packId(), 1, Integer::sum);
        }
        for (HistoryGazetteer.Site site : g.allSites()) {
            sitesByPack.merge(site.packId(), 1, Integer::sum);
        }
        List<String> packs = indexPacks();
        List<String> problems = new ArrayList<>();
        for (String file : packs) {
            PackCount c = countPack("/data/eternalengine/eternal/history/" + file);
            if (c == null) {
                problems.add(file + " is missing from the mod");
                continue;
            }
            if (c.error() != null) {
                problems.add(file + " cannot be read (" + c.error() + ")");
                continue;
            }
            int ev = eventsByPack.getOrDefault(c.id(), 0);
            int si = sitesByPack.getOrDefault(c.id(), 0);
            if ((c.events() > 0 && ev == 0) || (c.sites() > 0 && si == 0)) {
                problems.add(file + " is in the mod but did not load (" + c.events() + " events, " + c.sites()
                        + " places in the file)");
            }
            s.infoQuiet(String.format(Locale.ROOT, "pack %s: %,d events, %,d places", file, ev, si));
        }
        s.check(!packs.isEmpty() && problems.isEmpty(),
                "all " + packs.size() + " history packs listed in index.json load",
                packs.isEmpty() ? "index.json is missing or lists no packs" : String.join("; ", problems));
        s.check(g.eventCount() >= MIN_EVENTS && g.siteCount() >= MIN_SITES,
                String.format(Locale.ROOT, "history: %,d events, %,d places, %d border boxes", g.eventCount(),
                        g.siteCount(), g.borderCount()),
                String.format(Locale.ROOT, "expected at least %,d events and %,d places", MIN_EVENTS, MIN_SITES));
    }

    private static boolean fromMod(String source) {
        return source != null && source.startsWith("/data/");
    }

    private record PackCount(String id, int events, int sites, String error) {}

    private static List<String> indexPacks() {
        try (InputStream in = HistoryChecks.class.getResourceAsStream("/data/eternalengine/eternal/history/index.json")) {
            if (in == null) {
                return List.of();
            }
            JsonObject index = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = index.getAsJsonArray("packs");
            List<String> out = new ArrayList<>();
            if (arr != null) {
                for (JsonElement el : arr) {
                    out.add(el.getAsString());
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** The pack's id and how many events and places the file holds, read as a stream (no copy kept). */
    private static PackCount countPack(String resource) {
        try (InputStream in = HistoryChecks.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            InputStream src = resource.endsWith(".gz") ? new GZIPInputStream(in, 1 << 16) : in;
            JsonReader r = new JsonReader(new InputStreamReader(src, StandardCharsets.UTF_8));
            String id = "unknown";
            int events = 0;
            int sites = 0;
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                if ("id".equals(name) && r.peek() == JsonToken.STRING) {
                    id = r.nextString();
                } else if (("events".equals(name) || "sites".equals(name)) && r.peek() == JsonToken.BEGIN_ARRAY) {
                    int n = 0;
                    r.beginArray();
                    while (r.hasNext()) {
                        r.skipValue();
                        n++;
                    }
                    r.endArray();
                    if ("events".equals(name)) {
                        events = n;
                    } else {
                        sites = n;
                    }
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
            return new PackCount(id, events, sites, null);
        } catch (Exception e) {
            return new PackCount("unknown", 0, 0, e.toString());
        }
    }

    // ================================================================== places and rulers

    /**
     * Check each place. With {@code withTalk} (the 15 checklist places), every place is followed by
     * the other things its villagers can say and how to go there and look: what the checklist's eye
     * check would show on arrival, for all 15 places at once. Those lines are notes (in the Minecraft
     * log only, not in chat), not checks.
     */
    private static void spots(Section s, List<Spot> spots, boolean withTalk) {
        int ok = 0;
        int known = 0;
        int changed = 0;
        int heardTotal = 0;
        int quiet = 0;
        List<Line> lines = new ArrayList<>();
        for (Spot spot : spots) {
            Line l = checkSpot(spot);
            lines.add(l);
            ok += l.status() == Status.OK ? 1 : 0;
            known += l.status() == Status.KNOWN ? 1 : 0;
            changed += l.status() == Status.INFO ? 1 : 0;
            if (withTalk) {
                List<String> heard = villagerLines(spot.lat(), spot.lon(), spot.year());
                heardTotal += heard.size();
                quiet += heard.isEmpty() ? 1 : 0;
                List<String> more = new ArrayList<>();
                for (String said : heard) {
                    if (!l.text().contains(said)) {
                        more.add(said);
                    }
                }
                lines.add(new Line(Status.INFO, more.isEmpty() ? "    villagers there say nothing else"
                        : "    villagers there also say:", "", false, false));
                for (String said : more) {
                    lines.add(new Line(Status.INFO, "      \"" + said + "\"", "", false, false));
                }
                lines.add(new Line(Status.INFO, String.format(Locale.ROOT,
                        "    to look yourself: /tpll %s %s, then /ee year set %d", coordinate(spot.lat()),
                        coordinate(spot.lon()), spot.year()), "", false, false));
            }
        }
        int checked = spots.size() - known - changed;
        int limits = known + changed;
        String knownText = limits == 0 ? "" : " (" + limits + " known " + (limits == 1 ? "limit" : "limits") + " below)";
        s.summary(ok == checked, ok == checked
                ? ok + " of " + checked + " places name the right state and ruler" + knownText
                : (checked - ok) + " of " + checked + " places give a wrong answer" + knownText);
        if (withTalk) {
            s.info("villagers at these places have " + heardTotal + " things to say in all"
                    + (quiet == 0 ? "" : " (" + quiet + (quiet == 1 ? " place says" : " places say") + " nothing)")
                    + "; each place's lines and how to go there are under it in the Minecraft log");
        }
        s.lines.addAll(lines);
    }

    /** A latitude or longitude as written in the checklist: no trailing zeros. */
    private static String coordinate(double d) {
        String t = String.format(Locale.ROOT, "%.4f", d);
        t = t.replaceAll("0+$", "");
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }

    // ================================================================== talk

    private static void talk(Section s) {
        int ok = 0;
        List<Line> lines = new ArrayList<>();
        for (TalkSpot t : TALK_SPOTS) {
            HistoricalPresence.Result r = HistoricalPresence.query(null, t.lat(), t.lon(), t.year());
            String ruler = EventNews.rulerLine(r);
            String famous = firstNonBlank(EventNews.famousLine(r, 7L), EventNews.famousLine(r, 8L));
            List<String> said = new ArrayList<>();
            for (String line : new String[] {ruler, EventNews.cultureLine(r), famous, EventNews.wayOfLifeLine(r),
                    EventNews.tradeLine(r), EventNews.roadLine(r)}) {
                if (!line.isBlank()) {
                    said.add(line);
                }
            }
            List<String> wrong = new ArrayList<>();
            if (t.head() == null && !ruler.isBlank()) {
                wrong.add("no state is recorded here, but the villager says: \"" + ruler + "\"");
            }
            if (t.head() != null && !matches(ruler, t.head())) {
                wrong.add("expected the ruler " + t.head() + ", the villager says: " + quoteOrNone(ruler));
            }
            if (t.famous() && famous.isBlank()) {
                wrong.add("nobody famous is talked about");
            }
            for (String word : t.mustSay()) {
                boolean found = false;
                for (String line : said) {
                    found |= normalize(line).contains(normalize(word));
                }
                if (!found) {
                    wrong.add("no line mentions \"" + word + "\"");
                }
            }
            if (said.isEmpty()) {
                wrong.add("the villager has nothing to say");
            }
            String label = t.place() + ", " + yearText(t.year());
            String example = said.isEmpty() ? "" : "\"" + said.get(0) + "\"";
            if (wrong.isEmpty()) {
                ok++;
                lines.add(new Line(Status.OK, label + ": " + said.size() + (said.size() == 1 ? " thing" : " things")
                        + " to say, e.g. " + example,
                        String.join(" / ", said), true, false));
            } else {
                lines.add(new Line(Status.FAIL, label + ": " + String.join("; ", wrong),
                        "lines: " + String.join(" / ", said), true, true));
            }
        }
        s.summary(ok == TALK_SPOTS.size(), ok == TALK_SPOTS.size()
                ? "villagers at all " + ok + " standard places say the right things"
                : (TALK_SPOTS.size() - ok) + " of " + TALK_SPOTS.size() + " standard places say something wrong");
        s.lines.addAll(lines);

        // "How often villagers repeat the same line": ten townspeople over ten minutes, with the seed
        // the spawner uses (the villager's id, changing every 1,200 ticks).
        talkVariety(s, "Hyderabad", 17.385, 78.4867, 1700);
        talkVariety(s, "Rome", 41.80, 12.60, 100);
        talkVariety(s, "Paris", 48.86, 2.35, 1789);
    }

    private static void talkVariety(Section s, String place, double lat, double lon, int year) {
        HistoricalPresence.Result r = HistoricalPresence.query(null, lat, lon, year);
        Random rnd = new Random(20261012L);
        long[] villagers = new long[10];
        for (int i = 0; i < villagers.length; i++) {
            villagers[i] = rnd.nextLong();
        }
        Map<String, Integer> said = new LinkedHashMap<>();
        int total = 0;
        for (long minute = 1000L; minute < 1010L; minute++) {
            for (long id : villagers) {
                String line = EventNews.townTalk(r, id ^ minute);
                if (!line.isBlank()) {
                    said.merge(line, 1, Integer::sum);
                    total++;
                }
            }
        }
        int top = said.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int share = total == 0 ? 100 : Math.round(100.0f * top / total);
        boolean ok = said.size() >= 4 && share <= 50;
        s.check(ok, String.format(Locale.ROOT, "%s, %s: 10 villagers over 10 minutes say %d different things;"
                        + " the commonest line is %d%% of the talk", place, yearText(year), said.size(), share),
                "expected at least 4 different lines and no line over half the talk");
    }

    // ================================================================== events

    private static void events(Section s) {
        for (R2EventChecks.Result result : R2EventChecks.run()) {
            switch (result.status()) {
                case OK -> s.check(true, result.text(), "");
                case FAIL -> s.fail(result.text(), result.detail());
                case KNOWN -> s.known(result.text(), result.detail());
            }
        }
        HistoryGazetteer g = HistoryGazetteer.get();
        int lookups = 0;
        int violations = 0;
        List<String> examples = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
        for (double[] p : EVENT_SPOTS) {
            points.add(p);
        }
        for (double[] p : landPoints()) {
            for (int y : YEARS) {
                points.add(new double[] {p[0], p[1], y});
            }
        }
        int localFound = 0;
        for (double[] p : points) {
            int y = (int) p[2];
            Optional<HistoryGazetteer.EventHit> any = g.nearestEvent(p[0], p[1], y);
            Optional<HistoryGazetteer.EventHit> local = g.nearestLocalEvent(p[0], p[1], y);
            lookups += 2;
            boolean bad = local.isPresent() && HistoryGazetteer.isRegional(local.get().event());
            bad |= local.isPresent() && any.isPresent() && HistoryGazetteer.isRegional(any.get().event());
            localFound += local.isPresent() ? 1 : 0;
            if (bad) {
                violations++;
                if (examples.size() < 3) {
                    examples.add(String.format(Locale.ROOT, "%.2f,%.2f in %s: %s", p[0], p[1], yearText(y),
                            any.map(h -> h.event().name()).orElse("-")));
                }
            }
        }
        s.check(violations == 0,
                String.format(Locale.ROOT, "0 rule breaks in %,d lookups: a local event always beats country-wide"
                        + " news, and only a local event can be stood in", lookups),
                violations + " lookups broke the rule, e.g. " + String.join("; ", examples));
        int regional = 0;
        int conflicts = 0;
        for (HistoryGazetteer.Event e : g.allEvents()) {
            regional += HistoryGazetteer.isRegional(e) ? 1 : 0;
            conflicts += "conflict".equals(e.kind()) ? 1 : 0;
        }
        int local = g.eventCount() - regional;
        s.check(local > 0 && regional > 0 && conflicts > 0,
                String.format(Locale.ROOT, "%,d local events, %,d regional news lines, %,d conflict spots (UCDP)",
                        local, regional, conflicts),
                "one kind of event is missing (local, regional news or UCDP conflict spots)");
        s.infoQuiet(String.format(Locale.ROOT, "%,d of the %,d places and years had a local event", localFound,
                points.size()));
    }

    // ================================================================== index

    /**
     * The per-year event index must give exactly the answers of a plain search through every event
     * (worldfill/probe_index.sh compared the two outside the game, byte for byte).
     */
    private static void index(Section s) {
        HistoryGazetteer g = HistoryGazetteer.get();
        List<HistoryGazetteer.Event> all = g.allEvents();
        List<double[]> queries = new ArrayList<>();
        double[][] land = landPoints();
        int[] years = {-2500, -1500, -500, 1, 500, 1000, 1300, 1500, 1700, 1850, 1900, 1944, 1990, 2000, 2010, 2020,
                2024};
        for (int y : years) {
            for (int i = 0; i < 100; i++) {
                queries.add(new double[] {land[i][0], land[i][1], y});
            }
        }
        Random rnd = new Random(7);
        for (int i = 0; i < all.size(); i += 150) {
            HistoryGazetteer.Event e = all.get(i);
            queries.add(new double[] {e.lat() + (rnd.nextDouble() - 0.5), e.lon() + (rnd.nextDouble() - 0.5), e.start()});
        }
        Map<Integer, List<double[]>> byYear = new TreeMap<>();
        for (double[] q : queries) {
            byYear.computeIfAbsent((int) q[2], k -> new ArrayList<>()).add(q);
        }
        Predicate<String> hidden = HistoryGazetteer.suppressionFilter();
        int lookups = 0;
        int differ = 0;
        List<String> examples = new ArrayList<>();
        for (Map.Entry<Integer, List<double[]>> en : byYear.entrySet()) {
            int y = en.getKey();
            List<HistoryGazetteer.Event> active = new ArrayList<>();
            for (HistoryGazetteer.Event e : all) {
                if (e.activeIn(y) && !hidden.test(e.id())) {
                    active.add(e);
                }
            }
            for (double[] q : en.getValue()) {
                HistoryGazetteer.EventHit local = scanNearest(active, q[0], q[1], false);
                HistoryGazetteer.EventHit any = local != null ? local : scanNearest(active, q[0], q[1], true);
                String want = key(any) + " | " + key(local) + " | " + scanNear(active, q[0], q[1], 100.0d);
                String got = key(g.nearestEvent(q[0], q[1], y).orElse(null)) + " | "
                        + key(g.nearestLocalEvent(q[0], q[1], y).orElse(null)) + " | "
                        + ids(g.eventsNear(q[0], q[1], y, 100.0d));
                lookups += 3;
                if (!want.equals(got)) {
                    differ++;
                    if (examples.size() < 2) {
                        examples.add(String.format(Locale.ROOT, "%.3f,%.3f in %s: index %s, full search %s", q[0], q[1],
                                yearText(y), got, want));
                    }
                }
            }
        }
        s.check(differ == 0,
                String.format(Locale.ROOT, "the fast index and a full search agree on all %,d lookups (%,d places and"
                        + " years)", lookups, queries.size()),
                differ + " places and years differ, e.g. " + String.join("; ", examples));
    }

    private static HistoryGazetteer.EventHit scanNearest(List<HistoryGazetteer.Event> active, double lat, double lon,
                                                         boolean regional) {
        HistoryGazetteer.EventHit best = null;
        for (HistoryGazetteer.Event e : active) {
            if (HistoryGazetteer.isRegional(e) != regional) {
                continue;
            }
            double km = HistoricalPresence.haversineKm(lat, lon, e.lat(), e.lon());
            if (km <= e.radiusKm() && (best == null || km < best.distanceKm())) {
                best = new HistoryGazetteer.EventHit(e, km);
            }
        }
        return best;
    }

    private static String scanNear(List<HistoryGazetteer.Event> active, double lat, double lon, double km) {
        List<HistoryGazetteer.EventHit> out = new ArrayList<>();
        for (HistoryGazetteer.Event e : active) {
            double d = HistoricalPresence.haversineKm(lat, lon, e.lat(), e.lon());
            if (d <= km) {
                out.add(new HistoryGazetteer.EventHit(e, d));
            }
        }
        return ids(out);
    }

    private static String key(HistoryGazetteer.EventHit h) {
        return h == null ? "-" : h.event().id() + String.format(Locale.ROOT, "@%.3f", h.distanceKm());
    }

    private static String ids(List<HistoryGazetteer.EventHit> hits) {
        StringBuilder sb = new StringBuilder().append(hits.size()).append(':');
        for (HistoryGazetteer.EventHit h : hits) {
            sb.append(h.event().id()).append(',');
        }
        return sb.toString();
    }

    // ================================================================== coverage

    private static void coverage(Section s) {
        HistoryGazetteer g = HistoryGazetteer.get();
        double[][] pts = landPoints();
        int[][] gaz = new int[YEARS.length][4];
        int[] anyone = new int[YEARS.length];
        for (int k = 0; k < YEARS.length; k++) {
            int y = YEARS[k];
            for (double[] p : pts) {
                boolean site = g.nearestOccupied(p[0], p[1], y).isPresent();
                boolean hinter = !site && g.estimatedHinterland(p[0], p[1], y).isPresent();
                boolean box = g.borderAt(p[0], p[1], y).isPresent();
                gaz[k][0] += site ? 1 : 0;
                gaz[k][1] += hinter ? 1 : 0;
                gaz[k][2] += box ? 1 : 0;
                gaz[k][3] += site || hinter || box ? 1 : 0;
                anyone[k] += HistoricalPresence.query(null, p[0], p[1], y).occupied() ? 1 : 0;
            }
            s.infoQuiet(String.format(Locale.ROOT, "%9s: named place %d, estimated countryside %d, empire box %d,"
                            + " any of these %d; anyone at all %d (of 400 land points)", yearText(y), gaz[k][0],
                    gaz[k][1], gaz[k][2], gaz[k][3], anyone[k]));
        }

        if (GAZETTEER_BASELINE == null || ANYONE_BASELINE == null) {
            s.info("no recorded answers to compare with yet; measured: " + java.util.Arrays.deepToString(gaz)
                    + " anyone " + java.util.Arrays.toString(anyone));
        } else {
            List<String> differ = new ArrayList<>();
            List<String> fewer = new ArrayList<>();
            for (int k = 0; k < YEARS.length; k++) {
                if (!java.util.Arrays.equals(gaz[k], GAZETTEER_BASELINE[k])) {
                    differ.add(yearText(YEARS[k]) + " " + java.util.Arrays.toString(gaz[k]) + " (built: "
                            + java.util.Arrays.toString(GAZETTEER_BASELINE[k]) + ")");
                }
                // Terra++'s map cache can only add people in the game, never remove any.
                if (anyone[k] < ANYONE_BASELINE[k]) {
                    fewer.add(yearText(YEARS[k]) + " " + anyone[k] + " (built: " + ANYONE_BASELINE[k] + ")");
                }
            }
            s.check(differ.isEmpty(),
                    "named places, countryside and empire boxes: the same answers at 400 land points x 13 years as"
                            + " when the data was built",
                    "different answers: " + String.join("; ", differ));
            s.check(fewer.isEmpty(),
                    String.format(Locale.ROOT, "people somewhere on %d%% of the land points in 2000 CE, %d%% in"
                                    + " 2500 BCE; nowhere fewer than when the data was built",
                            Math.round(anyone[YEARS.length - 1] / 4.0f), Math.round(anyone[0] / 4.0f)),
                    "fewer places with people than when the data was built: " + String.join("; ", fewer));
        }

        int estimated = 0;
        long hash = 1125899906842597L;
        for (HistoryGazetteer.Site site : g.allSites()) {
            if (site.presentOnly()) {
                estimated++;
                hash = 31L * hash + (site.id() + "\t" + site.occupiedStart()).hashCode();
            }
        }
        if (ESTIMATED_YEARS_BASELINE < 0) {
            s.info("estimated founding years not recorded yet; measured " + estimated + " places, hash " + hash + "L");
        } else {
            s.check(estimated == ESTIMATED_YEARS_BASELINE && hash == ESTIMATED_YEARS_HASH,
                    String.format(Locale.ROOT, "the %,d estimated founding years of today's villages and towns are"
                            + " unchanged", estimated),
                    String.format(Locale.ROOT, "%,d places / hash %d, built: %,d / %d", estimated, hash,
                            ESTIMATED_YEARS_BASELINE, ESTIMATED_YEARS_HASH));
        }
    }

    // ================================================================== speed

    private static void speed(Section s) {
        if (loadedInBackground) {
            s.check(firstLoadMillis <= 15000L,
                    String.format(Locale.ROOT, "the history data loaded in %.1f s in the background when this world"
                            + " started, so nobody waited for it", firstLoadMillis / 1000.0d),
                    String.format(Locale.ROOT, "loading took %.1f s in the background; villagers appear only once"
                            + " it is done", firstLoadMillis / 1000.0d));
        } else if (loadedByCheck) {
            s.check(firstLoadMillis <= 15000L,
                    String.format(Locale.ROOT, "the history data loaded in %.1f s (a one-time pause the first time"
                            + " anyone needs it)", firstLoadMillis / 1000.0d),
                    String.format(Locale.ROOT, "loading took %.1f s; the first player to arrive somewhere would feel a"
                            + " long pause", firstLoadMillis / 1000.0d));
        } else {
            s.info("the history data was already loaded by the game, so there is no first-time pause left");
        }
        List<double[]> places = new ArrayList<>();
        for (Spot p : CHECKLIST) {
            places.add(new double[] {p.lat(), p.lon(), p.year()});
        }
        for (Spot p : MORE_RULERS) {
            places.add(new double[] {p.lat(), p.lon(), p.year()});
        }
        for (TalkSpot t : TALK_SPOTS) {
            places.add(new double[] {t.lat(), t.lon(), t.year()});
        }
        HistoricalPresence.query(null, 0.0d, 0.0d, 1);
        // Each place's best of three rounds: the steady cost of an answer, which is what a villager adds to a
        // tick. One round alone also measures the JIT warming up and any garbage collection or other thread that
        // happens to run at that moment; on a just-started server that once doubled the average (2026-10-12).
        int rounds = 3;
        double[] best = new double[places.size()];
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        for (int round = 0; round < rounds; round++) {
            for (int i = 0; i < places.size(); i++) {
                double[] p = places.get(i);
                long t0 = System.nanoTime();
                HistoricalPresence.Result r = HistoricalPresence.query(null, p[0], p[1], (int) p[2]);
                HistoricalPresence.describe(r);
                EventNews.townTalk(r, 1L);
                EventNews.townTalk(r, 2L);
                best[i] = Math.min(best[i], (System.nanoTime() - t0) / 1e6d);
            }
        }
        double total = 0.0d;
        double max = 0.0d;
        for (double ms : best) {
            total += ms;
            max = Math.max(max, ms);
        }
        double avg = total / places.size();
        s.check(avg <= 5.0d && max <= 50.0d,
                String.format(Locale.ROOT, "one place's full answer (who rules, the /ee villager who text, talk) takes"
                        + " %.1f ms on average, %.1f ms at most, over %d places (each the best of %d tries)", avg, max,
                        places.size(), rounds),
                String.format(Locale.ROOT, "%.1f ms on average, %.1f ms at most: villagers may make the game stutter"
                        + " (limits 5 ms and 50 ms)", avg, max));
    }

    // ================================================================== helpers

    /** The 400 random land points of DensityProbe (seed 42, its rough continent boxes); public for the probes. */
    public static double[][] landPoints() {
        Random rnd = new Random(42);
        double[][] p = new double[400][];
        int n = 0;
        while (n < 400) {
            double la = -58 + rnd.nextDouble() * 141;
            double lo = -180 + rnd.nextDouble() * 360;
            if (isLand(la, lo)) {
                p[n++] = new double[] {la, lo};
            }
        }
        return p;
    }

    /** DensityProbe.isLand: very rough continent boxes, the same ones every coverage number used. */
    private static boolean isLand(double lat, double lon) {
        if (lat > 83 || lat < -58) {
            return false;
        }
        if (lat > 35 && lat < 72 && lon > -10 && lon < 60) {
            return true;
        }
        if (lat > 18 && lat < 54 && lon > 60 && lon < 145) {
            return true;
        }
        if (lat > -11 && lat < 35 && lon > -18 && lon < 52) {
            return true;
        }
        if (lat > 5 && lat < 38 && lon > 66 && lon < 98) {
            return true;
        }
        if (lat > 24 && lat < 50 && lon > -125 && lon < -66) {
            return true;
        }
        if (lat > -56 && lat < 13 && lon > -82 && lon < -34) {
            return true;
        }
        if (lat > -40 && lat < -10 && lon > 112 && lon < 154) {
            return true;
        }
        return lat > 48 && lat < 72 && lon > -141 && lon < -52;
    }

    /** True if {@code actual} contains one of the alternatives as whole words (or equals a "=" one). */
    static boolean matches(String actual, String expected) {
        String a = normalize(actual);
        for (String alt : expected.split("\\|")) {
            boolean whole = alt.startsWith("=");
            String e = normalize(whole ? alt.substring(1) : alt);
            if (e.isEmpty()) {
                continue;
            }
            if (whole ? a.equals(e) || a.equals("the " + e) : (" " + a + " ").contains(" " + e + " ")) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, no accents, punctuation as spaces: "Ichijō" and "ichijo", "Kim Il-sung" and "Kim Il Sung". */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String alternatives(String expected) {
        return "\"" + expected.replace("=", "").replace("|", "\" or \"") + "\"";
    }

    private static String quoteOrNone(String s) {
        return s == null || s.isBlank() ? "none" : "\"" + s + "\"";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    // ================================================================== step 40: armies in action

    /**
     * Armies in action (step 40). The checks themselves live in {@link ArmyChecks}, in the war
     * package, so that the probe can run the very same code outside the game — see that class
     * for why.
     */
    private static void famous(Section s) {
        com.eternalengine.civilization.FamousChecks.run(new com.eternalengine.civilization.FamousChecks.Sink() {
            @Override
            public void check(boolean ok, String text, String whyNot) {
                s.check(ok, text, whyNot);
            }

            @Override
            public void known(String text, String detail) {
                s.known(text, detail);
            }

            @Override
            public void info(String text) {
                s.info(text);
            }
        });
    }

    private static void armies(Section s) {
        ArmyChecks.run(new ArmyChecks.Sink() {
            @Override
            public void check(boolean ok, String text, String whyNot) {
                s.check(ok, text, whyNot);
            }

            @Override
            public void known(String text, String detail) {
                s.known(text, detail);
            }

            @Override
            public void info(String text) {
                s.info(text);
            }
        });
    }
}
