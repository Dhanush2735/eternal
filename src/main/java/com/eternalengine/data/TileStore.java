package com.eternalengine.data;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Step 26 (system A4): data that streams by region and era.
 *
 * <p>A tile file (built by {@code worldfill/build_tiles.py}) cuts a data set into groups by region (a state, or a box of
 * the map) and, inside each group, into tiles by era (centuries). Each tile is compressed on its own. Only this small
 * compressed file stays in memory; a tile is opened when a question needs that place and era, and kept in the shared
 * {@link TileCache} under its size budget, so the places and years near the players stay open and the rest of the
 * world costs nothing.
 *
 * <p>File layout (big-endian): {@code "EETILES1"}, dataset, master hash, rows, metadata, the group index (each tile:
 * first and last bucket, offset, compressed length, raw length, rows), then the compressed tiles. Strings are an
 * unsigned 16-bit length and UTF-8 bytes.
 */
public final class TileStore {

    /** One tile: buckets {@code first..last} (centuries) of a group, where its bytes are, and how many rows. */
    public record Tile(String group, int first, int last, int offset, int length, int rawLength, int rows) {
        public boolean overlaps(int fromBucket, int toBucket) {
            return last >= fromBucket && first <= toBucket;
        }
    }

    private static final byte[] MAGIC = "EETILES1".getBytes(StandardCharsets.US_ASCII);
    private static final Tile[] NONE = new Tile[0];

    private final String dataset;
    private final String masterHash;
    private final int rows;
    private final Map<String, String> meta;
    private final Map<String, Tile[]> groups;
    private final byte[] blob;
    private final int tileCount;
    private final long rawBytes;

    private TileStore(String dataset, String masterHash, int rows, Map<String, String> meta, Map<String, Tile[]> groups,
                      byte[] blob) {
        this.dataset = dataset;
        this.masterHash = masterHash;
        this.rows = rows;
        this.meta = meta;
        this.groups = groups;
        this.blob = blob;
        int n = 0;
        long raw = 0;
        for (Tile[] ts : groups.values()) {
            n += ts.length;
            for (Tile t : ts) {
                raw += t.rawLength();
            }
        }
        this.tileCount = n;
        this.rawBytes = raw;
    }

    public static TileStore read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16));
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!java.util.Arrays.equals(magic, MAGIC)) {
            throw new IOException("not a tile file (no EETILES1 header)");
        }
        String dataset = str(in);
        String hash = str(in);
        int rows = in.readInt();
        int metas = in.readInt();
        Map<String, String> meta = new LinkedHashMap<>();
        for (int i = 0; i < metas; i++) {
            meta.put(str(in), str(in));
        }
        int groupCount = in.readInt();
        Map<String, Tile[]> groups = new java.util.HashMap<>(groupCount * 2);
        for (int g = 0; g < groupCount; g++) {
            String name = str(in);
            int k = in.readInt();
            Tile[] ts = new Tile[k];
            for (int i = 0; i < k; i++) {
                ts[i] = new Tile(name, in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
            }
            groups.put(name, ts);
        }
        int len = in.readInt();
        byte[] blob = new byte[len];
        in.readFully(blob);
        return new TileStore(dataset, hash, rows, Collections.unmodifiableMap(meta), Map.copyOf(groups), blob);
    }

    static String str(DataInputStream in) throws IOException {
        int n = in.readUnsignedShort();
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    /** All tiles of a group, in bucket order (empty for an unknown group). */
    public Tile[] tiles(String group) {
        Tile[] ts = groups.get(group);
        return ts == null ? NONE : ts;
    }

    /** The tiles of a group that hold any bucket in {@code fromBucket..toBucket}, in bucket order. */
    public List<Tile> tiles(String group, int fromBucket, int toBucket) {
        Tile[] ts = groups.get(group);
        if (ts == null) {
            return List.of();
        }
        List<Tile> out = new ArrayList<>(2);
        for (Tile t : ts) {
            if (t.overlaps(fromBucket, toBucket)) {
                out.add(t);
            }
        }
        return out;
    }

    public boolean has(String group) {
        return groups.containsKey(group);
    }

    /** The raw bytes of one tile. */
    public byte[] inflate(Tile t) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(blob, t.offset(), t.length());
            byte[] out = new byte[t.rawLength()];
            int n = 0;
            while (n < out.length && !inf.finished()) {
                int k = inf.inflate(out, n, out.length - n);
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }
                n += k;
            }
            if (n != out.length) {
                throw new IllegalStateException(dataset + " tile " + t.group() + "/" + t.first() + " is broken ("
                        + n + " of " + out.length + " bytes)");
            }
            return out;
        } catch (DataFormatException e) {
            throw new IllegalStateException(dataset + " tile " + t.group() + "/" + t.first() + " is broken: "
                    + e.getMessage(), e);
        } finally {
            inf.end();
        }
    }

    public Set<String> groupNames() {
        return groups.keySet();
    }

    public String dataset() {
        return dataset;
    }

    public String masterHash() {
        return masterHash;
    }

    public int rows() {
        return rows;
    }

    public int groupCount() {
        return groups.size();
    }

    public int tileCount() {
        return tileCount;
    }

    /** What stays in memory: the compressed tiles. */
    public long compressedBytes() {
        return blob.length;
    }

    /** What all the tiles would take opened at once (raw bytes, before they become objects). */
    public long rawBytes() {
        return rawBytes;
    }

    public String meta(String key, String fallback) {
        return meta.getOrDefault(key, fallback);
    }

    public int metaInt(String key, int fallback) {
        try {
            return Integer.parseInt(meta.getOrDefault(key, ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
