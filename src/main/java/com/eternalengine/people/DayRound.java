package com.eternalengine.people;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Step 38 (D5): the day round — work, rest, market, prayer and season, by place and year.
 *
 * <p>{@code VillagerBehaviorSystem} is twenty-two lines and none of them is about behaviour.
 * Nothing in this mod knows when anyone gets up, whether today is market day, whether there is a
 * day on which one does not work, or how many times a day one stops to pray. A villager in Mecca
 * in 900 and a villager in Manchester in 1850 keep the same hours, because nobody has told the
 * mod otherwise.
 *
 * <p><b>The clock is not universal.</b> E. P. Thompson's distinction is the whole of it: a
 * pre-industrial worker is <i>task-oriented</i>, not <i>time-oriented</i>. You work until the job
 * is done or the light goes; the harvest day is longer than the day in February. The clock comes
 * with the factory. The eight-hour day and the two-day weekend are not the default state of the
 * human race; they are about a hundred years old.
 *
 * <p><b>The market is not open every day either</b>, and the cycle is not always seven days long:
 * the <b>pasaran</b> of Java is five, the <b>Triwara</b> market of Bali is three, the
 * <b>nundinae</b> of Rome is eight.
 */
public final class DayRound {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/day_round.json.gz";

    private static final DayRound INSTANCE = new DayRound();

    private final List<Day> rounds = new ArrayList<>();
    private final List<Season> seasonRows = new ArrayList<>();
    private final List<Prayers> prayerRows = new ArrayList<>();
    private boolean loaded;

    private DayRound() {
    }

    /** How the day is spent here. */
    public record Day(String id, String name, double[][] boxes, int from, Integer to, boolean urban,
                      String work, int hours, int rest, String restName, int market,
                      String marketName, String mark, String note, String source) {

        /** Whether the day is set by the task to be done, by the light, or by the clock. */
        public boolean byClock() {
            return "clock".equals(work) || "shift".equals(work);
        }

        /** -1 where a place has no day on which nobody works, which is most of history. */
        public boolean hasRestDay() {
            return rest >= 0;
        }

        /** 0 where there is no market at all — no buying and selling on a fixed day. */
        public boolean hasMarket() {
            return market > 0;
        }

        public boolean estimated() {
            return "estimated".equals(mark);
        }
    }

    /** How a culture divides the year. */
    public record Season(String id, String name, double[][] boxes, int from, Integer to, int count,
                         List<String> names, String mark, String note, String source) {

        /** The season that holds on a given month, 1 to 12. */
        public String forMonth(int month) {
            if (count <= 0 || names.isEmpty()) {
                return "";
            }
            int per = Math.max(1, 12 / count);
            return names.get(Math.min(names.size() - 1, (month - 1) / per));
        }
    }

    /** How many times a day one stops to pray, and when. This belongs to a faith, not a place. */
    public record Prayers(String faith, int count, int keep, List<String> names,
                          List<String> when, String note, String source) {

        /** How many of the set hours a lay person would actually keep today. */
        public int keptToday() {
            return keep > 0 ? keep : count;
        }

        public String at(int i) {
            if (i < 0 || i >= count) {
                return "";
            }
            return names.get(i) + ", " + when.get(i);
        }
    }

    public static synchronized DayRound get() {
        INSTANCE.load();
        return INSTANCE;
    }

