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
 * The language of the people around a place, from Glottolog 5.3 (CC BY 4.0).
 *
 * <p>One map point per living language, where it is spoken today (built by
 * {@code worldfill/build_local_languages.py}). Only the big languages (with an ISO 639-1 code:
 * Telugu, French, Pashto...) are named as "the people's language", and only from AD 1500 on: a point
 * shows today's speech, and before 1500 many of today's language maps did not hold yet (Arabic in
 * Egypt, Turkish in Anatolia). Before 1500 the state's own languages are all the mod says.
 */
public final class LocalLanguages {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/local_languages.json.gz";
    public static final int FROM_YEAR = 1500;
    public static final double MAJOR_KM = 300.0d;
    private static final double CELL = 2.0d;

    public record Language(String name, double lat, double lon, boolean major) {}

    public record Hit(Language language, double distanceKm) {}

    private static final LocalLanguages INSTANCE = new LocalLanguages();

    private boolean loaded;
    private String source = "missing";
    private int count;
    private Map<Long, List<Language>> grid = Map.of();

    private LocalLanguages() {}

    public static LocalLanguages get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** The nearest big language within 300 km, from AD 1500 on; empty otherwise. */
    public Optional<Hit> peoplesLanguage(double lat, double lon, int year) {
        if (year < FROM_YEAR) {
            return Optional.empty();
        }
        return nearest(lat, lon, MAJOR_KM, true);
    }

    /** The nearest living language of any size within {@code km} (for /ee who). */
    public Optional<Hit> nearestAny(double lat, double lon, double km) {
        return nearest(lat, lon, km, false);
    }

    private Optional<Hit> nearest(double lat, double lon, double km, boolean majorOnly) {
        int ring = (int) Math.ceil(km / (CELL * 111.0d)) + 1;
        long cy = (long) Math.floor((lat + 90.0d) / CELL);
        long cx = (long) Math.floor((lon + 180.0d) / CELL);
        long cols = (long) Math.ceil(360.0d / CELL);
        Hit best = null;
        for (long dy = -ring; dy <= ring; dy++) {
            for (long dx = -ring; dx <= ring; dx++) {
                long x = Math.floorMod(cx + dx, cols);
                List<Language> in = grid.get((cy + dy) * 1000L + x);
                if (in == null) {
                    continue;
                }
                for (Language l : in) {
                    if (majorOnly && !l.major()) {
                        continue;
                    }
                    double d = HistoricalPresence.haversineKm(lat, lon, l.lat(), l.lon());
                    if (d <= km && (best == null || d < best.distanceKm())) {
                        best = new Hit(l, d);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public String summary() {
        return "local languages: " + count + " living languages from " + source;
    }

    /** Languages loaded, for the runtime checks. */
    public int size() {
        return count;
    }

    /** Where the languages were read from, or why they could not be. */
    public String source() {
        return source;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream raw = LocalLanguages.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                Map<Long, List<Language>> g = new HashMap<>();
                int n = 0;
                for (JsonElement el : root.getAsJsonArray("languages")) {
                    JsonArray a = el.getAsJsonArray();
                    Language l = new Language(a.get(0).getAsString(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                            a.get(3).getAsInt() == 1);
                    long key = (long) Math.floor((l.lat() + 90.0d) / CELL) * 1000L
                            + (long) Math.floor((l.lon() + 180.0d) / CELL);
                    g.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
                    n++;
                }
                grid = g;
                count = n;
                source = RESOURCE;
            }
        } catch (Exception e) {
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
            grid = Map.of();
        }
    }
}
