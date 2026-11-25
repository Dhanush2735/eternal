package com.eternalengine.war;

/**
 * Mutable army state. Large-scale armies are virtual unless a later renderer
 * materializes nearby representations.
 *
 * <p>Step 40 (D10 armies in action) gave an army the four things it was missing and that decide
 * whether it holds: <b>where it came from</b>, <b>how it was raised</b>, <b>what kind of party
 * fields it</b>, and <b>what it has already lost</b>. All four are read by {@link Morale} and
 * {@link Sides}; none of them is a judgement, they are facts about the body that the register
 * then reads.
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

    private final double homeLatitude;
    private final double homeLongitude;
    private final String raisedId;
    private final long sizeAtRaise;
    private String partyId;
    private FormationType formation;
    private long lost;

    /** The old shape: an army with no stated home is at home where it stands. */
    public Army(String armyId, String ownerCivId, double latitude, double longitude, long size,
                int morale, int supplyLevel, String equipmentEra, double movementSpeedKmPerDay) {
        this(armyId, ownerCivId, latitude, longitude, size, morale, supplyLevel, equipmentEra,
            movementSpeedKmPerDay, latitude, longitude, null, null, FormationType.LINE);
    }

    /**
     * An army that says where it came from and how it was raised.
     *
     * <p>{@code raisedId} is a row in the morale pack (levy, citizen_militia, retinue,
     * mercenary_company, conscript_national, professional_standing, slave_soldier,
     * steppe_following, warlord_band). Leave it null and the register picks the one that belongs
     * to the army's home ground and year. {@code partyId} is a row in the sides pack.
     */
    public Army(String armyId, String ownerCivId, double latitude, double longitude, long size,
                int morale, int supplyLevel, String equipmentEra, double movementSpeedKmPerDay,
                double homeLatitude, double homeLongitude, String raisedId, String partyId,
                FormationType formation) {
        this.armyId = armyId == null || armyId.isBlank() ? "army_unknown" : armyId;
        this.homeLatitude = homeLatitude;
        this.homeLongitude = homeLongitude;
        this.raisedId = raisedId == null || raisedId.isBlank() ? null : raisedId;
        this.partyId = partyId == null || partyId.isBlank() ? null : partyId;
        this.formation = formation == null ? FormationType.LINE : formation;
        this.sizeAtRaise = Math.max(0, size);
        this.lost = 0L;
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

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0d * 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
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

    public double homeLatitude() { return homeLatitude; }
    public double homeLongitude() { return homeLongitude; }

    /** How far from the fields it came from, in kilometres. */
    public double homeDistanceKm() {
        return haversineKm(latitude, longitude, homeLatitude, homeLongitude);
    }

    public void setPartyId(String partyId) { this.partyId = partyId; }
    public String partyId() { return partyId; }

    /**
     * What kind of party fields this army. Falls back on the ordinary party of its home ground
     * and year when nothing was stated, which is the same rule the register uses everywhere else.
     */
    public Sides.Party party(int year) {
        Sides.Party named = partyId == null ? null : Sides.party(partyId);
        return named != null ? named
            : Sides.partyAt(homeLatitude, homeLongitude, year);
    }

    /** How these men were raised, or null if the register has no row for it. */
    public Morale.Raised raised(int year) {
        Morale.Raised named = raisedId == null ? null : Morale.raised(raisedId);
        return named != null ? named
            : Morale.raisedAt(homeLatitude, homeLongitude, year);
    }

    public FormationType formation() { return formation; }
    public void setFormation(FormationType formation) {
        this.formation = formation == null ? FormationType.LINE : formation;
    }

    /** The share of the army that has gone, as a fraction. Krentz's breaking point is 12%. */
    public double lossShare() {
        return sizeAtRaise <= 0 ? 0.0d : lost / (double) sizeAtRaise;
    }

    /** Take casualties, and with them the losses that decide whether the body holds. */
    public void takeLosses(long n) {
        long gone = Math.max(0, n);
        this.lost += gone;
        this.size = Math.max(0, size - gone);
    }

    /** Has this body lost enough of itself to come apart? */
    public boolean routing() {
        return Morale.routs(lossShare() * 100.0d);
    }

    /**
     * How many men go this year, and why.
     *
     * <p>Read from the measured rates where the record has one and from the raised row's own
     * figure where it does not, then adjusted for the three things the record is clear about:
     * pay in arrears, a campaign going badly, and distance from home.
     */
    public long desertions(int year, boolean winning, boolean losing, boolean paid,
                           boolean besieged) {
        Morale.State st = new Morale.State(year, latitude, longitude,
            raised(year) == null ? null : raised(year).id(), formation,
            formation == null ? 2 : formation.defaultRanks(), winning, losing, false, false,
            homeDistanceKm() < 250.0d, paid, false, false, besieged,
            supplyLevel < 25, false, false, false, false, false, false);
        double perYear = Morale.desertionPerYear(st);
        long gone = (long) Math.floor(size * perYear / 100.0d);
        gone = Math.max(0L, Math.min(size, gone));
        takeLosses(gone);
        return gone;
    }
    public void setCurrentOrder(MovementOrder currentOrder) { this.currentOrder = currentOrder; }
    public double movementSpeedKmPerDay() { return movementSpeedKmPerDay; }

    public record MovementOrder(String orderId, String orderType, double targetLat, double targetLon,
                                String targetId, int issuedYear) {
    }
}
