package com.eternalengine.money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * Step 23b: the pieces of a money that people held in a year: coins, notes, cowries, cacao beans, cut silver. Each
 * piece has a value in the money's smallest unit, a look (the item that carries it) and a name ("1 anna", "5 rupees").
 * <p>
 * The pieces follow the money's own units. Each unit gets a run of 1, 2, 5, 10, 20, 50... of it (1, 2, 4, 8 where
 * the next unit is a power of two of it, as 16 annas to the rupee; 1, 2, 3, 6 where it is 12, as pence to the
 * shilling), never as big as the next unit and never above 20 days' pay (5 days' pay from 1914, when gold coins and
 * big notes left people's hands); each unit itself has a piece. Pieces worth less than a two-thousandth of a day's
 * pay are left out (a kobo or a naira in Lagos in 2026, a bolivar in Caracas in 2020), and so is a smallest piece that
 * does not go into every other piece (20 lipa next to 50): amounts are then rounded to the smallest piece, and every
 * amount of whole smallest pieces can be made. Weighed money also gets tenths of its smallest weight.
 * <p>
 * The look follows the value in days of work and the time. Before 1914: gold for named gold coins and, from 1500, for
 * a unit worth 6 days or more (and its multiples); silver for an eighth of a day or more, copper below (all pieces of
 * the smallest unit share its metal: 2, 3 and 6 folles stay copper); paper from 1800 for 20 days or more. From 1914
 * paper for more than two-thirds of a day (the rupee note, not the 8-anna coin), from 1980 for a twenty-fifth of a day
 * or more, with nickel and copper coins below.
 * Some monies have their own look: cowries, cacao beans, manillas, nzimbu shells, salt bars, homespun cloth, beaver
 * pelts, Siamese bullet coins, the holed cash of China, Japan, Korea and Vietnam (Song and Yuan paper money for a
 * string of cash and more), and weighed silver or copper (also units named "... of silver").
 * <p>
 * No Minecraft classes here, so the checks run without a game.
 */
public final class Denominations {

    /** What a piece looks like: each look is one item. */
    public enum Form {
        GOLD("coin_gold", "Gold coin", "A gold coin", true),
        SILVER("coin_silver", "Silver coin", "A silver coin", true),
        COPPER("coin_copper", "Copper coin", "A copper or bronze coin", true),
        HOLED("coin_holed", "Holed coin", "Cast copper cash with a square hole, strung on cords", true),
        NICKEL("coin_nickel", "Coin", "A coin of nickel or steel", true),
        BANKNOTE("banknote", "Banknote", "A paper note", true),
        COWRIE("cowrie", "Cowrie shells", "Cowrie shells, counted out", false),
        CACAO("cacao_bean", "Cacao beans", "Cacao beans, counted out", false),
        SILVER_WEIGHT("silver_weighed", "Silver by weight", "Silver cut and weighed on the scales", false),
        COPPER_WEIGHT("copper_weighed", "Copper by weight", "Copper weighed on the scales", false),
        MANILLA("manilla", "Manillas", "Bronze rings (manillas)", false),
        SHELL("shell_money", "Shell money", "Small nzimbu shells, counted out", false),
        SALT("salt_bar", "Salt bars", "Bars of rock salt (amole)", false),
        CLOTH("cloth_bolt", "Homespun cloth", "Homespun woollen cloth (vadmal), measured in ells", false),
        PELT("pelt", "Beaver pelts", "Beaver pelts, the money of the trading posts", false),
        BULLET("bullet_coin", "Bullet coin", "A bent silver bullet (pod duang)", true);

        /** The item id (eternalengine:...). */
        public final String itemId;
        /** The item's name when it carries no money (also the language file's name). */
        public final String title;
        /** One line for the tooltip. */
        public final String lore;
        /** True when the piece's name also names its money ("1 anna (Osmania sicca)"); shells and goods name themselves. */
        public final boolean namesMoney;

