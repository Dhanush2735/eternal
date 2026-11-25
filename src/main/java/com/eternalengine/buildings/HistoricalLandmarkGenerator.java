package com.eternalengine.buildings;

import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates iconic historical landmarks and civic monuments from geographic coordinates or OSM footprints.
 */
public final class HistoricalLandmarkGenerator {

    public enum LandmarkType {
        PARTHENON,
        MEDIEVAL_KEEP,
        GREAT_PAGODA,
        ROMAN_COLOSSEUM,
        GOTHIC_CATHEDRAL
    }

    public PlannedBuilding generateLandmark(ServerLevel level, BlockPos origin, LandmarkType type, String landmarkId) {
        if (level == null || origin == null) {
            return new PlannedBuilding(landmarkId, null, origin, Map.of(), "Missing level/origin");
        }

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        int baseY = TerraCoordinateHelper.getPlacementY(level, origin.getX(), origin.getZ());

        switch (type) {
            case PARTHENON -> generateParthenon(blocks, level, origin, baseY);
            case MEDIEVAL_KEEP -> generateMedievalKeep(blocks, level, origin, baseY);
            case GREAT_PAGODA -> generateGreatPagoda(blocks, level, origin, baseY);
            case ROMAN_COLOSSEUM -> generateColosseum(blocks, level, origin, baseY);
            case GOTHIC_CATHEDRAL -> generateGothicCathedral(blocks, level, origin, baseY);
        }

        return new PlannedBuilding(landmarkId, level.dimension(), origin, blocks,
            "Landmark: " + type + " origin=" + origin.toShortString() + " blocks=" + blocks.size());
    }

