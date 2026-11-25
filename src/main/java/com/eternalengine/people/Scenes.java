package com.eternalengine.people;

import com.eternalengine.civilization.CultureAtlas;
import com.eternalengine.civilization.Disasters;
import com.eternalengine.civilization.Disasters.Disaster;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 39 (D9 event scenes): <b>what is going on here, and who is in it</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>{@code PresenceLocalSpawner.sceneRoles(effect)} is four keyword tests on the event's effect
 * string, handing back two or three copies of {@code guard}, {@code citizen} or {@code merchant}.
 * That is the whole of the mod's scene-making, and it cannot tell a coronation from a cattle
 * market. It does not know what year it is, where it is standing, or who is available.
 *
 * <h3>What a scene is here</h3>
 *
 * <p>Five things, and not one of them is a list of entities:
 *
 * <ul>
 * <li><b>what</b> it is — one of the twenty D9 names;</li>
 * <li><b>when</b> it is on — the trigger. Seven kinds, and every one of them is asked of a
 *     register that already exists: the event gazetteer (step 28), the hazard pack (step 36),
 *     {@code DayRound} for market and prayer days (step 38), {@code CultureAtlas} for feasts
 *     (step 30b), {@code Migrations} for refugees (step 38), the incident types, and the life
 *     table itself. <b>There is no new "when" pack.</b></li>
 * <li><b>where</b> it stands — the kind of ground it needs, which is not the same for all of
 *     them;</li>
 * <li><b>who</b> is in it — a cast given in work sectors and drawn from step 38's {@code Jobs};</li>
 * <li><b>what they are doing</b> — the business of the scene, which is what you walk into.</li>
 * </ul>
 *
 * <h3>Two rules that keep it honest</h3>
 *
 * <p><b>A scene needs a quorum.</b> A coronation wants three priests and three clerks. Rome has
 * them. A band of twenty-five foragers, whose regime gives two in the hundred to the shaman and
 * none at all to clerks, has nobody — because two in the hundred of twenty-five people is not a
 * person. So each part is filled up to the number of such people the place actually holds, and
 * where fewer than half the parts can be filled the scene is not on at all. A foraging band does
 * not hold coronations, and this register does not pretend otherwise by putting a farmer in a
 * borrowed robe.
 *
 * <p><b>Where the register does not know, it declines.</b> It will not say whether the hour of
 * prayer is on unless you tell it what hour it is. Better an empty street than a confident guess.
 *
 * <h3>What it does not do</h3>
 *
 * <p><b>It does not stage fighting.</b> Weapons and army gear are step 45 and armies in action are
 * step 40, so the battle and siege scenes hold the part of a war that is not the battle: the camp,
 * the baggage, the wounded, the watchers, and — for a siege — how short of food the town is, which
 * is what a siege is actually about.
 *
 * <p><b>The suffering scenes are scenes of absence.</b> Five of the twenty are about people not
 * being there. The register says how much quieter the street is, not how many corpses there are.
 * A plague scene with seven people in it is a street that should have had seventy.
 *
 * <p>Funerals come from the death rate. In a place where a newborn expects forty years, one person
 * in forty dies each year, and every death is a funeral somebody sees. That is the life table's
 * own number, not an invention.
 */
public final class Scenes {

    /** What sets a scene off. */
    public enum Trigger {
        /** A dated event from step 28's gazetteer. */
        EVENT,
        /** A hazard in force from step 36's pack. */
        DISASTER,
        /** The market day, from step 38's {@code DayRound}. */
        MARKET,
        /** An hour of prayer or a feast day, from {@code DayRound} and step 30b's atlas. */
        CALENDAR,
        /** A recorded movement of people, from step 38's {@code Migrations}. */
        MIGRATION,
        /** A generated incident. */
        INCIDENT,
        /** Something that happens to a family: a wedding, a funeral. */
        LIFE
    }

    /** One part in the cast: how many people of what line of work. */
    public record Part(String sector, int count) {}

