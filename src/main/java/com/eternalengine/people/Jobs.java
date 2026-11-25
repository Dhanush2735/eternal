package com.eternalengine.people;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 38 (D4): jobs and workplaces — what people do for a living, by place and year.
 *
 * <p>Before this the mod picked a villager's job with a six-way switch — farmer, artisan,
 * merchant, soldier, scholar, citizen — at random, and identically in the Nile valley in 3000 BC
 * and in Manchester in 1850. A Palaeolithic forager and a Victorian railwayman were one
 * {@code case 1} apart.
 *
 * <p><b>The number that matters is how many people it takes to feed everybody.</b> Everything
 * else follows from it. In a pre-industrial economy it is most of them: the share of the
 * workforce in farming is the single figure that separates 1688 from 2022, and it falls from
 * about <b>56 per cent of the English labour force in 1688</b> (Crafts, and the Cambridge
 * Group's revision of Gregory King's social table) to <b>1.0 per cent in the United Kingdom
 * today</b> and 1.6 in the United States. In Burundi it is still 85.
 *
 * <p>And note what the measured series does not say: the folk figure of "eighty per cent of
 * people were peasants" is too high even for 1688 England. As the Cambridge Group put it,
 * "no-one would now suggest that a figure as high as 80 per cent is remotely plausible" — England
 * was already proto-industrial, and by 1841 making things employed more people than growing food.
 */
public final class Jobs {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/jobs.json.gz";

    /** The twelve kinds of work, in the order they are stored. */
    public static final String[] WORKS = {"farming", "herding", "foraging", "craft", "miner",
            "trade", "service", "transport", "soldier", "clergy", "official", "scholar"};

    /** What each is called in plain words. */
    public static final String[] PLAIN = {"farmer", "herder", "hunter", "craftsman", "miner",
            "merchant", "servant", "carter", "soldier", "priest", "clerk", "scholar"};

    private static final Jobs INSTANCE = new Jobs();

    private final List<Job> rows = new ArrayList<>();
    private boolean loaded;

    private Jobs() {
    }

    /** What share of working people do what, here and now. */
    public record Job(String id, String name, double[][] boxes, int from, Integer to, boolean urban,
                      Map<String, Integer> shares, String mark, String note, String source,
                      boolean pub) {

        /** The share, in per cent, of one kind of work. */
        public int share(String work) {
            return shares.getOrDefault(work, 0);
        }

        /** Primary (getting food), secondary (making) or tertiary (everything else). */
        public int sector(int n) {
            String[] which = switch (n) {
                case 1 -> new String[]{"farming", "herding", "foraging"};
                case 2 -> new String[]{"craft", "miner"};
                default -> new String[]{"trade", "service", "transport", "soldier", "clergy",
                        "official", "scholar"};
            };
            int s = 0;
            for (String w : which) {
                s += share(w);
            }
            return s;
        }

        public boolean estimated() {
            return "estimated".equals(mark);
        }
    }

    public static synchronized Jobs get() {
        INSTANCE.load();
        return INSTANCE;
    }

