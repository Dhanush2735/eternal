package com.eternalengine.buildings;

import com.google.gson.Gson;

/**
 * Immutable building state change record.
 */
public final class BuildingStateChange {
    private static final Gson GSON = new Gson();

    private final int simulationYear;
    private final String type;
    private final String cause;
    private final String description;
    private final String stateBefore;
    private final String stateAfter;
    private final String eraBefore;
    private final String eraAfter;
    private final String ownerBefore;
    private final String ownerAfter;
    private final String purposeBefore;
    private final String purposeAfter;
    private final int damagePercentChange;

    public BuildingStateChange(int simulationYear, String type, String cause, String description,
                               String stateBefore, String stateAfter, String eraBefore, String eraAfter,
                               String ownerBefore, String ownerAfter, String purposeBefore, String purposeAfter,
                               int damagePercentChange) {
        this.simulationYear = simulationYear;
        this.type = safe(type, "UNKNOWN");
        this.cause = safe(cause, "unknown");
        this.description = safe(description, "");
        this.stateBefore = safe(stateBefore, "unknown");
        this.stateAfter = safe(stateAfter, "unknown");
        this.eraBefore = safe(eraBefore, "unknown");
        this.eraAfter = safe(eraAfter, "unknown");
        this.ownerBefore = safe(ownerBefore, "unknown");
        this.ownerAfter = safe(ownerAfter, "unknown");
        this.purposeBefore = safe(purposeBefore, "unknown");
        this.purposeAfter = safe(purposeAfter, "unknown");
        this.damagePercentChange = Math.max(-100, Math.min(100, damagePercentChange));
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static BuildingStateChange fromJson(String json) {
        return GSON.fromJson(json, BuildingStateChange.class);
    }

    private String safe(String value, String fallback) { return value == null ? fallback : value; }

    public int simulationYear() { return simulationYear; }
    public String type() { return type; }
    public String cause() { return cause; }
    public String description() { return description; }
    public String stateBefore() { return stateBefore; }
    public String stateAfter() { return stateAfter; }
    public String eraBefore() { return eraBefore; }
    public String eraAfter() { return eraAfter; }
    public String ownerBefore() { return ownerBefore; }
    public String ownerAfter() { return ownerAfter; }
    public String purposeBefore() { return purposeBefore; }
    public String purposeAfter() { return purposeAfter; }
    public int damagePercentChange() { return damagePercentChange; }
}
