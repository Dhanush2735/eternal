package com.eternalengine.bot;

import com.eternalengine.affairs.BotAffairsRole;

/**
 * Identity and role lens for a bot/person in Eternal Engine.
 */
public final class BotIdentity {
    private final String botId;
    private final String displayName;
    private final BotAffairsRole affairsRole;
    private final String linkedVipId;
    private final String civilizationId;

    public BotIdentity(String botId, String displayName, BotAffairsRole affairsRole,
                       String linkedVipId, String civilizationId) {
        this.botId = botId == null || botId.isBlank() ? "bot_unknown" : botId;
        this.displayName = displayName == null || displayName.isBlank() ? this.botId : displayName;
        this.affairsRole = affairsRole == null ? BotAffairsRole.VILLAGER : affairsRole;
        this.linkedVipId = linkedVipId == null ? "" : linkedVipId;
        this.civilizationId = civilizationId == null || civilizationId.isBlank() ? "neutral" : civilizationId;
    }

    public String botId() { return botId; }
    public String displayName() { return displayName; }
    public BotAffairsRole affairsRole() { return affairsRole; }
    public String linkedVipId() { return linkedVipId; }
    public String civilizationId() { return civilizationId; }
}
