package com.eternalengine.trade;

import com.eternalengine.civilization.WaysGrid;
import com.eternalengine.vehicle.Vehicles;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
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
 * Step 46 part 4 (E11): <b>traffic</b> — how much of it there is, and by which medium.
 *
 * <p>Three registers already answer parts of the plan's E11, and this class is the fourth:
 *
 * <table border="1">
 *   <caption>who knows what</caption>
 *   <tr><th>knows</th><th>answers</th></tr>
 *   <tr><td>step 34 {@link WaysGrid}</td><td>is there a road / railway / canal <b>here</b> by now
 *       (124,240 squares)</td></tr>
 *   <tr><td>{@link Vehicles}</td><td>which vehicle is right, and what it needs</td></tr>
 *   <tr><td>{@code WorkingAnimals}</td><td>has the beast that draws it arrived</td></tr>
 *   <tr><td><b>this class</b></td><td><b>how much, and by which medium</b></td></tr>
 * </table>
 *
 * <h2>The decisive fact: freight is priced by the kilogram and the kilometre</h2>
 *
 * <p>Diocletian's Edict on Maximum Prices (AD 301) is the only surviving document that prices
 * freight and goods in the same currency on the same day, which is why its ratios are usable where
 * later reconstructions are not: <b>1 sea : 5 downriver : 10 upriver : 52 road</b> per kg-km.
 *
 * <p>So a cargo travels exactly as far as its own value pays for — and the leash is short for
 * heavy cheap things:
 *
 * <pre>
 *   wheat         10 denarii/kg   road    286 km   river  2,941 km   sea  14,925 km
 *   timber         0.4            road     11 km   river    118 km   sea     597 km
 *   pepper     2,667              road 76,200 km   (goes anywhere)
 *   silk      36,697              road  1,048,485 km
 * </pre>
 *
 * <p><b>That is why Rome was fed by sea and not by road</b>, and why every great pre-industrial
 * city sits on water. It is a derived fact, not a decoration: change a price and the leash moves.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Traffic {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/traffic.json.gz";

    private static volatile Traffic instance;

    /** A good and the leash each medium gives it. */
    public record Good(String id, String name, double perKg, String kind, boolean bulky,
                       String entry, Map<String, Integer> reach) {

        /** How far this good can go by a route before freight doubles its price. */
        public int reachBy(String route) {
            Integer r = reach.get(route);
            return r == null ? 0 : r;
        }

        public String describe() {
            return name + " (" + perKg + " denarii/kg): " + reachBy("road") + " km by road, "
                    + reachBy("downriver") + " by river, " + reachBy("sea") + " by sea";
        }
    }

    /** What is moving on one stretch of one route. */
    public record Street(String route, @Nullable Vehicles.Vehicle vehicle, int vehiclesPerDay,
                         double tonnesPerDay, int kmPerDay, boolean congested,
                         @Nullable String limit) {

        public String line() {
            String what = vehicle == null ? "traffic" : vehicle.name();
            return vehiclesPerDay + " x " + what + " a day, " + fmt(tonnesPerDay) + " t, at "
                    + kmPerDay + " km/day"
                    + (congested ? " - OVERLOADED: " + limit : "");
        }
    }

    /** Which route a good should take here, and why. */
    public record Choice(String route, int km, boolean possible, @Nullable String why) {

        public String line() {
            return route + ": " + (possible ? "possible" : "not possible - " + why);
        }
    }

    /** How full a vehicle actually runs: a design capacity is not a load. Derived from the
     *  Bolton count — 14,000 tons a year in forty wagons is 0.96 t against a 1.2 t wagon. */
    private double loadFactor = 0.8d;
    private final Map<String, Double> freight;
    private final Map<String, Good> goods;
    private final Map<String, Integer> speeds;
    private final Map<String, Integer> gates;
    private final List<Map<String, Object>> counts;
    private final String finding;
    private final String problem;

    private Traffic(Map<String, Double> freight, Map<String, Good> goods,
                    Map<String, Integer> speeds, Map<String, Integer> gates,
                    List<Map<String, Object>> counts, String finding, String problem) {
        this.freight = freight;
        this.goods = goods;
        this.speeds = speeds;
        this.gates = gates;
        this.counts = counts;
        this.finding = finding;
        this.problem = problem;
    }

    public static Traffic get() {
        Traffic t = instance;
        if (t == null) {
            synchronized (Traffic.class) {
                t = instance;
                if (t == null) {
                    t = load();
                    instance = t;
                }
            }
        }
        return t;
    }

    public static Traffic reload() {
        synchronized (Traffic.class) {
            instance = load();
            return instance;
        }
    }

    private static Traffic load() {
        Map<String, Double> freight = new LinkedHashMap<>();
        Map<String, Good> goods = new LinkedHashMap<>();
        Map<String, Integer> speeds = new LinkedHashMap<>();
        Map<String, Integer> gates = new LinkedHashMap<>();
        List<Map<String, Object>> counts = new ArrayList<>();
        try (InputStream raw = Traffic.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new Traffic(freight, goods, speeds, gates, counts, null,
                        "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            double lf = root.has("loadFactor") ? root.get("loadFactor").getAsDouble() : 0.8d;
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("freight").entrySet()) {
                freight.put(e.getKey(), e.getValue().getAsDouble());
            }
            for (JsonElement ge : root.getAsJsonArray("goods")) {
                JsonObject o = ge.getAsJsonObject();
                Map<String, Integer> reach = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> r : o.getAsJsonObject("reach").entrySet()) {
                    reach.put(r.getKey(), r.getValue().getAsInt());
                }
                goods.put(str(o, "id"), new Good(str(o, "id"), str(o, "name"),
                        o.get("perKg").getAsDouble(), str(o, "class"),
                        o.get("bulky").getAsBoolean(), str(o, "entry"), reach));
            }
            for (JsonElement se : root.getAsJsonArray("speeds")) {
                JsonObject o = se.getAsJsonObject();
                speeds.put(str(o, "route"), o.get("kmPerDay").getAsInt());
            }
            for (JsonElement ge : root.getAsJsonArray("gates")) {
                JsonObject o = ge.getAsJsonObject();
                gates.put(str(o, "id"), o.get("vehiclesPerHour").getAsInt());
            }
            for (JsonElement ce : root.getAsJsonArray("counts")) {
                counts.add(Map.of("id", str(ce.getAsJsonObject(), "id"),
                        "counted", str(ce.getAsJsonObject(), "counted")));
            }
                     Traffic t = new Traffic(freight, goods, speeds, gates, counts,
                    root.has("finding") ? str(root, "finding") : null, null);
            t.loadFactor = lf;
            return t;
        } catch (Exception e) {
            return new Traffic(freight, goods, speeds, gates, counts, null,
                    "resource " + RESOURCE + " failed: " + e);
        }
    }

    // -------------------------------------------------------------------------------------------- asking

    public boolean hasData() {
        return problem == null && !goods.isEmpty();
    }

    @Nullable
    public String problem() {
        return problem;
    }

    @Nullable
    public String finding() {
        return finding;
    }

    /** Denarii per kilogram per kilometre on a route. */
    public double freight(String route) {
        Double d = freight.get(route);
        return d == null ? 0.0d : d;
    }

    /** The ratio the mod quotes: 1 sea, 5 downriver, 10 upriver, 52 road. */
    public double ratio(String route) {
        double sea = freight("sea");
        return sea <= 0.0d ? 0.0d : freight(route) / sea;
    }

    @Nullable
    public Good good(String id) {
        return goods.get(id);
    }

    public List<Good> goods() {
        return List.copyOf(goods.values());
    }

    /** A working speed for a route, in km a day. */
    public int kmPerDay(String route) {
        Integer k = speeds.get(route);
        if (k != null) return k;
        return switch (route) {
            case "road" -> 25;
            case "rail" -> 500;
            case "river", "canal" -> 60;
            case "sea" -> 120;
            default -> 20;
        };
    }

    /**
     * How far a good can go by a route before freight doubles its price.
     *
     * <p>The Edict's own arithmetic: value per kilogram divided by freight per kilogram-kilometre.
     */
    public int howFar(String goodId, String route) {
        Good g = good(goodId);
        double c = freight(route);
        if (g == null || c <= 0.0d) return 0;
        return (int) Math.round(g.perKg() / c);
    }

    /**
     * Which route a good of this value should take over this distance, <b>here</b>.
     *
     * <p>Water first if the water is there, because it is fifty times cheaper; road only if the
     * good is worth enough or the distance short enough. Returns the choice and, when a route is
     * refused, why.
     */
    public List<Choice> routesFor(String goodId, int km, double lat, double lon, int year) {
        List<Choice> out = new ArrayList<>();
        WaysGrid ways = WaysGrid.get();
        for (String route : List.of("sea", "downriver", "canal", "rail", "road")) {
            boolean present = switch (route) {
                case "road" -> ways.roadBy(lat, lon, year);
                case "rail" -> ways.railBy(lat, lon, year);
                case "canal" -> ways.canalBy(lat, lon, year);
                case "sea", "downriver" -> com.eternalengine.civilization.CountryGrid.get()
                        .nearSea(lat, lon, 3);
                default -> false;
            };
            if (!present) {
                out.add(new Choice(route, km, false, "it does not reach here"));
                continue;
            }
            int reach = howFar(goodId, route);
            if (reach < km) {
                out.add(new Choice(route, km, false,
                        "freight would double its price at " + reach + " km"));
                continue;
            }
            out.add(new Choice(route, km, true, null));
        }
        return out;
    }

    /**
     * What is moving on a route here, given how much tonnage has to move.
     *
     * <p>The arithmetic is deliberately plain — tonnage divided by what one vehicle carries,
     * divided by how many trips a vehicle can make in a day — and it is checked against three
     * documented counts rather than asserted. Forty loaded wagons a day carrying 14,000 tons a year
     * through Bolton, Massachusetts, comes back as 38.
     *
     * @param tonnesPerDay how much has to move along this route here
     */
    public Street trafficOn(String route, double tonnesPerDay, @Nullable String vehicleId) {
        Vehicles.Vehicle v = vehicleId == null ? null : Vehicles.get().byId(vehicleId);
        int capacity = v == null ? 500 : Math.max(1, (int) v.loadKg());
        int speed = v == null ? kmPerDay(route) : v.kmPerDay();
        double tonnesPerVehicle = capacity / 1000.0d * loadFactor;
        int perDay = (int) Math.ceil(tonnesPerDay / Math.max(0.001d, tonnesPerVehicle));

        // A gate is a ceiling, and a single opening is about one vehicle a minute.
        String limit = null;
        boolean congested = false;
        if ("road".equals(route)) {
            if (perDay > 20000) { congested = true; limit = "more than a trunk road carries"; }
            else if (perDay > 2000) { congested = true; limit = "more than a turnpike gate takes"; }
        } else if ("rail".equals(route) && perDay > 288) {
            congested = true;
            limit = "more than a single-track line takes (12 an hour)";
        }
        return new Street(route, v, perDay, tonnesPerDay, speed, congested, limit);
    }

    /**
     * How long a consignment takes to cover a distance by a route, in days.
     *
     * <p>The number a trader actually cares about, and the one that explains why the same journey
     * cost five times as much upriver as down.
     */
    public double days(int km, String route) {
        int speed = kmPerDay(route);
        return speed <= 0 ? Double.MAX_VALUE : km / (double) speed;
    }

    /** How full a vehicle runs. Derived from a documented count, not assumed. */
    public double loadFactor() {
        return loadFactor;
    }

    /** One line for {@code /ee check}. */
    public String summary() {
        if (!hasData()) return "the traffic register has no data: " + problem;
        return goods.size() + " goods with derived leashes; road costs "
                + String.format(Locale.ROOT, "%.0f", ratio("road"))
                + " times sea per kg-km; " + counts.size() + " documented counts";
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Traffic[%d goods, %s]", goods.size(),
                problem == null ? RESOURCE : problem);
    }
}