    private synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = Jobs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            try (GZIPInputStream gz = new GZIPInputStream(in);
                 InputStreamReader r = new InputStreamReader(gz, StandardCharsets.UTF_8)) {
                JsonArray arr = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("jobs");
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    Map<String, Integer> shares = new LinkedHashMap<>();
                    JsonObject sh = o.getAsJsonObject("shares");
                    for (String w : WORKS) {
                        if (sh != null && sh.has(w)) {
                            shares.put(w, sh.get(w).getAsInt());
                        }
                    }
                    rows.add(new Job(str(o, "id"), str(o, "name"), boxes(o.getAsJsonArray("boxes")),
                            (int) dbl(o, "from"), o.get("to").isJsonNull() ? null
                            : (int) dbl(o, "to"),
                            o.has("urban") && o.get("urban").getAsBoolean(), shares,
                            str(o, "mark"), str(o, "note"), str(o, "source"),
                            o.has("pub") && o.get("pub").getAsBoolean()));
                }
            }
        } catch (Exception e) {
            rows.clear();
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

    public Job at(double lat, double lon, int year) {
        return at(lat, lon, year, false);
    }

    /** The most specific row that holds: a town row for a town, then the smallest box, then the
     *  later start. The whole world scores as infinitely large, so a named box always beats it. */
    public Job at(double lat, double lon, int year, boolean urban) {
        Job best = null;
        double[] bestKey = null;
        for (Job row : rows) {
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
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i];
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ one person's work

    /** The word for one kind of work, or the work itself if it is not known. */
    public static String plain(String work) {
        for (int i = 0; i < WORKS.length; i++) {
            if (WORKS[i].equals(work)) {
                return PLAIN[i];
            }
        }
        return work;
    }

    /** One person's job, drawn from the structure for their own place and year. */
    public String pick(long salt, double lat, double lon, int year, boolean urban) {
        return pick(salt, lat, lon, year, urban, null);
    }

    /**
     * A person in one particular line of work, or {@code null} if nobody here does it.
     *
     * <p>Step 39 (D9) needs this because a scene is cast, not drawn: a coronation wants three
     * clergy and two soldiers, and it should get them where they exist and go without them where
     * they do not. Returning null is the honest answer, and the scene register says so out loud
     * -- *"and there is nobody here whose business this is"* -- rather than filling the part with
     * a farmer in a borrowed robe.
     *
     * @return the work asked for, or null when this place and year has no share of it at all
     */
    public String pickIn(long salt, double lat, double lon, int year, boolean urban, String work) {
        if (work == null) {
            return pick(salt, lat, lon, year, urban);
        }
        Job j = at(lat, lon, year, urban);
        if (j == null || j.share(work) <= 0) {
            return null;
        }
        return work;
    }

    /**
     * How many in a hundred here follow this line of work, or 0 where nobody does.
     *
     * <p>Step 39 (D9) needs the number rather than the word, because a scene that wants three
     * priests can have them in a city and cannot in a band of twenty-five foragers, even where
     * the regime gives that band two in the hundred for the shaman. Two in the hundred of
     * twenty-five people is nobody, and the scene should say so.
     */
    public int shareOf(double lat, double lon, int year, boolean urban, String work) {
        if (work == null) {
            return 0;
        }
        Job j = at(lat, lon, year, urban);
        return j == null ? 0 : j.share(work);
    }

    /**
     * One person's job, allowing for what the land here can actually support. {@code levels} is
     * the ArchaeoGLOBE reading for the place — {@code {foraging, farming, pastoral}}, each 0 to 3
     * — or {@code null} to use the row's shares as they stand. A place where nobody farms has no
     * farmers, whatever the row for its era says; that is what "jobs hang off the subsistence
     * map, not off a list" means.
     */
    /**
     * What share of working people do this work here, allowing for what the land can carry.
     * Step 44 part D: the same gate as {@link #pick}, because a count of makers that ignores the
     * subsistence map will happily put a hundred and sixty-five farmers in a Siberia that lives
     * by hunting. {@code levels} is {@code {foraging, farming, pastoral}}, each 0 to 3, or
     * {@code null} for the row's shares as they stand.
     */
    public int shareOf(double lat, double lon, int year, boolean urban, String work, int[] levels) {
        if (work == null) {
            return 0;
        }
        Job j = at(lat, lon, year, urban);
        if (j == null) {
            return 0;
        }
        return (int) Math.round(j.share(work) * (levels == null ? 1.0d : gate(work, levels)));
    }

    public String pick(long salt, double lat, double lon, int year, boolean urban, int[] levels) {
        Job j = at(lat, lon, year, urban);
        if (j == null) {
            return "service";
        }
        double[] w = new double[WORKS.length];
        double total = 0.0d;
        for (int i = 0; i < WORKS.length; i++) {
            w[i] = j.share(WORKS[i]);
            if (levels != null) {
                w[i] *= gate(WORKS[i], levels);
            }
            total += w[i];
        }
        if (total <= 0.0d) {
            return "service";
        }
        double r = uniform(salt, 7L) * total;
        for (int i = 0; i < WORKS.length; i++) {
            r -= w[i];
            if (r <= 0.0d) {
                return WORKS[i];
            }
        }
        return WORKS[WORKS.length - 1];
    }

    /**
     * How much of one kind of work the land here can carry, from the ArchaeoGLOBE level.
     *
     * <p>Step 46 (occupations): exposed so the trade register can gate a trade by the work it
     * belongs to -- a shepherd in a place with no pasture is the same mistake as a farmer there,
     * and the gate should not be written twice.
     */
    public static double canCarry(String work, int[] levels) {
        return levels == null ? 1.0d : gate(work, levels);
    }

    /** How much of one kind of work the land here can carry, from the ArchaeoGLOBE level. */
    private static double gate(String work, int[] levels) {
        int level = switch (work) {
            case "foraging" -> levels.length > 0 ? levels[0] : 3;
            case "farming" -> levels.length > 1 ? levels[1] : 3;
            case "herding" -> levels.length > 2 ? levels[2] : 3;
            default -> 3;
        };
        // a place that does not farm at all still has a few people at it, because the level is a
        // consensus judgement about the whole region, not about the one field
        return switch (level) {
            case 3 -> 1.0d;
            case 2 -> 0.70d;
            case 1 -> 0.30d;
            default -> 0.15d;
        };
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

    // ------------------------------------------------------------------ words

    /** "Most people here farm. One in ten makes things; one in twenty keeps a shop." */
    public String line(double lat, double lon, int year, boolean urban) {
        Job j = at(lat, lon, year, urban);
        if (j == null) {
            return "";
        }
        String top = "";
        int best = -1;
        for (String w : WORKS) {
            if (j.share(w) > best) {
                best = j.share(w);
                top = w;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (best >= 45) {
            sb.append("Most people here ").append(verb(top)).append(". ");
        } else {
            sb.append("The largest single trade here is ").append(plural(top)).append(". ");
        }
        sb.append(String.format(Locale.ROOT, "Roughly %d in a hundred raise food, %d make "
                + "things, and %d do everything else.", j.sector(1), j.sector(2), j.sector(3)));
        if (j.estimated()) {
            sb.append(" (estimated: no occupational table was ever taken here.)");
        }
        return sb.toString();
    }

    private static String verb(String work) {
        return switch (work) {
            case "farming" -> "farm";
            case "herding" -> "herd animals";
            case "foraging" -> "hunt and gather";
            case "craft" -> "make things by hand";
            case "miner" -> "dig";
            case "trade" -> "buy and sell";
            case "service" -> "work in other people's houses";
            case "transport" -> "move goods";
            case "soldier" -> "are soldiers";
            case "clergy" -> "serve the temple";
            case "official" -> "keep records";
            default -> "study";
        };
    }

    private static String plural(String work) {
        String p = plain(work);
        if (p.endsWith("man")) {
            return p.substring(0, p.length() - 3) + "men";
        }
        return p + "s";
    }

    public int count() {
        return rows.size();
    }
}
