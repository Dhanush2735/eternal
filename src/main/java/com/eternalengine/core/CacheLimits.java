package com.eternalengine.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Disk use: saves that grow as chunks are rebuilt, and caches with limits.</b> I11.
 *
 * <h2>Why this class exists</h2>
 *
 * I11: "world saves grow as chunks are rebuilt for many years; cache limits". Step 26 measured the data
 * side (128 MB of registers, 64 MB of budget); the save side had no budget at all. Every time a player
 * jumps, Terra++ rebuilds the loaded chunks (step 57's wait), and a save that keeps every rebuilt year
 * of every chunk grows without end. Nothing drops anything silently here: a cache that evicts <b>counts
 * what it dropped</b>, and the save's years are checked against a named budget.
 *
 * <h2>The two limits</h2>
 *
 * <ol>
 *   <li><b>A cache has bytes</b>, and the ceiling is step 26's measured budget (64 MB, shared by every
 *       cache). An entry bigger than the whole cache is refused rather than evicting everything for it;
 *       past the ceiling the <b>oldest entry goes first</b> (a cache is for what is coming, not for what
 *       has been) and the drop is counted.</li>
 *   <li><b>A chunk keeps twelve rebuilt years</b> (a game default, named as such). Past that the oldest
 *       year's copy is the one that goes, and the save's line says so.</li>
 * </ol>
 */
public final class CacheLimits {

    /** The shared cache ceiling: step 26's data budget. */
    public static final long CACHE_BYTES = (long) PerfBudget.CACHE_BYTES.limit();

    /** How many rebuilt years one chunk keeps: a game default. */
    public static final int MAX_REBUILD_YEARS = 12;

    /** <b>One entry.</b> */
    public record Entry(String name, long bytes) {

        public Entry {
            name = name == null ? "" : name.trim();
        }
    }

    /** <b>What putting an entry did</b>: nothing dropped, or how many went and their bytes. */
    public record Added(Entry entry, int droppedNow, long droppedBytes, String refused) {
        public boolean ok() {
            return entry != null;
        }
    }

    /** <b>A named cache with a byte limit.</b> Oldest-first eviction, every drop counted. */
    public static final class Cache {
        private final String name;
        private final long limit;
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private long used = 0;
        private int dropped = 0;
        private long droppedBytes = 0;

        public Cache(String name, long limit) {
            this.name = name == null ? "" : name.trim();
            this.limit = limit;
        }

        /** Put an entry. Refused: a name of nothing, a size of nothing or less, and an entry bigger
         * than the whole cache (evicting everything for one entry is not caching, it is losing). */
        public synchronized Added put(String entryName, long bytes) {
            if (entryName == null || entryName.trim().isEmpty()) {
                return new Added(null, 0, 0, "an entry with no name is an entry nobody can evict");
            }
            if (bytes <= 0) {
                return new Added(null, 0, 0, "an entry of " + bytes + " bytes is nothing");
            }
            if (bytes > limit) {
                return new Added(null, 0, 0, "\"" + entryName.trim() + "\" is " + bytes + " bytes and "
                        + "the cache holds " + limit + ": an entry bigger than the whole cache is "
                        + "refused, because evicting everything for one entry is not caching, it is "
                        + "losing");
            }
            int droppedNow = 0;
            long droppedNowBytes = 0;
            while (used + bytes > limit && !entries.isEmpty()) {
                Map.Entry<String, Entry> oldest = entries.entrySet().iterator().next();
                entries.remove(oldest.getKey());
                used -= oldest.getValue().bytes();
                dropped++;
                droppedBytes += oldest.getValue().bytes();
                droppedNow++;
                droppedNowBytes += oldest.getValue().bytes();
            }
            Entry e = new Entry(entryName.trim(), bytes);
            entries.put(e.name(), e);
            used += bytes;
            return new Added(e, droppedNow, droppedNowBytes, "");
        }

        public synchronized long used() {
            return used;
        }

        public synchronized int dropped() {
            return dropped;
        }

        public synchronized long droppedBytes() {
            return droppedBytes;
        }

        public synchronized int size() {
            return entries.size();
        }

        /** The report: what is held, and what was dropped to stay inside. Never silent. */
        public synchronized String report() {
            return name + ": " + entries.size() + " entries, " + used + " of " + limit + " bytes; "
                    + (dropped == 0 ? "nothing dropped"
                    : dropped + " entries dropped (" + droppedBytes + " bytes) to stay inside");
        }
    }

    private CacheLimits() {
    }

    /**
     * <b>The save's line.</b> How many rebuilt years one chunk holds, against the named budget. Over
     * budget, the words say which year's copy goes; inside, they say so.
     */
    public static String saveLine(int yearsKept) {
        if (yearsKept < 0) {
            return "a chunk cannot keep " + yearsKept + " years: below none is no years at all";
        }
        if (yearsKept > MAX_REBUILD_YEARS) {
            return "a chunk keeps " + yearsKept + " rebuilt years against " + MAX_REBUILD_YEARS
                    + " (a game default): the oldest " + (yearsKept - MAX_REBUILD_YEARS) + " year(s) "
                    + "of copies go, oldest first";
        }
        return "a chunk keeps " + yearsKept + " rebuilt years, inside " + MAX_REBUILD_YEARS
                + " (a game default)";
    }

    public static String ruleLine() {
        return "A cache has bytes and counts every drop; an entry bigger than the whole cache is "
                + "refused, and past the ceiling the oldest goes first. A chunk keeps " + MAX_REBUILD_YEARS
                + " rebuilt years (a game default); past that the oldest year's copy goes, and the "
                + "save's line says so.";
    }
}
