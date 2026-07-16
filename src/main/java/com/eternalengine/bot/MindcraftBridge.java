package com.eternalengine.bot;

import com.eternalengine.affairs.CurrentAffairsBriefing;
import com.eternalengine.brain.LlmProposal;
import com.eternalengine.brain.MindcraftBridgeContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory Mindcraft bridge foundation.
 *
 * This bridge is a trust boundary. It publishes server facts to bot IDs and
 * accepts structured proposals back. Proposals still require server validation
 * through LlmProposalValidator/ServerDecisionQueue before any action occurs.
 */
public final class MindcraftBridge implements MindcraftBridgeContract {
    private final Set<String> connectedBots = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CurrentAffairsBriefing> latestBriefings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<LlmProposal>> pendingProposals = new ConcurrentHashMap<>();

    public void connectBot(String botId) {
        if (botId != null && !botId.isBlank()) {
            connectedBots.add(botId);
            pendingProposals.computeIfAbsent(botId, id -> new ConcurrentLinkedQueue<>());
        }
    }

    public void disconnectBot(String botId) {
        if (botId != null) {
            connectedBots.remove(botId);
        }
    }

    @Override
    public void publishBriefing(String botId, CurrentAffairsBriefing briefing) {
        if (botId == null || botId.isBlank() || briefing == null) {
            return;
        }
        connectBot(botId);
        latestBriefings.put(botId, briefing);
    }

    @Override
    public List<LlmProposal> readPendingProposals(String botId, int maxCount) {
        if (botId == null || maxCount <= 0) {
            return List.of();
        }
        ConcurrentLinkedQueue<LlmProposal> queue = pendingProposals.get(botId);
        if (queue == null) {
            return List.of();
        }
        List<LlmProposal> result = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            LlmProposal proposal = queue.poll();
            if (proposal == null) {
                break;
            }
            result.add(proposal);
        }
        return List.copyOf(result);
    }

    @Override
    public boolean isConnected(String botId) {
        return botId != null && connectedBots.contains(botId);
    }

    public void submitProposal(String botId, LlmProposal proposal) {
        if (botId == null || botId.isBlank() || proposal == null) {
            return;
        }
        connectBot(botId);
        pendingProposals.computeIfAbsent(botId, id -> new ConcurrentLinkedQueue<>()).offer(proposal);
    }

    public CurrentAffairsBriefing latestBriefing(String botId) {
        return botId == null ? null : latestBriefings.get(botId);
    }

    public int connectedCount() {
        return connectedBots.size();
    }

    public Set<String> connectedBots() {
        return Set.copyOf(connectedBots);
    }
}
