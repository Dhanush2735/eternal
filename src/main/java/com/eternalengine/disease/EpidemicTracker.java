package com.eternalengine.disease;

import com.eternalengine.core.WorldStateManager;

/**
 * Epidemic scale and quarantine effectiveness foundation.
 */
public final class EpidemicTracker {
    public EpidemicScale detectEpidemicThreshold(DiseaseOutbreak outbreak) {
        if (outbreak == null) {
            return EpidemicScale.LOCAL;
        }
        int regions = outbreak.affectedRegionIds().size();
        if (regions >= 12) {
            return EpidemicScale.PANDEMIC;
        }
        if (regions >= 4) {
            return EpidemicScale.REGIONAL;
        }
        return EpidemicScale.LOCAL;
    }

    public boolean checkQuarantineZone(String regionId, WorldStateManager state) {
        return state != null && regionId != null && state.hasFlag("quarantine." + regionId);
    }

    public double calculateQuarantineEffectiveness(String regionId, int year, WorldStateManager state) {
        if (!checkQuarantineZone(regionId, state)) {
            return 0.0d;
        }
        if (year >= 1900) {
            return 0.65d;
        }
        if (year >= 1400) {
            return 0.45d;
        }
        return 0.25d;
    }
}
