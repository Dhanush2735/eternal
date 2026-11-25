package com.eternalengine.money;

import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.trade.CommodityPricingEngine;
import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 19b: earning money. Merchants buy goods at the trade engine's prices ({@link CommodityPricingEngine}), turned
 * into the money of the place and year through an ordinary labourer's day's pay there, so a price makes sense in
 * Babylon and in 1948 Hyderabad alike. A merchant pays a share of what a good is worth; he pays full price for the
 * first few of each good in a day, half for as many again, and then has enough of it; and he has only so much
 * money a day. The money-changer swaps any money for the money of the place (and the money of the place for other
 * money taken there) and keeps a cut: a small one where a fixed rate is known, a bigger one for old money or money
 * from far away, which he values by what it buys.
 *
 * <p>Step 19g: every settled place has somebody who buys. A town has a trader (he pays a little less, has less money,
 * and changes money too, keeping the bigger cut); a village with money has a pedlar (less again); where people swap
 * and nobody trades for a living, anybody swaps a little. Buyers also take the products of the atlas that people
 * here make or bring in, at the price a seller asks (twice as much when brought from far), less their share.
 */
public final class MarketService {

    /** One price unit of the trade engine is worth a twentieth of an ordinary labourer's day. */
    public static final double DAYS_PER_PRICE_UNIT = 0.05d;
    public static final String MERCHANT = "merchant";
    public static final String MONEY_CHANGER = "money_changer";
    /** Step 19g: the town trader, the village pedlar, and the terms of anybody who swaps a little. */
    public static final String TRADER = "trader";
    public static final String PEDLAR = "pedlar";
    public static final String LOCAL = "local";
    /** Step 19g: the goods ids of atlas products a buyer takes ("atlas:paper"). */
    public static final String ATLAS = "atlas:";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CommodityPricingEngine BASE = new CommodityPricingEngine();
    private static final Map<String, TagKey<Item>> TAGS = new ConcurrentHashMap<>();

    private MarketService() {
    }

    /** The market's settings (config, or these defaults when no config is loaded). */
    public record Terms(int buyPercent, int budgetDays, int feePercent, int strangeFeePercent) {
        public static final Terms DEFAULT = new Terms(70, 8, 2, 10);

        /**
         * Step 19g: the terms of a buyer of this role. A town trader pays 10 less in 100 and has half the money; a
         * village pedlar 20 less and a quarter; anybody swapping a little 20 less and an eighth (never less than a
         * day's pay). The trader changes money keeping the bigger cut, whatever the rate. A merchant or a
         * money-changer: these terms.
         */
        public Terms as(String role) {
            String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
            return switch (r) {
                case TRADER -> new Terms(Math.max(0, buyPercent - 10), Math.max(1, budgetDays / 2), strangeFeePercent,
                        strangeFeePercent);
                case PEDLAR -> new Terms(Math.max(0, buyPercent - 20), Math.max(1, budgetDays / 4), strangeFeePercent,
                        strangeFeePercent);
                case LOCAL -> new Terms(Math.max(0, buyPercent - 20), Math.max(1, budgetDays / 8), strangeFeePercent,
                        strangeFeePercent);
                default -> this;
            };
        }

        public static Terms current() {
            try {
                var c = com.eternalengine.config.EternalEngineConfig.COMMON;
                return new Terms(c.merchantBuyPercent.get(), c.merchantBudgetDays.get(), c.moneyChangerFeePercent.get(),
                        c.moneyChangerStrangeFeePercent.get());
            } catch (Throwable t) {
                return DEFAULT;
            }
        }
    }

    /** A merchant's offer for a good: how many he takes and what he pays, or why he takes none. */
    public record Offer(int count, long amount, String reason) {
        public static final String NONE = "none";
        public static final String ENOUGH = "enough";
        public static final String BUDGET = "budget";
        public static final String LITTLE = "little";

        public boolean ok() {
            return count > 0 && amount > 0;
        }
    }

    /** A money-changer's offer: all of {@code amount} of one money for {@code result} of another. */
    public record Swap(MoneyTable.Money from, long amount, MoneyTable.Money to, long result, boolean fixed, int fee) {
    }

    /** What a sale or a swap did, in the player's words. */
    public record Done(boolean ok, String text) {
    }

