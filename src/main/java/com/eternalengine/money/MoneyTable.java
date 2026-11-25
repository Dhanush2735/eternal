package com.eternalengine.money;

import com.eternalengine.civilization.RulerRegister;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step 19a: which money people used at a place in a year, what its coins were called, and what an ordinary
 * labourer's day of work paid in it. Read once from {@value #RESOURCE} (written by
 * worldfill/build_money_table.py, which also explains the file's rules).
 *
 * <p>Every place and every year has an answer: a row for the place, or a fallback for the year (barter before
 * coins, plain "silver and copper coins" where no row names the money, "local money" after 1800). Where people
 * swapped goods, the "money" is goods counted in days of work. The table's notes and its "estimate" marks are
 * for developers only; nothing here tells a player where a number came from.
 */
public final class MoneyTable {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/money_table.json";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Struck or printed money; metal weighed out (named units of weight); goods swapped (days of work); or a
     * fallback that names no coins (money counted in days of work).
     */
    public enum Kind { COINS, WEIGHED, BARTER, LOCAL }

    /** One coin, note or weight name, named only in the years {@code from..to}. */
    public record Unit(String name, String plural, long size, int from, int to) {
        public boolean usedIn(int year) {
            return year >= from && year <= to;
        }

        public String word(boolean one) {
            return one ? name : plural;
        }
    }

    /** A money: its names, its units (largest first) and a labourer's day's pay in it, by year. */
    public static final class Money {
        private final String id;
        private final String name;
        private final String called;
        private final String shortName;
        private final String metal;
        private final Kind kind;
        private final int decimals;
        private final List<Unit> units;
        private final int[] payYears;
        private final long[] pays;
        private final boolean estimate;

        Money(String id, String name, String called, String shortName, String metal, Kind kind, int decimals,
              List<Unit> units, int[] payYears, long[] pays, boolean estimate) {
            this.id = id;
            this.name = name;
            this.called = called == null || called.isEmpty() ? name : called;
            this.shortName = shortName;
            this.metal = metal;
            this.kind = kind;
            this.decimals = decimals;
            this.units = List.copyOf(units);
            this.payYears = payYears;
            this.pays = pays;
            this.estimate = estimate;
        }

        public String id() { return id; }
        /** What people call this money, as a label ("Osmania sicca", "denarius", "goods to trade"). */
        public String name() { return name; }
        /** The name in a sentence ("the Osmania sicca", "the denarius", "weighed silver"). */
        public String called() { return called; }
        /** A short name for "7 O.S. rupees", or empty. */
        public String shortName() { return shortName; }
        /** "silver" or "copper" for weighed money, else empty. */
        public String metal() { return metal; }
        public Kind kind() { return kind; }
        /** Decimals shown on the smallest named unit (weighed money); amounts then count tenths of it. */
        public int decimals() { return decimals; }
        public List<Unit> units() { return units; }
        /** True when the table marks this money's numbers as guessed (never shown to players). */
        public boolean estimate() { return estimate; }

        public boolean barter() {
            return kind == Kind.BARTER;
        }

        /** True when amounts show as days of work (barter, and the fallbacks that name no coins). */
        public boolean inDays() {
            return kind == Kind.BARTER || kind == Kind.LOCAL;
        }

        /** An ordinary labourer's pay for one day in this year, in this money's smallest unit (at least 1). */
        public long dayPay(int year) {
            return interpolate(payYears, pays, year);
        }

        /** The units named in this year, largest first; the smallest unit when none is. */
        public List<Unit> unitsIn(int year) {
            List<Unit> out = new ArrayList<>();
            for (Unit u : units) {
                if (u.usedIn(year)) {
                    out.add(u);
                }
            }
            if (out.isEmpty()) {
                out.add(units.get(units.size() - 1));
            }
            return out;
        }

