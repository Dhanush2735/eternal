package com.eternalengine.villager;

import com.eternalengine.affairs.AffairsScope;
import com.eternalengine.affairs.BotAffairsRole;
import com.eternalengine.bot.BotIdentity;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Lightweight behavior foundation for villager agents.
 */
public final class VillagerBehaviorSystem {
    public String explainCurrentAffairs(String villagerId, String displayName, String civId,
                                        BotAffairsRole role, ServerLevel level, BlockPos pos) {
        BotIdentity identity = new BotIdentity(villagerId, displayName, role, "", civId);
        var briefing = SimulationEngine.getInstance().getBotCurrentAffairsService()
            .createBriefing(identity, WorldStateManager.getInstance(), level, pos, AffairsScope.LOCAL);
        return SimulationEngine.getInstance().getBotCurrentAffairsService().explainBriefing(identity, briefing);
    }
}
