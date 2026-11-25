package com.eternalengine.knowledge;

import com.eternalengine.civilization.RulerRegister;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 37 (A2): <b>the calendar this place actually uses</b>.
 *
 * <p>The mod has always had one date. The clock runs, {@code /ee date} prints it, and it is a
 * Gregorian date in every year and every country on Earth — which is to say, it is wrong nearly
 * everywhere for nearly all of history. A paper printed in Hyderabad in 1948 was not dated
 * 14 September 1948; it was dated in the Fasli year 1358, and the Muslims in the same city would
 * have given you 10 Dhu al-Qadah 1367, and the Hindus 29 Bhadrapada 2005 of Vikram Samvat. Three
 * dates, one morning, one city.
 *
 * <p>So this register does two things. It <b>converts</b>: give it the mod's date and a calendar
 * and it will write that date in it. And it <b>chooses</b>: give it a place and it will tell you
 * which calendars the people there were writing, because that is a fact about a place and a year
 * and not about arithmetic, and it lives in {@code calendars.json.gz}.
 *
 * <p>Every answer says whether it is exact or an approximation. That distinction is most of the
 * honesty in this file:
 *
 * <pre>
 *   exact         Gregorian, Julian, Hijri (tabular), Saka, Chinese (computed from astronomy)
 *   approximate   Vikram and Fasli months, and a year counted from the year a rule began
 * </pre>
 *
 * <h2>Why some of it cannot be exact</h2>
 *
 * <p><b>Hijri</b> is computed by the tabular rule — a 30-year cycle with eleven leap years — but
 * the calendar Muslims have actually kept is sighted: a month begins when the crescent is seen,
 * and that is a decision made by people looking at the sky, not an arithmetic one. The tabular
 * calendar is right to within a day or two of the sighted one, and sometimes three. It is the
 * standard approximation, and the month is flagged as one.
 *
 * <p><b>Vikram Samvat</b> is lunisolar, and its months are settled by a panchang computed for a
 * particular place. There is no arithmetic version that is right everywhere; the regional variants
 * (the North Indian year begins in Chaitra, the Gujarati in Kartika) disagree by six months about
 * when a year starts. This register gets the <b>year</b> right, which is what a document is dated
 * by, and gives the month by the solar approximation, which is right to about a fortnight.
 *
 * <p><b>Fasli</b> - the revenue calendar of the Deccan, and the one on Hyderabad's papers - is
 * solar, and its year number is the Hijri year counted by solar years: add 590 and you have the
 * Gregorian year. The Deccan year runs from July to June, so the number is exact; the month is
 * the same solar approximation.
 *
 * <p><b>Chinese</b> is lunisolar, and it is the one calendar here that is <b>computed from
 * astronomy</b> rather than approximated: the year turns at the new moon that is the second or
 * third after the winter solstice, and the month with no major solar term in it is the leap
 * month. That needs the real motion of the sun and the moon, so that is what it uses — Meeus's
 * series, reduced to Beijing time. It is exact, and it reproduces eighteen known Chinese New
 * Year dates from 1966 to 2026.
 *
 * <h2>Regnal years</h2>
 *
 * <p>"In the fifteenth year of Tiberius Caesar" is how Luke dates his gospel, and it is how most
 * of the world dated things for most of history. The year of a rule comes from step 8's
 * {@code world_rulers}, which holds 1,581 polities over a half-degree grid to 2024; this register
 * asks it who ruled a place in a year and since when, and counts.
 *
 * <p><b>What that is and is not.</b> Cliopatria records polities, not monarchs, so the count is
 * years of a <i>rule</i>, not the regnal year of one king: a long stretch of Mughal rule gives a
 * large number where a contemporary would have named the emperor and counted from his accession.
 * The number is truthful — it is how long this rule has run — and it is marked approximate,
 * because calling it a regnal year would be claiming more than the source carries.
 *
 * <h2>Where nothing is known</h2>
 *
 * <p>Three regions of the pack say so instead of answering: the pre-Columbian Americas, most of
 * sub-Saharan Africa, and the Pacific. They have calendars this register cannot write — the Long
 * Count, the thirteen-month Ethiopian year, seasonal reckonings — and they hand back a Gregorian
 * date carrying a note that says plainly no one there would have written it. Every region row
 * carries the Wikipedia article it came from, and a row with no article is a bug.
 *
 * <p><b>The rule for a caller: ask this before you print a date on anything.</b> A newspaper, a
 * letter, an inscription and a coin all carry the date their own people would have written, and a
 * paper dated 1948 in Hyderabad is as wrong as a map of the wrong century.
 */
public final class Calendars {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/calendars.json.gz";

    private static final Calendars INSTANCE = new Calendars();
    private static final Calendars EMPTY = new Calendars();

