package com.eternalengine.population;

import com.eternalengine.core.WorldStateManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Population simulation foundation.
 */
public final class PopulationEngine {
    private final ConcurrentHashMap<String, PopulationData> regionPopulations = new ConcurrentHashMap<>();

    public void tick(int simYear, WorldStateManager state) {
        if (state == null) {
            return;
        }
        for (String civId : state.getAllCivilizationIds()) {
            PopulationData data = getOrCreate(civId, state.getPopulation(civId));
            applyStatePressures(data, state);
            long delta = data.advanceOneYear();
            state.setPopulation(civId, data.current());
            state.setFlag("population.last_delta." + civId, Long.toString(delta));
        }
    }

    public PopulationData getOrCreate(String regionId, long fallbackPopulation) {
        return regionPopulations.computeIfAbsent(regionId == null ? "global" : regionId,
            id -> new PopulationData(id, Math.max(1000L, fallbackPopulation), 0.025d, 0.018d, Math.max(10_000L, fallbackPopulation * 4L)));
    }

    public long getPopulation(String regionId, WorldStateManager state) {
        PopulationData data = regionPopulations.get(regionId);
        if (data != null) {
            return data.current();
        }
        return state == null ? 0L : state.getPopulation(regionId);
    }

    public long getTotalPopulation(String civId, WorldStateManager state) {
        return getPopulation(civId, state);
    }

    public void applyDiseaseModifier(String regionId, double modifier) {
        getOrCreate(regionId, 1000L).setDiseaseModifier(modifier);
    }

    public void applyFamineModifier(String regionId, double modifier) {
        getOrCreate(regionId, 1000L).setFamineModifier(modifier);
    }

    public void applyWarModifier(String regionId, double modifier) {
        getOrCreate(regionId, 1000L).setWarModifier(modifier);
    }

    public void applyClimateModifier(String regionId, double modifier) {
        getOrCreate(regionId, 1000L).setClimateModifier(modifier);
    }

    public void setCapacity(String regionId, long capacity) {
        getOrCreate(regionId, 1000L).setMaxCapacity(capacity);
    }

    public Map<String, PopulationData> regionPopulations() {
        return Map.copyOf(regionPopulations);
    }

    private void applyStatePressures(PopulationData data, WorldStateManager state) {
        String region = data.regionId();
        if (state.hasFlag("famine.active." + region)) {
            data.setFamineModifier(0.65d);
            data.setDeathRate(Math.min(0.10d, data.deathRate() + 0.015d));
        }
        if (!state.getAllActiveWars().isEmpty()) {
            data.setWarModifier(0.90d);
        }
        if (state.getDisease("outbreak_" + region) != null || state.hasFlag("disease.pressure." + region)) {
            data.setDiseaseModifier(0.80d);
        }
    }
}
