package com.eternalengine.data;

import com.eternalengine.core.PersistenceManager;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.skin.SkinManager;
import net.minecraft.server.MinecraftServer;

/**
 * Loads installed world-folder foundation data into the currently running systems.
 */
public final class FoundationDataLoader {
    private FoundationDataLoader() {
    }

    public static LoadResult loadInstalled(MinecraftServer server, SimulationEngine engine) {
        return loadInstalled(server, engine, true);
    }

    /**
     * @param includeVips false for a reload during play ({@code /ee reload}, step 14): reading the VIP files
     *                    again replaces every VIP slot, and with it the claims players hold now
     */
    public static LoadResult loadInstalled(MinecraftServer server, SimulationEngine engine, boolean includeVips) {
        if (server == null || engine == null) {
            return new LoadResult(0, "server or engine missing");
        }
        int loadedGroups = 0;
        // Only touch the event, vip and building databases when the world actually holds an
        // installed copy. HistoricalEventDatabase.loadFromFiles() clears the database before it
        // scans, so calling it against an uninstalled world would empty a table that is otherwise
        // fine -- a background load must never destroy data it was only meant to refresh.
        if (FoundationDataInstaller.hasInstalledData(server)) {
            engine.getHistoricalEventDatabase().loadFromFiles(EternalDataPaths.events(server));
            loadedGroups++;
            if (includeVips) {
                engine.getVipSystem().loadVips(EternalDataPaths.vips(server));
                loadedGroups++;
            }
            engine.getBuildingHistoryDatabase().loadRegion(PersistenceManager.getInstance(),
                EternalDataPaths.buildings(server).resolve("foundation_buildings.json"));
            loadedGroups++;
        }
        // Civilizations always load: the manager falls back to the table shipped in the jar when
        // the world folder has none, so an occupant lookup works in a world nobody installed into.
        engine.getCivilizationManager().loadCivilizations(EternalDataPaths.civilizations(server), com.eternalengine.core.WorldStateManager.getInstance());
        loadedGroups++;
        // Name pools. Shipped, installed into the world, and never read until now -- which is why
        // every NPC outside Rome and Maurya was called "Villager Alex Stone".
        engine.getVillagerManager().loadNames(EternalDataPaths.names(server));
        loadedGroups++;
        // Skin pools. The same three levels the names have - the world's own pack, the copy in the jar,
        // the defaults in code - and the same world folder root, so a server owner overrides one beside
        // the other. Faces and clothes are then decided per person, per place and per year.
        SkinManager.boot(EternalDataPaths.root(server));
        loadedGroups++;
        return new LoadResult(loadedGroups, "foundation data loaded (world folder, or packaged copy)");
    }

    public record LoadResult(int loadedGroups, String message) {
        public String summary() {
            return "Foundation data load groups=" + loadedGroups + " message=" + message;
        }
    }
}
