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
 * Step 28 (known problems 39 and 40 of step 26): occupied and disputed land.
 *
 * <p>Occupations: step 26 gave lands occupied in the Second World War back their own state ("the Kingdom of Denmark,
 * ruled by Christian X" in 1942). {@code history/occupations.json.gz} keeps the occupation itself, square by square
 * (built by {@code worldfill/build_occupations.py} from the record before the fix), so the ruler line can add "German
 * troops have held this land since 1940."
 *
 * <p>Disputed land: {@code history/disputed_lands.json}, a short hand-written table (the West Bank, Gaza, the Golan
 * Heights, Crimea, Northern Cyprus, Western Sahara) whose line replaces the record's "This land belongs to ..." in its
 * years.
 */
public final class OccupiedLands {

    public static final String OCCUPATIONS = "/data/eternalengine/eternal/history/occupations.json.gz";
    public static final String DISPUTED = "/data/eternalengine/eternal/history/disputed_lands.json";

    /** Troops of one power held the square in these years ({@code who}: "German", "Japanese", "Soviet" ...). */
    public record Held(String who, int from, int to) {
    }

    /** One disputed area: its outline (longitude, latitude pairs) and its lines by years. */
    public record Area(String id, String name, double[] outline, List<int[]> years, List<String> lines) {
        public Optional<String> lineIn(int year) {
            for (int i = 0; i < years.size(); i++) {
                if (years.get(i)[0] <= year && year <= years.get(i)[1]) {
                    return Optional.of(lines.get(i));
                }
            }
            return Optional.empty();
        }

        public boolean contains(double lat, double lon) {
            boolean in = false;
            int n = outline.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = outline[2 * i], yi = outline[2 * i + 1];
                double xj = outline[2 * j], yj = outline[2 * j + 1];
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                    in = !in;
                }
            }
            return in;
        }
    }

    private static volatile OccupiedLands instance;

    private final Map<Long, Held[]> held;
    private final List<Area> areas;
    private final int squares;
    private final String problem;

    private OccupiedLands(Map<Long, Held[]> held, List<Area> areas, int squares, String problem) {
        this.held = held;
        this.areas = areas;
        this.squares = squares;
        this.problem = problem;
    }

    public static OccupiedLands get() {
        OccupiedLands o = instance;
        if (o == null) {
            synchronized (OccupiedLands.class) {
                if (instance == null) {
                    instance = load();
                }
                o = instance;
            }
        }
        return o;
    }

    private static long key(int lat100, int lon100) {
        return (long) lat100 * 100_000L + lon100;
    }

    /** The key of the 0.5-degree square around a point (centres at .25 and .75). */
    static long squareOf(double lat, double lon) {
        int la = (int) Math.round((Math.floor(lat * 2.0d) / 2.0d + 0.25d) * 100.0d);
        int lo = (int) Math.round((Math.floor(lon * 2.0d) / 2.0d + 0.25d) * 100.0d);
        return key(la, lo);
    }

    private static OccupiedLands load() {
        Map<Long, Held[]> held = new HashMap<>();
        List<Area> areas = new ArrayList<>();
        StringBuilder problems = new StringBuilder();
        int squares = 0;
        try (InputStream raw = OccupiedLands.class.getResourceAsStream(OCCUPATIONS)) {
            if (raw == null) {
                problems.append(OCCUPATIONS).append(" is missing; ");
            } else {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(raw),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement ge : root.getAsJsonArray("groups")) {
                    JsonObject g = ge.getAsJsonObject();
                    List<Held> spans = new ArrayList<>();
                    for (JsonElement se : g.getAsJsonArray("spans")) {
                        JsonArray sp = se.getAsJsonArray();
                        spans.add(new Held(sp.get(0).getAsString(), sp.get(1).getAsInt(), sp.get(2).getAsInt()));
                    }
                    Held[] arr = spans.toArray(new Held[0]);
                    JsonArray cells = g.getAsJsonArray("cells");
                    for (int i = 0; i + 1 < cells.size(); i += 2) {
                        held.put(key(cells.get(i).getAsInt(), cells.get(i + 1).getAsInt()), arr);
                        squares++;
                    }
                }
            }
        } catch (Exception e) {
            problems.append("cannot read ").append(OCCUPATIONS).append(": ").append(e).append("; ");
        }
        try (InputStream raw = OccupiedLands.class.getResourceAsStream(DISPUTED)) {
            if (raw == null) {
                problems.append(DISPUTED).append(" is missing; ");
            } else {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement ae : root.getAsJsonArray("areas")) {
                    JsonObject a = ae.getAsJsonObject();
                    JsonArray out = a.getAsJsonArray("outline");
                    double[] pts = new double[out.size() * 2];
                    for (int i = 0; i < out.size(); i++) {
                        pts[2 * i] = out.get(i).getAsJsonArray().get(0).getAsDouble();
                        pts[2 * i + 1] = out.get(i).getAsJsonArray().get(1).getAsDouble();
                    }
                    List<int[]> years = new ArrayList<>();
                    List<String> lines = new ArrayList<>();
                    for (JsonElement se : a.getAsJsonArray("spans")) {
                        JsonObject sp = se.getAsJsonObject();
                        years.add(new int[]{sp.get("from").getAsInt(), sp.get("to").getAsInt()});
                        lines.add(sp.get("line").getAsString());
                    }
                    areas.add(new Area(a.get("id").getAsString(), a.get("name").getAsString(), pts, List.copyOf(years),
                            List.copyOf(lines)));
                }
            }
        } catch (Exception e) {
            problems.append("cannot read ").append(DISPUTED).append(": ").append(e).append("; ");
        }
        return new OccupiedLands(Map.copyOf(held), List.copyOf(areas), squares,
                problems.length() == 0 ? null : problems.toString());
    }

    /**
     * Troops holding the square in this year. {@code placeId} is the ruler register's place (a grid place names its
     * square: "grid_55.75_12.25"); otherwise the square around the point.
     */
    public Optional<Held> heldIn(String placeId, double lat, double lon, int year) {
        long k = squareOf(lat, lon);
        if (placeId != null && placeId.startsWith(RulerRegister.GRID_PREFIX)) {
            try {
                String[] parts = placeId.substring(RulerRegister.GRID_PREFIX.length()).split("_");
                k = key((int) Math.round(Double.parseDouble(parts[0]) * 100.0d),
                        (int) Math.round(Double.parseDouble(parts[1]) * 100.0d));
            } catch (RuntimeException ignored) {
                // Not a grid place after all: the square around the point.
            }
        }
        Held[] spans = held.get(k);
        if (spans == null) {
            return Optional.empty();
        }
        for (Held h : spans) {
            if (h.from() <= year && year <= h.to()) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }

    /** The line of a disputed area containing the point in this year, if any. */
    public Optional<String> disputedLine(double lat, double lon, int year) {
        for (Area a : areas) {
            if (a.contains(lat, lon)) {
                Optional<String> l = a.lineIn(year);
                if (l.isPresent()) {
                    return l;
                }
            }
        }
        return Optional.empty();
    }

    public List<Area> areas() {
        return areas;
    }

    public int squares() {
        return squares;
    }

    public String problem() {
        return problem;
    }
}
