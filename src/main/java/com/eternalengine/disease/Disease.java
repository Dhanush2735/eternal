package com.eternalengine.disease;

import java.util.List;

/**
 * Immutable disease definition.
 */
public final class Disease {
    private final String id;
    private final String name;
    private final String historicalName;
    private final DiseaseType type;
    private final SpreadType spreadType;
    private final int historicalYear;
    private final double originLat;
    private final double originLon;
    private final double spreadRadiusPerYearKm;
    private final double baseMortalityRate;
    private final int cureYear;
    private final String preventionMethod;
    private final List<String> triggerEventIds;
    private final double tradeRouteMultiplier;
    private final double warMultiplier;
    private final double famineMultiplier;

    public Disease(String id, String name, String historicalName, DiseaseType type, SpreadType spreadType,
                   int historicalYear, double originLat, double originLon, double spreadRadiusPerYearKm,
                   double baseMortalityRate, int cureYear, String preventionMethod, List<String> triggerEventIds,
                   double tradeRouteMultiplier, double warMultiplier, double famineMultiplier) {
        this.id = id == null || id.isBlank() ? "disease_unknown" : id;
        this.name = name == null || name.isBlank() ? this.id : name;
        this.historicalName = historicalName == null ? this.name : historicalName;
        this.type = type == null ? DiseaseType.BACTERIAL : type;
        this.spreadType = spreadType == null ? SpreadType.CONTACT : spreadType;
        this.historicalYear = historicalYear;
        this.originLat = originLat;
        this.originLon = originLon;
        this.spreadRadiusPerYearKm = Math.max(0.0d, spreadRadiusPerYearKm);
        this.baseMortalityRate = clamp01(baseMortalityRate);
        this.cureYear = cureYear;
        this.preventionMethod = preventionMethod == null ? "unknown" : preventionMethod;
        this.triggerEventIds = List.copyOf(triggerEventIds == null ? List.of() : triggerEventIds);
        this.tradeRouteMultiplier = Math.max(0.0d, tradeRouteMultiplier);
        this.warMultiplier = Math.max(0.0d, warMultiplier);
        this.famineMultiplier = Math.max(0.0d, famineMultiplier);
    }

    private double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public String id() { return id; }
    public String name() { return name; }
    public String historicalName() { return historicalName; }
    public DiseaseType type() { return type; }
    public SpreadType spreadType() { return spreadType; }
    public int historicalYear() { return historicalYear; }
    public double originLat() { return originLat; }
    public double originLon() { return originLon; }
    public double spreadRadiusPerYearKm() { return spreadRadiusPerYearKm; }
    public double baseMortalityRate() { return baseMortalityRate; }
    public int cureYear() { return cureYear; }
    public String preventionMethod() { return preventionMethod; }
    public List<String> triggerEventIds() { return triggerEventIds; }
    public double tradeRouteMultiplier() { return tradeRouteMultiplier; }
    public double warMultiplier() { return warMultiplier; }
    public double famineMultiplier() { return famineMultiplier; }
}
