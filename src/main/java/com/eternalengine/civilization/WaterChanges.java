package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Step 35 (B2): <b>the water that moved</b>, and when.
 *
 * <p>A world that can be set to any date cannot draw today's rivers. The Yellow River has changed
 * course six times in the record, the last in 1855; the Zuiderzee was an inland sea until 1932;
 * the Aral was the fourth largest lake on Earth in 1960 and is a salt flat now; Doggerland was
 * dry land with people on it in 6000 BCE and is under the North Sea. Every one of those is a
 * year, and a map without the year is a map of the wrong world.
 *
 * <p>{@code history/water_changes.json.gz} (built by {@code worldfill/build_water_changes.py}
 * from the hand-written table in {@code worldfill/water_changes_table.py}) holds 158 changes
 * written by hand, because no data set has them. Each names the water, where it was, how wide a
 * thing it was, the year it happened, the year it was undone where it was, and whether that year
 * is recorded or estimated.
 *
 * <p><b>The rule for a caller: ask this before you draw water here.</b> A square the table says
 * dried in 1200 must not be given a lake in 1400; one it says was reclaimed in 1932 must not be
 * given a sea today. Where nothing is recorded there is no answer, and no answer is not
 * permission — it means the pack does not know.
 *
 * <p>The radius is how wide a thing the change was: a river's avulsion counts in hundreds of
 * kilometres, a harbour silting up in tens. It is what decides whether a change is close enough
 * to the place being asked about to be the answer.
 */
public final class WaterChanges {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/water_changes.json.gz";

    /** The end year of a change that was never undone. */
    public static final int STILL = Integer.MAX_VALUE;

    private static volatile WaterChanges instance;

    private final List<Change> changes;
    private final String problem;

    /** One change to the water: what it was, where, when, and what happened to it. */
    public record Change(String name, String kind, double lat, double lon, double radiusKm,
                         int year, int endYear, String change, String note, boolean estimated) {

        public boolean isStandingIn(int atYear) {
            return atYear >= year && atYear <= (endYear == STILL ? Integer.MAX_VALUE : endYear);
        }

        public boolean happenedBy(int atYear) {
            return atYear >= year;
        }

        public double distanceKm(double atLat, double atLon) {
            return haversineKm(lat, lon, atLat, atLon);
        }

        public String span() {
            String from = year < 0 ? (-year) + " BC" : "AD " + year;
            if (endYear == STILL) {
                return "from " + from;
            }
            String to = endYear < 0 ? (-endYear) + " BC" : "AD " + endYear;
            return from + " to " + to;
        }
    }

    private WaterChanges(List<Change> changes, String problem) {
        this.changes = List.copyOf(changes);
        this.problem = problem;
    }

    public static WaterChanges get() {
        WaterChanges w = instance;
        if (w == null) {
            synchronized (WaterChanges.class) {
                if (instance == null) {
                    instance = load();
                }
                w = instance;
            }
        }
        return w;
    }

    public boolean loaded() {
        return problem == null && !changes.isEmpty();
    }

    public String problem() {
        return problem;
    }

    public int count() {
        return changes.size();
    }

    public List<Change> changes() {
        return changes;
    }

    /**
     * The changes that were standing in this year and reached this place: a river that had moved
     * and had not moved back, a sea that had been made and not unmade.
     *
     * <p><b>Latest first, not nearest.</b> The newest thing that happened to a piece of water is
     * what it is now: the Zuiderzee was made by the St Lucia flood of 1287 and unmade by the
     * Afsluitdijk of 1932, and a caller asking about 1960 wants the dam, not the flood. Sorting
     * by distance alone gives the flood, because both rows sit on the same bay.
     */
    public List<Change> standing(double lat, double lon, int year) {
        List<Change> out = new ArrayList<>();
        for (Change c : changes) {
            if (c.isStandingIn(year) && c.distanceKm(lat, lon) <= c.radiusKm()) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparingInt(Change::year).reversed()
                .thenComparingDouble(c -> c.distanceKm(lat, lon)));
        return List.copyOf(out);
    }

    /** Every change that had happened here by this year, whether or not it is still standing. */
    public List<Change> happenedBy(double lat, double lon, int year) {
        List<Change> out = new ArrayList<>();
        for (Change c : changes) {
            if (c.happenedBy(year) && c.distanceKm(lat, lon) <= c.radiusKm()) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparingInt(Change::year).reversed()
                .thenComparingDouble(c -> c.distanceKm(lat, lon)));
        return List.copyOf(out);
    }

    /** Changes within this many kilometres, whatever their radius says — for a command, mostly. */
    public List<Change> near(double lat, double lon, int year, double withinKm) {
        List<Change> out = new ArrayList<>();
        for (Change c : changes) {
            if (c.isStandingIn(year) && c.distanceKm(lat, lon) <= withinKm) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparingDouble(c -> c.distanceKm(lat, lon)));
        return List.copyOf(out);
    }

    /** Was the water here changed at all by this year? */
    public boolean changedBy(double lat, double lon, int year) {
        for (Change c : changes) {
            if (c.happenedBy(year) && c.distanceKm(lat, lon) <= c.radiusKm()) {
                return true;
            }
        }
        return false;
    }

    /** One sentence for a player standing here in this year, or empty when nothing is known. */
    public String describe(double lat, double lon, int year) {
        List<Change> here = standing(lat, lon, year);
        if (here.isEmpty()) {
            return "";
        }
        Change c = here.get(0);
        return c.name() + " " + c.change() + ", " + c.span() + ". " + c.note();
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        return r * 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
    }

    private static WaterChanges load() {
        List<Change> out = new ArrayList<>();
        try (InputStream raw = WaterChanges.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new WaterChanges(List.of(), "resource " + RESOURCE + " absent");
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(
                    new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            for (JsonElement el : root.getAsJsonArray("changes")) {
                JsonArray r = el.getAsJsonArray();
                // [name, kind, lat*1000, lon*1000, radiusKm, year, endYear or null,
                //  change, note, recorded|estimated]
                int end = r.get(6).isJsonNull() ? STILL : r.get(6).getAsInt();
                out.add(new Change(
                        r.get(0).getAsString(),
                        r.get(1).getAsString(),
                        r.get(2).getAsDouble() / 1000.0d,
                        r.get(3).getAsDouble() / 1000.0d,
                        r.get(4).getAsDouble(),
                        r.get(5).getAsInt(),
                        end,
                        r.get(7).getAsString(),
                        r.get(8).getAsString(),
                        "estimated".equals(r.get(9).getAsString())));
            }
            if (out.isEmpty()) {
                return new WaterChanges(List.of(), RESOURCE + " held no changes");
            }
            return new WaterChanges(out, null);
        } catch (Exception e) {
            return new WaterChanges(List.of(), "resource " + RESOURCE + " failed: " + e.getMessage());
        }
    }
}
