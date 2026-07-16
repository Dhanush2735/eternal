package com.eternalengine.vehicle;

import java.util.Objects;

/**
 * Immutable large-scale vehicle route facts.
 */
public final class VehicleRoute {

    private final String routeId;
    private final VehicleType vehicleType;
    private final VehicleEra era;
    private final double originLat;
    private final double originLon;
    private final double destLat;
    private final double destLon;
    private final double distanceKm;
    private final double dangerLevel;
    private final String ownerCivId;
    private final boolean tradeRoute;
    private final boolean militaryRoute;

    public VehicleRoute(String routeId, VehicleType vehicleType, VehicleEra era,
                        double originLat, double originLon, double destLat, double destLon,
                        double distanceKm, double dangerLevel, String ownerCivId,
                        boolean tradeRoute, boolean militaryRoute) {
        this.routeId = Objects.requireNonNullElse(routeId, "vehicle_route_unknown");
        this.vehicleType = vehicleType == null ? VehicleType.CARAVAN : vehicleType;
        this.era = era == null ? VehicleEra.ANCIENT : era;
        this.originLat = originLat;
        this.originLon = originLon;
        this.destLat = destLat;
        this.destLon = destLon;
        this.distanceKm = Math.max(0.0d, distanceKm);
        this.dangerLevel = Math.max(0.0d, Math.min(100.0d, dangerLevel));
        this.ownerCivId = ownerCivId == null || ownerCivId.isBlank() ? "neutral" : ownerCivId;
        this.tradeRoute = tradeRoute;
        this.militaryRoute = militaryRoute;
    }

    public String routeId() { return routeId; }
    public VehicleType vehicleType() { return vehicleType; }
    public VehicleEra era() { return era; }
    public double originLat() { return originLat; }
    public double originLon() { return originLon; }
    public double destLat() { return destLat; }
    public double destLon() { return destLon; }
    public double distanceKm() { return distanceKm; }
    public double dangerLevel() { return dangerLevel; }
    public String ownerCivId() { return ownerCivId; }
    public boolean tradeRoute() { return tradeRoute; }
    public boolean militaryRoute() { return militaryRoute; }
}
