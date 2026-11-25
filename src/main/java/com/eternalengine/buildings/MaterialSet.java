package com.eternalengine.buildings;

import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.towns.BuildingCulture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

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

    /**
     * Step 33 (systems B9, E16): the palette of a place and a year, read off the building culture
     * register. Where the register has nothing (a missing pack, a point off the country grid) the
     * era palette is used instead, so nothing that worked before stops working.
     *
     * <p>The words a row was written with are the authority: "mud brick (banco)" becomes packed mud
     * brick, "corrugated iron" is only said where Portland cement has come, and a house with no
     * glass in its windows keeps its bars. The technology atlas has already been asked by the
     * register, so the palette moves with the year inside one row: the same street is of earth in
     * 1850 and of concrete block in 1950.
     */
    public static MaterialSet forPlace(double lat, double lon, int year, SettlementRank rank) {
        java.util.Optional<BuildingCulture.Reading> r =
                BuildingCulture.get().at(lat, lon, year, rank == null ? SettlementRank.TOWN : rank);
        if (r.isEmpty()) {
            return forEra(eraForYear(year));
        }
        BuildingCulture.Reading v = r.get();
        BlockState wall = wallBlock(v.wall());
        return new MaterialSet(foundationBlock(v.wall()), wall, floorBlock(v.floor()), roofBlock(v.roof()),
                accentBlock(v.wall()), v.glazed() ? Blocks.GLASS_PANE.defaultBlockState()
                        : Blocks.IRON_BARS.defaultBlockState());
    }

    /**
     * Step 33 (system B13): what the lanes of a place are surfaced with, in a year. Read from the
     * same register, in the same way, and falling back to the era's own answer.
     */
    public static BlockState roadFor(double lat, double lon, int year, SettlementRank rank) {
        java.util.Optional<BuildingCulture.Reading> r =
                BuildingCulture.get().at(lat, lon, year, rank == null ? SettlementRank.TOWN : rank);
        String words = r.map(BuildingCulture.Reading::surface).orElse("");
        String w = words.toLowerCase(Locale.ROOT);
        if (w.contains("asphalt") || w.contains("tarmac")) {
            return Blocks.BLACK_CONCRETE.defaultBlockState();
        }
        if (w.contains("concrete")) {
            return Blocks.SMOOTH_STONE.defaultBlockState();
        }
        if (w.contains("sett") || w.contains("cobble")) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (w.contains("brick")) {
            return Blocks.BRICKS.defaultBlockState();
        }
        if (w.contains("slab") || w.contains("flag") || w.contains("stone")) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (w.contains("macadam") || w.contains("gravel") || w.contains("shell") || w.contains("coral")) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        return Blocks.DIRT_PATH.defaultBlockState();
    }

    /** The era a year falls in, for the fallbacks: the same four the era palette already uses. */
    public static String eraForYear(int year) {
        if (year >= 1900) {
            return "MODERN";
        }
        if (year >= 1760) {
            return "INDUSTRIAL";
        }
        if (year >= 500) {
            return "MEDIEVAL";
        }
        return "ANCIENT";
    }

    private static BlockState wallBlock(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT);
        // A wall built of one named stone keeps the stone even when the row also names mud:
        // Great Zimbabwe is "coursed granite laid dry, or daga over poles", and it is the granite
        // that is standing. "Mud brick on a stone footing" is the other way round, and must not
        // match here, so only the stones a wall is *made* of are listed.
        if (w.contains("granite") || w.contains("coursed") || w.contains("marble")
                || w.contains("limestone") || w.contains("sandstone")) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (w.contains("concrete")) {
            return Blocks.WHITE_CONCRETE.defaultBlockState();
        }
        if (w.contains("mud brick") || w.contains("adobe") || w.contains("banco") || w.contains("pis")
                || w.contains("daga")) {
            return Blocks.MUD_BRICKS.defaultBlockState();
        }
        if (w.contains("brick")) {
            return Blocks.BRICKS.defaultBlockState();
        }
        if (w.contains("turf") || w.contains("sod") || w.contains("earth") || w.contains("rammed")) {
            return Blocks.PACKED_MUD.defaultBlockState();
        }
        if (w.contains("log") || w.contains("timber") || w.contains("bark") || w.contains("post")) {
            return Blocks.SPRUCE_PLANKS.defaultBlockState();
        }
        if (w.contains("thatch") || w.contains("palm") || w.contains("bamboo") || w.contains("mat")
                || w.contains("felt") || w.contains("hide") || w.contains("skin") || w.contains("cloth")) {
            return Blocks.BAMBOO_MOSAIC.defaultBlockState();
        }
        if (w.contains("granite") || w.contains("laterite") || w.contains("coral") || w.contains("tuff")
                || w.contains("fieldstone") || w.contains("rubble") || w.contains("stone")) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (w.contains("wattle") || w.contains("daub") || w.contains("plank") || w.contains("lattice")
                || w.contains("weatherboard")) {
            return Blocks.OAK_PLANKS.defaultBlockState();
        }
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    private static BlockState foundationBlock(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT);
        if (w.contains("concrete")) {
            return Blocks.SMOOTH_STONE.defaultBlockState();
        }
        if (w.contains("log") || w.contains("timber") || w.contains("post") || w.contains("plank")
                || w.contains("bark")) {
            return Blocks.OAK_LOG.defaultBlockState();
        }
        if (w.contains("mud") || w.contains("earth") || w.contains("turf") || w.contains("thatch")) {
            return Blocks.PACKED_MUD.defaultBlockState();
        }
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    private static BlockState accentBlock(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT);
        if (w.contains("log") || w.contains("timber") || w.contains("post") || w.contains("bark")) {
            return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        }
        if (w.contains("mud") || w.contains("adobe") || w.contains("banco") || w.contains("earth")) {
            return Blocks.PACKED_MUD.defaultBlockState();
        }
        if (w.contains("granite") || w.contains("laterite") || w.contains("stone")) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        return Blocks.STONE_SLAB.defaultBlockState();
    }

    private static BlockState roofBlock(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT);
        if (w.contains("corrugated") || w.contains("iron") || w.contains("metal")) {
            return Blocks.GRAY_CONCRETE.defaultBlockState();
        }
        if (w.contains("concrete")) {
            return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        }
        if (w.contains("slate")) {
            return Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        if (w.contains("tile")) {
            return Blocks.RED_TERRACOTTA.defaultBlockState();
        }
        if (w.contains("shingle") || w.contains("shake") || w.contains("board")) {
            return Blocks.DARK_OAK_PLANKS.defaultBlockState();
        }
        if (w.contains("flat") || w.contains("mud") || w.contains("earth")) {
            return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        }
        if (w.contains("snow") || w.contains("ice")) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        return Blocks.HAY_BLOCK.defaultBlockState();   // thatch: grass, reed, palm, straw
    }

    private static BlockState floorBlock(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT);
        if (w.contains("concrete") || w.contains("tiles") || w.contains("tile")) {
            return Blocks.SMOOTH_STONE.defaultBlockState();
        }
        if (w.contains("stone") || w.contains("flag") || w.contains("slab")) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (w.contains("board") || w.contains("plank") || w.contains("tatami") || w.contains("mat")) {
            return Blocks.OAK_PLANKS.defaultBlockState();
        }
        if (w.contains("brick")) {
            return Blocks.BRICKS.defaultBlockState();
        }
        return Blocks.PACKED_MUD.defaultBlockState();   // beaten earth, swept and cow-dunged
    }

    public BlockState foundation() { return foundation; }
    public BlockState wall() { return wall; }
    public BlockState floor() { return floor; }
    public BlockState roof() { return roof; }
    public BlockState accent() { return accent; }
    public BlockState window() { return window; }
}