        /** A unit by any of its names (case and accents ignored), whatever the year; null when none fits. */
        public Unit unitNamed(String word) {
            String w = MoneyFormat.fold(word);
            for (Unit u : units) {
                if (MoneyFormat.fold(u.name()).equals(w) || MoneyFormat.fold(u.plural()).equals(w)) {
                    return u;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** {@code aAmount} of money a is worth {@code bAmount} of money b (smallest units) in {@code from..to}. */
    public record Rate(String a, long aAmount, String b, long bAmount, int from, int to) {
        boolean covers(int year) {
            return year >= from && year <= to;
        }
    }

    /** Who ruled a point in a year: the ruler register's polity name, or null. */
    @FunctionalInterface
    public interface Rulers {
        String at(double lat, double lon, int year);
    }

    /**
     * The last year the ruler register knows (its record stops in 2024). Step 23a: later years ask the register about
     * this year, so that 2025 and 2026 keep the rulers - and the money - of 2024 (a change after it, like Bulgaria's
     * euro of 2026, is a row of its own).
     */
    public static final int LAST_RULER_YEAR = 2024;

    /** The ruler register of the game. Any failure there is "nobody known". */
    public static final Rulers REGISTER = (lat, lon, year) -> {
        try {
            // step 26 (A1): through the one key, so a timeline that changes who rules changes the money too
            return com.eternalengine.core.WorldQuery.ruler(
                            com.eternalengine.core.WorldKey.at(lat, lon, Math.min(year, LAST_RULER_YEAR)))
                    .map(RulerRegister.Hit::ruler).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    };

    /** One way a row can match a place: every condition it holds must be true. */
    public record Where(int rulerInYear, Set<String> rulerIn, Set<String> rulerNow, double[] box, boolean world) {
        boolean test(double lat, double lon, int year, Rulers rulers, Map<Integer, String> memo) {
            boolean placed = !Double.isNaN(lat) && !Double.isNaN(lon);
            if (!placed) {
                return world && box == null && rulerNow == null && rulerIn == null;
            }
            if (box != null && !(lat >= box[0] && lat <= box[2] && lon >= box[1] && lon <= box[3])) {
                return false;
            }
            if (rulerNow != null && !rulerNow.contains(ruler(lat, lon, year, rulers, memo))) {
                return false;
            }
            if (rulerIn != null && !rulerIn.contains(ruler(lat, lon, rulerInYear, rulers, memo))) {
                return false;
            }
            return world || box != null || rulerNow != null || rulerIn != null;
        }

        private static String ruler(double lat, double lon, int year, Rulers rulers, Map<Integer, String> memo) {
            if (memo.containsKey(year)) {
                return memo.get(year);
            }
            String r = rulers == null ? null : rulers.at(lat, lon, year);
            memo.put(year, r);
            return r;
        }
    }

    /**
     * A day's pay read in straight lines between [year, amount] anchors, held flat beyond the first and the last;
     * at least 1. Shared by the monies and by rows with a pay of their own.
     */
    static long interpolate(int[] payYears, long[] pays, int year) {
        int n = payYears.length;
        if (n == 0) {
            return 1L;
        }
        if (year <= payYears[0]) {
            return Math.max(1L, pays[0]);
        }
        if (year >= payYears[n - 1]) {
            return Math.max(1L, pays[n - 1]);
        }
        for (int i = 1; i < n; i++) {
            if (year <= payYears[i]) {
                double t = (year - payYears[i - 1]) / (double) (payYears[i] - payYears[i - 1]);
                return Math.max(1L, Math.round(pays[i - 1] + t * (pays[i] - pays[i - 1])));
            }
        }
        return Math.max(1L, pays[n - 1]);
    }

    /** A row's own day's pay (step 23a), for a money shared by places of very different wages. */
    public record Pay(int[] years, long[] amounts) {
        public long at(int year) {
            return interpolate(years, amounts, year);
        }
    }

    /**
     * A row of the table: this money, in these years, at these places. A row may have a day's pay of its own (step
     * 23a): the euro in Sofia, the US dollar in Quito or sterling in Colombo pay what people earned there, not what
     * the money pays where it is at home.
     */
    public record Row(String id, Money money, int from, int to, List<Where> where, List<Money> accepts, int priority,
                      Pay pay) {
        public Row(String id, Money money, int from, int to, List<Where> where, List<Money> accepts, int priority) {
            this(id, money, from, to, where, accepts, priority, null);
        }

        /** A labourer's day's pay under this row in a year: the row's own pay, else the money's. */
        public long dayPay(int year) {
            return pay == null ? money.dayPay(year) : pay.at(year);
        }
    }

    /** The money of one place in one year: the row that won, its money, and the other money taken there. */
    public record Here(Row row, Money money, List<Money> accepts, int year, double lat, double lon) {
        public boolean placeKnown() {
            return !Double.isNaN(lat) && !Double.isNaN(lon);
        }

        public long dayPay() {
            return row.dayPay(year);
        }

        /** True when sellers here take this money: the money of the place, or one also taken. */
        public boolean takes(String moneyId) {
            if (money.id().equals(moneyId)) {
                return true;
            }
            for (Money m : accepts) {
                if (m.id().equals(moneyId)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record Summary(int monies, int rows, int rates, boolean loaded, String source) {
    }

    private static volatile MoneyTable instance;

    private final Map<String, Money> monies;
    private final List<Row> rows;
    private final List<Rate> rates;
    private final List<String> problems;
    private final String source;
    private final Money barter;

    private MoneyTable(Map<String, Money> monies, List<Row> rows, List<Rate> rates, List<String> problems,
                       String source) {
        this.monies = monies;
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt((Row r) -> r.priority()).reversed());
        this.rows = List.copyOf(sorted);
        this.rates = List.copyOf(rates);
        this.problems = List.copyOf(problems);
        this.source = source;
        Money b = monies.get("barter");
        this.barter = b != null ? b : new Money("barter", "goods to trade", "goods to trade", "", "", Kind.BARTER, 0,
                List.of(new Unit("day of work", "days of work", 100, Integer.MIN_VALUE, Integer.MAX_VALUE)),
                new int[] {0}, new long[] {100}, false);
    }

    /** The game's table, read from the mod jar the first time it is asked for. */
    public static MoneyTable get() {
        MoneyTable t = instance;
        if (t == null) {
            synchronized (MoneyTable.class) {
                t = instance;
                if (t == null) {
                    t = loadResource();
                    instance = t;
                }
            }
        }
        return t;
    }

    /** Read the table again (/ee reload). */
    public static MoneyTable reload() {
        synchronized (MoneyTable.class) {
            instance = loadResource();
            return instance;
        }
    }

    private static MoneyTable loadResource() {
        try (InputStream in = MoneyTable.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.warn("[Money] {} is missing from the mod jar: everyone barters", RESOURCE);
                return empty("missing " + RESOURCE);
            }
            MoneyTable t = read(new InputStreamReader(in, StandardCharsets.UTF_8), RESOURCE);
            LOGGER.info("[Money] money table: {} kinds of money, {} rows, {} rates{}", t.monies.size(), t.rows.size(),
                    t.rates.size(), t.problems.isEmpty() ? "" : "; problems: " + String.join("; ", t.problems));
            return t;
        } catch (Exception e) {
            LOGGER.warn("[Money] could not read {}: {}", RESOURCE, e.toString());
            return empty(RESOURCE + " failed: " + e);
        }
    }

    private static MoneyTable empty(String problem) {
        return new MoneyTable(new LinkedHashMap<>(), List.of(), List.of(), List.of(problem), "none");
    }

    /** Read a table (for the game and for checks). Problems are collected, not thrown. */
    public static MoneyTable read(Reader reader, String source) {
        List<String> problems = new ArrayList<>();
        Map<String, Money> monies = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        List<Rate> rates = new ArrayList<>();
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        for (JsonElement e : arr(root, "currencies")) {
            try {
                Money m = readMoney(e.getAsJsonObject(), problems);
                if (monies.putIfAbsent(m.id(), m) != null) {
                    problems.add("money " + m.id() + " twice");
                }
            } catch (RuntimeException ex) {
                problems.add("a money could not be read: " + ex.getMessage());
            }
        }
        Set<String> rowIds = new HashSet<>();
        for (JsonElement e : arr(root, "rows")) {
            try {
                JsonObject o = e.getAsJsonObject();
                String id = str(o, "id", "?");
                if (!rowIds.add(id)) {
                    problems.add("row " + id + " twice");
                }
                Money m = monies.get(str(o, "money", ""));
                if (m == null) {
                    problems.add("row " + id + ": no money " + str(o, "money", ""));
                    continue;
                }
                int from = o.has("from") ? o.get("from").getAsInt() : Integer.MIN_VALUE;
                int to = o.has("to") ? o.get("to").getAsInt() : Integer.MAX_VALUE;
                if (from > to) {
                    problems.add("row " + id + ": from " + from + " is after to " + to);
                }
                List<Where> where = new ArrayList<>();
                for (JsonElement w : arr(o, "where")) {
                    Where parsed = readWhere(w.getAsJsonObject(), id, problems);
                    if (parsed != null) {
                        where.add(parsed);
                    }
                }
                if (where.isEmpty()) {
                    problems.add("row " + id + ": no place");
                }
                List<Money> accepts = new ArrayList<>();
                for (JsonElement a : arr(o, "accepts")) {
                    Money am = monies.get(a.getAsString());
                    if (am == null) {
                        problems.add("row " + id + ": no money " + a.getAsString() + " to accept");
                    } else {
                        accepts.add(am);
                    }
                }
                int priority = o.has("priority") ? o.get("priority").getAsInt() : 0;
                Pay pay = null;
                if (o.has("pay")) {
                    JsonArray pa = o.getAsJsonArray("pay");
                    int[] py = new int[pa.size()];
                    long[] pv = new long[pa.size()];
                    for (int i = 0; i < pa.size(); i++) {
                        JsonArray a = pa.get(i).getAsJsonArray();
                        py[i] = a.get(0).getAsInt();
                        pv[i] = a.get(1).getAsLong();
                        if (pv[i] <= 0 || (i > 0 && py[i] < py[i - 1])) {
                            problems.add("row " + id + ": its pay must be above 0, in year order");
                        }
                    }
                    pay = py.length == 0 ? null : new Pay(py, pv);
                }
                rows.add(new Row(id, m, from, to, List.copyOf(where), List.copyOf(accepts), priority, pay));
            } catch (RuntimeException ex) {
                problems.add("a row could not be read: " + ex.getMessage());
            }
        }
        for (JsonElement e : arr(root, "rates")) {
            try {
                JsonObject o = e.getAsJsonObject();
                Rate r = new Rate(str(o, "a", ""), o.get("aAmount").getAsLong(), str(o, "b", ""),
                        o.get("bAmount").getAsLong(), o.has("from") ? o.get("from").getAsInt() : Integer.MIN_VALUE,
                        o.has("to") ? o.get("to").getAsInt() : Integer.MAX_VALUE);
                if (!monies.containsKey(r.a()) || !monies.containsKey(r.b())) {
                    problems.add("rate " + r.a() + "/" + r.b() + ": unknown money");
                } else if (r.aAmount() <= 0 || r.bAmount() <= 0) {
                    problems.add("rate " + r.a() + "/" + r.b() + ": amounts must be above 0");
                } else {
                    rates.add(r);
                }
            } catch (RuntimeException ex) {
                problems.add("a rate could not be read: " + ex.getMessage());
            }
        }
        if (!monies.containsKey("barter")) {
            problems.add("no barter money");
        }
        return new MoneyTable(monies, rows, rates, problems, source);
    }

    private static Money readMoney(JsonObject o, List<String> problems) {
        String id = str(o, "id", "");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("a money without an id");
        }
        Kind kind;
        try {
            kind = Kind.valueOf(str(o, "kind", "coins").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add("money " + id + ": kind " + str(o, "kind", "") + " is not coins, weighed, barter or local");
            kind = Kind.COINS;
        }
        int decimals = o.has("decimals") ? o.get("decimals").getAsInt() : 0;
        if (decimals < 0 || decimals > 3) {
            problems.add("money " + id + ": decimals " + decimals);
            decimals = 0;
        }
        List<Unit> units = new ArrayList<>();
        for (JsonElement e : arr(o, "units")) {
            JsonObject u = e.getAsJsonObject();
            units.add(new Unit(str(u, "name", "?"), str(u, "plural", str(u, "name", "?")), u.get("size").getAsLong(),
                    u.has("from") ? u.get("from").getAsInt() : Integer.MIN_VALUE,
                    u.has("to") ? u.get("to").getAsInt() : Integer.MAX_VALUE));
        }
        if (units.isEmpty()) {
            problems.add("money " + id + ": no units");
            units.add(new Unit("piece", "pieces", 1, Integer.MIN_VALUE, Integer.MAX_VALUE));
        }
        for (int i = 1; i < units.size(); i++) {
            if (units.get(i).size() > units.get(i - 1).size()) {
                problems.add("money " + id + ": units are not largest first");
                break;
            }
        }
        long smallest = units.get(units.size() - 1).size();
        long expected = kind == Kind.BARTER || kind == Kind.LOCAL ? smallest : (long) Math.pow(10, decimals);
        if (smallest != expected || smallest <= 0) {
            problems.add("money " + id + ": the smallest unit has size " + smallest + ", not " + expected);
        }
        JsonArray pay = arr(o, "pay");
        int[] years = new int[pay.size()];
        long[] pays = new long[pay.size()];
        for (int i = 0; i < pay.size(); i++) {
            JsonArray p = pay.get(i).getAsJsonArray();
            years[i] = p.get(0).getAsInt();
            pays[i] = p.get(1).getAsLong();
            if (pays[i] <= 0) {
                problems.add("money " + id + ": a day's pay of " + pays[i] + " in " + years[i]);
            }
            if (i > 0 && years[i] <= years[i - 1]) {
                problems.add("money " + id + ": pay years are not in order");
            }
        }
        if (pay.isEmpty()) {
            problems.add("money " + id + ": no day's pay");
        }
        return new Money(id, str(o, "name", id), str(o, "called", ""), str(o, "short", ""), str(o, "metal", ""), kind,
                decimals, units, years, pays, o.has("estimate") && o.get("estimate").getAsBoolean());
    }

    private static Where readWhere(JsonObject o, String rowId, List<String> problems) {
        double[] box = null;
        if (o.has("box")) {
            JsonArray b = o.getAsJsonArray("box");
            if (b.size() != 4) {
                problems.add("row " + rowId + ": a box needs latMin, lonMin, latMax, lonMax");
                return null;
            }
            box = new double[] {b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                    b.get(3).getAsDouble()};
            if (box[0] > box[2] || box[1] > box[3] || box[0] < -90 || box[2] > 90 || box[1] < -180 || box[3] > 180) {
                problems.add("row " + rowId + ": a box is not a box");
            }
        }
        Set<String> now = o.has("ruler") ? names(o.getAsJsonArray("ruler")) : null;
        Set<String> in = null;
        int inYear = 0;
        if (o.has("rulerIn")) {
            inYear = o.get("rulerIn").getAsInt();
            in = names(arr(o, "names"));
        }
        boolean world = o.has("world") && o.get("world").getAsBoolean();
        if (box == null && now == null && in == null && !world) {
            problems.add("row " + rowId + ": a place without any condition");
            return null;
        }
        return new Where(inYear, in, now, box, world);
    }

    private static Set<String> names(JsonArray a) {
        Set<String> s = new HashSet<>();
        for (JsonElement e : a) {
            s.add(e.getAsString());
        }
        return s;
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }

    // ---------------------------------------------------------------------------------------------------------

    /** The money at this point in this year, by the game's ruler register. Never null. */
    public Here at(double lat, double lon, int year) {
        return at(lat, lon, year, REGISTER);
    }

    /** The money at this point in this year; NaN for an unknown place (then only world rows match). */
    public Here at(double lat, double lon, int year, Rulers rulers) {
        Map<Integer, String> memo = new HashMap<>();
        for (Row r : rows) {
            if (year < r.from() || year > r.to()) {
                continue;
            }
            for (Where w : r.where()) {
                if (w.test(lat, lon, year, rulers, memo)) {
                    return new Here(r, r.money(), r.accepts(), year, lat, lon);
                }
            }
        }
        Row fallback = new Row("none", barter, Integer.MIN_VALUE, Integer.MAX_VALUE, List.of(), List.of(), -1);
        return new Here(fallback, barter, List.of(), year, lat, lon);
    }

    public Money money(String id) {
        return monies.get(id);
    }

    public Map<String, Money> monies() {
        return Collections.unmodifiableMap(monies);
    }

    public List<Row> rows() {
        return rows;
    }

    public List<String> problems() {
        return problems;
    }

    public Summary summary() {
        return new Summary(monies.size(), rows.size(), rates.size(), !rows.isEmpty(), source);
    }

    /** The fixed rate between two monies in a year, as [amount of a, amount of b], or null. */
    public long[] rate(String a, String b, int year) {
        for (Rate r : rates) {
            if (!r.covers(year)) {
                continue;
            }
            if (r.a().equals(a) && r.b().equals(b)) {
                return new long[] {r.aAmount(), r.bAmount()};
            }
            if (r.a().equals(b) && r.b().equals(a)) {
                return new long[] {r.bAmount(), r.aAmount()};
            }
        }
        return null;
    }

    /**
     * What an amount of one money is worth in another in a year, rounded down: by a fixed rate when the table has
     * one (directly or through one money in between), otherwise by days of work (each money's day's pay that year).
     */
    public long convert(Money from, long amount, Money to, int year) {
        if (from == null || to == null) {
            return 0L;
        }
        if (from.id().equals(to.id())) {
            return amount;
        }
        long[] r = rate(from.id(), to.id(), year);
        if (r != null) {
            return (long) Math.floor((double) amount * r[1] / r[0]);
        }
        for (Money mid : monies.values()) {
            long[] r1 = rate(from.id(), mid.id(), year);
            long[] r2 = r1 == null ? null : rate(mid.id(), to.id(), year);
            if (r2 != null) {
                return (long) Math.floor((double) amount * r1[1] / r1[0] * r2[1] / r2[0]);
            }
        }
        double days = amount / (double) from.dayPay(year);
        return (long) Math.floor(days * to.dayPay(year));
    }

    /**
     * True when two monies have a fixed rate in a year, directly or through one money in between (the way
     * {@link #convert} finds one); false when they compare only by a day's pay.
     */
    public boolean fixed(String a, String b, int year) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b) || rate(a, b, year) != null) {
            return true;
        }
        for (Money mid : monies.values()) {
            if (rate(a, mid.id(), year) != null && rate(mid.id(), b, year) != null) {
                return true;
            }
        }
        return false;
    }

    /** True when some row of the table can name this money in this year (for checks). */
    public boolean usedIn(String moneyId, int year) {
        for (Row r : rows) {
            if (r.money().id().equals(moneyId) && year >= r.from() && year <= r.to()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The table's own check: the problems found while reading; Hyderabad in 1948 pays a day's work with 1 rupee of
     * Osmania sicca (a paper 1 anna, a map 8 annas) and takes Indian rupees at 7 to 6; and a grid of places over
     * the years from 3000 BCE to 2026 always finds money (or barter) with a day's pay. Null when all is well.
     *
     * @param rulers the ruler lookup to use (the game's register, or a stand-in for tests)
     */
    public String selfTest(Rulers rulers) {
        if (!problems.isEmpty()) {
            return problems.size() + " problem(s) in the table: " + String.join("; ",
                    problems.subList(0, Math.min(5, problems.size())));
        }
        Here hyd = at(17.3616, 78.4747, 1948, rulers);
        if (!hyd.money().id().equals("osmania_sicca")) {
            return "Hyderabad in 1948 pays in " + hyd.money().id() + " (row " + hyd.row().id()
                    + "), not Osmania sicca";
        }
        long day = hyd.dayPay();
        String dayText = MoneyFormat.amount(hyd.money(), day, 1948);
        String paper = MoneyFormat.amount(hyd.money(), day / 16, 1948);
        String map = MoneyFormat.amount(hyd.money(), day / 2, 1948);
        if (day != 192 || !dayText.equals("1 rupee") || !paper.equals("1 anna") || !map.equals("8 annas")) {
            return "Hyderabad in 1948: a day's pay is " + dayText + " (" + day + " pai), a paper " + paper + ", a map "
                    + map + "; expected 1 rupee, 1 anna, 8 annas";
        }
        Money inr = money("indian_rupee_anna");
        if (inr == null || !hyd.takes(inr.id()) || convert(hyd.money(), 7 * 192, inr, 1948) != 6 * 192) {
            return "Hyderabad in 1948 does not take Indian rupees at 7 O.S. = 6 Indian rupees";
        }
        int[] years = {-3000, -2000, -1200, -600, -300, 0, 100, 500, 800, 1000, 1200, 1400, 1550, 1700, 1800, 1858,
                1900, 1948, 1960, 2000, 2026};
        int checked = 0;
        for (int year : years) {
            for (int lat = -50; lat <= 70; lat += 10) {
                for (int lon = -170; lon <= 180; lon += 10) {
                    Here h = at(lat + 0.5, lon + 0.5, year, rulers);
                    if (h.money() == null || h.dayPay() <= 0) {
                        return "no money at " + lat + ", " + lon + " in " + year;
                    }
                    String text = MoneyFormat.amount(h.money(), h.dayPay(), year);
                    if (text.isBlank()) {
                        return "a day's pay at " + lat + ", " + lon + " in " + year + " has no words";
                    }
                    checked++;
                }
            }
            if (at(Double.NaN, Double.NaN, year, rulers).money() == null) {
                return "no money for an unknown place in " + year;
            }
        }
        return checked > 0 ? null : "nothing was checked";
    }
}
