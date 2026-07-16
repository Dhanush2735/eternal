package com.eternalengine.history;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.timeline.TimelineSeed;
import com.eternalengine.timeline.TimelineManager;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic historical event chain foundation.
 */
public final class EventChainEngine {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private final HistoricalEventDatabase database;
    private final TimelineManager timelineManager;
    private final Set<String> triggeredEventIds = ConcurrentHashMap.newKeySet();
    private final Set<String> preventedEventIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> eventTriggerTimes = new ConcurrentHashMap<>();

    public EventChainEngine(HistoricalEventDatabase database, TimelineManager timelineManager) {
        this.database = database == null ? new HistoricalEventDatabase() : database;
        this.timelineManager = timelineManager;
    }

    public List<HistoricalEvent> tick(int simYear, WorldStateManager state) {
        List<HistoricalEvent> pending = database.getPendingEvents(simYear, state, triggeredEventIds, preventedEventIds);
        for (HistoricalEvent event : pending) {
            triggerEvent(event, state);
        }
        return pending;
    }

    public void triggerEvent(HistoricalEvent event, WorldStateManager state) {
        if (event == null || triggeredEventIds.contains(event.id()) || preventedEventIds.contains(event.id())) {
            return;
        }
        triggeredEventIds.add(event.id());
        eventTriggerTimes.put(event.id(), System.currentTimeMillis());
        if (state != null) {
            state.setFlag("event.triggered." + event.id(), Integer.toString(event.year()));
            for (String outcome : event.outcomesIfHappens()) {
                state.setFlag("event.outcome." + event.id() + "." + outcome, "true");
            }
        }
        LOGGER.info("[EventChainEngine] Triggered event {} ({})", event.id(), event.name());
    }

    public void markPrevented(HistoricalEvent event, String playerUuid, String cause, long worldSeed, WorldStateManager state) {
        if (event == null || triggeredEventIds.contains(event.id())) {
            return;
        }
        preventedEventIds.add(event.id());
        if (state != null) {
            state.setFlag("event.prevented." + event.id(), Integer.toString(event.year()));
        }
        if (timelineManager != null) {
            TimelineSeed seed = new TimelineSeed(worldSeed, timelineManager.activeBranchId(), event.id(),
                event.year(), playerUuid == null ? "unknown_player" : playerUuid);
            timelineManager.createDivergence(seed, cause == null ? "Event prevented" : cause, state);
        }
        LOGGER.info("[EventChainEngine] Prevented event {} ({})", event.id(), event.name());
    }

    public double calculatePreventionConfidence(HistoricalEvent event, WorldStateManager state,
                                                List<String> recentPlayerActions) {
        if (event == null || state == null) {
            return 0.0d;
        }
        double score = 0.0d;
        if (recentPlayerActions != null) {
            for (String action : recentPlayerActions) {
                if (action != null && action.toLowerCase(java.util.Locale.ROOT).contains(event.id().toLowerCase(java.util.Locale.ROOT))) {
                    score += 0.45d;
                }
                if (action != null && action.toLowerCase(java.util.Locale.ROOT).contains("prevent")) {
                    score += 0.20d;
                }
            }
        }
        if (state.hasFlag("event.prevented_candidate." + event.id())) {
            score += 0.40d;
        }
        return Math.max(0.0d, Math.min(1.0d, score));
    }

    public boolean isTriggered(String eventId) {
        return triggeredEventIds.contains(eventId);
    }

    public boolean isPrevented(String eventId) {
        return preventedEventIds.contains(eventId);
    }

    public Set<String> getTriggeredEvents() {
        return Collections.unmodifiableSet(triggeredEventIds);
    }

    public Set<String> getPreventedEvents() {
        return Collections.unmodifiableSet(preventedEventIds);
    }

    public HistoricalEventDatabase database() {
        return database;
    }
}
