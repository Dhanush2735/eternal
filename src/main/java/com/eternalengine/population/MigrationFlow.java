package com.eternalengine.population;

/**
 * Immutable annual migration flow.
 */
public record MigrationFlow(String fromRegion, String toRegion, long peoplePerYear, double pushFactor, double pullFactor) {
    public MigrationFlow {
        if (fromRegion == null || fromRegion.isBlank()) {
            fromRegion = "unknown_from";
        }
        if (toRegion == null || toRegion.isBlank()) {
            toRegion = "unknown_to";
        }
        peoplePerYear = Math.max(0L, peoplePerYear);
        pushFactor = Math.max(0.0d, Math.min(1.0d, pushFactor));
        pullFactor = Math.max(0.0d, Math.min(1.0d, pullFactor));
    }
}
