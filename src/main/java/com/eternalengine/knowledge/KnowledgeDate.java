package com.eternalengine.knowledge;

import com.eternalengine.clock.SimulationClockManager;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Dates for what villagers know, as one sortable number: {@code year * 10000 + month * 100 + day}.
 *
 * <p>14 September 1948 is 19480914. Years before the common era work the same way: 250 BCE (year -250)
 * on 1 March is -250 * 10000 + 301, and the numbers still sort in time order. {@link #ALWAYS} is a fact
 * that was true before any date the game can be in.
 *
 * <p>Today comes from the world's one calendar, {@link com.eternalengine.clock.WorldCalendar} (step 18d): it
 * follows the Minecraft days with either clock, and only /ee date moves it back. With Terra++ time the date is the
 * Minecraft day count, which is what the Terra++ year is made of ({@code BASE_YEAR + dayCount * scale / 365}; step
 * 18b). A Terra++ year has 365 days, so in a leap year the last day shown is 30 December.
 */
public final class KnowledgeDate {

    public static final int ALWAYS = Integer.MIN_VALUE;
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};

    private KnowledgeDate() {
    }

    public static int key(int year, int month, int day) {
        return year * 10000 + month * 100 + day;
    }

    public static int year(int key) {
        return Math.floorDiv(key, 10000);
    }

    public static int month(int key) {
        return Math.floorMod(key, 10000) / 100;
    }

    public static int day(int key) {
        return Math.floorMod(key, 100);
    }

    /** Today in the game, as a key: the world's calendar (step 18d), or the middle of the year without a world. */
    public static int today() {
        SimulationClockManager clock = SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance() : null;
        if (clock == null) {
            return key(2000, 7, 1);
        }
        int today = com.eternalengine.clock.WorldCalendar.today();
        return today != ALWAYS ? today : fromDayOfYear(clock.getCurrentYear(), 183);
    }

    /**
     * The date on Minecraft day {@code dayCount} with Terra++ time: the year is
     * {@code baseYear + dayCount * scale / daysPerYear}, and what that division leaves is the day inside it
     * (day 1,806,020 at 1x from 3000 BCE is 1 January 1948; at 1x one Minecraft day is one day of history).
     */
    public static int fromTerraDay(long dayCount, int scale, int baseYear, int daysPerYear) {
        long scaled = Math.max(0L, dayCount) * Math.max(1, scale);
        int dpy = Math.max(1, daysPerYear);
        int year = (int) (baseYear + Math.floorDiv(scaled, dpy));
        int index = (int) Math.floorMod(scaled, (long) dpy);
        return fromDayOfYear(year, index + 1);
    }

    /**
     * The Minecraft day count that shows {@code key} with Terra++ time, or the first day after it that can (at
     * 10x one Minecraft day is ten days of history). In a leap year 31 December is not there (365-day years):
     * it gives 30 December. -1 before the base year.
     */
    public static long terraDayFor(int key, int scale, int baseYear, int daysPerYear) {
        int s = Math.max(1, scale);
        int dpy = Math.max(1, daysPerYear);
        long yearsFromBase = (long) year(key) - baseYear;
        if (yearsFromBase < 0) {
            return -1L;
        }
        int index = Math.min(dayOfYear(key), dpy) - 1;
        long day = Math.floorDiv(yearsFromBase * dpy + index + s - 1, s);
        while (day > 0 && year(fromTerraDay(day, s, baseYear, dpy)) > year(key)) {
            day--; // the year's last Minecraft day at this scale
        }
        return day;
    }

    /** The day after {@code key}. */
    public static int nextDay(int key) {
        if (key == ALWAYS || key == Integer.MAX_VALUE) {
            return key;
        }
        LocalDate d = LocalDate.of(year(key), Math.max(1, month(key)), Math.max(1, day(key))).plusDays(1);
        return key(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    public static int fromDayOfYear(int year, int dayOfYear) {
        int days = daysIn(year);
        LocalDate d = LocalDate.ofYearDay(days == 366 ? 2000 : 2001, Math.max(1, Math.min(days, dayOfYear)));
        return key(year, d.getMonthValue(), d.getDayOfMonth());
    }

    public static int dayOfYear(int key) {
        int y = year(key);
        return LocalDate.of(daysIn(y) == 366 ? 2000 : 2001, Math.max(1, month(key)), Math.max(1, day(key))).getDayOfYear();
    }

    public static int daysIn(int year) {
        boolean leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
        return leap ? 366 : 365;
    }

    /** "14 September 1948", "1 March 250 BCE". */
    public static String label(int key) {
        if (key == ALWAYS) {
            return "always";
        }
        int y = year(key), m = month(key), d = day(key);
        String yearText = y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y);
        if (m < 1 || m > 12) {
            return yearText;
        }
        return d + " " + MONTHS[m - 1] + " " + yearText;
    }

    /** "14 Sep". */
    public static String shortLabel(int key) {
        int m = month(key);
        return day(key) + " " + (m >= 1 && m <= 12 ? MONTHS[m - 1].substring(0, 3) : "?");
    }

    /** Parses "1948-09-14" or "19480914"; returns {@link #ALWAYS} for null or "always". */
    public static int parse(Object value) {
        if (value == null) {
            return ALWAYS;
        }
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("always") || s.equals("null")) {
            return ALWAYS;
        }
        if (s.matches("-?\\d+-\\d{1,2}-\\d{1,2}")) {
            boolean neg = s.startsWith("-");
            String[] p = (neg ? s.substring(1) : s).split("-");
            int y = Integer.parseInt(p[0]) * (neg ? -1 : 1);
            return key(y, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        }
        return (int) Double.parseDouble(s);
    }
}
