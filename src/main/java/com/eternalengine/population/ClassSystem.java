package com.eternalengine.population;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Social class foundation for taxation and revolution pressure.
 */
public final class ClassSystem {
    private final ConcurrentHashMap<String, ClassDistribution> civClasses = new ConcurrentHashMap<>();

    public void updateDistribution(String civId, int simYear, String techEra) {
        ClassDistribution dist = civClasses.computeIfAbsent(civId, id -> ClassDistribution.ancientDefault());
        if (simYear >= 1750 || (techEra != null && techEra.toUpperCase(java.util.Locale.ROOT).contains("INDUSTRIAL"))) {
            dist.setIndustrialized();
        }
    }

    public double getClassTension(String civId) {
        return civClasses.computeIfAbsent(civId, id -> ClassDistribution.ancientDefault()).inequalityScore();
    }

    public boolean isRevolutionRisk(String civId) {
        return getClassTension(civId) >= 0.70d;
    }

    public long calculateTaxRevenue(String civId, double taxRate, long population) {
        ClassDistribution dist = civClasses.computeIfAbsent(civId, id -> ClassDistribution.ancientDefault());
        double taxableShare = dist.merchant() * 3.0d + dist.artisan() * 2.0d + dist.peasant() * 0.7d + dist.nobility() * 4.0d;
        return Math.max(0L, Math.round(population * Math.max(0.0d, taxRate) * taxableShare));
    }

    public Map<String, ClassDistribution> distributions() {
        return Map.copyOf(civClasses);
    }
}
