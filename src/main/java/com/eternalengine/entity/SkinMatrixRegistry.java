package com.eternalengine.entity;

import com.eternalengine.EternalEngineMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkinMatrixRegistry handles deterministic cultural and era-based skin selection
 * for HumanNpcEntities across all historical periods and civilizations.
 *
 * <h3>What was wrong</h3>
 * This class built a candidate path — {@code textures/entity/human/<civ>/<era>_<role>.png} — and
 * then discarded it on the next line, always returning Steve or Alex. It also keyed its cache on
 * civ/era/role/gender with no per-entity term, so even a working pick would have made every person
 * of one culture and role identical.
 *
 * <h3>What it does now</h3>
 * <ol>
 *   <li>The candidate path is <b>used</b> when a resource pack or data pack actually ships that
 *       texture. Mod-added skins work by dropping files in, with no code change.</li>
 *   <li>Otherwise the NPC gets one of Minecraft's nine default skins, chosen from its UUID by
 *       {@link SkinVariety} — so a crowd is nine different faces instead of one repeated.</li>
 * </ol>
 */
public final class SkinMatrixRegistry {

    private static final ResourceLocation DEFAULT_STEVE =
        new ResourceLocation("minecraft", SkinVariety.texturePath("steve"));

    /** Cache key includes the entity's UUID: the choice is per person, not per culture. */
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private SkinMatrixRegistry() {}

    /**
     * Determines the skin texture location for a given NPC.
     */
    public static ResourceLocation getTextureLocation(HumanNpcEntity npc) {
        if (npc == null) {
            return DEFAULT_STEVE;
        }

        String civ = npc.getCivilizationId() == null ? "neutral"
                : npc.getCivilizationId().toLowerCase(java.util.Locale.ROOT);
        String era = npc.getEra() == null ? "unknown" : npc.getEra().toLowerCase(java.util.Locale.ROOT);
        String role = npc.getRole() == null ? "citizen" : npc.getRole().toLowerCase(java.util.Locale.ROOT);
        String identity = npc.getUUID() == null ? "unspawned" : npc.getUUID().toString();

        String key = String.format("%s_%s_%s_%s", civ, era, role, identity);

        return TEXTURE_CACHE.computeIfAbsent(key, k -> resolveTexture(npc, civ, era, role));
    }

    private static ResourceLocation resolveTexture(HumanNpcEntity npc, String civ, String era, String role) {
        // A real texture for this culture and role wins over the default set. Path:
        // eternalengine:textures/entity/human/<civ>/<era>_<role>.png
        ResourceLocation candidate = new ResourceLocation(EternalEngineMod.MOD_ID,
                String.format("textures/entity/human/%s/%s_%s.png",
                        sanitize(civ), sanitize(era), sanitize(role)));
        if (textureExists(candidate)) {
            return candidate;
        }

        long most = npc.getUUID() == null ? 0L : npc.getUUID().getMostSignificantBits();
        long least = npc.getUUID() == null ? 0L : npc.getUUID().getLeastSignificantBits();
        return new ResourceLocation("minecraft", SkinVariety.texturePath(SkinVariety.nameFor(most, least)));
    }

    /**
     * Whether the client's resource manager actually has this texture.
     *
     * <p>Guarded rather than trusting: this runs inside the render path, where a resource reload or
     * a headless caller can make the manager unavailable, and a missing texture must degrade to the
     * default skin rather than take the frame down.
     */
    private static boolean textureExists(ResourceLocation location) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft != null
                    && minecraft.getResourceManager() != null
                    && minecraft.getResourceManager().getResource(location).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String sanitize(String input) {
        if (input == null || input.isBlank()) return "default";
        return input.replaceAll("[^a-z0-9_.-]", "_");
    }
}