    // ------------------------------------------------------------------------------------------------- prices

    /** The trade engine's price of one unit of a good: the running engine's when there is one, else its base price. */
    public static double enginePrice(String goodId, String civ) {
        if (goodId != null && goodId.startsWith(ATLAS)) {
            return atlasPrice(goodId);
        }
        try {
            if (SimulationEngine.isInitialized() && SimulationEngine.getInstance().getLogisticsManager() != null) {
                String c = civ == null || civ.isBlank() ? "market" : civ;
                double p = SimulationEngine.getInstance().getLogisticsManager().pricingEngine()
                        .calculateUnitPrice(goodId, c, c, 0.0d, 0.0d);
                if (p > 0) {
                    return p;
                }
            }
        } catch (Throwable ignored) {
            // No running engine: the base price below.
        }
        return basePrice(goodId);
    }

    /** The engine's base price of one unit (no supply and demand). */
    public static double basePrice(String goodId) {
        if (goodId != null && goodId.startsWith(ATLAS)) {
            return atlasPrice(goodId);
        }
        CommodityPricingEngine.CommoditySpec s = BASE.getSpec(goodId);
        return s == null ? 0.0d : s.basePrice();
    }

    /** Step 19g: an atlas product in engine units: its days of work for one lot (the lot's size is the good's unit). */
    static double atlasPrice(String goodId) {
        try {
            ProductAtlas.Product p = ProductAtlas.get().product(goodId.substring(ATLAS.length()));
            return p == null || p.days() <= 0 ? 0.0d : p.days() / DAYS_PER_PRICE_UNIT;
        } catch (Throwable t) {
            return 0.0d;
        }
    }

    /** What one item is worth, in the smallest unit of the money of here, for an engine price (before any share). */
    public static double worth(GoodsTable.Good g, MoneyTable.Here here, double enginePrice) {
        return enginePrice * DAYS_PER_PRICE_UNIT / g.unitItems() * here.dayPay();
    }

    /** What a merchant here pays for one item at full price. */
    public static double pays(GoodsTable.Good g, MoneyTable.Here here, String civ, Terms t) {
        return worth(g, here, enginePrice(g.id(), civ)) * Math.max(0, t.buyPercent()) / 100.0d;
    }

    /** What a merchant can pay out in one day here. */
    public static long budget(MoneyTable.Here here, Terms t) {
        return here.dayPay() * Math.max(0, t.budgetDays());
    }

    /**
     * What a merchant takes of {@code carried} items paying {@code each} at full price: full price while he has bought
     * fewer than {@code want} today, half price up to twice that, then no more; and never more than
     * {@code budgetLeft} in all. The amount is rounded down to the smallest unit.
     */
    public static Offer offer(double each, int want, int carried, int soldToday, long budgetLeft) {
        if (carried <= 0) {
            return new Offer(0, 0L, Offer.NONE);
        }
        if (soldToday >= 2 * want) {
            return new Offer(0, 0L, Offer.ENOUGH);
        }
        if (budgetLeft <= 0) {
            return new Offer(0, 0L, Offer.BUDGET);
        }
        double total = 0.0d;
        int n = 0;
        for (int k = 0; k < carried; k++) {
            int s = soldToday + k;
            double f = s < want ? 1.0d : s < 2 * want ? 0.5d : 0.0d;
            if (f <= 0) {
                break;
            }
            double next = total + each * f;
            if ((long) Math.floor(next + 1e-9) > budgetLeft) {
                break;
            }
            total = next;
            n++;
        }
        long amount = (long) Math.floor(total + 1e-9);
        if (n == 0) {
            return new Offer(0, 0L, Offer.BUDGET);
        }
        if (amount <= 0) {
            return new Offer(0, 0L, Offer.LITTLE);
        }
        return new Offer(n, amount, "");
    }

    /**
     * "4 annas 5 pai for 16 sheaves of wheat": a count that shows the price well (1, 16 or 64 items) and what a
     * merchant pays for it at full price.
     */
    public static String priceEntry(GoodsTable.Good g, double each, MoneyTable.Money money, int year) {
        // Enough of the smallest unit that rounding down says little: 20 units (Hyderabad), fewer where a day's pay is
        // only a few coins, but at least 4; days of work are told to the quarter day.
        double threshold = money.inDays() ? money.dayPay(year) / 4.0d : Math.max(4.0d, Math.min(20.0d,
                money.dayPay(year) / 8.0d));
        int q = each >= threshold ? 1 : each * 16 >= threshold ? 16 : 64;
        long amount = (long) Math.floor(each * q + 1e-9);
        String price = amount > 0 ? MoneyFormat.amount(money, amount, year) : "next to nothing";
        return price + " for " + g.some(q);
    }

