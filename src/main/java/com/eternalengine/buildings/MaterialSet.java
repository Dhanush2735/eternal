package com.eternalengine.buildings;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Era-aware material palette for planned buildings.
 */
public final class MaterialSet {
    private final BlockState foundation;
    private final BlockState wall;
    private final BlockState floor;
    private final BlockState roof;
    private final BlockState accent;
    private final BlockState window;

    public MaterialSet(BlockState foundation, BlockState wall, BlockState floor,
                       BlockState roof, BlockState accent, BlockState window) {
        this.foundation = foundation == null ? Blocks.STONE.defaultBlockState() : foundation;
        this.wall = wall == null ? Blocks.STONE_BRICKS.defaultBlockState() : wall;
        this.floor = floor == null ? Blocks.OAK_PLANKS.defaultBlockState() : floor;
        this.roof = roof == null ? Blocks.STONE_SLAB.defaultBlockState() : roof;
        this.accent = accent == null ? this.wall : accent;
        this.window = window == null ? Blocks.GLASS_PANE.defaultBlockState() : window;
    }

    public static MaterialSet forEra(String era) {
        String normalized = era == null ? "ANCIENT" : era.toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "MODERN" -> new MaterialSet(
                Blocks.SMOOTH_STONE.defaultBlockState(),
                Blocks.WHITE_CONCRETE.defaultBlockState(),
                Blocks.SMOOTH_STONE.defaultBlockState(),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState());
            case "INDUSTRIAL" -> new MaterialSet(
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.BRICKS.defaultBlockState(),
                Blocks.SMOOTH_STONE.defaultBlockState(),
                Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                Blocks.IRON_BARS.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState());
            case "MEDIEVAL" -> new MaterialSet(
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                Blocks.OAK_LOG.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState());
            default -> new MaterialSet(
                Blocks.SANDSTONE.defaultBlockState(),
                Blocks.SANDSTONE.defaultBlockState(),
                Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.SANDSTONE_SLAB.defaultBlockState(),
                Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                Blocks.IRON_BARS.defaultBlockState());
        };
    }

    public BlockState foundation() { return foundation; }
    public BlockState wall() { return wall; }
    public BlockState floor() { return floor; }
    public BlockState roof() { return roof; }
    public BlockState accent() { return accent; }
    public BlockState window() { return window; }
}
