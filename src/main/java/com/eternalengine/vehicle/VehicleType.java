package com.eternalengine.vehicle;

/**
 * Historical vehicle and transport categories.
 *
 * <p>Step 21: each has the first year it existed anywhere on Earth (negative before the common era). Nothing is used
 * before that year: no train before 1825, no car before 1886. Where it spread and when is the technology atlas's
 * work (step 30a: {@link #availableAt(double, double, int)}); the first year is the floor everywhere. Years: horse riding and carts with the first wheels (Mesopotamia, about
 * 3500 BCE; the world here begins in 3000 BCE), oared ships and sails on the Nile (about 3000 BCE), four-wheeled wagons
 * (about 3000 BCE), siege engines (Assyria, 9th century BCE), the first public steam railway (Stockton and Darlington,
 * 1825), the first commercial steamboat (Fulton's Clermont, 1807), the motor car (Benz, 1886), the motor lorry
 * (Daimler, 1896), the aeroplane (the Wright brothers, 1903), the motor cargo ship (Selandia, 1912), the tank (1916),
 * the helicopter (Focke-Wulf Fw 61, 1936).
 */
public enum VehicleType {
    HORSE(VehicleEra.ANCIENT, 45.0d, -3500),
    CART(VehicleEra.ANCIENT, 25.0d, -3500),
    CARAVAN(VehicleEra.ANCIENT, 30.0d, -3000),
    GALLEY(VehicleEra.ANCIENT, 80.0d, -3000),

    WAGON(VehicleEra.ANCIENT, 25.0d, -3000),
    SAILING_SHIP(VehicleEra.ANCIENT, 120.0d, -3000),
    SIEGE_ENGINE(VehicleEra.ANCIENT, 10.0d, -850),

    TRAIN(VehicleEra.INDUSTRIAL, 500.0d, 1825),
    STEAMSHIP(VehicleEra.INDUSTRIAL, 300.0d, 1807),
    AUTOMOBILE(VehicleEra.INDUSTRIAL, 120.0d, 1886),

    TRUCK(VehicleEra.INDUSTRIAL, 350.0d, 1896),
    TANK(VehicleEra.INDUSTRIAL, 120.0d, 1916),
    AIRPLANE(VehicleEra.INDUSTRIAL, 2500.0d, 1903),
    HELICOPTER(VehicleEra.INDUSTRIAL, 900.0d, 1936),
    CARGO_SHIP(VehicleEra.INDUSTRIAL, 700.0d, 1912);

    private final VehicleEra earliestEra;
    private final double baseKmPerDay;
    private final int firstYear;

    VehicleType(VehicleEra earliestEra, double baseKmPerDay, int firstYear) {
        this.earliestEra = earliestEra;
        this.baseKmPerDay = baseKmPerDay;
        this.firstYear = firstYear;
    }

    /** Whether it exists at some time in this era (its first year is not after the era's end). */
    public boolean availableIn(VehicleEra era) {
        return era != null && firstYear <= era.endYear();
    }

    /** Step 21: whether it exists anywhere in this year. */
    public boolean availableInYear(int year) {
        return year >= firstYear;
    }

    /**
     * Step 30a: the technology of the atlas this transport needs (null: none, the first year alone decides). Trains
     * need the railway, carts and wagons the wheel, horses horses, caravans camels or horses ...
     */
    public String atlasId() {
        return switch (this) {
            case HORSE -> "horses";
            case CART, WAGON -> "wheel";
            case CARAVAN -> "camels";
            case TRAIN -> "railway";
            case STEAMSHIP -> "steamship";
            case AUTOMOBILE -> "car";
            case TRUCK -> "lorry";
            case TANK -> "tank";
            case AIRPLANE -> "air_travel";
            case CARGO_SHIP -> "motor_ship";
            case SIEGE_ENGINE -> "siege_engines";
            default -> null;
        };
    }

    /**
     * Step 30a: whether it is in use at this place in this year (the technology atlas: no train in Kabul in 1900, no
     * horse in Cusco in 1500). Caravans also go with horses where there are no camels. Without the atlas, the year.
     */
    public boolean availableAt(double lat, double lon, int year) {
        if (!availableInYear(year)) {
            return false;
        }
        String id = atlasId();
        try {
            var atlas = com.eternalengine.technology.TechnologyAtlas.get();
            if (id == null || !atlas.loaded() || atlas.tech(id) == null) {
                return true;
            }
            var town = com.eternalengine.civilization.SettlementRank.TOWN;
            if (this == CARAVAN) {
                return atlas.present("camels", lat, lon, year, town) || atlas.present("horses", lat, lon, year, town);
            }
            if (atlas.tech(id).kind == com.eternalengine.technology.TechnologyAtlas.Kind.INFRA) {
                // a land has it when its towns are getting it (not only this town's own draw)
                return atlas.share(atlas.tech(id), lat, lon, year, town) > 0.02d;
            }
            return atlas.present(id, lat, lon, year, town);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Step 30a: the fastest land transport in use here in this year. */
    public static VehicleType fastestLandAt(double lat, double lon, int year) {
        VehicleType best = null;
        for (VehicleType t : new VehicleType[]{CARAVAN, WAGON, HORSE, TRAIN, TRUCK}) {
            if (t.availableAt(lat, lon, year) && (best == null || t.baseKmPerDay > best.baseKmPerDay)) {
                best = t;
            }
        }
        return best == null ? CARAVAN : best;
    }

    /** Step 21: the fastest of the land transports that exist in this year (caravan, wagon, train, lorry). */
    public static VehicleType fastestLand(int year) {
        VehicleType best = CARAVAN;
        for (VehicleType t : new VehicleType[]{CARAVAN, WAGON, HORSE, TRAIN, TRUCK}) {
            if (t.availableInYear(year) && t.baseKmPerDay > best.baseKmPerDay) {
                best = t;
            }
        }
        return best;
    }

    public VehicleEra earliestEra() { return earliestEra; }
    public double baseKmPerDay() { return baseKmPerDay; }
    public int firstYear() { return firstYear; }
}
