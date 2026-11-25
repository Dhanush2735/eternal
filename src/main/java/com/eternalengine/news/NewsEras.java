package com.eternalengine.news;

import com.eternalengine.civilization.SettlementRank;

import java.util.List;
import java.util.Locale;

/**
 * Step 19d: where and when news is printed, written by hand, or called out in the street.
 *
 * <p>The years are from the history of the press: handwritten news sheets (avvisi) in Venice from 1566, the first
 * printed newspaper in Strasbourg in 1605, the Mughal news-writers from Akbar's time, the Roman Acta from 59 BCE, the
 * first paper in India in 1780 (Calcutta). They give the year a region's <b>cities</b> have printed papers; towns get
 * them {@link #TOWN_LAG} years later, villages never (a crier brings the news there). Where only the first paper of a
 * whole region is known, the year is an estimate, and it says so in the README.
 */
public final class NewsEras {

    /** How the news reaches people in one place and year. */
    public enum Kind {
        PRINTED("paper_seller"), HANDWRITTEN("news_writer"), CRIER("town_crier"), NONE("");

        private final String role;

        Kind(String role) {
            this.role = role;
        }

        /** The person who brings it (a villager role), or "" for none. */
        public String role() {
            return role;
        }
    }

    public static final String PAPER_SELLER = "paper_seller";
    public static final String NEWS_WRITER = "news_writer";
    public static final String TOWN_CRIER = "town_crier";
    /** Towns get printed papers this many years after the cities of their region (an estimate). */
    public static final int TOWN_LAG = 50;
    private static final int NO = Integer.MIN_VALUE;

    /**
     * One region: a box, the year its cities have printed papers, and the years of handwritten newsletters there.
     * Checked in order; the first box that holds the place wins.
     */
    record Region(String name, double minLat, double maxLat, double minLon, double maxLon, int print, int handFrom,
                  int handTo, boolean estimate) {
        boolean holds(double lat, double lon) {
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }
    }

    static final List<Region> REGIONS = List.of(
            new Region("Italy", 36.0, 47.5, 6.5, 19.0, 1640, 1566, 1639, true),
            new Region("Spain and Portugal", 36.0, 44.0, -10.0, 3.5, 1661, 1566, 1660, true),
            new Region("north-west Europe", 43.0, 71.0, -11.0, 25.0, 1620, 1566, 1619, false),
            new Region("Russia and eastern Europe", 41.0, 71.0, 25.0, 60.0, 1703, NO, NO, true),
            new Region("Anatolia and the Levant", 29.0, 43.0, 25.0, 48.0, 1831, NO, NO, false),
            new Region("Egypt and north Africa", 20.0, 37.5, -17.0, 35.0, 1828, NO, NO, true),
            new Region("Persia and central Asia", 25.0, 45.0, 44.0, 75.0, 1837, NO, NO, true),
            new Region("South Asia", 5.0, 37.0, 60.0, 98.0, 1840, 1574, 1839, true),
            new Region("China", 18.0, 54.0, 73.0, 135.0, 1872, 713, 1871, true),
            new Region("Japan and Korea", 30.0, 46.0, 124.0, 146.0, 1870, NO, NO, true),
            new Region("south-east Asia", -11.0, 28.0, 92.0, 141.0, 1820, NO, NO, true),
            new Region("North America", 25.0, 72.0, -170.0, -50.0, 1704, NO, NO, false),
            new Region("Mexico and Central America", 7.0, 25.0, -118.0, -77.0, 1722, NO, NO, false),
            new Region("South America", -56.0, 13.0, -82.0, -34.0, 1808, NO, NO, true),
            new Region("southern Africa", -36.0, -15.0, 10.0, 41.0, 1800, NO, NO, true),
            new Region("Australia and New Zealand", -48.0, -10.0, 110.0, 180.0, 1803, NO, NO, false));

    /** The rest of the world: an estimate. */
    static final Region WORLD = new Region("the world", -90, 90, -180, 180, 1870, NO, NO, true);
    /** The Roman Acta: handwritten copies of the city news, 59 BCE to about 235 CE, in the Roman world. */
    static final Region ROME = new Region("the Roman world", 30.0, 56.0, -10.0, 42.0, NO, -59, 235, false);

    private NewsEras() {
    }

