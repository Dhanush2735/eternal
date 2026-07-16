package com.eternalengine.brain;

import com.eternalengine.affairs.CurrentAffairsBriefing;

import java.util.List;

/**
 * Contract boundary for Mindcraft-style external agents.
 *
 * Mindcraft agents should receive facts/briefings and return structured
 * proposals. They are not trusted world authority.
 */
public interface MindcraftBridgeContract {

    void publishBriefing(String botId, CurrentAffairsBriefing briefing);

    List<LlmProposal> readPendingProposals(String botId, int maxCount);

    boolean isConnected(String botId);
}
