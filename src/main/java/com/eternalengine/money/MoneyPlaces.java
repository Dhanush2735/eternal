package com.eternalengine.money;

import com.eternalengine.population.HydeGrid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 23a: the money of the whole world, checked. Before step 23a the money table named the money of India, the
 * ancient Near East and the Mediterranean, western Europe, China, Japan, the USA, Spanish America before 1860 and a
 * few more places, and said "silver and copper coins" (600 BCE - 1799) or "local money" (from 1800) everywhere else.
 * The world layer (worldfill/money_world.py) names the money of every region and era; this class holds what the
 * checks expect of it:
 *
 * <ul>
 *   <li>{@link #EXPECT}: test places across every region and era with the money they must get (Oslo 1200 the
 *       Scandinavian mark, Lhasa 1900 the Tibetan tangka, Calabar 1800 manillas, Phnom Penh 1977 no money at all,
 *       Sofia 2026 the euro...);</li>
 *   <li>{@link #census}: random points of the globe in many years, kept where people lived that year (the HYDE
 *       population grid), sorted into real money, barter, and the old placeholders. The sea and empty land keep the
 *       placeholder on purpose, so only settled points count.</li>
 * </ul>
 */
public final class MoneyPlaces {

    private MoneyPlaces() {
    }

    /** A place, a year and the money id the table must give there. */
    public record Expect(String place, double lat, double lon, int year, String money) {
    }

