package com.eternalengine.disease;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Medicine tier and mortality modifier foundation.
 */
public final class MedicineSystem {
    private final ConcurrentHashMap<String, MedicineTier> civTiers = new ConcurrentHashMap<>();

    public MedicineTier getMedicineTier(String civId, int simYear, int techLevel) {
        if (civId != null && civTiers.containsKey(civId)) {
            return civTiers.get(civId);
        }
        if (simYear >= 1945 || techLevel >= 8) {
            return MedicineTier.MODERN;
        }
        if (simYear >= 1800 || techLevel >= 6) {
            return MedicineTier.INDUSTRIAL;
        }
        if (simYear >= 1450 || techLevel >= 4) {
            return MedicineTier.RENAISSANCE;
        }
        if (simYear >= 500 || techLevel >= 2) {
            return MedicineTier.MEDIEVAL;
        }
        if (simYear >= -3000) {
            return MedicineTier.ANCIENT;
        }
        return MedicineTier.STONE_AGE;
    }

    public double getMortalityModifier(MedicineTier tier, String diseaseId) {
        if (tier == null) {
            tier = MedicineTier.STONE_AGE;
        }
        return switch (tier) {
            case STONE_AGE -> 1.0d;
            case ANCIENT -> 0.9d;
            case MEDIEVAL -> 1.1d;
            case RENAISSANCE -> 0.8d;
            case INDUSTRIAL -> 0.5d;
            case MODERN -> 0.1d;
        };
    }

    public void advanceMedicine(String civId, MedicineTier tier) {
        if (civId != null && !civId.isBlank() && tier != null) {
            civTiers.put(civId, tier);
        }
    }

    public int getEarliestCureYear(Disease disease) {
        return disease == null ? -1 : disease.cureYear();
    }
}
