package com.eternalengine.buildings;

import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Procedural Historical Architectural Grammar Engine.
 * Replaces simplistic box buildings with authentic regional architecture,
 * structural colonnades, pediments, bracketed eaves, and vaulted roofs.
 */
public final class CivilizationGrammarEngine {

    public enum GrammarCulture {
        CLASSICAL_GRECO_ROMAN,
        MEDIEVAL_GOTHIC,
        EAST_ASIAN_PAGODA,
        ISLAMIC_SARACENIC,
        INDUSTRIAL_VICTORIAN,
        MODERN_METROPOLITAN
    }

    public enum BuildingGrammarType {
        HOUSE,
        TEMPLE,
        MARKET_HALL,
        FORTRESS_KEEP,
        CIVIC_PALACE
    }

    public record GrammarPalette(
        BlockState foundation,
        BlockState column,
        BlockState wall,
        BlockState trim,
        BlockState floor,
        BlockState roof,
        BlockState window
    ) {}

    public static GrammarCulture inferCulture(String cultureStr, int year) {
        if (cultureStr != null) {
            String c = cultureStr.toLowerCase(Locale.ROOT);
            if (c.contains("rome") || c.contains("greek") || c.contains("latin") || c.contains("byzant")) {
                return GrammarCulture.CLASSICAL_GRECO_ROMAN;
            }
            if (c.contains("china") || c.contains("japan") || c.contains("korea") || c.contains("east_asia")) {
                return GrammarCulture.EAST_ASIAN_PAGODA;
            }
            if (c.contains("arab") || c.contains("islam") || c.contains("persia") || c.contains("egypt")) {
                return GrammarCulture.ISLAMIC_SARACENIC;
            }
            if (c.contains("britain") || c.contains("germany") || c.contains("france") || c.contains("europe")) {
                return year >= 1800 ? GrammarCulture.INDUSTRIAL_VICTORIAN : GrammarCulture.MEDIEVAL_GOTHIC;
            }
        }
        if (year < 500) return GrammarCulture.CLASSICAL_GRECO_ROMAN;
        if (year < 1750) return GrammarCulture.MEDIEVAL_GOTHIC;
        if (year < 1920) return GrammarCulture.INDUSTRIAL_VICTORIAN;
        return GrammarCulture.MODERN_METROPOLITAN;
    }

    public static GrammarPalette getPalette(GrammarCulture culture) {
        return switch (culture) {
            case CLASSICAL_GRECO_ROMAN -> new GrammarPalette(
                Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.QUARTZ_PILLAR.defaultBlockState(),
                Blocks.CUT_SANDSTONE.defaultBlockState(),
                Blocks.SMOOTH_QUARTZ.defaultBlockState(),
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                Blocks.TERRACOTTA.defaultBlockState(),
                Blocks.AIR.defaultBlockState() // Open colonnade light
            );
            case MEDIEVAL_GOTHIC -> new GrammarPalette(
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(),
                Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState()
            );
            case EAST_ASIAN_PAGODA -> new GrammarPalette(
                Blocks.STONE.defaultBlockState(),
                Blocks.DARK_OAK_LOG.defaultBlockState(),
                Blocks.RED_TERRACOTTA.defaultBlockState(),
                Blocks.STRIPPED_OAK_WOOD.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.DARK_PRISMARINE.defaultBlockState(),
                Blocks.OAK_FENCE.defaultBlockState()
            );
            case ISLAMIC_SARACENIC -> new GrammarPalette(
                Blocks.SANDSTONE.defaultBlockState(),
                Blocks.CUT_SANDSTONE.defaultBlockState(),
                Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                Blocks.CYAN_GLAZED_TERRACOTTA.defaultBlockState(),
                Blocks.GOLD_BLOCK.defaultBlockState(),
                Blocks.IRON_BARS.defaultBlockState()
            );
            case INDUSTRIAL_VICTORIAN -> new GrammarPalette(
                Blocks.POLISHED_GRANITE.defaultBlockState(),
                Blocks.IRON_BLOCK.defaultBlockState(),
                Blocks.BRICKS.defaultBlockState(),
                Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                Blocks.POLISHED_DIORITE.defaultBlockState(),
                Blocks.DEEPSLATE_TILES.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState()
            );
            case MODERN_METROPOLITAN -> new GrammarPalette(
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.SMOOTH_STONE.defaultBlockState(),
                Blocks.WHITE_CONCRETE.defaultBlockState(),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.TINTED_GLASS.defaultBlockState()
            );
        };
    }