    static Region region(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return WORLD;
        }
        for (Region r : REGIONS) {
            if (r.holds(lat, lon)) {
                return r;
            }
        }
        return WORLD;
    }

    /** The year printed papers reach a place of this rank (a village: never, {@link Integer#MAX_VALUE}). */
    public static int printYear(SettlementRank rank, double lat, double lon) {
        Region r = region(lat, lon);
        if (rank == SettlementRank.CITY) {
            return r.print;
        }
        return rank == SettlementRank.TOWN ? r.print + TOWN_LAG : Integer.MAX_VALUE;
    }

    /** True when handwritten newsletters are sold in a city of this place in this year. */
    public static boolean handwritten(double lat, double lon, int year) {
        if (ROME.holds(lat, lon) && year >= ROME.handFrom && year <= ROME.handTo) {
            return true;
        }
        Region r = region(lat, lon);
        return r.handFrom != NO && year >= r.handFrom && year <= r.handTo && year < r.print;
    }

    /**
     * How the news reaches people here. Cities and towns with printed papers have a paper seller; cities in the time of
     * handwritten newsletters a newsletter writer; every other place that has a ruler a town crier; a place with no
     * ruler (no state to announce anything) nobody.
     */
    public static Kind kind(SettlementRank rank, boolean ruled, double lat, double lon, int year) {
        if (rank == null) {
            rank = SettlementRank.VILLAGE;
        }
        if (rank == SettlementRank.COUNTRYSIDE) {
            // A band of hunters and gatherers has no paper seller and no crier, even under a
            // ruler: the ruler's word arrives with the tax collector, not a town crier (turn 231).
            return Kind.NONE;
        }
        if (year >= printYear(rank, lat, lon)) {
            return Kind.PRINTED;
        }
        if (rank == SettlementRank.CITY && handwritten(lat, lon, year)) {
            return Kind.HANDWRITTEN;
        }
        return ruled ? Kind.CRIER : Kind.NONE;
    }

    /** The region's name, for the README and the checks. */
    public static String regionName(double lat, double lon) {
        return region(lat, lon).name;
    }

    /** True when this region's print year is an estimate. */
    public static boolean estimate(double lat, double lon) {
        return region(lat, lon).estimate;
    }

    /** What a printed paper costs, in days of work (an estimate: early papers were dear, taxed and few). */
    public static double paperDays(int year) {
        if (year < 1830) {
            return 1.0d / 4.0d;
        }
        return year < 1900 ? 1.0d / 12.0d : 1.0d / 16.0d;
    }

    /** What a handwritten newsletter costs, in days of work: a copyist's day. */
    public static double newsletterDays() {
        return 1.0d;
    }

    /** What a map costs, in days of work: printed half a day's pay, drawn by hand three days'. */
    public static double mapDays(boolean printed) {
        return printed ? 0.5d : 3.0d;
    }

    /**
     * The roles of a place with the one person who brings the news added (step 19d): after the money-changer or the
     * merchant if there is one, else at the end. Nothing is added when the role is there already or nobody brings
     * news here.
     */
    public static String[] withNewsPerson(String[] roles, SettlementRank rank, boolean ruled, double lat, double lon,
                                          int year) {
        if (roles == null || roles.length == 0) {
            return roles == null ? new String[0] : roles;
        }
        String role = kind(rank, ruled, lat, lon, year).role();
        if (role.isEmpty()) {
            return roles;
        }
        int at = roles.length - 1;
        for (int i = 0; i < roles.length; i++) {
            String r = roles[i] == null ? "" : roles[i].toLowerCase(Locale.ROOT);
            if (r.equals(PAPER_SELLER) || r.equals(NEWS_WRITER) || r.equals(TOWN_CRIER)) {
                return roles;
            }
            if (r.equals("merchant") || r.equals("money_changer") || r.equals("trader") || r.equals("pedlar")) {
                at = i;
            }
        }
        String[] out = new String[roles.length + 1];
        System.arraycopy(roles, 0, out, 0, at + 1);
        out[at + 1] = role;
        System.arraycopy(roles, at + 1, out, at + 2, roles.length - at - 1);
        return out;
    }

    /** True for the three news roles. */
    public static boolean newsRole(String role) {
        if (role == null) {
            return false;
        }
        String r = role.toLowerCase(Locale.ROOT);
        return r.equals(PAPER_SELLER) || r.equals(NEWS_WRITER) || r.equals(TOWN_CRIER);
    }

    /** The check with no world: the years above give the right person in eight places; null when all is well. */
    public static String selfTest() {
        Object[][] cases = {
                {SettlementRank.CITY, 17.36, 78.47, 1948, Kind.PRINTED},      // Hyderabad 1948
                {SettlementRank.VILLAGE, 18.87, 79.43, 1948, Kind.CRIER},     // a Telangana village
                {SettlementRank.CITY, 17.36, 78.47, 1700, Kind.HANDWRITTEN},  // Hyderabad under the Mughals
                {SettlementRank.CITY, 41.89, 12.49, 100, Kind.HANDWRITTEN},   // Rome, the Acta
                {SettlementRank.CITY, 51.51, -0.13, 1800, Kind.PRINTED},      // London
                {SettlementRank.CITY, 51.51, -0.13, 1500, Kind.CRIER},        // London before the avvisi
                {SettlementRank.CITY, 45.44, 12.33, 1580, Kind.HANDWRITTEN},  // Venice, the avvisi
                {SettlementRank.TOWN, 52.0, 5.0, 1650, Kind.CRIER},           // a Dutch town before 1670
        };
        for (Object[] c : cases) {
            Kind k = kind((SettlementRank) c[0], true, (double) c[1], (double) c[2], (int) c[3]);
            if (k != c[4]) {
                return "news at " + c[1] + "," + c[2] + " in " + c[3] + " (" + c[0] + "): " + k + ", expected " + c[4];
            }
        }
        if (kind(SettlementRank.VILLAGE, false, 18.87, 79.43, 1948) != Kind.NONE) {
            return "a place with no ruler should have no crier";
        }
        String[] r = withNewsPerson(new String[]{"farmer", "merchant", "money_changer", "citizen"}, SettlementRank.CITY,
                true, 17.36, 78.47, 1948);
        if (r.length != 5 || !PAPER_SELLER.equals(r[3])) {
            return "the paper seller should stand after the money-changer: " + String.join(",", r);
        }
        if (Math.abs(paperDays(1948) * 192 - 12) > 1e-9 || Math.abs(mapDays(true) * 192 - 96) > 1e-9) {
            return "a paper should cost 1 anna and a map 8 annas in Hyderabad in 1948";
        }
        return null;
    }
}
