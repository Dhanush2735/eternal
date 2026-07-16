package com.eternalengine.vehicle;

/**
 * Broad historical transport eras.
 */
public enum VehicleEra {
    ANCIENT(-3000, 499),
    MEDIEVAL(500, 1499),
    INDUSTRIAL(1500, 1944),
    MODERN(1945, 9999);

    private final int startYear;
    private final int endYear;

    VehicleEra(int startYear, int endYear) {
        this.startYear = startYear;
        this.endYear = endYear;
    }

    public boolean containsYear(int year) {
        return year >= startYear && year <= endYear;
    }

    public static VehicleEra fromYear(int year) {
        for (VehicleEra era : values()) {
            if (era.containsYear(year)) {
                return era;
            }
        }
        return year < ANCIENT.startYear ? ANCIENT : MODERN;
    }

    public int startYear() { return startYear; }
    public int endYear() { return endYear; }
}
