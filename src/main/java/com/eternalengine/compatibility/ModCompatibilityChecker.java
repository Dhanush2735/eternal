package com.eternalengine.compatibility;

import com.eternalengine.EternalEngineMod;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Checks for the presence of optional mod dependencies and caches the results.
 * All optional mod features must use this class to check availability before use.
 */
public class ModCompatibilityChecker {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    // Mod IDs for optional dependencies
    public static final String MOD_MCA_REBORN = "mca";
    public static final String MOD_MINECOLONIES = "minecolonies";
    public static final String MOD_CREATE = "create";
    public static final String MOD_SERENE_SEASONS = "sereneseasons";
    public static final String MOD_GAME_STAGES = "gamestages";
    public static final String MOD_SUPPLEMENTARIES = "supplementaries";
    public static final String MOD_JOURNEYMAP = "journeymap";
    public static final String MOD_XAEROS_MINIMAP = "xaerominimap";
    public static final String MOD_XAEROS_WORLDMAP = "xaeroworldmap";
    
    // Cached mod presence results
    private static final Map<String, Boolean> modPresenceCache = new HashMap<>();
    private static final Map<String, String> modVersionCache = new HashMap<>();
    
    private static boolean initialized = false;
    
    /**
     * Check all optional mods and log results.
     * Should be called once during mod initialization.
     */
    public static void checkAllMods() {
        if (initialized) {
            return;
        }
        
        LOGGER.info("[EE] Checking optional mod dependencies...");
        
        // Check each optional mod
        checkMod(MOD_MCA_REBORN, "MCA Reborn", "Villager life simulation");
        checkMod(MOD_MINECOLONIES, "MineColonies", "City building base");
        checkMod(MOD_CREATE, "Create", "Mechanical transport");
        checkMod(MOD_SERENE_SEASONS, "Serene Seasons", "Agriculture and climate");
        checkMod(MOD_GAME_STAGES, "GameStages", "Era progression gating");
        checkMod(MOD_SUPPLEMENTARIES, "Supplementaries", "Furniture and interiors");
        checkMod(MOD_JOURNEYMAP, "JourneyMap", "Map overlay rendering");
        checkMod(MOD_XAEROS_MINIMAP, "Xaero's Minimap", "Map overlay rendering");
        checkMod(MOD_XAEROS_WORLDMAP, "Xaero's World Map", "Map overlay rendering");
        
        // Summary
        int foundCount = (int) modPresenceCache.values().stream().filter(b -> b).count();
        LOGGER.info("[EE] Found {} optional mod(s) installed", foundCount);
        
        initialized = true;
    }
    
    /**
     * Check a single mod and cache the result
     */
    private static void checkMod(String modId, String displayName, String purpose) {
        boolean present = ModList.get().isLoaded(modId);
        modPresenceCache.put(modId, present);
        
        if (present) {
            ModList.get().getModContainerById(modId).ifPresent(container -> {
                String version = container.getModInfo().getVersion().toString();
                modVersionCache.put(modId, version);
                LOGGER.info("[EE]   ✓ {} v{} detected - {}", displayName, version, purpose);
            });
        } else {
            LOGGER.debug("[EE]   ✗ {} not found - {} features disabled", displayName, purpose);
        }
    }
    
    /**
     * Check if a mod is present (cached)
     */
    public static boolean isModLoaded(String modId) {
        if (!initialized) {
            // Direct check if called before initialization
            return ModList.get().isLoaded(modId);
        }
        return modPresenceCache.getOrDefault(modId, false);
    }
    
    /**
     * Get mod version if present
     */
    public static String getModVersion(String modId) {
        return modVersionCache.getOrDefault(modId, "unknown");
    }
    
    // ============ Convenience methods for each optional mod ============
    
    /**
     * Check if MCA Reborn is present
     */
    public static boolean isMcaRebornPresent() {
        return isModLoaded(MOD_MCA_REBORN);
    }
    
    /**
     * Check if MineColonies is present
     */
    public static boolean isMineColoniesPresent() {
        return isModLoaded(MOD_MINECOLONIES);
    }
    
    /**
     * Check if Create is present
     */
    public static boolean isCreatePresent() {
        return isModLoaded(MOD_CREATE);
    }
    
    /**
     * Check if Serene Seasons is present
     */
    public static boolean isSereneSeasonsPresent() {
        return isModLoaded(MOD_SERENE_SEASONS);
    }
    
    /**
     * Check if GameStages is present
     */
    public static boolean isGameStagesPresent() {
        return isModLoaded(MOD_GAME_STAGES);
    }
    
    /**
     * Check if Supplementaries is present
     */
    public static boolean isSupplementariesPresent() {
        return isModLoaded(MOD_SUPPLEMENTARIES);
    }
    
    /**
     * Check if JourneyMap is present
     */
    public static boolean isJourneymapPresent() {
        return isModLoaded(MOD_JOURNEYMAP);
    }
    
    /**
     * Check if either Xaero's map is present
     */
    public static boolean isXaerosMapPresent() {
        return isModLoaded(MOD_XAEROS_MINIMAP) || isModLoaded(MOD_XAEROS_WORLDMAP);
    }
    
    /**
     * Check if any map mod is present
     */
    public static boolean isAnyMapModPresent() {
        return isJourneymapPresent() || isXaerosMapPresent();
    }
    
    /**
     * Get a summary of loaded optional mods
     */
    public static String getLoadedModsSummary() {
        StringBuilder sb = new StringBuilder("Optional mods: ");
        boolean first = true;
        
        for (Map.Entry<String, Boolean> entry : modPresenceCache.entrySet()) {
            if (entry.getValue()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(entry.getKey());
                first = false;
            }
        }
        
        if (first) {
            sb.append("none");
        }
        
        return sb.toString();
    }
}