    // ------------------------------------------------------------------------------------------- money-changer

    /** The money-changer's offer for all of an amount: the table's conversion less his cut. */
    public static Swap swapOffer(MoneyTable table, MoneyTable.Money from, long amount, MoneyTable.Money to, int year,
                                 Terms t) {
        boolean fixed = table.fixed(from.id(), to.id(), year);
        int fee = Math.max(0, Math.min(100, fixed ? t.feePercent() : t.strangeFeePercent()));
        long gross = table.convert(from, amount, to, year);
        long result = (long) Math.floor(gross * (100 - fee) / 100.0d + 1e-9);
        long piece = Denominations.smallest(to, year); // step 23b: he pays in pieces, so he offers whole pieces
        if (!to.inDays() && piece > 1) {
            result = result / piece * piece;
        }
        return new Swap(from, amount, to, Math.max(0L, result), fixed, fee);
    }

    /**
     * Step 23b: an offer as the buyer can pay it: rounded down to the smallest piece of the money (a merchant in
     * Moscow in 2026 pays whole rubles); too little for one piece is no offer.
     */
    public static Offer payable(Offer o, MoneyTable.Money money, int year) {
        if (o == null || !o.ok() || money == null || money.inDays()) {
            return o;
        }
        long piece = Denominations.smallest(money, year);
        if (piece <= 1) {
            return o;
        }
        long amount = o.amount() / piece * piece;
        return amount <= 0 ? new Offer(0, 0L, Offer.LITTLE) : new Offer(o.count(), amount, o.reason());
    }

