package com.eternalengine.entity;

/**
 * Which of Minecraft's default skins an NPC wears.
 *
 * <h3>Why this exists</h3>
 * {@code SkinMatrixRegistry} worked out a texture path for each NPC and then threw it away,
 * returning Steve for everyone (Alex for a "female" — and nothing ever set gender, so everybody was
 * Steve). Every person in the world had the same face. That is most of why a crowd of four reads as
 * four clones rather than as four people.
 *
 * <h3>What it does instead</h3>
 * Picks deterministically from the nine default skins Minecraft ships — steve, alex, ari, efe, kai,
 * makena, noor, sunny, zuri — using the NPC's UUID. No texture files, no data pack, nothing to
 * install: the same mechanism the game already uses for players who never chose a skin. The choice
 * is stable for a given NPC across restarts, because a UUID is.
 *
 * <p><b>Why the wide folder.</b> {@code HumanNpcRenderer} builds its model with
 * {@code new PlayerModel<>(..., false)} — the wide-armed model — so a slim-designed texture would
 * sit on wider arms than it was drawn for. Each of the nine characters has both a wide and a slim
 * texture in the jar; this class always names the wide one, which is the one that matches the model.
 *
 * <p>No Minecraft types: this is the part that can be tested without a client.
 */
public final class SkinVariety {

    /**
     * Minecraft's default skins, lowercase as the texture files are named. Order is this class's
     * own; the set is the game's.
     */
    public static final String[] SKINS = {
            "alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"
    };

    /** The folder inside {@code textures/entity/player/} that matches the rendered model. */
    public static final String MODEL_FOLDER = "wide";

    private SkinVariety() {}

    /** Which skin NPC {@code index} wears — deterministic, so a restart does not change faces. */
    public static int indexFor(long uuidMost, long uuidLeast, int skinCount) {
        if (skinCount <= 1) {
            return 0;
        }
        long h = uuidMost * 0x9E3779B97F4A7C15L + uuidLeast * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 29);
        return (int) Math.floorMod(h, skinCount);
    }

    /** The skin name for this NPC. */
    public static String nameFor(long uuidMost, long uuidLeast) {
        return SKINS[indexFor(uuidMost, uuidLeast, SKINS.length)];
    }

    /**
     * The vanilla texture path for a skin name: {@code minecraft:textures/entity/player/wide/<x>.png}.
     */
    public static String texturePath(String skinName) {
        String name = sanitize(skinName);
        return "textures/entity/player/" + MODEL_FOLDER + "/" + name + ".png";
    }

    /** True when this is one of the nine names Minecraft ships a texture for. */
    public static boolean isVanillaSkin(String skinName) {
        String name = sanitize(skinName);
        for (String s : SKINS) {
            if (s.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "steve";
        }
        String lower = input.toLowerCase(java.util.Locale.ROOT);
        return lower.replaceAll("[^a-z0-9_.-]", "_");
    }
}
