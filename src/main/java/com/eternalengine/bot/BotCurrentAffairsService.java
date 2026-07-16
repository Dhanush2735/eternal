package com.eternalengine.bot;

import com.eternalengine.affairs.AffairsScope;
import com.eternalengine.affairs.CurrentAffairsBriefing;
import com.eternalengine.affairs.CurrentAffairsEngine;
import com.eternalengine.core.WorldStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot-facing current affairs service.
 *
 * Bots receive server facts from this service and may explain/rephrase them.
 * They must not invent facts that are absent from the briefing.
 */
public final class BotCurrentAffairsService {
    private final CurrentAffairsEngine currentAffairsEngine;
    private final ConcurrentHashMap<String, CurrentAffairsBriefing> lastBriefings = new ConcurrentHashMap<>();

    public BotCurrentAffairsService(CurrentAffairsEngine currentAffairsEngine) {
        this.currentAffairsEngine = currentAffairsEngine == null ? new CurrentAffairsEngine() : currentAffairsEngine;
    }

    public CurrentAffairsBriefing createBriefing(BotIdentity bot,
                                                 WorldStateManager state,
                                                 @Nullable ServerLevel level,
                                                 @Nullable BlockPos position,
                                                 AffairsScope scope) {
        BotIdentity safeBot = bot == null
            ? new BotIdentity("bot_unknown", "Unknown Bot", null, null, null)
            : bot;
        CurrentAffairsBriefing briefing = currentAffairsEngine.createBriefing(
            state, level, position, safeBot.affairsRole(), scope, java.util.List.of());
        lastBriefings.put(safeBot.botId(), briefing);
        return briefing;
    }

    public String explainBriefing(BotIdentity bot, CurrentAffairsBriefing briefing) {
        if (briefing == null) {
            return "I do not have reliable current-affairs facts right now.";
        }
        String name = bot == null ? "Bot" : bot.displayName();
        return name + ": " + currentAffairsEngine.explainForBot(briefing);
    }

    public CurrentAffairsBriefing getLastBriefing(String botId) {
        return botId == null ? null : lastBriefings.get(botId);
    }

    public int cachedBriefingCount() {
        return lastBriefings.size();
    }
}