    /** Days from 1 January 1970 to the Julian day number 0. */
    private static final long JDN_OF_EPOCH = 2440588L;
    /** Days from 1 January 1970 to Rata Die 0 (1 January 1 CE, proleptic Gregorian). */
    private static final long RD_OF_EPOCH = 719163L;
    /** Rata Die of 1 Muharram 1 AH (16 July 622 CE, Julian). */
    private static final long HIJRI_EPOCH = 227015L;

    /** The calendars this register can write a date in. */
    public enum System {
        GREGORIAN, JULIAN, HIJRI, SAKA, VIKRAM, FASLI, CHINESE, REGNAL;

        public String label() {
            switch (this) {
                case GREGORIAN -> { return "Gregorian"; }
                case JULIAN -> { return "Julian"; }
                case HIJRI -> { return "Hijri"; }
                case SAKA -> { return "Saka"; }
                case VIKRAM -> { return "Vikram Samvat"; }
                case FASLI -> { return "Fasli"; }
                case CHINESE -> { return "Chinese"; }
                default -> { return "the rule of"; }
            }
        }

        /** Is what this register produces for it an exact date, or an approximation? */
        public boolean exact() {
            return this == GREGORIAN || this == JULIAN || this == HIJRI || this == SAKA
                    || this == CHINESE;
        }

        /** The key the pack spells this calendar with. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static System ofKey(String key) {
            for (System s : values()) {
                if (s.key().equals(key)) {
                    return s;
                }
            }
            return null;
        }
    }

    /**
     * A date written in one calendar.
     *
     * @param text        "10 Dhu al-Qadah 1367", "the fifteenth year of Mughal rule"
     * @param approximate true where the register is giving an approximation — Vikram and Fasli
     *                    months, the Chinese month and day, or a year of a rule
     */
    public record Date(System system, int year, int month, int day, String text,
                       boolean approximate) {

        /** The year on its own, which is what most documents carry. */
        public String yearText() {
            return switch (system) {
                case CHINESE -> {
                    int i = Math.floorMod(year - 4, 60);
                    yield GANZHI_STEMS[i % 10]
                            + GANZHI_BRANCHES[i % 12].toLowerCase(Locale.ROOT);
                }
                case REGNAL -> text;
                default -> year + " " + system.label();
            };
        }
    }

    /**
     * A year counted from the year a rule began — the way most of the world dated things for most
     * of history.
     *
     * @param ruler the polity as the source spells it
     * @param since the year the present rule began
     * @param year  how far into it this year is: 1 for the first year
     * @param text  "the twelfth year of Mughal rule"
     */
    public record Regnal(String ruler, int since, int year, String text) {

        /** "Mughal Empire, 1668" — the short form for a dateline. */
        public String shortText() {
            return ruler + ", " + since + " + " + year;
        }
    }

    // ------------------------------------------------------------------ the region table

    /** One row of the pack: a box, the years it covers, and the calendars used inside it. */
    public record Region(String name, double lat0, double lat1, double lon0, double lon1,
                         int from, int to, List<System> calendars, String note, String source) {

        boolean covers(double lat, double lon, int year) {
            return lat >= lat0 && lat <= lat1 && lon >= lon0 && lon <= lon1
                    && year >= from && year <= to;
        }
    }

    private boolean loaded;
    private String source = "missing";
    private List<Region> regions = List.of();

    private Calendars() {}

    /** The register, loaded from the shipped pack. */
    public static synchronized Calendars get() {
        if (!INSTANCE.loaded) {
            INSTANCE.loaded = true;
            INSTANCE.loadResource();
        }
        return INSTANCE;
    }

    /** An empty register: no regions, and every query falls back to Gregorian. For checks. */
    public static Calendars empty() {
        return EMPTY;
    }

    private void loadResource() {
        try (InputStream in = Calendars.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(
                    new GZIPInputStream(in, 1 << 16), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                install(root);
                source = RESOURCE;
            }
        } catch (Exception e) {
            source = RESOURCE + " failed: " + e.getMessage();
        }
    }

