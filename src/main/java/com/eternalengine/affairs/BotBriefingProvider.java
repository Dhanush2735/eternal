package com.eternalengine.affairs;

import com.eternalengine.core.WorldStateManager;
import com.eternalengine.incident.IncidentRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Thin bot-facing API for current affairs.
 */
public final class BotBriefingProvider {

    private final CurrentAffairsEngine engine;

    public BotBriefingProvider() {
        this(new CurrentAffairsEngine());
    }

    public BotBriefingProvider(CurrentAffairsEngine engine) {
        this.engine = engine == null ? new CurrentAffairsEngine() : engine;
    }

    public CurrentAffairsBriefing getBriefing(WorldStateManager state,
                                              @Nullable ServerLevel level,
                                              @Nullable BlockPos position,
                                              BotAffairsRole role,
                                              AffairsScope scope) {
        return engine.createBriefing(state, level, position, role, scope, List.of());
    }

    public CurrentAffairsBriefing getBriefing(WorldStateManager state,
                                              @Nullable ServerLevel level,
                                              @Nullable BlockPos position,
                                              BotAffairsRole role,
                                              AffairsScope scope,
                                              List<IncidentRecord> recentIncidents) {
        return engine.createBriefing(state, level, position, role, scope, recentIncidents);
    }

    public String explain(WorldStateManager state,
                          @Nullable ServerLevel level,
                          @Nullable BlockPos position,
                          BotAffairsRole role,
                          AffairsScope scope) {
        return engine.explainForBot(getBriefing(state, level, position, role, scope));
    }
}
