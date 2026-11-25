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
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 34 (B14 railways, B15 waterways, B18 industry, B26 rest houses): <b>the dated works of the
 * past</b> — what stood in a place, and from what year.
 *
 * <p>{@code history/ways_works.json.gz} (built by {@code worldfill/build_ways_works.py} from the
 * Wikidata rows fetched in part 2) holds 110,447 works: railway stations and metro stations, railway
 * lines and tunnels, bridges, canals, locks and weirs, ports, harbours, wharves and dry docks,
 * lighthouses, dams, factories, power stations and substations, watermills and windmills, shipyards,
 * gasworks, sawmills, steel mills, oil refineries, telephone exchanges, airports, mines,
 * caravanserais, post stations, coaching inns, Roman roads and pilgrim ways.
 *
 * <p>Every work carries a year, and that is the whole point. Wikidata holds 172 railway stations and
 * 76 metro stations within 25 km of Rome; they are all dated after 1830, so Rome in AD 100 answers
 * three bridges, two ports and a Roman road and nothing else. Sydney in 1850 answers a lighthouse,
 * a port and a works — no railway, because Australia's first opened in 1854.
 *
 * <p>Two kinds of year, and the caller can tell them apart:
 * <ul>
 *   <li>{@code estimated == false}: Wikidata records an inception, an official opening or a start
 *       time. 82,304 works.</li>
 *   <li>{@code estimated == true}: Wikidata gives no year at all, and the year used is the one the
 *       technology atlas gives for the arrival of that technology in the work's own country — a
 *       railway station cannot predate the railway. 28,143 works.</li>
 * </ul>
 * The 11,633 rows with neither are not in this pack. A bridge with no date is the very thing that
 * would put a motorway in Rome in AD 100.
 *
 * <p>Looked up by a 1° index, so asking about a place costs nothing. Loaded once, lazily, and kept.
 * Nothing here touches Minecraft.
 */
public final class WaysWorks {

    public static final String FILE = "/data/eternalengine/eternal/history/ways_works.json.gz";
    /** A work that has never been recorded as ending. */
    public static final int NO_END = Integer.MIN_VALUE;

    private static volatile WaysWorks instance;

    /** One work: what it is, where, and the years it stood. */
    public record Work(double lat, double lon, String kind, int start, int end, String name,
                       int links, boolean estimated) {

        /** Did it stand in this year? */
        public boolean standsIn(int year) {
            return start <= year && (end == NO_END || year <= end);
        }

        /** "Paddington railway station, opened 1854" — the words a caller would say. */
        public String describe() {
            String what = (name == null || name.isEmpty()) ? kind : name;
            return what + ", opened " + start + (estimated ? " (estimated)" : "");
        }
    }

    private final double[] lats;
    private final double[] lons;
    private final short[] kinds;
    private final int[] starts;
    private final int[] ends;
    private final String[] names;
    private final short[] links;
    private final boolean[] estimated;
    private final String[] kindNames;
    /** 1° cell -> the works inside it. */
    private final Map<Integer, int[]> cells;
    private final String problem;

    private WaysWorks(double[] lats, double[] lons, short[] kinds, int[] starts, int[] ends,
                      String[] names, short[] links, boolean[] estimated, String[] kindNames,
                      Map<Integer, int[]> cells, String problem) {
        this.lats = lats;
        this.lons = lons;
        this.kinds = kinds;
        this.starts = starts;
        this.ends = ends;
        this.names = names;
        this.links = links;
        this.estimated = estimated;
        this.kindNames = kindNames;
        this.cells = cells;
        this.problem = problem;
    }

