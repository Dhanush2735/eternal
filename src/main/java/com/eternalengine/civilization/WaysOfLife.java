package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * How people lived in a region and era, from the ArchaeoGLOBE expert survey (CC0).
 *
 * <p>ArchaeoGLOBE (Science 365:897, 2019) asked 255 archaeologists how land was used in 146 regions
 * at 10 dates from 8050 BC to AD 1850. For each region and date it rates five ways of life: hunting
 * and gathering, shifting farming, intensive farming, herding (0 none, 1 minimal, 2 common, 3
 * widespread) and towns (absent or present). Built by {@code worldfill/build_ways_of_life.py}; the
 * region outlines are already in latitude/longitude.
 *
 * <p>A year uses the latest survey date at or before it (8050 BC before the first). After 1850 the
 * survey has nothing to say, and callers fall back to the population-model estimate.
 */
public final class WaysOfLife {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/ways_of_life.json.gz";
    public static final int LAST_SLICE_USED = 1899;

    /** One region at one survey date. Levels: 0 none, 1 minimal, 2 common, 3 widespread, -1 unknown. */
    public record Reading(String region, int sliceYear, int foraging, int shiftingFarming, int intensiveFarming,
                          int herding, int towns) {

        /** "Most people around here farm the land and herd animals; some hunt, fish and gather." */
        public String sentence() {
            String[][] acts = {
                    {Integer.toString(intensiveFarming), "farm the land"},
                    {Integer.toString(shiftingFarming), "grow crops in small, shifting fields"},
                    {Integer.toString(herding), "herd animals"},
                    {Integer.toString(foraging), "hunt, fish and gather"}};
            List<String> most = new ArrayList<>();
            List<String> some = new ArrayList<>();
            List<String> few = new ArrayList<>();
            for (String[] a : acts) {
                int level = Integer.parseInt(a[0]);
                if (level == 3) {
                    most.add(a[1]);
                } else if (level == 2) {
                    some.add(a[1]);
                } else if (level == 1) {
                    few.add(a[1]);
                }
            }
            StringBuilder sb = new StringBuilder();
            if (!most.isEmpty()) {
                sb.append("Most people around here ").append(join(most));
                if (!some.isEmpty()) {
                    sb.append("; some ").append(join(some));
                }
            } else if (!some.isEmpty()) {
                sb.append("Many people around here ").append(join(some));
                if (!few.isEmpty()) {
                    sb.append("; a few ").append(join(few));
                }
            } else if (!few.isEmpty()) {
                sb.append("Only a few people live around here; they ").append(join(few));
            } else {
                return "";
            }
            if (towns == 1) {
                sb.append(", and there are towns in this land");
            }
            return sb.append(".").toString();
        }

        private static String join(List<String> parts) {
            if (parts.size() == 1) {
                return parts.get(0);
            }
            return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
        }
    }

    /** A survey region and its large area ("world"), whatever the year: for the product atlas (step 19, part B). */
    public record Place(String region, String world) {
    }

