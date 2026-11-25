package com.eternalengine.vehicle;

import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative vehicle route evaluator.
 *
 * This is a virtual simulation foundation. It does not spawn entities or place
 * blocks. Later phases may materialize vehicles near players through validated
 * queues while distant vehicles remain virtual.
 */
public final class VehicleEngine {

    public VehicleRouteReport evaluateRoute(VehicleRoute route, Difficulty difficulty, boolean hardcore,
                                            double warPressure, double weatherPressure, double infrastructurePressure,
                                            boolean nearPlayer) {
        if (route == null) {
            return new VehicleRouteReport(null, false, 0.0d, 0.0d,
                VehicleVirtualizationMode.SUSPENDED, "Route is null");
        }

        boolean eraValid = route.vehicleExists(); // step 21: in the route's year when known (no train before 1825)
        double speed = eraValid ? route.vehicleType().baseKmPerDay() : fallbackSpeed(route.era());
        double travelDays = route.distanceKm() / Math.max(1.0d, speed);
        double incidentRisk = VehicleIncidentRisk.calculate(route, difficulty, hardcore,
            warPressure, weatherPressure, infrastructurePressure);
        VehicleVirtualizationMode mode = nearPlayer && eraValid
            ? VehicleVirtualizationMode.MATERIALIZED
            : VehicleVirtualizationMode.VIRTUAL;
        String explanation = route.vehicleType().name() + " in " + route.era().name()
            + (route.year() == Integer.MIN_VALUE ? "" : " (" + (route.year() < 0 ? -route.year() + " BCE" : route.year())
                + "; first known " + (route.vehicleType().firstYear() < 0 ? -route.vehicleType().firstYear() + " BCE"
                : route.vehicleType().firstYear()) + ")")
            + " uses " + speed + " km/day; eraValid=" + eraValid
            + ", difficulty=" + (difficulty == null ? "NORMAL" : difficulty.name());
        return new VehicleRouteReport(route, eraValid, travelDays, incidentRisk, mode, explanation);
    }

    public List<VehicleRouteReport> evaluateRoutes(List<VehicleRoute> routes, Difficulty difficulty, boolean hardcore,
                                                   double warPressure, double weatherPressure,
                                                   double infrastructurePressure, boolean nearPlayer) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }
        List<VehicleRouteReport> reports = new ArrayList<>();
        for (VehicleRoute route : routes) {
            reports.add(evaluateRoute(route, difficulty, hardcore, warPressure, weatherPressure, infrastructurePressure, nearPlayer));
        }
        return reports;
    }

    private double fallbackSpeed(VehicleEra era) {
        if (era == null) {
            return 25.0d;
        }
        return switch (era) {
            case ANCIENT -> VehicleType.CARAVAN.baseKmPerDay();
            case MEDIEVAL, EARLY_MODERN -> VehicleType.WAGON.baseKmPerDay();
            case INDUSTRIAL -> VehicleType.WAGON.baseKmPerDay(); // step 21: a train only from 1825
            case MODERN -> VehicleType.TRUCK.baseKmPerDay();
        };
    }
}
