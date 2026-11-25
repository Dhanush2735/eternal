package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 34 (B24): <b>what lies under the ground</b>, and where it has been worked.
 *
 * <p>{@code history/minerals.json.gz} (built by {@code worldfill/build_minerals.py}) holds 143,302
 * deposit sites — gold, silver, copper, iron, tin, lead, zinc, coal, salt, clay, stone, gems, oil,
 * phosphates and forty-three more — each with a point, the minerals in it, and, where any source
 * knows, the years it was worked.
 *
 * <p>Three sources, because no one of them answers the question:
 * <ul>
 *   <li><b>USGS MRDS</b> (public domain): the world's mineral records, clustered here at 1 km into
 *       142,971 sites. It is 82% American and gives a production year for only 5% of them, and it
 *       barely knows coal or oil at all.</li>
 *   <li><b>A hand-written table of 122 famous deposits and coalfields</b>, each naming the Wikipedia
 *       article it comes from and taking its coordinates from that article. This is what supplies
 *       the silver of Laurium that paid for Athens' fleet, the cinnabar of Almadén, the salt of
 *       Hallstatt, the tin of Cornwall, the copper of Timna, the diamonds of Golconda, and the
 *       sixteen coalfields that ran the industrial world.</li>
 *   <li><b>Wikidata's oil fields</b>: 209 of them, the one deposit class Wikidata does have. (Its
 *       gold mine class has two items.)</li>
 * </ul>
 *
 * <p>Two questions, and they are different:
 * <ul>
 *   <li>{@link #under(double, double, double)} — what is in the ground here. The geology does not
 *       change, so this takes no year.</li>
 *   <li>{@link #worked(double, double, double, int)} — what was known and worked here by a given
 *       year. Only the 21,878 sites with a year can answer it, and that is a limit, not a fault of
 *       the question.</li>
 * </ul>
 *
 * <p>Each site also carries a status — occurrence, prospect, past producer or producer — so a caller
 * can tell a seam that was worked from a place where a mineral merely shows in the rock.
 *
 * <p>Looked up by a 1° index. Loaded once, lazily, and kept. Nothing here touches Minecraft.
 */
public final class Minerals {

    public static final String FILE = "/data/eternalengine/eternal/history/minerals.json.gz";
    /** A site with no known first year, or none that any source records. */
    public static final int NO_YEAR = Integer.MIN_VALUE;

    private static volatile Minerals instance;

    /** One deposit: what is under it, and whether it was worked. */
    public record Site(double lat, double lon, List<String> minerals, int start, int end,
                       String name, String status, int source) {

        /** Was it worked in this year? Sites with no year are not counted. */
        public boolean workedIn(int year) {
            return start != NO_YEAR && start <= year && (end == NO_YEAR || year <= end);
        }

        public String describe() {
            String what = (name == null || name.isEmpty())
                    ? String.join(", ", minerals) + " deposit"
                    : name;
            return what + " (" + String.join(", ", minerals) + ")"
                    + (start == NO_YEAR ? "" : ", worked from " + start);
        }
    }

    private final double[] lats;
    private final double[] lons;
    private final short[][] mins;
    private final int[] starts;
    private final int[] ends;
    private final String[] names;
    private final byte[] status;
    private final byte[] source;
    private final String[] mineralNames;
    private final String[] statusNames;
    private final Map<Integer, int[]> cells;
    private final String problem;

    private Minerals(double[] lats, double[] lons, short[][] mins, int[] starts, int[] ends,
                     String[] names, byte[] status, byte[] source, String[] mineralNames,
                     String[] statusNames, Map<Integer, int[]> cells, String problem) {
        this.lats = lats;
        this.lons = lons;
        this.mins = mins;
        this.starts = starts;
        this.ends = ends;
        this.names = names;
        this.status = status;
        this.source = source;
        this.mineralNames = mineralNames;
        this.statusNames = statusNames;
        this.cells = cells;
        this.problem = problem;
    }

    public static Minerals get() {
        Minerals m = instance;
        if (m == null) {
            synchronized (Minerals.class) {
                if (instance == null) {
                    instance = load();
                }
                m = instance;
            }
        }
        return m;
    }

    public boolean loaded() {
        return problem == null && lats.length > 0;
    }

    public String problem() {
        return problem;
    }

    public int size() {
        return lats.length;
    }

    public String[] minerals() {
        return mineralNames.clone();
    }

    /** What lies under the ground within {@code km} of a point, whatever the year. */
    public List<Site> under(double lat, double lon, double km) {
        if (!loaded()) {
            return Collections.emptyList();
        }
        List<Site> out = new ArrayList<>();
        for (int i : candidates(lat, lon, km)) {
            out.add(at(i));
        }
        return out;
    }

    /** The minerals under the ground within {@code km} of a point, in no particular order. */
    public Set<String> mineralsUnder(double lat, double lon, double km) {
        Set<String> out = new LinkedHashSet<>();
        if (!loaded()) {
            return out;
        }
        for (int i : candidates(lat, lon, km)) {
            for (short m : mins[i]) {
                out.add(mineralNames[m]);
            }
        }
        return out;
    }

    /** The deposits known to have been worked within {@code km} of a point by this year. */
    public List<Site> worked(double lat, double lon, double km, int year) {
        if (!loaded()) {
            return Collections.emptyList();
        }
        List<Site> out = new ArrayList<>();
        for (int i : candidates(lat, lon, km)) {
            if (starts[i] != NO_YEAR && starts[i] <= year && (ends[i] == NO_YEAR || year <= ends[i])) {
                out.add(at(i));
            }
        }
        return out;
    }

    /** Does this mineral lie under the ground here? */
    public boolean hasMineral(double lat, double lon, double km, String mineral) {
        if (!loaded()) {
            return false;
        }
        for (int i : candidates(lat, lon, km)) {
            for (short m : mins[i]) {
                if (mineralNames[m].equalsIgnoreCase(mineral)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Site at(int i) {
        List<String> list = new ArrayList<>(mins[i].length);
        for (short m : mins[i]) {
            list.add(mineralNames[m]);
        }
        return new Site(lats[i], lons[i], list, starts[i], ends[i], names[i],
                statusNames[status[i]], source[i]);
    }

    private List<Integer> candidates(double lat, double lon, double km) {
        List<Integer> out = new ArrayList<>();
        int span = (int) Math.ceil(km / 111.0d);
        int cy = (int) Math.floor(lat);
        int cx = (int) Math.floor(lon);
        double dLat = km / 111.32d;
        double dLon = km / (111.32d * Math.max(0.2d, Math.cos(Math.toRadians(lat))));
        for (int dy = -span; dy <= span; dy++) {
            for (int dx = -span; dx <= span; dx++) {
                int[] ids = cells.get(cellKey(cy + dy, cx + dx));
                if (ids == null) {
                    continue;
                }
                for (int id : ids) {
                    if (Math.abs(lats[id] - lat) <= dLat && Math.abs(lons[id] - lon) <= dLon) {
                        out.add(id);
                    }
                }
            }
        }
        return out;
    }

    private static int cellKey(int y, int x) {
        return (y + 200) * 1000 + (x + 400);
    }

    private static Minerals load() {
        try (InputStream raw = Minerals.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                return empty(FILE + " is missing");
            }
            JsonObject o;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw, 1 << 16),
                    StandardCharsets.UTF_8)) {
                o = JsonParser.parseReader(r).getAsJsonObject();
            }
            JsonArray mn = o.getAsJsonArray("minerals");
            String[] mineralNames = new String[mn.size()];
            for (int i = 0; i < mn.size(); i++) {
                mineralNames[i] = mn.get(i).getAsString();
            }
            JsonArray st = o.getAsJsonArray("status");
            String[] statusNames = new String[st.size()];
            for (int i = 0; i < st.size(); i++) {
                statusNames[i] = st.get(i).getAsString();
            }
            JsonArray sites = o.getAsJsonArray("sites");
            int n = sites.size();
            double[] lats = new double[n];
            double[] lons = new double[n];
            short[][] mins = new short[n][];
            int[] starts = new int[n];
            int[] ends = new int[n];
            String[] names = new String[n];
            byte[] status = new byte[n];
            byte[] source = new byte[n];
            Map<Integer, List<Integer>> cellList = new HashMap<>();
            int i = 0;
            for (JsonElement e : sites) {
                JsonArray a = e.getAsJsonArray();
                lats[i] = a.get(0).getAsDouble();
                lons[i] = a.get(1).getAsDouble();
                JsonArray ma = a.get(2).getAsJsonArray();
                short[] m = new short[ma.size()];
                for (int k = 0; k < m.length; k++) {
                    m[k] = (short) ma.get(k).getAsInt();
                }
                mins[i] = m;
                starts[i] = a.get(3).isJsonNull() ? NO_YEAR : a.get(3).getAsInt();
                ends[i] = a.get(4).isJsonNull() ? NO_YEAR : a.get(4).getAsInt();
                names[i] = a.get(5).isJsonNull() ? null : a.get(5).getAsString();
                status[i] = (byte) a.get(6).getAsInt();
                source[i] = (byte) a.get(7).getAsInt();
                cellList.computeIfAbsent(cellKey((int) Math.floor(lats[i]), (int) Math.floor(lons[i])),
                        k -> new ArrayList<>()).add(i);
                i++;
            }
            Map<Integer, int[]> cells = new HashMap<>();
            for (Map.Entry<Integer, List<Integer>> e : cellList.entrySet()) {
                List<Integer> v = e.getValue();
                int[] arr = new int[v.size()];
                for (int k = 0; k < arr.length; k++) {
                    arr[k] = v.get(k);
                }
                cells.put(e.getKey(), arr);
            }
            return new Minerals(lats, lons, mins, starts, ends, names, status, source,
                    mineralNames, statusNames, cells, null);
        } catch (Exception e) { // noqa: the mod must run without this file
            return empty(FILE + ": " + e);
        }
    }

    private static Minerals empty(String problem) {
        return new Minerals(new double[0], new double[0], new short[0][], new int[0], new int[0],
                new String[0], new byte[0], new byte[0], new String[0], new String[0],
                new HashMap<>(), problem);
    }
}