    private void generateParthenon(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos origin, int baseY) {
        // Octastyle classical temple: 17x31 footprint
        int width = 17;
        int depth = 31;
        int colHeight = 8;

        // Stylobate base (3 steps)
        for (int step = 0; step < 3; step++) {
            for (int x = -step; x < width + step; x++) {
                for (int z = -step; z < depth + step; z++) {
                    putBlock(blocks, level, origin.getX() + x, baseY + step, origin.getZ() + z,
                        Blocks.SMOOTH_QUARTZ.defaultBlockState());
                }
            }
        }
        int floorY = baseY + 3;

        // Outer Peristyle Colonnade (quartz pillars)
        for (int x = 0; x < width; x += 2) {
            for (int z = 0; z < depth; z += 2) {
                boolean isEdge = (x == 0 || x == width - 1 || z == 0 || z == depth - 1);
                if (isEdge) {
                    for (int y = 0; y < colHeight; y++) {
                        putBlock(blocks, level, origin.getX() + x, floorY + y, origin.getZ() + z,
                            Blocks.QUARTZ_PILLAR.defaultBlockState());
                    }
                }
            }
        }

        // Inner Cella Walls
        for (int x = 4; x <= width - 5; x++) {
            for (int z = 6; z <= depth - 7; z++) {
                boolean cellaEdge = (x == 4 || x == width - 5 || z == 6 || z == depth - 7);
                if (cellaEdge) {
                    for (int y = 0; y < colHeight; y++) {
                        putBlock(blocks, level, origin.getX() + x, floorY + y, origin.getZ() + z,
                            Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                    }
                }
            }
        }

        // Entablature & Pediment Roof
        int entablatureY = floorY + colHeight;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                putBlock(blocks, level, origin.getX() + x, entablatureY, origin.getZ() + z,
                    Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
        }

        // Pediment triangular gable
        int halfW = width / 2;
        for (int s = 0; s <= halfW; s++) {
            int ry = entablatureY + 1 + s;
            for (int z = 0; z < depth; z++) {
                putBlock(blocks, level, origin.getX() + s, ry, origin.getZ() + z, Blocks.TERRACOTTA.defaultBlockState());
                putBlock(blocks, level, origin.getX() + width - 1 - s, ry, origin.getZ() + z, Blocks.TERRACOTTA.defaultBlockState());
            }
        }
    }

    private void generateMedievalKeep(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos origin, int baseY) {
        int size = 15;
        int height = 14;

        // Main keep walls
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    boolean wall = (x == 0 || x == size - 1 || z == 0 || z == size - 1);
                    boolean tower = (x < 4 || x >= size - 4) && (z < 4 || z >= size - 4);
                    if (wall || tower) {
                        putBlock(blocks, level, origin.getX() + x, baseY + y, origin.getZ() + z,
                            Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
            }
        }

        // Crenelated battlements on top
        int topY = baseY + height;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                boolean edge = (x == 0 || x == size - 1 || z == 0 || z == size - 1);
                if (edge && (x + z) % 2 == 0) {
                    putBlock(blocks, level, origin.getX() + x, topY, origin.getZ() + z,
                        Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
        }
    }

    private void generateGreatPagoda(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos origin, int baseY) {
        int tiers = 5;
        int currentSize = 13;
        int curY = baseY;

        for (int t = 0; t < tiers; t++) {
            int tierHeight = 4;
            int offset = (13 - currentSize) / 2;

            // Tier walls
            for (int y = 0; y < tierHeight; y++) {
                for (int x = 0; x < currentSize; x++) {
                    for (int z = 0; z < currentSize; z++) {
                        boolean edge = (x == 0 || x == currentSize - 1 || z == 0 || z == currentSize - 1);
                        boolean corner = (x == 0 || x == currentSize - 1) && (z == 0 || z == currentSize - 1);
                        if (corner) {
                            putBlock(blocks, level, origin.getX() + offset + x, curY + y, origin.getZ() + offset + z,
                                Blocks.DARK_OAK_LOG.defaultBlockState());
                        } else if (edge) {
                            putBlock(blocks, level, origin.getX() + offset + x, curY + y, origin.getZ() + offset + z,
                                Blocks.RED_TERRACOTTA.defaultBlockState());
                        }
                    }
                }
            }
            curY += tierHeight;

            // Tier flaring roof eaves
            for (int x = -1; x <= currentSize; x++) {
                for (int z = -1; z <= currentSize; z++) {
                    putBlock(blocks, level, origin.getX() + offset + x, curY, origin.getZ() + offset + z,
                        Blocks.DARK_PRISMARINE.defaultBlockState());
                }
            }
            curY++;
            currentSize = Math.max(5, currentSize - 2);
        }

        // Spire crown
        for (int y = 0; y < 4; y++) {
            putBlock(blocks, level, origin.getX() + 6, curY + y, origin.getZ() + 6, Blocks.GOLD_BLOCK.defaultBlockState());
        }
    }

    private void generateColosseum(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos origin, int baseY) {
        int radiusX = 14;
        int radiusZ = 10;
        int height = 12;

        for (int y = 0; y < height; y++) {
            for (int x = -radiusX; x <= radiusX; x++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    double normDist = ((double) (x * x) / (radiusX * radiusX)) + ((double) (z * z) / (radiusZ * radiusZ));
                    if (normDist >= 0.75D && normDist <= 1.0D) {
                        boolean arcadePillar = (x + z) % 3 == 0;
                        BlockState state = arcadePillar ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
                        putBlock(blocks, level, origin.getX() + x + radiusX, baseY + y, origin.getZ() + z + radiusZ, state);
                    }
                }
            }
        }
    }

    private void generateGothicCathedral(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos origin, int baseY) {
        int naveLength = 26;
        int naveWidth = 10;
        int naveHeight = 12;

        // Long nave with pointed vaulting
        for (int y = 0; y < naveHeight; y++) {
            for (int x = 0; x < naveWidth; x++) {
                for (int z = 0; z < naveLength; z++) {
                    boolean wall = (x == 0 || x == naveWidth - 1);
                    if (wall) {
                        boolean buttress = (z % 4 == 0);
                        BlockState b = buttress ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.GLASS_PANE.defaultBlockState();
                        putBlock(blocks, level, origin.getX() + x, baseY + y, origin.getZ() + z, b);
                    }
                }
            }
        }

        // Vaulted roof
        int halfNave = naveWidth / 2;
        for (int s = 0; s <= halfNave; s++) {
            int ry = baseY + naveHeight + s;
            for (int z = 0; z < naveLength; z++) {
                putBlock(blocks, level, origin.getX() + s, ry, origin.getZ() + z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                putBlock(blocks, level, origin.getX() + naveWidth - 1 - s, ry, origin.getZ() + z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            }
        }
    }

    private void putBlock(Map<BlockPos, BlockState> blocks, ServerLevel level, int x, int y, int z, BlockState state) {
        int surfaceY = TerraCoordinateHelper.getSurfaceY(level, x, z);
        if (y > surfaceY && y >= TerraCoordinateHelper.getMinY() && y <= TerraCoordinateHelper.getMaxY()) {
            blocks.put(new BlockPos(x, y, z), state);
        }
    }
}
