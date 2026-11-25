package com.eternalengine.civilization;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Step 30a (foundation for C16 and later steps): which country of today a point lies in, anywhere on Earth.
 *
 * <p>{@code history/countries.grid.gz} (built by {@code worldfill/build_country_grid.py} from Natural Earth's
 * countries, public domain) is a grid of 0.1 degrees (about 11 km): 3,600 x 1,800 squares, stored row by row as runs.
 * A point at sea takes the nearest land within about 35 km (a coastal town whose square is mostly water). The
 * technology atlas keeps the year each technology reached each of these 258 countries.
 */
public final class CountryGrid {

    public static final String FILE = "/data/eternalengine/eternal/history/countries.grid.gz";
    private static final int MAGIC = 0x45454347;
    private static volatile CountryGrid instance;

    private final int width;
    private final int height;
    /** Per row: run lengths and values, interleaved (value 0 = sea, else 1 + country). */
    private final short[][] rows;
    private final String[] codes;
    private final String[] names;
    private final String[] subregions;
    private final float[] mainLat;
    private final float[] mainLon;
    private final String problem;

    private CountryGrid(int width, int height, short[][] rows, String[] codes, String[] names, String[] subregions,
                        float[] mainLat, float[] mainLon, String problem) {
        this.width = width;
        this.height = height;
        this.rows = rows;
        this.codes = codes;
        this.names = names;
        this.subregions = subregions;
        this.mainLat = mainLat;
        this.mainLon = mainLon;
        this.problem = problem;
    }

    public static CountryGrid get() {
        CountryGrid g = instance;
        if (g == null) {
            synchronized (CountryGrid.class) {
                if (instance == null) {
                    instance = load();
                }
                g = instance;
            }
        }
        return g;
    }

    public boolean loaded() {
        return problem == null && codes.length > 0;
    }

    public String problem() {
        return problem;
    }

    public int size() {
        return codes.length;
    }

    public String code(int i) {
        return i < 0 || i >= codes.length ? "" : codes[i];
    }

    public String name(int i) {
        return i < 0 || i >= names.length ? "" : names[i];
    }

    public String subregion(int i) {
        return i < 0 || i >= subregions.length ? "" : subregions[i];
    }

    /** The country's biggest town (of the town list), where a technology spreads from unless a record says where. */
    public double mainLat(int i) {
        return mainLat[i];
    }

    public double mainLon(int i) {
        return mainLon[i];
    }

    public int indexOf(String code) {
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(code)) {
                return i;
            }
        }
        return -1;
    }

    /** The country of a point: its own square, else the nearest land square within 3 squares; -1 out at sea. */
    public int indexAt(double lat, double lon) {
        if (!loaded()) {
            return -1;
        }
        int x = clamp((int) Math.floor((lon + 180.0d) * 10.0d), width);
        int y = clamp((int) Math.floor((90.0d - lat) * 10.0d), height);
        int v = valueAt(x, y);
        if (v > 0) {
            return v - 1;
        }
        for (int r = 1; r <= 3; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int yy = y + dy;
                    if (yy < 0 || yy >= height) {
                        continue;
                    }
                    int xx = Math.floorMod(x + dx, width);
                    int w = valueAt(xx, yy);
                    if (w > 0) {
                        return w - 1;
                    }
                }
            }
        }
        return -1;
    }

    /** Whether the sea is within about {@code squares} squares of 11 km (a port town). */
    public boolean nearSea(double lat, double lon, int squares) {
        if (!loaded()) {
            return true;
        }
        int x = clamp((int) Math.floor((lon + 180.0d) * 10.0d), width);
        int y = clamp((int) Math.floor((90.0d - lat) * 10.0d), height);
        for (int dy = -squares; dy <= squares; dy++) {
            int yy = y + dy;
            if (yy < 0 || yy >= height) {
                continue;
            }
            for (int dx = -squares; dx <= squares; dx++) {
                if (valueAt(Math.floorMod(x + dx, width), yy) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public String codeAt(double lat, double lon) {
        return code(indexAt(lat, lon));
    }

    private int valueAt(int x, int y) {
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

    private static CountryGrid load() {
        try (InputStream raw = CountryGrid.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                return empty(FILE + " is missing");
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 1 << 16)));
            if (in.readInt() != MAGIC) {
                return empty(FILE + ": not a country grid");
            }
            in.readInt(); // version
            int w = in.readInt();
            int h = in.readInt();
            int n = in.readInt();
            String[] codes = new String[n];
            String[] names = new String[n];
            String[] subs = new String[n];
            float[] mla = new float[n];
            float[] mlo = new float[n];
            for (int i = 0; i < n; i++) {
                codes[i] = in.readUTF();
                names[i] = in.readUTF();
                subs[i] = in.readUTF();
                in.readFloat();
                in.readFloat();
                mla[i] = in.readFloat();
                mlo[i] = in.readFloat();
                in.readInt();
            }
            short[][] rows = new short[h][];
            for (int y = 0; y < h; y++) {
                int k = in.readInt();
                short[] row = new short[2 * k];
                for (int j = 0; j < k; j++) {
                    row[2 * j] = in.readShort();
                    row[2 * j + 1] = in.readShort();
                }
                rows[y] = row;
            }
            return new CountryGrid(w, h, rows, codes, names, subs, mla, mlo, null);
        } catch (Exception e) {
            return empty("cannot read " + FILE + ": " + e);
        }
    }

    private static CountryGrid empty(String problem) {
        return new CountryGrid(1, 1, new short[][]{new short[0]}, new String[0], new String[0], new String[0],
                new float[0], new float[0], problem);
    }
}
