package com.eternalengine.civilization;

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
 * Roads and trade routes near a place: Roman roads from Itiner-e (CC BY 4.0) and the towns of the
 * great trade routes from their Wikipedia articles. Built by {@code worldfill/build_trade_routes.py}.
 *
 * <p>A road counts within {@link #ROAD_KM} of its line, from its first year; after its last year it
 * is an old road, still talked about until {@link #OLD_ROAD_UNTIL}. A route counts within
 * {@link #ROUTE_KM} of one of its towns, in the route's own years.
 */
public final class TradeRoutes {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/trade_routes.json.gz";
    public static final double ROAD_KM = 5.0d;
    public static final double ROUTE_KM = 30.0d;
    public static final int OLD_ROAD_UNTIL = 1500;
    private static final double CELL = 0.5d;
    private static final double KM_PER_DEG = 111.2d;

    public record Road(String name, boolean main, int start, int end, double[] points) {}

    public record RoadHit(Road road, double distanceKm, boolean inUse) {
        /** "from Tingi to Tamuda", or the bare name when it does not split into two places. */
        public String between() {
            String n = road.name();
            int i = n.indexOf('-');
            if (i > 0 && n.indexOf('-', i + 1) < 0) {
                return "from " + n.substring(0, i).trim() + " to " + n.substring(i + 1).trim();
            }
            return n.isBlank() ? "" : "called " + n;
        }
    }

    public record Route(String name, int start, int end, String kind, String[] towns, double[] points) {}

    public record RouteHit(Route route, String town, double distanceKm) {}

    private static final TradeRoutes INSTANCE = new TradeRoutes();

    private boolean loaded;
    private String source = "missing";
    private List<Road> roads = List.of();
    private List<Route> routes = List.of();
    private Map<Long, int[]> roadGrid = Map.of();

    private TradeRoutes() {}

    public static TradeRoutes get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** The nearest road within {@link #ROAD_KM} that exists this year (in use, or old until 1500). */
    public Optional<RoadHit> roadNear(double lat, double lon, int year) {
        if (year > OLD_ROAD_UNTIL) {
            return Optional.empty();
        }
        int[] in = roadGrid.get(cellKey(lat, lon));
        if (in == null) {
            return Optional.empty();
        }
        RoadHit best = null;
        double k = Math.cos(Math.toRadians(lat));
        for (int idx : in) {
            Road r = roads.get(idx);
            if (year < r.start()) {
                continue;
            }
            double[] p = r.points();
            for (int i = 0; i + 3 < p.length; i += 2) {
                double d = segmentKm(lat, lon, p[i], p[i + 1], p[i + 2], p[i + 3], k);
                if (d <= ROAD_KM && (best == null || d < best.distanceKm()
                        || (d == best.distanceKm() && r.main() && !best.road().main()))) {
                    best = new RoadHit(r, d, year <= r.end());
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The nearest town of a trade route active this year, within {@link #ROUTE_KM}. */
    public Optional<RouteHit> routeNear(double lat, double lon, int year) {
        RouteHit best = null;
        for (Route r : routes) {
            if (year < r.start() || year > r.end()) {
                continue;
            }
            double[] p = r.points();
            for (int i = 0; i < r.towns().length; i++) {
                double d = HistoricalPresence.haversineKm(lat, lon, p[2 * i], p[2 * i + 1]);
                if (d <= ROUTE_KM && (best == null || d < best.distanceKm())) {
                    best = new RouteHit(r, r.towns()[i], d);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Every route active this year with a town within {@link #ROUTE_KM}, nearest town per route,
     * nearest route first. A villager names only the nearest ({@link #routeNear}); the runtime
     * checks use this to see that a route passes a place at all.
     */
    public List<RouteHit> routesNear(double lat, double lon, int year) {
        List<RouteHit> out = new ArrayList<>();
        for (Route r : routes) {
            if (year < r.start() || year > r.end()) {
                continue;
            }
            RouteHit best = null;
            double[] p = r.points();
            for (int i = 0; i < r.towns().length; i++) {
                double d = HistoricalPresence.haversineKm(lat, lon, p[2 * i], p[2 * i + 1]);
                if (d <= ROUTE_KM && (best == null || d < best.distanceKm())) {
                    best = new RouteHit(r, r.towns()[i], d);
                }
            }
            if (best != null) {
                out.add(best);
            }
        }
        out.sort(java.util.Comparator.comparingDouble(RouteHit::distanceKm));
        return out;
    }

    /** Road sections loaded, for the runtime checks. */
    public int roadCount() {
        return roads.size();
    }

    /** Routes loaded (read-only), for the runtime checks. */
    /** Step 19e: every road section (for the map item). */
    public List<Road> roads() {
        return roads;
    }

    public List<Route> routes() {
        return routes;
    }

    /** Where the roads and routes were read from, or why they could not be. */
    public String source() {
        return source;
    }

    public String summary() {
        int towns = 0;
        for (Route r : routes) {
            towns += r.towns().length;
        }
        return "trade routes: " + roads.size() + " road sections, " + routes.size() + " routes with " + towns
                + " towns from " + source;
    }

    private static double segmentKm(double lat, double lon, double la1, double lo1, double la2, double lo2, double k) {
        double px = lon * k;
        double ax = lo1 * k;
        double bx = lo2 * k;
        double dx = bx - ax;
        double dy = la2 - la1;
        double len = dx * dx + dy * dy;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (lat - la1) * dy) / len));
        double qx = ax + t * dx - px;
        double qy = la1 + t * dy - lat;
        return Math.sqrt(qx * qx + qy * qy) * KM_PER_DEG;
    }

    private static long cellKey(double lat, double lon) {
        return (long) Math.floor((lat + 90.0d) / CELL) * 10000L + (long) Math.floor((lon + 180.0d) / CELL);
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream raw = TradeRoutes.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                List<Road> rs = new ArrayList<>();
                Map<Long, List<Integer>> grid = new HashMap<>();
                double pad = ROAD_KM / KM_PER_DEG + 1e-6;
                for (JsonElement el : root.getAsJsonArray("roads")) {
                    JsonArray a = el.getAsJsonArray();
                    JsonArray pts = a.get(4).getAsJsonArray();
                    double[] p = new double[pts.size()];
                    for (int i = 0; i < p.length; i++) {
                        p[i] = pts.get(i).getAsDouble();
                    }
                    int idx = rs.size();
                    rs.add(new Road(a.get(0).getAsString(), a.get(1).getAsInt() == 1, a.get(2).getAsInt(),
                            a.get(3).getAsInt(), p));
                    java.util.Set<Long> cells = new java.util.HashSet<>();
                    for (int i = 0; i + 3 < p.length; i += 2) {
                        double minLa = Math.min(p[i], p[i + 2]) - pad;
                        double maxLa = Math.max(p[i], p[i + 2]) + pad;
                        double padLo = pad / Math.max(0.2d, Math.cos(Math.toRadians(p[i])));
                        double minLo = Math.min(p[i + 1], p[i + 3]) - padLo;
                        double maxLo = Math.max(p[i + 1], p[i + 3]) + padLo;
                        for (double la = Math.floor(minLa / CELL) * CELL; la <= maxLa; la += CELL) {
                            for (double lo = Math.floor(minLo / CELL) * CELL; lo <= maxLo; lo += CELL) {
                                cells.add(cellKey(la + CELL / 2, lo + CELL / 2));
                            }
                        }
                    }
                    for (long c : cells) {
                        grid.computeIfAbsent(c, x -> new ArrayList<>()).add(idx);
                    }
                }
                Map<Long, int[]> packed = new HashMap<>();
                grid.forEach((c, list) -> packed.put(c, list.stream().mapToInt(Integer::intValue).toArray()));
                List<Route> ro = new ArrayList<>();
                JsonArray routeArr = root.has("routes") ? root.getAsJsonArray("routes") : new JsonArray();
                for (JsonElement el : routeArr) {
                    JsonObject o = el.getAsJsonObject();
                    JsonArray towns = o.getAsJsonArray("towns");
                    String[] names = new String[towns.size()];
                    double[] pts = new double[towns.size() * 2];
                    for (int i = 0; i < names.length; i++) {
                        JsonArray t = towns.get(i).getAsJsonArray();
                        names[i] = t.get(0).getAsString();
                        pts[2 * i] = t.get(1).getAsDouble();
                        pts[2 * i + 1] = t.get(2).getAsDouble();
                    }
                    ro.add(new Route(o.get("name").getAsString(), o.get("start").getAsInt(), o.get("end").getAsInt(),
                            o.has("kind") ? o.get("kind").getAsString() : "land", names, pts));
                }
                roads = List.copyOf(rs);
                routes = List.copyOf(ro);
                roadGrid = packed;
                source = RESOURCE;
            }
        } catch (Exception e) {
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
            roads = List.of();
            routes = List.of();
            roadGrid = Map.of();
        }
    }
}
