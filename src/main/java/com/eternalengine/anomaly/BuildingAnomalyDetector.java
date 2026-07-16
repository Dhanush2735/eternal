package com.eternalengine.anomaly;

import com.eternalengine.buildings.BuildingRecord;
import com.eternalengine.telemetry.WorldDebugSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Building-specific anomaly detector.
 *
 * It consumes building history records plus nearby Terra++ telemetry. It does
 * not place blocks, edit building records, or invent terrain facts.
 */
public final class BuildingAnomalyDetector {

    private final RootCauseMapper rootCauseMapper = new RootCauseMapper();

    public AnomalyScanResult detect(WorldDebugSnapshot nearbySnapshot, List<BuildingRecord> buildings, int year) {
        if (buildings == null || buildings.isEmpty()) {
            return new AnomalyScanResult(List.of());
        }
        List<AnomalyRecord> records = new ArrayList<>();
        for (BuildingRecord building : buildings) {
            if (building == null) {
                continue;
            }
            detectSingle(records, nearbySnapshot, building, year);
        }
        return new AnomalyScanResult(records);
    }

    private void detectSingle(List<AnomalyRecord> records, WorldDebugSnapshot snapshot, BuildingRecord building, int year) {
        if (year < building.constructionYear()) {
            records.add(record(AnomalyType.BUILDING_BEFORE_CONSTRUCTION, snapshot,
                "not present before " + building.constructionYear(),
                "record exists at year " + year,
                0.90d,
                evidence(building, "constructionYear=" + building.constructionYear(), "year=" + year)));
        }

        if (building.destroyedYear() != Integer.MAX_VALUE && year >= building.destroyedYear()) {
            records.add(record(AnomalyType.BUILDING_AFTER_DESTRUCTION, snapshot,
                "not present after destroyedYear=" + building.destroyedYear(),
                "record still indexed at year " + year,
                0.88d,
                evidence(building, "destroyedYear=" + building.destroyedYear(), "year=" + year)));
        }

        String expectedEra = eraForYear(building.constructionYear());
        if (building.era() != null
            && !building.era().equalsIgnoreCase("UNKNOWN")
            && !building.era().equalsIgnoreCase(expectedEra)) {
            records.add(record(AnomalyType.BUILDING_ERA_MISMATCH, snapshot,
                expectedEra,
                building.era(),
                0.76d,
                evidence(building, "constructionYear=" + building.constructionYear())));
        }

        boolean stateDestroyed = "DESTROYED".equalsIgnoreCase(building.currentState());
        boolean damageDestroyed = building.damagePercent() >= 100;
        if (stateDestroyed != damageDestroyed) {
            records.add(record(AnomalyType.BUILDING_STATE_CONTRADICTION, snapshot,
                "currentState and damagePercent agree",
                "state=" + building.currentState() + " damage=" + building.damagePercent(),
                0.82d,
                evidence(building, "currentState=" + building.currentState(), "damagePercent=" + building.damagePercent())));
        }

        if (snapshot != null && snapshot.valid()) {
            String terrain = snapshot.terrainType() == null ? "UNKNOWN" : snapshot.terrainType().toUpperCase(Locale.ROOT);
            boolean waterTerrain = terrain.contains("OCEAN") || terrain.contains("WATER") || terrain.contains("RIVER") || terrain.contains("LAKE");
            if (waterTerrain && building.isStanding()) {
                records.add(record(AnomalyType.BUILDING_Y_MISMATCH_RISK, snapshot,
                    "standing building on verified land surface",
                    "nearby terrain=" + snapshot.terrainType(),
                    0.70d,
                    evidence(building, "surfaceY=" + snapshot.terraSurfaceY(), "terrain=" + snapshot.terrainType())));
            }
        }
    }

    private AnomalyRecord record(AnomalyType type, WorldDebugSnapshot snapshot, String expected,
                                 String actual, double confidence, List<String> evidence) {
        return new AnomalyRecord(type, snapshot, expected, actual, confidence, evidence, rootCauseMapper.map(type));
    }

    private List<String> evidence(BuildingRecord building, String... extra) {
        List<String> evidence = new ArrayList<>();
        evidence.add("buildingId=" + building.buildingId());
        evidence.add("name=" + building.name());
        evidence.add("lat=" + building.latitude());
        evidence.add("lon=" + building.longitude());
        for (String item : extra) {
            if (item != null && !item.isBlank()) {
                evidence.add(item);
            }
        }
        return evidence;
    }

    private String eraForYear(int year) {
        if (year >= 1945) return "MODERN";
        if (year >= 1750) return "INDUSTRIAL";
        if (year >= 1450) return "RENAISSANCE";
        if (year >= 500) return "MEDIEVAL";
        return "ANCIENT";
    }
}
