package com.eternalengine.brain;

import com.eternalengine.affairs.CurrentAffairsBriefing;
import com.eternalengine.anomaly.AnomalyRecord;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for a server-side local brain service.
 *
 * Implementations may talk to a local process later, but must obey timeouts and
 * return proposals only. They must never directly mutate Minecraft state.
 */
public interface LlmBrainBridge {

    CompletableFuture<List<LlmProposal>> proposeFromAffairs(CurrentAffairsBriefing briefing);

    CompletableFuture<List<LlmProposal>> proposeFromAnomalies(List<AnomalyRecord> anomalies);

    /**
     * General diagnostic/developer chat against the same server-local brain
     * bridge used by Minecraft systems. Implementations may keep memory keyed
     * by sessionId. This is advisory text only and must not mutate world state.
     */
    default CompletableFuture<String> chat(String sessionId, String userMessage, List<BrainChatAttachment> attachments) {
        return CompletableFuture.completedFuture("Brain chat is not available for " + getClass().getSimpleName() + ".");
    }

    boolean isAvailable();

    void shutdown();
}
