package com.eternalengine.player;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import org.slf4j.Logger;

/**
 * Routes player actions into capability history, reputation, and world flags.
 */
public final class PlayerActionRouter {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    public void route(PlayerAction action) {
        if (action == null || action.playerUuid() == null) {
            return;
        }
        WorldStateManager state = WorldStateManager.getInstance();
        state.setFlag("player.last_action." + action.playerUuid(), action.type().name());
        state.setFlag("player.last_action_year." + action.playerUuid(), Integer.toString(action.year()));
        tryApplyReputation(action);
    }

    private void tryApplyReputation(PlayerAction action) {
        var server = SimulationEngine.isInitialized() ? SimulationEngine.getInstance().getServer() : null;
        if (server == null) {
            return;
        }
        var player = server.getPlayerList().getPlayer(action.playerUuid());
        if (player == null) {
            return;
        }
        try {
            IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
            data.recordAction(action.type().name(), action.year(), action.details());
            String repAction = reputationAction(action.type());
            if (repAction != null) {
                SimulationEngine.getInstance().getReputationEngine().applyAction(data, repAction);
            }
        } catch (Exception e) {
            LOGGER.debug("[PlayerActionRouter] Failed to route reputation action: {}", e.getMessage());
        }
    }

    private String reputationAction(PlayerActionType type) {
        return switch (type) {
            case KILL_VILLAGER -> "ORDER_CIVILIAN_MASSACRE";
            case KILL_PLAYER -> "WIN_MAJOR_BATTLE";
            case KILL_HOSTILE -> "WIN_MAJOR_BATTLE";
            case KILL_VIP_CANDIDATE -> null; // VIP takeover system applies detailed reputation consequences.
            case CLAIM_VIP -> "GIVE_INSPIRING_SPEECH";
            default -> null;
        };
    }

    public int currentYear() {
        return SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
    }
}