    /**
     * Generates a fully formed architectural plan adhering to historical grammar rules.
     */
    public PlannedBuilding generatePlan(ServerLevel level, BlockPos origin, int width, int depth,
                                        GrammarCulture culture, BuildingGrammarType type, String planId) {
        if (level == null || origin == null) {
            return new PlannedBuilding(planId, null, origin, Map.of(), "Null level/origin");
        }

        int w = Math.max(7, Math.min(32, width));
        int d = Math.max(7, Math.min(32, depth));
        int baseY = TerraCoordinateHelper.getPlacementY(level, origin.getX(), origin.getZ());
        GrammarPalette pal = getPalette(culture);
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        int wallHeight = switch (type) {
            case TEMPLE, CIVIC_PALACE -> 7;
            case FORTRESS_KEEP -> 10;
            case MARKET_HALL -> 5;
            case HOUSE -> 4;
        };

        // 1. Foundation Plinth (Layer 0)
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                int wx = origin.getX() + x;
                int wz = origin.getZ() + z;
                putIfAboveSurface(blocks, level, new BlockPos(wx, baseY, wz), pal.foundation());
                putIfAboveSurface(blocks, level, new BlockPos(wx, baseY + 1, wz), pal.floor());
            }
        }

        // 2. Colonnade & Infill Walls (Layers 2 to 1 + wallHeight)
        int startY = baseY + 2;
        int topY = startY + wallHeight - 1;

        for (int y = startY; y <= topY; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean isPerimeter = (x == 0 || x == w - 1 || z == 0 || z == d - 1);
                    if (!isPerimeter) continue;

                    int wx = origin.getX() + x;
                    int wz = origin.getZ() + z;
                    BlockPos pos = new BlockPos(wx, y, wz);

                    // Colonnade columns placed rhythmically every 3 blocks
                    boolean isCorner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                    boolean isBayColumn = (x % 3 == 0 || z % 3 == 0);

                    if (isCorner || isBayColumn) {
                        putIfAboveSurface(blocks, level, pos, pal.column());
                    } else {
                        // Infill wall with window aperture
                        boolean isWindow = (y >= startY + 2 && y <= topY - 1);
                        boolean isDoor = (z == 0 && (x == w / 2 || x == w / 2 + 1) && y <= startY + 2);

                        if (isDoor) {
                            putIfAboveSurface(blocks, level, pos, Blocks.AIR.defaultBlockState());
                        } else if (isWindow) {
                            putIfAboveSurface(blocks, level, pos, pal.window());
                        } else {
                            putIfAboveSurface(blocks, level, pos, pal.wall());
                        }
                    }
                }
            }
        }

        // 3. Entablature / Projecting Cornice Trim
        int corniceY = topY + 1;
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                if (x == -1 || x == w || z == -1 || z == d) {
                    int wx = origin.getX() + x;
                    int wz = origin.getZ() + z;
                    putIfAboveSurface(blocks, level, new BlockPos(wx, corniceY, wz), pal.trim());
                }
            }
        }

        // 4. Roof Grammar
        applyRoofGrammar(blocks, level, origin, w, d, corniceY + 1, culture, type, pal);

        return new PlannedBuilding(planId, level.dimension(), origin, blocks,
            "Grammar plan: " + culture + " " + type + " (" + w + "x" + d + ") blocks=" + blocks.size());
    }

    private void applyRoofGrammar(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos origin,
                                  int w, int d, int roofBaseY, GrammarCulture culture,
                                  BuildingGrammarType type, GrammarPalette pal) {
        if (culture == GrammarCulture.CLASSICAL_GRECO_ROMAN) {
            // Low-pitch pediment triangular gable
            int halfW = w / 2;
            for (int step = 0; step <= halfW; step++) {
                int ry = roofBaseY + (step / 2);
                for (int z = 0; z < d; z++) {
                    int xLeft = origin.getX() + step;
                    int xRight = origin.getX() + (w - 1 - step);
                    int wz = origin.getZ() + z;
                    putIfAboveSurface(blocks, level, new BlockPos(xLeft, ry, wz), pal.roof());
                    putIfAboveSurface(blocks, level, new BlockPos(xRight, ry, wz), pal.roof());
                }
            }
        } else if (culture == GrammarCulture.EAST_ASIAN_PAGODA) {
            // Overhanging flaring pagoda eaves
            for (int x = -2; x <= w + 1; x++) {
                for (int z = -2; z <= d + 1; z++) {
                    int wx = origin.getX() + x;
                    int wz = origin.getZ() + z;
                    boolean edge = (x <= -1 || x >= w || z <= -1 || z >= d);
                    int ry = edge ? roofBaseY + 1 : roofBaseY;
                    putIfAboveSurface(blocks, level, new BlockPos(wx, ry, wz), pal.roof());
                }
            }
        } else {
            // Pitched gable or flat parapet
            int maxSlope = Math.min(w, d) / 2;
            for (int s = 0; s <= maxSlope; s++) {
                int ry = roofBaseY + s;
                for (int x = s; x < w - s; x++) {
                    for (int z = s; z < d - s; z++) {
                        boolean ring = (x == s || x == w - s - 1 || z == s || z == d - s - 1);
                        if (ring) {
                            int wx = origin.getX() + x;
                            int wz = origin.getZ() + z;
                            putIfAboveSurface(blocks, level, new BlockPos(wx, ry, wz), pal.roof());
                        }
                    }
                }
            }
        }
    }

    private void putIfAboveSurface(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos pos, BlockState state) {
        if (pos == null || state == null) return;
        int surfaceY = TerraCoordinateHelper.getSurfaceY(level, pos.getX(), pos.getZ());
        if (pos.getY() > surfaceY && pos.getY() >= TerraCoordinateHelper.getMinY() && pos.getY() <= TerraCoordinateHelper.getMaxY()) {
            blocks.put(pos.immutable(), state);
        }
    }
}
