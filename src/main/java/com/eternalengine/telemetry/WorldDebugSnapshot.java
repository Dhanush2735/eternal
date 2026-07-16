package com.eternalengine.telemetry;

import com.eternalengine.terrabridge.TerraBridge;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Immutable F3-style world debug snapshot for one Minecraft column.
 *
 * This is the base telemetry object for future anomaly detection. It records
 * actual world state and Terra++ truth-side state separately so later systems
 * can compare expected vs actual without inventing data.
 */
public final class WorldDebugSnapshot {

    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final double latitude;
    private final double longitude;
    private final double elevationMeters;
    private final int terraSurfaceY;
    private final int placementY;
    private final String terrainType;
    private final String nativeTerrainType;
    private final String worldCoverClass;
    private final String actualSurfaceBlock;
    private final String aboveSurfaceBlock;
    private final String biomeName;
    private final String dimensionName;
    private final int chunkX;
    private final int chunkZ;
    private final boolean valid;
    private final String failureReason;

    private WorldDebugSnapshot(int blockX, int blockY, int blockZ,
                               double latitude, double longitude, double elevationMeters,
                               int terraSurfaceY, int placementY,
                               String terrainType, String nativeTerrainType, String worldCoverClass,
                               String actualSurfaceBlock, String aboveSurfaceBlock,
                               String biomeName, String dimensionName,
                               int chunkX, int chunkZ, boolean valid, String failureReason) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevationMeters = elevationMeters;
        this.terraSurfaceY = terraSurfaceY;
        this.placementY = placementY;
        this.terrainType = terrainType;
        this.nativeTerrainType = nativeTerrainType;
        this.worldCoverClass = worldCoverClass;
        this.actualSurfaceBlock = actualSurfaceBlock;
        this.aboveSurfaceBlock = aboveSurfaceBlock;
        this.biomeName = biomeName;
        this.dimensionName = dimensionName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.valid = valid;
        this.failureReason = failureReason;
    }

    public static WorldDebugSnapshot capture(ServerLevel level, int blockX, int blockZ) {
        if (level == null) {
            return invalid(blockX, blockZ, "level is null");
        }

        TerraBridge.TerrainSnapshot terra = TerraCoordinateHelper.probe(blockX, blockZ);
        if (terra == null || !terra.valid()) {
            return invalid(blockX, blockZ, "Terra++ probe unavailable");
        }

        int surfaceY = terra.surfaceY();
        int placementY = Math.max(TerraCoordinateHelper.getMinY(),
            Math.min(TerraCoordinateHelper.getMaxY(), surfaceY + 1));
        BlockPos surfacePos = new BlockPos(blockX, surfaceY, blockZ);
        BlockPos abovePos = new BlockPos(blockX, placementY, blockZ);

        String surfaceBlock = safeBlockName(level.getBlockState(surfacePos));
        String aboveBlock = safeBlockName(level.getBlockState(abovePos));
        String biomeName = safeBiomeName(level, surfacePos);
        String dimension = level.dimension().location().toString();

        return new WorldDebugSnapshot(
            blockX,
            surfaceY,
            blockZ,
            terra.latitude(),
            terra.longitude(),
            terra.elevationMeters(),
            surfaceY,
            placementY,
            terra.terrainType().name(),
            terra.nativeTerrainType(),
            terra.worldCoverClass(),
            surfaceBlock,
            aboveBlock,
            biomeName,
            dimension,
            Math.floorDiv(blockX, 16),
            Math.floorDiv(blockZ, 16),
            true,
            ""
        );
    }

    public static WorldDebugSnapshot captureAtPlayer(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return invalid(0, 0, "player position is null");
        }
        return capture(level, pos.getX(), pos.getZ());
    }

    public static WorldDebugSnapshot invalid(int blockX, int blockZ, String reason) {
        return new WorldDebugSnapshot(
            blockX, 0, blockZ,
            Double.NaN, Double.NaN, -50.0d,
            63, 64,
            "UNKNOWN", "UNKNOWN", "UNKNOWN",
            "unknown", "unknown", "unknown", "unknown",
            Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16),
            false, reason == null ? "unknown failure" : reason
        );
    }

    private static String safeBlockName(@Nullable BlockState state) {
        if (state == null) {
            return "unknown";
        }
        try {
            var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            return key == null ? state.toString() : key.toString();
        } catch (Exception e) {
            return state.toString();
        }
    }

    private static String safeBiomeName(ServerLevel level, BlockPos pos) {
        try {
            return level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String toShortLine() {
        if (!valid) {
            return "Probe failed at x=" + blockX + " z=" + blockZ + ": " + failureReason;
        }
        return String.format(Locale.ROOT,
            "x=%d y=%d z=%d lat=%.6f lon=%.6f elev=%.1fm terrain=%s wc=%s surface=%s biome=%s",
            blockX, blockY, blockZ, latitude, longitude, elevationMeters,
            terrainType, worldCoverClass, actualSurfaceBlock, biomeName);
    }

    public String toMultilineString() {
        if (!valid) {
            return "WorldDebugSnapshot failed\n"
                + "  x=" + blockX + " z=" + blockZ + "\n"
                + "  reason=" + failureReason;
        }
        return String.format(Locale.ROOT,
            "WorldDebugSnapshot\n"
                + "  block: x=%d y=%d z=%d chunk=%d,%d dimension=%s\n"
                + "  geo: lat=%.7f lon=%.7f elevation=%.2fm\n"
                + "  terra: surfaceY=%d placementY=%d terrain=%s native=%s worldCover=%s\n"
                + "  world: surfaceBlock=%s aboveSurface=%s biome=%s",
            blockX, blockY, blockZ, chunkX, chunkZ, dimensionName,
            latitude, longitude, elevationMeters,
            terraSurfaceY, placementY, terrainType, nativeTerrainType, worldCoverClass,
            actualSurfaceBlock, aboveSurfaceBlock, biomeName);
    }

    public int blockX() { return blockX; }
    public int blockY() { return blockY; }
    public int blockZ() { return blockZ; }
    public double latitude() { return latitude; }
    public double longitude() { return longitude; }
    public double elevationMeters() { return elevationMeters; }
    public int terraSurfaceY() { return terraSurfaceY; }
    public int placementY() { return placementY; }
    public String terrainType() { return terrainType; }
    public String nativeTerrainType() { return nativeTerrainType; }
    public String worldCoverClass() { return worldCoverClass; }
    public String actualSurfaceBlock() { return actualSurfaceBlock; }
    public String aboveSurfaceBlock() { return aboveSurfaceBlock; }
    public String biomeName() { return biomeName; }
    public String dimensionName() { return dimensionName; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public boolean valid() { return valid; }
    public String failureReason() { return failureReason; }
}