        Form(String itemId, String title, String lore, boolean namesMoney) {
            this.itemId = itemId;
            this.title = title;
            this.lore = lore;
            this.namesMoney = namesMoney;
        }
    }

    /** One piece: its value in the money's smallest unit, its look and its name. */
    public record Piece(long value, Form form, String label) {
    }

    /** Pieces worth less than this share of a day's pay are left out. */
    public static final long SMALLEST_SHARE = 2000L;
    /** No piece above this many days' pay before 1914 (each unit itself has one). */
    public static final long LARGEST_DAYS = 20L;
    /** No piece above this many days' pay from 1914. */
    public static final long LARGEST_DAYS_MODERN = 5L;

    /** Unit names of gold coins (folded), for the years before pieces are judged by value alone. */
    static final Set<String> GOLD_NAMES = Set.of("daric", "aureus", "solidus", "nomisma", "hyperpyron", "bezant",
            "mohur", "ducat", "florin", "fiorino", "zecchino", "sequin", "noble", "hun", "pagoda", "varaha", "koban",
            "ryo", "dinara", "suvarna", "mithqal", "guinea", "sovereign", "escudo", "doubloon", "pistole", "louis d'or",
            "napoleon", "eagle", "half eagle", "double eagle", "ashrafi", "tilla", "toman");

    /** Cache by money (the money objects of one table; a reloaded table makes new ones) and year. */
    private static final Map<MoneyTable.Money, Map<Integer, List<Piece>>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Denominations() {
    }

    /** The pieces of a money in a year, smallest first; none for barter and money counted in days of work. */
    public static List<Piece> of(MoneyTable.Money m, int year) {
        if (m == null || m.inDays()) {
            return List.of();
        }
        Map<Integer, List<Piece>> byYear;
        synchronized (CACHE) {
            byYear = CACHE.computeIfAbsent(m, k -> Collections.synchronizedMap(new java.util.HashMap<>()));
        }
        return byYear.computeIfAbsent(year, y -> Collections.unmodifiableList(compute(m, y)));
    }

    /** The value of the smallest piece (1 when the money has none). */
    public static long smallest(MoneyTable.Money m, int year) {
        List<Piece> p = of(m, year);
        return p.isEmpty() ? 1L : p.get(0).value();
    }

    /** The piece of exactly this value, or null. */
    public static Piece piece(MoneyTable.Money m, int year, long value) {
        for (Piece p : of(m, year)) {
            if (p.value() == value) {
                return p;
            }
        }
        return null;
    }

    /** The name a piece carries: "1 anna (Osmania sicca)"; shells, beans and weighed metal name themselves. */
    public static String itemName(MoneyTable.Money m, Piece p) {
        return p.form().namesMoney ? p.label() + " (" + m.name() + ")" : p.label();
    }

