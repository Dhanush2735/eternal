package com.eternalengine.agriculture;

import com.eternalengine.core.WorldStateManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agriculture and food-security simulation foundation.
 */
public final class AgricultureEngine {
    private final ConcurrentHashMap<String, FoodData> regionFood = new ConcurrentHashMap<>();
    private final CropSystem cropSystem;
    private final FamineSystem famineSystem;

    public AgricultureEngine() {
        this(new CropSystem(), new FamineSystem());
    }

    public AgricultureEngine(CropSystem cropSystem, FamineSystem famineSystem) {
        this.cropSystem = cropSystem == null ? new CropSystem() : cropSystem;
        this.famineSystem = famineSystem == null ? new FamineSystem() : famineSystem;
    }

    public void tick(int simYear, WorldStateManager state) {
        if (state == null) {
            return;
        }
        for (String civId : state.getAllCivilizationIds()) {
            updateRegion(civId, simYear, state);
        }
        for (String regionId : regionFood.keySet()) {
            famineSystem.checkFamineThreshold(regionId, this, simYear, state);
        }
    }

    public FoodData updateRegion(String regionId, int simYear, WorldStateManager state) {
        long population = state == null ? 1000L : Math.max(1000L, state.getPopulation(regionId));
        String crop = cropSystem.getCropType(regionId, simYear, techEraForYear(simYear));
        long baseYield = cropSystem.getBaseYield(crop, regionId);
        FoodData data = regionFood.computeIfAbsent(regionId,
            id -> new FoodData(id, baseYield, population, 1.0d, crop));
        data.setCurrentCropType(crop);
        data.setDemand(Math.round(population * 1.0d));
        data.setProduction(Math.round(baseYield * data.yieldMultiplier()));
        if (state != null) {
            state.setFlag("food.security." + regionId, Double.toString(data.foodSecurity()));
            state.setFlag("food.crop." + regionId, crop);
        }
        return data;
    }

    public long getYield(String regionId, int year) {
        FoodData data = regionFood.get(regionId);
        if (data != null) {
            return data.production();
        }
        String crop = cropSystem.getCropType(regionId, year, techEraForYear(year));
        return cropSystem.getBaseYield(crop, regionId);
    }

    public double getFoodSecurity(String regionId) {
        FoodData data = regionFood.get(regionId);
        return data == null ? 1.0d : data.foodSecurity();
    }

    public void applyClimateEffect(String regionId, double modifier) {
        FoodData data = regionFood.computeIfAbsent(regionId,
            id -> new FoodData(id, 1000L, 1000L, 1.0d, "mixed_grain"));
        data.setYieldMultiplier(data.yieldMultiplier() * Math.max(0.0d, modifier));
    }

    public void applyTechBoost(String regionId, String tech) {
        FoodData data = regionFood.computeIfAbsent(regionId,
            id -> new FoodData(id, 1000L, 1000L, 1.0d, "mixed_grain"));
        double boost = tech != null && tech.toLowerCase(java.util.Locale.ROOT).contains("irrigation") ? 1.15d : 1.05d;
        data.setYieldMultiplier(data.yieldMultiplier() * boost);
    }

    public Map<String, FoodData> regionFood() {
        return Map.copyOf(regionFood);
    }

    public FamineSystem famineSystem() {
        return famineSystem;
    }

    public CropSystem cropSystem() {
        return cropSystem;
    }

    private String techEraForYear(int year) {
        if (year >= 1945) return "MODERN";
        if (year >= 1750) return "INDUSTRIAL";
        if (year >= 500) return "MEDIEVAL";
        if (year >= -3000) return "ANCIENT";
        return "STONE_AGE";
    }
}
