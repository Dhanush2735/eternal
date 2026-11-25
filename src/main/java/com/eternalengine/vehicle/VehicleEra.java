package com.eternalengine.vehicle;

/**
 * Broad historical transport eras.
 *
 * <p>Step 21: the industrial era began in 1500 here, which let a train run in 1600; it now begins in 1800, with the
 * early modern era (1500-1799) between. Which vehicle exists in a year is decided by the vehicle's own first year
 * ({@link VehicleType#firstYear()}), not by the era.
 */
public enum VehicleEra {
    ANCIENT(-3000, 499),
    MEDIEVAL(500, 1499),
    EARLY_MODERN(1500, 1799),
    INDUSTRIAL(1800, 1944),
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
