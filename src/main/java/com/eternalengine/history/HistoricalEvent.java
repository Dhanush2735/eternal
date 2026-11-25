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
            strOr(json, "type", "kind", "GENERAL"),
            str(json, "severity", "NORMAL"),
            integerOr(json, "year", "start", 0),
            integer(json, "month", 1),
            integer(json, "day", 1),
            dblOr(json, "latitude", "lat", 0.0d),
            dblOr(json, "longitude", "lon", 0.0d),
            str(json, "regionId", "global"),
            conditions,
            stringList(json, "preconditionIds"),
            stringList(json, "alliedFactionIds"),
            stringList(json, "opposedFactionIds"),
            durationYearsOf(json),
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

    // ------------------------------------------------------------------
    // Tolerant readers.
    //
    // The event packs under data/.../history/ do NOT use the key names this class originally
    // expected. Measured across all 15,017 records:
    //     "lat"  x15017, "latitude"   x0
    //     "lon"  x15017, "longitude"  x0
    //     "start" x14944, "year"      x73
    // Reading only the long names silently produced latitude 0.0, longitude 0.0 for every event
    // ever loaded, and year 0 for all but 73 of them. Nothing threw; the events simply all sat at
    // the Gulf of Guinea in year zero.
    //
    // These helpers accept either spelling. They are additive: any file using the long names keeps
    // working exactly as before.
    // ------------------------------------------------------------------

    /** First present key wins. Used where two spellings of the same field both occur in the data. */
    private static String strOr(JsonObject json, String primary, String fallbackKey, String fallback) {
        if (json.has(primary) && !json.get(primary).isJsonNull()) return json.get(primary).getAsString();
        if (json.has(fallbackKey) && !json.get(fallbackKey).isJsonNull()) return json.get(fallbackKey).getAsString();
        return fallback;
    }

    private static int integerOr(JsonObject json, String primary, String fallbackKey, int fallback) {
        if (json.has(primary)) return json.get(primary).getAsInt();
        if (json.has(fallbackKey)) return json.get(fallbackKey).getAsInt();
        return fallback;
    }

    private static double dblOr(JsonObject json, String primary, String fallbackKey, double fallback) {
        if (json.has(primary)) return json.get(primary).getAsDouble();
        if (json.has(fallbackKey)) return json.get(fallbackKey).getAsDouble();
        return fallback;
    }

    /**
     * Duration in years. Explicit {@code durationYears} wins; otherwise it is derived from the
     * {@code end} the packs actually carry. A single-year event ends one year after it starts, which
     * is what {@code HistoryGazetteer.Event.activeIn} already assumes -- it is exclusive on
     * {@code end}. Keeping the two consistent is what lets an in-world intervention be allowed
     * exactly while the event is live, and refused afterwards.
     */
    private static int durationYearsOf(JsonObject json) {
        if (json.has("durationYears")) return Math.max(0, json.get("durationYears").getAsInt());
        if (json.has("end")) {
            int start = json.has("start") ? json.get("start").getAsInt()
                      : (json.has("year") ? json.get("year").getAsInt() : 0);
            return Math.max(0, json.get("end").getAsInt() - start);
        }
        return 0;
    }
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
