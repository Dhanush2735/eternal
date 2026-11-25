package com.eternalengine.civilization;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Step 34 (B12 roads, B14 railways, B15 waterways): <b>the year a network reached a square</b>,
 * which is the gate the whole step turns on.
 *
 * <p>Step 31 found the mod drawing today's world into the past: Sydney in 1850 with two railways,
 * though Australia's first opened in 1854; Patna in 300 BC with 98 modern roads. Wikidata can name
 * a station or a bridge and date it, but it cannot say that no railway ran through a given square
 * until 1880 — and without that, every road and railway the map generator draws from today's
 * OpenStreetMap is out of its time.
 *
 * <p>{@code history/ways.grid.gz} (built by {@code worldfill/build_ways_grid.py}) is a grid of 0.1
 * degrees, numbered exactly as {@code history/countries.grid.gz} is, so the two can be read
 * together. Each square holds the first year a railway, a dated road and a canal existed there, or
 * {@link #NONE} where nothing was ever recorded. Three sources are merged by the earliest year any
 * of them gives:
 *
 * <ul>
 *   <li><b>OpenHistoricalMap</b>: 233,930 railway ways, 314,140 highways opened before 1900 and
 *       10,277 canals, each with a {@code start_date}, fetched tile by tile and binned here.</li>
 *   <li><b>Itiner-e</b> (already in the mod as {@code trade_routes.json.gz}): 14,769 segments of
 *       Roman road, years -514 to 700. This is what carries the first millennium, because
 *       OpenHistoricalMap's dates before AD 1000 are not to be trusted — {@code 0060} may be AD 60
 *       or a mistyped 1060, and the gate must not draw a road in AD 60 on the strength of one.</li>
 *   <li><b>Wikidata</b>: the dated works of part 2 — a station opened in 1850 is proof of a railway
 *       in its square in 1850.</li>
 * </ul>
 *
 * <p>The rule for a caller: <b>before you draw a road, a railway or a canal here, ask this grid
 * what year it began</b>. Where the answer is {@link #NONE}, that is not permission to draw — it is
 * the opposite: nothing is known, so the thing should not be drawn on the strength of today's map.
 * Where the technology atlas says a technology has not reached this country yet, that answer wins
 * over everything here.
 *
 * <p>Loaded once, lazily, and kept. Nothing here touches Minecraft.
 */
public final class WaysGrid {

    public static final String FILE = "/data/eternalengine/eternal/history/ways.grid.gz";
    private static final int MAGIC = 0x57415953; // "WAYS"
    /** The value of a square where nothing was ever recorded. */
    public static final int NONE = -32768;

    private static volatile WaysGrid instance;

    private final int width;
    private final int height;
    /** Per row: run length and palette index, interleaved, as the country grid stores its own runs. */
    private final short[][] rows;
    /** Palette entries: {railFirst, roadFirst, canalFirst}. */
    private final short[][] palettes;
    private final int cellsWithSomething;
    private final String problem;

    private WaysGrid(int width, int height, short[][] rows, short[][] palettes, int cells, String problem) {
        this.width = width;
        this.height = height;
        this.rows = rows;
        this.palettes = palettes;
        this.cellsWithSomething = cells;
        this.problem = problem;
    }

    public static WaysGrid get() {
        WaysGrid g = instance;
        if (g == null) {
            synchronized (WaysGrid.class) {
                if (instance == null) {
                    instance = load();
                }
                g = instance;
            }
        }
        return g;
    }

    public boolean loaded() {
        return problem == null && rows.length > 0;
    }

    public String problem() {
        return problem;
    }

    /** How many of the 6,480,000 squares have anything recorded in them. */
    public int cellsWithSomething() {
        return cellsWithSomething;
    }

    /** The first year a railway existed in this square, or {@link #NONE}. */
    public int railFirst(double lat, double lon) {
        return year(lat, lon, 0);
    }

    /** The first year a dated road existed in this square, or {@link #NONE}. */
    public int roadFirst(double lat, double lon) {
        return year(lat, lon, 1);
    }

    /** The first year a canal existed in this square, or {@link #NONE}. */
    public int canalFirst(double lat, double lon) {
        return year(lat, lon, 2);
    }

    /** May a railway be drawn here in this year? */
    public boolean railBy(double lat, double lon, int year) {
        return by(lat, lon, year, 0);
    }

    /** May a dated road be drawn here in this year? */
    public boolean roadBy(double lat, double lon, int year) {
        return by(lat, lon, year, 1);
    }

    /** May a canal be drawn here in this year? */
    public boolean canalBy(double lat, double lon, int year) {
        return by(lat, lon, year, 2);
    }

    /**
     * The gate, in the words a caller wants: is there any record of this kind of network reaching
     * this square by this year? Where nothing is recorded the answer is {@code false} — a missing
     * year is not permission, it is absence.
     */
    public boolean by(double lat, double lon, int year, int which) {
        if (!loaded()) {
            return false;
        }
        int y = year(lat, lon, which);
        return y != NONE && y <= year;
    }

    private int year(double lat, double lon, int which) {
        if (!loaded()) {
            return NONE;
        }
        int x = clamp((int) Math.floor((lon + 180.0d) * 10.0d), width);
        int y = clamp((int) Math.floor((90.0d - lat) * 10.0d), height);
        return palettes[paletteAt(x, y)][which];
    }

    private int paletteAt(int x, int y) {
        short[] row = rows[y];
        int acc = 0;
        for (int k = 0; k + 1 < row.length; k += 2) {
            int len = row[k] & 0xFFFF;
            if (x < acc + len) {
                return row[k + 1] & 0xFFFF;
            }
            acc += len;
        }
        return 0;
    }

    private static int clamp(int v, int n) {
        return Math.max(0, Math.min(n - 1, v));
    }

    private static WaysGrid load() {
        try (InputStream raw = WaysGrid.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                return empty(FILE + " is missing");
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 1 << 16)));
            if (in.readInt() != MAGIC) {
                return empty(FILE + ": not a ways grid");
            }
            in.readInt();                       // version
            int w = in.readInt();
            int h = in.readInt();
            in.readFloat();                     // cell size
            int n = in.readInt();
            short[][] palettes = new short[Math.max(1, n)][3];
            for (int i = 0; i < n; i++) {
                palettes[i][0] = in.readShort();
                palettes[i][1] = in.readShort();
                palettes[i][2] = in.readShort();
            }
            short[][] rows = new short[h][];
            int cells = 0;
            for (int y = 0; y < h; y++) {
                int k = in.readInt();
                short[] row = new short[k * 2];
                for (int i = 0; i < k; i++) {
                    int len = in.readShort() & 0xFFFF;
                    int v = in.readShort() & 0xFFFF;
                    row[i * 2] = (short) len;
                    row[i * 2 + 1] = (short) v;
                    if (palettes[v][0] != NONE || palettes[v][1] != NONE || palettes[v][2] != NONE) {
                        cells += len;
                    }
                }
                rows[y] = row;
            }
            return new WaysGrid(w, h, rows, palettes, cells, null);
        } catch (Exception e) { // noqa: the mod must run without this file
            return empty(FILE + ": " + e);
        }
    }

    private static WaysGrid empty(String problem) {
        return new WaysGrid(1, 1, new short[1][], new short[1][3], 0, problem);
    }
}