    static List<Piece> compute(MoneyTable.Money m, int year) {
        List<MoneyTable.Unit> units = m.unitsIn(year);
        long day = Math.max(1L, m.dayPay(year));
        long days = year >= 1914 ? LARGEST_DAYS_MODERN : LARGEST_DAYS;
        long cap = day > Long.MAX_VALUE / days ? Long.MAX_VALUE : days * day;
        long floor = day / SMALLEST_SHARE;
        TreeMap<Long, MoneyTable.Unit> values = new TreeMap<>();
        for (int i = 0; i < units.size(); i++) {
            MoneyTable.Unit u = units.get(i);
            long size = u.size();
            if (size <= 0) {
                continue;
            }
            long next = i == 0 ? 0L : units.get(i - 1).size();
            for (long k : multipliers(next > 0 && next % size == 0 ? next / size : 0L)) {
                if (k > 1 && size > Long.MAX_VALUE / k) {
                    break;
                }
                long v = k * size;
                if (next > 0 && v >= next) {
                    break;
                }
                if (k > 1 && v > cap) {
                    break;
                }
                if (v < floor) {
                    continue;
                }
                values.putIfAbsent(v, u);
            }
        }
        MoneyTable.Unit last = units.get(units.size() - 1);
        if (last.size() > 1) {
            // Weighed money counts tenths of its smallest weight: pieces of 1, 2, 5 tenths.
            for (long k : multipliers(last.size())) {
                if (k >= last.size()) {
                    break;
                }
                if (k >= floor) {
                    values.putIfAbsent(k, last);
                }
            }
        }
        if (values.isEmpty()) {
            values.put(units.get(0).size(), units.get(0)); // nothing worth having: the largest unit alone
        }
        // The smallest piece must go into every other piece, or some amounts cannot be made (60 lipa from 20s and 50s).
        while (values.size() > 1) {
            long s0 = values.firstKey();
            boolean divides = true;
            for (long v : values.keySet()) {
                if (v % s0 != 0) {
                    divides = false;
                    break;
                }
            }
            if (divides) {
                break;
            }
            values.remove(s0);
        }
        List<Piece> out = new ArrayList<>();
        for (Map.Entry<Long, MoneyTable.Unit> e : values.entrySet()) {
            long v = e.getKey();
            out.add(new Piece(v, form(m, e.getValue(), v, year, day, e.getValue() == last),
                    MoneyFormat.amount(m, v, year)));
        }
        return out;
    }

    /** The run of a unit: powers of two below a power-of-two step, 1-2-3-6 below 12, else 1-2-5-10... */
    static long[] multipliers(long ratio) {
        List<Long> out = new ArrayList<>();
        if (ratio >= 2 && Long.bitCount(ratio) == 1) {
            for (long k = 1; k < ratio; k *= 2) {
                out.add(k);
            }
        } else if (ratio == 6 || ratio == 12 || ratio == 24) {
            for (long k : new long[] {1, 2, 3, 6, 12}) {
                if (k < ratio) {
                    out.add(k);
                }
            }
        } else {
            for (long k = 1; k > 0 && k <= 1_000_000_000_000_000_000L / 10 * 5; k *= 10) {
                out.add(k);
                out.add(2 * k);
                out.add(5 * k);
                if (ratio > 0 && 5 * k >= ratio) {
                    break;
                }
            }
        }
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }

    /** The look of one piece of value {@code v} in the run of unit {@code u} ({@code smallest}: the smallest unit's run). */
    static Form form(MoneyTable.Money m, MoneyTable.Unit u, long v, int year, long day, boolean smallest) {
        String unit = MoneyFormat.fold(u.name());
        switch (m.id()) {
            case "cowrie", "cowrie_md", "cowrie_cn":
                return Form.COWRIE;
            case "cacao":
                return Form.CACAO;
            case "manilla":
                return Form.MANILLA;
            case "nzimbu":
                return Form.SHELL;
            case "amole":
                return Form.SALT;
            case "vadmal":
                return Form.CLOTH;
            case "made_beaver":
                return Form.PELT;
            case "baht_old":
                return unit.equals("bia") ? Form.COWRIE : unit.equals("att") ? Form.COPPER
                        : year < 1860 ? Form.BULLET : Form.SILVER;
            case "china_cash":
                if (unit.startsWith("tael")) {
                    return Form.SILVER_WEIGHT;
                }
                return year >= 1024 && year <= 1455 && v >= 1000 ? Form.BANKNOTE : Form.HOLED;
            case "mon", "mun_kr", "van_vn":
                return Form.HOLED;
            default:
                break;
        }
        if (m.kind() == MoneyTable.Kind.WEIGHED) {
            return "copper".equals(m.metal()) ? Form.COPPER_WEIGHT : Form.SILVER_WEIGHT;
        }
        if (unit.endsWith(" of silver")) {
            return Form.SILVER_WEIGHT;
        }
        if (unit.endsWith(" of copper")) {
            return Form.COPPER_WEIGHT;
        }
        if (year >= 1980) {
            return v * 25 >= day ? Form.BANKNOTE : v * 500 >= day ? Form.NICKEL : Form.COPPER;
        }
        if (year >= 1914) {
            return v * 3 > day * 2 ? Form.BANKNOTE : v * 20 >= day ? Form.NICKEL : Form.COPPER;
        }
        if (year >= 1800 && v >= LARGEST_DAYS * day) {
            return Form.BANKNOTE;
        }
        if (GOLD_NAMES.contains(unit) || unit.contains("gold") || (year >= 1500 && u.size() >= 6 * day)) {
            return Form.GOLD;
        }
        long basis = smallest ? u.size() : v;
        return basis * 8 >= day ? Form.SILVER : Form.COPPER;
    }

