package com.eternalengine.buildings;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Building history record foundation.
 */
public final class BuildingRecord {
    private static final Gson GSON = new Gson();

    private String buildingId;
    private String osmId;
    private String wikidataId;
    private String name;
    private double latitude;
    private double longitude;
    private int constructionYear;
    private String era;
    private String currentState;
    private String ownerCivId;
    private String purpose;
    private int damagePercent;
    private int culturalSignificance;
    private int destroyedYear;
    private final List<BuildingStateChange> stateHistory = new ArrayList<>();

    public BuildingRecord(String buildingId, String name, double latitude, double longitude, int constructionYear) {
        this.buildingId = safe(buildingId, "building_unknown");
        this.name = safe(name, this.buildingId);
        this.latitude = latitude;
        this.longitude = longitude;
        this.constructionYear = constructionYear;
        this.era = "UNKNOWN";
        this.currentState = "STANDING";
        this.ownerCivId = "neutral";
        this.purpose = "unknown";
        this.destroyedYear = Integer.MAX_VALUE;
    }

    public boolean isStanding() { return "STANDING".equalsIgnoreCase(currentState) || damagePercent < 80; }
    public boolean isDamaged() { return damagePercent > 0 && damagePercent < 100; }
    public boolean isDestroyed() { return damagePercent >= 100 || "DESTROYED".equalsIgnoreCase(currentState); }

    public void addStateChange(BuildingStateChange change) {
        if (change == null) return;
        stateHistory.add(change);
        this.currentState = change.stateAfter();
        this.era = change.eraAfter();
        this.ownerCivId = change.ownerAfter();
        this.purpose = change.purposeAfter();
        this.damagePercent = Math.max(0, Math.min(100, this.damagePercent + change.damagePercentChange()));
        if (isDestroyed() && destroyedYear == Integer.MAX_VALUE) {
            destroyedYear = change.simulationYear();
        }
    }

    public Optional<BuildingStateChange> getLatestChange() {
        return stateHistory.isEmpty() ? Optional.empty() : Optional.of(stateHistory.get(stateHistory.size() - 1));
    }

    public List<BuildingStateChange> getChangesOfType(String type) {
        if (type == null) return List.of();
        return stateHistory.stream().filter(change -> type.equalsIgnoreCase(change.type())).toList();
    }

    public boolean existsInYear(int year) {
        return year >= constructionYear && year < destroyedYear;
    }

    public String toJson() { return GSON.toJson(this); }
    public static BuildingRecord fromJson(String json) { return GSON.fromJson(json, BuildingRecord.class); }
    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    public String buildingId() { return buildingId; }
    public String osmId() { return osmId; }
    public void setOsmId(String osmId) { this.osmId = osmId; }
    public String wikidataId() { return wikidataId; }
    public void setWikidataId(String wikidataId) { this.wikidataId = wikidataId; }
    public String name() { return name; }
    public void setName(String name) { this.name = safe(name, this.name); }
    public double latitude() { return latitude; }
    public double longitude() { return longitude; }
    public int constructionYear() { return constructionYear; }
    public void setConstructionYear(int constructionYear) { this.constructionYear = constructionYear; }
    public String era() { return era; }
    public void setEra(String era) { this.era = safe(era, this.era); }
    public String currentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = safe(currentState, this.currentState); }
    public String ownerCivId() { return ownerCivId; }
    public void setOwnerCivId(String ownerCivId) { this.ownerCivId = safe(ownerCivId, this.ownerCivId); }
    public String purpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = safe(purpose, this.purpose); }
    public int damagePercent() { return damagePercent; }
    public void setDamagePercent(int damagePercent) { this.damagePercent = Math.max(0, Math.min(100, damagePercent)); }
    public int culturalSignificance() { return culturalSignificance; }
    public void setCulturalSignificance(int culturalSignificance) { this.culturalSignificance = Math.max(0, Math.min(100, culturalSignificance)); }
    public int destroyedYear() { return destroyedYear; }
    public void setDestroyedYear(int destroyedYear) { this.destroyedYear = destroyedYear; }
    public List<BuildingStateChange> stateHistory() { return List.copyOf(stateHistory); }
}