    /** The test places (each checked against the register's real rulers). */
    public static final List<Expect> EXPECT = List.of(
            new Expect("Oslo", 59.91, 10.75, 1200, "scandi_mark"),
            new Expect("Uppsala", 59.86, 17.64, 900, "norse_silver"),
            new Expect("Reykjavik", 64.15, -21.94, 1100, "vadmal"),
            new Expect("Venice", 45.44, 12.33, 1400, "lira_it_old"),
            new Expect("Naples", 40.85, 14.27, 1700, "ducato_napoli"),
            new Expect("Rome", 41.89, 12.49, 1800, "scudo_papal"),
            new Expect("Rome", 41.89, 12.49, 1900, "lira_it"),
            new Expect("Lisbon", 38.72, -9.14, 1600, "real_pt"),
            new Expect("Seville", 37.39, -5.98, 1450, "maravedi_cas"),
            new Expect("Barcelona", 41.39, 2.17, 1300, "diner_ar"),
            new Expect("Amsterdam", 52.37, 4.9, 1700, "guilder_nl_old"),
            new Expect("Brussels", 50.85, 4.35, 1950, "franc_be"),
            new Expect("Zurich", 47.37, 8.54, 1950, "franc_ch"),
            new Expect("Geneva", 46.2, 6.14, 2010, "franc_ch"),
            new Expect("Vienna", 48.21, 16.37, 1800, "gulden_kreuzer"),
            new Expect("Vienna", 48.21, 16.37, 1900, "krone_au"),
            new Expect("Prague", 50.08, 14.44, 1980, "koruna_cs"),
            new Expect("Warsaw", 52.23, 21.01, 1946, "zloty_pl_old"),
            new Expect("Leipzig", 51.34, 12.37, 1949, "ddr_mark"),
            new Expect("Warsaw", 52.23, 21.01, 1650, "grosz_pl"),
            new Expect("Warsaw", 52.23, 21.01, 2020, "zloty_new"),
            new Expect("Budapest", 47.5, 19.04, 1960, "forint"),
            new Expect("Copenhagen", 55.68, 12.57, 1700, "rigsdaler_dk"),
            new Expect("Stockholm", 59.33, 18.07, 1900, "krona_se"),
            new Expect("Helsinki", 60.17, 24.94, 1950, "markka_old"),
            new Expect("Kyiv", 50.45, 30.52, 1100, "grivna"),
            new Expect("Moscow", 55.76, 37.62, 1450, "denga"),
            new Expect("Moscow", 55.76, 37.62, 1970, "ruble_su61"),
            new Expect("Moscow", 55.76, 37.62, 2026, "ruble_ru"),
            new Expect("Kyiv", 50.45, 30.52, 2026, "hryvnia"),
            new Expect("Leipzig", 51.34, 12.37, 1970, "ddr_mark"),
            new Expect("Athens", 37.98, 23.73, 1900, "drachma_mod"),
            new Expect("Belgrade", 44.79, 20.45, 1980, "dinar_yu_1966"),
            new Expect("Zagreb", 45.81, 15.98, 2023, "euro"),
            new Expect("Sofia", 42.7, 23.32, 2025, "lev_1999"),
            new Expect("Sofia", 42.7, 23.32, 2026, "euro"),
            new Expect("Bucharest", 44.43, 26.1, 2010, "leu_ro_2005"),
            new Expect("Dublin", 53.35, -6.26, 1990, "pound_ie_dec"),
            new Expect("Belfast", 54.6, -5.93, 1990, "gbp"),
            new Expect("Valletta", 35.9, 14.51, 1990, "lira_mt"),
            new Expect("Persepolis", 29.94, 52.89, -500, "siglos"),
            new Expect("Ctesiphon", 33.09, 44.58, 400, "drachm_persian"),
            new Expect("Carthage", 36.85, 10.32, -300, "shekel_punic"),
            new Expect("Cairo", 30.04, 31.24, 1900, "pound_eg"),
            new Expect("Tehran", 35.69, 51.39, 1950, "rial_ir"),
            new Expect("Istanbul", 41.01, 28.98, 1950, "lira_tr"),
            new Expect("Istanbul", 41.01, 28.98, 2026, "lira_tr_new"),
            new Expect("Baghdad", 33.31, 44.36, 1950, "dinar_iq"),
            new Expect("Jerusalem", 31.78, 35.22, 1940, "pound_pa"),
            new Expect("Tel Aviv", 32.09, 34.78, 2000, "shekel"),
            new Expect("Riyadh", 24.71, 46.68, 2000, "riyal_sa"),
            new Expect("Dubai", 25.2, 55.27, 2026, "dirham_ae"),
            new Expect("Algiers", 36.75, 3.06, 1900, "franc_dz"),
            new Expect("Casablanca", 33.57, -7.59, 2000, "dirham_ma"),
            new Expect("Addis Ababa", 9.03, 38.74, 1900, "mt_thaler"),
            new Expect("Lagos", 6.45, 3.39, 1930, "pound_wa"),
            new Expect("Lagos", 6.45, 3.39, 2026, "naira"),
            new Expect("Accra", 5.6, -0.19, 1980, "cedi_old"),
            new Expect("Dakar", 14.69, -17.45, 1980, "franc_cfa"),
            new Expect("Nairobi", -1.29, 36.82, 1950, "shilling_ea"),
            new Expect("Nairobi", -1.29, 36.82, 2000, "shilling_ke"),
            new Expect("Kisangani", 0.52, 25.19, 1980, "zaire"),
            new Expect("Johannesburg", -26.2, 28.05, 1950, "pound_za"),
            new Expect("Johannesburg", -26.2, 28.05, 2000, "rand"),
            new Expect("Luanda", -8.84, 13.23, 1950, "escudo_ao"),
            new Expect("Harare", -17.83, 31.05, 2026, "zig"),
            new Expect("Antananarivo", -18.88, 47.51, 1850, "cut_silver_mg"),
            new Expect("Calabar", 4.95, 8.32, 1800, "manilla"),
            new Expect("Mbanza Kongo", -6.27, 14.24, 1600, "nzimbu"),
            new Expect("Karachi", 24.86, 67.01, 1955, "rupee_pk_anna"),
            new Expect("Lahore", 31.55, 74.34, 2000, "rupee_pk"),
            new Expect("Dhaka", 23.81, 90.41, 2000, "taka"),
            new Expect("Colombo", 6.93, 79.85, 1950, "rupee_lk"),
            new Expect("Colombo", 6.93, 79.85, 1900, "rupee_lk"),
            new Expect("Basel", 47.56, 7.59, 1990, "franc_ch"),
            new Expect("Kathmandu", 27.71, 85.32, 1900, "mohur"),
            new Expect("Lhasa", 29.65, 91.17, 1900, "tangka_tb"),
            new Expect("Yangon", 16.84, 96.17, 1960, "kyat"),
            new Expect("Bangkok", 13.75, 100.5, 1800, "baht_old"),
            new Expect("Bangkok", 13.75, 100.5, 2000, "baht"),
            new Expect("Hanoi", 21.03, 105.85, 1500, "van_vn"),
            new Expect("Saigon", 10.82, 106.63, 1960, "dong_sv"),
            new Expect("Phnom Penh", 11.56, 104.92, 1977, "barter"),
            new Expect("Jakarta", -6.21, 106.85, 1900, "guilder_ni"),
            new Expect("Manila", 14.6, 120.98, 1950, "peso_ph"),
            new Expect("Kuala Lumpur", 3.14, 101.69, 1990, "ringgit"),
            new Expect("Singapore", 1.29, 103.85, 1990, "sgd"),
            new Expect("Hong Kong", 22.3, 114.17, 1990, "hkd"),
            new Expect("Taipei", 25.03, 121.57, 1990, "twd"),
            new Expect("Seoul", 37.57, 126.98, 1800, "mun_kr"),
            new Expect("Seoul", 37.57, 126.98, 1990, "won_kr"),
            new Expect("Pyongyang", 39.04, 125.76, 1990, "won_kp"),
            new Expect("Naha", 26.21, 127.68, 1965, "dollar"),
            new Expect("Ulaanbaatar", 47.92, 106.92, 1990, "tugrik"),
            new Expect("Samarkand", 39.65, 66.96, 1700, "tanga"),
            new Expect("Almaty", 43.24, 76.89, 2000, "tenge"),
            new Expect("Tashkent", 41.3, 69.24, 2010, "sum_uz"),
            new Expect("Tbilisi", 41.72, 44.78, 2010, "lari"),
            new Expect("Buenos Aires", -34.6, -58.38, 1700, "peso_real"),
            new Expect("Buenos Aires", -34.6, -58.38, 1990, "austral"),
            new Expect("Sao Paulo", -23.55, -46.63, 1750, "real_br"),
            new Expect("Sao Paulo", -23.55, -46.63, 2020, "real_br_new"),
            new Expect("Rio de Janeiro", -22.91, -43.17, 1960, "cruzeiro_42"),
            new Expect("Mexico City", 19.43, -99.13, 1950, "peso_mx"),
            new Expect("Lima", -12.05, -77.04, 1990, "inti"),
            new Expect("Bogota", 4.71, -74.07, 1990, "peso_co"),
            new Expect("Caracas", 10.48, -66.9, 2022, "bolivar_digital"),
            new Expect("Havana", 23.11, -82.37, 1990, "peso_cu"),
            new Expect("Kingston", 17.97, -76.79, 1950, "pound_jm"),
            new Expect("Montreal", 45.5, -73.57, 1950, "cad"),
            new Expect("Toronto", 43.65, -79.38, 1990, "cad"),
            new Expect("Quebec", 46.81, -71.21, 1700, "livre"),
            new Expect("York Factory", 57.0, -92.3, 1750, "made_beaver"),
            new Expect("Honolulu", 21.31, -157.86, 1880, "dollar_hi"),
            new Expect("Sitka", 57.05, -135.33, 1830, "ruble"),
            new Expect("Sydney", -33.87, 151.21, 1950, "aud_pound"),
            new Expect("Sydney", -33.87, 151.21, 2000, "aud"),
            new Expect("Auckland", -36.85, 174.76, 2000, "nzd"),
            new Expect("Port Moresby", -9.44, 147.18, 2000, "kina"),
            new Expect("Suva", -18.14, 178.44, 2000, "fjd"),
            new Expect("Noumea", -22.27, 166.46, 2000, "cfp_franc"));

