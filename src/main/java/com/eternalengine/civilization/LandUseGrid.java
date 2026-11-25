package com.eternalengine.civilization;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 35 (B3 land use): <b>what the land was being used for, at this place, in this year</b>.
 *
 * <p>The world can be set to any date, so the land cannot be one map: the same square that held a
 * rice village in 1900 was wild woodland in 3000 BCE, and the crops, the pasture and the timber a
 * settlement can draw on all hang on which it is. Step 31's complaint — the mod drawing today's
 * world into the past — applies to the land under everything as much as it did to the roads.
 *
 * <p>{@code history/land_use.grid.gz} (built by {@code worldfill/build_land_use.py} from Anthromes
 * 12K, Ellis, Beusen and Klein Goldewijk 2020, CC0) is a grid of 0.1 degrees, 3600 x 1800,
 * numbered exactly as {@code history/countries.grid.gz} and {@code history/ways.grid.gz} are, so
 * one square means the same thing in all three. It holds 73 time steps from 10,000 BCE to 2015 CE
 * and 1,538,421 land cells; because 73 full maps would be 473 million numbers, the pack is the
 * earliest map plus the 2,393,643 cells that ever changed, which is 1.6 changes per land cell
 * across the whole of human history.
 *
 * <p><b>The rule for a caller: ask this before you decide what the ground is.</b> A square the
 * grid says is wildland in 3000 BCE must not be given a ploughed field; a square it says is
 * cropland in 1900 must not be given old growth. Where the year asked for is before the first
 * step, the first step is used; after the last, the last is used — the pack is a reconstruction
 * with ends, and 2015 is its end.
 *
 * <p>The map for a step is built once and kept; three steps are cached, because a tick that walks
 * the world asks about one year many times and about many years rarely.
 */
public final class LandUseGrid {

    public static final String FILE = "/data/eternalengine/eternal/history/land_use.grid.gz";
    private static final int MAGIC = 0x45454C55; // "EELU"

    /** Palette index of a square that is not land: ocean, or outside the reconstruction. */
    public static final int OCEAN = 0;

    private static final int CACHED_STEPS = 3;

    private static volatile LandUseGrid instance;

    private final int width;
    private final int height;
    /** Per row: run length and palette index, interleaved, as the ways grid stores its own runs. */
    private final short[][] rows;
    /** The year of each step, rising. */
    private final int[] years;
    private final List<LandClass> palette;
    /** Per step after the first: the cells that changed, in rising order, and what they became. */
    private final int[][] diffCells;
    private final byte[][] diffValues;
    private final int landCells;
    private final int totalChanges;
    private final String problem;

    /** One class of land: the Anthromes code, its name, its level and the one-word answer. */
    public record LandClass(int code, String name, String level, String shortName) {
        public boolean isSettled() {
            return "settlement".equals(shortName) || "village".equals(shortName);
        }

        public boolean isFarmed() {
            return "cropland".equals(shortName) || isSettled();
        }

        public boolean isPasture() {
            return "rangeland".equals(shortName);
        }

        public boolean isWild() {
            return "wildland".equals(shortName) || "woodland".equals(shortName);
        }
    }

    private LandUseGrid(int width, int height, short[][] rows, int[] years, List<LandClass> palette,
                        int[][] diffCells, byte[][] diffValues, int landCells, int totalChanges,
                        String problem) {
        this.width = width;
        this.height = height;
        this.rows = rows;
        this.years = years;
        this.palette = List.copyOf(palette);
        this.diffCells = diffCells;
        this.diffValues = diffValues;
        this.landCells = landCells;
        this.totalChanges = totalChanges;
        this.problem = problem;
        this.baseMap = problem == null && rows.length > 0 ? buildBaseMap() : new byte[0];
    }

    private byte[] buildBaseMap() {
        byte[] map = new byte[width * height];
        for (int y = 0; y < height && y < rows.length; y++) {
            short[] row = rows[y];
            int x = 0;
            for (int k = 0; k + 1 < row.length; k += 2) {
                int len = row[k] & 0xFFFF;
                byte v = (byte) (row[k + 1] & 0xFF);
                int end = Math.min(width, x + len);
                java.util.Arrays.fill(map, y * width + x, y * width + end, v);
                x = end;
            }
        }
        return map;
    }

    public static LandUseGrid get() {
        LandUseGrid g = instance;
        if (g == null) {
            synchronized (LandUseGrid.class) {
                if (instance == null) {
                    instance = load();
                }
                g = instance;
            }
        }
        return g;
    }

    public boolean loaded() {
        return problem == null && rows.length > 0 && years.length > 0;
    }

