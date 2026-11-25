package com.eternalengine.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A name for a villager, chosen by where and when that villager lives.
 *
 * <h3>What this replaces</h3>
 * The old path resolved a place to one of eleven culture pools and drew a name without weights:
 * Florence and Lisbon drew from the same "mediterranean" list, and a Deccan village drew Sinhala
 * names. This resolver reads the place- and era-true register built in R3 (2026-11-07): every
 * name is counted at the place its bearer was born and in the years the bearer lived.
 *
 * <h3>The pool is built, never chosen</h3>
 * Two circles widen around the person until there is enough to name a village believably:
 * <ul>
 *   <li><b>place</b> — the language spoken where they stand (Glottolog's nearest attested point,
 *       subject to its known span), then everything within 250, 500, 1000, 2000 km; a neighbour
 *       counts less the further off it is.</li>
 *   <li><b>time</b> — the era they stand in, then the eras either side, fading as a Gaussian:
 *       ten years out a name still counts for 95%, a hundred years out for under 1%.</li>
 * </ul>
 * If both circles are exhausted and the pool is still thin, the culture bucket answers and the
 * report says "borrowed". Nothing is invented and no list is padded.
 *
 * <h3>Rules the register enforces</h3>
 * <ul>
 *   <li>No single name holds more than 6% of its pool; a name fainter than 2% of the strongest
 *       is dropped as noise left over from widening (the floor relaxes to 0.5%, then 0.1%, where
 *       a place is thin, and the report says which floor was used).</li>
 *   <li>A village is named without replacement: {@link #villageNames} never repeats a full name.</li>
 *   <li>Women's names: where the record's local women are thin, the checked register borrows —
 *       and only names that pass the sex lexicon, the macroarea gate and the record's time check
 *       (precomputed in {@code fill_names.json}). A place with no borrow eligible says so.</li>
 *   <li>Titles used as names (Rani, Landgravine) and a community thread's sex tags were removed
 *       from the register at build time; a name the dated sources disagree about is used for
 *       neither sex.</li>
 * </ul>
 *
 * <p>A faithful port of {@code R3/names/place_names.py} — same constants, same order of
 * operations — with the sex lexicon and the borrow precomputed into the shipped data so the game
 * carries no research pipeline.
 */
public final class PlaceNameResolver {

    /** Where the shipped data lives inside the jar. */
    private static final String DIR = "/data/eternalengine/eternal/names/";

    // ==================== constants (identical to the R3 research code) ====================

    private static final double[] RADII = {250.0, 500.0, 1000.0, 2000.0};
    private static final double HOME_WEIGHT = 6.0;   // the home language counts for this many neighbours
    private static final double POWER = 1.5;         // how fast a neighbour's weight falls with distance
    private static final double TIME_SCALE = 45.0;   // years: a name fades to 29% fifty years out of its era
    private static final double TIME_CUT = 0.02;     // a band fainter than this is not worth adding
    private static final double SHARE_CAP = 0.06;    // no single name may hold more than this share of its pool
    private static final int TARGET_CELLS = 400;     // a pool smaller than this many full names is too thin to trust
    private static final double[] FLOORS = {0.02, 0.005, 0.001};

    // ==================== data ====================

    private static final class Cell {
        final String bandKey;
        final Map<String, Double> surnames = new LinkedHashMap<>();
        final Map<String, Double> given = new LinkedHashMap<>();

        Cell(String bandKey) {
            this.bandKey = bandKey;
        }
    }

    private static final class Point {
        final String name;
        final double lat, lon;
        final Integer y0, y1;
        final String macro;
        final String culture;

        Point(String name, double lat, double lon, Integer y0, Integer y1, String macro, String culture) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.y0 = y0;
            this.y1 = y1;
            this.macro = macro;
            this.culture = culture;
        }
    }

    private static final class Resolution {
        final Point point;
        final double km;

        Resolution(Point point, double km) {
            this.point = point;
            this.km = km;
        }
    }

    /** The answer the game can show: why this name was chosen. */
    public static final class Report {
        public final String language;
        public final double km;
        public final int bandStart, bandEnd;
        public final String rung;
        public final String sexNote;
        public final double floor;

        Report(String language, double km, int bandStart, int bandEnd,
               String rung, String sexNote, double floor) {
            this.language = language;
            this.km = km;
            this.bandStart = bandStart;
            this.bandEnd = bandEnd;
            this.rung = rung;
            this.sexNote = sexNote;
            this.floor = floor;
        }

        /** The "why is this villager called this" answer, in plain words. */
        public String describe() {
            return "language " + language + " (" + (int) Math.round(km) + " km to the nearest attested "
                    + "speaker), era " + bandStart + "-" + bandEnd + ", pool from " + rung
                    + ", sex: " + sexNote;
        }
    }

    private final Map<String, Map<String, Cell>> languages = new LinkedHashMap<>();
    private final Map<String, Map<String, Cell>> cultures = new LinkedHashMap<>();
    private int[][] bandEdges = new int[0][2];
    private final List<Point> points = new ArrayList<>();
    private final Map<String, List<Point>> langPoints = new LinkedHashMap<>();
    private final Set<String> maleNames = new HashSet<>();
    private final Set<String> femaleNames = new HashSet<>();
    private final Set<String> contestedNames = new HashSet<>();
    private final Map<String, List<String>> fillNames = new LinkedHashMap<>();
    // every name in the checked borrow register is a woman's name: if the lexicon has a hole
    // (a woman's name it never marked), the register still keeps it off a man's pool
    private final Set<String> filledFemale = new HashSet<>();

    private Report lastReport = null;
    private boolean loaded = false;

    public boolean isLoaded() {
        return loaded;
    }

    public Report lastReport() {
        return lastReport;
    }

    // ==================== loading ====================

    /**
     * Read the four data files from a world folder, falling back to the copies in the jar.
     *
     * @return the number of languages loaded, 0 when neither copy could be read
     */
    public int loadFrom(Path dir) {
        JsonObject pools = readObject(dir, "place_names.json");
        if (pools == null) {
            return 0;
        }
        int n = 0;
        if (pools.has("band_edges") && pools.get("band_edges").isJsonArray()) {
            JsonArray arr = pools.getAsJsonArray("band_edges");
            bandEdges = new int[arr.size()][2];
            for (int i = 0; i < arr.size(); i++) {
                JsonArray b = arr.get(i).getAsJsonArray();
                bandEdges[i][0] = b.get(0).getAsInt();
                bandEdges[i][1] = b.get(1).getAsInt();
            }
        }
        n += readPools(pools.has("language") && pools.get("language").isJsonObject()
                ? pools.getAsJsonObject("language") : null, languages);
        readPools(pools.has("culture") && pools.get("culture").isJsonObject()
                ? pools.getAsJsonObject("culture") : null, cultures);

        JsonObject lm = readObject(dir, "language_map.json");
        if (lm != null && lm.has("points") && lm.get("points").isJsonArray()) {
            for (JsonElement e : lm.getAsJsonArray("points")) {
                // one malformed point is skipped, never a failed startup
                try {
                    JsonObject o = e.getAsJsonObject();
                    Point p = new Point(
                            o.get("n").getAsString(),
                            o.get("lat").getAsDouble(),
                            o.get("lon").getAsDouble(),
                            optInt(o, "y0"),
                            optInt(o, "y1"),
                            optStr(o, "m"),
                            optStr(o, "cu"));
                    points.add(p);
                    langPoints.computeIfAbsent(p.name, k -> new ArrayList<>()).add(p);
                } catch (Exception ignored) {
                }
            }
        }

        JsonObject lex = readObject(dir, "sex_lexicon.json");
        if (lex != null) {
            fillSet(lex, "male", maleNames);
            fillSet(lex, "female", femaleNames);
            fillSet(lex, "contested", contestedNames);
        }

        JsonObject fill = readObject(dir, "fill_names.json");
        if (fill != null && fill.has("entries") && fill.get("entries").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : fill.getAsJsonObject("entries").entrySet()) {
                try {
                    List<String> names = new ArrayList<>();
                    for (JsonElement x : e.getValue().getAsJsonArray()) {
                        names.add(x.getAsString());
                        filledFemale.add(x.getAsString().toLowerCase(Locale.ROOT));
                    }
                    fillNames.put(e.getKey(), names);
                } catch (Exception ignored) {
                }
            }
        }

        loaded = n > 0 && !points.isEmpty();
        if (!loaded) {
            com.eternalengine.EternalEngineMod.getLogger().warn(
                    "[PlaceNameResolver] Name data incomplete ({} languages, {} points) — place-true naming disabled",
                    n, points.size());
        } else {
            com.eternalengine.EternalEngineMod.getLogger().info(
                    "[PlaceNameResolver] Loaded {} languages, {} points, {} male / {} female given names, {} fill keys",
                    n, points.size(), maleNames.size(), femaleNames.size(), fillNames.size());
        }
        return n;
    }

    private static JsonObject readObject(Path dir, String file) {
        JsonObject root = null;
        Path path = dir == null ? null : dir.resolve(file);
        if (path != null && Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                com.eternalengine.EternalEngineMod.getLogger().warn(
                        "[PlaceNameResolver] World copy of {} unreadable ({}); using the packaged copy",
                        file, e.getMessage());
            }
        }
        if (root == null) {
            try (InputStream in = PlaceNameResolver.class.getResourceAsStream(DIR + file)) {
                if (in == null) {
                    com.eternalengine.EternalEngineMod.getLogger().error(
                            "[PlaceNameResolver] No {} at {} in the jar", file, DIR + file);
                    return null;
                }
                root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                com.eternalengine.EternalEngineMod.getLogger().error(
                        "[PlaceNameResolver] Failed to read the packaged {}: {}", file, e.getMessage());
                return null;
            }
        }
        return root;
    }

    private static int readPools(JsonObject section, Map<String, Map<String, Cell>> out) {
        if (section == null) {
            return 0;
        }
        int n = 0;
        for (Map.Entry<String, JsonElement> le : section.entrySet()) {
            // one malformed language is skipped, never a failed startup
            try {
                JsonObject bands = le.getValue().getAsJsonObject();
                Map<String, Cell> perBand = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> be : bands.entrySet()) {
                    try {
                        JsonObject cell = be.getValue().getAsJsonObject();
                        Cell c = new Cell(be.getKey());
                        fillWeights(cell.getAsJsonObject("s"), c.surnames);
                        fillWeights(cell.getAsJsonObject("g"), c.given);
                        if (!c.surnames.isEmpty() || !c.given.isEmpty()) {
                            perBand.put(be.getKey(), c);
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (!perBand.isEmpty()) {
                    out.put(le.getKey(), perBand);
                    n++;
                }
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    private static void fillWeights(JsonObject o, Map<String, Double> out) {
        if (o == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().getAsDouble());
            } catch (Exception ignored) {
                // one malformed entry does not lose the pool
            }
        }
    }

    private static Integer optInt(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsInt();
    }

    private static String optStr(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }

    private static void fillSet(JsonObject o, String key, Set<String> out) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement e : o.getAsJsonArray(key)) {
            out.add(e.getAsString().toLowerCase(Locale.ROOT));
        }
    }

    // ==================== geography ====================

    private static double kmBetween(double la1, double lo1, double la2, double lo2) {
        double p1 = Math.toRadians(la1), p2 = Math.toRadians(la2);
        double dp = p2 - p1, dl = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * 6371.0 * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private int[] bandFor(int year) {
        for (int[] b : bandEdges) {
            if (b[0] <= year && year < b[1]) {
                return b;
            }
        }
        if (bandEdges.length == 0) {
            return new int[]{-3000, 2100};
        }
        return year >= bandEdges[bandEdges.length - 1][0]
                ? bandEdges[bandEdges.length - 1] : bandEdges[0];
    }

    /**
     * The language people speak at this place in this year.
     *
     * <p>A dated variety covering the year beats a modern neighbour, provided it is not absurdly
     * far (twice the nearest distance, never less than 150 km — Cornish really was spoken in 1666
     * but it is 304 km from London, and English is 176). Otherwise the nearest people alive in
     * that year answer. A lifetime excludes the year when the year is past a known end, or before
     * a known start; an unknown bound is the only thing that cannot be disproven. (This is what
     * keeps extinct Etruscan from answering for Rome in 400.) Constructed auxiliary languages are
     * not in the shipped point list at all.
     */
    public Resolution resolveLanguage(double lat, double lon, int year) {
        Point nearest = null, covering = null, alive = null;
        double nearestD = Double.MAX_VALUE, coveringD = Double.MAX_VALUE, aliveD = Double.MAX_VALUE;
        for (Point p : points) {
            double d = kmBetween(lat, lon, p.lat, p.lon);
            if (d < nearestD) {
                nearestD = d;
                nearest = p;
            }
            if (p.y0 != null && p.y1 != null && p.y0 <= year && year <= p.y1 && d < coveringD) {
                coveringD = d;
                covering = p;
            }
            if ((p.y0 == null || year >= p.y0) && (p.y1 == null || year <= p.y1) && d < aliveD) {
                aliveD = d;
                alive = p;
            }
        }
        if (covering != null && coveringD <= Math.max(2 * nearestD, 150.0)) {
            return new Resolution(covering, coveringD);
        }
        if (alive != null) {
            return new Resolution(alive, aliveD);
        }
        return new Resolution(nearest, nearestD);
    }

    // ==================== pool building ====================

    private static double timeWeight(int a, int z, int year) {
        double gap = (a <= year && year < z) ? 0 : Math.min(Math.abs(year - a), Math.abs(year - z));
        return Math.exp(-(gap / TIME_SCALE) * (gap / TIME_SCALE));
    }

    private static void merge(Map<String, Double> target, Map<String, Double> src, double w) {
        if (src == null || src.isEmpty()) {
            return;
        }
        double sum = 0;
        for (double v : src.values()) {
            sum += v;
        }
        // the neighbour's size counts for the square root of itself: raw counts let a big
        // neighbour swamp, full normalisation let a five-name settlement 1,500 km away shout
        // louder per name than the home language
        double scale = Math.max(1.0, Math.sqrt(sum));
        for (Map.Entry<String, Double> e : src.entrySet()) {
            target.merge(e.getKey(), w * (e.getValue() / scale), Double::sum);
        }
    }

    private static double mergeTimed(Map<String, Double> sur, Map<String, Double> giv,
                                     Map<String, Cell> bands, int year, double w) {
        if (bands == null) {
            return 0.0;
        }
        double used = 0.0;
        for (Cell cell : bands.values()) {
            // band key "a:b"
            String key = cell.bandKey;
            int sep = key.indexOf(':');
            int a = Integer.parseInt(key.substring(0, sep));
            int z = Integer.parseInt(key.substring(sep + 1));
            double tw = timeWeight(a, z, year);
            if (tw < TIME_CUT) {
                continue;
            }
            if (tw > used) {
                used = tw;
            }
            merge(sur, cell.surnames, w * tw);
            merge(giv, cell.given, w * tw);
        }
        return used;
    }

    private static Map<String, Double> prune(Map<String, Double> pool, double floor) {
        if (pool.isEmpty()) {
            return pool;
        }
        double top = 0;
        for (double v : pool.values()) {
            if (v > top) {
                top = v;
            }
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : pool.entrySet()) {
            if (e.getValue() >= floor * top) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static Map<String, Double> cap(Map<String, Double> pool, double share) {
        if (pool.isEmpty()) {
            return pool;
        }
        double total = 0;
        for (double v : pool.values()) {
            total += v;
        }
        if (total <= 0) {
            return pool;
        }
        double ceiling = total * share;
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : pool.entrySet()) {
            out.put(e.getKey(), Math.min(e.getValue(), ceiling));
        }
        return out;
    }

    private static final class SexRes {
        final Map<String, Double> names;
        final String note;

        SexRes(Map<String, Double> names, String note) {
            this.names = names;
            this.note = note;
        }
    }

    private SexRes sexFilter(Map<String, Double> giv, String sex, String culture,
                             int[] band, String macro) {
        if (!"m".equals(sex) && !"f".equals(sex)) {
            return new SexRes(giv, "not split");
        }
        if ("m".equals(sex)) {
            // The record is overwhelmingly male, so a name it does not mark as a woman's is kept:
            // the male pool is "male or unmarked", which is the safe reading. Known women's names
            // and contested names come out, so Amrita cannot become a man and Anne — male in one
            // list, female in another — becomes neither. The checked borrow register's names are
            // always women's, so they come out as well: a name the game hands to a woman must
            // never end up on a man, even when the lexicon has a hole for it.
            Map<String, Double> out = new LinkedHashMap<>();
            for (Map.Entry<String, Double> e : giv.entrySet()) {
                String k = e.getKey().toLowerCase(Locale.ROOT);
                if (!femaleNames.contains(k) && !contestedNames.contains(k)
                        && !filledFemale.contains(k)) {
                    out.put(e.getKey(), e.getValue());
                }
            }
            if ("mediterranean".equals(culture)) {
                // an unmarked name ending in -a/-e is almost always a woman's (Alethea, Adora)
                Iterator<Map.Entry<String, Double>> it = out.entrySet().iterator();
                while (it.hasNext()) {
                    String k = it.next().getKey();
                    char last = Character.toLowerCase(k.charAt(k.length() - 1));
                    boolean drop = last == 'a' || last == 'e';
                    if (!drop && k.length() >= 3) {
                        String tail = k.substring(k.length() - 3).toLowerCase(Locale.ROOT);
                        drop = tail.equals("ina");
                    }
                    if (drop) {
                        it.remove();
                    }
                }
            }
            return out.isEmpty() ? new SexRes(giv, "not split") : new SexRes(out, "from the record");
        }
        // women: the record's women are 8.8% of its weight and thin at most places
        Map<String, Double> out = new LinkedHashMap<>();
        double localWeight = 0;
        for (Map.Entry<String, Double> e : giv.entrySet()) {
            if (femaleNames.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                out.put(e.getKey(), e.getValue());
                localWeight += e.getValue();
            }
        }
        double total = 0;
        for (double v : giv.values()) {
            total += v;
        }
        if (out.size() >= 25 && localWeight >= 0.30 * total) {
            return new SexRes(out, "from the record");
        }
        List<String> fill = fillNames.get(culture + "|" + band[0] + "|" + band[1] + "|" + macro);
        if (fill == null || fill.isEmpty()) {
            return new SexRes(out, "thin, no fill available");
        }
        Map<String, Double> merged = new LinkedHashMap<>();
        for (String n : fill) {
            merged.put(n, 0.35);
        }
        // the local women, where the record has any, lead at 4x
        for (Map.Entry<String, Double> e : out.entrySet()) {
            merged.merge(e.getKey(), e.getValue() * 4.0, Math::max);
        }
        String note = out.isEmpty()
                ? "borrowed " + fill.size() + " from the checked register"
                : "local women where the record has them, " + fill.size() + " borrowed from the checked register";
        return new SexRes(merged, note);
    }

    /**
     * The weighted surname and given-name pools for a person standing at a place in a year.
     *
     * @param sex "m", "f", or null for an unsplit pool
     */
    public Pools poolFor(double lat, double lon, int year, String sex) {
        Resolution r = resolveLanguage(lat, lon, year);
        Point p = r.point;
        int[] band = bandFor(year);
        Map<String, Double> sur = new LinkedHashMap<>();
        Map<String, Double> giv = new LinkedHashMap<>();
        double homeW = mergeTimed(sur, giv, languages.get(p.name), year, HOME_WEIGHT);
        String rung = null;
        if (sur.size() * giv.size() >= TARGET_CELLS && homeW > 0.5) {
            rung = "language";
        }
        if (rung == null) {
            for (double radius : RADII) {
                for (Map.Entry<String, List<Point>> e : langPoints.entrySet()) {
                    if (e.getKey().equals(p.name)) {
                        continue;
                    }
                    double d = Double.MAX_VALUE;
                    for (Point q : e.getValue()) {
                        double dq = kmBetween(lat, lon, q.lat, q.lon);
                        if (dq < d) {
                            d = dq;
                        }
                    }
                    if (d > radius) {
                        continue;
                    }
                    double w = 1.0 / (1.0 + Math.pow(d / 100.0, POWER));
                    mergeTimed(sur, giv, languages.get(e.getKey()), year, w);
                }
                if (sur.size() * giv.size() >= TARGET_CELLS) {
                    rung = "language + neighbours within " + (int) radius + " km";
                    break;
                }
            }
        }
        if (rung == null) {
            mergeTimed(sur, giv, cultures.get(p.culture), year, 1.0);
            rung = "culture (borrowed)";
        }
        SexRes sr = sexFilter(giv, sex, p.culture, band, p.macro);
        Map<String, Double> giv2 = sr.names;

        // Pruning is right in a rich pool and wrong in a thin place: at Timbuktu the 2% floor
        // left fifteen men's names, fewer than a village holds. The floor relaxes until the pool
        // can name a village, and the report says which floor was used.
        double floor = FLOORS[FLOORS.length - 1];
        Map<String, Double> ps = null, pg = null;
        for (double f : FLOORS) {
            floor = f;
            ps = cap(prune(sur, f), SHARE_CAP);
            pg = cap(prune(giv2, f), SHARE_CAP);
            if (ps.size() * pg.size() >= TARGET_CELLS) {
                break;
            }
        }
        lastReport = new Report(p.name, r.km, band[0], band[1], rung, sr.note, floor);
        return new Pools(ps, pg, lastReport);
    }

    /** The weighted pools and the report that goes with them. */
    public static final class Pools {
        public final Map<String, Double> surnames;
        public final Map<String, Double> given;
        public final Report report;

        Pools(Map<String, Double> surnames, Map<String, Double> given, Report report) {
            this.surnames = surnames;
            this.given = given;
            this.report = report;
        }

        public int distinctSpace() {
            return surnames.size() * given.size();
        }
    }

    // ==================== drawing ====================

    private static String weighted(Map<String, Double> names, Random r) {
        double total = 0;
        for (double v : names.values()) {
            total += v;
        }
        double x = r.nextDouble() * total;
        for (Map.Entry<String, Double> e : names.entrySet()) {
            x -= e.getValue();
            if (x <= 0) {
                return e.getKey();
            }
        }
        return names.keySet().iterator().next();
    }

    /**
     * One villager's name, or null when the resolver has no answer for this place (the caller
     * falls back to the old culture pools).
     *
     * @param used names already spoken in this village, to avoid repeating a full name; may be null
     */
    public String nameFor(double lat, double lon, int year, String sex, Random rng, Set<String> used) {
        if (!loaded) {
            return null;
        }
        Pools pool = poolFor(lat, lon, year, sex);
        if (pool.surnames.isEmpty() || pool.given.isEmpty()) {
            return null;
        }
        Random r = rng == null ? new Random() : rng;
        Map<String, Double> alt = null;
        for (int i = 0; i < 8; i++) {
            String g = weighted(pool.given, r);
            String s = weighted(pool.surnames, r);
            if (s.equalsIgnoreCase(g) && pool.surnames.size() > 1) {
                if (alt == null) {
                    alt = new LinkedHashMap<>();
                    for (Map.Entry<String, Double> e : pool.surnames.entrySet()) {
                        if (!e.getKey().equalsIgnoreCase(g)) {
                            alt.put(e.getKey(), e.getValue());
                        }
                    }
                }
                s = weighted(alt, r);
            }
            String name = g + " " + s;
            if (used == null || used.isEmpty() || !used.contains(name)) {
                return name;
            }
        }
        // the space ran out before the avoidance succeeded; report rather than invent
        return null;
    }

    /**
     * Names for a whole village at once, never repeating one. Weighting alone cannot stop
     * duplicates — forty draws from five hundred surnames collide however the weights are set —
     * so a village is drawn without replacement. If the space runs out, the list is shorter and
     * {@code reused} in the report tells how many could not be named distinct.
     */
    public List<String> villageNames(double lat, double lon, int year, int count, String sex, Random rng) {
        List<String> out = new ArrayList<>();
        if (!loaded) {
            return out;
        }
        Pools pool = poolFor(lat, lon, year, sex);
        if (pool.surnames.isEmpty() || pool.given.isEmpty()) {
            return out;
        }
        Random r = rng == null ? new Random() : rng;
        Set<String> seen = new HashSet<>();
        int guard = 0;
        while (out.size() < count && guard < count * 400) {
            guard++;
            String name = weighted(pool.given, r) + " " + weighted(pool.surnames, r);
            if (seen.add(name)) {
                out.add(name);
            }
        }
        return out;
    }

}
