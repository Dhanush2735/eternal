package com.eternalengine.trade;

/**
 * Historical transport categories used by trade routes.
 */
public enum TransportType {
    FOOT(10.0d),
    DONKEY(20.0d),
    CARAVAN(30.0d),
    SHIP(120.0d),
    TRAIN(500.0d),
    TRUCK(350.0d),
    AIR(2500.0d);

    private final double kmPerDay;

    TransportType(double kmPerDay) {
        this.kmPerDay = kmPerDay;
    }

    public double kmPerDay() {
        return kmPerDay;
    }
}
