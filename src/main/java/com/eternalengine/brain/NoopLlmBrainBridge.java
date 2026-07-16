package com.eternalengine.brain;

import com.eternalengine.affairs.CurrentAffairsBriefing;
import com.eternalengine.anomaly.AnomalyRecord;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Safe default brain bridge used when no local brain service is configured.
 */
public final class NoopLlmBrainBridge implements LlmBrainBridge {

    @Override
    public CompletableFuture<List<LlmProposal>> proposeFromAffairs(CurrentAffairsBriefing briefing) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<LlmProposal>> proposeFromAnomalies(List<AnomalyRecord> anomalies) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<String> chat(String sessionId, String userMessage, List<BrainChatAttachment> attachments) {
        return CompletableFuture.completedFuture("Brain chat is currently using NoopLlmBrainBridge, so no remote LLM gateway is connected. "
            + "Eternal Engine does not use local LLMs. Configure [brainGateway] with LiteLLM as primary and Portkey as secondary in eternalengine-server.toml, then run /ee brain remote-start.");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void shutdown() {
        // No resources to release.
    }
}
