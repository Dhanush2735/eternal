package com.eternalengine.terrabridge;

import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.civilization.HistoricalPresence;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

/**
 * OHM occupancy from Terra++ disk cache only. Does not call Overpass.
 * Dated features use start_date. Undated named places use
 * {@link com.eternalengine.civilization.SettlementYearEstimator}
 * (not beginning-of-time, not a modern-only floor).
 */
public final class OhmPresence {

    private static final double TILE_DEG = 0.01d;
    private static final double SEARCH_DEG = 0.03d;
    private static final double MAX_KM = 30.0d;
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private OhmPresence() {}

    public record Hit(
            String name,
            String place,
            double lat,
            double lon,
            int startYear,
            int endYear,
            SettlementRank rank,
            double distanceKm
    ) {}

    public static Optional<Hit> nearestDated(double lat, double lon, int year) {
        return nearest(lat, lon, year, false);
    }

    /**
     * Undated named places. Start year is estimated (gravity / diffusion / parent),
     * never the beginning of time and never a modern-only floor.
     */
    public static Optional<Hit> nearestPresent(double lat, double lon, int year) {
        return nearest(lat, lon, year, true);
    }

    private static Optional<Hit> nearest(double lat, double lon, int year, boolean undatedOk) {
        Path dir = cacheDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return Optional.empty();
        }
        Hit best = null;
        int minI = (int) Math.floor((lat - SEARCH_DEG) / TILE_DEG);
        int maxI = (int) Math.floor((lat + SEARCH_DEG) / TILE_DEG);
        int minJ = (int) Math.floor((lon - SEARCH_DEG) / TILE_DEG);
        int maxJ = (int) Math.floor((lon + SEARCH_DEG) / TILE_DEG);
        for (int i = minI; i <= maxI; i++) {
            for (int j = minJ; j <= maxJ; j++) {
                long key = ((long) i << 32) | (j & 0xffffffffL);
                Path file = dir.resolve(Long.toHexString(key) + ".json");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    String json = Files.readString(file);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    JsonArray elements = root.getAsJsonArray("elements");
                    if (elements == null) {
                        continue;
                    }
                    for (JsonElement el : elements) {
                        if (!el.isJsonObject()) {
                            continue;
                        }
                        Optional<Hit> hit = parseElement(el.getAsJsonObject(), lat, lon, year, undatedOk);
                        if (hit.isPresent() && (best == null || hit.get().distanceKm() < best.distanceKm())) {
                            best = hit.get();
                        }
                    }
                } catch (Exception ignored) {
                    // Skip a bad tile.
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Hit> parseElement(JsonObject obj, double plat, double plon, int year) {
        return parseElement(obj, plat, plon, year, false);
    }

    private static Optional<Hit> parseElement(JsonObject obj, double plat, double plon, int year,
                                              boolean undatedOk) {
        JsonObject tags = obj.has("tags") && obj.get("tags").isJsonObject()
                ? obj.getAsJsonObject("tags") : null;
        if (tags == null) {
            return Optional.empty();
        }
        if (undatedOk && (tag(tags, "name") == null || tag(tags, "place") == null)) {
            return Optional.empty();
        }
        SettlementRank rank = rankFromTags(tags);
        if (rank == null) {
            return Optional.empty();
        }
        double[] ll = coords(obj);
        if (ll == null) {
            return Optional.empty();
        }
        int start = parseYear(tag(tags, "start_date"));
        if (start == UNKNOWN) {
            if (!undatedOk) {
                return Optional.empty();
            }
            start = com.eternalengine.civilization.HistoryGazetteer.get()
                    .estimateYear(ll[0], ll[1], rank);
            if (start == com.eternalengine.civilization.SettlementYearEstimator.UNKNOWN) {
                return Optional.empty();
            }
        }
        int end = parseYear(tag(tags, "end_date"));
        if (year < start) {
            return Optional.empty();
        }
        if (end != UNKNOWN && year >= end) {
            return Optional.empty();
        }
        double km = HistoricalPresence.haversineKm(plat, plon, ll[0], ll[1]);
        if (km > MAX_KM) {
            return Optional.empty();
        }
        String place = tag(tags, "place");
        String name = tag(tags, "name");
        if (name == null) {
            name = place != null ? place : "ohm";
        }
        return Optional.of(new Hit(name, place == null ? "" : place, ll[0], ll[1], start, end, rank, km));
    }

    private static SettlementRank rankFromTags(JsonObject tags) {
        String place = tag(tags, "place");
        if (place != null) {
            String p = place.toLowerCase(Locale.ROOT);
            if (p.equals("city") || p.equals("municipality")) {
                return SettlementRank.CITY;
            }
            if (p.equals("town") || p.equals("borough")) {
                return SettlementRank.TOWN;
            }
            if (p.equals("village") || p.equals("hamlet") || p.equals("suburb")
                    || p.equals("neighbourhood") || p.equals("isolated_dwelling")) {
                return SettlementRank.VILLAGE;
            }
        }
        String building = tag(tags, "building");
        if (building != null && tag(tags, "name") != null) {
            return SettlementRank.VILLAGE;
        }
        String landuse = tag(tags, "landuse");
        if (landuse != null && landuse.equalsIgnoreCase("residential")) {
            return SettlementRank.VILLAGE;
        }
        return null;
    }

    private static double[] coords(JsonObject obj) {
        if (obj.has("lat") && obj.has("lon")) {
            return new double[] {obj.get("lat").getAsDouble(), obj.get("lon").getAsDouble()};
        }
        if (obj.has("center") && obj.get("center").isJsonObject()) {
            JsonObject c = obj.getAsJsonObject("center");
            if (c.has("lat") && c.has("lon")) {
                return new double[] {c.get("lat").getAsDouble(), c.get("lon").getAsDouble()};
            }
        }
        if (obj.has("bounds") && obj.get("bounds").isJsonObject()) {
            JsonObject b = obj.getAsJsonObject("bounds");
            if (b.has("minlat") && b.has("minlon") && b.has("maxlat") && b.has("maxlon")) {
                return new double[] {
                        (b.get("minlat").getAsDouble() + b.get("maxlat").getAsDouble()) / 2.0d,
                        (b.get("minlon").getAsDouble() + b.get("maxlon").getAsDouble()) / 2.0d
                };
            }
        }
        return null;
    }

    private static String tag(JsonObject tags, String key) {
        if (!tags.has(key) || tags.get(key).isJsonNull()) {
            return null;
        }
        try {
            String s = tags.get(key).getAsString();
            return s == null || s.isBlank() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    static int parseYear(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace("circa ", "").replace("~", "").replace("about ", "");
        boolean bce = s.contains("bce") || s.contains("bc") || s.startsWith("-");
        s = s.replace("bce", "").replace("bc", "").replace("ad", "").trim();
        if (s.startsWith("-")) {
            bce = true;
            s = s.substring(1);
        }
        int cut = s.indexOf('-');
        if (cut > 0) {
            s = s.substring(0, cut);
        }
        s = s.replaceAll("[^0-9]", "");
        if (s.isEmpty()) {
            return UNKNOWN;
        }
        try {
            int y = Integer.parseInt(s);
            if (y == 0) {
                return UNKNOWN;
            }
            return bce ? -y : y;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    private static Path cacheDir() {
        try {
            Class<?> cls = Class.forName("net.buildtheearth.terraplusplus.util.TerraCachePaths");
            Object p = cls.getMethod("dir", String[].class).invoke(null, (Object) new String[] {"ohm_structures_v2"});
            if (p instanceof Path path) {
                return path;
            }
        } catch (Throwable ignored) {
            // Terra++ not on classpath in some tests.
        }
        Path fallback = Paths.get(System.getProperty("user.dir", "."), "terra_cache", "ohm_structures_v2");
        return fallback;
    }
}
