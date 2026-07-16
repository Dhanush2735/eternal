package com.eternalengine.vehicle;

import com.eternalengine.incident.DifficultyScaler;
import net.minecraft.world.Difficulty;

/**
 * Difficulty-aware vehicle incident risk calculation.
 */
public final class VehicleIncidentRisk {

    private VehicleIncidentRisk() {
    }

    public static double calculate(VehicleRoute route, Difficulty difficulty, boolean hardcore,
                                   double warPressure, double weatherPressure, double infrastructurePressure) {
        if (route == null) {
            return 0.0d;
        }
        double base = route.dangerLevel() / 100.0d;
        double routePurpose = route.militaryRoute() ? 0.08d : 0.0d;
        double tradeExposure = route.tradeRoute() ? 0.03d : 0.0d;
        double pressure = clamp01(warPressure) * 0.12d
            + clamp01(weatherPressure) * 0.08d
            + clamp01(infrastructurePressure) * 0.10d
            + routePurpose + tradeExposure;
        double difficultyMultiplier = DifficultyScaler.negativeMultiplier(difficulty, hardcore);
        return clamp01((base + pressure) * difficultyMultiplier);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
