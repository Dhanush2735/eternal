package com.eternalengine.player;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.SimulationStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Player lifecycle sync foundation.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class PlayerDataManager {

    private PlayerDataManager() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        int year = SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getCurrentYear() : -3000;
        int effectiveSpeed = SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getEffectiveSpeed() : 10;
        int baseSpeed = SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getBaseSpeed() : 10;
        boolean slowdown = SimulationClockManager.isInitialized() && SimulationClockManager.getInstance().isSlowdownActive();
        String branch = WorldStateManager.isInitialized() ? WorldStateManager.getInstance().getActiveTimelineBranchId() : "main";
        EternalEnginePacketHandler.sendToPlayer(new SimulationStatePacket(year, effectiveSpeed, baseSpeed,
            branch, false, slowdown, slowdown ? "active" : "", List.of()), player);
        data.recordAction("LOGIN", year, "Player logged in");
        // Step 20: the history book, once per player.
        com.eternalengine.knowledge.BookService.welcome(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        int year = SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getCurrentYear() : -3000;
        data.recordAction("LOGOUT", year, "Player logged out");
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getOriginal() instanceof ServerPlayer original)) {
            return;
        }
        IPlayerSimulationData originalData = EternalEngineCapabilities.getPlayerData(original);
        IPlayerSimulationData newData = EternalEngineCapabilities.getPlayerData(player);
        newData.copyFrom(originalData);
    }
}
