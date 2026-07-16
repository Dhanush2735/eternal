package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable-ish timeline branch container.
 */
public final class TimelineBranch {

    private final String branchId;
    private final String parentBranchId;
    private final String divergenceEventId;
    private final int divergenceYear;
    private final String divergenceCause;
    private final String playerUuidCause;
    private final long createdTimestamp;
    private final boolean mainTimeline;
    private final List<AlternativeEvent> events;
    private final List<String> permanentlyPrevented;
    private final List<PossibleFutureEvent> stillPossible;
    private String historyLogEntry;

    public TimelineBranch(String branchId, String parentBranchId, String divergenceEventId,
                          int divergenceYear, String divergenceCause, String playerUuidCause,
                          boolean mainTimeline) {
        this.branchId = safe(branchId, mainTimeline ? "main" : "branch_unknown");
        this.parentBranchId = parentBranchId;
        this.divergenceEventId = divergenceEventId;
        this.divergenceYear = divergenceYear;
        this.divergenceCause = safe(divergenceCause, "No cause recorded");
        this.playerUuidCause = playerUuidCause;
        this.createdTimestamp = System.currentTimeMillis();
        this.mainTimeline = mainTimeline;
        this.events = new ArrayList<>();
        this.permanentlyPrevented = new ArrayList<>();
        this.stillPossible = new ArrayList<>();
        this.historyLogEntry = "";
    }

    public static TimelineBranch main(int startYear) {
        return new TimelineBranch("main", null, null, startYear, "Original historical timeline", null, true);
    }

    public void addEvent(AlternativeEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public void addPermanentlyPrevented(String eventId) {
        if (eventId != null && !eventId.isBlank() && !permanentlyPrevented.contains(eventId)) {
            permanentlyPrevented.add(eventId);
        }
    }

    public void addStillPossible(PossibleFutureEvent event) {
        if (event != null) {
            stillPossible.add(event);
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public String branchId() { return branchId; }
    public String parentBranchId() { return parentBranchId; }
    public String divergenceEventId() { return divergenceEventId; }
    public int divergenceYear() { return divergenceYear; }
    public String divergenceCause() { return divergenceCause; }
    public String playerUuidCause() { return playerUuidCause; }
    public long createdTimestamp() { return createdTimestamp; }
    public boolean isMainTimeline() { return mainTimeline; }
    public List<AlternativeEvent> events() { return Collections.unmodifiableList(events); }
    public List<String> permanentlyPrevented() { return Collections.unmodifiableList(permanentlyPrevented); }
    public List<PossibleFutureEvent> stillPossible() { return Collections.unmodifiableList(stillPossible); }
    public String historyLogEntry() { return historyLogEntry; }
    public void setHistoryLogEntry(String historyLogEntry) { this.historyLogEntry = historyLogEntry == null ? "" : historyLogEntry; }
}