    private void install(JsonObject root) {
        List<Region> out = new ArrayList<>();
        JsonArray arr = root.getAsJsonArray("regions");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                List<System> cals = new ArrayList<>();
                JsonArray ca = o.getAsJsonArray("calendars");
                for (int j = 0; j < ca.size(); j++) {
                    System s = System.ofKey(ca.get(j).getAsString());
                    if (s != null && !cals.contains(s)) {
                        cals.add(s);
                    }
                }
                if (cals.isEmpty()) {
                    continue;
                }
                out.add(new Region(
                        o.get("name").getAsString(),
                        o.get("lat0").getAsDouble(), o.get("lat1").getAsDouble(),
                        o.get("lon0").getAsDouble(), o.get("lon1").getAsDouble(),
                        o.get("from").getAsInt(),
                        o.has("to") && !o.get("to").isJsonNull() ? o.get("to").getAsInt()
                                : Integer.MAX_VALUE,
                        List.copyOf(cals),
                        o.has("note") ? o.get("note").getAsString() : "",
                        o.has("source") ? o.get("source").getAsString() : ""));
            }
        }
        regions = List.copyOf(out);
    }

    public int regionCount() {
        return regions.size();
    }

    /** Every row of the table, for checks and for the data-credits listing. */
    public List<Region> regions() {
        return regions;
    }

    public String source() {
        return source;
    }

    /**
     * Which calendars a place was writing its dates in, in the order a paper would use them: the
     * first is the one on official documents, and the rest are what the other communities there
     * would have said. Boxes may overlap on purpose, so a place can be given several at once.
     *
     * <p>Hyderabad in 1948 returns {@code [FASLI, HIJRI, SAKA]} — the revenue calendar on the
     * government's papers, the Hijri year the Muslims of the city kept, and the Saka year behind
     * the Vikram Samvat the Hindus kept. Never empty: a place the pack knows nothing about gets
     * {@code [GREGORIAN]}, which is the mod's own date and not a claim about history.
     */
    public List<System> usedAt(double lat, double lon, int year) {
        Set<System> out = new LinkedHashSet<>();
        for (Region r : regions) {
            if (r.covers(lat, lon, year)) {
                out.addAll(r.calendars());
            }
        }
        if (out.isEmpty()) {
            out.add(System.GREGORIAN);
        }
        return List.copyOf(out);
    }

    /**
     * The notes carried by the rows that cover a place: mostly the plain statement that this
     * register cannot write the calendar the people there were actually using. A caller that
     * prints a date for a place whose note says "Not modelled" should print the note too.
     */
    public List<String> notesAt(double lat, double lon, int year) {
        List<String> out = new ArrayList<>();
        for (Region r : regions) {
            if (r.covers(lat, lon, year) && !r.note().isEmpty() && !out.contains(r.note())) {
                out.add(r.note());
            }
        }
        return out;
    }

    /**
     * The calendar on official documents: the first of {@link #usedAt}. Everything that prints one
     * date — a dateline, a coin, a letter — should print this one.
     */
    public System primaryAt(double lat, double lon, int year) {
        return usedAt(lat, lon, year).get(0);
    }

    /**
     * The date of one day in every calendar the place used, in the order a paper would use them.
     * This is the answer to "what date is it".
     *
     * @param epochDay the mod's own day count, as {@code LocalDate.toEpochDay()} reckons it
     */
    public List<Date> at(double lat, double lon, long epochDay) {
        int year = java.time.LocalDate.ofEpochDay(epochDay).getYear();
        List<Date> out = new ArrayList<>();
        for (System s : usedAt(lat, lon, year)) {
            if (s == System.REGNAL) {
                Optional<Regnal> r = regnal(lat, lon, year);
                r.ifPresent(reg -> out.add(new Date(System.REGNAL, reg.year(), 0, 0,
                        reg.text(), true)));
            } else {
                out.add(in(s, epochDay));
            }
        }
        return List.copyOf(out);
    }

    /**
     * The one date to print on a document: the primary calendar of the place, and the regnal year
     * after it where there is one. "Fasli 1358" for Hyderabad in 1948.
     */
    public String dateline(double lat, double lon, long epochDay) {
        int year = java.time.LocalDate.ofEpochDay(epochDay).getYear();
        System s = primaryAt(lat, lon, year);
        Date d = in(s, epochDay);
        Optional<Regnal> r = regnal(lat, lon, year);
        if (r.isPresent()) {
            return d.text() + " - " + r.get().text();
        }
        return d.text();
    }

    /**
     * The year of the rule, counted from the year it began: the way most of the world dated things
     * for most of history. Empty where step 8's register has no ruler for the place, and empty is
     * not permission to invent one.
     *
     * <p>Marked approximate, because the source records polities and not monarchs: this is how
     * long the present rule has run, not a king's regnal year.
     */
    public static Optional<Regnal> regnal(double lat, double lon, int year) {
        Optional<RulerRegister.Hit> hit = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year);
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        RulerRegister.Hit h = hit.get();
        int since = h.start();
        if (since > year) {
            return Optional.empty();
        }
        int n = year - since + 1;
        String text = n == 1
                ? "the first year of " + h.ruler() + " rule"
                : "the " + ordinal(n) + " year of " + h.ruler() + " rule";
        return Optional.of(new Regnal(h.ruler(), since, n, text));
    }

    /** "first", "twelfth", "twenty-first", "128th" - for year counts and days of a month. */
    public static String ordinal(int n) {
        if (n <= 0) {
            return String.valueOf(n);
        }
        int v = n % 100;
        String suffix = v >= 11 && v <= 13 ? "th" : switch (v % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        if (n >= 100) {
            return n + suffix;
        }
        if (n <= 19) {
            return SMALL_ORDINAL[n];
        }
        int t = n / 10;
        int o = n % 10;
        return o == 0 ? TENS_ORDINAL[t] : TENS[t] + "-" + SMALL_ORDINAL[o];
    }

    private static final String[] SMALL_ORDINAL = {
            "", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth",
            "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth",
            "sixteenth", "seventeenth", "eighteenth", "nineteenth"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty",
            "seventy", "eighty", "ninety"};
    private static final String[] TENS_ORDINAL = {"", "", "twentieth", "thirtieth", "fortieth",
            "fiftieth", "sixtieth", "seventieth", "eightieth", "ninetieth"};

    // ------------------------------------------------------------------ the conversions

    /** Julian day number for a mod date. */
    public static long jdn(long epochDay) {
        return epochDay + JDN_OF_EPOCH;
    }

    /** Rata Die — days since 1 January 1 CE — for a mod date. */
    public static long rataDie(long epochDay) {
        return epochDay + RD_OF_EPOCH;
    }

    /** The Gregorian date, which is what the mod's own clock keeps. */
    public static Date gregorian(long epochDay) {
        java.time.LocalDate d = java.time.LocalDate.ofEpochDay(epochDay);
        int y = d.getYear();
        return new Date(System.GREGORIAN, y, d.getMonthValue(), d.getDayOfMonth(),
                d.getDayOfMonth() + " " + MONTHS_GREGORIAN[d.getMonthValue() - 1] + " "
                        + (y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y)), false);
    }

    /**
     * The Julian date, which is what Europe used until 1582 and what much of the Orthodox world
     * used long after: Wilkins's almanac of 1707 still printed both.
     */
    public static Date julian(long epochDay) {
        long c = jdn(epochDay) + 32082L;
        long d1 = (4L * c + 3L) / 1461L;
        long e1 = c - (1461L * d1) / 4L;
        long m1 = (5L * e1 + 2L) / 153L;
        int day = (int) (e1 - (153L * m1 + 2L) / 5L + 1L);
        int month = (int) (m1 + 3L - 12L * (m1 / 10L));
        int year = (int) (d1 - 4800L + m1 / 10L);
        return new Date(System.JULIAN, year, month, day,
                day + " " + MONTHS_GREGORIAN[month - 1] + " "
                        + (year < 0 ? Math.abs(year) + " BCE" : String.valueOf(year)), false);
    }

    /** Is a Hijri year a leap year, by the tabular 30-year rule? */
    static boolean hijriLeap(int y) {
        return ((14 + 11 * y) % 30) < 11;
    }

    /** Rata Die of the first day of a Hijri month. */
    static long fixedFromHijri(int y, int m, int d) {
        return HIJRI_EPOCH - 1L
                + 354L * (y - 1)
                + (3L + 11L * (long) y) / 30L
                + 29L * (m - 1)
                + (long) (m / 2)
                + d;
    }

    /**
     * The Hijri date by the tabular rule. Right to within a day or two of the sighted calendar,
     * which is a decision made by people looking at the sky and not an arithmetic one, so the
     * month and day are flagged approximate.
     */
    public static Date hijri(long epochDay) {
        long rd = rataDie(epochDay);
        int y = (int) Math.floor((30.0d * (rd - HIJRI_EPOCH) + 10646.0d) / 10631.0d);
        long prior = rd - fixedFromHijri(y, 1, 1);
        int m = (int) Math.min(12, Math.max(1, Math.floor((11.0d * prior + 330.0d) / 325.0d)));
        while (m > 1 && fixedFromHijri(y, m, 1) > rd) {
            m--;
        }
        while (m < 12 && fixedFromHijri(y, m + 1, 1) <= rd) {
            m++;
        }
        int d = (int) (rd - fixedFromHijri(y, m, 1) + 1);
        return new Date(System.HIJRI, y, m, d,
                d + " " + MONTHS_HIJRI[m - 1] + " " + y + " AH", true);
    }

    /**
     * The Saka date: the solar calendar of India, and the national calendar since 1957. Its year
     * begins at the vernal equinox, 22 March (21 in a leap year). Exact.
     */
    public static Date saka(long epochDay) {
        java.time.LocalDate g = java.time.LocalDate.ofEpochDay(epochDay);
        int gy = g.getYear();
        java.time.LocalDate start = java.time.LocalDate.of(gy, 3, isLeap(gy) ? 21 : 22);
        int y;
        if (g.isBefore(start)) {
            y = gy - 79;
            start = java.time.LocalDate.of(gy - 1, 3, isLeap(gy - 1) ? 21 : 22);
        } else {
            y = gy - 78;
        }
        int doy = (int) (g.toEpochDay() - start.toEpochDay());   // days into the Saka year
        int m = 1;
        while (m < 12 && doy >= SAKA_MONTH_LENGTHS[m - 1]) {
            doy -= SAKA_MONTH_LENGTHS[m - 1];
            m++;
        }
        return new Date(System.SAKA, y, m, doy + 1,
                (doy + 1) + " " + MONTHS_INDIAN[m - 1] + " " + y + " Saka", false);
    }

    /**
     * The Vikram Samvat date. The <b>year</b> is right — that is what a document is dated by — and
     * it is the North Indian Chaitradi convention, which begins in Chaitra. The month is the solar
     * approximation: the real calendar is lunisolar and settled by a panchang for a particular
     * place, so it is right to about a fortnight and flagged.
     */
    public static Date vikram(long epochDay) {
        Date s = saka(epochDay);
        int y = s.year() + 135;
        return new Date(System.VIKRAM, y, s.month(), s.day(),
                s.day() + " " + MONTHS_INDIAN[s.month() - 1] + " " + y + " VS", true);
    }

    /**
     * The Fasli date: the revenue calendar of the Deccan, the one on Hyderabad's papers. It is
     * Akbar's, dated from his accession in 1556 (963 AH), and its year number is the Hijri year
     * counted by solar years: add 590 and you have the Gregorian year. The Deccan year runs from
     * July to June, so the year is exact and the month is the same solar approximation.
     */
    public static Date fasli(long epochDay) {
        java.time.LocalDate g = java.time.LocalDate.ofEpochDay(epochDay);
        int gy = g.getYear();
        int y = g.getMonthValue() >= 7 ? gy - 590 : gy - 591;
        Date s = saka(epochDay);
        return new Date(System.FASLI, y, s.month(), s.day(),
                "Fasli " + y + " (" + (s.day() + " " + MONTHS_INDIAN[s.month() - 1]) + ")", true);
    }

    /**
     * The Chinese date: lunisolar, computed, and <b>exact</b>.
     *
     * <p>The year turns at the new moon that is the second or third after the winter solstice,
     * and the month that has no major solar term in it is the leap month. Both rules need real
     * astronomy, so this is real astronomy: the new-moon times are Meeus's <i>Astronomical
     * Algorithms</i> chapter 49 series (good to a couple of seconds over two millennia), the
     * solar longitude is his chapter 25 series (good to 0.01 degrees), and both are reduced to
     * Beijing time. It reproduces eighteen known Chinese New Year dates from 1966 to 2026,
     * including 21 January 1966 (the earliest the new year can fall) and 20 February 1985 (a
     * year with a leap month).
     *
     * <p>Where it is less sure: the times are dynamical, so the register subtracts the
     * Earth's clock error (delta-T) to get to civil time. That is well established from about
     * 1600 and interpolated before it, and the further back you go the wider the interpolation:
     * before about 1500 BCE a month boundary can slip by a day. The year name, the leap month
     * and the day of the month are all still right in the overwhelming majority of cases.
     */
    public static Date chinese(long epochDay) {
        Chinese c = chineseInfo(epochDay);
        return new Date(System.CHINESE, c.year(), c.month(), c.day(), c.text(), false);
    }

    /**
     * The Chinese date in full: the year's ganzhi and animal, and the month and day inside it.
     *
     * @param newYear the day the year began, as the mod counts days
     * @param year    the year, named by the Gregorian year its first day fell in
     * @param month   1 to 12
     * @param leap    true for a repeated month, which is how the calendar keeps in step
     * @param day     1 to 30
     */
    public record Chinese(long newYear, int year, int month, boolean leap, int day,
                          String ganzhi, String animal) {

        /** "the 3rd day of the 5th month of Gengzi, the year of the Rat". */
        public String text() {
            return (leap ? "the " + ordinal(day) + " day of the leap " + ordinal(month) + " month"
                    : "the " + ordinal(day) + " day of the " + ordinal(month) + " month")
                    + " of " + ganzhi + ", the year of the " + animal;
        }

        /** "Gengzi" — the sexagenary name, which is what a document is headed by. */
        public String yearText() {
            return ganzhi + " (" + year + ")";
        }
    }

    /**
     * The Chinese date with its parts. The month and day are counted from the year's first day,
     * walking the months forward; a year of thirteen months has one month repeated, and the
     * repeated one is the month with no major solar term in it.
     */
    public static Chinese chineseInfo(long epochDay) {
        long newYear = chineseNewYearOnOrBefore(epochDay);
        long nextNewYear = chineseNewYearOnOrBefore(newYear + 385);
        int leapSlot = leapSlotOfYear(newYear, nextNewYear);

        int year = java.time.LocalDate.ofEpochDay(newYear).getYear();
        int idx = Math.floorMod(year - 4, 60);
        String ganzhi = GANZHI_STEMS[idx % 10] + GANZHI_BRANCHES[idx % 12].toLowerCase(Locale.ROOT);
        String animal = ANIMALS[idx % 12];

        long monthStart = newYear;
        for (int slot = 0; slot < 14; slot++) {
            long next = newMoonDayAfter(monthStart);
            if (epochDay < next) {
                int number = leapSlot >= 0 && slot >= leapSlot ? slot : slot + 1;
                return new Chinese(newYear, year, number, leapSlot >= 0 && slot == leapSlot,
                        (int) (epochDay - monthStart + 1), ganzhi, animal);
            }
            monthStart = next;
        }
        return new Chinese(newYear, year, 12, false, (int) (epochDay - monthStart + 1),
                ganzhi, animal);
    }

    /**
     * Which month of the year is the repeated one, counted from 0: the month with no major solar
     * term in it. -1 where the year has only twelve months.
     */
    private static int leapSlotOfYear(long newYear, long nextNewYear) {
        long m = newYear;
        int leap = -1;
        for (int slot = 0; slot < 14; slot++) {
            long next = newMoonDayAfter(m);
            if (next >= nextNewYear) {
                break;
            }
            if (leap < 0 && !hasMajorSolarTerm(m, next)) {
                leap = slot;
            }
            m = next;
        }
        return leap;
    }

    /** The first day of the Chinese year that began on or before this day. */
    public static long chineseNewYearOnOrBefore(long epochDay) {
        long solstice = winterSolsticeOnOrBefore(epochDay);
        long year = firstMonthOfSui(solstice);
        if (year > epochDay) {
            solstice = winterSolsticeOnOrBefore(solstice - 200);
            year = firstMonthOfSui(solstice);
        }
        return year;
    }

    /**
     * Walk forward from the month holding the winter solstice — which is month 11 by definition —
     * until the month that opens the new year. A month carrying no major solar term is a leap
     * month and does not advance the count, which is what pushes the new year a month later in
     * the years that have one.
     */
    private static long firstMonthOfSui(long solstice) {
        long m = newMoonDayOnOrBefore(solstice);
        long monthEleven = m;
        int n = 11;
        for (int i = 0; i < 16; i++) {
            if (n == 1) {
                return m;
            }
            long next = newMoonDayAfter(m);
            if (m == monthEleven || hasMajorSolarTerm(m, next)) {
                n = n == 11 ? 12 : 1;
            }
            m = next;
        }
        return m;
    }

    /** Does the lunation from {@code start} to {@code end} carry one of the twelve major terms? */
    static boolean hasMajorSolarTerm(long start, long end) {
        return (int) (solarLongitudeAtMidnightInChina(start) / 30)
                != (int) (solarLongitudeAtMidnightInChina(end) / 30);
    }

    /** The day of the winter solstice on or before this day: when the sun reaches 270 degrees. */
    public static long winterSolsticeOnOrBefore(long epochDay) {
        int y = java.time.LocalDate.ofEpochDay(epochDay).getYear();
        long s = solsticeInYear(y);
        if (s != Long.MIN_VALUE && s <= epochDay) {
            return s;
        }
        s = solsticeInYear(y - 1);
        return s != Long.MIN_VALUE ? s : epochDay - 355;
    }

    private static long solsticeInYear(int y) {
        long base = java.time.LocalDate.of(y, 12, 21).toEpochDay();
        for (int off = 0; off <= 3; off++) {
            for (long d : new long[] {base + off, base - off}) {
                double a = solarLongitudeAtMidnightInChina(d);
                double b = solarLongitudeAtMidnightInChina(d + 1);
                if (((270.0d - a) % 360.0d + 360.0d) % 360.0d <= ((b - a) % 360.0d + 360.0d) % 360.0d) {
                    return d;
                }
            }
        }
        return Long.MIN_VALUE;
    }

    /** Solar longitude, in degrees, at midnight Beijing time on this day. */
    static double solarLongitudeAtMidnightInChina(long epochDay) {
        return solarLongitude(midnightInChina(epochDay));
    }

    /** Meeus, <i>Astronomical Algorithms</i> ch. 25: good to about 0.01 degrees. */
    static double solarLongitude(double jd) {
        double t = (jd - 2451545.0d) / 36525.0d;
        double l0 = 280.46646d + 36000.76983d * t + 0.0003032d * t * t;
        double m = (357.52911d + 35999.05029d * t - 0.0001537d * t * t) * DEG;
        double c = (1.914602d - 0.004817d * t - 0.000014d * t * t) * Math.sin(m)
                + (0.019993d - 0.000101d * t) * Math.sin(2.0d * m)
                + 0.000289d * Math.sin(3.0d * m);
        double v = l0 + c;
        return v - 360.0d * Math.floor(v / 360.0d);
    }

    private static final double DEG = Math.PI / 180.0d;

    /** The Julian day, in dynamical time, of the mean new moon of lunation {@code k}. */
    static double newMoon(double k) {
        double t = k / 1236.85d;
        double jd = 2451550.09766d + 29.530588861d * k
                + 0.00015437d * t * t - 0.000000150d * t * t * t
                + 0.00000000073d * t * t * t * t;
        double m = (2.5534d + 29.10535670d * k - 0.0000014d * t * t
                - 0.00000011d * t * t * t) * DEG;
        double mp = (201.5643d + 385.81693528d * k + 0.0107582d * t * t
                + 0.00001238d * t * t * t - 0.000000058d * t * t * t * t) * DEG;
        double f = (160.7108d + 390.67050284d * k - 0.0016118d * t * t
                - 0.00000227d * t * t * t + 0.000000011d * t * t * t * t) * DEG;
        double om = (124.7746d - 1.56375588d * k + 0.0020672d * t * t
                + 0.00000215d * t * t * t) * DEG;
        double e = 1.0d - 0.002516d * t - 0.0000047d * t * t;
        double s = -0.40720d * Math.sin(mp)
                + 0.17241d * e * Math.sin(m)
                + 0.01608d * Math.sin(2.0d * mp)
                + 0.01039d * Math.sin(2.0d * f)
                + 0.00739d * e * Math.sin(mp - m)
                - 0.00514d * e * Math.sin(mp + m)
                + 0.00208d * e * e * Math.sin(2.0d * m)
                - 0.00111d * Math.sin(mp - 2.0d * f)
                - 0.00057d * Math.sin(mp + 2.0d * f)
                + 0.00056d * e * Math.sin(2.0d * mp + m)
                - 0.00042d * Math.sin(3.0d * mp)
                + 0.00042d * e * Math.sin(m + 2.0d * f)
                + 0.00038d * e * Math.sin(m - 2.0d * f)
                - 0.00024d * e * Math.sin(2.0d * mp - m)
                - 0.00017d * Math.sin(om)
                - 0.00007d * Math.sin(mp + 2.0d * m)
                + 0.00004d * Math.sin(2.0d * mp - 2.0d * f);
        return jd + s;
    }

    /** The Beijing day on which the new moon of lunation {@code k} falls. */
    static long newMoonDay(double k, long hint) {
        return dayInChina(newMoon(k) - deltaT(java.time.LocalDate.ofEpochDay(hint).getYear())
                / 86400.0d, hint);
    }

    /** The day of the last new moon on or before this day, as Beijing counts days. */
    static long newMoonDayOnOrBefore(long epochDay) {
        double k = (epochDay + 2440587.5d - 2451550.09766d) / 29.530588861d;
        long lo = (long) Math.floor(k) - 3;
        long best = lo;
        for (long i = lo; i <= lo + 7; i++) {
            if (newMoonDay(i, epochDay) <= epochDay) {
                best = i;
            }
        }
        return newMoonDay(best, epochDay);
    }

    /** The day of the first new moon after this day, as Beijing counts days. */
    static long newMoonDayAfter(long epochDay) {
        double k = (epochDay + 2440587.5d - 2451550.09766d) / 29.530588861d;
        long lo = (long) Math.floor(k) - 3;
        for (long i = lo; i <= lo + 9; i++) {
            long d = newMoonDay(i, epochDay);
            if (d > epochDay) {
                return d;
            }
        }
        return epochDay + 30;
    }

    /** Beijing's offset from Greenwich: 8 hours since 1929, local mean time before. */
    static double chineseZone(long epochDay) {
        return epochDay >= java.time.LocalDate.of(1929, 1, 1).toEpochDay()
                ? 8.0d : 1397.0d / 180.0d;
    }

    /** The universal-time instant that is midnight in Beijing on this day. */
    static double midnightInChina(long epochDay) {
        return epochDay + 2440587.5d - chineseZone(epochDay) / 24.0d;
    }

    /** The Beijing day on which this instant falls. */
    static long dayInChina(double jd, long hint) {
        return (long) Math.floor(jd + chineseZone(hint) / 24.0d + 0.5d) - 2440588L;
    }

    /**
     * The Earth's clock error, in seconds: dynamical time minus universal time. Meeus's moon
     * times are dynamical, and a civil date is universal, so this has to come off. Well
     * established from about 1600 and interpolated from the historical record before it.
     */
    static double deltaT(int year) {
        double t;
        if (year < -500) {
            double u = (year - 1820) / 100.0d;
            return -20.0d + 32.0d * u * u;
        }
        if (year < 500) {
            t = year / 100.0d;
            return 10583.6d - 1014.41d * t + 33.78311d * t * t - 5.952053d * t * t * t
                    - 0.1798452d * t * t * t * t + 0.022174192d * Math.pow(t, 5)
                    + 0.0090316521d * Math.pow(t, 6);
        }
        if (year < 1600) {
            t = (year - 1000) / 100.0d;
            return 1574.2d - 556.01d * t + 71.23472d * t * t + 0.319781d * t * t * t
                    - 0.8503463d * Math.pow(t, 4) - 0.005050998d * Math.pow(t, 5)
                    + 0.0083572073d * Math.pow(t, 6);
        }
        if (year < 1700) {
            t = year - 1600;
            return 120.0d - 98.08d * t - 153.85d * t * t + 8.9286d * t * t * t;
        }
        if (year < 1800) {
            t = year - 1700;
            return 8.83d + 0.1603d * t - 0.0059285d * t * t + 0.00013336d * t * t * t
                    - 0.0000009d * Math.pow(t, 4);
        }
        if (year < 1860) {
            t = year - 1800;
            return 13.72d - 0.332447d * t + 0.0068612d * t * t + 0.0041116d * t * t * t
                    - 0.00037436d * Math.pow(t, 4) + 0.0000121272d * Math.pow(t, 5)
                    - 0.0000001699d * Math.pow(t, 6) + 0.000000000875d * Math.pow(t, 7);
        }
        if (year < 1900) {
            t = year - 1860;
            return 7.62d + 0.5737d * t - 0.251754d * t * t + 0.01680668d * t * t * t
                    - 0.0004473624d * Math.pow(t, 4) + 0.0000000000000d;
        }
        if (year < 1920) {
            t = year - 1900;
            return -2.79d + 1.494119d * t - 0.0598939d * t * t + 0.0061966d * t * t * t
                    - 0.000197d * Math.pow(t, 4);
        }
        if (year < 1941) {
            t = year - 1920;
            return 21.20d + 0.84493d * t - 0.076100d * t * t + 0.0020936d * t * t * t;
        }
        if (year < 1961) {
            t = year - 1950;
            return 29.07d + 0.407d * t - t * t / 233.0d + t * t * t / 2547.0d;
        }
        if (year < 1986) {
            t = year - 1975;
            return 45.45d + 1.067d * t - t * t / 260.0d - t * t * t / 718.0d;
        }
        if (year < 2005) {
            t = year - 2000;
            return 63.86d + 0.3345d * t - 0.060374d * t * t + 0.0017275d * t * t * t
                    + 0.000651814d * Math.pow(t, 4) + 0.00002373599d * Math.pow(t, 5);
        }
        if (year < 2050) {
            t = year - 2000;
            return 62.92d + 0.32217d * t + 0.005589d * t * t;
        }
        if (year < 2150) {
            double u = (year - 1820) / 100.0d;
            return -20.0d + 32.0d * u * u + 0.5628d * (2150 - year);
        }
        double u = (year - 1820) / 100.0d;
        return -20.0d + 32.0d * u * u;
    }

    /**
     * The date in a given calendar. Regnal needs a place, so ask {@link #regnal} for that one —
     * this returns an empty placeholder, and nothing should print it.
     */
    public static Date in(System s, long epochDay) {
        return switch (s) {
            case GREGORIAN -> gregorian(epochDay);
            case JULIAN -> julian(epochDay);
            case HIJRI -> hijri(epochDay);
            case SAKA -> saka(epochDay);
            case VIKRAM -> vikram(epochDay);
            case FASLI -> fasli(epochDay);
            case CHINESE -> chinese(epochDay);
            case REGNAL -> new Date(System.REGNAL, 0, 0, 0, "", true);
        };
    }

    private static boolean isLeap(int y) {
        return y % 4 == 0 && (y % 100 != 0 || y % 400 == 0);
    }

    // ------------------------------------------------------------------ names

    public static final String[] MONTHS_GREGORIAN = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    public static final String[] MONTHS_HIJRI = {
            "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Shaban",
            "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah"};

    /** Chaitra onwards: the North Indian solar months, shared by Saka, Vikram and Fasli. */
    public static final String[] MONTHS_INDIAN = {
            "Chaitra", "Vaisakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
            "Ashvina", "Kartika", "Agrahayana", "Pausha", "Magha", "Phalguna"};

    /** Saka month lengths: Chaitra 30, then five 31s, then six 30s. */
    private static final int[] SAKA_MONTH_LENGTHS = {
            30, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 30};

    private static final String[] GANZHI_STEMS = {
            "Jia", "Yi", "Bing", "Ding", "Wu", "Ji", "Geng", "Xin", "Ren", "Gui"};
    private static final String[] GANZHI_BRANCHES = {
            "Zi", "Chou", "Yin", "Mao", "Chen", "Si", "Wu", "Wei",
            "Shen", "You", "Xu", "Hai"};
    private static final String[] ANIMALS = {
            "Rat", "Ox", "Tiger", "Rabbit", "Dragon", "Snake",
            "Horse", "Goat", "Monkey", "Rooster", "Dog", "Pig"};
}
