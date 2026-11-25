package com.eternalengine.client;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.skin.SkinManager;
import com.eternalengine.skin.SkinPainter;
import com.eternalengine.skin.SkinPng;
import com.eternalengine.skin.SkinPlaceResolver;
import com.eternalengine.skin.SkinHashes;
import com.eternalengine.terrabridge.TerraCoordinateHelper;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bridge: the pool's 64x64 in memory becomes a texture the renderer can bind.
 *
 * <h3>Where this sits</h3>
 * {@code HumanNpcRenderer.getTextureLocation} asks here first and falls back to
 * {@code SkinMatrixRegistry} - so a texture a pack ships still wins, and this is what a person wears
 * when no such file exists. That is the same order of precedence the names have: pack, then the pool.
 *
 * <h3>The year</h3>
 * A person keeps the year he was stood up in, and his clothes follow it. The client is not told that
 * year as a number - the entity syncs an era name - so this uses the simulation clock when it is
 * present (an integrated server, or a client the state packet has reached) and the era's middle year
 * otherwise. Both paths are real; the difference is a decade of wardrobe, not a century of it.
 *
 * <h3>Why a PNG and not pixels</h3>
 * Minecraft's {@code NativeImage} stores colour words in an order a reader has to be told, and being
 * told wrongly paints every face in swapped colours. So the painted skin is encoded as a PNG - a format
 * whose channel order is fixed by its own specification - and handed to {@code NativeImage.read}. The
 * encoder ({@code SkinPng}) is verified headlessly: the self-test decodes its own output with the JDK's
 * image reader and compares every pixel against the painter's memory, and a Python on the other side of
 * the rig opens the same file. Nothing is left to a guess.
 */
public final class SkinTextures {

    /** A place in this cache is a picture, not a person: a crowd shares the pictures it can draw. */
    private static final int MAX_TEXTURES = 1024;

    private static final Map<String, ResourceLocation> CACHE =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
                    if (size() > MAX_TEXTURES) {
                        var tex = Minecraft.getInstance().getTextureManager().getTexture(eldest.getValue());
                        if (tex != null) {
                            tex.close();                  // the GPU memory goes back
                        }
                        return true;
                    }
                    return false;
                }
            };

    private static long painted;
    private static long misses;

    private SkinTextures() {
    }

    /**
     * The texture this person wears right now, or null when he cannot be painted (no client, no
     * manager, an unreadable position) - the caller then keeps whatever it had before.
     */
    public static ResourceLocation of(HumanNpcEntity npc) {
        Minecraft mc = Minecraft.getInstance();
        if (npc == null || mc == null || mc.getTextureManager() == null) {
            return null;
        }
        SkinManager skin = SkinManager.get() != null ? SkinManager.get() : SkinManager.client();
        try {
            double[] geo = TerraCoordinateHelper.toGeo(npc.getBlockX(), npc.getBlockZ());
            SkinPlaceResolver.Place place = skin.placeAt(geo[0], geo[1], npc.getCivilizationId());
            boolean female = "female".equalsIgnoreCase(npc.getGender());
            String role = npc.getRole() == null ? "citizen" : npc.getRole();
            int year = yearFor(npc);
            long day = dayFor(mc);
            String key = skin.textureKey(npc.getUUID(), female, role, place, year, day);
            ResourceLocation cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            misses++;
            ResourceLocation id = new ResourceLocation(EternalEngineMod.MOD_ID,
                    "skins/" + String.format("%016x", SkinHashes.of(key)));
            SkinPainter.Skin paintedSkin = skin.paint(npc.getUUID(), female, role, place, year, day);
            byte[] png = SkinPng.encode(paintedSkin);
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
            mc.getTextureManager().register(id, new DynamicTexture(image));
            CACHE.put(key, id);
            painted++;
            if (painted % 64 == 0) {
                EternalEngineMod.getLogger().info(
                        "[SkinTextures] {} pictures painted, {} people dressed from them, {} in cache",
                        painted, misses, CACHE.size());
            }
            return id;
        } catch (Exception e) {
            EternalEngineMod.getLogger().warn("[SkinTextures] could not dress an NPC: {}", e.toString());
            return null;
        }
    }

    /** The simulation's year, or the era's middle year when the clock has not reached this side. */
    private static int yearFor(HumanNpcEntity npc) {
        try {
            if (com.eternalengine.clock.SimulationClockManager.isInitialized()) {
                return com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear();
            }
        } catch (Throwable ignored) {
            // a dedicated client with no clock: fall through to the era
        }
        String era = npc.getEra() == null ? "ancient" : npc.getEra().toLowerCase(java.util.Locale.ROOT);
        // Step 21: townspeople and figures carry their year as "year_1948" (or "year_-2500"). Until now only the old
        // era names were read here, so on a dedicated server, where the client has no clock, everybody was dressed
        // for the year 400.
        if (era.startsWith("year_")) {
            try {
                return Integer.parseInt(era.substring(5));
            } catch (NumberFormatException ignored) {
                // not a year after all: the era names below
            }
        }
        return switch (era) {
            case "modern" -> 1990;
            case "industrial" -> 1850;
            case "renaissance" -> 1600;
            case "medieval" -> 1200;
            default -> 400;
        };
    }

    /** The day, from the world's own day counter - the clothes change on it, so it must be the game's. */
    private static long dayFor(Minecraft mc) {
        if (mc.level == null) {
            return 0L;
        }
        long time = mc.level.getDayTime();
        return time / 24000L;                     // the same 24000-tick day the clock uses
    }

    /** How many pictures have been painted and how many were missed: for the log and for the command. */
    public static String stats() {
        return painted + " pictures painted, " + misses + " people dressed, " + CACHE.size()
                + " in cache";
    }
}
