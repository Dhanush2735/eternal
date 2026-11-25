package com.eternalengine.clock;

import java.util.Locale;

/**
 * <b>Naming a span of years the way people actually name it</b> - "the 1940s", "the 20th century",
 * "the 2nd millennium", "the 30th century BC". 2026-10-27.
 *
 * <h2>Why this cannot be done by dividing by 100</h2>
 *
 * <p>Cross-checked against Wikipedia's <i>List of decades, centuries and millennia</i> (which indexes every
 * decade, century and millennium from 15,000 BC to AD 3000). That list encodes four traps that a naive
 * {@code year / 100 + 1} gets wrong:
 *
 * <ol>
 *   <li><b>There is no year 0.</b> The 1st century BC is 100 BC - 1 BC and the 1st century AD is AD 1 - 100.
 *       So the boundaries run 1, 101, 201 ... on both sides of the era divide.</li>
 *   <li><b>The 3rd millennium is AD 2001 - 3000</b>, not 2000 - 2999. The year 2000 is the 20th century and the
 *       2nd millennium. Celebrating the millennium in 2000 was a year early.</li>
 *   <li><b>BC spans run backwards.</b> The 18th century BC is 1800 BC - 1701 BC, and the decade "1790s BC" is
 *       1799 BC - 1790 BC. The <i>name</i> sorts ascending; the <i>years</i> do not.</li>
 *   <li><b>"1900s" is ambiguous</b> - it is both the decade 1900-1909 and the century 1900-1999. The Wikipedia
 *       index disambiguates the page title as "1900s (decade)". This class refuses to guess: it names the decade
 *       and flags the ambiguity, so no line in the game silently means the wrong hundred years.</li>
 * </ol>
 *
 * <p>Everything here is arithmetic on the year. It makes no claim about what happened in the span - that is the
 * history corpus's job - and it never invents a name for a span outside 15,000 BC - AD 3000, the range the
 * source list covers.
 */
public final class SpanName {

    /** Widest span the source list indexes. Outside this, no name is offered rather than a guessed one. */
    public static final int OLDEST_NAMED_YEAR = -15000;   // 15,000 BC
    public static final int NEWEST_NAMED_YEAR = 3000;     // AD 3000

    private SpanName() {
    }

    /** A named span: what it is called, and the years it actually covers, inclusive. */
    public record Span(String name, int firstYear, int lastYear, boolean ambiguous) {
        /** True when both ends are BC. */
        public boolean bc() {
            return lastYear < 1;
        }

        /** The span in words, e.g. {@code "the 1940s (AD 1940 - 1949)"}. */
        public String inWords() {
            return "the " + name + " (" + years() + ")";
        }

        /** The years in words, era-correct, e.g. {@code "AD 1940 - 1949"} or {@code "1799 BC - 1790 BC"}. */
        public String years() {
            if (bc()) {
                return Math.abs(firstYear) + " BC - " + Math.abs(lastYear) + " BC";
            }
            if (firstYear < 1) {
                return Math.abs(firstYear) + " BC - AD " + lastYear;
            }
            return "AD " + firstYear + " - " + lastYear;
        }
    }

    // ==================== millennia ====================

    /**
     * The millennium containing {@code year}, numbered as the source list numbers it.
     *
     * <p>The 3rd millennium is AD 2001 - 3000; the 3rd millennium BC is 3000 BC - 2001 BC.
     */
    public static Span millennium(int year) {
        if (!named(year)) {
            return null;
        }
        if (year < 1) {
            // BC years count backwards: 3000 BC starts the 3rd millennium BC.
            int n = ((-year - 1) / 1000) + 1;          // 3000 BC -> 3 ; 2000 BC -> 2
            int first = -(n * 1000);                   // 3000 BC -> -3000
            int last = -(((n - 1) * 1000) + 1);        // -> -2001
            return new Span(ordinal(n) + " millennium BC", first, last, false);
        }
        int n = ((year - 1) / 1000) + 1;               // 2001 -> 3 ; 2000 -> 2
        return new Span(ordinal(n) + " millennium", (n - 1) * 1000 + 1, n * 1000, false);
    }