    /** One kind of gathering. */
    public record Scene(String id, String name, String group, String where, Trigger trigger,
                        List<Part> cast, String doing, String note, String source, String mark) {
        /** How many people are in it when it is fully cast. */
        public int size() {
            int n = 0;
            for (Part p : cast) {
                n += p.count();
            }
            return n;
        }

        /** How many parts went unfilled here. */
        public int missing(List<Person> people) {
            int n = 0;
            if (people != null) {
                for (Person p : people) {
                    if (p.work() == null) {
                        n++;
                    }
                }
            }
            return Math.max(0, size() - (people == null ? 0 : people.size())) + n;
        }

        /** Is this composition this table's own reading rather than a source's? */
        public boolean estimated() {
            return "estimated".equals(mark);
        }

        /** The mark, in words, for a sentence. */
        public String markLine() {
            return switch (mark == null ? "" : mark) {
                case "mixed" -> " (the sources disagree, and this is the middle reading)";
                case "estimated" -> " (this composition is the mod's own reading, not a source's)";
                default -> "";
            };
        }
    }

    /** One person cast in a scene. {@code work} is null when the place could not supply anyone of
     * that sort, which the scene says out loud rather than filling the part. */
    public record Person(String part, String work, String word, int age, int folk) {
        /** "a priest of 41, with five at home". */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(work == null ? "nobody" : "a " + word).append(" of ").append(age);
            if (folk > 1) {
                sb.append(", with ").append(folk).append(" at home");
            }
            return sb.toString();
        }
    }

    /** A scene, in a place, on a day, with the people who are in it. */
    public record Occasion(Scene scene, List<Person> people, String why, double absence,
                           String line) {
        /** How many of the parts could not be filled here. */
        public int missing() {
            return scene.missing(people);
        }

        /** How many parts were filled. */
        public int filled() {
            int n = 0;
            for (Person p : people) {
                if (p.work() != null) {
                    n++;
                }
            }
            return n;
        }
    }

    /**
     * A place and a moment, as much of it as the caller knows. Built with {@link #of(double,
     * double, int)} and the {@code with} methods; anything left unset the register treats as
     * unknown and declines to guess.
     *
     * @param hour      0 to 23, or -1 when the caller does not know. The hour of prayer needs it
     * @param dayNumber a count of days, for the market cycle
     * @param eventText the kind, effect and name of the event here now, joined up, or null. The
     *                  register does not go and look: step 28's gazetteer carries Minecraft types,
     *                  and whoever already has the event in hand can hand over the words
     * @param incident  the name of the incident here now, or null. Same reason: the register does
     *                  not go and generate one. An election, a protest and a trial are the three
     *                  scenes that hang off this
     * @param crowd     how many people live here, which is what decides whether a scene can be
     *                  cast at all
     */
    public record Where(double lat, double lon, int year, int month, int day, long dayNumber,
                        int hour, boolean urban, String faith, String eventText, String incident,
                        int crowd) {

        /** A place and a year, with everything else unknown. */
        public static Where of(double lat, double lon, int year) {
            return new Where(lat, lon, year, 1, 1, 0L, -1, false, null, null, null, 200);
        }

        /** With the date in the year. */
        public Where on(int m, int d) {
            return new Where(lat, lon, year, m, d, dayNumber, hour, urban, faith, eventText,
                incident, crowd);
        }

        /** With a count of days, for the market cycle. */
        public Where day(long n) {
            return new Where(lat, lon, year, month, day, n, hour, urban, faith, eventText,
                incident, crowd);
        }

        /** With the hour of day, 0 to 23. */
        public Where at(int h) {
            return new Where(lat, lon, year, month, day, dayNumber, h, urban, faith, eventText,
                incident, crowd);
        }

        /** Whether this is a town. */
        public Where town(boolean b) {
            return new Where(lat, lon, year, month, day, dayNumber, hour, b, faith, eventText,
                incident, crowd);
        }

        /** The faith whose hours and feasts are kept here. */
        public Where keeps(String f) {
            return new Where(lat, lon, year, month, day, dayNumber, hour, urban, f, eventText,
                incident, crowd);
        }

        /** The event here now, by its own words. */
        public Where holding(String text) {
            return new Where(lat, lon, year, month, day, dayNumber, hour, urban, faith, text,
                incident, crowd);
        }

        /** The incident here now, by its name. */
        public Where after(String name) {
            return new Where(lat, lon, year, month, day, dayNumber, hour, urban, faith, eventText,
                name, crowd);
        }

        /** How many people live here. */
        public Where folk(int n) {
            return new Where(lat, lon, year, month, day, dayNumber, hour, urban, faith, eventText,
                incident, Math.max(1, n));
        }
    }

    /** Which dated event sets off which scene. An explicit table, not four keyword tests. */
    private static final String[][] EVENT_MATCH = {
        {"crowning", "coronation", "crown", "crowned", "accession", "anointed"},
        {"procession", "triumph", "procession", "entry", "visit", "pilgrimage"},
        {"court_day", "court", "council", "treaty", "parliament", "assembly", "synod", " diets"},
        {"caravan_arrival", "voyage", "expedition", "arrival", "discovery", "port", "caravan"},
        {"building_works", "built", "construction", "founded", "completed", "cathedral", "bridge"},
        // siege before battle: "war siege" contains "war", and a siege is not a battle
        {"siege", "siege", "blockade", "sack"},
        {"battle", "battle", "war", "rebellion", "revolt", "massacre", "campaign", "conquest"},
    };

    /** The hour a word for a time of day means, so an hour of prayer can be matched. */
    private static final String[][] HOUR_WORDS = {
        {"before dawn", "4"}, {"first light", "5"}, {"dawn", "5"}, {"daybreak", "5"},
        {"sunrise", "6"}, {"early morning", "7"}, {"morning", "8"}, {"forenoon", "10"},
        {"third hour", "9"}, {"sixth hour", "12"}, {"ninth hour", "15"}, {"midday", "12"},
        {"noon", "12"}, {"afternoon", "15"}, {"sunset", "19"}, {"sundown", "19"},
        {"dusk", "20"}, {"evening", "18"}, {"twilight", "20"}, {"night", "22"},
        {"late night", "23"}, {"midnight", "0"}, {"before sunrise", "4"},
    };

    private static final Scenes INSTANCE = new Scenes();
    private final List<Scene> rows;

    private Scenes() {
        List<Scene> out = new ArrayList<>();
        JsonObject root = read();
        if (root != null && root.has("scenes")) {
            for (JsonElement e : root.getAsJsonArray("scenes")) {
                JsonObject o = e.getAsJsonObject();
                JsonArray sec = o.getAsJsonArray("sectors");
                JsonArray cnt = o.getAsJsonArray("counts");
                List<Part> cast = new ArrayList<>();
                if (sec != null && cnt != null) {
                    for (int i = 0; i < sec.size() && i < cnt.size(); i++) {
                        cast.add(new Part(sec.get(i).getAsString(), cnt.get(i).getAsInt()));
                    }
                }
                out.add(new Scene(str(o, "id"), str(o, "name"), str(o, "group"), str(o, "where"),
                    triggerOf(str(o, "trigger")), Collections.unmodifiableList(cast),
                    str(o, "doing"), str(o, "note"), str(o, "source"), str(o, "mark")));
            }
        }
        this.rows = Collections.unmodifiableList(out);
    }

    /** The one register. */
    public static Scenes get() {
        return INSTANCE;
    }

    /** Every scene we hold. */
    public static List<Scene> all() {
        return get().rows;
    }

    /** How many scenes there are. */
    public static int count() {
        return get().rows.size();
    }

    /** The scene with this id, or null. */
    public static Scene find(String id) {
        for (Scene s : get().rows) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    private static Trigger triggerOf(String s) {
        for (Trigger t : Trigger.values()) {
            if (t.name().equalsIgnoreCase(s)) {
                return t;
            }
        }
        return Trigger.EVENT;
    }

    // ================================================================== when a scene is on

    /**
     * Which scenes are on here now. Empty is an honest answer, and it is the common one: most
     * places on most days are not holding anything.
     */
    public static List<Scene> on(Where w) {
        List<Scene> out = new ArrayList<>();
        if (w == null) {
            return out;
        }
        for (Scene s : get().rows) {
            if (on(s, w)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Is this one scene on here, now? */
    public static boolean on(Scene s, Where w) {
        if (s == null || w == null) {
            return false;
        }
        if (!triggered(s, w)) {
            return false;
        }
        // a scene needs a quorum: a band of twenty-five does not hold a coronation
        return available(s, w) * 2 >= s.size();
    }

    /** Is whatever sets this scene off happening? */
    public static boolean triggered(Scene s, Where w) {
        if (s == null || w == null) {
            return false;
        }
        switch (s.trigger()) {
            case EVENT -> {
                return s.id().equals(eventScene(w.eventText()));
            }
            case DISASTER -> {
                return s.id().equals(disasterScene(nearestInForce(w.lat(), w.lon(), w.year())));
            }
            case MARKET -> {
                DayRound.Day d = DayRound.get().at(w.lat(), w.lon(), w.year(), w.urban());
                return d != null && d.hasMarket()
                    && DayRound.get().isMarketDay(w.lat(), w.lon(), w.year(), w.dayNumber());
            }
            case CALENDAR -> {
                if ("prayer".equals(s.id())) {
                    if (w.hour() < 0) {
                        return false;           // we are not told the hour, so we decline
                    }
                    return prayerHour(DayRound.get().prayers(w.faith()), w.hour());
                }
                if ("festival".equals(s.id())) {
                    List<String> f = new ArrayList<>();
                    if (w.faith() != null && !w.faith().isEmpty()) {
                        f.add(w.faith());
                    }
                    String feast = CultureAtlas.get().feastOn(w.lat(), w.lon(), w.year(),
                        w.month(), w.day(), f);
                    return feast != null && !feast.isBlank();
                }
                return false;
            }
            case MIGRATION -> {
                return !Migrations.near(w.lat(), w.lon(), w.year()).isEmpty();
            }
            case INCIDENT -> {
                return s.id().equals(incidentScene(w.incident()));
            }
            case LIFE -> {
                return s.id().equals(lifeScene(w));
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Which scene a dated event sets off, or "" for none.
     *
     * <p>The caller hands over the event's own words — its kind, its effect and its name joined
     * up — rather than the register going to look. Step 28's gazetteer carries Minecraft types,
     * and a register about who is standing in the street has no business importing them; whoever
     * already has the event can pass the words.
     */
    public static String eventScene(String eventText) {
        if (eventText == null || eventText.isEmpty()) {
            return "";
        }
        String text = eventText.toLowerCase(Locale.ROOT);
        for (String[] row : EVENT_MATCH) {
            for (int i = 1; i < row.length; i++) {
                if (text.contains(row[i])) {
                    return row[0];
                }
            }
        }
        return "";
    }

    /** The words to hand to {@link #eventScene(String)} for one of step 28's events. */
    public static String wordsOf(String kind, String effect, String name) {
        return (kind == null ? "" : kind) + " " + (effect == null ? "" : effect) + " "
            + (name == null ? "" : name);
    }

    /**
     * The hazard in force here and now that actually bites here, or null.
     *
     * <p>Step 36's pack records hazards over whole regions and whole countries, and a box that
     * covers German New Guinea is not a reason for a street in Glasgow to be empty. So this takes
     * only what reaches far enough to touch this spot: a hazard whose death rate here is under
     * two in the hundred is not a scene, it is news. The Black Death in London in 1348 is a
     * scene; the same plague reading about it from Milan is not.
     */
    public static Disaster nearestInForce(double lat, double lon, int year) {
        Disaster best = null;
        double bestScore = 0.0d;
        for (Disaster d : Disasters.get().all()) {
            if (!d.inForceIn(year) || !d.reaches(lat, lon)) {
                continue;
            }
            double bite = Disasters.get().mortality(d, lat, lon);
            if (bite < 0.02d) {
                continue;
            }
            double score = bite * (1.0d - Math.min(1.0d,
                d.distanceKm(lat, lon) / Math.max(1.0d, d.radiusKm())));
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    /**
     * Which scene an incident sets off, or "" for none.
     *
     * <p>Step 39's three scenes that hang off the incident engine are an election, a protest and a
     * trial. They are the only three that cannot be read off a register that already exists,
     * because an incident is generated rather than recorded — so, as with the events, the caller
     * hands over the name and the register does not go and make one up. A popular reform is
     * carried at a poll; a rebellion, a raid and a crash bring people into the street; an attempt
     * on a life ends in a court. The other sixteen kinds of incident set no scene off at all, and
     * that is deliberate: a good harvest is not a gathering.
     */
    public static String incidentScene(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return "";
        }
        String t = typeName.toLowerCase(Locale.ROOT).trim();
        if (t.contains("popular_reform") || t.contains("election")) {
            return "election";
        }
        if (t.contains("rebellion") || t.contains("economic_crash") || t.contains("raid")
            || t.contains("protest")) {
            return "protest";
        }
        if (t.contains("assassination") || t.contains("trial")) {
            return "trial";
        }
        return "";
    }

    /** Which scene a hazard sets off. */
    public static String disasterScene(Disaster d) {
        if (d == null) {
            return "";
        }
        return switch (d.kind()) {
            case PLAGUE, EPIDEMIC, PANDEMIC -> "plague";
            case FAMINE -> "famine";
            case FLOOD, EARTHQUAKE, VOLCANO, TSUNAMI -> "flight";
            case FIRE -> "fire";
            default -> "";
        };
    }

    /** Is one of this faith's set hours of prayer in this hour of the day? */
    public static boolean prayerHour(DayRound.Prayers p, int hour) {
        if (p == null || hour < 0) {
            return false;
        }
        for (String when : p.when()) {
            String low = (when == null ? "" : when).toLowerCase(Locale.ROOT);
            for (String[] row : HOUR_WORDS) {
                if (!low.contains(row[0])) {
                    continue;
                }
                int h = Integer.parseInt(row[1]);
                int gap = Math.abs(h - hour);
                return gap <= 1 || gap >= 23;
            }
        }
        return false;
    }

    /**
     * Which scene the life table puts on here today: a funeral, as often as people die, or a
     * wedding, as often as they marry.
     *
     * <p>Both come off the same number. In a population that is not growing, the people who marry
     * in a year and the people who die in a year are each about one year's worth of births, so
     * out of {@code folk} people with an expectation of life of {@code e0} years:
     *
     * <ul>
     * <li>deaths a day: {@code folk / (e0 * 365)};</li>
     * <li>weddings a day: the same, less the share who never marry. The register uses 85 per
     *     cent, which is the middle of what the parish and census records show for Europe
     *     between 1600 and 1900 and is marked estimated, not sourced.</li>
     * </ul>
     *
     * <p>So a city of forty thousand where a newborn expects forty years buries about three people
     * a day and has a funeral on every one of them, while a village of three hundred buries one
     * person every six weeks and holds a wedding a little less often than that. That difference
     * is the point, and it comes out of the life table rather than out of anybody's sense of what
     * a village is like.
     */
    public static String lifeScene(Where w) {
        LifeTable life = LifeTable.get();
        LifeTable.Mortality m = life.mortalityAt(w.lat(), w.lon(), w.year(), w.urban());
        if (m == null) {
            return "";
        }
        double e0 = Math.max(1.0d, life.expectancy(m, 0.0d));
        double die = w.crowd() / (e0 * 365.25d);
        double wed = die * 0.85d;
        double r = uniform(w.dayNumber() * 31L + (long) w.year() * 7919L, 5L);
        if (r < wed) {
            return "wedding";
        }
        return r < wed + die ? "funeral" : "";
    }

    private static double uniform(long salt, long tweak) {
        long x = salt * 0x9E3779B97F4A7C15L ^ tweak * 0xC2B2AE356165667CL;
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }

    // ================================================================== who is in it

    /** How many of this scene's parts this place can actually fill. */
    public static int available(Scene s, Where w) {
        if (s == null || w == null) {
            return 0;
        }
        Jobs jobs = Jobs.get();
        int n = 0;
        for (Part p : s.cast()) {
            n += Math.min(p.count(), jobs.shareOf(w.lat(), w.lon(), w.year(), w.urban(),
                p.sector()) * w.crowd() / 100);
        }
        return n;
    }

    /**
     * Cast this scene here. Parts the place has nobody for are left empty, and the scene says so.
     */
    public static List<Person> cast(Scene s, Where w) {
        List<Person> out = new ArrayList<>();
        if (s == null || w == null) {
            return out;
        }
        LifeTable life = LifeTable.get();
        LifeTable.Mortality m = life.mortalityAt(w.lat(), w.lon(), w.year(), w.urban());
        Jobs jobs = Jobs.get();
        int n = 0;
        for (Part p : s.cast()) {
            int have = jobs.shareOf(w.lat(), w.lon(), w.year(), w.urban(), p.sector())
                * w.crowd() / 100;
            for (int i = 0; i < p.count(); i++) {
                long who = salt(w) + 101L * (long) (n + 1);
                String work = i < have
                    ? jobs.pickIn(who, w.lat(), w.lon(), w.year(), w.urban(), p.sector()) : null;
                int age = life.deathAge(m, who * 3L + 17L);
                int kin = 1;
                Households.Home home = Households.get().home(who + 5L, w.year(), w.lat(),
                    w.lon(), w.urban());
                if (home != null) {
                    kin = home.members().size();
                }
                out.add(new Person(p.sector(), work, Jobs.plain(work == null ? "" : work),
                    Math.max(5, age), kin));
                n++;
            }
        }
        return out;
    }

    private static long salt(Where w) {
        return (long) (w.lat() * 1000.0d) * 7919L + (long) (w.lon() * 1000.0d) * 104729L
            + (long) w.year() * 31L + w.dayNumber();
    }

    /** The largest scene on here now, cast. Null when nothing is on. */
    public static Occasion occasion(Where w) {
        List<Scene> on = on(w);
        if (on.isEmpty()) {
            return null;
        }
        Scene best = on.get(0);
        for (Scene s : on) {
            if (s.size() > best.size()) {
                best = s;
            }
        }
        return make(best, w);
    }

    /** This scene, here, now, cast. */
    public static Occasion make(Scene s, Where w) {
        if (s == null || w == null) {
            return null;
        }
        List<Person> people = cast(s, w);
        double absence = absence(s, w);
        return new Occasion(s, people, why(s, w), absence, buildLine(s, people, absence, w));
    }

    /** How much of the crowd is not here, for the scenes about people not being there. */
    public static double absence(Scene s, Where w) {
        if (s == null || w == null || !"suffering".equals(s.group())) {
            return 0.0d;
        }
        Disaster d = nearestInForce(w.lat(), w.lon(), w.year());
        if (d == null) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(0.95d, Disasters.get().mortality(d, w.lat(), w.lon())));
    }

    /** Why this scene is on, in the register's own words. */
    public static String why(Scene s, Where w) {
        switch (s.trigger()) {
            case EVENT -> {
                return w.eventText() == null ? "" : w.eventText().trim();
            }
            case DISASTER -> {
                Disaster d = nearestInForce(w.lat(), w.lon(), w.year());
                return d == null ? "" : d.name() + ", " + d.yearLabel();
            }
            case MIGRATION -> {
                return firstSentence(Migrations.line(w.lat(), w.lon(), w.year()));
            }
            case INCIDENT -> {
                return w.incident() == null ? ""
                    : w.incident().replace('_', ' ').toLowerCase(Locale.ROOT);
            }
            case CALENDAR -> {
                if ("festival".equals(s.id())) {
                    List<String> f = new ArrayList<>();
                    if (w.faith() != null && !w.faith().isEmpty()) {
                        f.add(w.faith());
                    }
                    return CultureAtlas.get().feastOn(w.lat(), w.lon(), w.year(), w.month(),
                        w.day(), f);
                }
                return "";
            }
            default -> {
                return "";
            }
        }
    }

    private static String buildLine(Scene s, List<Person> people, double absence, Where w) {
        StringBuilder sb = new StringBuilder();
        String low = s.name().toLowerCase(Locale.ROOT);
        boolean article = low.startsWith("a ") || low.startsWith("an ");
        sb.append(article ? "There is " : "There are ").append(s.name()).append(" here");
        String why = why(s, w);
        if (!why.isEmpty()) {
            sb.append(" - ").append(why);
        }
        sb.append(": ").append(s.doing()).append('.');
        if (absence >= 0.05d) {
            sb.append(String.format(Locale.ROOT,
                " The street is about %.0f%% emptier than it should be.", absence * 100.0d));
        }
        Map<String, Integer> byWord = new LinkedHashMap<>();
        int filled = 0;
        for (Person p : people) {
            if (p.work() == null) {
                continue;
            }
            filled++;
            byWord.merge(p.word(), 1, Integer::sum);
        }
        if (filled == 0) {
            sb.append(" There is nobody here whose business this is.");
        } else {
            List<String> parts = new ArrayList<>();
            byWord.forEach((word, k) -> parts.add(plural(word, k)));
            sb.append(" In it: ").append(String.join(", ", parts)).append('.');
            int short1 = s.size() - filled;
            if (short1 > 0) {
                sb.append(" The other ").append(short1)
                  .append(short1 == 1 ? " part is" : " parts are")
                  .append(" unfilled: nobody here does that work.");
            }
        }
        return sb.toString();
    }

    /** The first sentence of a line, so a scene reason does not run to a paragraph. */
    private static String firstSentence(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int dot = text.indexOf(". ");
        if (dot > 20 && dot < 200) {
            return text.substring(0, dot + 1);
        }
        return text.length() <= 200 ? text : text.substring(0, 197).trim() + "...";
    }

    /**
     * The part a person plays, in the words the villager role tables already know.
     *
     * <p>The register casts in work sectors because that is what step 38's {@code Jobs} draws
     * from, but the people standing in the street are named and titled by role. So: the priest is
     * the clergy, the scribe is the official, the artisan is the craft, and where two sectors
     * share a role - herding with farming, the miner with the craftsman - they share it, because
     * that is how the name tables work and not because the register cannot tell them apart.
     */
    public static String roleOf(String sector) {
        return switch (sector == null ? "" : sector) {
            case "farming", "herding", "foraging" -> "farmer";
            case "craft", "miner" -> "artisan";
            case "trade", "transport" -> "merchant";
            case "soldier" -> "soldier";
            case "clergy" -> "priest";
            case "official" -> "scribe";
            case "scholar" -> "scholar";
            default -> "citizen";
        };
    }

    /** "a priest", "3 priests", "2 craftsmen". */
    private static String plural(String word, int n) {
        if (n <= 1) {
            return "a " + word;
        }
        if (word.endsWith("man") && !word.endsWith("human")) {
            return n + " " + word.substring(0, word.length() - 3) + "men";
        }
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("ch")
            || word.endsWith("sh")) {
            return n + " " + word + "es";
        }
        if (word.endsWith("y")) {
            return n + " " + word.substring(0, word.length() - 1) + "ies";
        }
        return n + " " + word + "s";
    }

    // ================================================================== what to say

    /** One sentence: the scene on here now, or that there is not one. */
    public static String line(Where w) {
        Occasion o = occasion(w);
        if (o == null) {
            return "Nothing is going on here today.";
        }
        return o.line();
    }

    /** A short card for a tooltip. */
    public static String card(Where w) {
        List<Scene> on = on(w);
        if (on.isEmpty()) {
            return "scene: none today";
        }
        StringBuilder sb = new StringBuilder("scene: ").append(on.get(0).name());
        for (int i = 1; i < on.size(); i++) {
            sb.append(", ").append(on.get(i).name());
        }
        Occasion o = occasion(w);
        if (o != null) {
            sb.append("\n  ground: ").append(o.scene().where())
              .append(" | cast ").append(o.filled()).append('/').append(o.scene().size());
            if (o.absence() >= 0.05d) {
                sb.append(String.format(Locale.ROOT, " | %.0f%% emptier",
                    o.absence() * 100.0d));
            }
            if (!o.why().isEmpty()) {
                sb.append("\n  because: ").append(firstSentence(o.why()));
            }
        }
        return sb.toString();
    }

    private static JsonObject read() {
        try (InputStream in = Scenes.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader r = new InputStreamReader(
                    new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    private static final String RESOURCE =
        "/data/eternalengine/eternal/history/scenes.json.gz";

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}
