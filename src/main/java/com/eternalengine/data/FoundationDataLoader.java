package com.eternalengine.data;

import com.eternalengine.core.PersistenceManager;
import com.eternalengine.core.SimulationEngine;
import net.minecraft.server.MinecraftServer;

/**
 * Loads installed world-folder foundation data into the currently running systems.
 */
public final class FoundationDataLoader {
    private FoundationDataLoader() {
    }

    public static LoadResult loadInstalled(MinecraftServer server, SimulationEngine engine) {
        if (server == null || engine == null) {
            return new LoadResult(0, "server or engine missing");
        }
        int loadedGroups = 0;
        engine.getHistoricalEventDatabase().loadFromFiles(EternalDataPaths.events(server));
        loadedGroups++;
        engine.getVipSystem().loadVips(EternalDataPaths.vips(server));
        loadedGroups++;
        engine.getBuildingHistoryDatabase().loadRegion(PersistenceManager.getInstance(),
            EternalDataPaths.buildings(server).resolve("foundation_buildings.json"));
        loadedGroups++;
        return new LoadResult(loadedGroups, "events, vips, buildings loaded from world foundation folders");
    }

    public record LoadResult(int loadedGroups, String message) {
        public String summary() {
            return "Foundation data load groups=" + loadedGroups + " message=" + message;
        }
    }
}
