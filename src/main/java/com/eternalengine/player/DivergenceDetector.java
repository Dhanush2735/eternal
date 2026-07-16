package com.eternalengine.player;

import com.eternalengine.core.WorldStateManager;
import com.eternalengine.history.HistoricalEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Foundation for detecting player-caused divergence from historical events.
 */
public final class DivergenceDetector {
    private final ConcurrentHashMap<String, DivergenceCheck> checks = new ConcurrentHashMap<>();
    private final List<PlayerAction> recentActions = Collections.synchronizedList(new ArrayList<>());
    private final int maxRecentActions = 500;

    public void recordAction(PlayerAction action) {
        if (action == null) {
            return;
        }
        recentActions.add(action);
        synchronized (recentActions) {
            while (recentActions.size() > maxRecentActions) {
                recentActions.remove(0);
            }
        }
    }

    public double calculatePreventionConfidence(HistoricalEvent event, WorldStateManager state) {
        if (event == null) {
            return 0.0d;
        }
        double confidence = 0.0d;
        synchronized (recentActions) {
            for (PlayerAction action : recentActions) {
                String detail = action.details().toLowerCase(java.util.Locale.ROOT);
                if (detail.contains(event.id().toLowerCase(java.util.Locale.ROOT))) {
                    confidence += 0.35d;
                }
                if (action.type() == PlayerActionType.KILL_VIP_CANDIDATE && event.type().equalsIgnoreCase("ASSASSINATION")) {
                    confidence += 0.25d;
                }
                if (detail.contains("prevent") || detail.contains("quarantine") || detail.contains("peace")) {
                    confidence += 0.10d;
                }
            }
        }
        if (state != null && state.hasFlag("event.prevented_candidate." + event.id())) {
            confidence += 0.40d;
        }
        return Math.max(0.0d, Math.min(1.0d, confidence));
    }

    public void markEventCandidate(String eventId, double confidence, List<String> actions) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        checks.put(eventId, new DivergenceCheck(eventId, confidence, actions, System.currentTimeMillis()));
    }

    public List<DivergenceCheck> checks() {
        return List.copyOf(checks.values());
    }

    public List<PlayerAction> recentActions(int count) {
        synchronized (recentActions) {
            int from = Math.max(0, recentActions.size() - Math.max(0, count));
            return List.copyOf(recentActions.subList(from, recentActions.size()));
        }
    }

    public record DivergenceCheck(String eventId, double confidence, List<String> playerActions, long firstDetectedMs) {
        public DivergenceCheck {
            confidence = Math.max(0.0d, Math.min(1.0d, confidence));
            playerActions = playerActions == null ? List.of() : List.copyOf(playerActions);
        }
    }
}
