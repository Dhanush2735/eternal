package com.eternalengine.population;

/**
 * Mutable population state for one region.
 */
public final class PopulationData {
    private final String regionId;
    private long current;
    private double birthRate;
    private double deathRate;
    private double diseaseModifier;
    private double famineModifier;
    private double warModifier;
    private double climateModifier;
    private long maxCapacity;

    public PopulationData(String regionId, long current, double birthRate, double deathRate, long maxCapacity) {
        this.regionId = regionId == null || regionId.isBlank() ? "global" : regionId;
        this.current = Math.max(0L, current);
        this.birthRate = clampRate(birthRate);
        this.deathRate = clampRate(deathRate);
        this.diseaseModifier = 1.0d;
        this.famineModifier = 1.0d;
        this.warModifier = 1.0d;
        this.climateModifier = 1.0d;
        this.maxCapacity = Math.max(1L, maxCapacity);
    }

    public long advanceOneYear() {
        double netRate = (birthRate - deathRate) * diseaseModifier * famineModifier * warModifier * climateModifier;
        long delta = Math.round(current * netRate);
        current = Math.max(0L, Math.min(maxCapacity, current + delta));
        return delta;
    }

    private double clampRate(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(0.10d, value));
    }

    public String regionId() { return regionId; }
    public long current() { return current; }
    public void setCurrent(long current) { this.current = Math.max(0L, current); }
    public double birthRate() { return birthRate; }
    public void setBirthRate(double birthRate) { this.birthRate = clampRate(birthRate); }
    public double deathRate() { return deathRate; }
    public void setDeathRate(double deathRate) { this.deathRate = clampRate(deathRate); }
    public double diseaseModifier() { return diseaseModifier; }
    public void setDiseaseModifier(double diseaseModifier) { this.diseaseModifier = Math.max(0.0d, diseaseModifier); }
    public double famineModifier() { return famineModifier; }
    public void setFamineModifier(double famineModifier) { this.famineModifier = Math.max(0.0d, famineModifier); }
    public double warModifier() { return warModifier; }
    public void setWarModifier(double warModifier) { this.warModifier = Math.max(0.0d, warModifier); }
    public double climateModifier() { return climateModifier; }
    public void setClimateModifier(double climateModifier) { this.climateModifier = Math.max(0.0d, climateModifier); }
    public long maxCapacity() { return maxCapacity; }
    public void setMaxCapacity(long maxCapacity) { this.maxCapacity = Math.max(1L, maxCapacity); }
}
