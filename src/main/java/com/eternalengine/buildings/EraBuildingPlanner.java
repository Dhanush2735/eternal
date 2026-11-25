package com.eternalengine.buildings;

import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.towns.BuildingCulture;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates safe building placement plans. It never edits the world directly.
 */
public final class EraBuildingPlanner {

    public BuildingStyle selectStyle(String era, String buildingType) {
        String normalized = era == null ? "ANCIENT" : era.toUpperCase(java.util.Locale.ROOT);
        int floors = switch (normalized) {
            case "MODERN" -> 3;
            case "INDUSTRIAL" -> 2;
            default -> 1;
        };
        int size = switch (buildingType == null ? "generic" : buildingType.toLowerCase(java.util.Locale.ROOT)) {
            case "house" -> 7;
            case "market" -> 11;
            case "temple" -> 13;
            default -> 9;
        };
        return new BuildingStyle(normalized.toLowerCase(java.util.Locale.ROOT) + "_" + (buildingType == null ? "generic" : buildingType),
            buildingType, size, size, floors, 4, MaterialSet.forEra(normalized));
    }

    /**
     * Step 33 (B9, E16): the style of a house in a place and a year, from the building culture
     * register. The building's heights and materials come from the row that covers the point --
     * a mud-brick compound of one storey in Timbuktu in 1350, a panel block of eight in Moscow in
     * 1980 -- rather than from a coarse era, and the era form below is left exactly as it was.
     */
    public BuildingStyle selectStyle(double lat, double lon, int year, SettlementRank rank, String buildingType) {
        String type = buildingType == null ? "generic" : buildingType.toLowerCase(java.util.Locale.ROOT);
        int size = switch (type) {
            case "house" -> 7;
            case "market" -> 11;
            case "temple" -> 13;
            default -> 9;
        };
        String id = "place_" + type;
        int floors = 1;
        MaterialSet mats = MaterialSet.forPlace(lat, lon, year, rank);
        java.util.Optional<BuildingCulture.Reading> r =
                BuildingCulture.get().at(lat, lon, year, rank == null ? SettlementRank.TOWN : rank);
        if (r.isPresent()) {
            BuildingCulture.Reading v = r.get();
            id = v.id() + "_" + type;
            floors = Math.max(1, Math.min(6, v.storeys()));
            if ("house".equals(type) && v.storeys() <= 1) {
                floors = 1;
            }
        }
        return new BuildingStyle(id, buildingType, size, size, floors, 4, mats);
    }

    public PlannedBuilding planAtSurface(ServerLevel level, BlockPos requestedOrigin, BuildingStyle style, String planId) {
        if (level == null || requestedOrigin == null || style == null) {
            return new PlannedBuilding(planId, level == null ? null : level.dimension(), requestedOrigin, Map.of(), "Missing level/origin/style");
        }

        int baseY = highestPlacementY(level, requestedOrigin, style);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        MaterialSet mat = style.materials();
        int maxX = style.width() - 1;
        int maxZ = style.depth() - 1;
        int totalHeight = style.height();

        for (int x = 0; x < style.width(); x++) {
            for (int z = 0; z < style.depth(); z++) {
                int worldX = requestedOrigin.getX() + x;
                int worldZ = requestedOrigin.getZ() + z;
                int surfaceY = TerraCoordinateHelper.getSurfaceY(level, worldX, worldZ);
                if (baseY <= surfaceY) {
                    continue;
                }
                boolean edge = x == 0 || z == 0 || x == maxX || z == maxZ;

                // Floor at base.
                putIfSafe(blocks, level, new BlockPos(worldX, baseY, worldZ), mat.floor());

                for (int y = 1; y <= totalHeight; y++) {
                    BlockPos pos = new BlockPos(worldX, baseY + y, worldZ);
                    if (y == totalHeight) {
                        putIfSafe(blocks, level, pos, mat.roof());
                    } else if (edge) {
                        boolean windowBand = y > 1 && y % style.floorHeight() == 2 && (x + z) % 3 == 0;
                        putIfSafe(blocks, level, pos, windowBand ? mat.window() : mat.wall());
                    } else if (y % style.floorHeight() == 0) {
                        putIfSafe(blocks, level, pos, mat.floor());
                    } else {
                        // interior air clears only above surface via queue; safe but optional.
                        if (y == 1) {
                            putIfSafe(blocks, level, pos, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }

        return new PlannedBuilding(planId, level.dimension(), new BlockPos(requestedOrigin.getX(), baseY, requestedOrigin.getZ()),
            blocks, "Planned " + style.buildingType() + " style=" + style.styleId() + " blocks=" + blocks.size());
    }

    private int highestPlacementY(ServerLevel level, BlockPos origin, BuildingStyle style) {
        int highest = TerraCoordinateHelper.getPlacementY(level, origin.getX(), origin.getZ());
        for (int x = 0; x < style.width(); x++) {
            for (int z = 0; z < style.depth(); z++) {
                highest = Math.max(highest, TerraCoordinateHelper.getPlacementY(level, origin.getX() + x, origin.getZ() + z));
            }
        }
        return highest;
    }

    private void putIfSafe(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos pos, BlockState state) {
        if (pos == null || state == null) {
            return;
        }
        int surfaceY = TerraCoordinateHelper.getSurfaceY(level, pos.getX(), pos.getZ());
        if (pos.getY() <= surfaceY) {
            return;
        }
        if (pos.getY() < TerraCoordinateHelper.getMinY() || pos.getY() > TerraCoordinateHelper.getMaxY()) {
            return;
        }
        blocks.put(pos.immutable(), state);
    }
}
