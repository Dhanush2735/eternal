package com.eternalengine.climate;

import com.eternalengine.agriculture.AgricultureEngine;
import com.eternalengine.core.WorldStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Climate simulation foundation.
 */
public final class ClimateEngine {
    private final List<ClimateEvent> events = new ArrayList<>();
    private final ConcurrentHashMap<String, Double> regionTempModifier = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> regionPrecipModifier = new ConcurrentHashMap<>();
    private final LittleIceAgeSystem littleIceAgeSystem = new LittleIceAgeSystem();

    public ClimateEngine() {
        registerDefaults();
    }

    public void registerEvent(ClimateEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public void tick(int simYear, WorldStateManager state, AgricultureEngine agricultureEngine) {
        if (state == null) {
            return;
        }
        for (String regionId : state.getAllCivilizationIds()) {
            double temp = getTemperatureModifier(regionId, simYear);
            double precip = getPrecipModifier(regionId, simYear);
            state.setFlag("climate.temp." + regionId, Double.toString(temp));
            state.setFlag("climate.precip." + regionId, Double.toString(precip));
            if (agricultureEngine != null) {
                double yieldModifier = yieldModifier(regionId, simYear);
                agricultureEngine.applyClimateEffect(regionId, yieldModifier);
                state.setFlag("climate.yield." + regionId, Double.toString(yieldModifier));
            }
        }
        state.setFlag("climate.little_ice_age", Boolean.toString(littleIceAgeSystem.isActive(simYear)));
    }

    public double getTemperatureModifier(String regionId, int year) {
        double value = regionTempModifier.getOrDefault(regionId, 0.0d);
        value += littleIceAgeSystem.temperatureModifier(year);
        for (ClimateEvent event : getActiveClimateEvents(year)) {
            value += event.tempChangeDegrees();
        }
        return value;
    }

    public double getPrecipModifier(String regionId, int year) {
        double value = regionPrecipModifier.getOrDefault(regionId, 0.0d);
        for (ClimateEvent event : getActiveClimateEvents(year)) {
            value += event.precipChangePercent();
        }
        return value;
    }

    public boolean isLittleIceAgeActive(int year) {
        return littleIceAgeSystem.isActive(year);
    }

    public List<ClimateEvent> getActiveClimateEvents(int year) {
        List<ClimateEvent> active = new ArrayList<>();
        for (ClimateEvent event : events) {
            if (event.isActiveInYear(year)) {
                active.add(event);
            }
        }
        return List.copyOf(active);
    }

    public void setRegionalTempModifier(String regionId, double modifier) {
        if (regionId != null) {
            regionTempModifier.put(regionId, modifier);
        }
    }

    public void setRegionalPrecipModifier(String regionId, double modifier) {
        if (regionId != null) {
            regionPrecipModifier.put(regionId, modifier);
        }
    }

    public List<ClimateEvent> events() {
        return List.copyOf(events);
    }

    private double yieldModifier(String regionId, int year) {
        double modifier = littleIceAgeSystem.yieldModifier(year);
        for (ClimateEvent event : getActiveClimateEvents(year)) {
            modifier *= Math.max(0.0d, 1.0d + event.yieldImpactPercent() / 100.0d);
        }
        return Math.max(0.05d, Math.min(3.0d, modifier));
    }

    private void registerDefaults() {
        registerEvent(new ClimateEvent("little_ice_age", "Little Ice Age", "COOLING",
            1300, 1850, 6.0d, 0.0d, 50.0d, 0.0d, -0.6d, 0.0d, -8.0d));
        registerEvent(new ClimateEvent("year_without_summer", "Year Without a Summer", "VOLCANIC",
            1816, 1816, 8.0d, 0.0d, 0.0d, 0.0d, -2.0d, 10.0d, -25.0d));
    }
}
