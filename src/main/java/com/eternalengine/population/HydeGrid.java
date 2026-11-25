package com.eternalengine.population;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * How many people lived around a point in a year, from the HYDE 3.2.1 population model.
 *
 * <h3>What it reads</h3>
 * {@code population/hyde_density.bin.gz}: people per km2 of land for every 0.5-degree land cell
 * at 28 time slices from 3000 BC to AD 2017 (HYDE 3.2.1, Klein Goldewijk et al. 2017, DANS
 * doi:10.17026/DANS-25G-GEZ3; built by {@code worldfill/build_hyde_resource.py}). Densities are
 * stored as 8-bit logarithmic codes (40 steps per factor of ten).
 *
 * <h3>What it answers</h3>
 * {@link #density} interpolates log-linearly between the two slices around the year (before the
 * first slice it holds the first, after the last it holds the last). {@link #reading} also says
 * whether anyone is here: at or above {@link #FULL_DENSITY} people per km2 always; below that, a
 * fixed share of 0.05-degree tiles equal to {@code density / FULL_DENSITY}, chosen by a hash of
 * the tile, so thinly settled land has people in some places and not others, the same places on
 * every machine and every visit. Below {@link #MIN_DENSITY} nobody is here.
 *
 * <p>This is a model, not a record: every answer is labelled {@code estimated}. It is the T4
 * GRIDDED tier of {@link PopulationResolver} and the last presence layer of
 * {@code HistoricalPresence}, used only where nothing sourced, dated or curated answers first.
 */
public final class HydeGrid {

    public static final String RESOURCE = "/data/eternalengine/eternal/population/hyde_density.bin.gz";

    /** People per km2 at which every tile of the cell has people. */
    public static final double FULL_DENSITY = 2.0d;

    /** Below this many people per km2, nobody is here. */
    public static final double MIN_DENSITY = 0.01d;

    /** Tile edge, in degrees, for the share-of-tiles rule. About 5.5 km north-south. */
    public static final double TILE_DEGREES = 0.05d;

    public static final String SOURCE = "HYDE 3.2.1 population model, 0.5-degree cell";

    /**
     * One answer.
     *
     * @param density    people per km2 of land, interpolated to the year
     * @param share      the share of tiles in this cell that have people, 0..1
     * @param present    whether this point's tile is one of them
     * @param fromYear   the slice at or before the year
     * @param toYear     the slice at or after the year
     */
    public record Reading(double density, double share, boolean present, int fromYear, int toYear) {
        public String display() {
            String years = fromYear == toYear ? Integer.toString(fromYear) : fromYear + ".." + toYear;
            return String.format(java.util.Locale.ROOT, "~%s people/km2 (%s, slices %s)",
                    density >= 10 ? String.format(java.util.Locale.ROOT, "%.0f", density)
                            : String.format(java.util.Locale.ROOT, "%.2f", density),
                    SOURCE, years);
        }
    }

    private static final HydeGrid INSTANCE = new HydeGrid();

    private boolean loaded;
    private String source = "not loaded";
    private double cell = 0.5d;
    private int cols = 720;
    private int[] years = new int[0];
    /** codes[slice][row * cols + col]; 0 = nobody. */
    private byte[][] codes = new byte[0][];

    private HydeGrid() {}

    public static HydeGrid get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** A grid read from an explicit file - for checks and tools, never for the game. */
    public static HydeGrid fromFile(Path file) {
        HydeGrid g = new HydeGrid();
        g.loaded = true;
        try (InputStream in = Files.newInputStream(file)) {
            g.read(in);
            g.source = file.toString();
        } catch (Exception e) {
            g.source = file + " failed: " + e.getMessage();
        }
        return g;
    }

    public boolean isLoaded() {
        return years.length > 0;
    }

    public String source() {
        return source;
    }

    public int sliceCount() {
        return years.length;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = HydeGrid.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            read(in);
            source = RESOURCE;
        } catch (Exception e) {
            years = new int[0];
            codes = new byte[0][];
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
        }
    }

    private void read(InputStream raw) throws java.io.IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!"HYD1".equals(new String(magic, StandardCharsets.US_ASCII))) {
                throw new java.io.IOException("not a HYDE grid (magic " + new String(magic, StandardCharsets.US_ASCII) + ")");
            }
            double cellDegrees = in.readInt() / 1000.0d;
            int slices = in.readInt();
            int[] ys = new int[slices];
            for (int i = 0; i < slices; i++) {
                ys[i] = in.readInt();
            }
            int rows = (int) Math.round(180.0d / cellDegrees);
            int nc = (int) Math.round(360.0d / cellDegrees);
            byte[][] cs = new byte[slices][rows * nc];
            int cells = in.readInt();
            byte[] buf = new byte[slices];
            for (int c = 0; c < cells; c++) {
                int index = in.readInt();
                in.readFully(buf);
                if (index < 0 || index >= rows * nc) {
                    throw new java.io.IOException("cell index out of range: " + index);
                }
                for (int s = 0; s < slices; s++) {
                    cs[s][index] = buf[s];
                }
            }
            this.cell = cellDegrees;
            this.cols = nc;
            this.years = ys;
            this.codes = cs;
        }
    }

    private static double decode(byte code) {
        int c = code & 0xFF;
        return c == 0 ? 0.0d : Math.pow(10.0d, (c - 1) / 40.0d - 2.3d);
    }

    private int index(double lat, double lon) {
        int rows = codes.length == 0 ? 0 : codes[0].length / cols;
        int r = (int) Math.floor((lat + 90.0d) / cell);
        double l = lon;
        while (l < -180.0d) {
            l += 360.0d;
        }
        while (l >= 180.0d) {
            l -= 360.0d;
        }
        int c = (int) Math.floor((l + 180.0d) / cell);
        if (r < 0 || r >= rows || c < 0 || c >= cols) {
            return -1;
        }
        return r * cols + c;
    }

    /** People per km2 of land around this point in this year, if the grid has the cell. */
    public Optional<Double> density(double lat, double lon, int year) {
        return reading(lat, lon, year).map(Reading::density);
    }

    /** Density, share of inhabited tiles and whether this point's tile is inhabited. */
    public Optional<Reading> reading(double lat, double lon, int year) {
        if (!isLoaded()) {
            return Optional.empty();
        }
        int i = index(lat, lon);
        if (i < 0) {
            return Optional.empty();
        }
        int hi = 0;
        while (hi < years.length && years[hi] < year) {
            hi++;
        }
        int a;
        int b;
        if (hi == 0) {
            a = 0;
            b = 0;
        } else if (hi == years.length) {
            a = years.length - 1;
            b = a;
        } else if (years[hi] == year) {
            a = hi;
            b = hi;
        } else {
            a = hi - 1;
            b = hi;
        }
        double da = decode(codes[a][i]);
        double db = decode(codes[b][i]);
        double d;
        if (a == b) {
            d = da;
        } else {
            double t = (year - years[a]) / (double) (years[b] - years[a]);
            d = (da > 0 && db > 0)
                    ? Math.exp(Math.log(da) + (Math.log(db) - Math.log(da)) * t)
                    : da + (db - da) * t;
        }
        double share = d < MIN_DENSITY ? 0.0d : Math.min(1.0d, d / FULL_DENSITY);
        boolean present = share >= 1.0d || (share > 0.0d && tileFraction(lat, lon) < share);
        return Optional.of(new Reading(d, share, present, years[a], years[b]));
    }

    /** Stable 0..1 for the 0.05-degree tile holding the point; written out so every JVM agrees. */
    static double tileFraction(double lat, double lon) {
        long ty = (long) Math.floor((lat + 90.0d) / TILE_DEGREES);
        long tx = (long) Math.floor((lon + 180.0d) / TILE_DEGREES);
        long h = ty * 0x9E3779B97F4A7C15L + tx * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return (h >>> 11) / (double) (1L << 53);
    }
}