    // ==================== centuries ====================

    /**
     * The century containing {@code year}.
     *
     * <p>AD 2000 is the 20th century. AD 2001 is the 21st. There is no year 0, so the 1st century AD is AD 1-100
     * and the 1st century BC is 100 BC - 1 BC.
     */
    public static Span century(int year) {
        if (!named(year)) {
            return null;
        }
        if (year < 1) {
            int n = ((-year - 1) / 100) + 1;           // 3000 BC -> 30 ; 1 BC -> 1
            int first = -(n * 100);                    // 3000 BC -> -3000
            int last = -(((n - 1) * 100) + 1);         // -> -2901
            return new Span(ordinal(n) + " century BC", first, last, false);
        }
        int n = ((year - 1) / 100) + 1;                // 2001 -> 21 ; 2000 -> 20
        boolean ambiguous = n >= 20;                   // "1900s"/"2000s" can also mean a decade
        return new Span(ordinal(n) + " century", (n - 1) * 100 + 1, n * 100, ambiguous);
    }

    // ==================== decades ====================

    /**
     * The decade containing {@code year}.
     *
     * <p>AD: 1948 -> 1940s, covering 1940 - 1949. BC: 1795 BC -> 1790s BC, covering <b>1799 BC - 1790 BC</b>,
     * because BC years run backwards. The 0s decade of a BC century is the one ending in the century year:
     * 1800 BC - 1791 BC is "the 1800s BC".
     */
    public static Span decade(int year) {
        if (!named(year)) {
            return null;
        }
        if (year < 1) {
            // BC decades follow the source's own decade articles: "the 1790s BC lasted from 1799 BC to
            // 1790 BC". The decade is named for the year it ENDS on, and in BC that is the smaller number.
            //
            // This has a consequence the source itself lives with, and it is worth naming plainly: because
            // 1800 BC is the first year of the 18th century BC and belongs to a decade running 1809 - 1800 BC,
            // a BC century holds only nine whole decades plus two half ones. That is not a defect here; it is
            // what the naming convention does. The probe states it rather than hiding it.
            int bc = -year;                                  // 1795
            int top = (bc / 10) * 10;                        // 1790
            if (top == 0) {
                // There is no year zero: the "0s BC" is 9 BC - 1 BC, nine years long.
                return new Span("0s BC", -9, -1, false);
            }
            return new Span(top + "s BC", -(top + 9), -top, false);
        }
        int top = (year / 10) * 10;                          // 1940
        boolean ambiguous = top % 100 == 0;                  // "1900s" - also a century
        return new Span(top + "s", top, top + 9, ambiguous);
    }

    /** Any span, chosen by the caller: {@code "year"}, {@code "decade"}, {@code "century"}, {@code "millennium"}. */
    public static Span of(int year, String size) {
        String s = size == null ? "year" : size.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "millennium" -> millennium(year);
            case "century" -> century(year);
            case "decade" -> decade(year);
            case "year" -> named(year) ? new Span(yearName(year), year, year, false) : null;
            default -> null;
        };
    }

    /** The year itself in era-correct words: {@code "AD 1948"}, {@code "3000 BC"}. */
    public static String yearName(int year) {
        return year < 1 ? Math.abs(year) + " BC" : "AD " + year;
    }

    /** Whether a name exists for this year. Outside the source list's range, none is invented. */
    public static boolean named(int year) {
        return year >= OLDEST_NAMED_YEAR && year <= NEWEST_NAMED_YEAR && year != 0;
    }

    /** 1 -> "1st", 2 -> "2nd", 11 -> "11th", 21 -> "21st", 30 -> "30th". */
    public static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }
}
