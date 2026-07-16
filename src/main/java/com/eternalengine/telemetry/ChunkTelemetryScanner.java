package com.eternalengine.telemetry;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight synchronous scanner for a single chunk.
 *
 * It samples columns and records F3-style debug snapshots. It does not modify
 * the world and does not perform anomaly decisions yet; Phase 0C will consume
 * this telemetry to produce root-cause anomaly reports.
 */
public final class ChunkTelemetryScanner {

    private static final int CHUNK_SIZE = 16;
    private static final int DEFAULT_STEP = 4;

    private ChunkTelemetryScanner() {
    }

    public static ScanResult scanChunk(ServerLevel level, int chunkX, int chunkZ, int step) {
        if (level == null) {
            return ScanResult.invalid(chunkX, chunkZ, "level is null");
        }
        int safeStep = sanitizeStep(step);
        int minBlockX = chunkX * CHUNK_SIZE;
        int minBlockZ = chunkZ * CHUNK_SIZE;
        List<WorldDebugSnapshot> snapshots = new ArrayList<>();

        for (int dx = 0; dx < CHUNK_SIZE; dx += safeStep) {
            for (int dz = 0; dz < CHUNK_SIZE; dz += safeStep) {
                snapshots.add(WorldDebugSnapshot.capture(level, minBlockX + dx, minBlockZ + dz));
            }
        }

        return ScanResult.fromSnapshots(chunkX, chunkZ, safeStep, snapshots);
    }

    public static ScanResult scanChunkContaining(ServerLevel level, int blockX, int blockZ, int step) {
        return scanChunk(level, Math.floorDiv(blockX, CHUNK_SIZE), Math.floorDiv(blockZ, CHUNK_SIZE), step);
    }

    private static int sanitizeStep(int step) {
        if (step <= 0) {
            return DEFAULT_STEP;
        }
        if (step > CHUNK_SIZE) {
            return CHUNK_SIZE;
        }
        return step;
    }

    public static final class ScanResult {
        private final int chunkX;
        private final int chunkZ;
        private final int step;
        private final List<WorldDebugSnapshot> snapshots;
        private final int validCount;
        private final int invalidCount;
        private final int minSurfaceY;
        private final int maxSurfaceY;
        private final int maxSampledDeltaY;
        private final Map<String, Integer> surfaceBlockCounts;
        private final Map<String, Integer> worldCoverCounts;
        private final boolean valid;
        private final String failureReason;

        private ScanResult(int chunkX, int chunkZ, int step,
                           List<WorldDebugSnapshot> snapshots,
                           int validCount, int invalidCount,
                           int minSurfaceY, int maxSurfaceY, int maxSampledDeltaY,
                           Map<String, Integer> surfaceBlockCounts,
                           Map<String, Integer> worldCoverCounts,
                           boolean valid, String failureReason) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.step = step;
            this.snapshots = Collections.unmodifiableList(new ArrayList<>(snapshots));
            this.validCount = validCount;
            this.invalidCount = invalidCount;
            this.minSurfaceY = minSurfaceY;
            this.maxSurfaceY = maxSurfaceY;
            this.maxSampledDeltaY = maxSampledDeltaY;
            this.surfaceBlockCounts = Collections.unmodifiableMap(new HashMap<>(surfaceBlockCounts));
            this.worldCoverCounts = Collections.unmodifiableMap(new HashMap<>(worldCoverCounts));
            this.valid = valid;
            this.failureReason = failureReason;
        }

        static ScanResult invalid(int chunkX, int chunkZ, String reason) {
            return new ScanResult(chunkX, chunkZ, DEFAULT_STEP, List.of(), 0, 0,
                0, 0, 0, Map.of(), Map.of(), false, reason == null ? "unknown failure" : reason);
        }

        static ScanResult fromSnapshots(int chunkX, int chunkZ, int step, List<WorldDebugSnapshot> snapshots) {
            int validCount = 0;
            int invalidCount = 0;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxDelta = 0;
            Map<String, Integer> surfaceCounts = new HashMap<>();
            Map<String, Integer> worldCoverCounts = new HashMap<>();
            Map<String, WorldDebugSnapshot> bySampleCoord = new HashMap<>();

            for (WorldDebugSnapshot snapshot : snapshots) {
                if (snapshot.valid()) {
                    validCount++;
                    minY = Math.min(minY, snapshot.terraSurfaceY());
                    maxY = Math.max(maxY, snapshot.terraSurfaceY());
                    surfaceCounts.merge(snapshot.actualSurfaceBlock(), 1, Integer::sum);
                    worldCoverCounts.merge(snapshot.worldCoverClass(), 1, Integer::sum);
                    bySampleCoord.put(snapshot.blockX() + ":" + snapshot.blockZ(), snapshot);
                } else {
                    invalidCount++;
                }
            }

            for (WorldDebugSnapshot snapshot : snapshots) {
                if (!snapshot.valid()) {
                    continue;
                }
                WorldDebugSnapshot east = bySampleCoord.get((snapshot.blockX() + step) + ":" + snapshot.blockZ());
                WorldDebugSnapshot south = bySampleCoord.get(snapshot.blockX() + ":" + (snapshot.blockZ() + step));
                if (east != null) {
                    maxDelta = Math.max(maxDelta, Math.abs(snapshot.terraSurfaceY() - east.terraSurfaceY()));
                }
                if (south != null) {
                    maxDelta = Math.max(maxDelta, Math.abs(snapshot.terraSurfaceY() - south.terraSurfaceY()));
                }
            }

            if (validCount == 0) {
                minY = 0;
                maxY = 0;
            }

            return new ScanResult(chunkX, chunkZ, step, snapshots, validCount, invalidCount,
                minY, maxY, maxDelta, surfaceCounts, worldCoverCounts, true, "");
        }

        public String toSummaryString() {
            if (!valid) {
                return "ChunkTelemetryScan failed for chunk " + chunkX + "," + chunkZ + ": " + failureReason;
            }
            return String.format(Locale.ROOT,
                "ChunkTelemetryScan chunk=%d,%d step=%d samples=%d valid=%d invalid=%d surfaceY=[%d,%d] maxSampleDeltaY=%d surfaces=%s worldCover=%s",
                chunkX, chunkZ, step, snapshots.size(), validCount, invalidCount,
                minSurfaceY, maxSurfaceY, maxSampledDeltaY, surfaceBlockCounts, worldCoverCounts);
        }

        public List<String> firstSnapshotLines(int maxLines) {
            int limit = Math.max(0, Math.min(maxLines, snapshots.size()));
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                lines.add(snapshots.get(i).toShortLine());
            }
            return lines;
        }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public int step() { return step; }
        public List<WorldDebugSnapshot> snapshots() { return snapshots; }
        public int validCount() { return validCount; }
        public int invalidCount() { return invalidCount; }
        public int minSurfaceY() { return minSurfaceY; }
        public int maxSurfaceY() { return maxSurfaceY; }
        public int maxSampledDeltaY() { return maxSampledDeltaY; }
        public Map<String, Integer> surfaceBlockCounts() { return surfaceBlockCounts; }
        public Map<String, Integer> worldCoverCounts() { return worldCoverCounts; }
        public boolean valid() { return valid; }
        public String failureReason() { return failureReason; }
    }
}
