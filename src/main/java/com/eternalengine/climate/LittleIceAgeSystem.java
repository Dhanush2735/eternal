package com.eternalengine.climate;

/**
 * Deterministic Little Ice Age helper.
 */
public final class LittleIceAgeSystem {
    public boolean isActive(int year) {
        return year >= 1300 && year <= 1850;
    }

    public double temperatureModifier(int year) {
        if (!isActive(year)) {
            return 0.0d;
        }
        if (year >= 1600 && year <= 1700) {
            return -1.2d;
        }
        return -0.6d;
    }

    public double yieldModifier(int year) {
        if (!isActive(year)) {
            return 1.0d;
        }
        if (year >= 1600 && year <= 1700) {
            return 0.82d;
        }
        return 0.92d;
    }
}