    private synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = DayRound.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            try (GZIPInputStream gz = new GZIPInputStream(in);
                 InputStreamReader r = new InputStreamReader(gz, StandardCharsets.UTF_8)) {
                JsonObject doc = JsonParser.parseReader(r).getAsJsonObject();
                for (JsonElement e : doc.getAsJsonArray("rounds")) {
                    JsonObject o = e.getAsJsonObject();
                    rounds.add(new Day(str(o, "id"), str(o, "name"), boxes(o.getAsJsonArray("boxes")),
                            (int) dbl(o, "from"), o.get("to").isJsonNull() ? null
                            : (int) dbl(o, "to"), o.has("urban") && o.get("urban").getAsBoolean(),
                            str(o, "work"), (int) dbl(o, "hours"), (int) dbl(o, "rest"),
                            str(o, "restname"), (int) dbl(o, "market"), str(o, "marketname"),
                            str(o, "mark"), str(o, "note"), str(o, "source")));
                }
                for (JsonElement e : doc.getAsJsonArray("seasons")) {
                    JsonObject o = e.getAsJsonObject();
                    seasonRows.add(new Season(str(o, "id"), str(o, "name"),
                            boxes(o.getAsJsonArray("boxes")), (int) dbl(o, "from"),
                            o.get("to").isJsonNull() ? null : (int) dbl(o, "to"),
                            (int) dbl(o, "count"), list(o.getAsJsonArray("names")),
                            str(o, "mark"), str(o, "note"), str(o, "source")));
                }
                for (JsonElement e : doc.getAsJsonArray("prayers")) {
                    JsonObject o = e.getAsJsonObject();
                    prayerRows.add(new Prayers(str(o, "faith"), (int) dbl(o, "count"),
                            (int) dbl(o, "keep"), list(o.getAsJsonArray("names")),
                            list(o.getAsJsonArray("when")), str(o, "note"), str(o, "source")));
                }
            }
        } catch (Exception e) {
            rounds.clear();
            seasonRows.clear();
            prayerRows.clear();
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static double dbl(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0.0d : e.getAsDouble();
    }

    private static List<String> list(JsonArray arr) {
        List<String> out = new ArrayList<>();
        if (arr != null) {
            for (JsonElement e : arr) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    private static double[][] boxes(JsonArray arr) {
        if (arr == null || arr.isEmpty()) {
            return new double[0][];
        }
        double[][] out = new double[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonArray b = arr.get(i).getAsJsonArray();
            out[i] = new double[]{b.get(0).getAsDouble(), b.get(1).getAsDouble(),
                    b.get(2).getAsDouble(), b.get(3).getAsDouble()};
        }
        return out;
    }

    // ------------------------------------------------------------------ lookup

    public Day at(double lat, double lon, int year) {
        return at(lat, lon, year, false);
    }

    public Day at(double lat, double lon, int year, boolean urban) {
        Day best = null;
        double[] bestKey = null;
        for (Day row : rounds) {
            if (year < row.from() || (row.to() != null && year > row.to())) {
                continue;
            }
            if (row.boxes().length > 0 && !inside(row.boxes(), lat, lon)) {
                continue;
            }
            double[] key = {row.urban() == urban ? 0.0d : 1.0d,
                    row.boxes().length == 0 ? Double.MAX_VALUE : area(row.boxes()), -row.from()};
            if (bestKey == null || less(key, bestKey)) {
                best = row;
                bestKey = key;
            }
        }
        return best;
    }

    /**
     * How the year divides here. Looked up on its own, so that naming the six seasons of India
     * cannot decide what day the market is held there. Where no row holds, the answer comes from
     * the latitude: two seasons in the tropics, four outside them, and the four are named for
     * the hemisphere they are in.
     */
    public Season seasons(double lat, double lon, int year) {
        Season found = null;
        double[] foundKey = null;
        for (Season row : seasonRows) {
            if (year < row.from() || (row.to() != null && year > row.to())) {
                continue;
            }
            if (row.boxes().length > 0 && !inside(row.boxes(), lat, lon)) {
                continue;
            }
            double[] key = {row.boxes().length == 0 ? Double.MAX_VALUE : area(row.boxes()),
                    -row.from()};
            if (foundKey == null || less(key, foundKey)) {
                found = row;
                foundKey = key;
            }
        }
        if (found != null) {
            return found;
        }
        // the latitude's own answer, where no culture has named the year its own way
        if (Math.abs(lat) < 23.5d) {
            return new Season("tropical", "the tropics", new double[0][], -100000, null, 2,
                    List.of("the wet", "the dry"), "estimated",
                    "Two seasons: the rain is either here or it is not.",
                    "this register, from the latitude");
        }
        boolean south = lat < 0.0d;
        return new Season("temperate", south ? "the southern year" : "the northern year",
                new double[0][], -100000, null, 4,
                south ? List.of("autumn", "winter", "spring", "summer")
                        : List.of("spring", "summer", "autumn", "winter"),
                "estimated", "Four seasons.", "this register, from the latitude");
    }

    /** The set hours of a faith: five for Islam, eight for the canonical hours, three for the
     *  sandhyas. Unknown faiths get no hours rather than a guess. */
    public Prayers prayers(String faith) {
        String key = faith == null ? "none" : faith.toLowerCase(Locale.ROOT).replace(" ", "_");
        for (Prayers p : prayerRows) {
            if (p.faith().equals(key)) {
                return p;
            }
        }
        return new Prayers("none", 0, 0, List.of(), List.of(),
                "No set hours. Whatever the seasons and the work demand.", "");
    }

    // ------------------------------------------------------------------ is today the day?

    /** Whether a given day of the week is the one on which nobody works. 0 is Sunday. */
    public boolean isRestDay(double lat, double lon, int year, int weekday) {
        Day d = at(lat, lon, year);
        return d != null && d.rest() == weekday;
    }

    /**
     * Whether an absolute day number is market day. Pass the same running count of days the rest
     * of the mod uses, and the cycle does the rest: every fifth day in Java, every third in
     * Bali, every eighth in Italy under Rome, every seventh in most of the world.
     */
    public boolean isMarketDay(double lat, double lon, int year, long dayNumber) {
        Day d = at(lat, lon, year);
        if (d == null || !d.hasMarket()) {
            return false;
        }
        long n = dayNumber % d.market();
        return n < 0 ? n + d.market() == 0 : n == 0;
    }

    // ------------------------------------------------------------------ words

    /** "The day runs from first light to last — about nine hours of it. Sunday is the day of
     *  rest, and the market comes round every seventh day." */
    public String line(double lat, double lon, int year, boolean urban, String faith) {
        Day d = at(lat, lon, year, urban);
        if (d == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(workLine(d));
        if (d.hasRestDay()) {
            sb.append(' ').append(d.restName()).append(" is the day of rest.");
        } else {
            sb.append(" There is no day of the week on which nobody works.");
        }
        if (d.hasMarket()) {
            sb.append(" The market comes round every ").append(ordinal(d.market()))
              .append(" day");
            String mn = d.marketName();
            sb.append(mn.startsWith("the ") ? ", " + mn : ", the " + mn).append('.');
        } else {
            sb.append(" There is no market here: nothing is bought and sold on a fixed day.");
        }
        Prayers p = prayers(faith);
        if (p.count() > 0) {
            sb.append(" The day is marked by ").append(words(p.count()))
              .append(" hours of prayer (").append(String.join(", ", p.names())).append(')');
            if (p.keptToday() != p.count()) {
                sb.append(", though a parish keeps ").append(words(p.keptToday()));
            }
            sb.append('.');
        }
        Season s = seasons(lat, lon, year);
        if (s.count() > 0) {
            sb.append(" The year has ").append(words(s.count())).append(" seasons: ")
              .append(String.join(", ", s.names())).append('.');
        }
        if (d.estimated()) {
            sb.append(" (estimated: nothing is published about the day here.)");
        }
        return sb.toString();
    }

    private static String workLine(Day d) {
        String how = switch (d.work()) {
            case "task" -> "The work is set by the task, not by the clock: you stop when the job "
                    + "is done, and the day is longer in June than in December.";
            case "daylight" -> "The day runs from first light to last, and the year turns on the "
                    + "light rather than on the hour.";
            case "shift" -> "The work runs in shifts, and the day and the night are the same to "
                    + "the people in it.";
            default -> "The day is set by the clock, not by the work.";
        };
        return how + " About " + d.hours() + " hours of it.";
    }

    private static String words(int n) {
        return switch (n) {
            case 0 -> "no";
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            case 5 -> "five";
            case 6 -> "six";
            case 7 -> "seven";
            case 8 -> "eight";
            default -> String.valueOf(n);
        };
    }

    private static String ordinal(int n) {
        return switch (n) {
            case 1 -> "single";
            case 2 -> "second";
            case 3 -> "third";
            case 4 -> "fourth";
            case 5 -> "fifth";
            case 6 -> "sixth";
            case 7 -> "seventh";
            case 8 -> "eighth";
            case 9 -> "ninth";
            case 10 -> "tenth";
            default -> n + "th";
        };
    }

    private static boolean inside(double[][] boxes, double lat, double lon) {
        for (double[] b : boxes) {
            if (lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3]) {
                return true;
            }
        }
        return false;
    }

    private static double area(double[][] boxes) {
        double a = 0.0d;
        for (double[] b : boxes) {
            a += (b[1] - b[0]) * (b[3] - b[2]);
        }
        return a;
    }

    private static boolean less(double[] a, double[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i];
            }
        }
        return false;
    }

    public int count() {
        return rounds.size();
    }

    public int seasonCount() {
        return seasonRows.size();
    }

    public int faithCount() {
        return prayerRows.size();
    }
}
