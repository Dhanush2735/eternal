package com.eternalengine.war;

/**
 * Mutable army state. Large-scale armies are virtual unless a later renderer
 * materializes nearby representations.
 */
public final class Army {
    private final String armyId;
    private final String ownerCivId;
    private double latitude;
    private double longitude;
    private long size;
    private int morale;
    private int supplyLevel;
    private String equipmentEra;
    private MovementOrder currentOrder;
    private double movementSpeedKmPerDay;

    public Army(String armyId, String ownerCivId, double latitude, double longitude, long size,
                int morale, int supplyLevel, String equipmentEra, double movementSpeedKmPerDay) {
        this.armyId = armyId == null || armyId.isBlank() ? "army_unknown" : armyId;
        this.ownerCivId = ownerCivId == null || ownerCivId.isBlank() ? "neutral" : ownerCivId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.size = Math.max(0, size);
        this.morale = clamp100(morale);
        this.supplyLevel = clamp100(supplyLevel);
        this.equipmentEra = equipmentEra == null ? "ANCIENT" : equipmentEra;
        this.movementSpeedKmPerDay = Math.max(1.0d, movementSpeedKmPerDay);
    }

    public void moveToward(double targetLat, double targetLon, double days) {
        double fraction = Math.min(1.0d, Math.max(0.0d, (movementSpeedKmPerDay * days) / Math.max(1.0d, roughDistanceKm(latitude, longitude, targetLat, targetLon))));
        latitude += (targetLat - latitude) * fraction;
        longitude += (targetLon - longitude) * fraction;
    }

    private double roughDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = (lat2 - lat1) * 111.32d;
        double dLon = (lon2 - lon1) * 111.32d * Math.cos(Math.toRadians(lat1));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    private int clamp100(int value) { return Math.max(0, Math.min(100, value)); }

    public String armyId() { return armyId; }
    public String ownerCivId() { return ownerCivId; }
    public double latitude() { return latitude; }
    public double longitude() { return longitude; }
    public long size() { return size; }
    public void setSize(long size) { this.size = Math.max(0, size); }
    public int morale() { return morale; }
    public void adjustMorale(int delta) { this.morale = clamp100(this.morale + delta); }
    public int supplyLevel() { return supplyLevel; }
    public void setSupplyLevel(int supplyLevel) { this.supplyLevel = clamp100(supplyLevel); }
    public String equipmentEra() { return equipmentEra; }
    public MovementOrder currentOrder() { return currentOrder; }
    public void setCurrentOrder(MovementOrder currentOrder) { this.currentOrder = currentOrder; }
    public double movementSpeedKmPerDay() { return movementSpeedKmPerDay; }

    public record MovementOrder(String orderId, String orderType, double targetLat, double targetLon,
                                String targetId, int issuedYear) {
    }
}
