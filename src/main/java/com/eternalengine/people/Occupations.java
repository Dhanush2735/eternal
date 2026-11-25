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
 * Step 46 (D6): the trades people actually name, by place and year.
 *
 * <p>{@link Jobs} answers a coarse question -- how many in a hundred here are farmers, how many
 * keep shop -- and for the pre-industrial world that is close to the whole truth, because nearly
 * everybody was in one of twelve boats. It stops being the truth the moment the boats change. It
 * says one person in a hundred here is "a servant" and cannot say whether that person cleans an
 * office in Bengaluru or is a live-in maid in Riyadh; it says "scholar" and cannot say whether
 * that is a village schoolteacher or a biotech technician.
 *
 * <p><b>So this register splits the twelve into the trades.</b> The twelve shares are not
 * touched: whatever {@link Jobs} says the farming share here is, this file divides that same
 * figure among farmers, farm labourers, plantation workers, market gardeners, dairy workers and
 * fishers, in the proportions those trades are known to hold. The total is always the twelve
 * shares, so a sector total taken from {@link Jobs} and a sum of trades taken from here agree to
 * the per cent.
 *
 * <p><b>Two different qualities of number live in here, and they are marked.</b> From 2000 on the
 * sector shares come from the measured tables in {@code jobs.json} (FAO/ILO employment series),
 * and the trade split is applied to them; the row says {@code recorded} or {@code mixed}. From
 * 1900 to 1980 the {@code jobs.json} register has one world row for the entire planet, which
 * would make Norway and Nyasaland identical, so this file overrides the two largest shares --
 * food and making -- with the per-region employment-structure series, and says {@code estimated}
 * on the row. 2100 is {@code projected}: it repeats the 2026 split, because nobody has measured
 * the year 2100, and the trades that do not exist yet (fusion operator, orbital worker) carry
 * their own start year so they do not appear early.
 */
public final class Occupations {

    /**
     * Not {@code occupations.json.gz}: that file already exists in this project and means
     * something else entirely -- the map squares held by an occupying power in the Second World
     * War, read by {@link com.eternalengine.civilization.OccupiedLands}. This register is
     * {@code trades.json.gz}, and the two must not be confused again.
     */
    public static final String RESOURCE =
            "/data/eternalengine/eternal/history/trades.json.gz";

    private static final Occupations INSTANCE = new Occupations();

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Outline> outlines = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private boolean loaded;

    private Occupations() {
    }

    /**
     * One trade: its id, the words for it, and the kind of work it falls under.
     *
     * <p>{@code share} is in a thousand, not in a hundred. A hundred cannot say "three people in
     * a thousand are musicians", and if it cannot say it, the trade does not exist in the game:
     * at whole per cent the musician, the pilot, the politician and the rabbi all round to nobody.
     */
    public record Trade(String id, String name, String sector, int share) {

        /** The same figure in a hundred, to one decimal place. */
        public double perCent() {
            return share / 10.0d;
        }
    }

    /** What people do for a living here and now, trade by trade. */
    public record Row(String id, String name, double[][] boxes, String outline, int from,
                      Integer to, boolean urban, String mark, String sectorRow,
                      Map<String, Integer> sectors, List<Trade> trades, List<String> rare) {

        /** How many in a thousand here follow this trade, or 0 where nobody does. */
        public int share(String tradeId) {
            for (Trade t : trades) {
                if (t.id().equals(tradeId)) {
                    return t.share();
                }
            }
            return 0;
        }

        /** The most common trade here, or "" if the row is empty. */
        public String top() {
            return trades.isEmpty() ? "" : trades.get(0).name();
        }

        /**
         * Trades that belong here but still fall below one in a thousand -- the astronaut, the
         * diamond digger. They are named rather than lost; a scene that wants one specific rare
         * trade can ask for it, and {@link #pick} will not turn one up by itself.
         */
        public List<String> rare() {
            return rare;
        }

        /** Every trade under one of the twelve kinds of work, largest first. */
        public List<Trade> in(String sector) {
            List<Trade> out = new ArrayList<>();
            for (Trade t : trades) {
                if (t.sector().equals(sector)) {
                    out.add(t);
                }
            }
            return out;
        }

        /** The share of one of the twelve kinds of work, in a thousand, summed from its trades. */
        public int sectorShare(String sector) {
            int s = 0;
            for (Trade t : trades) {
                if (t.sector().equals(sector)) {
                    s += t.share();
                }
            }
            return s;
        }

        /** The same figure in a hundred, which is the unit {@link Jobs} uses. */
        public int sectorPct(String sector) {
            return (int) Math.round(sectorShare(sector) / 10.0d);
        }

        public boolean estimated() {
            return "estimated".equals(mark);
        }

        /** The food-getting share here was split by the place-by-place survey, not by a guess. */
        public boolean surveyed() {
            return "surveyed".equals(mark);
        }

        public boolean projected() {
            return "projected".equals(mark);
        }
    }

