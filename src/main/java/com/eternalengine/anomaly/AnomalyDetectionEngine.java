package com.eternalengine.anomaly;

import com.eternalengine.telemetry.ChunkTelemetryScanner;
import com.eternalengine.telemetry.WorldDebugSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic anomaly detector.
 *
 * It consumes server-side telemetry only. It does not call LLMs, mutate the
 * world, or invent Terra++ facts. LLM systems may later explain these records.
 */
public final class AnomalyDetectionEngine {

    private final RootCauseMapper rootCauseMapper;
    private final ExpectedSurfaceModel expectedSurfaceModel;
    private final int heightJumpThreshold;

    public AnomalyDetectionEngine() {
        this(12);
    }

    public AnomalyDetectionEngine(int heightJumpThreshold) {
        this.rootCauseMapper = new RootCauseMapper();
        this.expectedSurfaceModel = new ExpectedSurfaceModel();
        this.heightJumpThreshold = Math.max(1, heightJumpThreshold);
    }

    public AnomalyScanResult detectSnapshot(WorldDebugSnapshot snapshot, int year) {
        List<AnomalyRecord> records = new ArrayList<>();
        addSnapshotAnomalies(records, snapshot, year);
        return new AnomalyScanResult(records);
    }

    public AnomalyScanResult detectChunk(ChunkTelemetryScanner.ScanResult scanResult, int year) {
        if (scanResult == null || !scanResult.valid()) {
            return new AnomalyScanResult(List.of());
        }

        List<AnomalyRecord> records = new ArrayList<>();
        List<WorldDebugSnapshot> snapshots = scanResult.snapshots();
        for (WorldDebugSnapshot snapshot : snapshots) {
            addSnapshotAnomalies(records, snapshot, year);
        }
        addHeightJumpAnomalies(records, snapshots, scanResult.step());
        return new AnomalyScanResult(records);
    }

    private void addSnapshotAnomalies(List<AnomalyRecord> records, WorldDebugSnapshot snapshot, int year) {
        if (snapshot == null) {
            return;
        }

        if (!snapshot.valid()) {
            records.add(record(AnomalyType.INVALID_TERRA_PROBE, snapshot,
                "valid Terra++ probe", "invalid", 0.95d,
                List.of("snapshot.valid=false", "reason=" + snapshot.failureReason())));
            return;
        }

        if (equalsIgnoreCase(snapshot.worldCoverClass(), "UNKNOWN")) {
            records.add(record(AnomalyType.WORLDCOVER_UNKNOWN, snapshot,
                "known ESA WorldCover class", "UNKNOWN", 0.80d,
                List.of("worldCover=UNKNOWN")));
        }

        String expected = expectedSurfaceModel.expectedSurface(snapshot, year);
        String actual = snapshot.actualSurfaceBlock();
        if (!"unknown".equals(expected) && actual != null && !actual.equals(expected)) {
            double confidence = expected.contains("water") || actual.contains("water") ? 0.74d : 0.88d;
            records.add(record(AnomalyType.SURFACE_BLOCK_MISMATCH, snapshot,
                expected, actual, confidence,
                List.of("worldCover=" + snapshot.worldCoverClass(),
                    "terrain=" + snapshot.terrainType(),
                    "year=" + year,
                    "biome=" + snapshot.biomeName())));
        }

        if ((equalsIgnoreCase(snapshot.terrainType(), "LAND") || equalsIgnoreCase(snapshot.terrainType(), "COASTAL"))
            && "minecraft:water".equals(actual)) {
            records.add(record(AnomalyType.WATER_LAND_CONFLICT, snapshot,
                "land surface block", actual, 0.82d,
                List.of("terrain=" + snapshot.terrainType(), "surfaceBlock=" + actual)));
        }
    }

    private void addHeightJumpAnomalies(List<AnomalyRecord> records, List<WorldDebugSnapshot> snapshots, int step) {
        for (WorldDebugSnapshot snapshot : snapshots) {
            if (snapshot == null || !snapshot.valid()) {
                continue;
            }
            for (WorldDebugSnapshot neighbor : snapshots) {
                if (neighbor == null || !neighbor.valid()) {
                    continue;
                }
                boolean east = neighbor.blockX() == snapshot.blockX() + step && neighbor.blockZ() == snapshot.blockZ();
                boolean south = neighbor.blockX() == snapshot.blockX() && neighbor.blockZ() == snapshot.blockZ() + step;
                if (!east && !south) {
                    continue;
                }
                int delta = Math.abs(snapshot.terraSurfaceY() - neighbor.terraSurfaceY());
                if (delta >= heightJumpThreshold) {
                    double confidence = Math.min(0.99d, 0.60d + delta / 80.0d);
                    records.add(record(AnomalyType.HEIGHT_JUMP, snapshot,
                        "ΔY<" + heightJumpThreshold, "ΔY=" + delta, confidence,
                        List.of("neighbor=(" + neighbor.blockX() + "," + neighbor.blockZ() + ")",
                            "surfaceY=" + snapshot.terraSurfaceY(),
                            "neighborSurfaceY=" + neighbor.terraSurfaceY())));
                }
            }
        }
    }

    private AnomalyRecord record(AnomalyType type, WorldDebugSnapshot snapshot, String expected, String actual,
                                 double confidence, List<String> evidence) {
        return new AnomalyRecord(type, snapshot, expected, actual, confidence, evidence, rootCauseMapper.map(type));
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
}
