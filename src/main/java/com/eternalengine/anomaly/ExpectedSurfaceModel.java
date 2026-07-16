package com.eternalengine.anomaly;

import com.eternalengine.telemetry.WorldDebugSnapshot;

import java.util.Locale;

/**
 * Deterministic expected-surface model for anomaly triage.
 *
 * This is not a terrain generator. It consumes telemetry produced by Terra++
 * and Eternal's world snapshot layer to identify suspicious mismatches.
 */
public final class ExpectedSurfaceModel {

    public String expectedSurface(WorldDebugSnapshot snapshot, int year) {
        if (snapshot == null || !snapshot.valid()) {
            return "unknown";
        }

        String worldCover = normalize(snapshot.worldCoverClass());
        String terrain = normalize(snapshot.terrainType());

        if (terrain.equals("OCEAN") || terrain.equals("SHALLOW_WATER")
            || terrain.equals("RIVER") || terrain.equals("LAKE")) {
            return "minecraft:water";
        }

        if (snapshot.elevationMeters() >= 4000.0d) {
            return "minecraft:snow_block";
        }

        return switch (worldCover) {
            case "BARE" -> "minecraft:red_sand";
            case "BUILT_UP" -> year >= 1900 ? "minecraft:tuff" : "minecraft:grass_block";
            case "SNOW_ICE" -> "minecraft:snow_block";
            case "SHRUBLAND" -> "minecraft:coarse_dirt";
            case "WATER" -> "minecraft:water";
            case "MANGROVES", "WETLAND" -> "minecraft:grass_block";
            default -> "minecraft:grass_block";
        };
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length()) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized;
    }
}
