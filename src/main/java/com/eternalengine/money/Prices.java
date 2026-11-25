package com.eternalengine.money;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * What things cost, measured rather than invented: the Allen-Unger Global Commodity Prices
 * Database (step 44, E4).
 *
 * <p>Allen and Unger gathered 125,588 price observations for 160 commodities in 193 markets
 * between 965 and 1914, and standardised every one of them to grams of silver so that Florence
 * in 1350 and Allahabad in 1861 can be compared. This class reads the pack that
 * {@code worldfill/build_prices.py} writes from it: 773 series (a market's prices for one good
 * over time), 89 goods, 193 markets, and 1,225 shortage years.
 *
 * <p><strong>The index, not the silver, is the number to trust.</strong> Some of the database's
 * own conversions from local currencies into silver are wrong: Tuscany's wheat comes out at
 * twenty-eight times the English price, and Tokyo's at a hundredth of everyone else's in nearly
 * every commodity. A series whose ordinary level is wildly out of line with the same good
 * elsewhere is marked {@link Reading#suspect()}, and its absolute figure should not be quoted.
 * But a constant conversion error cancels out of a ratio, so the index is safe either way:
 * it is the price divided by the median price of the thirty-one years around it, and 1.00 means
 * simply "what this ordinarily costs here".
 *
 * <p>That is what makes shortages readable. The Great Famine of 1315-17 comes out at 2.75 in
 * southern England and then falls back to 0.78 by 1318. The Black Death makes grain cheaper,
 * because there were a third fewer people to buy it. Leipzig's barley in 1622 reads 10.5, which
 * is the Kipper und Wipper inflation, not a harvest.
 *
 * <p>Two honest limits. The database stops in 1914, so the twentieth century is out of reach
 * here; and it is European and Indian, so most of Africa and the Americas have nothing. Where
 * this class has no answer the caller must fall back to something else and say so.
 */
public final class Prices {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/prices.json.gz";

    /** The first and last years the source covers at all. */
    public static final int FIRST_YEAR = 965;
    public static final int LAST_YEAR = 1914;

    /**
     * How far a single town's market reaches. A market placed as a city has radius 0, which
     * means "this point and nowhere else" -- and that loses to the region around it: Florence
     * is 0.13 km from the coordinates given for it, so it scored 0.13, while Tuscany (radius
     * 150 km) swallowed the 30 km to its own centre and scored 0. The region won, and it is the
     * worse answer. A town's prices were the prices of the land around it for a day's walk.
     */
    public static final double CITY_REACH_KM = 25.0;

    /** A market, as the source names it, placed by hand. radiusKm is 0 for a city. */
    public record Market(String id, String place, double lat, double lon, double radiusKm) {

        /** How far this market's prices reach: its own radius, or a town's for a city. */
        public double reachKm() {
            return radiusKm <= 0.0 ? CITY_REACH_KM : radiusKm;
        }

        /** How far this point is outside the market's reach; inside it counts as nothing. */
        public double distanceKm(double inLat, double inLon) {
            return Math.max(0.0, haversine(inLat, inLon, lat, lon) - reachKm());
        }
    }

    /** A commodity, with the unit its prices are counted in. */
    public record Good(String id, String name, String family, String measure) {
    }

    /** One market's price for one good in one year. */
    public record Reading(String market, String place, String good, String measure, int year,
                          double grams, double usual, double index, boolean suspect) {

        /** Is this a dear year for this good here? 1.8 catches famines and misses bad harvests. */
        public boolean isShortage() {
            return index >= 1.8;
        }

        /**
         * "Rye costs about three times its usual price here this year" - or "is cheap just now".
         * The index is used rather than the silver figure because it survives a wrong currency
         * conversion, and never quotes a suspect absolute value.
         */
        public String sentence() {
            if (index >= 3.0) {
                return name() + " costs about " + round(index) + " times its usual price here this year: "
                        + "a famine";
            }
            if (index >= 1.8) {
                return name() + " costs about " + round(index) + " times its usual price here this year: "
                        + "a dear year";
            }
            if (index >= 1.25) {
                return name() + " is dearer than usual here this year, about " + round(index)
                        + " times its ordinary price";
            }
            if (index >= 0.8) {
                return name() + " costs about what it usually does here";
            }
            return name() + " is cheap here just now, about " + round(index) + " times its usual price";
        }

        private String name() {
            return good.isEmpty() ? "This" : Character.toUpperCase(good.charAt(0)) + good.substring(1);
        }

        private static String round(double v) {
            return v >= 10 ? String.format("%.0f", v) : String.format("%.1f", v);
        }
    }

    /** A year in which grain at this market cost far more than in the years around it. */
    public record Shortage(String market, String place, int year, double worst, String good,
                           List<String> goods) {

        /** "In 1316 at Southern England, wheat reached 2.8 times its usual price." */
        public String sentence() {
            return "In " + year + " at " + place + ", " + good + " reached " + String.format("%.1f", worst)
                    + " times its usual price";
        }
    }

    private record Series(int market, int good, int[] years, double[] grams, double usual, double[] index,
                          boolean suspect) {
    }

    private static final Prices INSTANCE = new Prices();

    private boolean loaded;
    private String source = "missing";
    private List<Market> markets = List.of();
    private List<Good> goods = List.of();
    private List<Series> series = List.of();
    private List<Shortage> shortages = List.of();
    private final Map<String, Integer> goodIndex = new HashMap<>();

    private Prices() {
    }

    public static Prices get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** The nearest market to this point, empty if none is within {@code maxKm}. */
    public Optional<Market> nearestMarket(double lat, double lon, double maxKm) {
        ensureLoaded();
        Market best = null;
        double bestD = Double.MAX_VALUE;
        for (Market m : markets) {
            double d = m.distanceKm(lat, lon);
            // A city must beat the region it lies inside. Both Tuscany (a region, radius 150 km)
            // and Florence (a city, radius 0) are "no distance away" from Florence; without this
            // tie-break the region won, and it is the worse answer: its currency conversion is
            // exactly the one marked suspect. Narrower wins.
            boolean better = d < bestD
                    || (d == bestD && best != null && m.reachKm() < best.reachKm());
            if (better) {
                bestD = d;
                best = m;
            }
        }
        if (best == null || bestD > maxKm) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    /** The nearest market within 500 km, the reach a merchant's news and carts might have had. */
    public Optional<Market> marketAt(double lat, double lon) {
        return nearestMarket(lat, lon, 500.0);
    }

    /**
     * The price of one good at one market in one year. The year is matched exactly: these series
     * are full of holes and guessing the nearest one would invent a price for a year nobody
     * recorded. Empty before 965, after 1914, or where this market never priced this good.
     */
    public Optional<Reading> price(String marketId, String goodId, int year) {
        ensureLoaded();
        int mi = -1;
        for (int i = 0; i < markets.size(); i++) {
            if (markets.get(i).id().equals(marketId)) {
                mi = i;
                break;
            }
        }
        Integer gi = goodIndex.get(goodId);
        if (mi < 0 || gi == null) {
            return Optional.empty();
        }
        for (Series s : series) {
            if (s.market() != mi || s.good() != gi) {
                continue;
            }
            int k = -1;
            for (int i = 0; i < s.years().length; i++) {
                if (s.years()[i] == year) {
                    k = i;
                    break;
                }
            }
            if (k < 0) {
                return Optional.empty();
            }
            Market m = markets.get(mi);
            Good g = goods.get(gi);
            return Optional.of(new Reading(m.id(), m.place(), g.name(), g.measure(), year,
                    s.grams()[k], s.usual(), s.index()[k], s.suspect()));
        }
        return Optional.empty();
    }

    /** The price of one good near this point in this year, via the nearest market within 500 km. */
    public Optional<Reading> priceAt(double lat, double lon, String goodId, int year) {
        Optional<Market> m = marketAt(lat, lon);
        return m.isEmpty() ? Optional.empty() : price(m.get().id(), goodId, year);
    }

    /** Every shortage recorded at this market, worst first. */
    public List<Shortage> shortagesAt(String marketId) {
        ensureLoaded();
        List<Shortage> out = new ArrayList<>();
        for (Shortage s : shortages) {
            if (s.market().equals(marketId)) {
                out.add(s);
            }
        }
        out.sort((a, b) -> Double.compare(b.worst(), a.worst()));
        return out;
    }

    /** Every shortage within this many years of a date, anywhere, worst first. */
    public List<Shortage> shortagesAround(int year, int halfWindow) {
        ensureLoaded();
        List<Shortage> out = new ArrayList<>();
        for (Shortage s : shortages) {
            if (Math.abs(s.year() - year) <= halfWindow) {
                out.add(s);
            }
        }
        out.sort((a, b) -> Double.compare(b.worst(), a.worst()));
        return out;
    }

    /** Every good this market ever priced, for the checks and for discovery. */
    public List<Good> goodsAt(String marketId) {
        ensureLoaded();
        int mi = -1;
        for (int i = 0; i < markets.size(); i++) {
            if (markets.get(i).id().equals(marketId)) {
                mi = i;
                break;
            }
        }
        List<Good> out = new ArrayList<>();
        if (mi < 0) {
            return out;
        }
        for (Series s : series) {
            if (s.market() == mi) {
                out.add(goods.get(s.good()));
            }
        }
        return out;
    }

    public List<Market> marketList() {
        ensureLoaded();
        return markets;
    }

    public List<Good> goodList() {
        ensureLoaded();
        return goods;
    }

    public int shortageCount() {
        ensureLoaded();
        return shortages.size();
    }

    public String summary() {
        ensureLoaded();
        return "prices: " + series.size() + " series over " + markets.size() + " markets and "
                + goods.size() + " goods, " + shortages.size() + " shortage years, " + FIRST_YEAR
                + "-" + LAST_YEAR + ", from " + source;
    }

    // ------------------------------------------------------------------ loading

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = Prices.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            JsonObject o;
            try (GZIPInputStream gz = new GZIPInputStream(in);
                 InputStreamReader r = new InputStreamReader(gz, StandardCharsets.UTF_8)) {
                o = JsonParser.parseReader(r).getAsJsonObject();
            }
            source = text(o, "source", "Allen-Unger Global Commodity Prices Database");

            List<Market> ms = new ArrayList<>();
            for (JsonElement e : array(o, "places")) {
                JsonObject p = e.getAsJsonObject();
                ms.add(new Market(text(p, "id", ""), text(p, "place", text(p, "id", "")),
                        dbl(p, "lat"), dbl(p, "lon"), dbl(p, "radiusKm")));
            }
            markets = List.copyOf(ms);

            List<Good> gs = new ArrayList<>();
            for (JsonElement e : array(o, "goods")) {
                JsonObject p = e.getAsJsonObject();
                gs.add(new Good(text(p, "id", ""), text(p, "name", ""), text(p, "family", ""),
                        text(p, "measure", "")));
            }
            goods = List.copyOf(gs);
            for (int i = 0; i < goods.size(); i++) {
                goodIndex.putIfAbsent(goods.get(i).id(), i);
            }

            List<Series> ss = new ArrayList<>();
            for (JsonElement e : array(o, "series")) {
                JsonObject p = e.getAsJsonObject();
                JsonArray ys = p.getAsJsonArray("years");
                JsonArray gr = p.getAsJsonArray("grams");
                JsonArray ix = p.getAsJsonArray("index");
                if (ys == null || gr == null || ix == null || ys.size() != gr.size()
                        || ys.size() != ix.size()) {
                    continue;
                }
                int n = ys.size();
                int[] years = new int[n];
                double[] grams = new double[n];
                double[] index = new double[n];
                for (int i = 0; i < n; i++) {
                    years[i] = ys.get(i).getAsInt();
                    grams[i] = gr.get(i).getAsDouble();
                    index[i] = ix.get(i).getAsDouble();
                }
                ss.add(new Series(p.get("place").getAsInt(), p.get("good").getAsInt(), years, grams,
                        p.has("usual") ? p.get("usual").getAsDouble() : 0.0, index,
                        p.has("suspect") && p.get("suspect").getAsBoolean()));
            }
            series = List.copyOf(ss);

            List<Shortage> sh = new ArrayList<>();
            for (JsonElement e : array(o, "shortages")) {
                JsonObject p = e.getAsJsonObject();
                int pi = p.get("place").getAsInt();
                List<String> gs2 = new ArrayList<>();
                for (JsonElement g : array(p, "goods")) {
                    gs2.add(g.getAsString());
                }
                sh.add(new Shortage(pi >= 0 && pi < markets.size() ? markets.get(pi).id() : "?",
                        pi >= 0 && pi < markets.size() ? markets.get(pi).place() : "?",
                        p.get("year").getAsInt(), p.get("worst").getAsDouble(),
                        text(p, "good", ""), List.copyOf(gs2)));
            }
            shortages = List.copyOf(sh);
        } catch (Exception e) {
            source = "unreadable: " + e;
        }
    }

    private static double haversine(double la1, double lo1, double la2, double lo2) {
        double r = 6371.0;
        double dLat = Math.toRadians(la2 - la1);
        double dLon = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static String text(JsonObject o, String key, String fallback) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static double dbl(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0.0;
    }
}