    // ------------------------------------------------------------------------------------- paying with pieces

    /** How to make an amount from pieces, largest first: value and count; what is left below the smallest is dropped. */
    public static List<long[]> split(List<Piece> pieces, long amount) {
        List<long[]> out = new ArrayList<>();
        long rest = Math.max(0L, amount);
        for (int i = pieces.size() - 1; i >= 0 && rest > 0; i--) {
            long v = pieces.get(i).value();
            long n = rest / v;
            if (n > 0) {
                out.add(new long[] {v, n});
                rest -= n * v;
            }
        }
        return out;
    }

    /** What {@link #split} really makes of an amount (rounded down to the smallest piece). */
    public static long splitValue(List<Piece> pieces, long amount) {
        long sum = 0L;
        for (long[] vn : split(pieces, amount)) {
            sum += vn[0] * vn[1];
        }
        return sum;
    }

    /** How many pieces {@link #split} hands over. */
    public static long splitCount(List<Piece> pieces, long amount) {
        long n = 0L;
        for (long[] vn : split(pieces, amount)) {
            n += vn[1];
        }
        return n;
    }

    /**
     * A payment from the pieces held: which pieces go (value to count), what they are worth, and the change that comes
     * back (as pieces of the money in that year, rounded down to the smallest piece).
     */
    public record Plan(TreeMap<Long, Long> taken, long paid, long change, List<long[]> changePieces) {
        public long given() {
            long sum = 0L;
            for (long[] vn : changePieces) {
                sum += vn[0] * vn[1];
            }
            return sum;
        }
    }

    /**
     * Pay {@code amount} from {@code held} (value to count): the exact sum when the pieces allow (largest first),
     * else the smallest piece that covers the rest, else more pieces, largest first; change comes back in
     * {@code pieces}. Null when all of it is too little.
     */
    public static Plan plan(Map<Long, Long> held, long amount, List<Piece> pieces) {
        if (amount <= 0) {
            return new Plan(new TreeMap<>(), 0L, 0L, List.of());
        }
        TreeMap<Long, Long> left = new TreeMap<>();
        long total = 0L;
        for (Map.Entry<Long, Long> e : held.entrySet()) {
            if (e.getKey() != null && e.getKey() > 0 && e.getValue() != null && e.getValue() > 0) {
                left.merge(e.getKey(), e.getValue(), Long::sum);
                long add = e.getKey() > Long.MAX_VALUE / e.getValue() ? Long.MAX_VALUE : e.getKey() * e.getValue();
                total = total > Long.MAX_VALUE - add ? Long.MAX_VALUE : total + add;
            }
        }
        if (total < amount) {
            return null;
        }
        TreeMap<Long, Long> taken = new TreeMap<>();
        long rest = amount;
        for (Long v : left.descendingKeySet()) {
            long n = Math.min(left.get(v), rest / v);
            if (n > 0) {
                taken.merge(v, n, Long::sum);
                rest -= n * v;
            }
        }
        if (rest > 0) {
            Long cover = null;
            for (Long v : left.keySet()) {
                if (v >= rest && left.get(v) - taken.getOrDefault(v, 0L) > 0) {
                    cover = v;
                    break;
                }
            }
            if (cover != null) {
                taken.merge(cover, 1L, Long::sum);
                rest -= cover;
            } else {
                for (Long v : left.descendingKeySet()) {
                    while (rest > 0 && left.get(v) - taken.getOrDefault(v, 0L) > 0) {
                        taken.merge(v, 1L, Long::sum);
                        rest -= v;
                    }
                }
            }
            // One piece that covers it all may need less change (a note alone, not small coins and the note).
            Long single = left.ceilingKey(amount);
            if (single != null && single - amount <= -rest) {
                taken = new TreeMap<>();
                taken.put(single, 1L);
                rest = amount - single;
            }
        }
        if (rest > 0) {
            return null;
        }
        long change = -rest;
        return new Plan(taken, amount + change, change, change > 0 ? split(pieces, change) : List.of());
    }

