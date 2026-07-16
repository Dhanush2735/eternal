package com.eternalengine.population;

import com.eternalengine.core.WorldStateManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Urbanization foundation.
 */
public final class UrbanizationSystem {
    private final ConcurrentHashMap<String, Double> urbanRatio = new ConcurrentHashMap<>();

    public void updateUrbanization(String civId, int simYear, WorldStateManager state) {
        if (civId == null) {
            return;
        }
        double current = urbanRatio.getOrDefault(civId, baseline(simYear));
        double growth = simYear >= 1750 ? 0.005d : 0.001d;
        double next = Math.min(0.95d, current + growth);
        urbanRatio.put(civId, next);
        if (state != null) {
            state.setFlag("urban.ratio." + civId, Double.toString(next));
            if (next > 0.70d) {
                state.setFlag("urban.slum." + civId, "true");
            }
        }
    }

    public double getUrbanRatio(String civId) {
        return urbanRatio.getOrDefault(civId, 0.05d);
    }

    public boolean isSlumForming(String civId) {
        return getUrbanRatio(civId) > 0.70d;
    }

    public long getCityPopulation(String cityName, long regionPopulation) {
        return Math.max(0L, Math.round(regionPopulation * 0.20d));
    }

    public Map<String, Double> urbanRatios() {
        return Map.copyOf(urbanRatio);
    }

    private double baseline(int year) {
        if (year >= 1950) return 0.45d;
        if (year >= 1750) return 0.20d;
        if (year >= 500) return 0.08d;
        return 0.04d;
    }
}
