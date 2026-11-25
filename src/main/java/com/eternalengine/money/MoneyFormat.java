package com.eternalengine.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Step 19a: money in words, the way people of the place and year would count it ("1 rupee 4 annas 6 pai",
 * "1 shilling 8 pence", "6 grains of silver", "goods worth about half a day's work"), and amounts typed by an
 * admin ("4 annas", "1.5 rupees", "2 days") back into the smallest unit.
 */
public final class MoneyFormat {

    /** The largest amount a purse or a command deals with, in smallest units. */
    public static final long MAX = 1_000_000_000_000_000L;

    private MoneyFormat() {
    }

    /** An amount of this money in this year, in words. */
    public static String amount(MoneyTable.Money m, long amount, int year) {
        if (m.barter()) {
            return goods(amount, m.dayPay(year));
        }
        if (m.inDays()) {
            return worth(m.name(), amount, m.dayPay(year));
        }
        String sign = amount < 0 ? "minus " : "";
        long rem = Math.abs(amount);
        List<MoneyTable.Unit> units = m.unitsIn(year);
        MoneyTable.Unit last = units.get(units.size() - 1);
        long base = (long) Math.pow(10, m.decimals());
        if (last.size() > base) {
            // The smallest named unit of the year is larger than what amounts count (sen after 1953): round to it.
            rem = Math.round(rem / (double) last.size()) * last.size();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < units.size(); i++) {
            MoneyTable.Unit u = units.get(i);
            boolean isLast = i == units.size() - 1;
            if (!isLast) {
                long n = rem / u.size();
                if (n > 0) {
                    append(sb, n + " " + u.word(n == 1));
                    rem -= n * u.size();
                }
                continue;
            }
            if (rem == 0 && sb.length() > 0) {
                break;
            }
            if (m.decimals() > 0 && u.size() > 1 && rem % u.size() != 0) {
                BigDecimal v = BigDecimal.valueOf(rem).divide(BigDecimal.valueOf(u.size()), m.decimals(),
                        RoundingMode.HALF_UP).stripTrailingZeros();
                append(sb, v.toPlainString() + " " + u.word(false));
            } else {
                long n = rem / u.size();
                append(sb, n + " " + u.word(n == 1));
            }
        }
        String text = sign + sb;
        return m.metal().isEmpty() ? text : text + " of " + m.metal();
    }

    /** Goods counted in days of work, to the nearest quarter day. */
    public static String goods(long amount, long dayPay) {
        return amount <= 0 ? "no goods to trade" : worth("goods", amount, dayPay);
    }

    /** Something counted in days of work, to the nearest quarter day: "local money worth about 1 day's work". */
    public static String worth(String what, long amount, long dayPay) {
        if (amount <= 0) {
            return "no " + what;
        }
        long quarters = Math.round(amount * 4.0d / Math.max(1L, dayPay));
        if (quarters == 0) {
            return what + " worth less than a quarter of a day's work";
        }
        return what + " worth about " + days(quarters);
    }

    /** A number of quarter days in words: "a quarter of a day's work", "half a day's work", "1½ days' work". */
    public static String days(long quarters) {
        if (quarters == 1) {
            return "a quarter of a day's work";
        }
        if (quarters == 2) {
            return "half a day's work";
        }
        if (quarters == 3) {
            return "three quarters of a day's work";
        }
        if (quarters == 4) {
            return "1 day's work";
        }
        long whole = quarters / 4;
        String part = switch ((int) (quarters % 4)) {
            case 1 -> "¼";
            case 2 -> "½";
            case 3 -> "¾";
            default -> "";
        };
        return whole + part + " days' work";
    }

    /** How long a labourer here works for this amount, in words ("about 1 day's work"); empty when under a quarter. */
    public static String workFor(long amount, long dayPay) {
        long quarters = Math.round(amount * 4.0d / Math.max(1L, dayPay));
        return quarters <= 0 ? "" : "about " + days(quarters);
    }

    /** How the units of a year go into each other: "1 rupee = 16 annas, 1 anna = 12 pai"; empty for one unit. */
    public static String units(MoneyTable.Money m, int year) {
        if (m.inDays()) {
            return "";
        }
        List<MoneyTable.Unit> units = m.unitsIn(year);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < units.size(); i++) {
            MoneyTable.Unit big = units.get(i);
            MoneyTable.Unit small = units.get(i + 1);
            String n;
            if (big.size() % small.size() == 0) {
                n = String.valueOf(big.size() / small.size());
            } else {
                n = BigDecimal.valueOf(big.size()).divide(BigDecimal.valueOf(small.size()), 2, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString();
            }
            list(sb, "1 " + big.name() + " = " + n + " " + small.word(n.equals("1")));
        }
        return sb.toString();
    }

    /**
     * An amount typed by an admin, in smallest units: a number (a sign and decimals allowed) and a unit of this
     * money ("anna", "annas", any of its names, accents ignored), "day" or "days" (a labourer's day's pay this
     * year), or nothing (the smallest unit named this year; a day for barter).
     *
     * @throws IllegalArgumentException with a plain-words reason
     */
    public static long parse(MoneyTable.Money m, String number, String unitWord, int year) {
        BigDecimal n;
        try {
            n = new BigDecimal(number.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("\"" + number + "\" is not a number");
        }
        long size;
        String w = unitWord == null ? "" : unitWord.trim();
        if (w.isEmpty()) {
            size = m.inDays() ? m.dayPay(year) : m.unitsIn(year).get(m.unitsIn(year).size() - 1).size();
        } else if (fold(w).equals("day") || fold(w).equals("days")) {
            size = m.dayPay(year);
        } else {
            MoneyTable.Unit u = m.unitNamed(w);
            if (u == null) {
                throw new IllegalArgumentException("\"" + unitWord + "\" is not a unit of " + m.name() + " (try "
                        + unitList(m) + ", or days)");
            }
            size = u.size();
        }
        BigDecimal v = n.multiply(BigDecimal.valueOf(size)).setScale(0, RoundingMode.HALF_UP);
        if (v.abs().compareTo(BigDecimal.valueOf(MAX)) > 0) {
            throw new IllegalArgumentException("that is too much money");
        }
        return v.longValue();
    }

    /** The unit names of a money, for messages: "rupees, annas, pai". */
    public static String unitList(MoneyTable.Money m) {
        StringBuilder sb = new StringBuilder();
        for (MoneyTable.Unit u : m.units()) {
            list(sb, u.plural());
        }
        return sb.toString();
    }

    /** Lower case without accents, for matching typed unit names ("Kurus" finds "kuruş"). */
    public static String fold(String s) {
        if (s == null) {
            return "";
        }
        String d = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return d.toLowerCase(Locale.ROOT);
    }

    /** Parts of one amount, joined by spaces: "1 rupee 4 annas". */
    private static void append(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(part);
    }

    /** Items of a list, joined by commas: "1 rupee = 16 annas, 1 anna = 12 pai". */
    private static void list(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part);
    }
}