    /** The years the random points are tried in. */
    public static final int[] CHECK_YEARS = {-600, 0, 800, 1300, 1600, 1800, 1900, 1950, 2000, 2026};

    /** People per square kilometre below which a point counts as empty (the sea, ice, desert). */
    public static final double SETTLED = 0.1d;

    /** What an answer of the table is: a money with names, barter, or one of the old placeholders. */
    public enum Answer { NAMED, BARTER, PLACEHOLDER }

    /** How many people live at a point in a year (people per km2). */
    @FunctionalInterface
    public interface Density {
        double at(double lat, double lon, int year);
    }

    /** The game's HYDE grid; 0 where it knows nothing. */
    public static final Density HYDE = (lat, lon, year) -> {
        try {
            return HydeGrid.get().density(lat, lon, year).orElse(0.0d);
        } catch (Throwable t) {
            return 0.0d;
        }
    };

    public static Answer classify(MoneyTable.Money m) {
        if (m == null || m.barter()) {
            return Answer.BARTER;
        }
        return m.kind() == MoneyTable.Kind.LOCAL ? Answer.PLACEHOLDER : Answer.NAMED;
    }

    /** One line per test place that gets another money, or none. */
    public static List<String> wrongPlaces(MoneyTable t, MoneyTable.Rulers rulers) {
        List<String> bad = new ArrayList<>();
        for (Expect e : EXPECT) {
            MoneyTable.Here h = t.at(e.lat(), e.lon(), e.year(), rulers);
            if (!h.money().id().equals(e.money())) {
                bad.add(e.place() + " " + e.year() + ": " + h.money().id() + " (row " + h.row().id() + "), not " + e.money());
            }
        }
        return bad;
    }