    // ------------------------------------------------------------------------------------------------- checks

    /** Counts for checks: monies with pieces, pieces in all, the most in one money and year, looks used. */
    public record Census(int monies, int withPieces, long pieces, int most, String mostAt, int looks, String missing) {
    }

    /**
     * Every money of the table in up to 3 years it was used (first, middle, last): how many have pieces (all but
     * barter and money counted in days should), how many pieces, and which looks appear.
     */
    public static Census census(MoneyTable table) {
        int monies = 0;
        int with = 0;
        long pieces = 0L;
        int most = 0;
        String mostAt = "";
        java.util.EnumSet<Form> looks = java.util.EnumSet.noneOf(Form.class);
        List<String> missing = new ArrayList<>();
        for (MoneyTable.Money m : table.monies().values()) {
            if (m.inDays()) {
                continue;
            }
            monies++;
            int[] span = span(table, m);
            boolean ok = true;
            for (int y : new int[] {span[0], span[0] + (span[1] - span[0]) / 2, span[1]}) {
                List<Piece> p = of(m, y);
                if (p.isEmpty()) {
                    ok = false;
                    continue;
                }
                pieces += p.size();
                if (p.size() > most) {
                    most = p.size();
                    mostAt = m.id() + " " + y;
                }
                for (Piece x : p) {
                    looks.add(x.form());
                }
            }
            if (ok) {
                with++;
            } else if (missing.size() < 4) {
                missing.add(m.id());
            }
        }
        return new Census(monies, with, pieces, most, mostAt, looks.size(), String.join(", ", missing));
    }

    /** The first and last year any row of the table names this money (the money's pay years when none does). */
    public static int[] span(MoneyTable table, MoneyTable.Money m) {
        int from = Integer.MAX_VALUE;
        int to = Integer.MIN_VALUE;
        for (MoneyTable.Row r : table.rows()) {
            if (r.money() == m || r.money().id().equals(m.id())) {
                from = Math.min(from, r.from());
                to = Math.max(to, r.to());
            }
        }
        if (from > to) {
            from = -3000;
            to = 2026;
        }
        return new int[] {Math.max(-3000, from), Math.min(2026, to)};
    }

    /** The year to strike pieces of a money in: this year when it is used now, else its nearest year of use. */
    public static int yearFor(MoneyTable table, MoneyTable.Money m, int now) {
        if (table.usedIn(m.id(), now)) {
            return now;
        }
        int best = now;
        int dist = Integer.MAX_VALUE;
        for (MoneyTable.Row r : table.rows()) {
            if (!r.money().id().equals(m.id())) {
                continue;
            }
            int y = now < r.from() ? r.from() : Math.min(now, r.to());
            int d = Math.abs(now - y);
            if (d < dist) {
                dist = d;
                best = y;
            }
        }
        return best;
    }

