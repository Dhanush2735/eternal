package com.eternalengine.buildings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Famous buildings, walls and ruins by year (systems B8, B16, B19).
 *
 * <p>23,489 structures built by {@code worldfill/build_famous_buildings.py} from Wikidata, with the
 * mod's own gazetteer filling the years Wikidata does not carry. Each has a point, a kind, the year
 * it was built, the year it ended, and how many Wikipedias know it.
 *
 * <p>What this is for: Terra++ draws the world from OpenStreetMap, and step 31 measured what that
 * means -- 0.9% of mapped structures carry a start date, so the rest fall to a regional population
 * floor and today's skyline is drawn in antiquity, and 2 of 31 test places show nothing at all.
 * This register is the dated answer. It does not replace Terra++; it says what was standing, so the
 * map and the player's sentence can agree with the year.
 *
 * <p>Three things the caller should know:
 * <ul>
 *   <li>{@link #near} only returns a building that is standing in the year asked about. A building
 *       whose end year is unknown is treated as still standing, which is the honest reading of "we
 *       do not know when it ended" but does mean a ruin with no recorded end shows as standing.</li>
 *   <li>A building flagged {@link Building#holy} is also in the holy places register (C13). Both
 *       know about it on purpose -- only this one carries an end date -- so a caller that is about
 *       to place something should skip the holy ones and let C13 speak.</li>
 *   <li>About 1,300 of the years are marked {@code estimated}. They come from the gazetteer's
 *       occupation window for the site, not from a recorded build year, so they bound the building
 *       rather than date it.</li>
 * </ul>
 *
 * <p>Loaded once, lazily, and kept. 0.5 MB gzipped, held as a list of records and a one-degree
 * grid, in the same shape {@code CultureAtlas} uses for holy places.
 */
public final class FamousBuildings {

    private static final String FILE =
        "/data/eternalengine/eternal/history/buildings_famous.json.gz";

    /**
     * The year a building with no known end stops being listed. Nothing is that old.
     *
     * <p><b>Step 36:</b> public, because {@link BuildingLife} has to tell "this building ended in
     * a year the record knows" from "this building has never ended", and a magic number in two
     * packages is a bug waiting to happen.
     */
    public static final int NO_END = Integer.MAX_VALUE;

    private static volatile FamousBuildings instance;

    /**
     * One famous building: its name, point, kind, years, fame, and where the year came from.
     *
     * @param ruin whether Wikidata calls the thing a ruin. This is a fact about it, not a date --
     *             the records that carry it usually do not say when it fell, so it cannot be
     *             placed in time. See {@link #ruinedIn}.
     */
    public record Building(String name, double lat, double lon, String kind, int from, int to,
                           int articles, String source, boolean holy, @Nullable String site,
                           boolean ruin) {

        /** Does it stand in this year? An unknown end is treated as still standing. */
        public boolean standsIn(int year) {
            return year >= from && (to == NO_END || year < to);
        }

        /**
         * Is it in ruins by this year?
         *
         * <p>Only answerable where the end year is known. {@link #ruin} says a thing is a ruin
         * without saying when it became one, so it is not used here: the Colosseum carries the
         * flag, and calling it a ruin in AD 100 would be as wrong as calling it new.
         */
        public boolean ruinedIn(int year) {
            return to != NO_END && year >= to;
        }

        /** True where the year is a gazetteer occupation window, not a recorded build year. */
        public boolean estimated() {
            return "gazetteer".equals(source);
        }

        /** B16: is this part of a place's defences rather than a building inside them? */
        public boolean isDefence() {
            return DEFENCE_KINDS.contains(kind);
        }
    }

    /** B16: city walls, gates and forts. */
    private static final java.util.Set<String> DEFENCE_KINDS = java.util.Set.of(
        "city wall", "wall", "rampart", "city gate", "citadel", "fort", "fortification");

    private final List<Building> all = new ArrayList<>();
    private final Map<Integer, List<Building>> grid = new HashMap<>();
    private String problem;
    private int estimated;
    private int holyCount;
    private int withEnd;
    private int ruinCount;
    private int defenceCount;
    private long loadMillis;

    private FamousBuildings() {
    }

    /** The shared register, loaded on first use. */
    public static FamousBuildings get() {
        FamousBuildings f = instance;
        if (f == null) {
            synchronized (FamousBuildings.class) {
                f = instance;
                if (f == null) {
                    f = new FamousBuildings();
                    f.load();
                    instance = f;
                }
            }
        }
        return f;
    }

    // ================================================================== the answer

    /**
     * The best known buildings standing within {@code km} in that year, most famous first.
     *
     * @param max at most this many; pass {@code Integer.MAX_VALUE} for all of them
     */
    public List<Building> near(double lat, double lon, int year, double km, int max) {
        List<Building> out = new ArrayList<>();
        if (problem != null) {
            return out;
        }
        int cy = (int) Math.floor(lat);
        int cx = (int) Math.floor(lon);
        int span = (int) Math.ceil(km / 111.0d);
        for (int dy = -span; dy <= span; dy++) {
            for (int dx = -span; dx <= span; dx++) {
                List<Building> cell = grid.get(key(cy + dy, cx + dx));
                if (cell == null) {
                    continue;
                }
                for (Building b : cell) {
                    if (b.standsIn(year) && kmBetween(lat, lon, b.lat, b.lon) <= km) {
                        out.add(b);
                    }
                }
            }
        }
        out.sort(Comparator.comparingInt((Building b) -> -b.articles)
            .thenComparing(b -> b.name));
        return out.size() <= max ? out : new ArrayList<>(out.subList(0, max));
    }

    /**
     * B19: what is in ruins here by this year -- Hampi after 1565, Mohenjo-daro after 1900 BC.
     *
     * <p>Only buildings whose end year is known can be placed in time. A further few hundred are
     * known to be ruins without a year (see {@link Building#ruin}); they are counted in
     * {@link #ruinCount()} but cannot be returned here, because "it is a ruin" does not say
     * "it was a ruin then". Most famous first.
     */
    public List<Building> ruinsNear(double lat, double lon, int year, double km, int max) {
        List<Building> out = new ArrayList<>();
        if (problem != null) {
            return out;
        }
        for (Building b : scan(lat, lon, km)) {
            if (b.ruinedIn(year)) {
                out.add(b);
            }
        }
        out.sort(Comparator.comparingInt((Building b) -> -b.articles).thenComparing(b -> b.name));
        return out.size() <= max ? out : new ArrayList<>(out.subList(0, max));
    }

    /**
     * B16: the walls, gates and forts of this place standing in this year, most famous first.
     */
    public List<Building> defencesNear(double lat, double lon, int year, double km, int max) {
        List<Building> out = new ArrayList<>();
        if (problem != null) {
            return out;
        }
        for (Building b : scan(lat, lon, km)) {
            if (b.isDefence() && b.standsIn(year)) {
                out.add(b);
            }
        }
        out.sort(Comparator.comparingInt((Building b) -> -b.articles).thenComparing(b -> b.name));
        return out.size() <= max ? out : new ArrayList<>(out.subList(0, max));
    }

    /** "In ruins here: Hampi (an archaeological site, from 1336 until 1565)." */
    public String ruinLine(double lat, double lon, int year, double km, int max) {
        List<Building> ruins = ruinsNear(lat, lon, year, km, max);
        if (ruins.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Building b : ruins) {
            parts.add(b.name() + " (" + article(b.kind()) + " " + b.kind() + ", from "
                + yearText(b.from()) + " until " + yearText(b.to()) + ")");
        }
        return "In ruins here: " + join(parts) + ".";
    }

    /** "Its walls and gates: the Aurelian Walls (a city wall, from 271), the Porta Appia." */
    public String defencesLine(double lat, double lon, int year, double km, int max) {
        List<Building> d = defencesNear(lat, lon, year, km, max);
        if (d.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Building b : d) {
            parts.add(b.name() + " (a " + b.kind() + ", from " + yearText(b.from()) + ")");
        }
        return "Its walls, gates and forts: " + join(parts) + ".";
    }

    /** Every building within {@code km}, whatever year -- the raw scan the others filter. */
    private List<Building> scan(double lat, double lon, double km) {
        List<Building> out = new ArrayList<>();
        int cy = (int) Math.floor(lat);
        int cx = (int) Math.floor(lon);
        int span = (int) Math.ceil(km / 111.0d);
        for (int dy = -span; dy <= span; dy++) {
            for (int dx = -span; dx <= span; dx++) {
                List<Building> cell = grid.get(key(cy + dy, cx + dx));
                if (cell != null) {
                    out.addAll(cell);
                }
            }
        }
        return out;
    }

    /** The single most famous building standing here in this year, if any. */
    public java.util.Optional<Building> best(double lat, double lon, int year, double km) {
        List<Building> near = near(lat, lon, year, km, 1);
        return near.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(near.get(0));
    }

    /**
     * "Here stand the Colosseum (an amphitheatre, from 82) and the Arch of Titus (a city gate,
     * from 82)." A sentence in the mod's own words for the player; empty when nothing is known.
     */
    public String line(double lat, double lon, int year, double km, int max) {
        List<Building> near = near(lat, lon, year, km, max);
        if (near.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Building b : near) {
            parts.add(b.name() + " (" + article(b.kind()) + " " + b.kind()
                + ", from " + yearText(b.from()) + ")");
        }
        return "Here stand " + join(parts) + ".";
    }

    /** The short version: "the Colosseum, the Arch of Titus". */
    public String names(double lat, double lon, int year, double km, int max) {
        List<String> out = new ArrayList<>();
        for (Building b : near(lat, lon, year, km, max)) {
            out.add(b.name());
        }
        return join(out);
    }

    // ================================================================== state

    public boolean loaded() {
        return problem == null;
    }

    @Nullable
    public String problem() {
        return problem;
    }

    public int count() {
        return all.size();
    }

    public int estimatedCount() {
        return estimated;
    }

    public int holyCount() {
        return holyCount;
    }

    public int withEndCount() {
        return withEnd;
    }

    /** Buildings Wikidata calls a ruin, whether or not it says when they fell. */
    public int ruinCount() {
        return ruinCount;
    }

    /** B16: walls, gates and forts. */
    public int defenceCount() {
        return defenceCount;
    }

    public long loadMillis() {
        return loadMillis;
    }

    public String summary() {
        return problem != null ? "not loaded" : count() + " famous buildings";
    }

    // ================================================================== loading

    private void load() {
        long t0 = System.currentTimeMillis();
        try (InputStream raw = FamousBuildings.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                problem = FILE + " is missing";
                return;
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            JsonArray rows = root.getAsJsonArray("buildings");
            for (JsonElement re : rows) {
                JsonArray a = re.getAsJsonArray();
                // [name, lat*1000, lon*1000, kind, from, to, sitelinks, source, holy, site]
                int to = intOr(a.get(5), NO_END);
                Building b = new Building(
                    a.get(0).getAsString(),
                    a.get(1).getAsInt() / 1000.0d,
                    a.get(2).getAsInt() / 1000.0d,
                    a.get(3).getAsString(),
                    a.get(4).getAsInt(),
                    to,
                    a.get(6).getAsInt(),
                    a.get(7).getAsString(),
                    a.get(8).getAsInt() != 0,
                    a.get(9).isJsonNull() ? null : a.get(9).getAsString(),
                    a.size() > 10 && a.get(10).getAsInt() != 0);
                all.add(b);
                if (b.estimated()) {
                    estimated++;
                }
                if (b.holy()) {
                    holyCount++;
                }
                if (b.ruin()) {
                    ruinCount++;
                }
                if (b.isDefence()) {
                    defenceCount++;
                }
                if (to != NO_END) {
                    withEnd++;
                }
                grid.computeIfAbsent(key((int) Math.floor(b.lat), (int) Math.floor(b.lon)),
                    k -> new ArrayList<>()).add(b);
            }
        } catch (Exception e) {
            problem = "cannot read " + FILE + ": " + e;
            return;
        }
        loadMillis = System.currentTimeMillis() - t0;
    }

    // ================================================================== small helpers

    private static int key(int y, int x) {
        return (y + 90) * 1000 + (x + 180);
    }

    private static double kmBetween(double la1, double lo1, double la2, double lo2) {
        double p1 = Math.toRadians(la1);
        double p2 = Math.toRadians(la2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371.0d * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static int intOr(JsonElement e, int dflt) {
        return e == null || e.isJsonNull() ? dflt : e.getAsInt();
    }

    /** Years before our era are written "2500 BC" rather than "-2500". */
    private static String yearText(int year) {
        return year < 0 ? (-year) + " BC" : String.valueOf(year);
    }

    /** "a theatre" but "an archaeological site". */
    private static String article(String word) {
        if (word.isEmpty()) {
            return "a";
        }
        char c = Character.toLowerCase(word.charAt(0));
        return (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') ? "an" : "a";
    }

    private static String join(List<String> parts) {
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(i == parts.size() - 1 ? " and " : ", ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
