package com.eternalengine.money;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 24: the rules of Minecraft's trade screen with a townsperson, with no world (so the checks can run them).
 *
 * <p>The screen has two slots to pay with and one to take from, as with a villager. What lies in the two slots is
 * turned into {@link Lump}s; {@link #judge} says for the picked trade whether it goes through, what is taken from each
 * slot, what comes back (change, the price of your goods, changed money) and what the trader says about it. The trade
 * menu ({@link TradeDesk}) shows that and, when the player takes the thing, does it.
 *
 * <ul>
 *   <li><b>Buy</b>: any pieces of money taken here pay, in any mix of two kinds: the money of the place, money also
 *       taken here at its fixed rate, or the small change taken beside it by what it buys (cowries beside manillas);
 *       change comes back in pieces. Old money and money from far away are refused with the reason.</li>
 *   <li><b>Sell</b>: your goods for his money, a lot at a time (full price for his first few of a good each day, half
 *       for more, never past his day's money).</li>
 *   <li><b>Change</b>: as much of one money as you put in, for another (he keeps his cut); <b>bigger</b> and
 *       <b>smaller</b> pieces of one money, for nothing.</li>
 *   <li><b>Swap</b> (where people have no money): your goods for his, at what he pays for yours; a trader also takes
 *       coins, as metal, by what they buy. What you give too much he owes you (a written tab), and the tab pays.</li>
 * </ul>
 */
public final class TradeRules {

    /** Small change is used for prices below this many of the place's smallest pieces. */
    public static final int SMALL_BELOW = 4;

    private static final Map<String, Double> RATES = new ConcurrentHashMap<>();

    private TradeRules() {
    }

    /**
     * One stack in a slot: pieces of money (money id, value of one piece), goods (good id, what one is worth to this
     * trader in the trade's money), or anything else; {@code name} is the item's name for the words.
     */
    public record Lump(int slot, String money, long value, String good, double worth, long count, String name) {
        public static Lump money(int slot, String money, long value, long count) {
            return new Lump(slot, money, value, "", 0.0d, count, "");
        }

        public static Lump goods(int slot, String good, double worth, long count, String name) {
            return new Lump(slot, "", 0L, good, worth, count, name);
        }

        public static Lump other(int slot, long count, String name) {
            return new Lump(slot, "", 0L, "", 0.0d, count, name);
        }

        public boolean isMoney() {
            return money != null && !money.isEmpty();
        }

        public boolean isGoods() {
            return good != null && !good.isEmpty();
        }
    }

    /** One trade as the rules see it (no items). */
    public static final class Deal {
        public final String id;
        public final TradeView.Kind kind;
        /** Buy, swap: the price's money; sell: the money he pays in; change, bigger, smaller: the money you get. */
        public MoneyTable.Money money;
        /** Buy, swap: the price; sell: what he pays for the lot. */
        public long amount;
        /** Change, bigger, smaller: the money you put in. */
        public MoneyTable.Money from;
        /** Sell: the good and how many make the lot ("16 sheaves of wheat"). */
        public String good = "";
        public int count;
        public String goodWords = "";
        public String name = "";
        public String lore = "";
        public String priceText = "";
        public int left = -1;
        public boolean owned;
        /** Why he cannot do this trade now (he has enough wheat for today, too little to pay for), or "". */
        public String refusal = "";

        public Deal(String id, TradeView.Kind kind) {
            this.id = id;
            this.kind = kind;
        }
    }

    /**
     * Where the trade happens: the table, the money of the place, the trader's terms, whether he takes coins as metal
     * (a trader where people swap), the player's written money by money id (barter tabs, money counted in days), and
     * the pieces in the player's inventory (money id to value to count; for paying out of the purse).
     */
    public record Ctx(MoneyTable table, MoneyTable.Here here, MarketService.Terms terms, boolean coinsAsMetal,
                      Map<String, Long> written, Map<String, ? extends Map<Long, Long>> held) {
        public Ctx(MoneyTable table, MoneyTable.Here here, MarketService.Terms terms, boolean coinsAsMetal,
                   Map<String, Long> written) {
            this(table, here, terms, coinsAsMetal, written, Map.of());
        }

        long tab(MoneyTable.Money m) {
            Long t = written == null || m == null ? null : written.get(m.id());
            return t == null ? 0L : Math.max(0L, t);
        }
    }

    /**
     * What the slots do for a trade: {@code ok} when it goes through; how many to take from slot 0 and slot 1; the
     * pieces handed back (largest first, value and count) in {@code giveMoney}; the change of the written tab (+ he owes
     * you more, - it paid); the words; and {@code purse}: the slots are empty and the price needs more kinds of pieces
     * than two slots hold, so he counts it out of the purse (the inventory) and gives change there.
     */
    public record Outcome(boolean ok, long take0, long take1, @Nullable MoneyTable.Money giveMoney, List<long[]> give,
                          long tab, String words, boolean purse) {
        public Outcome(boolean ok, long take0, long take1, @Nullable MoneyTable.Money giveMoney, List<long[]> give,
                       long tab, String words) {
            this(ok, take0, take1, giveMoney, give, tab, words, false);
        }

        public long take(int slot) {
            return slot == 0 ? take0 : slot == 1 ? take1 : 0L;
        }

        public long given() {
            long sum = 0L;
            for (long[] vn : give) {
                sum += vn[0] * vn[1];
            }
            return sum;
        }

        public long givenPieces() {
            long sum = 0L;
            for (long[] vn : give) {
                sum += vn[1];
            }
            return sum;
        }
    }

    static Outcome no(String words) {
        return new Outcome(false, 0L, 0L, null, List.of(), 0L, words);
    }

    // --------------------------------------------------------------------------------------------- money

    /**
     * What one smallest unit of {@code a} is worth in smallest units of {@code b} in a year: by the table's fixed rate
     * when it has one, otherwise by days of work (each money's day's pay that year).
     */
    public static double rate(MoneyTable table, MoneyTable.Money a, MoneyTable.Money b, int year) {
        if (table == null || a == null || b == null) {
            return 0.0d;
        }
        if (a.id().equals(b.id())) {
            return 1.0d;
        }
        String key = System.identityHashCode(table) + ":" + a.id() + ">" + b.id() + "@" + year;
        return RATES.computeIfAbsent(key, k -> {
            long big = 1_000_000_000_000L;
            return table.convert(a, big, b, year) / (double) big;
        });
    }

    /** The pieces of a money in a year, or (old money) in its nearest year of use. */
    public static List<Denominations.Piece> pieces(MoneyTable table, MoneyTable.Money m, int year) {
        return Denominations.of(m, Denominations.yearFor(table, m, year));
    }

    /**
     * The small change taken beside the money of a place: a money also taken there whose smallest piece is worth at
     * most a quarter of the place's smallest piece (cowries beside manillas in Calabar), or null.
     */
    @Nullable
    public static MoneyTable.Money smallChange(MoneyTable table, MoneyTable.Here here) {
        MoneyTable.Money m = here.money();
        if (m == null || m.inDays()) {
            return null;
        }
        int year = here.year();
        List<Denominations.Piece> mp = Denominations.of(m, year);
        if (mp.isEmpty()) {
            return null;
        }
        long piece = mp.get(0).value();
        for (MoneyTable.Money a : here.accepts()) {
            if (a.id().equals(m.id()) || a.inDays()) {
                continue;
            }
            List<Denominations.Piece> ap = pieces(table, a, year);
            if (ap.isEmpty()) {
                continue;
            }
            double one = rate(table, a, m, year) * ap.get(0).value();
            if (one > 0 && one * SMALL_BELOW <= piece) {
                return a;
            }
        }
        return null;
    }

    /** A price and the money it is in. */
    public record Price(MoneyTable.Money money, long amount) {
    }

    /**
     * A seller's price in round money, from what it is worth in the smallest unit of the place's money: in the money
     * of the place, or (below {@link #SMALL_BELOW} of its smallest pieces) in the small change taken beside it: a
     * notebook in Calabar in 1800 costs cowries, not a whole manilla. {@code up}: round a part of a unit up (goods).
     */
    public static Price price(double raw, MoneyTable table, MoneyTable.Here here, boolean up) {
        MoneyTable.Money m = here.money();
        int year = here.year();
        MoneyTable.Money small = smallChange(table, here);
        if (small != null && raw < SMALL_BELOW * Denominations.smallest(m, year)) {
            double inSmall = raw * rate(table, m, small, year);
            long p = up ? (long) Math.ceil(inSmall - 1e-9) : Math.round(inSmall);
            return new Price(small, ShopService.nice(Math.max(1L, p), small, year));
        }
        long p = up ? (long) Math.ceil(raw - 1e-9) : Math.round(raw);
        return new Price(m, ShopService.nice(Math.max(1L, p), m, year));
    }

    /** True when sellers here take this money (the money of the place, or one also taken here). */
    static boolean taken(MoneyTable.Here here, String moneyId) {
        return here.takes(moneyId);
    }

    // --------------------------------------------------------------------------------------------- judging

    /** What the two slots do for a trade. */
    public static Outcome judge(Deal d, List<Lump> tray, Ctx c) {
        if (d == null) {
            return no("Pick a trade on the left.");
        }
        return switch (d.kind) {
            case BUY, SWAP -> buy(d, tray, c);
            case SELL -> sell(d, tray, c);
            case CHANGE -> change(d, tray, c);
            case MAKE, BREAK -> regroup(d, tray, c);
        };
    }

    /**
     * What one of a lump is worth in the trade's money: money taken here (at the fixed rate, or the small change by
     * what it buys), goods for a swap at what he pays, coins as metal for a trader where people swap; 0 when it does
     * not pay.
     */
    static double worthIn(Lump l, Deal d, Ctx c) {
        int year = c.here().year();
        if (d.money.inDays()) {
            if (l.isGoods()) {
                // Step 24: he does not take his own good for itself (logs for logs).
                return d.kind == TradeView.Kind.SWAP && l.good().equals(d.good) ? 0.0d : Math.max(0.0d, l.worth());
            }
            if (l.isMoney() && c.coinsAsMetal()) {
                MoneyTable.Money m = c.table().money(l.money());
                if (m == null || m.inDays()) {
                    return 0.0d;
                }
                int fee = Math.max(0, Math.min(100, c.terms().strangeFeePercent()));
                return l.value() * rate(c.table(), m, d.money, year) * (100 - fee) / 100.0d;
            }
            return 0.0d;
        }
        if (!l.isMoney()) {
            return 0.0d;
        }
        MoneyTable.Money m = c.table().money(l.money());
        if (m == null) {
            return 0.0d;
        }
        if (m.id().equals(d.money.id())) {
            return l.value();
        }
        if (!taken(c.here(), m.id())) {
            return 0.0d;
        }
        return l.value() * rate(c.table(), m, d.money, year);
    }

    static Outcome buy(Deal d, List<Lump> tray, Ctx c) {
        int year = c.here().year();
        boolean days = d.money.inDays();
        String price = d.priceText.isEmpty() ? MoneyFormat.amount(d.money, d.amount, year) : d.priceText;
        if (d.owned) {
            return no("You have that already.");
        }
        if (d.left == 0) {
            return no("That is all I have today. Come back tomorrow.");
        }
        if (!d.refusal.isEmpty()) {
            return no(d.refusal);
        }
        List<Tender.Kind> kinds = new ArrayList<>();
        List<Lump> used = new ArrayList<>();
        Lump refused = null;
        boolean goods = false;
        boolean coins = false;
        for (Lump l : tray) {
            double each = worthIn(l, d, c);
            if (each > 0) {
                kinds.add(new Tender.Kind(l.slot() + ":" + l.money() + l.good(), each, l.count(), (int) Math.min(64, l.count())));
                used.add(l);
            } else if (l.isMoney()) {
                coins = true;
                if (refused == null) {
                    refused = l;
                }
            } else {
                goods = true;
            }
        }
        long tab = days ? c.tab(d.money) : 0L;
        Tender.Pick pick = Tender.choose(kinds, d.amount - tab);
        String tabWords = tab > 0 ? " Your tab: " + MoneyFormat.amount(d.money, tab, year) + "." : "";
        if (pick == null) {
            if (kinds.isEmpty()) {
                if (days) {
                    String why = coins ? " I don't take coins." : goods ? " That is not something I take." : "";
                    return no("It takes " + price + "." + why + " Put goods I take in the slots." + tabWords);
                }
                if (refused != null) {
                    return no("It costs " + price + ". " + refusal(refused, c));
                }
                if (goods) {
                    return no("It costs " + price + ". I sell for money, not for goods.");
                }
                Outcome purse = tray.isEmpty() ? fromPurse(d, c, price) : null;
                return purse != null ? purse : no("It costs " + price + ". " + emptyWords(d, c));
            }
            long all = 0L;
            for (Tender.Kind k : kinds) {
                all += k.value(k.usable());
            }
            return no((days ? "It takes " : "It costs ") + price + ". You put in "
                    + (days ? MoneyFormat.amount(d.money, all, year) : handed(used, null, c)) + "." + tabWords);
        }
        long[] take = new long[2];
        for (int i = 0; i < used.size(); i++) {
            int slot = used.get(i).slot();
            if (slot == 0 || slot == 1) {
                take[slot] += pick.of(i);
            }
        }
        if (days) {
            long tabChange = pick.value() - d.amount;
            StringBuilder w = new StringBuilder();
            if (pick.pieces() == 0) {
                w.append("Your tab pays for it: ").append(price).append('.');
            } else {
                w.append("You give ").append(handed(used, pick, c));
                if (tabChange < 0) {
                    w.append(" and ").append(MoneyFormat.amount(d.money, -tabChange, year)).append(" from your tab");
                }
                w.append('.');
                if (tabChange > 0) {
                    w.append(" He owes you ").append(MoneyFormat.amount(d.money, tabChange, year)).append('.');
                }
            }
            return new Outcome(true, take[0], take[1], null, List.of(), tabChange, w.toString());
        }
        long change = pick.value() - d.amount;
        List<Denominations.Piece> ps = pieces(c.table(), d.money, year);
        List<long[]> back = change > 0 ? Denominations.split(ps, change) : List.of();
        String w = "You pay " + handed(used, pick, c) + "."
                + (change > 0 ? " Your change: " + MoneyFormat.amount(d.money, Denominations.splitValue(ps, change), year)
                + "." : "");
        return new Outcome(true, take[0], take[1], d.money, back, 0L, w);
    }

    /**
     * The slots are empty and two of them cannot hold the pieces it takes (all you have, in many kinds): he counts the
     * price out of the purse, with change; null when the purse cannot pay it in that money.
     */
    @Nullable
    static Outcome fromPurse(Deal d, Ctx c, String price) {
        Map<Long, Long> held = c.held() == null ? null : c.held().get(d.money.id());
        if (held == null || held.isEmpty()) {
            return null;
        }
        List<Denominations.Piece> ps = pieces(c.table(), d.money, c.here().year());
        Denominations.Plan plan = Denominations.plan(held, d.amount, ps);
        if (plan == null) {
            return null;
        }
        List<Tender.Kind> kinds = new ArrayList<>();
        for (Map.Entry<Long, Long> e : held.entrySet()) {
            long n = e.getValue();
            while (n > 0) {
                long k = Math.min(64L, n);
                kinds.add(new Tender.Kind(String.valueOf(e.getKey()), e.getKey(), k, 64));
                n -= k;
            }
        }
        if (Tender.choose(kinds, d.amount) != null) {
            return null; // two slots can hold it: the screen fills them instead
        }
        int year = c.here().year();
        String w = "It costs " + price + ". That takes more kinds of pieces than the two slots hold, so he counts it out of"
                + " your purse" + (plan.change() > 0 ? "; your change: " + MoneyFormat.amount(d.money,
                Denominations.splitValue(ps, plan.change()), year) : "") + ".";
        return new Outcome(true, 0L, 0L, d.money, List.of(), 0L, w, true);
    }

    static Outcome sell(Deal d, List<Lump> tray, Ctx c) {
        int year = c.here().year();
        String pay = MoneyFormat.amount(d.money, d.amount, year);
        String lot = d.goodWords.isEmpty() ? d.count + " " + d.good : d.goodWords;
        if (!d.refusal.isEmpty()) {
            return no(d.refusal);
        }
        long have = 0L;
        boolean other = false;
        for (Lump l : tray) {
            if (l.isGoods() && l.good().equals(d.good)) {
                have += l.count();
            } else {
                other = true;
            }
        }
        String offer = "I pay " + pay + " for " + lot + ".";
        if (have < d.count) {
            if (have == 0 && other) {
                return no(offer + " That is not what I buy here.");
            }
            if (have == 0) {
                return no(offer + " Put them in the slots.");
            }
            return no(offer + " You put in " + have + ".");
        }
        long[] take = new long[2];
        long left = d.count;
        for (int slot = 0; slot < 2 && left > 0; slot++) {
            for (Lump l : tray) {
                if (l.slot() == slot && l.isGoods() && l.good().equals(d.good)) {
                    long t = Math.min(left, l.count());
                    take[slot] += t;
                    left -= t;
                }
            }
        }
        List<long[]> give = Denominations.split(pieces(c.table(), d.money, year), d.amount);
        return new Outcome(true, take[0], take[1], d.money, give, 0L, offer);
    }

    static Outcome change(Deal d, List<Lump> tray, Ctx c) {
        int year = c.here().year();
        MoneyTable.Money from = d.from;
        MoneyTable.Money to = d.money;
        long have = 0L;
        long[] take = new long[2];
        for (Lump l : tray) {
            if (l.isMoney() && l.money().equals(from.id()) && (l.slot() == 0 || l.slot() == 1)) {
                have += l.value() * l.count();
                take[l.slot()] += l.count();
            }
        }
        boolean fixed = c.table().fixed(from.id(), to.id(), year);
        int fee = Math.max(0, Math.min(100, fixed ? c.terms().feePercent() : c.terms().strangeFeePercent()));
        if (have <= 0) {
            return no("Put in the " + from.name() + " you want to change, as much as you like. I give " + to.called()
                    + " for it and keep " + fee + " in every 100.");
        }
        MarketService.Swap s = MarketService.swapOffer(c.table(), from, have, to, year, c.terms());
        if (s.result() <= 0) {
            return no("That is too little to change.");
        }
        List<Denominations.Piece> ps = pieces(c.table(), to, year);
        if (Denominations.splitCount(ps, s.result()) > CoinPurse.MOST_PIECES) {
            return no("That is more than you can carry. Change less at a time.");
        }
        return new Outcome(true, take[0], take[1], to, Denominations.split(ps, s.result()), 0L, "For "
                + PurseService.named(from, have, year) + " I give " + PurseService.named(to, s.result(), year) + ".");
    }

    static Outcome regroup(Deal d, List<Lump> tray, Ctx c) {
        int year = c.here().year();
        boolean make = d.kind == TradeView.Kind.MAKE;
        MoneyTable.Money m = d.from;
        List<Denominations.Piece> ps = pieces(c.table(), m, year);
        long have = 0L;
        long count = 0L;
        long smallestIn = Long.MAX_VALUE;
        long[] take = new long[2];
        for (Lump l : tray) {
            if (l.isMoney() && l.money().equals(m.id()) && (l.slot() == 0 || l.slot() == 1)) {
                have += l.value() * l.count();
                count += l.count();
                smallestIn = Math.min(smallestIn, l.value());
                take[l.slot()] += l.count();
            }
        }
        if (have <= 0) {
            return no("Put in pieces of the " + m.name() + " and I give the same in " + (make ? "bigger" : "smaller")
                    + " pieces, for nothing.");
        }
        List<long[]> out;
        if (make) {
            out = Denominations.split(ps, have);
            if (Denominations.splitValue(ps, have) != have) {
                return no("Those pieces don't add up to bigger ones.");
            }
            long n = 0L;
            for (long[] vn : out) {
                n += vn[1];
            }
            if (n >= count) {
                return no("Those are as big as they come.");
            }
        } else {
            List<Denominations.Piece> smaller = new ArrayList<>();
            for (Denominations.Piece p : ps) {
                if (p.value() < smallestIn) {
                    smaller.add(p);
                }
            }
            if (smaller.isEmpty()) {
                return no("Those are the smallest pieces there are.");
            }
            if (Denominations.splitValue(smaller, have) != have) {
                return no("Those don't break into smaller pieces evenly.");
            }
            if (Denominations.splitCount(smaller, have) > CoinPurse.MOST_PIECES) {
                return no("That is more than you can carry. Break fewer at a time.");
            }
            out = Denominations.split(smaller, have);
        }
        return new Outcome(true, take[0], take[1], m, out, 0L, PurseService.named(m, have, year) + " in "
                + (make ? "bigger" : "smaller") + " pieces.");
    }

    // ------------------------------------------------------------------------------------------------- words

    /**
     * The slots are empty and the screen could not fill them: the player carries too little money taken here, or only
     * money not taken here (old money, money from far away), or nothing.
     */
    static String emptyWords(Deal d, Ctx c) {
        int year = c.here().year();
        long taken = 0L;
        Lump foreign = null;
        if (c.held() != null) {
            for (Map.Entry<String, ? extends Map<Long, Long>> e : c.held().entrySet()) {
                for (Map.Entry<Long, Long> pv : e.getValue().entrySet()) {
                    Lump l = Lump.money(0, e.getKey(), pv.getKey(), pv.getValue());
                    double each = worthIn(l, d, c);
                    if (each > 0) {
                        taken += (long) Math.floor(each * pv.getValue() + 1e-6);
                    } else if (foreign == null) {
                        MoneyTable.Money m = c.table().money(e.getKey());
                        if (m != null && !m.inDays()) {
                            foreign = l;
                        }
                    }
                }
            }
        }
        String have = taken > 0 ? " You have only " + MoneyFormat.amount(d.money, taken, year) + " in money taken here." : "";
        if (foreign != null) {
            return refusal(foreign, c) + have;
        }
        return taken > 0 ? have.trim() : "Put your money in the two slots.";
    }

    /** "That old Mughal rupee is not taken any more. A money-changer will change it for you." */
    static String refusal(Lump l, Ctx c) {
        MoneyTable.Money m = c.table().money(l.money());
        String name = m == null ? "money" : m.name();
        boolean old = m != null && ShopService.old(c.table(), m, c.here().year());
        return (old ? "That old " + name + " is not taken any more." : "We don't take the " + name + " here.")
                + " A money-changer will change it for you.";
    }

    /** What is handed over, in words: "1 rupee and 2 annas", "16 sheaves of wheat" (all of each lump when no pick). */
    static String handed(List<Lump> used, @Nullable Tender.Pick pick, Ctx c) {
        int year = c.here().year();
        List<String> parts = new ArrayList<>();
        Map<String, Long> money = new java.util.LinkedHashMap<>();
        for (int i = 0; i < used.size(); i++) {
            Lump l = used.get(i);
            long k = pick == null ? l.count() : pick.of(i);
            if (k <= 0) {
                continue;
            }
            if (l.isMoney()) {
                money.merge(l.money(), k * l.value(), Long::sum);
            } else {
                parts.add(goodsWords(l, k));
            }
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : money.entrySet()) {
            MoneyTable.Money m = c.table().money(e.getKey());
            if (m == null) {
                continue;
            }
            out.add(m.id().equals(c.here().money().id()) ? MoneyFormat.amount(m, e.getValue(), year)
                    : PurseService.named(m, e.getValue(), year));
        }
        out.addAll(parts);
        return MarketTalkWords.list(out);
    }

    /** "16 sheaves of wheat" for a good of the table, else "16 × Wheat". */
    static String goodsWords(Lump l, long k) {
        GoodsTable.Good g = l.good().startsWith(MarketService.ATLAS) ? null : GoodsTable.get().good(l.good());
        if (g != null) {
            return g.count(k);
        }
        return k + " × " + (l.name().isEmpty() ? "goods" : l.name());
    }

    /** Lists in words ("a, b and c"), the way the talk says them. */
    static final class MarketTalkWords {
        private MarketTalkWords() {
        }

        static String list(List<String> words) {
            if (words.isEmpty()) {
                return "nothing";
            }
            if (words.size() == 1) {
                return words.get(0);
            }
            return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
        }
    }

    // ------------------------------------------------------------------------------------------------- checks

    /**
     * The rules' own check, with no world. Hyderabad 1948: a 19-pie notebook paid with a rupee gives 173 pies back as
     * pieces; Indian rupees pay at 7 for 6; an old Mughal rupee is refused with the reason; 16 sheaves of wheat sell when
     * 16 are in the slots and not with 10; the money-changer changes one O.S. rupee of two, any amount; a rupee breaks
     * into smaller pieces and small pieces make bigger ones. Calabar 1800: cowries are the small change beside manillas
     * (a quarter of a day's work costs cowries; a manilla pays it and cowries come back). Where people swap: goods pay at what he pays,
     * too much is owed back, the tab pays the rest. Null when all is well.
     */
    public static String selfTest() {
        String t = Tender.selfTest();
        if (t != null) {
            return "two slots: " + t;
        }
        MoneyTable table = MoneyTable.get();
        MarketService.Terms terms = MarketService.Terms.DEFAULT;
        MoneyTable.Here hyd = table.at(17.3616, 78.4747, 1948);
        MoneyTable.Money os = hyd.money();
        MoneyTable.Money inr = table.money("indian_rupee_anna");
        if (!"osmania_sicca".equals(os.id()) || inr == null) {
            return "Hyderabad 1948 should use the Osmania sicca and take Indian rupees: " + os.id();
        }
        Ctx c = new Ctx(table, hyd, terms, false, Map.of());
        Deal book = new Deal("book:notebook", TradeView.Kind.BUY);
        book.money = os;
        book.amount = 19;
        Outcome o = judge(book, List.of(Lump.money(0, os.id(), 192, 1)), c);
        if (!o.ok() || o.take0() != 1 || o.given() != 173) {
            return "a 19-pie notebook paid with an O.S. rupee: " + o;
        }
        Outcome in = judge(book, List.of(Lump.money(1, inr.id(), 12, 2)), c);
        if (!in.ok() || in.take1() != 2 || in.given() != 28 - 19) {
            return "19 pai paid with 2 Indian annas (28 pai at 7 for 6): " + in;
        }
        MoneyTable.Money sicca = table.money("sicca");
        if (sicca != null) {
            Outcome old = judge(book, List.of(Lump.money(0, sicca.id(), 192, 1)), c);
            if (old.ok() || !old.words().contains("not taken any more")) {
                return "an old sicca rupee should be refused as old money: " + old.words();
            }
        }
        Deal wheat = new Deal("sell:WHEAT", TradeView.Kind.SELL);
        wheat.money = os;
        wheat.amount = 52;
        wheat.good = "WHEAT";
        wheat.count = 16;
        wheat.goodWords = "16 sheaves of wheat";
        Outcome sold = judge(wheat, List.of(Lump.goods(0, "WHEAT", 3.3, 20, "Wheat")), c);
        Outcome few = judge(wheat, List.of(Lump.goods(0, "WHEAT", 3.3, 10, "Wheat")), c);
        if (!sold.ok() || sold.take0() != 16 || sold.given() != 52 || few.ok()) {
            return "16 sheaves of wheat for 52 pai: " + sold + " / with 10: " + few;
        }
        Deal ch = new Deal("change:osmania_sicca>indian_rupee_anna", TradeView.Kind.CHANGE);
        ch.from = os;
        ch.money = inr;
        Outcome one = judge(ch, List.of(Lump.money(0, os.id(), 192, 1)), c);
        if (!one.ok() || one.take0() != 1 || one.given() <= 0 || one.given() >= 192) {
            return "one O.S. rupee into Indian money: " + one;
        }
        Deal brk = new Deal("break:osmania_sicca", TradeView.Kind.BREAK);
        brk.from = os;
        brk.money = os;
        Outcome broken = judge(brk, List.of(Lump.money(0, os.id(), 192, 1)), c);
        Deal mk = new Deal("make:osmania_sicca", TradeView.Kind.MAKE);
        mk.from = os;
        mk.money = os;
        Outcome made = judge(mk, List.of(Lump.money(0, os.id(), 12, 16)), c);
        Map<String, Map<Long, Long>> held = Map.of(os.id(), Map.of(12L, 1L, 3L, 1L, 1L, 4L));
        Outcome purse = judge(book, List.of(), new Ctx(table, hyd, terms, false, Map.of(), held));
        if (!purse.ok() || !purse.purse()) {
            return "19 pai from 1 anna, 3 pai and 4 single pai (three kinds) should come out of the purse: " + purse;
        }
        if (!broken.ok() || broken.given() != 192 || broken.givenPieces() < 2 || !made.ok() || made.given() != 192
                || made.givenPieces() != 1) {
            return "a rupee broken: " + broken + "; 16 annas made bigger: " + made;
        }
        MoneyTable.Here cal = table.at(4.95, 8.32, 1800);
        MoneyTable.Money small = smallChange(table, cal);
        if (!"manilla".equals(cal.money().id()) || small == null || !"cowrie".equals(small.id())) {
            return "Calabar 1800: cowries should be the small change beside manillas: " + cal.money().id() + " / " + small;
        }
        Price p = price(cal.dayPay() / 4.0d, table, cal, false);
        if (!p.money().id().equals("cowrie") || p.amount() <= 1) {
            return "a quarter of a day in Calabar 1800 should cost cowries: " + p;
        }
        Ctx cc = new Ctx(table, cal, terms, false, Map.of());
        Deal note = new Deal("book:x", TradeView.Kind.BUY);
        note.money = small;
        note.amount = p.amount();
        Outcome withManilla = judge(note, List.of(Lump.money(0, "manilla", 1, 1)), cc);
        if (!withManilla.ok() || withManilla.giveMoney() == null || !withManilla.giveMoney().id().equals("cowrie")
                || withManilla.given() <= 0) {
            return "a manilla for a cowrie price should give cowries back: " + withManilla;
        }
        MoneyTable.Money barter = table.money("barter");
        if (barter != null) {
            MoneyTable.Here none = new MoneyTable.Here(hyd.row(), barter, List.of(), 1500, 0.0d, 0.0d);
            Ctx bc = new Ctx(table, none, terms, false, Map.of(barter.id(), 30L));
            Deal axe = new Deal("good:x", TradeView.Kind.SWAP);
            axe.money = barter;
            axe.amount = 100;
            Outcome swap = judge(axe, List.of(Lump.goods(0, "WHEAT", 9.0, 64, "Wheat")), bc);
            if (!swap.ok() || swap.take0() != 8 || swap.tab() != 8 * 9 - 100) {
                return "goods worth 9 each for 100 with a tab of 30: 8 of them and the tab pays 28: " + swap;
            }
            Outcome over = judge(axe, List.of(Lump.goods(0, "WHEAT", 40.0, 5, "Wheat")), new Ctx(table, none, terms, false,
                    Map.of()));
            if (!over.ok() || over.take0() != 3 || over.tab() != 20) {
                return "3 goods worth 40 for 100: he owes 20: " + over;
            }
        }
        return null;
    }
}