    public String problem() {
        return problem;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The 73 years the reconstruction actually has a map for. */
    public int[] years() {
        return years.clone();
    }

    public List<LandClass> palette() {
        return palette;
    }

    public int landCells() {
        return landCells;
    }

    /** How many cells changed across all 12,000 years: 1.6 per land cell. */
    public int totalChanges() {
        return totalChanges;
    }

    /** The step whose year is the latest one at or before this year. */
    public int stepForYear(int year) {
        if (years.length == 0) {
            return 0;
        }
        if (year <= years[0]) {
            return 0;
        }
        int lo = 0;
        int hi = years.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (years[mid] <= year) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** The palette index of this square in this year: 0 is ocean. */
    public int classAt(double lat, double lon, int year) {
        if (!loaded()) {
            return OCEAN;
        }
        int x = clamp((int) Math.floor((lon + 180.0d) * 10.0d), width);
        int y = clamp((int) Math.floor((90.0d - lat) * 10.0d), height);
        int cell = y * width + x;
        int step = stepForYear(year);
        byte[] map = cachedMap(step);
        return map != null ? (map[cell] & 0xFF) : slowPath(cell, step);
    }

    /** What the land was, in one word: town, village, cropland, pasture, woodland, wildland, ice. */
    public String shortAt(double lat, double lon, int year) {
        LandClass c = landClass(lat, lon, year);
        return c == null ? "ocean" : c.shortName();
    }

    /** The level it belongs to, as Anthromes names it: "Croplands", "Wildlands", and so on. */
    public String levelAt(double lat, double lon, int year) {
        LandClass c = landClass(lat, lon, year);
        return c == null ? "Ocean or no data" : c.level();
    }

    /** The Anthromes class name, e.g. "Residential rainfed croplands". */
    public String nameAt(double lat, double lon, int year) {
        LandClass c = landClass(lat, lon, year);
        return c == null ? "Ocean or no data" : c.name();
    }

    public LandClass landClass(double lat, double lon, int year) {
        if (!loaded()) {
            return null;
        }
        int i = classAt(lat, lon, year);
        return i <= 0 || i >= palette.size() ? null : palette.get(i);
    }

    /** May this square be given a field, an orchard or a rice terrace in this year? */
    public boolean farmedBy(double lat, double lon, int year) {
        LandClass c = landClass(lat, lon, year);
        return c != null && c.isFarmed();
    }

    /** May livestock be grazed here in this year? */
    public boolean grazedBy(double lat, double lon, int year) {
        LandClass c = landClass(lat, lon, year);
        return c != null && (c.isPasture() || c.isFarmed());
    }

    /** Is this still wild country in this year — hunting, foraging, timber? */
    public boolean wildBy(double lat, double lon, int year) {
        LandClass c = landClass(lat, lon, year);
        return c != null && c.isWild();
    }

    /** One sentence for a player standing here in this year. */
    public String describe(double lat, double lon, int year) {
        if (!loaded()) {
            return "";
        }
        int step = stepForYear(year);
        LandClass c = landClass(lat, lon, year);
        if (c == null) {
            return "Open water, or outside the reconstruction.";
        }
        return c.level() + " — " + c.name() + " (from the " + yearLabel(years[step])
                + " map, the nearest the reconstruction has)";
    }

    public static String yearLabel(int year) {
        return year < 0 ? (-year) + " BC" : "AD " + year;
    }

    // ------------------------------------------------------------------ the maps ----------

    /**
     * Two ways in, because callers come in two shapes. A caller walking the world in one year —
     * a tick, a map generator — wants the whole map of that year materialised once and then O(1)
     * per square; it calls {@link #prepare(int)} first. A caller asking about scattered years —
     * a command, a timeline — must not pay for 6,480,000 squares to answer one, so it takes the
     * binary-search path below, which costs about a microsecond and no memory at all.
     *
     * <p>Both read the same base map, which is materialised once at load and never thrown away.
     */
    private final byte[] baseMap;

    private final Map<Integer, byte[]> cache = new LinkedHashMap<Integer, byte[]>(
            CACHED_STEPS + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
            return size() > CACHED_STEPS;
        }
    };

    /** Build the whole map of this year and keep it, so that bulk work in one year is O(1) a square. */
    public void prepare(int year) {
        if (loaded()) {
            mapForStep(stepForYear(year));
        }
    }

    /** The map of one step, or null when it has not been materialised. */
    private synchronized byte[] cachedMap(int step) {
        return cache.get(step);
    }

    private synchronized byte[] mapForStep(int step) {
        byte[] hit = cache.get(step);
        if (hit != null) {
            return hit;
        }
        int from = -1;
        byte[] base = null;
        for (Map.Entry<Integer, byte[]> e : cache.entrySet()) {
            if (e.getKey() <= step && (from < 0 || e.getKey() > from)) {
                from = e.getKey();
                base = e.getValue();
            }
        }
        byte[] map = base == null ? baseMap.clone() : base.clone();
        // The diffs are indexed by step - 1, so there is nothing to apply for step 0.
        for (int s = Math.max(1, from + 1); s <= step; s++) {
            int[] cells = diffCells[s - 1];
            byte[] vals = diffValues[s - 1];
            for (int i = 0; i < cells.length; i++) {
                map[cells[i]] = vals[i];
            }
        }
        cache.put(step, map);
        return map;
    }

    /**
     * A step earns its own map only after it has been asked about {@value #MATERIALISE_AFTER}
     * times in a row. Scattered questions about scattered years — a command, a timeline — never
     * reach that and stay on the cheap path; a tick or a generator walking one year crosses it
     * within a millisecond and gets O(1) per square for the rest of its run.
     */
    private static final int MATERIALISE_AFTER = 256;
    private int hotStep = -1;
    private int hotCount = 0;

    private int slowPath(int cell, int step) {
        int v = valueAtCell(cell, step);
        if (noteQuery(step)) {
            mapForStep(step);
        }
        return v;
    }

    private synchronized boolean noteQuery(int step) {
        if (step != hotStep) {
            hotStep = step;
            hotCount = 0;
            return false;
        }
        return ++hotCount >= MATERIALISE_AFTER;
    }

    /**
     * One square in one step, without materialising anything: take its value in the earliest map
     * and then walk the change lists forward, binary-searching each for this cell. There are 72
     * of them and each holds about 33,000 cells, so this is a few hundred comparisons — about a
     * microsecond — against the several milliseconds a rebuild costs.
     */
    private int valueAtCell(int cell, int step) {
        int v = baseMap[cell] & 0xFF;
        for (int s = 1; s <= step; s++) {
            int[] cells = diffCells[s - 1];
            int i = java.util.Arrays.binarySearch(cells, cell);
            if (i >= 0) {
                v = diffValues[s - 1][i] & 0xFF;
            }
        }
        return v;
    }

    private static int clamp(int v, int n) {
        return Math.max(0, Math.min(n - 1, v));
    }

    // ------------------------------------------------------------------ the load ----------

    private static LandUseGrid load() {
        try (InputStream raw = LandUseGrid.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                return empty(FILE + " is missing");
            }
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(new GZIPInputStream(raw, 1 << 16)));
            if (in.readInt() != MAGIC) {
                return empty(FILE + ": not a land use grid");
            }
            in.readInt();                                   // version
            int w = in.readInt();
            int h = in.readInt();
            if (w <= 0 || h <= 0 || w > 7200 || h > 3600) {
                return empty(FILE + ": implausible size " + w + "x" + h);
            }
            int nSteps = in.readInt();
            if (nSteps <= 0 || nSteps > 4096) {
                return empty(FILE + ": implausible step count " + nSteps);
            }
            int[] years = new int[nSteps];
            for (int i = 0; i < nSteps; i++) {
                years[i] = in.readInt();
            }
            int nPal = in.readInt();
            if (nPal <= 0 || nPal > 4096) {
                return empty(FILE + ": implausible palette " + nPal);
            }
            List<LandClass> pal = new ArrayList<>(nPal);
            for (int i = 0; i < nPal; i++) {
                int code = in.readInt();
                // Written as a two-byte byte length followed by plain UTF-8, which is read here
                // by hand rather than with readUTF so that a class name with an accent in it
                // cannot be misread as modified UTF-8.
                pal.add(new LandClass(code, readString(in), readString(in), readString(in)));
            }
            int land = in.readInt();
            int total = in.readInt();

            short[][] rows = new short[h][];
            for (int y = 0; y < h; y++) {
                int k = in.readInt();
                short[] row = new short[k * 2];
                for (int i = 0; i < k; i++) {
                    int len = in.readShort() & 0xFFFF;
                    int v = in.readByte() & 0xFF;
                    row[i * 2] = (short) len;
                    row[i * 2 + 1] = (short) v;
                }
                rows[y] = row;
            }

            int[][] diffCells = new int[nSteps - 1][];
            byte[][] diffValues = new byte[nSteps - 1][];
            for (int s = 1; s < nSteps; s++) {
                int n = in.readInt();
                int[] cells = new int[n];
                int prev = -1;
                for (int i = 0; i < n; i++) {
                    int gap = readVarint(in);
                    int cell = prev < 0 ? gap : prev + 1 + gap;
                    cells[i] = cell;
                    prev = cell;
                }
                byte[] vals = new byte[n];
                in.readFully(vals);
                diffCells[s - 1] = cells;
                diffValues[s - 1] = vals;
            }
            return new LandUseGrid(w, h, rows, years, pal, diffCells, diffValues, land, total, null);
        } catch (Exception e) { // the mod must run without this file
            return empty(FILE + ": " + e);
        }
    }

    private static String readString(DataInputStream in) throws java.io.IOException {
        int n = in.readShort() & 0xFFFF;
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int readVarint(DataInputStream in) throws java.io.IOException {
        int shift = 0;
        int value = 0;
        while (shift < 35) {
            int b = in.readByte() & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
        throw new java.io.IOException("varint too long");
    }

    private static LandUseGrid empty(String problem) {
        return new LandUseGrid(1, 1, new short[1][], new int[0], List.of(),
                new int[0][], new byte[0][], 0, 0, problem);
    }
}
