package com.eternalengine.agriculture;

import com.eternalengine.core.WorldStateManager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Famine threshold tracking foundation.
 */
public final class FamineSystem {
    public static final double FAMINE_THRESHOLD = 0.70d;
    public static final double RECOVERY_THRESHOLD = 0.85d;

    private final Set<String> activeFamines = ConcurrentHashMap.newKeySet();

    public boolean checkFamineThreshold(String regionId, AgricultureEngine agricultureEngine, int year, WorldStateManager state) {
        if (regionId == null || agricultureEngine == null) {
            return false;
        }
        double security = agricultureEngine.getFoodSecurity(regionId);
        if (security < FAMINE_THRESHOLD && !activeFamines.contains(regionId)) {
            triggerFamine(regionId, year, state);
            return true;
        }
        if (security > RECOVERY_THRESHOLD && activeFamines.contains(regionId)) {
            endFamine(regionId, year, state);
        }
        return activeFamines.contains(regionId);
    }

    public void triggerFamine(String regionId, int year, WorldStateManager state) {
        if (regionId == null || regionId.isBlank()) {
            return;
        }
        activeFamines.add(regionId);
        if (state != null) {
            state.setFlag("famine.active." + regionId, Integer.toString(year));
            long population = state.getPopulation(regionId);
            if (population > 0L) {
                state.setPopulation(regionId, Math.round(population * 0.98d));
            }
        }
    }

    public void endFamine(String regionId, int year, WorldStateManager state) {
        if (regionId == null) {
            return;
        }
        activeFamines.remove(regionId);
        if (state != null) {
            state.removeFlag("famine.active." + regionId);
            state.setFlag("famine.ended." + regionId, Integer.toString(year));
        }
    }

    public Set<String> activeFamineRegions() {
        return Collections.unmodifiableSet(activeFamines);
    }
}
