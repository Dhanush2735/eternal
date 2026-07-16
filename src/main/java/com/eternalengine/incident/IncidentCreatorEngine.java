package com.eternalengine.incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic incident generator.
 *
 * This engine is server-authoritative and does not use LLMs. LLM systems may
 * later narrate generated incidents, but they cannot decide or mutate them.
 */
public final class IncidentCreatorEngine {

    private static final IncidentType[] POSITIVE_TYPES = {
        IncidentType.GOOD_HARVEST,
        IncidentType.TRADE_BOOM,
        IncidentType.DISEASE_CONTAINED,
        IncidentType.POPULAR_REFORM,
        IncidentType.SUPPLY_BREAKTHROUGH,
        IncidentType.TECH_BREAKTHROUGH,
        IncidentType.SUCCESSFUL_DIPLOMACY
    };

    private static final IncidentType[] NEGATIVE_TYPES = {
        IncidentType.FAMINE,
        IncidentType.PLAGUE,
        IncidentType.REBELLION,
        IncidentType.TRADE_ROUTE_RAID,
        IncidentType.VEHICLE_ACCIDENT,
        IncidentType.BORDER_SKIRMISH,
        IncidentType.ECONOMIC_CRASH,
        IncidentType.ASSASSINATION_ATTEMPT,
        IncidentType.INFRASTRUCTURE_COLLAPSE
    };

    public List<IncidentRecord> createIncidents(IncidentContext context, int maxIncidents) {
        if (context == null || maxIncidents <= 0) {
            return List.of();
        }

        long seed = IncidentSeed.stableLong(
            context.worldSeed(), context.branchId(), context.year(), context.regionId(), context.difficulty().name(), context.hardcore()
        );
        Random random = new Random(seed);
        List<IncidentRecord> incidents = new ArrayList<>();

        double negativeChance = negativeChance(context);
        double positiveChance = positiveChance(context);

        for (int index = 0; index < maxIncidents; index++) {
            double roll = random.nextDouble();
            if (roll < negativeChance) {
                IncidentType type = NEGATIVE_TYPES[random.nextInt(NEGATIVE_TYPES.length)];
                incidents.add(createRecord(context, type, negativeChance, seed, index, random, true));
            } else if (roll < negativeChance + positiveChance) {
                IncidentType type = POSITIVE_TYPES[random.nextInt(POSITIVE_TYPES.length)];
                incidents.add(createRecord(context, type, positiveChance, seed, index, random, false));
            }
        }

        return incidents;
    }

    public double negativeChance(IncidentContext context) {
        if (context == null) {
            return 0.0d;
        }
        double base = 0.06d
            + context.instability() * 0.15d
            + context.warPressure() * 0.12d
            + context.diseasePressure() * 0.10d
            + context.faminePressure() * 0.14d;
        return clamp01(base * DifficultyScaler.negativeMultiplier(context.difficulty(), context.hardcore()));
    }

    public double positiveChance(IncidentContext context) {
        if (context == null) {
            return 0.0d;
        }
        double base = 0.04d
            + context.prosperity() * 0.16d
            + Math.max(0.0d, 1.0d - context.instability()) * 0.03d;
        return clamp01(base * DifficultyScaler.positiveMultiplier(context.difficulty(), context.hardcore()));
    }

    private IncidentRecord createRecord(IncidentContext context, IncidentType type, double probability,
                                        long seed, int index, Random random, boolean negative) {
        int severity = calculateSeverity(random, context, negative);
        String id = context.branchId() + ":" + context.regionId() + ":" + context.year() + ":" + type.name() + ":" + index;
        String explanation = type.name() + " generated from deterministic pressures; difficulty="
            + context.difficulty().name() + ", branch=" + context.branchId();
        return new IncidentRecord(id, type, context.year(), context.regionId(), severity, probability, seed, explanation);
    }

    private int calculateSeverity(Random random, IncidentContext context, boolean negative) {
        int severity = 1 + random.nextInt(6);
        if (negative) {
            severity += switch (context.difficulty()) {
                case PEACEFUL -> -2;
                case EASY -> -1;
                case NORMAL -> 0;
                case HARD -> 2;
            };
            if (context.hardcore()) {
                severity += 3;
            }
        }
        return Math.max(1, Math.min(10, severity));
    }

    private double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
