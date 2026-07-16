package com.eternalengine.vehicle;

/**
 * Historical vehicle and transport categories.
 */
public enum VehicleType {
    HORSE(VehicleEra.ANCIENT, 45.0d),
    CART(VehicleEra.ANCIENT, 25.0d),
    CARAVAN(VehicleEra.ANCIENT, 30.0d),
    GALLEY(VehicleEra.ANCIENT, 80.0d),

    WAGON(VehicleEra.MEDIEVAL, 25.0d),
    SAILING_SHIP(VehicleEra.MEDIEVAL, 120.0d),
    SIEGE_ENGINE(VehicleEra.MEDIEVAL, 10.0d),

    TRAIN(VehicleEra.INDUSTRIAL, 500.0d),
    STEAMSHIP(VehicleEra.INDUSTRIAL, 300.0d),
    AUTOMOBILE(VehicleEra.INDUSTRIAL, 120.0d),

    TRUCK(VehicleEra.MODERN, 350.0d),
    TANK(VehicleEra.MODERN, 120.0d),
    AIRPLANE(VehicleEra.MODERN, 2500.0d),
    HELICOPTER(VehicleEra.MODERN, 900.0d),
    CARGO_SHIP(VehicleEra.MODERN, 700.0d);

    private final VehicleEra earliestEra;
    private final double baseKmPerDay;

    VehicleType(VehicleEra earliestEra, double baseKmPerDay) {
        this.earliestEra = earliestEra;
        this.baseKmPerDay = baseKmPerDay;
    }

    public boolean availableIn(VehicleEra era) {
        return era.ordinal() >= earliestEra.ordinal();
    }

    public VehicleEra earliestEra() { return earliestEra; }
    public double baseKmPerDay() { return baseKmPerDay; }
}