    public static WaysWorks get() {
        WaysWorks w = instance;
        if (w == null) {
            synchronized (WaysWorks.class) {
                if (instance == null) {
                    instance = load();
                }
                w = instance;
            }
        }
        return w;
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

    /** The kinds a work can be: station, line, bridge, canal, port, lighthouse, dam, power, works, mill, air, mine, rest, way. */
    public String[] kinds() {
        return kindNames.clone();
    }

    /** Every work within {@code km} of a point, near enough first. */
    public List<Work> near(double lat, double lon, double km) {
        return near(lat, lon, km, Integer.MAX_VALUE, null);
    }

    /** Every work standing in {@code year} within {@code km} of a point. */
    public List<Work> near(double lat, double lon, double km, int year) {
        return near(lat, lon, km, year, null);
    }

    /**
     * The works of one kind (or all kinds, if {@code kind} is null) standing in {@code year} within
     * {@code km} of a point, the best known first.
     */
    public List<Work> near(double lat, double lon, double km, int year, String kind) {
        if (!loaded()) {
            return Collections.emptyList();
        }
        List<Work> out = new ArrayList<>();
        for (int i : candidates(lat, lon, km)) {
            if (year != Integer.MAX_VALUE && !standing(i, year)) {
                continue;
            }
            if (kind != null && !kind.equals(kindNames[kinds[i]])) {
                continue;
            }
            out.add(at(i));
        }
        out.sort((a, b) -> Integer.compare(b.links, a.links));
        return out;
    }

    /** Is there a work of this kind standing here in this year? */
    public boolean anyNear(double lat, double lon, double km, int year, String kind) {
        if (!loaded()) {
            return false;
        }
        for (int i : candidates(lat, lon, km)) {
            if (standing(i, year) && (kind == null || kind.equals(kindNames[kinds[i]]))) {
                return true;
            }
        }
        return false;
    }

    /** How many works of this kind stand here in this year. */
    public int countNear(double lat, double lon, double km, int year, String kind) {
        int n = 0;
        for (int i : candidates(lat, lon, km)) {
            if (standing(i, year) && (kind == null || kind.equals(kindNames[kinds[i]]))) {
                n++;
            }
        }
        return n;
    }

    private boolean standing(int i, int year) {
        return starts[i] <= year && (ends[i] == NO_END || year <= ends[i]);
    }

    private Work at(int i) {
        return new Work(lats[i], lons[i], kindNames[kinds[i]], starts[i], ends[i], names[i],
                links[i], estimated[i]);
    }

    private List<Integer> candidates(double lat, double lon, double km) {
        List<Integer> out = new ArrayList<>();
        int span = (int) Math.ceil(km / 111.0d);
        int cy = (int) Math.floor(lat);
        int cx = (int) Math.floor(lon);
        double dLat = km / 111.32d;
        for (int dy = -span; dy <= span; dy++) {
            for (int dx = -span; dx <= span; dx++) {
                int[] ids = cells.get(cellKey(cy + dy, cx + dx));
                if (ids == null) {
                    continue;
                }
                double dLon = km / (111.32d * Math.max(0.2d, Math.cos(Math.toRadians(lat))));
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

    private static WaysWorks load() {
        try (InputStream raw = WaysWorks.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                return empty(FILE + " is missing");
            }
            JsonObject o;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw, 1 << 16),
                    StandardCharsets.UTF_8)) {
                o = JsonParser.parseReader(r).getAsJsonObject();
            }
            JsonArray kindsArray = o.getAsJsonArray("kinds");
            String[] kindNames = new String[kindsArray.size()];
            Map<String, Integer> kindIndex = new HashMap<>();
            for (int i = 0; i < kindsArray.size(); i++) {
                kindNames[i] = kindsArray.get(i).getAsString();
                kindIndex.put(kindNames[i], i);
            }
            JsonArray works = o.getAsJsonArray("works");
            int n = works.size();
            double[] lats = new double[n];
            double[] lons = new double[n];
            short[] kinds = new short[n];
            int[] starts = new int[n];
            int[] ends = new int[n];
            String[] names = new String[n];
            short[] links = new short[n];
            boolean[] est = new boolean[n];
            Map<Integer, List<Integer>> cellList = new HashMap<>();
            int i = 0;
            for (JsonElement e : works) {
                JsonArray a = e.getAsJsonArray();
                lats[i] = a.get(0).getAsDouble();
                lons[i] = a.get(1).getAsDouble();
                kinds[i] = (short) a.get(2).getAsInt();
                starts[i] = a.get(3).getAsInt();
                if (a.get(4).isJsonNull()) {
                    ends[i] = NO_END;
                } else {
                    ends[i] = a.get(4).getAsInt();
                }
                names[i] = a.get(5).isJsonNull() ? null : a.get(5).getAsString();
                links[i] = (short) Math.min(Short.MAX_VALUE, a.get(6).getAsInt());
                est[i] = a.get(7).getAsInt() == 1;
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
            return new WaysWorks(lats, lons, kinds, starts, ends, names, links, est, kindNames,
                    cells, null);
        } catch (Exception e) { // noqa: the mod must run without this file
            return empty(FILE + ": " + e);
        }
    }

    private static WaysWorks empty(String problem) {
        return new WaysWorks(new double[0], new double[0], new short[0], new int[0], new int[0],
                new String[0], new short[0], new boolean[0], new String[0], new HashMap<>(), problem);
    }
}
