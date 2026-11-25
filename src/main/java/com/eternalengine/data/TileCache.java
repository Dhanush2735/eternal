package com.eternalengine.data;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Step 26 (systems A4 and I6): the opened tiles of every tile file, least recently used first out, under one size
 * budget. A tile opened for a player's place and era stays while it is used; when the budget is full the tile used
 * longest ago is closed (it can be opened again from the compressed file at any time).
 *
 * <p>The budget is {@value #DEFAULT_BUDGET_MB} MB unless the server starts with
 * {@code -Deternalengine.tileBudgetMb=N}. Sizes are estimates of the opened objects (raw bytes x 3 plus a fixed cost
 * per row), counted the same way every time.
 */
public final class TileCache {

    public static final int DEFAULT_BUDGET_MB = 48;
    public static final TileCache SHARED = new TileCache(
            Math.max(1L, Long.getLong("eternalengine.tileBudgetMb", DEFAULT_BUDGET_MB)) * 1024L * 1024L);

    /** What the cache holds and has done. */
    public record Stats(long budgetBytes, long bytes, long peakBytes, int tiles, long hits, long misses,
                        long evictions) {
        public String display() {
            return String.format(java.util.Locale.ROOT,
                    "tile cache: %d tiles open, %.1f of %.0f MB (peak %.1f MB); %d hits, %d opened, %d closed",
                    tiles, bytes / 1048576.0d, budgetBytes / 1048576.0d, peakBytes / 1048576.0d, hits, misses,
                    evictions);
        }
    }

    private record Entry(Object value, long bytes) {}

    private final long budget;
    private final LinkedHashMap<String, Entry> map = new LinkedHashMap<>(256, 0.75f, true);
    private long bytes;
    private long peak;
    private long hits;
    private long misses;
    private long evictions;

    public TileCache(long budgetBytes) {
        this.budget = budgetBytes;
    }

    /** The estimated memory of an opened tile: raw bytes x 3 plus 64 bytes per row. */
    public static long estimate(TileStore.Tile t) {
        return t.rawLength() * 3L + t.rows() * 64L;
    }

    /** The opened tile under this key, opening it with {@code open} if it is not open yet. */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, long estimatedBytes, Supplier<T> open) {
        synchronized (this) {
            Entry e = map.get(key);
            if (e != null) {
                hits++;
                return (T) e.value();
            }
        }
        T value = open.get();       // opened outside the lock: other threads keep reading open tiles
        synchronized (this) {
            Entry e = map.get(key);
            if (e != null) {        // another thread opened it meanwhile: keep that one
                hits++;
                return (T) e.value();
            }
            misses++;
            map.put(key, new Entry(value, estimatedBytes));
            bytes += estimatedBytes;
            Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator();
            while (bytes > budget && map.size() > 1 && it.hasNext()) {
                Map.Entry<String, Entry> eldest = it.next();
                if (eldest.getKey().equals(key)) {
                    continue;
                }
                bytes -= eldest.getValue().bytes();
                it.remove();
                evictions++;
            }
            peak = Math.max(peak, bytes);
            return value;
        }
    }

    public synchronized Stats stats() {
        return new Stats(budget, bytes, peak, map.size(), hits, misses, evictions);
    }

    /** Close every tile (a data reload, or a check that wants a cold start). */
    public synchronized void clear() {
        map.clear();
        bytes = 0;
    }

    public long budgetBytes() {
        return budget;
    }
}
