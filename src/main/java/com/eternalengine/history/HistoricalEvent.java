package com.eternalengine.history;

import com.eternalengine.core.WorldStateManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable historical event definition.
 */
public final class HistoricalEvent {

    private final String id;
    private final String name;
    private final String type;
    private final String severity;
    private final int year;
    private final int month;
    private final int day;
    private final double latitude;
    private final double longitude;
    private final String regionId;
    private final List<TriggerCondition> conditions;
    private final List<String> preconditionIds;
    private final List<String> alliedFactionIds;
    private final List<String> opposedFactionIds;
    private final int durationYears;
    private final long casualtiesMilitary;
    private final long casualtiesCivilian;
    private final double economicImpactPercent;
    private final List<String> outcomesIfHappens;
    private final List<String> nextChainEventIds;
    private final List<String> preventionConsequences;
    private final int autoSlowdownMaxSpeed;
    private final int autoSlowdownMinMinutes;
    private final List<String> vipSlotsActivated;
    private final String alternativeBranchesFile;

    public HistoricalEvent(String id, String name, String type, String severity, int year, int month, int day,
                           double latitude, double longitude, String regionId, List<TriggerCondition> conditions,
                           List<String> preconditionIds, List<String> alliedFactionIds, List<String> opposedFactionIds,
                           int durationYears, long casualtiesMilitary, long casualtiesCivilian,
                           double economicImpactPercent, List<String> outcomesIfHappens,
                           List<String> nextChainEventIds, List<String> preventionConsequences,
                           int autoSlowdownMaxSpeed, int autoSlowdownMinMinutes, List<String> vipSlotsActivated,
                           String alternativeBranchesFile) {
        this.id = safe(id, "unknown_event");
        this.name = safe(name, this.id);
        this.type = safe(type, "GENERAL");
        this.severity = safe(severity, "NORMAL");
        this.year = year;
        this.month = Math.max(1, Math.min(12, month));
        this.day = Math.max(1, Math.min(31, day));
        this.latitude = latitude;
        this.longitude = longitude;
        this.regionId = safe(regionId, "global");
        this.conditions = List.copyOf(conditions == null ? List.of() : conditions);
        this.preconditionIds = immutableStrings(preconditionIds);
        this.alliedFactionIds = immutableStrings(alliedFactionIds);
        this.opposedFactionIds = immutableStrings(opposedFactionIds);
        this.durationYears = Math.max(0, durationYears);
        this.casualtiesMilitary = Math.max(0, casualtiesMilitary);
        this.casualtiesCivilian = Math.max(0, casualtiesCivilian);
        this.economicImpactPercent = economicImpactPercent;
        this.outcomesIfHappens = immutableStrings(outcomesIfHappens);
        this.nextChainEventIds = immutableStrings(nextChainEventIds);
        this.preventionConsequences = immutableStrings(preventionConsequences);
        this.autoSlowdownMaxSpeed = autoSlowdownMaxSpeed;
        this.autoSlowdownMinMinutes = autoSlowdownMinMinutes;
        this.vipSlotsActivated = immutableStrings(vipSlotsActivated);
        this.alternativeBranchesFile = alternativeBranchesFile == null ? "" : alternativeBranchesFile;
    }

    public static HistoricalEvent fromJson(JsonObject json) {
        List<TriggerCondition> conditions = new ArrayList<>();
        if (json.has("conditions") && json.get("conditions").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("conditions")) {
                if (element.isJsonObject()) {
                    conditions.add(TriggerCondition.fromJson(element.getAsJsonObject()));
                }
            }
        }
        return new HistoricalEvent(
            str(json, "id", "unknown_event"),
            str(json, "name", "Unnamed Event"),
            str(json, "type", "GENERAL"),
            str(json, "severity", "NORMAL"),
            integer(json, "year", 0),
            integer(json, "month", 1),
            integer(json, "day", 1),
            dbl(json, "latitude", 0.0d),
            dbl(json, "longitude", 0.0d),
            str(json, "regionId", "global"),
            conditions,
            stringList(json, "preconditionIds"),
            stringList(json, "alliedFactionIds"),
            stringList(json, "opposedFactionIds"),
            integer(json, "durationYears", 0),
            lng(json, "casualtiesMilitary", 0L),
            lng(json, "casualtiesCivilian", 0L),
            dbl(json, "economicImpactPercent", 0.0d),
            stringList(json, "outcomesIfHappens"),
            stringList(json, "nextChainEventIds"),
            stringList(json, "preventionConsequences"),
            integer(json, "autoSlowdownMaxSpeed", 0),
            integer(json, "autoSlowdownMinMinutes", 0),
            stringList(json, "vipSlotsActivated"),
            str(json, "alternativeBranchesFile", "")
        );
    }

    public boolean isTriggered(WorldStateManager state, java.util.Set<String> triggeredEventIds) {
        if (state == null) {
            return false;
        }
        for (String preconditionId : preconditionIds) {
            if (triggeredEventIds == null || !triggeredEventIds.contains(preconditionId)) {
                return false;
            }
        }
        for (TriggerCondition condition : conditions) {
            if (!condition.evaluate(state)) {
                return false;
            }
        }
        return true;
    }

    public String formatYear() {
        return year < 0 ? Math.abs(year) + " BCE" : year + " CE";
    }

    private static List<String> immutableStrings(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? List.of() : values));
    }

    private static String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String str(JsonObject json, String key, String fallback) { return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback; }
    private static int integer(JsonObject json, String key, int fallback) { return json.has(key) ? json.get(key).getAsInt() : fallback; }
    private static long lng(JsonObject json, String key, long fallback) { return json.has(key) ? json.get(key).getAsLong() : fallback; }
    private static double dbl(JsonObject json, String key, double fallback) { return json.has(key) ? json.get(key).getAsDouble() : fallback; }
    private static List<String> stringList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        JsonArray arr = json.getAsJsonArray(key);
        List<String> list = new ArrayList<>();
        for (JsonElement element : arr) {
            if (!element.isJsonNull()) {
                list.add(element.getAsString());
            }
        }
        return list;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String type() { return type; }
    public String severity() { return severity; }
    public int year() { return year; }
    public int month() { return month; }
    public int day() { return day; }
    public double latitude() { return latitude; }
    public double longitude() { return longitude; }
    public String regionId() { return regionId; }
    public List<TriggerCondition> conditions() { return conditions; }
    public List<String> preconditionIds() { return preconditionIds; }
    public List<String> alliedFactionIds() { return alliedFactionIds; }
    public List<String> opposedFactionIds() { return opposedFactionIds; }
    public int durationYears() { return durationYears; }
    public long casualtiesMilitary() { return casualtiesMilitary; }
    public long casualtiesCivilian() { return casualtiesCivilian; }
    public double economicImpactPercent() { return economicImpactPercent; }
    public List<String> outcomesIfHappens() { return outcomesIfHappens; }
    public List<String> nextChainEventIds() { return nextChainEventIds; }
    public List<String> preventionConsequences() { return preventionConsequences; }
    public int autoSlowdownMaxSpeed() { return autoSlowdownMaxSpeed; }
    public int autoSlowdownMinMinutes() { return autoSlowdownMinMinutes; }
    public List<String> vipSlotsActivated() { return vipSlotsActivated; }
    public String alternativeBranchesFile() { return alternativeBranchesFile; }
}