    /** The census of settled random points: how many answers of each kind, and how many kinds of money were met. */
    public record Census(int points, int settled, int named, int barter, int placeholder, int monies,
                         List<String> placeholderAt) {
        public double placeholderShare() {
            return settled == 0 ? 0.0d : placeholder / (double) settled;
        }
    }

    public static Census census(MoneyTable t, MoneyTable.Rulers rulers, Density density, List<double[]> points,
                                int[] years) {
        int settled = 0;
        int named = 0;
        int barter = 0;
        int placeholder = 0;
        Set<String> monies = new HashSet<>();
        List<String> where = new ArrayList<>();
        for (int y : years) {
            for (double[] p : points) {
                if (density.at(p[0], p[1], y) < SETTLED) {
                    continue;
                }
                settled++;
                MoneyTable.Here h = t.at(p[0], p[1], y, rulers);
                monies.add(h.money().id());
                switch (classify(h.money())) {
                    case NAMED -> named++;
                    case BARTER -> barter++;
                    default -> {
                        placeholder++;
                        if (where.size() < 5) {
                            where.add(String.format(java.util.Locale.ROOT, "%.1f,%.1f in %d", p[0], p[1], y));
                        }
                    }
                }
            }
        }
        return new Census(points.size() * years.length, settled, named, barter, placeholder, monies.size(), where);
    }

    /**
     * The world layer on its own: every test place gets its money; the register's last year carries on into 2025
     * and 2026 (Sofia keeps the lev in 2025 and pays in euros in 2026); at least 300 kinds of money are loaded; no
     * more than 1 in 100 settled random answers is a placeholder. Null when all is well.
     */
    public static String selfTest(MoneyTable t, MoneyTable.Rulers rulers, Density density) {
        if (t.monies().size() < 300) {
            return "only " + t.monies().size() + " kinds of money are loaded";
        }
        List<String> bad = wrongPlaces(t, rulers);
        if (!bad.isEmpty()) {
            return bad.size() + " place(s) wrong: " + String.join("; ", bad.subList(0, Math.min(6, bad.size())));
        }
        Census c = census(t, rulers, density, com.eternalengine.news.BookForms.randomPoints(400, 23L), CHECK_YEARS);
        if (c.settled() == 0) {
            return "no random point is settled: the population grid did not load";
        }
        if (c.placeholderShare() > 0.01d) {
            return c.placeholder() + " of " + c.settled() + " settled random answers are placeholders (" + c.placeholderAt() + ")";
        }
        return null;
    }
}