    /**
     * The pieces' own check: Hyderabad 1948 has pieces from 1 pai to 5 rupees (annas in 1, 2, 4, 8; pai in 1, 2, 3,
     * 6), a 4-anna piece is nickel and 5 rupees paper (in Bombay too the 8-anna piece is a coin); London 1800 has a copper farthing, threepence and sixpence, a
     * silver shilling and a gold pound; Lagos 2026 has no kobo and no 1 or 2 naira (the smallest piece is 5 naira, 6000
     * naira is two notes); Rome in 100 has a gold aureus and a silver denarius; 6 folles in Constantinople in 1000 are
     * copper; Calabar's manillas, Bangkok's bullet coins, Edo's holed mon and weighed silver have their own look and
     * barter has no pieces; paying 5 annas with a rupee gives back
     * 11 annas (8 + 2 + 1); 63 pai is paid exactly from 4 annas, 1 anna and 3 pai; 49 pai from one 4-anna piece is
     * refused; 40 pai from 3 annas and a 10-rupee note takes the note alone and gives 1880 pai back. Null when all
     * is well.
     */
    public static String selfTest(MoneyTable table) {
        MoneyTable.Money os = table.money("osmania_sicca");
        MoneyTable.Money lsd = table.money("pound_lsd");
        MoneyTable.Money naira = table.money("naira");
        MoneyTable.Money manilla = table.money("manilla");
        MoneyTable.Money baht = table.money("baht_old");
        MoneyTable.Money mon = table.money("mon");
        MoneyTable.Money silver = table.money("silver_weighed");
        MoneyTable.Money barter = table.money("barter");
        if (os == null || lsd == null || naira == null || manilla == null || baht == null || mon == null
                || silver == null || barter == null) {
            return "the money table lacks one of osmania_sicca, pound_lsd, naira, manilla, baht_old, mon,"
                    + " silver_weighed, barter";
        }
        List<Piece> hyd = of(os, 1948);
        String hv = values(hyd);
        if (!hv.equals("1 2 3 6 12 24 48 96 192 384 960")) {
            return "Hyderabad 1948 pieces are " + hv + " pai; expected 1 2 3 6 12 24 48 96 192 384 960";
        }
        Piece four = piece(os, 1948, 48);
        Piece five = piece(os, 1948, 960);
        if (four == null || four.form() != Form.NICKEL || !four.label().equals("4 annas") || five == null
                || five.form() != Form.BANKNOTE || !itemName(os, five).equals("5 rupees (Osmania sicca)")) {
            return "Hyderabad 1948: 4 annas is " + four + ", 5 rupees is " + five + "; expected a nickel \"4 annas\" and"
                    + " a note \"5 rupees (Osmania sicca)\"";
        }
        MoneyTable.Money inr = table.money("indian_rupee_anna");
        Piece eight = inr == null ? null : piece(inr, 1948, 96);
        if (eight == null || eight.form() != Form.NICKEL) {
            return "Bombay 1948: 8 annas is " + eight + "; expected a nickel coin (the rupee is the smallest note)";
        }
        Piece farthing = piece(lsd, 1800, 1);
        Piece sixpence = piece(lsd, 1800, 24);
        Piece shilling = piece(lsd, 1800, 48);
        Piece pound = piece(lsd, 1800, 960);
        if (farthing == null || farthing.form() != Form.COPPER || sixpence == null || piece(lsd, 1800, 12) == null
                || shilling == null || shilling.form() != Form.SILVER || pound == null || pound.form() != Form.GOLD) {
            return "London 1800 pieces: " + describe(of(lsd, 1800)) + "; expected a copper farthing, threepence,"
                    + " sixpence, a silver shilling and a gold pound";
        }
        long nairaSmall = smallest(naira, 2026);
        if (nairaSmall != 500) {
            return "Lagos 2026: the smallest piece is " + nairaSmall + " kobo; expected 500 (5 naira)";
        }
        MoneyTable.Here rome = table.at(41.89, 12.49, 100);
        List<Piece> rp = of(rome.money(), 100);
        boolean aureus = false;
        boolean denarius = false;
        for (Piece p : rp) {
            aureus |= p.form() == Form.GOLD && p.label().contains("aure");
            denarius |= p.form() == Form.SILVER && p.label().startsWith("1 denari");
        }
        if (!aureus || !denarius) {
            return "Rome 100 (" + rome.money().id() + "): " + describe(rp) + "; expected a gold aureus and a silver denarius";
        }
        MoneyTable.Money solidus = table.money("solidus");
        Piece sixFolles = solidus == null ? null : piece(solidus, 1000, 6);
        if (sixFolles == null || sixFolles.form() != Form.COPPER) {
            return "Constantinople 1000: 6 folles are " + sixFolles + "; expected copper like the follis";
        }
        if (of(manilla, 1800).get(0).form() != Form.MANILLA || of(baht, 1800).get(of(baht, 1800).size() - 1).form()
                != Form.BULLET || of(mon, 1700).get(0).form() != Form.HOLED || of(silver, -500).get(0).form()
                != Form.SILVER_WEIGHT || !of(barter, 1000).isEmpty()) {
            return "own looks: manilla " + of(manilla, 1800).get(0).form() + ", tamlueng "
                    + of(baht, 1800).get(of(baht, 1800).size() - 1).form() + ", mon " + of(mon, 1700).get(0).form()
                    + ", shekel " + of(silver, -500).get(0).form() + ", barter " + of(barter, 1000).size() + " pieces";
        }
        Plan a = plan(Map.of(192L, 1L), 60, hyd);
        if (a == null || a.paid() != 192 || a.change() != 132 || !pairs(a.changePieces()).equals("96x1 24x1 12x1")) {
            return "paying 5 annas with a rupee: " + (a == null ? "refused" : a.paid() + " paid, change " + a.change()
                    + " as " + pairs(a.changePieces())) + "; expected 192 paid, 132 back as 8, 2 and 1 annas";
        }
        Plan b = plan(Map.of(48L, 2L, 12L, 3L, 1L, 5L), 63, hyd);
        if (b == null || b.change() != 0 || b.taken().getOrDefault(48L, 0L) != 1 || b.taken().getOrDefault(12L, 0L) != 1
                || b.taken().getOrDefault(1L, 0L) != 3) {
            return "paying 63 pai with 2 x 4 annas, 3 x 1 anna, 5 x 1 pai: " + (b == null ? "refused" : b.taken()
                    + " change " + b.change()) + "; expected 4 annas + 1 anna + 3 pai, no change";
        }
        Plan c = plan(Map.of(48L, 1L), 49, hyd);
        Plan d = plan(Map.of(12L, 3L, 1920L, 1L), 40, hyd);
        if (c != null || d == null || d.paid() != 1920 || d.given() != 1880) {
            return "49 pai from one 4-anna piece: " + (c == null ? "refused" : "paid") + "; 40 pai from 3 annas and a"
                    + " 10-rupee note: " + (d == null ? "refused" : d.paid() + " paid, " + d.given() + " back")
                    + "; expected refused, and the note paid with 1880 pai back";
        }
        List<Piece> lagos = of(naira, 2026);
        if (splitValue(lagos, 600_050L) != 600_000L || splitCount(lagos, 600_000L) != 2) {
            return "Lagos 2026: 6000 naira 50 kobo makes " + splitValue(lagos, 600_050L) + " kobo in "
                    + splitCount(lagos, 600_000L) + " piece(s) for 6000 naira; expected 600000 in 2 (5000 + 1000)";
        }
        return null;
    }

    static String values(List<Piece> ps) {
        StringBuilder sb = new StringBuilder();
        for (Piece p : ps) {
            sb.append(sb.length() == 0 ? "" : " ").append(p.value());
        }
        return sb.toString();
    }

    /** "1 pai copper, 2 pai copper, ..." for messages. */
    public static String describe(List<Piece> ps) {
        StringBuilder sb = new StringBuilder();
        for (Piece p : ps) {
            sb.append(sb.length() == 0 ? "" : ", ").append(p.label()).append(' ')
                    .append(p.form().name().toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }

    static String pairs(List<long[]> vn) {
        StringBuilder sb = new StringBuilder();
        for (long[] x : vn) {
            sb.append(sb.length() == 0 ? "" : " ").append(x[0]).append('x').append(x[1]);
        }
        return sb.toString();
    }
}
