package com.eternalengine.agriculture;

/**
 * Mutable regional food state.
 */
public final class FoodData {
    private final String regionId;
    private long production;
    private long demand;
    private double yieldMultiplier;
    private String currentCropType;

    public FoodData(String regionId, long production, long demand, double yieldMultiplier, String currentCropType) {
        this.regionId = regionId == null || regionId.isBlank() ? "global" : regionId;
        this.production = Math.max(0L, production);
        this.demand = Math.max(0L, demand);
        this.yieldMultiplier = Math.max(0.0d, yieldMultiplier);
        this.currentCropType = currentCropType == null || currentCropType.isBlank() ? "wild_gathering" : currentCropType;
    }

    public double foodSecurity() {
        if (demand <= 0L) {
            return production > 0L ? 1.0d : 0.0d;
        }
        return Math.max(0.0d, production / (double) demand);
    }

    public String regionId() { return regionId; }
    public long production() { return production; }
    public void setProduction(long production) { this.production = Math.max(0L, production); }
    public long demand() { return demand; }
    public void setDemand(long demand) { this.demand = Math.max(0L, demand); }
    public double yieldMultiplier() { return yieldMultiplier; }
    public void setYieldMultiplier(double yieldMultiplier) { this.yieldMultiplier = Math.max(0.0d, yieldMultiplier); }
    public String currentCropType() { return currentCropType; }
    public void setCurrentCropType(String currentCropType) { this.currentCropType = currentCropType == null ? this.currentCropType : currentCropType; }
}
