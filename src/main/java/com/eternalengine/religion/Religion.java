package com.eternalengine.religion;

import java.util.List;

/**
 * Religion definition/state foundation.
 */
public final class Religion {
    private final String id;
    private String name;
    private int foundedYear;
    private double originLat;
    private double originLon;
    private double spreadRatePerYear;
    private long currentFollowers;
    private double politicalPower;
    private final List<String> triggerEventIds;
    private final List<String> conflictReligionIds;
    private boolean allowsHolyWar;
    private boolean requiresConversion;

    public Religion(String id, String name, int foundedYear, double originLat, double originLon,
                    double spreadRatePerYear, long currentFollowers, double politicalPower,
                    List<String> triggerEventIds, List<String> conflictReligionIds,
                    boolean allowsHolyWar, boolean requiresConversion) {
        this.id = id == null || id.isBlank() ? "religion_unknown" : id;
        this.name = name == null || name.isBlank() ? this.id : name;
        this.foundedYear = foundedYear;
        this.originLat = originLat;
        this.originLon = originLon;
        this.spreadRatePerYear = Math.max(0.0d, spreadRatePerYear);
        this.currentFollowers = Math.max(0L, currentFollowers);
        this.politicalPower = clamp100(politicalPower);
        this.triggerEventIds = List.copyOf(triggerEventIds == null ? List.of() : triggerEventIds);
        this.conflictReligionIds = List.copyOf(conflictReligionIds == null ? List.of() : conflictReligionIds);
        this.allowsHolyWar = allowsHolyWar;
        this.requiresConversion = requiresConversion;
    }

    public boolean isFounded(int year) {
        return year >= foundedYear;
    }

    public void addFollowers(long amount) {
        currentFollowers = Math.max(0L, currentFollowers + Math.max(0L, amount));
    }

    public void adjustPoliticalPower(double delta) {
        politicalPower = clamp100(politicalPower + delta);
    }

    private double clamp100(double value) {
        if (Double.isNaN(value)) return 0.0d;
        return Math.max(0.0d, Math.min(100.0d, value));
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name == null ? this.name : name; }
    public int foundedYear() { return foundedYear; }
    public void setFoundedYear(int foundedYear) { this.foundedYear = foundedYear; }
    public double originLat() { return originLat; }
    public double originLon() { return originLon; }
    public double spreadRatePerYear() { return spreadRatePerYear; }
    public long currentFollowers() { return currentFollowers; }
    public double politicalPower() { return politicalPower; }
    public List<String> triggerEventIds() { return triggerEventIds; }
    public List<String> conflictReligionIds() { return conflictReligionIds; }
    public boolean allowsHolyWar() { return allowsHolyWar; }
    public void setAllowsHolyWar(boolean allowsHolyWar) { this.allowsHolyWar = allowsHolyWar; }
    public boolean requiresConversion() { return requiresConversion; }
    public void setRequiresConversion(boolean requiresConversion) { this.requiresConversion = requiresConversion; }
}
