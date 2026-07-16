package com.eternalengine.population;

import com.eternalengine.core.WorldStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Migration flow foundation.
 */
public final class MigrationSystem {
    private final ConcurrentHashMap<String, MigrationFlow> flows = new ConcurrentHashMap<>();

    public List<MigrationFlow> processMigration(int simYear, WorldStateManager state) {
        if (state == null) {
            return List.of();
        }
        List<String> regions = new ArrayList<>(state.getAllCivilizationIds());
        if (regions.size() < 2) {
            return List.of();
        }
        List<MigrationFlow> created = new ArrayList<>();
        for (String from : regions) {
            double push = calculatePushFactor(from, state);
            if (push < 0.35d) {
                continue;
            }
            String bestTo = null;
            double bestPull = 0.0d;
            for (String to : regions) {
                if (to.equals(from)) {
                    continue;
                }
                double pull = calculatePullFactor(to, state);
                if (pull > bestPull) {
                    bestPull = pull;
                    bestTo = to;
                }
            }
            if (bestTo != null && bestPull > 0.25d) {
                long flow = Math.round(state.getPopulation(from) * push * bestPull * 0.02d);
                if (flow > 0L) {
                    state.setPopulation(from, Math.max(0L, state.getPopulation(from) - flow));
                    state.setPopulation(bestTo, state.getPopulation(bestTo) + flow);
                    MigrationFlow mf = new MigrationFlow(from, bestTo, flow, push, bestPull);
                    flows.put(from + ":" + bestTo, mf);
                    created.add(mf);
                }
            }
        }
        return List.copyOf(created);
    }

    public double calculatePushFactor(String regionId, WorldStateManager state) {
        if (state == null || regionId == null) {
            return 0.0d;
        }
        double factor = 0.0d;
        if (state.hasFlag("famine.active." + regionId)) factor += 0.35d;
        if (!state.getAllActiveWars().isEmpty()) factor += 0.20d;
        if (state.hasFlag("disease.pressure." + regionId)) factor += 0.20d;
        if (state.hasFlag("oppression." + regionId)) factor += 0.20d;
        return Math.min(1.0d, factor);
    }

    public double calculatePullFactor(String regionId, WorldStateManager state) {
        if (state == null || regionId == null) {
            return 0.0d;
        }
        double factor = 0.30d;
        String food = state.getFlag("food.security." + regionId);
        if (food != null) {
            try {
                factor += Math.min(0.30d, Double.parseDouble(food) * 0.15d);
            } catch (NumberFormatException ignored) {
                factor += 0.05d;
            }
        }
        if (!state.hasFlag("famine.active." + regionId)) factor += 0.15d;
        if (state.getAllActiveWars().isEmpty()) factor += 0.10d;
        return Math.min(1.0d, factor);
    }

    public long getMigrationFlow(String fromRegion, String toRegion) {
        MigrationFlow flow = flows.get(fromRegion + ":" + toRegion);
        return flow == null ? 0L : flow.peoplePerYear();
    }

    public List<MigrationFlow> flows() {
        return List.copyOf(flows.values());
    }
}
