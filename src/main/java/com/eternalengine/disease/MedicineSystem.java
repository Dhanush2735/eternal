package com.eternalengine.disease;

import com.eternalengine.people.Health;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Medicine tier and mortality modifier foundation.
 *
 * <h3>Step 38 (C20): the world-wide year tier is no longer the answer</h3>
 *
 * <p>{@link #getMedicineTier(String, int, int)} decides how good the medicine is by the year
 * alone, so <b>in 1945 the entire planet became modern on the same day</b> — penicillin in the
 * Amazon, in rural Bihar, in Vienna, all one number, because the question it asked was "what year
 * is it" and not "where are we".
 *
 * <p>{@link #tierAt(double, double, int)} replaces it, and callers that know where they are should
 * use it. It asks step 38's health register, which knows when each remedy actually reached a
 * place: six of them from step 30a's technology atlas, the rest by running the atlas's own spread
 * function from the year and place they were learned in. The old year-tier method is kept as a
 * fallback for callers that have no coordinates, and it is marked as such.
 */
public final class MedicineSystem {
    private final ConcurrentHashMap<String, MedicineTier> civTiers = new ConcurrentHashMap<>();

    /**
     * <b>The medicine tier for a place.</b> This is the one to use. It is the era of the newest
     * medicine that has actually reached these coordinates, so in 1945 it is MODERN in Oxford and
     * INDUSTRIAL in much of the world, and it will not cure a person with penicillin that has
     * never been within a thousand miles of them.
     */
    public MedicineTier tierAt(double lat, double lon, int year) {
        return switch (Health.tierAt(lat, lon, year)) {
            case STONE_AGE -> MedicineTier.STONE_AGE;
            case ANCIENT -> MedicineTier.ANCIENT;
            case MEDIEVAL -> MedicineTier.MEDIEVAL;
            case RENAISSANCE -> MedicineTier.RENAISSANCE;
            case INDUSTRIAL -> MedicineTier.INDUSTRIAL;
            case MODERN -> MedicineTier.MODERN;
        };
    }

    /**
     * The multiplier a place's medicine applies to dying of anything: 1.0 where nothing has ever
     * reached, and the best multiplier on offer among everything that has. This is the number this
     * class should have been using all along.
     */
    public double careAt(double lat, double lon, int year) {
        return Health.careAt(lat, lon, year);
    }

    /** As {@link #getMortalityModifier(MedicineTier, String)}, but for a place rather than a tier
     * somebody picked. */
    public double mortalityAt(double lat, double lon, int year, String diseaseId) {
        MedicineTier tier = tierAt(lat, lon, year);
        double byTier = getMortalityModifier(tier, diseaseId);
        double byRemedy = Health.careAt(lat, lon, year);
        return Math.min(byTier, byRemedy);
    }

    /**
     * <b>Fallback only.</b> The old world-wide year tier: how good the medicine is in this year,
     * everywhere on Earth at once. Callers that know where they are must use
     * {@link #tierAt(double, double, int)} instead — this method is kept because some callers have
     * no coordinates at all, and it is marked for that reason.
     */
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
