package com.eternalengine.money;

import com.eternalengine.civilization.Disasters;
import com.eternalengine.civilization.Disasters.Disaster;
import com.eternalengine.climate.ClimatePeriods;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 44 part E (E5): what happens to goods when something goes wrong, and what people use
 * instead.
 *
 * <p>Part D gave every product the work that makes it and the place it is made in. This is the
 * other half of that decision: a disaster already knows what it does to a building, a harvest and
 * a population — {@link Disasters} carries all three — but until now nothing connected that to
 * what is on the shelf. Nothing turned a failed monsoon into short grain.
 *
 * <p>The four shocks are the four ways a good stops arriving:
 *
 * <ul>
 *   <li><b>field</b> — the land yields less: a failed monsoon, a volcanic winter, a famine.
 *       Read from {@link ClimatePeriods#yieldModifier} and from every disaster's field loss.</li>
 *   <li><b>hand</b> — there are fewer people to make it: plague, pandemic, war. The mortality
 *       of whatever is happening here.</li>
 *   <li><b>roof</b> — the workplace itself is gone: earthquake, fire, flood, tsunami. This is
 *       where part D's {@code workplace} earns its keep.</li>
 *   <li><b>trade</b> — the road is shut, so what is brought is not brought. Read from whether a
 *       trade route runs here in this year.</li>
 * </ul>
 *
 * <p>Which of them reaches a given product is decided by the {@code work} part D wrote on it:
 * a crop takes the field, a smith takes the hands and the roof, a merchant takes the road. That
 * table ships in the pack so the builder and this class cannot drift apart.
 */
public final class Shortages {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/substitutes.json.gz";
    public static final String ROUTES = "/data/eternalengine/eternal/history/trade_routes.json.gz";

    /** Below this, a place noticed and forgot; not worth telling a player about. */
    public static final double NOTICEABLE = 0.02d;

    private static volatile Shortages instance;

    private final Map<String, List<Instead>> table;
    private final Map<String, double[]> reach;
    private final double[] reachDefault;
    private final String problem;
    private final int count;
    private final int links;
    /** Every town on every route, as {lat, lon, start, end} — the trade network, for tradeCut. */
    private final List<double[]> routePoints;

    /**
     * What stands in for a product.
     *
     * @param id   the product that would do, if it can be had
     * @param how  {@code good}, {@code serves} or {@code last resort}
     * @param why  one line saying why this and not something else
     */
    public record Instead(String id, String how, String why) {

        /** True when this is eaten or burned only because there is nothing else. */
        public boolean famine() {
            return "last resort".equals(how);
        }

        /** "acorns and nuts" — the name only, so the sentence can place it where it reads. */
        public String phrase(ProductAtlas atlas) {
            ProductAtlas.Product p = atlas.product(id);
            return p == null ? id.replace('_', ' ') : p.name().toLowerCase(Locale.ROOT);
        }

        /** "they would eat acorns and nuts, which is the famine food of the oak woods". */
        public String withWhy(ProductAtlas atlas) {
            return phrase(atlas) + ", " + why;
        }
    }

    /**
     * The four shocks at one place and year, each 0 (nothing) to 1 (everything gone).
     *
     * @param field how much of the harvest is missing
     * @param hand  what share of the people who make things are missing
     * @param roof  how much of the workplace is on the ground
     * @param trade how much of what is brought is not coming
     * @param why   one line naming the worst thing behind it, plain words
     */
    public record Shock(double field, double hand, double roof, double trade, String why) {

        /** True when this place is not worth troubling a player about. */
        public boolean quiet() {
            return field < NOTICEABLE && hand < NOTICEABLE && roof < NOTICEABLE
                    && trade < NOTICEABLE;
        }

        /** The worst of the four, for one-line summaries. */
        public double worst() {
            return Math.max(Math.max(field, hand), Math.max(roof, trade));
        }

        /** What hits this kind of work hardest, named. */
        public String worstName() {
            double w = worst();
            if (w == field) {
                return "the land";
            }
            if (w == hand) {
                return "the people who make it";
            }
            if (w == roof) {
                return "the roof over the work";
            }
            return "the road";
        }
    }

    /**
     * How much of a product there actually is, and why.
     *
     * @param supply 1.0 for all of it, 0.0 for none
     * @param why    one line, or blank when nothing is wrong
     */
    public record Supply(double supply, String why) {

        /** True when the good is short enough to say so. */
        public boolean short1() {
            return supply < 1.0d - NOTICEABLE;
        }

        /** True when there is none of it at all. */
        public boolean none() {
            return supply <= NOTICEABLE;
        }

        /** "Flour and meal is down to about half of what there was." */
        public String sentence(ProductAtlas.Product p) {
            if (!short1()) {
                return p.name() + " is here as usual";
            }
            if (none()) {
                return "there is no " + p.name().toLowerCase(Locale.ROOT) + " to be had: " + why;
            }
            return p.name() + " is down to about " + words(supply) + " of what there was: " + why;
        }

        private static String words(double v) {
            if (v >= 0.85d) {
                return "nine tenths";
            }
            if (v >= 0.65d) {
                return "two thirds";
            }
            if (v >= 0.45d) {
                return "half";
            }
            if (v >= 0.25d) {
                return "a third";
            }
            return "a tenth";
        }
    }

    private Shortages(Map<String, List<Instead>> table, Map<String, double[]> reach,
                      double[] reachDefault, String problem, int count, int links,
                      List<double[]> routePoints) {
        this.table = table;
        this.reach = reach;
        this.reachDefault = reachDefault;
        this.problem = problem;
        this.count = count;
        this.links = links;
        this.routePoints = routePoints;
    }

    public static Shortages get() {
        Shortages s = instance;
        if (s == null) {
            synchronized (Shortages.class) {
                s = instance;
                if (s == null) {
                    s = load();
                    instance = s;
                }
            }
        }
        return s;
    }

    public boolean loaded() {
        return problem == null;
    }

    public String problem() {
        return problem;
    }

    public int count() {
        return count;
    }

    public int links() {
        return links;
    }

    /** One line for the atlas summary. */
    public String summary() {
        return problem == null
                ? "substitutes: " + count + " products, " + links + " links"
                : "substitutes: " + problem;
    }

    // ------------------------------------------------------------------ asking

    /** What stands in for this product, best first. Empty when nothing has been written. */
    public List<Instead> insteadOf(ProductAtlas.Product p) {
        return insteadOf(p == null ? null : p.id());
    }

    /** What stands in for this product id, best first. Empty when nothing has been written. */
    public List<Instead> insteadOf(@Nullable String id) {
        return id == null ? List.of() : table.getOrDefault(id, List.of());
    }

    /**
     * The four shocks at this place and year. Every one is read from a register that already
     * ships — the disasters, the climate periods and the trade routes — so nothing here is
     * invented; this class only adds them up.
     */
    public Shock shockAt(double lat, double lon, int year) {
        double field = 0.0d;
        double hand = 0.0d;
        double roof = 0.0d;
        String why = "";
        double worstSeen = 0.0d;

        // the land: the climate register's own yield modifier, then every disaster's field loss
        try {
            ClimatePeriods cp = ClimatePeriods.get();
            if (cp.loaded()) {
                double y = cp.yieldModifier(lat, lon, year);
                if (y < 1.0d) {
                    field = Math.max(field, 1.0d - y);
                    if (field > worstSeen) {
                        worstSeen = field;
                        // name the period rather than shrug: the register knows which one it is
                        why = cp.active(lat, lon, year).stream()
                                .filter(p -> p.yieldImpact() < 0.0d)
                                .map(ClimatePeriods.Period::name)
                                .reduce((a, b) -> a + " and " + b)
                                .orElse("the weather has turned against the harvest");
                    }
                }
            }
        } catch (Throwable t) {
            // a register that will not load must not lose the rest of the answer
        }

        try {
            Disasters ds = Disasters.get();
            if (ds.loaded()) {
                for (Disaster d : ds.at(lat, lon, year)) {
                    double f = ds.fieldLoss(d, lat, lon);
                    double m = ds.mortality(d, lat, lon);
                    double r = ds.damage(d, lat, lon);
                    if (f > field) {
                        field = f;
                    }
                    if (m > hand) {
                        hand = m;
                    }
                    if (r > roof) {
                        roof = r;
                    }
                    double here = Math.max(f, Math.max(m, r));
                    if (here > worstSeen) {
                        worstSeen = here;
                        why = d.name();
                    }
                }
            }
        } catch (Throwable t) {
            // as above
        }

        // the road: what is brought is only brought while a route runs here. Read from the pack
        // rather than through TradeRoutes, which drags the whole Minecraft side of the mod into
        // any class that touches it and so cannot be compiled or probed without a Forge runtime.
        double trade = tradeCut(lat, lon, year);
        if (trade > worstSeen) {
            worstSeen = trade;
            why = "the road that ran here is closed";
        }

        return new Shock(clamp(field), clamp(hand), clamp(roof), clamp(trade), why);
    }

    /**
     * How much of this product there is here and now. The product's own {@code work} — written in
     * part D — decides which of the four shocks reaches it: a crop takes the field, a smith takes
     * the hands and the roof, a merchant takes the road.
     */
    public Supply supplyOf(ProductAtlas.Product p, double lat, double lon, int year) {
        Shock s = shockAt(lat, lon, year);
        return supplyOf(p, s);
    }

    /**
     * How much of this product survives this shock, knowing whether it is made here or brought.
     *
     * <p>The trade shock is the one that depends on that: what arrives from elsewhere lives
     * entirely on the road, while what is made here only needs the road for its raw materials.
     * Everything else follows the work part D wrote on the product.
     */
    public Supply supplyOf(ProductAtlas.Product p, Shock s, ProductAtlas.Place at) {
        if (p == null) {
            return new Supply(0.0d, "there is no such thing");
        }
        double[] w = reach.getOrDefault(p.work(), reachDefault);
        boolean brought = at != null
                && ProductAtlas.get().status(p, at).status() == ProductAtlas.Status.BROUGHT;
        double tradeShare = brought ? 1.0d : w[3];
        double lost = s.field() * w[0] + s.hand() * w[1] + s.roof() * w[2] + s.trade() * tradeShare;
        lost = clamp(lost);
        return new Supply(1.0d - lost, s.why());
    }

    /** How much of this product survives this shock, not knowing where it comes from. */
    public Supply supplyOf(ProductAtlas.Product p, Shock s) {
        return supplyOf(p, s, null);
    }

    /**
     * What people here would use instead, best first, and only where the substitute is actually
     * to hand. This is the answer to give when {@link Supply#short1()} is true.
     */
    public List<Instead> insteadOf(ProductAtlas.Product p, ProductAtlas.Place at) {
        ProductAtlas atlas = ProductAtlas.get();
        List<Instead> out = new ArrayList<>();
        for (Instead in : insteadOf(p)) {
            ProductAtlas.Product sub = atlas.product(in.id());
            if (sub == null) {
                continue;
            }
            // the substitute has to exist here and now, or it is not an answer
            if (atlas.status(sub, at).status() == ProductAtlas.Status.MADE_HERE
                    || atlas.status(sub, at).status() == ProductAtlas.Status.BROUGHT) {
                out.add(in);
            }
        }
        return List.copyOf(out);
    }

    /** "Flour and meal is down to about half...; people here would eat porridge instead." */
    public String sentence(ProductAtlas.Product p, ProductAtlas.Place at, double lat, double lon) {
        Supply sup = supplyOf(p, shockAt(lat, lon, at.year()), at);
        String s = sup.sentence(p);
        if (!sup.short1()) {
            return s;
        }
        List<Instead> alt = insteadOf(p, at);
        if (alt.isEmpty()) {
            return s + "; there is nothing here that stands in for it";
        }
        Instead first = alt.get(0);
        String lead = first.famine()
                ? "; in the last resort people here would use "
                : "; people here would use ";
        return s + lead + first.phrase(ProductAtlas.get()) + " instead — " + first.why;
    }

    // ------------------------------------------------------------------ the road

    /**
     * How far a place can be from a route town and still count as on the road: about a week's
     * travel with a cart. There is no measurement behind this number — it is a radius, and the
     * alternative is claiming precision about roads nobody has surveyed.
     */
    public static final double ON_THE_ROAD_KM = 300.0d;

    /**
     * 1 when a route ran near this place and is no longer running — the road has closed — and 0
     * otherwise.
     *
     * <p>This is deliberately <em>not</em> "this place is remote, so nothing reaches it". The
     * fourteen routes in the register do not cover the world: the nearest town on any of them to
     * Hyderabad is Pondicherry, 622 km away, and the Dakshinapatha that actually crosses the
     * Deccan is recorded as three towns ending in 1300. Saying "no trade reaches Hyderabad"
     * because a register is thin is the same mistake as finding no peat in Ireland because a
     * United States survey never looked. Where no route is recorded near a place, this returns 0
     * and says nothing.
     */
    public double tradeCut(double lat, double lon, int year) {
        boolean ranHere = false;
        for (double[] p : routePoints) {
            if (km(lat, lon, p[0], p[1]) > ON_THE_ROAD_KM) {
                continue;
            }
            if (year >= p[2] && year <= p[3]) {
                return 0.0d;              // the road still runs
            }
            ranHere = true;               // it ran here once, in another year
        }
        return ranHere ? 1.0d : 0.0d;
    }

    /**
     * Whether any route in the register passes near this place at all. Where this is false, the
     * register is silent about the place's trade and {@link #tradeCut} will always say 0 — which
     * is not the same as saying the place trades.
     */
    public boolean routeRecordedNear(double lat, double lon) {
        for (double[] p : routePoints) {
            if (km(lat, lon, p[0], p[1]) <= ON_THE_ROAD_KM) {
                return true;
            }
        }
        return false;
    }

    /** The nearest town on any route, in any year, in km; -1 when the register holds none. */
    public double nearestRouteKm(double lat, double lon) {
        double best = Double.MAX_VALUE;
        for (double[] p : routePoints) {
            best = Math.min(best, km(lat, lon, p[0], p[1]));
        }
        return best == Double.MAX_VALUE ? -1.0d : best;
    }

    public int routeTowns() {
        return routePoints.size();
    }

    private static double km(double aLat, double aLon, double bLat, double bLon) {
        double r = 6371.0d;
        double dLat = Math.toRadians(bLat - aLat);
        double dLon = Math.toRadians(bLon - aLon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }

    // ------------------------------------------------------------------ loading

    private static double clamp(double v) {
        return v < 0.0d ? 0.0d : (v > 1.0d ? 1.0d : v);
    }

    private static Shortages load() {
        Map<String, List<Instead>> table = new HashMap<>();
        Map<String, double[]> reach = new HashMap<>();
        double[] reachDefault = {0.2d, 0.5d, 0.4d, 0.2d};
        int count = 0;
        int links = 0;
        try (InputStream raw = Shortages.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new Shortages(Map.of(), Map.of(), reachDefault,
                        "resource " + RESOURCE + " is absent", 0, 0, List.of());
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            if (root.has("substitutes")) {
                for (var e : root.getAsJsonArray("substitutes")) {
                    JsonObject o = e.getAsJsonObject();
                    List<Instead> list = new ArrayList<>();
                    if (o.has("instead")) {
                        for (var a : o.getAsJsonArray("instead")) {
                            JsonObject b = a.getAsJsonObject();
                            list.add(new Instead(str(b, "id"), str(b, "how"), str(b, "why")));
                            links++;
                        }
                    }
                    table.put(str(o, "id"), List.copyOf(list));
                    count++;
                }
            }
            if (root.has("shocksReach")) {
                for (String k : root.getAsJsonObject("shocksReach").keySet()) {
                    JsonArray a = root.getAsJsonObject("shocksReach").getAsJsonArray(k);
                    if (a.size() >= 4) {
                        reach.put(k, new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(),
                                a.get(2).getAsDouble(), a.get(3).getAsDouble()});
                    }
                }
            }
            if (root.has("shocksReachDefault")) {
                JsonArray a = root.getAsJsonArray("shocksReachDefault");
                if (a.size() >= 4) {
                    reachDefault = new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(),
                            a.get(2).getAsDouble(), a.get(3).getAsDouble()};
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Shortages(Map.of(), Map.of(), reachDefault,
                    "resource " + RESOURCE + " failed: " + e.getMessage(), 0, 0, List.of());
        }
        return new Shortages(Collections.unmodifiableMap(table),
                Collections.unmodifiableMap(reach), reachDefault, null, count, links,
                Collections.unmodifiableList(loadRoutes()));
    }

    /**
     * The towns on the fourteen trade routes, so a place can be asked whether the road still
     * runs to it. The roads themselves are left out: 14,769 of them, and they are local.
     */
    private static List<double[]> loadRoutes() {
        List<double[]> out = new ArrayList<>();
        try (InputStream raw = Shortages.class.getResourceAsStream(ROUTES)) {
            if (raw == null) {
                return List.of();
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            if (!root.has("routes")) {
                return List.of();
            }
            for (var e : root.getAsJsonArray("routes")) {
                JsonObject o = e.getAsJsonObject();
                int from = o.has("start") ? o.get("start").getAsInt() : Integer.MIN_VALUE;
                int to = o.has("end") ? o.get("end").getAsInt() : Integer.MAX_VALUE;
                if (!o.has("towns")) {
                    continue;
                }
                for (var t : o.getAsJsonArray("towns")) {
                    JsonArray a = t.getAsJsonArray();
                    if (a.size() < 3) {
                        continue;
                    }
                    out.add(new double[]{a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                            from, to});
                }
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
