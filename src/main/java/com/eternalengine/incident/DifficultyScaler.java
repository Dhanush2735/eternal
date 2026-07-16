package com.eternalengine.incident;

import net.minecraft.world.Difficulty;

/**
 * Difficulty-based incident probability scaling.
 *
 * Negative incidents become more likely on harder Minecraft difficulty. Positive
 * incidents become slightly less likely on harder difficulty.
 */
public final class DifficultyScaler {

    private DifficultyScaler() {
    }

    public static double negativeMultiplier(Difficulty difficulty, boolean hardcore) {
        if (hardcore) {
            return 2.0d;
        }
        if (difficulty == null) {
            return 1.0d;
        }
        return switch (difficulty) {
            case PEACEFUL -> 0.25d;
            case EASY -> 0.70d;
            case NORMAL -> 1.00d;
            case HARD -> 1.50d;
        };
    }

    public static double positiveMultiplier(Difficulty difficulty, boolean hardcore) {
        if (hardcore) {
            return 0.75d;
        }
        if (difficulty == null) {
            return 1.0d;
        }
        return switch (difficulty) {
            case PEACEFUL -> 1.25d;
            case EASY -> 1.10d;
            case NORMAL -> 1.00d;
            case HARD -> 0.85d;
        };
    }
}