    /** One survey region's outline: its corners, and the rectangle they fit in for a quick test. */
    public record Outline(String name, double[] box, double[][][] rings) {

        public boolean holds(double lat, double lon) {
            if (lat < box[0] || lat > box[1] || lon < box[2] || lon > box[3]) {
                return false;
            }
            for (double[][] ring : rings) {
                if (inRing(ring, lat, lon)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean inRing(double[][] ring, double lat, double lon) {
            boolean hit = false;
            for (int i = 0; i + 1 < ring.length; i++) {
                double y1 = ring[i][0], x1 = ring[i][1];
                double y2 = ring[i + 1][0], x2 = ring[i + 1][1];
                if ((y1 > lat) != (y2 > lat) && lon < x1 + (lat - y1) / (y2 - y1) * (x2 - x1)) {
                    hit = !hit;
                }
            }
            return hit;
        }
    }

    public static synchronized Occupations get() {
        INSTANCE.load();
        return INSTANCE;
    }

    private synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = Occupations.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            try (GZIPInputStream gz = new GZIPInputStream(in);
                 InputStreamReader r = new InputStreamReader(gz, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                JsonObject ol = root.getAsJsonObject("outlines");
                if (ol != null) {
                    for (Map.Entry<String, JsonElement> e : ol.entrySet()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        JsonArray rings = o.getAsJsonArray("rings");
                        double[][][] rs = new double[rings.size()][][];
                        for (int i = 0; i < rings.size(); i++) {
                            JsonArray ring = rings.get(i).getAsJsonArray();
                            rs[i] = new double[ring.size()][2];
                            for (int j = 0; j < ring.size(); j++) {
                                JsonArray p = ring.get(j).getAsJsonArray();
                                rs[i][j] = new double[]{p.get(0).getAsDouble(),
                                        p.get(1).getAsDouble()};
                            }
                        }
                        JsonArray b = o.getAsJsonArray("box");
                        outlines.put(e.getKey(), new Outline(str(o, "name"),
                                new double[]{b.get(0).getAsDouble(), b.get(1).getAsDouble(),
                                        b.get(2).getAsDouble(), b.get(3).getAsDouble()}, rs));
                    }
                }
                JsonArray occ = root.getAsJsonArray("occupations");
                if (occ != null) {
                    for (JsonElement e : occ) {
                        JsonObject o = e.getAsJsonObject();
                        names.put(str(o, "id"), str(o, "name"));
                    }
                }
                JsonArray arr = root.getAsJsonArray("rows");
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    Map<String, Integer> sectors = new LinkedHashMap<>();
                    JsonObject sc = o.getAsJsonObject("sectors");
                    if (sc != null) {
                        for (String w : Jobs.WORKS) {
                            if (sc.has(w)) {
                                sectors.put(w, sc.get(w).getAsInt());
                            }
                        }
                    }
                    List<Trade> trades = new ArrayList<>();
                    JsonArray oc = o.getAsJsonArray("occupations");
                    if (oc != null) {
                        for (JsonElement t : oc) {
                            JsonObject to = t.getAsJsonObject();
                            trades.add(new Trade(str(to, "id"), str(to, "name"), str(to, "sector"),
                                    (int) dbl(to, "share")));
                        }
                    }
                    List<String> rare = new ArrayList<>();
                    JsonArray ra = o.getAsJsonArray("rare");
                    if (ra != null) {
                        for (JsonElement t : ra) {
                            rare.add(t.getAsString());
                        }
                    }
                    rows.add(new Row(str(o, "id"), str(o, "name"), boxes(o.getAsJsonArray("boxes")),
                            str(o, "outline"), (int) dbl(o, "from"),
                            o.get("to") == null || o.get("to").isJsonNull() ? null
                                    : (int) dbl(o, "to"),
                            o.has("urban") && o.get("urban").getAsBoolean(), str(o, "mark"),
                            str(o, "sector_row"), sectors, trades, rare));
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

    public Row at(double lat, double lon, int year) {
        return at(lat, lon, year, false);
    }

    /**
     * The row for this place and year: the smallest region box that holds it, and the era inside
     * that region.
     *
     * <p>Unlike the twelve-way table this register keeps no world row and no gap. A year before
     * 1900 or after 2100 is answered with the nearest era of the same region rather than with
     * nothing, because a villager in 1860 is a farmer and a villager in 2140 is whatever the
     * projection says, and refusing to name a trade is worse than naming the nearest one.
     */
    public Row at(double lat, double lon, int year, boolean urban) {
        Row best = null;
        double[] bestKey = null;
        Row nearest = null;
        double nearestGap = Double.MAX_VALUE;
        for (Row row : rows) {
            if (!holds(row, lat, lon)) {
                continue;
            }
            double gap = year < row.from() ? row.from() - year
                    : (row.to() == null ? 0.0d : Math.max(0.0d, year - row.to()));
            if (gap < nearestGap) {
                nearest = row;
                nearestGap = gap;
            }
            if (gap > 0.0d) {
                continue;
            }
            double[] key = {row.urban() == urban ? 0.0d : 1.0d,
                    row.boxes().length == 0 ? Double.MAX_VALUE : area(row.boxes()), -row.from()};
            if (bestKey == null || less(key, bestKey)) {
                best = row;
                bestKey = key;
            }
        }
        if (best != null) {
            return best;
        }
        if (nearest != null) {
            return nearest;
        }
        // nothing holds and no year was near: take the closest region box on the globe, and prefer
        // one from the right era over a nearer one from the wrong century
        double bestD = Double.MAX_VALUE;
        for (Row row : rows) {
            double gap = year < row.from() ? row.from() - year
                    : (row.to() == null ? 0.0d : Math.max(0.0d, year - row.to()));
            double d = distance(row.boxes(), lat, lon) + gap * 90.0d;
            if (d < bestD) {
                nearest = row;
                bestD = d;
            }
        }
        return nearest;
    }

    /** A row with an outline is decided by the outline; otherwise by its rectangles. */
    private boolean holds(Row row, double lat, double lon) {
        if (row.outline() != null && !row.outline().isEmpty()) {
            Outline o = outlines.get(row.outline());
            return o != null && o.holds(lat, lon);
        }
        return row.boxes().length == 0 || inside(row.boxes(), lat, lon);
    }

    /** The survey region a point falls in, by name, or "" if it falls in none. */
    public String outlineAt(double lat, double lon) {
        for (Outline o : outlines.values()) {
            if (o.holds(lat, lon)) {
                return o.name();
            }
        }
        return "";
    }

    private static boolean inside(double[][] boxes, double lat, double lon) {
        for (double[] b : boxes) {
            if (lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3]) {
                return true;
            }
        }
        return false;
    }

    /** Rough distance from a point to the nearest corner of a row's rectangles, in degrees. */
    private static double distance(double[][] boxes, double lat, double lon) {
        double best = Double.MAX_VALUE;
        for (double[] b : boxes) {
            double dlat = lat < b[0] ? b[0] - lat : (lat > b[1] ? lat - b[1] : 0.0d);
            double dlon = lon < b[2] ? b[2] - lon : (lon > b[3] ? lon - b[3] : 0.0d);
            best = Math.min(best, dlat * dlat + dlon * dlon);
        }
        return best;
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

    // ------------------------------------------------------------------ one person's trade

    /** One person's trade here, drawn by share. {@code levels} is the ArchaeoGLOBE reading. */
    public String pick(long salt, double lat, double lon, int year, boolean urban) {
        return pick(salt, lat, lon, year, urban, null);
    }

    public String pick(long salt, double lat, double lon, int year, boolean urban, int[] levels) {
        Row row = at(lat, lon, year, urban);
        if (row == null || row.trades().isEmpty()) {
            return Jobs.plain(Jobs.get().pick(salt, lat, lon, year, urban, levels));
        }
        double total = 0.0d;
        double[] w = new double[row.trades().size()];
        for (int i = 0; i < w.length; i++) {
            Trade t = row.trades().get(i);
            w[i] = t.share() * Jobs.canCarry(t.sector(), levels);
            total += w[i];
        }
        if (total <= 0.0d) {
            return row.top();
        }
        double r = uniform(salt, 11L) * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0.0d) {
                return row.trades().get(i).name();
            }
        }
        return row.top();
    }

    /** A person in one named trade, or {@code null} if nobody here does it. */
    public String pickIn(long salt, double lat, double lon, int year, boolean urban, String tradeId) {
        if (tradeId == null) {
            return pick(salt, lat, lon, year, urban);
        }
        Row row = at(lat, lon, year, urban);
        return row != null && row.share(tradeId) > 0 ? tradeId : null;
    }

    /** How many in a thousand here follow this trade, or 0. */
    public int shareOf(double lat, double lon, int year, boolean urban, String tradeId) {
        Row row = at(lat, lon, year, urban);
        return row == null ? 0 : row.share(tradeId);
    }

    /** How many in a hundred here follow this trade, rounded, or 0. */
    public int sharePctOf(double lat, double lon, int year, boolean urban, String tradeId) {
        return (int) Math.round(shareOf(lat, lon, year, urban, tradeId) / 10.0d);
    }

    /** The trade id behind a name, or the id itself if the name is not known. */
    public static String idOf(String name) {
        for (Row row : INSTANCE.rows) {
            for (Trade t : row.trades()) {
                if (t.name().equalsIgnoreCase(name)) {
                    return t.id();
                }
            }
        }
        return name;
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

    /** "Most people here are farmers: 39 in a hundred. After that, farm labourers at 13." */
    public String line(double lat, double lon, int year, boolean urban) {
        Row row = at(lat, lon, year, urban);
        if (row == null || row.trades().isEmpty()) {
            return "";
        }
        Trade top = row.trades().get(0);
        StringBuilder sb = new StringBuilder();
        if (top.perCent() >= 15.0d) {
            sb.append("Most people here are ").append(plural(top.name())).append(": ")
              .append(inHundred(top.perCent())).append(". ");
        } else {
            sb.append("The commonest trade here is the ").append(top.name()).append(", at ")
              .append(inHundred(top.perCent())).append(". ");
        }
        if (row.trades().size() > 1) {
            sb.append("After that, ");
            for (int i = 1; i < Math.min(4, row.trades().size()); i++) {
                if (i > 1) {
                    sb.append(i == 3 ? " and " : ", ");
                }
                sb.append(plural(row.trades().get(i).name()))
                  .append(" at ").append(inHundred(row.trades().get(i).perCent()));
            }
            sb.append(". ");
        }
        sb.append(String.format(Locale.ROOT, "%d in a hundred raise food, %d make things, "
                        + "%d do everything else.",
                sectorPctSum(row, 1), sectorPctSum(row, 2), sectorPctSum(row, 3)));
        if (row.projected()) {
            sb.append(" (projected: this is the last measured year carried forward.)");
        } else if (row.surveyed()) {
            sb.append(" (from the place-by-place survey of how people here actually lived.)");
        } else if (row.estimated()) {
            sb.append(" (estimated: the shares of food and making here come from the regional "
                    + "employment series, not from a census of this town.)");
        }
        return sb.toString();
    }

    /** "three in a hundred", "one in a thousand". */
    private static String inHundred(double perCent) {
        if (perCent >= 1.0d) {
            long n = Math.round(perCent);
            return n == 1 ? "one in a hundred" : n + " in a hundred";
        }
        long perMille = Math.round(perCent * 10.0d);
        return perMille <= 1 ? "one in a thousand" : perMille + " in a thousand";
    }

    /** The last word of a trade's name, made plural: "farm labourer" -> "farm labourers". */
    private static String plural(String name) {
        String[] w = name.split(" ");
        String last = w[w.length - 1];
        String low = last.toLowerCase(Locale.ROOT);
        if (low.endsWith("man")) {
            last = last.substring(0, last.length() - 3) + "men";
        } else if (low.endsWith("woman")) {
            last = last.substring(0, last.length() - 5) + "women";
        } else if (low.endsWith("child")) {
            last = last + "ren";
        } else if (low.endsWith("s")) {
            last = last;
        } else {
            last = last + "s";
        }
        w[w.length - 1] = last;
        return String.join(" ", w);
    }

    private static int sectorPctSum(Row row, int n) {
        String[] which = switch (n) {
            case 1 -> new String[]{"farming", "herding", "foraging"};
            case 2 -> new String[]{"craft", "miner"};
            default -> new String[]{"trade", "service", "transport", "soldier", "clergy",
                    "official", "scholar"};
        };
        int s = 0;
        for (String w : which) {
            s += row.sectorPct(w);
        }
        return s;
    }

    /** How many rows the register holds. */
    public int count() {
        return rows.size();
    }

    /** How many distinct trades it knows. */
    public int trades() {
        List<String> seen = new ArrayList<>();
        for (Row row : rows) {
            for (Trade t : row.trades()) {
                if (!seen.contains(t.id())) {
                    seen.add(t.id());
                }
            }
        }
        return seen.size();
    }
}
