package com.eternalengine.climate;

/**
 * Immutable historical climate event definition.
 */
public final class ClimateEvent {
    private final String id;
    private final String name;
    private final String type;
    private final int startYear;
    private final int endYear;
    private final double severity;
    private final double radiusKm;
    private final double centerLat;
    private final double centerLon;
    private final double tempChangeDegrees;
    private final double precipChangePercent;
    private final double yieldImpactPercent;

    public ClimateEvent(String id, String name, String type, int startYear, int endYear,
                        double severity, double radiusKm, double centerLat, double centerLon,
                        double tempChangeDegrees, double precipChangePercent, double yieldImpactPercent) {
        this.id = id == null || id.isBlank() ? "climate_unknown" : id;
        this.name = name == null || name.isBlank() ? this.id : name;
        this.type = type == null || type.isBlank() ? "GENERAL" : type;
        this.startYear = Math.min(startYear, endYear);
        this.endYear = Math.max(startYear, endYear);
        this.severity = Math.max(0.0d, Math.min(10.0d, severity));
        this.radiusKm = Math.max(0.0d, radiusKm);
        this.centerLat = centerLat;
        this.centerLon = centerLon;
        this.tempChangeDegrees = tempChangeDegrees;
        this.precipChangePercent = precipChangePercent;
        this.yieldImpactPercent = yieldImpactPercent;
    }

    public boolean isActiveInYear(int year) {
        return year >= startYear && year <= endYear;
    }

    public boolean affectsRegion(double lat, double lon) {
        return radiusKm <= 0.0d || distanceKm(centerLat, centerLon, lat, lon) <= radiusKm;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        return r * 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
    }

    public String id() { return id; }
    public String name() { return name; }
    public String type() { return type; }
    public int startYear() { return startYear; }
    public int endYear() { return endYear; }
    public double severity() { return severity; }
    public double radiusKm() { return radiusKm; }
    public double centerLat() { return centerLat; }
    public double centerLon() { return centerLon; }
    public double tempChangeDegrees() { return tempChangeDegrees; }
    public double precipChangePercent() { return precipChangePercent; }
    public double yieldImpactPercent() { return yieldImpactPercent; }
}