    /**
     * What the money-changer offers for a purse here: all of each other money for the money of the place, and the
     * money of the place for each other money taken here.
     */
    public static List<Swap> swaps(MoneyTable table, PlayerPurses.Purse purse, MoneyTable.Here here, Terms t) {
        List<Swap> out = new ArrayList<>();
        MoneyTable.Money main = here.money();
        for (Map.Entry<String, Long> e : purse.money().entrySet()) {
            MoneyTable.Money m = table.money(e.getKey());
            if (m == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            if (m.id().equals(main.id())) {
                for (MoneyTable.Money a : here.accepts()) {
                    out.add(swapOffer(table, m, e.getValue(), a, here.year(), t));
                }
            } else {
                out.add(swapOffer(table, m, e.getValue(), main, here.year(), t));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ people of a place

    /**
     * The roles of a place with one money-changer added right after the merchants: only where there are merchants
     * and the place has money (not barter).
     */
    public static String[] withMoneyChanger(String[] roles, double lat, double lon, int year) {
        if (roles == null) {
            return new String[0];
        }
        int at = -1;
        for (int i = 0; i < roles.length; i++) {
            if (MONEY_CHANGER.equalsIgnoreCase(roles[i])) {
                return roles;
            }
            if (MERCHANT.equalsIgnoreCase(roles[i])) {
                at = i;
            }
        }
        if (at < 0) {
            return roles;
        }
        try {
            if (MoneyTable.get().at(lat, lon, year).money().barter()) {
                return roles;
            }
        } catch (Throwable t) {
            return roles;
        }
        String[] out = new String[roles.length + 1];
        System.arraycopy(roles, 0, out, 0, at + 1);
        out[at + 1] = MONEY_CHANGER;
        System.arraycopy(roles, at + 1, out, at + 2, roles.length - at - 1);
        return out;
    }

    /** Step 19g: the roles that buy goods: the merchant, the town trader and the village pedlar. */
    public static boolean buyer(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return r.equals(MERCHANT) || r.equals(TRADER) || r.equals(PEDLAR);
    }

    /** Step 19g: the roles that change money: the money-changer and the town trader. */
    public static boolean changes(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return r.equals(MONEY_CHANGER) || r.equals(TRADER);
    }

    /**
     * Step 19g: the roles of a place with one buyer added where none is: a trader in a town (where people swap, he
     * swaps), a pedlar in a village with money. A village where people swap gets nobody: anybody there swaps a little
     * ({@link #swapsALittle}).
     */
    public static String[] withTrader(String[] roles, SettlementRank rank, double lat, double lon, int year) {
        if (roles == null || roles.length == 0) {
            return roles == null ? new String[0] : roles;
        }
        for (String r : roles) {
            if (buyer(r)) {
                return roles;
            }
        }
        String add;
        if (rank == SettlementRank.TOWN) {
            add = TRADER;
        } else if (rank == SettlementRank.CITY) {
            return roles;
        } else {
            try {
                if (MoneyTable.get().at(lat, lon, year).money().barter()) {
                    return roles;
                }
            } catch (Throwable t) {
                return roles;
            }
            add = PEDLAR;
        }
        String[] out = Arrays.copyOf(roles, roles.length + 1);
        out[roles.length] = add;
        return out;
    }

    /**
     * Step 19g: true where anybody swaps a little: the place has no money (people swap) and nobody among its people
     * trades for a living (no merchant, trader or pedlar).
     */
    public static boolean swapsALittle(String[] placeRoles, MoneyTable.Here here) {
        if (here == null || !here.money().barter()) {
            return false;
        }
        if (placeRoles != null) {
            for (String r : placeRoles) {
                if (buyer(r)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------- atlas goods (19g)

    /**
     * Step 19g: the atlas products a buyer takes here, as goods: one per item (the choice sellers make: made here
     * before brought, the later product first), made here or brought, never an item of the goods table. Brought from
     * far, an item is worth {@link ProductAtlas#BROUGHT_MARKUP} times as much. A buyer wants two lots a day at full
     * price.
     */
    public static List<GoodsTable.Good> atlasGoods(double lat, double lon, int year) {
        List<GoodsTable.Good> out = new ArrayList<>();
        Set<String> tableItems = new HashSet<>();
        for (GoodsTable.Good g : GoodsTable.get().all()) {
            tableItems.addAll(g.ids());
        }
        try {
            for (ProductAtlas.Answer a : ProductAtlas.get().forSale(MERCHANT, lat, lon, year)) {
                GoodsTable.Good g = atlasGood(a);
                if (g != null && !tableItems.contains(a.product().item())) {
                    out.add(g);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[EE market] atlas goods at {}, {} in {}: {}", lat, lon, year, t.toString());
        }
        return out;
    }

    /** Step 19g: one atlas answer as a good (its unit is one lot, made dearer when brought), or null. */
    @javax.annotation.Nullable
    public static GoodsTable.Good atlasGood(ProductAtlas.Answer a) {
        ProductAtlas.Product p = a == null ? null : a.product();
        if (p == null || p.item().isBlank() || p.days() <= 0 || p.count() <= 0 || !a.status().present()) {
            return null;
        }
        double markup = a.status() == ProductAtlas.Status.BROUGHT ? ProductAtlas.BROUGHT_MARKUP : 1.0d;
        // Its name in a list: "sugar", "torches" (a lot of several), "iron axes" (one at a time).
        String name = p.count() == 1 || p.shortName().isBlank() ? p.many()
                : Character.toLowerCase(p.shortName().charAt(0)) + p.shortName().substring(1);
        return new GoodsTable.Good(ATLAS + p.id(), name, p.one(), p.many(), List.of(p.item()), p.count() / markup,
                Math.max(2, 2 * p.count()), List.of());
    }

    /** Step 19g: the atlas good a buyer takes here by its id ("atlas:paper"), or null. */
    @javax.annotation.Nullable
    public static GoodsTable.Good atlasGood(String id, double lat, double lon, int year) {
        for (GoodsTable.Good g : atlasGoods(lat, lon, year)) {
            if (g.id().equals(id)) {
                return g;
            }
        }
        return null;
    }

    /** Step 19g: everything a buyer takes here: the goods table's goods that have an item, then the atlas products. */
    public static List<GoodsTable.Good> bought(double lat, double lon, int year) {
        List<GoodsTable.Good> out = new ArrayList<>();
        for (GoodsTable.Good g : GoodsTable.get().boughtAt(lat, lon, year)) {
            if (g.sellable()) {
                out.add(g);
            }
        }
        out.addAll(atlasGoods(lat, lon, year));
        return out;
    }

    // --------------------------------------------------------------------------------------- the player's side

    /** A merchant's day: the overworld's Minecraft day count, whatever the speed of history. */
    public static long marketDay(MinecraftServer server) {
        return Math.floorDiv(server.overworld().getDayTime(), 24000L);
    }

    private static List<TagKey<Item>> tagKeys(GoodsTable.Good g) {
        List<TagKey<Item>> out = new ArrayList<>();
        for (String tag : g.tags()) {
            try {
                out.add(TAGS.computeIfAbsent(tag, x -> TagKey.create(Registries.ITEM, new ResourceLocation(x))));
            } catch (Exception ignored) {
                // A malformed tag name counts nothing.
            }
        }
        return out;
    }

    private static boolean matches(ItemStack s, List<String> ids, List<TagKey<Item>> tags) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (key != null && ids.contains(key.toString())) {
            return true;
        }
        for (TagKey<Item> t : tags) {
            if (s.is(t)) {
                return true;
            }
        }
        return false;
    }

    /** Step 24: true when this stack is the good (one of its items, or an item with one of its tags). */
    public static boolean isGood(ItemStack s, GoodsTable.Good g) {
        return g != null && matches(s, g.ids(), tagKeys(g));
    }

    /** How many items of a good the player carries (hotbar and inventory; not armour or the off hand). */
    public static int carried(ServerPlayer p, GoodsTable.Good g) {
        List<String> ids = g.ids();
        List<TagKey<Item>> tags = tagKeys(g);
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (matches(s, ids, tags)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Takes {@code n} items of a good from the player, the inventory before the hotbar; returns how many it took. */
    public static int take(ServerPlayer p, GoodsTable.Good g, int n) {
        List<String> ids = g.ids();
        List<TagKey<Item>> tags = tagKeys(g);
        NonNullList<ItemStack> items = p.getInventory().items;
        int left = n;
        int size = items.size();
        for (int k = 0; k < size && left > 0; k++) {
            int i = (k + 9) % size; // slots 9..35 first, then the hotbar 0..8
            ItemStack s = items.get(i);
            if (!matches(s, ids, tags)) {
                continue;
            }
            int t = Math.min(left, s.getCount());
            s.shrink(t);
            if (s.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
            }
            left -= t;
        }
        if (left != n) {
            p.getInventory().setChanged();
            p.containerMenu.broadcastChanges();
        }
        return n - left;
    }

    // ------------------------------------------------------------------------------------------------- checks

    /**
     * The market's own check, with no world: a merchant in Hyderabad in 1948 pays 3.36 pai a sheaf of wheat (64 for
     * 1 rupee 1 anna 11 pai), half price after 64, nothing after 128, never beyond his day's money; the money-changer
     * gives 5 rupees 14 annas (Indian) for 7 O.S. rupees, 10 annas 9 pai for 3 old Mughal dams and 15 rupees 3 annas
     * 10 pai for a pound; money-changers stand only where there are merchants and money; a merchant's day is saved
     * and read back. Step 19g: a town trader pays 60 in 100 and has 4 days' pay, a pedlar 50 and 2, anybody swapping a
     * little 50 and 1; a town gets a trader, a village with money a pedlar, a village where people swap nobody (they
     * all swap a little); 16 sheets of paper made here fetch 40 pai in Hyderabad in 1948 (twice as much a sheet when
     * brought from far); a buyer in Bombay in 1948 takes the 10 goods and at least 10 atlas products. Null when all is
     * well.
     */
    public static String selfTest() {
        MoneyTable table = MoneyTable.get();
        GoodsTable goods = GoodsTable.get();
        MoneyTable.Money os = table.money("osmania_sicca");
        MoneyTable.Money inr = table.money("indian_rupee_anna");
        MoneyTable.Money mughal = table.money("mughal_rupee");
        MoneyTable.Money pound = table.money("pound_lsd");
        GoodsTable.Good wheat = goods.good("WHEAT");
        if (os == null || inr == null || mughal == null || pound == null || wheat == null) {
            return "the money table or the goods table lacks Osmania sicca, Indian or Mughal rupees, pounds or wheat";
        }
        MoneyTable.Here hyd = new MoneyTable.Here(new MoneyTable.Row("self_test", os, 1948, 1948, List.of(), List.of(inr),
                0), os, List.of(inr), 1948, 17.3616, 78.4747);
        Terms t = Terms.DEFAULT;
        double each = worth(wheat, hyd, basePrice("WHEAT")) * t.buyPercent() / 100.0d;
        long budget = budget(hyd, t);
        if (Math.abs(each - 3.36d) > 1e-9 || budget != 8 * 192) {
            return "a sheaf of wheat in Hyderabad in 1948 pays " + each + " pai and a merchant has " + budget
                    + " pai a day; expected 3.36 and 1536";
        }
        Offer a = offer(each, wheat.want(), 64, 0, budget);
        Offer b = offer(each, wheat.want(), 200, 0, budget);
        Offer c = offer(each, wheat.want(), 50, 0, 100);
        Offer d = offer(each, wheat.want(), 10, 128, budget);
        Offer e = offer(each, wheat.want(), 10, 0, 0);
        if (a.count() != 64 || a.amount() != 215 || b.count() != 128 || b.amount() != 322 || c.count() != 30
                || c.amount() != 100 || !Offer.ENOUGH.equals(d.reason()) || !Offer.BUDGET.equals(e.reason())) {
            return "wheat offers: 64 -> " + a + ", 200 -> " + b + ", 50 with 100 pai left -> " + c + ", after 128 -> " + d
                    + ", no money left -> " + e + "; expected 64 for 215, 128 for 322, 30 for 100, enough, budget";
        }
        String words = MoneyFormat.amount(os, a.amount(), 1948);
        if (!words.equals("1 rupee 1 anna 11 pai")) {
            return "215 pai reads \"" + words + "\"";
        }
        Swap s1 = swapOffer(table, os, 7 * 192, inr, 1948, t);
        Swap s2 = swapOffer(table, mughal, 75, os, 1948, t);
        Swap s3 = swapOffer(table, pound, 960, os, 1948, t);
        if (!s1.fixed() || s1.result() != 1128 || s2.fixed() || s2.result() != 129 || !s3.fixed() || s3.result() != 2926) {
            return "swaps: 7 O.S. rupees -> " + s1.result() + " pies (" + s1.fee() + "%), 3 dams -> " + s2.result()
                    + " pai (" + s2.fee() + "%), a pound -> " + s3.result() + " pai (" + s3.fee()
                    + "%); expected 1128 at 2%, 129 at 10%, 2926 at 2%";
        }
        String[] city = {"farmer", "artisan", "merchant", "citizen"};
        String[] london = withMoneyChanger(city, 51.507, -0.128, 1800);
        String[] sydney = withMoneyChanger(city, -33.87, 151.21, 1500);
        String[] village = withMoneyChanger(new String[] {"farmer"}, 51.507, -0.128, 1800);
        if (london.length != 5 || !MONEY_CHANGER.equals(london[3]) || sydney.length != 4 || village.length != 1) {
            return "money-changers: London 1800 " + String.join(",", london) + "; Sydney 1500 " + String.join(",", sydney)
                    + "; a village " + String.join(",", village);
        }
        MarketLedger ledger = new MarketLedger();
        UUID merchant = new UUID(19L, 2L);
        ledger.record(merchant, 100L, os.id(), "WHEAT", 64, 215L);
        MarketLedger back = MarketLedger.load(ledger.save(new CompoundTag()));
        MarketLedger.Day today = back.today(merchant, 100L, os.id());
        MarketLedger.Day tomorrow = back.today(merchant, 101L, os.id());
        if (today.spent() != 215L || today.sold("WHEAT") != 64 || tomorrow.spent() != 0L || tomorrow.sold("WHEAT") != 0) {
            return "a merchant's saved day came back as " + today.spent() + " pai and " + today.sold("WHEAT")
                    + " wheat (the next day " + tomorrow.spent() + ")";
        }
        return selfTest19g(table, hyd, t, city);
    }

    /** Step 19g's part of {@link #selfTest()}. */
    static String selfTest19g(MoneyTable table, MoneyTable.Here hyd, Terms t, String[] city) {
        Terms tr = t.as(TRADER);
        Terms pe = t.as(PEDLAR);
        Terms lo = t.as(LOCAL);
        if (!tr.equals(new Terms(60, 4, 10, 10)) || !pe.equals(new Terms(50, 2, 10, 10)) || !lo.equals(new Terms(50, 1, 10, 10))
                || t.as(MERCHANT) != t || t.as(MONEY_CHANGER) != t) {
            return "buyers' terms: trader " + tr + ", pedlar " + pe + ", anybody " + lo;
        }
        String[] town = withTrader(new String[] {"farmer", "artisan"}, SettlementRank.TOWN, 51.507, -0.128, 1800);
        String[] hamlet = withTrader(new String[] {"farmer"}, SettlementRank.VILLAGE, 51.507, -0.128, 1800);
        String[] camp = withTrader(new String[] {"farmer"}, SettlementRank.VILLAGE, -33.87, 151.21, 1500);
        String[] bronzeTown = withTrader(new String[] {"farmer", "artisan"}, SettlementRank.TOWN, 30.63, 72.86, -2500);
        String[] withMerchant = withTrader(city, SettlementRank.CITY, 51.507, -0.128, 1800);
        if (!Arrays.equals(town, new String[] {"farmer", "artisan", TRADER})
                || !Arrays.equals(hamlet, new String[] {"farmer", PEDLAR}) || camp.length != 1
                || !Arrays.equals(bronzeTown, new String[] {"farmer", "artisan", TRADER}) || withMerchant.length != city.length) {
            return "buyers of a place: a town in 1800 " + String.join(",", town) + "; a village in 1800 "
                    + String.join(",", hamlet) + "; a camp near Sydney in 1500 " + String.join(",", camp)
                    + "; a town in 2500 BCE " + String.join(",", bronzeTown) + "; a city " + String.join(",", withMerchant);
        }
        if (!swapsALittle(new String[] {"farmer"}, table.at(-33.87, 151.21, 1500))
                || swapsALittle(new String[] {"farmer", "artisan", TRADER}, table.at(30.63, 72.86, -2500))
                || swapsALittle(new String[] {"farmer"}, table.at(51.507, -0.128, 1800))) {
            return "anybody should swap a little near Sydney in 1500, but not where a trader stands or where people pay"
                    + " in money";
        }
        ProductAtlas.Product paper = ProductAtlas.get().product("paper");
        if (paper == null) {
            return "the product atlas lacks paper";
        }
        GoodsTable.Good made = atlasGood(new ProductAtlas.Answer(paper, ProductAtlas.Status.MADE_HERE, 105, ""));
        GoodsTable.Good far = atlasGood(new ProductAtlas.Answer(paper, ProductAtlas.Status.BROUGHT, 105, ""));
        if (made == null || far == null
                || atlasGood(new ProductAtlas.Answer(paper, ProductAtlas.Status.NOT_YET, 105, "")) != null) {
            return "paper should be bought where it is made or brought, and nowhere else";
        }
        double eachMade = pays(made, hyd, "market", t);
        double eachFar = pays(far, hyd, "market", t);
        Offer sheets = offer(eachMade, made.want(), 16, 0, budget(hyd, t));
        if (Math.abs(eachMade - 2.52d) > 1e-9 || Math.abs(eachFar - 5.04d) > 1e-9 || made.want() != 32
                || sheets.count() != 16 || sheets.amount() != 40) {
            return "paper bought back in Hyderabad in 1948: " + eachMade + " and " + eachFar + " pai a sheet, wants "
                    + made.want() + ", 16 sheets -> " + sheets + "; expected 2.52, 5.04, 32 and 16 for 40";
        }
        if (!"16 sheets of paper".equals(made.count(16)) || !"1 sheet of paper".equals(made.count(1))) {
            return "paper counts as \"" + made.count(16) + "\" and \"" + made.count(1) + "\"";
        }
        List<GoodsTable.Good> bombay = bought(18.94, 72.83, 1948);
        int atlas = 0;
        for (GoodsTable.Good g : bombay) {
            atlas += g.id().startsWith(ATLAS) ? 1 : 0;
        }
        if (bombay.size() - atlas != 10 || atlas < 10 || atlasGood(ATLAS + "paper", 18.94, 72.83, 1948) == null) {
            return "a buyer in Bombay in 1948 takes " + (bombay.size() - atlas) + " goods and " + atlas
                    + " atlas products; expected 10 and at least 10, paper among them";
        }
        return null;
    }
}
