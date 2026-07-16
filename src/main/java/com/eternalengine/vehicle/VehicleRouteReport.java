package com.eternalengine.vehicle;

import java.util.Locale;

/**
 * Deterministic route evaluation result.
 */
public final class VehicleRouteReport {

    private final VehicleRoute route;
    private final boolean eraValid;
    private final double travelDays;
    private final double incidentRisk;
    private final VehicleVirtualizationMode virtualizationMode;
    private final String explanation;

    public VehicleRouteReport(VehicleRoute route, boolean eraValid, double travelDays,
                              double incidentRisk, VehicleVirtualizationMode virtualizationMode,
                              String explanation) {
        this.route = route;
        this.eraValid = eraValid;
        this.travelDays = Math.max(0.0d, travelDays);
        this.incidentRisk = Math.max(0.0d, Math.min(1.0d, incidentRisk));
        this.virtualizationMode = virtualizationMode == null ? VehicleVirtualizationMode.VIRTUAL : virtualizationMode;
        this.explanation = explanation == null ? "No explanation" : explanation;
    }

    public String toSummaryString() {
        String routeId = route == null ? "unknown" : route.routeId();
        String vehicle = route == null ? "unknown" : route.vehicleType().name();
        return String.format(Locale.ROOT,
            "VehicleRoute route=%s vehicle=%s eraValid=%s travelDays=%.2f incidentRisk=%.3f mode=%s",
            routeId, vehicle, eraValid, travelDays, incidentRisk, virtualizationMode);
    }

    public VehicleRoute route() { return route; }
    public boolean eraValid() { return eraValid; }
    public double travelDays() { return travelDays; }
    public double incidentRisk() { return incidentRisk; }
    public VehicleVirtualizationMode virtualizationMode() { return virtualizationMode; }
    public String explanation() { return explanation; }
}
