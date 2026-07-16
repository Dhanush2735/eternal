package com.eternalengine.anomaly;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Maps each anomaly type to likely real project code paths.
 */
public final class RootCauseMapper {

    private final Map<AnomalyType, List<RootCause>> causes = new EnumMap<>(AnomalyType.class);

    public RootCauseMapper() {
        causes.put(AnomalyType.INVALID_TERRA_PROBE, List.of(
            new RootCause("TerraBridge.probe", "Terra++ classifier or coordinate conversion returned invalid data", 0.85d),
            new RootCause("TerraDensityFunctions.classify", "Native Terra++ terrain column was unavailable", 0.75d),
            new RootCause("TerraDensityFunctions.toGeo", "Projection returned null/out-of-bounds coordinates", 0.70d)
        ));

        causes.put(AnomalyType.HEIGHT_JUMP, List.of(
            new RootCause("TerraDensityFunctions.getElevationFast", "Elevation tile or interpolation may have a discontinuity", 0.70d),
            new RootCause("TerraDensityFunctions.elevMetersToSurfaceY", "Elevation-to-Y scaling may amplify local delta", 0.60d),
            new RootCause("TerraChunkGenerator.generateTerrain", "Chunk seam or neighbour smoothing pass may be inconsistent", 0.65d)
        ));

        causes.put(AnomalyType.SURFACE_BLOCK_MISMATCH, List.of(
            new RootCause("TerraBiomeProvider.getBiomeTypeCode", "WorldCover/climate classification may select unexpected type", 0.75d),
            new RootCause("TerraChunkGenerator.gbt", "WorldCover UNKNOWN/BUILT_UP fallback may change surface code", 0.82d),
            new RootCause("TerraChunkGenerator.getSurfaceBlock", "Surface type code may map to a different block", 0.78d),
            new RootCause("EsaWorldCoverIndex.getClass", "Async WorldCover loading may have returned UNKNOWN during generation", 0.80d)
        ));

        causes.put(AnomalyType.WATER_LAND_CONFLICT, List.of(
            new RootCause("TerraDensityFunctions.classify", "Elevation/OSM coastline classification disagrees with actual block", 0.76d),
            new RootCause("RiverLakeEngine.analyze", "Water body overlay may have changed terrain column", 0.72d),
            new RootCause("TerraChunkGenerator.syncHistoricalWater", "Historical water refresh may have stale state", 0.68d)
        ));

        causes.put(AnomalyType.WORLDCOVER_UNKNOWN, List.of(
            new RootCause("EsaWorldCoverIndex.getClass", "WorldCover tile/header/internal tile was unavailable during query", 0.90d),
            new RootCause("EsaWorldCoverIndex.prefetch", "Prefetch may not have completed before terrain generation", 0.82d),
            new RootCause("TerraChunkGenerator.generateTerrain", "Generation may have used fallback before WorldCover result arrived", 0.70d)
        ));

        causes.put(AnomalyType.FLOATING_STRUCTURE_RISK, List.of(
            new RootCause("EraBuildingGenerator.placeBuilding", "Building origin did not use getSurfaceY()+1", 0.86d),
            new RootCause("BlockUpdateQueue.enqueue", "Queued placement may have used stale surface Y", 0.72d),
            new RootCause("TerraCoordinateHelper.getSurfaceY", "Surface lookup may have used an unloaded or wrong level/chunk", 0.62d)
        ));

        causes.put(AnomalyType.SUNKEN_STRUCTURE_RISK, List.of(
            new RootCause("EraBuildingGenerator.placeBuilding", "Building may have been placed at or below terrain surface", 0.86d),
            new RootCause("TerraCoordinateHelper.getSurfaceY", "Surface lookup may be stale or wrong level/chunk", 0.72d),
            new RootCause("BlockUpdateQueue.tick", "Block update may have executed after terrain changed", 0.60d)
        ));

        causes.put(AnomalyType.BUILDING_BEFORE_CONSTRUCTION, List.of(
            new RootCause("BuildingHistoryDatabase.getBuildingsExistingInYear", "Building is considered present before construction year", 0.86d),
            new RootCause("BuildingDateInferenceEngine.inferConstructionDate", "Construction date may be inferred too late or record date is wrong", 0.78d),
            new RootCause("EraBuildingGenerator.generateBuildingForChunk", "Building placement may not be gated by simulation year", 0.74d)
        ));

        causes.put(AnomalyType.BUILDING_AFTER_DESTRUCTION, List.of(
            new RootCause("BuildingHistoryDatabase.getBuildingsExistingInYear", "Destroyed building is still considered present", 0.86d),
            new RootCause("BuildingEvolutionEngine.checkWarDamage", "Destroyed state may not have been applied to world/history", 0.75d),
            new RootCause("BlockUpdateQueue.cancel", "Queued building blocks may remain after destruction", 0.62d)
        ));

        causes.put(AnomalyType.BUILDING_ERA_MISMATCH, List.of(
            new RootCause("BuildingDateInferenceEngine.inferConstructionDate", "Building era does not match construction year", 0.82d),
            new RootCause("EraAnchorSystem.getEraForLocation", "Regional era anchor may disagree with building record", 0.70d),
            new RootCause("BuildingRecord.setEra", "Building era metadata may be stale", 0.66d)
        ));

        causes.put(AnomalyType.BUILDING_STATE_CONTRADICTION, List.of(
            new RootCause("BuildingRecord.addStateChange", "Damage and currentState are inconsistent", 0.86d),
            new RootCause("BuildingEvolutionEngine.advanceAbandonedDecay", "State transition may not update damage consistently", 0.68d),
            new RootCause("BuildingHistoryDatabase.updateBuilding", "Indexes may have stale building state", 0.62d)
        ));

        causes.put(AnomalyType.BUILDING_Y_MISMATCH_RISK, List.of(
            new RootCause("EraBuildingGenerator.placeBuilding", "Building may not be placed at Terra++ surfaceY + 1", 0.84d),
            new RootCause("WorldDebugSnapshot.capture", "Nearby terrain indicates risky placement surface", 0.70d),
            new RootCause("TerraCoordinateHelper.getPlacementY", "Placement Y should be recomputed at actual footprint columns", 0.72d)
        ));
    }

    public List<RootCause> map(AnomalyType type) {
        return causes.getOrDefault(type, List.of());
    }
}
