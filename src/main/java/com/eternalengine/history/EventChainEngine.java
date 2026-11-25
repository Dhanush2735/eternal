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

    /**
     * World start (step 14): which events already happened and which were stopped, from the world flags
     * {@link #triggerEvent} and {@link #markPrevented} write. The chain kept both lists in memory only, so
     * after every restart its first tick "triggered" the whole past again, and an event stopped through
     * the chain could happen after all.
     *
     * @return how many events are now known as happened or stopped
     */
    public int restoreFrom(WorldStateManager state) {
        triggeredEventIds.clear();
        preventedEventIds.clear();
        eventTriggerTimes.clear();
        if (state == null) {
            return 0;
        }
        String happened = "event.triggered.";
        String stopped = "event.prevented.";
        for (String key : state.getFlagsWithPrefix(happened).keySet()) {
            triggeredEventIds.add(key.substring(happened.length()));
        }
        for (String key : state.getFlagsWithPrefix(stopped).keySet()) {
            preventedEventIds.add(key.substring(stopped.length()));
        }
        return triggeredEventIds.size() + preventedEventIds.size();
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

    /**
     * An <b>in-world</b> intervention stopped an event that had already begun.
     *
     * <h3>Why this is not {@link #markPrevented}</h3>
     * {@code markPrevented} refuses any event already in {@code triggeredEventIds}. That is correct
     * for a pre-emptive prevention — you stopped it before it started, so it never triggered. But it
     * makes the player's own mechanic impossible: the 26/11 attack is a sixty-hour siege. The
     * attackers are already inside the hotel by the time a player can reach them. Stopping them
     * <em>is</em> stopping the event, and the event is already triggered when they do it.
     *
     * <p>So this path allows a triggered event to be undone while it is still <b>live</b>:
     * from its start year up to and including its final year. Once the event is over, it is over —
     * this returns false and the past stays where it is.
     *
     * <p>The event is removed from {@code triggeredEventIds} on success, so that the in-memory chain
     * state agrees with the branch that was just created. A subsequent tick will not re-trigger it.
     *
     * @return true only if a branch was actually created and history actually changed.
     */
    public boolean stopInWorld(HistoricalEvent event, String playerUuid, String cause, long worldSeed,
                               WorldStateManager state, int currentYear) {
        if (event == null) {
            return false;
        }
        int endYearExclusive = event.year() + Math.max(1, event.durationYears());
        return stopPinInWorld(event.id(), event.name(), event.year(), endYearExclusive,
                playerUuid, cause, worldSeed, state, currentYear);
    }

    /**
     * An in-world intervention stopped a historical pin.
     *
     * <h3>Why this is not {@link #markPrevented}</h3>
     * {@code markPrevented} refuses any event already in {@code triggeredEventIds}. That is correct
     * for a pre-emptive prevention. But it makes the player's own mechanic impossible: 26/11 is a
     * sixty-hour siege, so the attackers are already inside the hotel by the time a player can reach
     * them. Stopping them <em>is</em> stopping the event, and the event has already triggered.
     *
     * <h3>Why it takes raw pin facts instead of a HistoricalEvent</h3>
     * The event chain database holds 76 authored events ({@code events/foundation_events.json}). The
     * gazetteer holds 15,017 pins ({@code history/events.json}, {@code events_world.json.gz}). The two
     * sets barely intersect: {@code mumbai_taj_2008} is a gazetteer pin and is <b>not</b> a chain
     * event. Requiring a {@code HistoricalEvent} made every real intervention impossible -- the
     * detector found the pin, the sink could not resolve it, and nothing happened. So the fork works
     * from the pin's own id and year span, with no chain lookup.
     *
     * <h3>The window</h3>
     * Live from {@code startYear} up to but NOT including {@code endYearExclusive} -- exactly the rule
     * {@code HistoryGazetteer.Event.activeIn} uses, so the detector and the world can never disagree
     * about whether the event is still happening. After that it is the past, and the past is not for
     * sale.
     *
     * @return true only if a branch was actually created and history actually changed.
     */
    public boolean stopPinInWorld(String pinId, String pinName, int startYear, int endYearExclusive,
                                  String playerUuid, String cause, long worldSeed,
                                  WorldStateManager state, int currentYear) {
        if (pinId == null || pinId.isBlank()) {
            return false;
        }
        if (preventedEventIds.contains(pinId)) {
            return false;
        }
        if (currentYear < startYear || currentYear >= endYearExclusive) {
            LOGGER.info("[EventChainEngine] Not live, cannot stop {} in {}: window is [{}, {})",
                    pinId, currentYear, startYear, endYearExclusive);
            return false;
        }
        if (timelineManager == null) {
            return false;
        }

        preventedEventIds.add(pinId);
        triggeredEventIds.remove(pinId);
        if (state != null) {
            state.setFlag("event.prevented." + pinId, Integer.toString(currentYear));
            state.setFlag("event.prevented_in_world." + pinId, Integer.toString(currentYear));
        }

        TimelineSeed seed = new TimelineSeed(worldSeed, timelineManager.activeBranchId(), pinId,
                startYear, playerUuid == null ? "in_world_intervention" : playerUuid);
        timelineManager.createDivergence(seed, cause == null ? "Stopped in the world" : cause, state);

        LOGGER.info("[EventChainEngine] Stopped {} in the world during {} ({})",
                pinId, currentYear, pinName);
        return true;
    }

    /** Look up an event definition by id. Used by the in-world intervention detector. */
    public java.util.Optional<HistoricalEvent> eventById(String id) {
        return database == null ? java.util.Optional.empty() : database.getEvent(id);
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