    private record Region(String name, String world, int[][] levels, double[][] rings, double minLat, double maxLat,
                          double minLon, double maxLon) {
        boolean contains(double lat, double lon) {
            if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) {
                return false;
            }
            boolean in = false;
            for (double[] ring : rings) {
                int n = ring.length / 2;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    double ai = ring[2 * i];
                    double oi = ring[2 * i + 1];
                    double aj = ring[2 * j];
                    double oj = ring[2 * j + 1];
                    if ((ai > lat) != (aj > lat) && lon < oi + (lat - ai) * (oj - oi) / (aj - ai)) {
                        in = !in;
                    }
                }
            }
            return in;
        }
    }

    private static final WaysOfLife INSTANCE = new WaysOfLife();

    private boolean loaded;
    private String source = "missing";
    private int[] slices = new int[0];
    private List<Region> regions = List.of();

    private WaysOfLife() {}

    public static WaysOfLife get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** The survey reading for this point and year; empty outside every region or after 1899. */
    public Optional<Reading> at(double lat, double lon, int year) {
        if (slices.length == 0 || year > LAST_SLICE_USED) {
            return Optional.empty();
        }
        int k = 0;
        for (int i = 0; i < slices.length; i++) {
            if (slices[i] <= year) {
                k = i;
            }
        }
        for (Region r : regions) {
            if (r.contains(lat, lon)) {
                int[][] lv = r.levels();
                return Optional.of(new Reading(r.name(), slices[k], lv[0][k], lv[1][k], lv[2][k], lv[3][k], lv[4][k]));
            }
        }
        return Optional.empty();
    }

    /** The survey region at this point and its large area, in any year; empty outside every region. */
    public Optional<Place> placeAt(double lat, double lon) {
        for (Region r : regions) {
            if (r.contains(lat, lon)) {
                return Optional.of(new Place(r.name(), r.world()));
            }
        }
        return Optional.empty();
    }

    /** The reading of a region, by name, in a year (empty after 1899 or for an unknown name): for the checks. */
    public Optional<Reading> readingFor(String region, int year) {
        if (slices.length == 0 || year > LAST_SLICE_USED) {
            return Optional.empty();
        }
        int k = 0;
        for (int i = 0; i < slices.length; i++) {
            if (slices[i] <= year) {
                k = i;
            }
        }
        for (Region r : regions) {
            if (r.name().equals(region)) {
                int[][] lv = r.levels();
                return Optional.of(new Reading(r.name(), slices[k], lv[0][k], lv[1][k], lv[2][k], lv[3][k], lv[4][k]));
            }
        }
        return Optional.empty();
    }

    /** Every survey region with its large area, for the checks. */
    public List<Place> regionList() {
        List<Place> out = new ArrayList<>();
        for (Region r : regions) {
            out.add(new Place(r.name(), r.world()));
        }
        return out;
    }

    public String summary() {
        return "ways of life: " + regions.size() + " regions x " + slices.length + " survey dates from " + source;
    }

    /** Survey regions loaded, for the runtime checks. */
    public int regionCount() {
        return regions.size();
    }

    /** Survey dates loaded, for the runtime checks. */
    public int sliceCount() {
        return slices.length;
    }

    /** Where the survey was read from, or why it could not be. */
    public String source() {
        return source;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream raw = WaysOfLife.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray sl = root.getAsJsonArray("slices");
                int[] s = new int[sl.size()];
                for (int i = 0; i < s.length; i++) {
                    s[i] = sl.get(i).getAsInt();
                }
                List<Region> out = new ArrayList<>();
                for (JsonElement el : root.getAsJsonArray("regions")) {
                    JsonObject o = el.getAsJsonObject();
                    String[] keys = {"fhg", "exagr", "inag", "pas", "urban"};
                    int[][] lv = new int[5][s.length];
                    int[] order = {0, 1, 2, 3, 4};
                    for (int c : order) {
                        JsonArray a = o.getAsJsonArray(keys[c]);
                        for (int i = 0; i < s.length; i++) {
                            lv[c][i] = a.get(i).getAsInt();
                        }
                    }
                    // Reading order: foraging, shifting farming, intensive farming, herding, towns
                    List<double[]> rings = new ArrayList<>();
                    double minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;
                    for (JsonElement re : o.getAsJsonArray("rings")) {
                        JsonArray ring = re.getAsJsonArray();
                        double[] flat = new double[ring.size() * 2];
                        for (int i = 0; i < ring.size(); i++) {
                            JsonArray p = ring.get(i).getAsJsonArray();
                            flat[2 * i] = p.get(0).getAsDouble();
                            flat[2 * i + 1] = p.get(1).getAsDouble();
                            minLa = Math.min(minLa, flat[2 * i]);
                            maxLa = Math.max(maxLa, flat[2 * i]);
                            minLo = Math.min(minLo, flat[2 * i + 1]);
                            maxLo = Math.max(maxLo, flat[2 * i + 1]);
                        }
                        rings.add(flat);
                    }
                    out.add(new Region(o.get("name").getAsString(), o.has("world") ? o.get("world").getAsString() : "",
                            lv, rings.toArray(new double[0][]),
                            minLa, maxLa, minLo, maxLo));
                }
                slices = s;
                regions = List.copyOf(out);
                source = RESOURCE;
            }
        } catch (Exception e) {
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
            regions = List.of();
            slices = new int[0];
        }
    }
}
