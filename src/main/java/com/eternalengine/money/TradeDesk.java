package com.eternalengine.money;

import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.knowledge.TalkHold;
import com.eternalengine.knowledge.TalkService;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Step 24: one trade screen on the server: the trades of one townsperson with one player, what the two slots do for
 * the picked trade ({@link TradeRules}), and the trade itself when the player takes the thing from the result slot.
 *
 * <p>The trades: a seller's goods (bought with money, or swapped for goods where people have no money), the player's
 * goods a buyer takes (a lot at a time), and at a money-changer (or a town trader) any amount of one money for
 * another and bigger or smaller pieces. Clicking a trade puts the right things from the inventory into the slots, as
 * in Minecraft's trade screen; the player can also put anything there by hand.
 */
public final class TradeDesk {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** The trade screen closes beyond this many blocks (the talk's reach). */
    public static final double REACH = TalkHold.REACH;
    static final TradeView.Stack NONE = TradeView.Stack.NONE;

    final ServerPlayer player;
    final HumanNpcEntity npc;
    final ShopService.Seller seller;
    final MoneyTable table;
    final MoneyTable.Here here;
    final MarketService.Terms terms;
    final String role;
    final boolean sells;
    final boolean buys;
    final boolean changes;
    final boolean local;
    /** A counter role the shop does not stock (farmer, smith, doctor…): his era counter, on this screen. */
    final boolean era;
    final TalkService.Ctx ctx;
    final boolean barter;
    final boolean coinsAsMetal;
    final String title;
    final String changerWords;
    final List<Built> deals = new ArrayList<>();
    private final List<GoodsTable.Good> taken;
    String says;
    String status = "";
    int sound;
    String selectedId = "";
    boolean dirty = true;
    boolean quiet;

    /** A trade as the screen shows it: the rules' deal, its pictures, and what the player gets. */
    static final class Built {
        final TradeRules.Deal deal;
        TradeView.Stack cost1 = NONE;
        TradeView.Stack cost2 = NONE;
        boolean moreCost;
        TradeView.Stack result = NONE;
        boolean morePay;
        @Nullable ShopService.Lot lot;
        @Nullable ItemStack give;
        @Nullable GoodsTable.Good good;

        Built(TradeRules.Deal deal) {
            this.deal = deal;
        }

        TradeView.Row row() {
            return new TradeView.Row(deal.id, deal.kind, cost1, cost2, moreCost, result, morePay, deal.name, deal.priceText,
                    deal.lore, deal.left, deal.owned);
        }
    }

    private TradeDesk(ServerPlayer player, HumanNpcEntity npc, TalkService.Ctx c) {
        this.player = player;
        this.npc = npc;
        this.ctx = c;
        this.seller = TalkService.seller(c);
        this.table = MoneyTable.get();
        this.here = ShopService.here(seller);
        this.role = seller.roleKey();
        this.terms = TalkService.terms(c);
        this.sells = ShopService.sells(role);
        this.buys = MarketService.buyer(role);
        this.changes = MarketService.changes(role);
        this.local = !sells && !changes && TalkService.swapsALittle(c);
        this.era = !sells && !changes && !local && TalkService.counterRole(role) && !here.money().inDays();
        this.barter = here.money().barter();
        this.coinsAsMetal = barter && MarketService.TRADER.equals(role);
        this.taken = MarketService.bought(seller.lat(), seller.lon(), here.year());
        this.title = titleOf();
        this.changerWords = changes ? TalkService.changerWords(c) : "";
        MinecraftServer server = player.getServer();
        String start = server == null ? null
                : PurseService.startIfNew(PlayerPurses.get(server), player.getUUID(), here, KnowledgeDate.today());
        CoinPurse.of(player); // written money becomes pieces first
        String words = greeting();
        this.says = start == null ? words : start + " " + words;
    }

    /** The trade screen of this person for this player, or null when this person does not trade. */
    @Nullable
    public static TradeDesk create(ServerPlayer player, HumanNpcEntity npc) {
        TalkService.Ctx c = TalkService.context(player, npc);
        String role = c.role() == null ? "" : c.role().toLowerCase(Locale.ROOT);
        if (!ShopService.sells(role) && !MarketService.changes(role) && !TalkService.swapsALittle(c)) {
            // The old screen for every counter role (owner's choice): a counter
            // role in a coin land, with goods at his counter, trades here too.
            // In swap lands (no coins) the era panel still stands.
            boolean eraOk = false;
            if (TalkService.counterRole(role)) {
                MoneyTable.Here h = ShopService.here(TalkService.seller(c));
                com.eternalengine.knowledge.TradePanelView pv = h.money().inDays() ? null
                        : com.eternalengine.knowledge.EraTradePanel.panel(c, "");
                eraOk = pv != null && !pv.sell.isEmpty();
            }
            if (!eraOk) {
                return null;
            }
        }
        return new TradeDesk(player, npc, c);
    }

    public String title() {
        return title;
    }

    public int entityId() {
        return npc.getId();
    }

    private String titleOf() {
        if (local) {
            return "Swapping with " + seller.name();
        }
        if (MarketService.MONEY_CHANGER.equals(role)) {
            return seller.name() + ", money-changer";
        }
        return ShopService.title(seller);
    }

    private String greeting() {
        if (era) {
            com.eternalengine.knowledge.TradePanelView pv = com.eternalengine.knowledge.EraTradePanel.panel(ctx, "");
            if (pv != null && pv.said != null && !pv.said.isBlank()) {
                return pv.said + " Pick a trade on the left.";
            }
            return "Have a look. Pick a trade on the left.";
        }
        List<String> parts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (GoodsTable.Good g : taken) {
            names.add(g.name());
        }
        if (barter) {
            parts.add(local ? "I can swap a little." : ShopService.greeting(seller));
            if (!names.isEmpty()) {
                parts.add("For my things I take " + some(names) + ", at what I would give for them.");
            }
            if (coinsAsMetal) {
                parts.add("Coins are only metal here: I take them by what they buy, and keep "
                        + terms.strangeFeePercent() + " in every 100.");
            }
        } else {
            if (sells) {
                parts.add(ShopService.greeting(seller));
            }
            if (buys && !names.isEmpty()) {
                parts.add("I buy " + some(names) + ": full price for the first few of each, half for more.");
            }
            if (changes) {
                parts.add(changerWords);
            }
        }
        parts.add("Pick a trade on the left.");
        return String.join(" ", parts);
    }

    /** "paper, torches, iron axes, eggs and 9 more". */
    static String some(List<String> names) {
        if (names.size() <= 5) {
            return TradeRules.MarketTalkWords.list(names);
        }
        return String.join(", ", names.subList(0, 4)) + " and " + (names.size() - 4) + " more";
    }

    // ------------------------------------------------------------------------------------------- the trades

    /** All the trades again: the stock of today, the prices, what the player carries (the slots count too). */
    void rebuild(Container tray) {
        deals.clear();
        MinecraftServer server = player.getServer();
        Map<String, TreeMap<Long, Long>> pieces = heldPieces(tray, true);
        if (sells && !barter) {
            for (ShopService.Lot l : ShopService.lots(seller, player, server)) {
                deals.add(buyDeal(l));
            }
        }
        if (barter && (sells || local)) {
            List<ShopService.Lot> lots = sells ? ShopService.lots(seller, player, server) : localLots(server);
            for (ShopService.Lot l : lots) {
                deals.add(swapDeal(l, tray));
            }
        }
        if (buys && !barter) {
            sellDeals(tray);
        }
        if (changes && !barter) {
            changeDeals(pieces);
        }
        if (era) {
            eraDeals();
        }
        while (deals.size() > TradeView.MAX_ROWS) {
            deals.remove(deals.size() - 1);
        }
        dirty = true;
    }

    private List<ShopService.Lot> localLots(@Nullable MinecraftServer server) {
        MarketLedger.Day today = server == null ? null
                : MarketLedger.get(server).today(npc.getUUID(), MarketService.marketDay(server), here.money().id());
        return ShopService.atlasLots(seller, here, today, List.of(), 3, true);
    }

    private MoneyTable.Money moneyOf(TradeView.Offer o) {
        MoneyTable.Money m = o.money().isEmpty() ? null : table.money(o.money());
        return m == null ? here.money() : m;
    }

    private Built buyDeal(ShopService.Lot lot) {
        TradeView.Offer o = lot.offer();
        TradeRules.Deal d = new TradeRules.Deal(o.id(), TradeView.Kind.BUY);
        d.money = moneyOf(o);
        d.amount = o.price();
        d.name = o.name();
        d.lore = o.lore();
        d.priceText = o.priceText();
        d.left = o.left();
        d.owned = o.owned();
        Built b = new Built(d);
        b.lot = lot;
        b.give = lot.give().get();
        b.result = stack(b.give);
        ItemStack carried = player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
        if (!d.owned && !lot.counted() && !carried.isEmpty() && ItemStack.isSameItem(carried, b.give)) {
            d.owned = true; // a paper, a map or a book just bought is still in the hand, not yet in the bag
        }
        List<long[]> split = Denominations.split(TradeRules.pieces(table, d.money, here.year()), d.amount);
        if (!split.isEmpty()) {
            b.cost1 = pieceStack(d.money, split.get(0));
        }
        if (split.size() > 1) {
            b.cost2 = pieceStack(d.money, split.get(1));
        }
        b.moreCost = split.size() > 2;
        return b;
    }

    private Built swapDeal(ShopService.Lot lot, Container tray) {
        TradeView.Offer o = lot.offer();
        TradeRules.Deal d = new TradeRules.Deal(o.id(), TradeView.Kind.SWAP);
        d.money = here.money();
        d.amount = o.price();
        // His own good does not pay for itself (16 logs are not bought with logs).
        d.good = o.id().startsWith("good:") ? o.id().substring(5) : o.id().startsWith(MarketService.ATLAS) ? o.id() : "";
        d.name = o.name();
        d.lore = o.lore();
        d.priceText = o.priceText();
        d.left = o.left();
        d.owned = o.owned();
        Built b = new Built(d);
        b.lot = lot;
        b.give = lot.give().get();
        b.result = stack(b.give);
        // The pictures: what you could give from what you carry, else the first good he takes.
        List<Held> held = heldPayers(d);
        Tender.Pick pick = Tender.choose(kinds(held), Math.max(0L, d.amount - tab()));
        if (pick != null && pick.pieces() > 0) {
            b.cost1 = heldStack(held, pick.a(), pick.na());
            b.cost2 = pick.b() >= 0 ? heldStack(held, pick.b(), pick.nb()) : NONE;
        } else {
            for (GoodsTable.Good g : taken) {
                double each = MarketService.pays(g, here, seller.civ(), terms);
                ItemStack it = goodsItem(g);
                if (g.id().equals(d.good) || each <= 0 || it == null || ItemStack.isSameItem(it, b.give)) {
                    continue;
                }
                long n = Math.max(1L, (long) Math.ceil(d.amount / each - 1e-6));
                b.cost1 = stack(it.copyWithCount((int) Math.min(64, n)));
                b.moreCost = n > 64;
                break;
            }
        }
        return b;
    }

    private void sellDeals(Container tray) {
        MinecraftServer server = player.getServer();
        int year = here.year();
        MoneyTable.Money money = here.money();
        MarketLedger.Day day = server == null ? null
                : MarketLedger.get(server).today(npc.getUUID(), MarketService.marketDay(server), money.id());
        long left = MarketService.budget(here, terms) - (day == null ? 0L : day.spent());
        MoneyTable.Money small = TradeRules.smallChange(table, here);
        for (GoodsTable.Good g : taken) {
            int have = MarketService.carried(player, g) + inTray(tray, g);
            if (have <= 0) {
                continue;
            }
            double each = MarketService.pays(g, here, seller.civ(), terms);
            int q = ShopService.lotSize(each, money, year);
            int count = Math.min(q, have);
            int soldToday = day == null ? 0 : day.sold(g.id());
            MarketService.Offer o = MarketService.payable(MarketService.offer(each, g.want(), count, soldToday, left), money,
                    year);
            MoneyTable.Money payIn = money;
            if (small != null && (!o.ok() || o.amount() < TradeRules.SMALL_BELOW * Denominations.smallest(money, year))) {
                // Step 24: below a few manillas he pays in cowries, the small change of the place.
                double r = TradeRules.rate(table, money, small, year);
                MarketService.Offer os = MarketService.payable(MarketService.offer(each * r, g.want(), count, soldToday,
                        (long) Math.floor(left * r)), small, year);
                if (os.ok()) {
                    o = os;
                    payIn = small;
                }
            }
            int n = o.ok() ? o.count() : count;
            TradeRules.Deal d = new TradeRules.Deal("sell:" + g.id(), TradeView.Kind.SELL);
            d.money = payIn;
            d.amount = o.ok() ? o.amount() : 0L;
            d.good = g.id();
            d.count = n;
            d.goodWords = g.count(n);
            d.name = "Sell " + g.count(n);
            d.priceText = o.ok() ? MoneyFormat.amount(payIn, o.amount(), year) : "nothing today";
            d.lore = "He pays full price for the first few of each good every day, half for more, and no more than his"
                    + " day's money.";
            if (!o.ok()) {
                d.left = 0;
                d.refusal = switch (o.reason()) {
                    case MarketService.Offer.ENOUGH -> "I have enough " + g.name() + " for today. Come back tomorrow.";
                    case MarketService.Offer.BUDGET -> "I have paid out all I can today. Come back tomorrow.";
                    case MarketService.Offer.LITTLE -> "That is too little to pay for. Bring more.";
                    default -> "I don't buy that today.";
                };
            }
            Built b = new Built(d);
            b.good = g;
            ItemStack mine = firstOf(tray, s -> MarketService.isGood(s, g));
            if (mine == null) {
                mine = goodsItem(g);
            }
            if (mine != null) {
                b.cost1 = stack(mine.copyWithCount(Math.max(1, Math.min(mine.getMaxStackSize(), n))));
                b.moreCost = n > mine.getMaxStackSize();
            }
            if (o.ok()) {
                List<long[]> split = Denominations.split(TradeRules.pieces(table, payIn, year), d.amount);
                if (!split.isEmpty()) {
                    b.result = pieceStack(payIn, split.get(0));
                }
                b.morePay = split.size() > 1;
            }
            deals.add(b);
        }
    }

    /**
     * A counter role's era counter on the old screen: each of his sells is a
     * trade here — the word-price ("four annas") in coins of the place, the
     * good itself as the closest Minecraft item. Services (a consultation, a
     * passage) give a paper token: his written word for the service.
     */
    private void eraDeals() {
        com.eternalengine.knowledge.TradePanelView pv = com.eternalengine.knowledge.EraTradePanel.panel(ctx, "");
        if (pv == null) {
            return;
        }
        int year = here.year();
        for (int i = 0; i < pv.sell.size(); i++) {
            String good = pv.sell.get(i);
            if (good == null || good.isBlank()) {
                continue;
            }
            String price = i < pv.price.size() ? pv.price.get(i) : "";
            long amount = parsePrice(here.money(), price);
            if (amount <= 0) {
                continue;
            }
            TradeRules.Deal d = new TradeRules.Deal("era:" + role + ":" + i, TradeView.Kind.BUY);
            d.money = here.money();
            d.amount = amount;
            d.name = capGood(good);
            d.priceText = price == null || price.isBlank() ? MoneyFormat.amount(d.money, amount, year) : price;
            d.lore = "From his counter, at the price of the place and year.";
            d.left = -1;
            Built b = new Built(d);
            b.give = eraItem(good);
            b.result = stack(b.give);
            List<long[]> split = Denominations.split(TradeRules.pieces(table, d.money, year), d.amount);
            if (!split.isEmpty()) {
                b.cost1 = pieceStack(d.money, split.get(0));
            }
            if (split.size() > 1) {
                b.cost2 = pieceStack(d.money, split.get(1));
            }
            b.moreCost = split.size() > 2;
            deals.add(b);
        }
    }

    /** A word-price in the smallest units of {@code money} ("four annas", "half a shekel"); -1 when it cannot be read. */
    static long parsePrice(MoneyTable.Money money, String price) {
        if (money == null || price == null || price.isBlank()) {
            return -1L;
        }
        String[] tokens = MoneyFormat.fold(price).replace(',', ' ').trim().split("\\s+");
        MoneyTable.Unit unit = null;
        int unitAt = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (i + 1 < tokens.length) {
                unit = money.unitNamed(tokens[i] + " " + tokens[i + 1]);
                if (unit != null) {
                    unitAt = i;
                    break;
                }
            }
            unit = money.unitNamed(tokens[i]);
            if (unit == null && tokens[i].length() > 1 && tokens[i].endsWith("s")) {
                unit = money.unitNamed(tokens[i].substring(0, tokens[i].length() - 1));
            }
            if (unit != null) {
                unitAt = i;
                break;
            }
        }
        if (unit == null) {
            // The era's coin word is not a unit of this place's money (an old
            // name the table calls otherwise): the number in the price, in
            // the smallest pieces of the place. The words still show as-is.
            unitAt = tokens.length;
            long smallest = Long.MAX_VALUE;
            for (MoneyTable.Unit u : money.units()) {
                smallest = Math.min(smallest, u.size());
            }
            if (smallest == Long.MAX_VALUE) {
                return -1L;
            }
            double value = 0.0d, current = 0.0d;
            boolean seen = false;
            for (String t : tokens) {
                if (t.equals("and") || t.equals("of")) {
                    continue;
                }
                if (t.equals("a") || t.equals("an")) {
                    if (!seen) {
                        current += 1.0d;
                        seen = true;
                    }
                    continue;
                }
                Double frac = fractionWord(t);
                if (frac != null) {
                    current += frac;
                    seen = true;
                    continue;
                }
                long n = numberWord(t);
                if (n == 100L) {
                    current = (current == 0.0d ? 1.0d : current) * 100.0d;
                    seen = true;
                } else if (n == 1000L) {
                    value += (current == 0.0d ? 1.0d : current) * 1000.0d;
                    current = 0.0d;
                    seen = true;
                } else if (n > 0L) {
                    current += n;
                    seen = true;
                }
            }
            value += current;
            if (!seen || value <= 0.0d) {
                return -1L;
            }
            long amount = Math.round(value * (double) smallest);
            return amount > 0L ? amount : 1L;
        }
        double value = 0.0d, current = 0.0d;
        boolean seen = false;
        for (int i = 0; i < unitAt; i++) {
            String t = tokens[i];
            if (t.equals("and") || t.equals("of")) {
                continue;
            }
            if (t.equals("a") || t.equals("an")) {
                if (!seen) {
                    current += 1.0d;
                    seen = true;
                }
                continue;
            }
            Double frac = fractionWord(t);
            if (frac != null) {
                current += frac;
                seen = true;
                continue;
            }
            long n = numberWord(t);
            if (n == 100L) {
                current = (current == 0.0d ? 1.0d : current) * 100.0d;
                seen = true;
            } else if (n == 1000L) {
                value += (current == 0.0d ? 1.0d : current) * 1000.0d;
                current = 0.0d;
                seen = true;
            } else if (n > 0L) {
                current += n;
                seen = true;
            }
        }
        value += current;
        if (!seen || value <= 0.0d) {
            return -1L;
        }
        long amount = Math.round(value * (double) unit.size());
        return amount > 0L ? amount : 1L;
    }

    private static long numberWord(String t) {
        return switch (t) {
            case "one" -> 1L;
            case "two" -> 2L;
            case "three" -> 3L;
            case "four" -> 4L;
            case "five" -> 5L;
            case "six" -> 6L;
            case "seven" -> 7L;
            case "eight" -> 8L;
            case "nine" -> 9L;
            case "ten" -> 10L;
            case "eleven" -> 11L;
            case "twelve" -> 12L;
            case "thirteen" -> 13L;
            case "fourteen" -> 14L;
            case "fifteen" -> 15L;
            case "sixteen" -> 16L;
            case "seventeen" -> 17L;
            case "eighteen" -> 18L;
            case "nineteen" -> 19L;
            case "twenty" -> 20L;
            case "thirty" -> 30L;
            case "forty" -> 40L;
            case "fifty" -> 50L;
            case "sixty" -> 60L;
            case "seventy" -> 70L;
            case "eighty" -> 80L;
            case "ninety" -> 90L;
            case "hundred" -> 100L;
            case "thousand" -> 1000L;
            default -> {
                try {
                    yield Long.parseLong(t);
                } catch (NumberFormatException e) {
                    yield -1L;
                }
            }
        };
    }

    @Nullable
    private static Double fractionWord(String t) {
        if (t.equals("half")) {
            return 0.5d;
        }
        if (t.equals("quarter")) {
            return 0.25d;
        }
        int dash = t.indexOf('-');
        if (dash <= 0) {
            return null;
        }
        long num = numberWord(t.substring(0, dash));
        double den = switch (t.substring(dash + 1)) {
            case "third", "thirds" -> 3.0d;
            case "quarter", "quarters" -> 4.0d;
            case "fifth", "fifths" -> 5.0d;
            case "eighth", "eighths" -> 8.0d;
            case "tenth", "tenths" -> 10.0d;
            default -> 0.0d;
        };
        return num > 0L && den > 0.0d ? num / den : null;
    }

    private static String capGood(String good) {
        String s = good == null ? "" : good.strip();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The closest Minecraft item for an era good; a paper token where the good is a service or has no item. */
    private static ItemStack eraItem(String good) {
        String s = " " + (good == null ? "" : good.toLowerCase(Locale.ROOT)) + " ";
        String id = "paper";
        if (has(s, "plough", "sickle", "hoe")) id = "iron_hoe";
        else if (has(s, "axe")) id = "iron_axe";
        else if (has(s, "knife", "knives")) id = "iron_sword";
        else if (has(s, "spike", "horseshoe", "needle")) id = "iron_nugget";
        else if (has(s, "tool", "anchor")) id = "iron_pickaxe";
        else if (has(s, "spear")) id = "trident";
        else if (has(s, "arrow", "cartridge")) id = "arrow";
        else if (has(s, "goat")) id = "goat_spawn_egg";
        else if (has(s, "sheep", "lamb")) id = "sheep_spawn_egg";
        else if (has(s, "pig")) id = "pig_spawn_egg";
        else if (has(s, "horse", "oxen", "ox")) id = "horse_spawn_egg";
        else if (has(s, "turkey", "fowl")) id = "chicken";
        else if (has(s, "fish")) id = "cod";
        else if (has(s, "bread", "provisions")) id = "bread";
        else if (has(s, "wheat", "barley", "grain", "rice", "maize", "millet")) id = "wheat";
        else if (has(s, "seed")) id = "wheat_seeds";
        else if (has(s, "fodder")) id = "hay_block";
        else if (has(s, "fruit", "dates")) id = "apple";
        else if (has(s, "honey")) id = "honey_bottle";
        else if (has(s, "wine", "drink", "medicine")) id = "potion";
        else if (has(s, "oil", "kerosene")) id = "honey_bottle";
        else if (has(s, "salt")) id = "sugar";
        else if (has(s, "coffee", "cocoa")) id = "cocoa_beans";
        else if (has(s, "cotton", "cloth", "linen", "silk", "wool")) id = "white_wool";
        else if (has(s, "thread", "yarn", "rope", "string")) id = "string";
        else if (has(s, "leather")) id = "leather";
        else if (has(s, "pottery")) id = "flower_pot";
        else if (has(s, "glass")) id = "glass";
        else if (has(s, "lamp", "lantern")) id = "lantern";
        else if (has(s, "candle")) id = "candle";
        else if (has(s, "brick", "tile")) id = "bricks";
        else if (has(s, "concrete")) id = "white_concrete";
        else if (has(s, "cement")) id = "gravel";
        else if (has(s, "timber")) id = "oak_log";
        else if (has(s, "bamboo")) id = "bamboo";
        else if (has(s, "book")) id = "book";
        else if (has(s, "paper", "papyrus", "scroll")) id = "paper";
        else if (has(s, "ink")) id = "ink_sac";
        else if (has(s, "dye")) id = "red_dye";
        else if (has(s, "charcoal")) id = "charcoal";
        else if (has(s, "coal")) id = "coal";
        else if (has(s, "copper")) id = "copper_ingot";
        else if (has(s, "tin", "steel")) id = "iron_ingot";
        else if (has(s, "silver")) id = "iron_ingot";
        else if (has(s, "gold")) id = "gold_ingot";
        else if (has(s, "iron")) id = "iron_ingot";
        else if (has(s, "pearl")) id = "ender_pearl";
        else if (has(s, "shell")) id = "nautilus_shell";
        else if (has(s, "ivory")) id = "bone";
        else if (has(s, "herb")) id = "fern";
        else if (has(s, "mushroom")) id = "red_mushroom";
        else if (has(s, "gourd", "pumpkin")) id = "pumpkin";
        else if (has(s, "sand")) id = "sand";
        else if (has(s, "clay")) id = "clay";
        else if (has(s, "stone")) id = "stone";
        else if (has(s, "tobacco", "tea")) id = "paper";
        net.minecraft.world.item.Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", id));
        ItemStack stack = new ItemStack(it == null ? Items.PAPER : it);
        boolean bulk = has(s, "wheat", "barley", "grain", "rice", "maize", "millet", "cotton", "coal", "charcoal", "salt", "seed", "fodder");
        if (bulk && stack.getMaxStackSize() > 1) {
            stack.setCount(16);
        }
        return stack;
    }

    private static boolean has(String s, String... words) {
        for (String w : words) {
            if (s.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private void changeDeals(Map<String, TreeMap<Long, Long>> pieces) {
        int year = here.year();
        MoneyTable.Money main = here.money();
        for (Map.Entry<String, TreeMap<Long, Long>> e : pieces.entrySet()) {
            MoneyTable.Money m = table.money(e.getKey());
            if (m == null || m.inDays()) {
                continue;
            }
            if (m.id().equals(main.id())) {
                for (MoneyTable.Money a : here.accepts()) {
                    if (!a.inDays() && !a.id().equals(main.id()) && !TradeRules.pieces(table, a, year).isEmpty()) {
                        deals.add(changeDeal(m, a, e.getValue()));
                    }
                }
            } else {
                deals.add(changeDeal(m, main, e.getValue()));
            }
        }
        for (Map.Entry<String, TreeMap<Long, Long>> e : pieces.entrySet()) {
            MoneyTable.Money m = table.money(e.getKey());
            if (m == null || m.inDays() || !here.takes(m.id())) {
                continue;
            }
            deals.add(regroupDeal(m, e.getValue(), true));
            deals.add(regroupDeal(m, e.getValue(), false));
        }
    }

    private Built changeDeal(MoneyTable.Money from, MoneyTable.Money to, TreeMap<Long, Long> held) {
        int year = here.year();
        TradeRules.Deal d = new TradeRules.Deal("change:" + from.id() + ">" + to.id(), TradeView.Kind.CHANGE);
        d.from = from;
        d.money = to;
        long have = 0L;
        for (Map.Entry<Long, Long> e : held.entrySet()) {
            have += e.getKey() * e.getValue();
        }
        MarketService.Swap s = MarketService.swapOffer(table, from, have, to, year, terms);
        d.name = to.id().equals(here.money().id()) ? "Change your " + from.name() : "Change into " + to.name();
        d.priceText = s.result() > 0 ? "all your " + MoneyFormat.amount(from, have, year) + " for "
                + PurseService.named(to, s.result(), year) : "too little to change";
        boolean old = ShopService.old(table, from, year);
        d.lore = "Put in as much as you want to change. He keeps " + s.fee() + " in every 100"
                + (s.fixed() ? ", at the fixed rate." : old ? ": old money goes by what it bought in its day."
                : ": money from far away goes by what it buys.");
        Built b = new Built(d);
        List<Map.Entry<Long, Long>> big = new ArrayList<>(held.descendingMap().entrySet());
        if (!big.isEmpty()) {
            b.cost1 = heldPiece(from, big.get(0).getKey(), Math.min(64L, big.get(0).getValue()));
        }
        if (big.size() > 1) {
            b.cost2 = heldPiece(from, big.get(1).getKey(), Math.min(64L, big.get(1).getValue()));
        }
        b.moreCost = big.size() > 2;
        if (s.result() > 0) {
            List<long[]> split = Denominations.split(TradeRules.pieces(table, to, year), s.result());
            if (!split.isEmpty()) {
                b.result = pieceStack(to, split.get(0));
            }
            b.morePay = split.size() > 1;
        }
        return b;
    }

    private Built regroupDeal(MoneyTable.Money m, TreeMap<Long, Long> held, boolean make) {
        int year = here.year();
        TradeRules.Deal d = new TradeRules.Deal((make ? "make:" : "break:") + m.id(),
                make ? TradeView.Kind.MAKE : TradeView.Kind.BREAK);
        d.from = m;
        d.money = m;
        boolean main = m.id().equals(here.money().id());
        d.name = (make ? "Bigger pieces" : "Smaller pieces") + (main ? "" : " (" + m.name() + ")");
        d.priceText = "the same money, for nothing";
        d.lore = make ? "Put in small pieces; he gives the same money in as few pieces as he can."
                : "Put in a piece; he gives the same money in smaller pieces.";
        Built b = new Built(d);
        List<Denominations.Piece> ps = TradeRules.pieces(table, m, year);
        if (held.isEmpty()) {
            return b;
        }
        if (make) {
            long v = held.firstKey();
            long n = Math.min(64L, held.get(v));
            b.cost1 = heldPiece(m, v, n);
            List<long[]> split = Denominations.split(ps, v * n);
            if (!split.isEmpty()) {
                b.result = pieceStack(m, split.get(0));
            }
            b.morePay = split.size() > 1;
        } else {
            long v = held.lastKey();
            b.cost1 = heldPiece(m, v, 1L);
            List<Denominations.Piece> smaller = new ArrayList<>();
            for (Denominations.Piece p : ps) {
                if (p.value() < v) {
                    smaller.add(p);
                }
            }
            List<long[]> split = Denominations.split(smaller, v);
            if (!split.isEmpty()) {
                b.result = pieceStack(m, split.get(0));
            }
            b.morePay = split.size() > 1;
        }
        return b;
    }

    // --------------------------------------------------------------------------------------- the slots

    @Nullable
    Built selected() {
        for (Built b : deals) {
            if (b.deal.id.equals(selectedId)) {
                return b;
            }
        }
        return null;
    }

    int selectedIndex() {
        for (int i = 0; i < deals.size(); i++) {
            if (deals.get(i).deal.id.equals(selectedId)) {
                return i;
            }
        }
        return -1;
    }

    TradeRules.Ctx rules() {
        MinecraftServer server = player.getServer();
        Map<String, Long> written = server == null ? Map.of() : PlayerPurses.get(server).view(player.getUUID()).money();
        return new TradeRules.Ctx(table, here, terms, coinsAsMetal, written, heldPieces(null, false));
    }

    private long tab() {
        MinecraftServer server = player.getServer();
        return server == null ? 0L : Math.max(0L, PlayerPurses.get(server).view(player.getUUID()).amount(here.money().id()));
    }

    @Nullable
    private GoodsTable.Good goodOf(ItemStack s) {
        for (GoodsTable.Good g : taken) {
            if (MarketService.isGood(s, g)) {
                return g;
            }
        }
        return null;
    }

    /** The two slots as the rules see them. */
    List<TradeRules.Lump> lumps(Container tray) {
        List<TradeRules.Lump> out = new ArrayList<>();
        for (int slot = 0; slot < 2; slot++) {
            ItemStack s = tray.getItem(slot);
            if (s.isEmpty()) {
                continue;
            }
            if (MoneyItem.isMoney(s)) {
                out.add(TradeRules.Lump.money(slot, MoneyItem.moneyId(s), MoneyItem.value(s), s.getCount()));
                continue;
            }
            GoodsTable.Good g = goodOf(s);
            if (g != null) {
                out.add(TradeRules.Lump.goods(slot, g.id(), MarketService.pays(g, here, seller.civ(), terms), s.getCount(),
                        s.getHoverName().getString()));
            } else {
                out.add(TradeRules.Lump.other(slot, s.getCount(), s.getHoverName().getString()));
            }
        }
        return out;
    }

    /** The slots or the pick changed: what the result slot shows now, and the words under the slots. */
    void update(TradeMenu menu) {
        if (quiet) {
            return;
        }
        Built b = selected();
        if (b == null) {
            status = deals.isEmpty() ? "There is nothing to trade with " + seller.name() + " now."
                    : "Pick a trade on the left.";
            menu.showResult(ItemStack.EMPTY);
        } else {
            TradeRules.Outcome o = TradeRules.judge(b.deal, lumps(menu.tray()), rules());
            status = o.words();
            menu.showResult(o.ok() ? resultStack(b, o) : ItemStack.EMPTY);
        }
        dirty = true;
    }

    private ItemStack resultStack(Built b, TradeRules.Outcome o) {
        if (b.deal.kind == TradeView.Kind.BUY || b.deal.kind == TradeView.Kind.SWAP) {
            return b.give == null ? ItemStack.EMPTY : b.give.copy();
        }
        if (o.give().isEmpty() || o.giveMoney() == null) {
            return ItemStack.EMPTY;
        }
        long[] first = o.give().get(0);
        return pieceItem(o.giveMoney(), first[0], (int) Math.min(64L, first[1]));
    }

    /** True when the result can be taken now (the slots still pay for the picked trade). */
    boolean canTake(TradeMenu menu) {
        Built b = selected();
        return b != null && TradeRules.judge(b.deal, lumps(menu.tray()), rules()).ok();
    }

    /** The player clicked a trade: the slots are emptied into the inventory and filled for it, as in Minecraft. */
    void select(TradeMenu menu, int index) {
        if (index < 0 || index >= deals.size()) {
            return;
        }
        String id = deals.get(index).deal.id;
        TalkHold.hold(player, npc);
        quiet = true;
        try {
            menu.emptyTray();
            rebuild(menu.tray()); // what the player carries may have changed since the list was made
            selectedId = id;
            Built b = selected();
            Container tray = menu.tray();
            if (b != null && tray.getItem(0).isEmpty() && tray.getItem(1).isEmpty()) {
                fill(tray, b);
            }
        } finally {
            quiet = false;
        }
        update(menu);
    }

    /** Puts what the trade needs from the inventory into the empty slots. */
    private void fill(Container tray, Built b) {
        Inventory inv = player.getInventory();
        TradeRules.Deal d = b.deal;
        switch (d.kind) {
            case BUY, SWAP -> {
                if (d.owned || d.left == 0) {
                    return;
                }
                List<Held> held = heldPayers(d);
                long need = d.amount - (d.money.inDays() ? tab() : 0L);
                Tender.Pick pick = Tender.choose(kinds(held), need);
                if (pick == null || pick.pieces() == 0) {
                    return;
                }
                Held a = held.get(pick.a());
                move(inv, tray, 0, a.match(), (int) pick.na());
                if (pick.b() >= 0) {
                    // A second kind, or more of the same kind than one slot holds: the second slot.
                    move(inv, tray, 1, held.get(pick.b()).match(), (int) pick.nb());
                }
            }
            case SELL -> {
                if (b.good == null || d.left == 0) {
                    return;
                }
                GoodsTable.Good g = b.good;
                int moved = move(inv, tray, 0, s -> MarketService.isGood(s, g), d.count);
                if (moved < d.count) {
                    move(inv, tray, 1, s -> MarketService.isGood(s, g), d.count - moved);
                }
            }
            case CHANGE -> {
                TreeMap<Long, Long> held = heldPieces(null, false).getOrDefault(d.from.id(), new TreeMap<>());
                int slot = 0;
                for (Long v : held.descendingKeySet()) {
                    if (slot > 1) {
                        break;
                    }
                    String id = d.from.id();
                    if (move(inv, tray, slot, s -> MoneyItem.isMoney(s) && MoneyItem.moneyId(s).equals(id)
                            && MoneyItem.value(s) == v, 64) > 0) {
                        slot++;
                    }
                }
            }
            case MAKE, BREAK -> {
                TreeMap<Long, Long> held = heldPieces(null, false).getOrDefault(d.from.id(), new TreeMap<>());
                if (held.isEmpty()) {
                    return;
                }
                String id = d.from.id();
                if (d.kind == TradeView.Kind.BREAK) {
                    long v = held.lastKey();
                    move(inv, tray, 0, s -> MoneyItem.isMoney(s) && MoneyItem.moneyId(s).equals(id) && MoneyItem.value(s) == v, 1);
                } else {
                    long v = held.firstKey();
                    move(inv, tray, 0, s -> MoneyItem.isMoney(s) && MoneyItem.moneyId(s).equals(id) && MoneyItem.value(s) == v, 64);
                }
            }
        }
    }

    /** The player took the thing from the result slot: the trade happens (the slots pay, change comes back). */
    void take(TradeMenu menu) {
        Built b = selected();
        Container tray = menu.tray();
        TradeRules.Outcome o = b == null ? null : TradeRules.judge(b.deal, lumps(tray), rules());
        if (o == null || !o.ok()) {
            LOGGER.warn("[EE market] {} took a trade result that no longer pays ({})", player.getGameProfile().getName(),
                    b == null ? "no trade" : b.deal.id);
            return;
        }
        MinecraftServer server = player.getServer();
        int year = here.year();
        String paid = o.purse() ? b.deal.priceText : TradeRules.handed(paidLumps(tray, o), null, rules());
        if (o.purse() && !CoinPurse.of(player).pay(b.deal.money.id(), b.deal.amount, year)) {
            // The rules said the purse pays; it did not (it cannot happen while nothing else moves the coins).
            LOGGER.warn("[EE market] {}: the purse did not pay {} for {}", player.getGameProfile().getName(),
                    b.deal.amount, b.deal.id);
            return;
        }
        quiet = true;
        try {
            for (int slot = 0; slot < 2; slot++) {
                long n = o.take(slot);
                if (n > 0) {
                    ItemStack s = tray.getItem(slot);
                    s.shrink((int) Math.min(n, s.getCount()));
                    if (s.isEmpty()) {
                        tray.setItem(slot, ItemStack.EMPTY);
                    }
                }
            }
            List<long[]> rest = new ArrayList<>(o.give());
            if (b.deal.kind != TradeView.Kind.BUY && b.deal.kind != TradeView.Kind.SWAP && !rest.isEmpty()) {
                long[] first = rest.get(0);
                long inSlot = Math.min(64L, first[1]);
                if (first[1] > inSlot) {
                    rest.set(0, new long[] {first[0], first[1] - inSlot});
                } else {
                    rest.remove(0);
                }
            }
            if (o.giveMoney() != null) {
                hand(o.giveMoney(), rest);
            }
            if (o.tab() != 0 && server != null) {
                PlayerPurses.get(server).add(player.getUUID(), here.money().id(), o.tab());
            }
            says = receipt(b, o, paid, year);
            sound = 1;
            rebuild(tray);
        } finally {
            quiet = false;
        }
        tray.setChanged();
        update(menu);
        player.getInventory().setChanged();
    }

    /** The lumps the payment took from, with the counts taken (for the words). */
    private List<TradeRules.Lump> paidLumps(Container tray, TradeRules.Outcome o) {
        List<TradeRules.Lump> out = new ArrayList<>();
        for (TradeRules.Lump l : lumps(tray)) {
            long n = o.take(l.slot());
            if (n > 0) {
                out.add(new TradeRules.Lump(l.slot(), l.money(), l.value(), l.good(), l.worth(), Math.min(n, l.count()),
                        l.name()));
            }
        }
        return out;
    }

    private String receipt(Built b, TradeRules.Outcome o, String paid, int year) {
        MinecraftServer server = player.getServer();
        TradeRules.Deal d = b.deal;
        String name = player.getGameProfile().getName();
        switch (d.kind) {
            case BUY, SWAP -> {
                String words = b.lot == null ? "Done. " + d.name + " is yours." : ShopService.sold(player, seller, b.lot, paid);
                if (d.kind == TradeView.Kind.SWAP) {
                    boolean gave = !"nothing".equals(paid) && !paid.isBlank();
                    words += (gave ? " You gave " + paid + "." : "") + (o.tab() > 0 ? " He owes you "
                            + MoneyFormat.amount(d.money, o.tab(), year) + "." : o.tab() < 0 ? " Your tab paid "
                            + MoneyFormat.amount(d.money, -o.tab(), year) + "." : "");
                } else if (o.given() > 0 && o.giveMoney() != null) {
                    words += " Your change: " + MoneyFormat.amount(o.giveMoney(), o.given(), year) + ".";
                }
                return words;
            }
            case SELL -> {
                long paidInMain = d.money.id().equals(here.money().id()) ? d.amount
                        : (long) Math.ceil(d.amount / Math.max(1e-12, TradeRules.rate(table, here.money(), d.money, year)));
                if (server != null) {
                    MarketLedger.get(server).record(npc.getUUID(), MarketService.marketDay(server), here.money().id(), d.good,
                            d.count, paidInMain);
                }
                String words = "Sold " + d.goodWords + " for " + MoneyFormat.amount(d.money, d.amount, year) + ".";
                LOGGER.info("[EE market] {} sold {} {} to buyer {} for {} {} ({}) in {}, at {}, {}", name, d.count, d.good,
                        npc.getUUID(), d.amount, d.money.id(), MoneyFormat.amount(d.money, d.amount, year), year,
                        String.format(Locale.ROOT, "%.4f", here.lat()), String.format(Locale.ROOT, "%.4f", here.lon()));
                journal(words);
                return words;
            }
            case CHANGE -> {
                String words = "Changed " + paid + " into " + PurseService.named(d.money, o.given(), year) + ".";
                LOGGER.info("[EE market] {} changed {} into {} {} ({} in 100 kept) in {}", name, paid, o.given(),
                        d.money.id(), table.fixed(d.from.id(), d.money.id(), year) ? terms.feePercent()
                                : terms.strangeFeePercent(), year);
                journal(words);
                return words;
            }
            default -> {
                return "Here you are: " + o.words();
            }
        }
    }

    private void journal(String words) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            com.eternalengine.knowledge.BookService.journal(server, player.getUUID(), KnowledgeDate.today(), words, "");
        }
    }

    /** Pieces into the inventory (dropped at the feet when it is full). */
    private void hand(MoneyTable.Money m, List<long[]> pieces) {
        for (long[] vn : pieces) {
            long n = vn[1];
            while (n > 0) {
                int c = (int) Math.min(64L, n);
                ItemStack s = pieceItem(m, vn[0], c);
                if (s.isEmpty()) {
                    break;
                }
                player.getInventory().add(s);
                if (!s.isEmpty()) {
                    player.drop(s, false);
                }
                n -= c;
            }
        }
    }

    // ------------------------------------------------------------------------------------------- the view

    /** What the screen shows now. */
    public TradeView view(int containerId, Container tray) {
        TradeView v = new TradeView();
        v.entityId = npc.getId();
        v.containerId = containerId;
        v.npcName = seller.name();
        v.title = title;
        v.job = role.replace('_', ' ');
        v.dateLabel = KnowledgeDate.label(seller.date());
        v.says = says;
        v.status = status;
        v.purse = purseLine(tray);
        v.selected = selectedIndex();
        v.sound = sound;
        sound = 0;
        for (Built b : deals) {
            v.rows.add(b.row());
        }
        dirty = false;
        return v;
    }

    private String purseLine(Container tray) {
        PlayerPurses.Purse p = CoinPurse.of(player).view();
        for (Map.Entry<String, TreeMap<Long, Long>> e : heldPieces(tray, true).entrySet()) {
            long inTray = 0L;
            for (int slot = 0; slot < 2; slot++) {
                ItemStack s = tray.getItem(slot);
                if (MoneyItem.isMoney(s) && MoneyItem.moneyId(s).equals(e.getKey())) {
                    inTray += MoneyItem.value(s) * s.getCount();
                }
            }
            if (inTray > 0) {
                p.money.merge(e.getKey(), inTray, Long::sum);
            }
        }
        String words = PurseService.holdings(table, p, here.year());
        return "Your money: " + ("empty".equals(words) ? "none" : words);
    }

    /** True while the person is alive, in the same world and within reach. */
    public boolean valid(Player p) {
        return npc.isAlive() && !npc.isRemoved() && npc.level() == p.level() && npc.distanceToSqr(p) <= REACH * REACH;
    }

    /**
     * The screen closed. Back to the talk: nothing more. Closed by the player: the talk is over. Closed because the
     * person left or the player walked away: the talk box says so ("Lakshmi has left.").
     */
    void closed(Player p, boolean back) {
        if (back || !(p instanceof ServerPlayer sp)) {
            return;
        }
        if (!fake(sp) && !valid(sp)) {
            com.eternalengine.network.EternalEnginePacketHandler.sendToPlayer(
                    new com.eternalengine.network.packets.TalkViewPacket(TalkService.ended(sp.getUUID(), npc.getId(),
                            !gone())), sp);
            return;
        }
        TalkService.forget(sp.getUUID());
    }

    /** True when the person has gone (not only out of reach). */
    boolean gone() {
        return !npc.isAlive() || npc.isRemoved();
    }

    static boolean fake(Player p) {
        return p instanceof FakePlayer;
    }

    // ------------------------------------------------------------------------------------------- helpers

    /** Pieces of money held (the inventory, and the slots when given): money id to value to count. */
    Map<String, TreeMap<Long, Long>> heldPieces(@Nullable Container tray, boolean withTray) {
        Map<String, TreeMap<Long, Long>> out = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            add(out, inv.items.get(i));
        }
        if (withTray && tray != null) {
            for (int slot = 0; slot < 2; slot++) {
                add(out, tray.getItem(slot));
            }
        }
        return out;
    }

    private static void add(Map<String, TreeMap<Long, Long>> out, ItemStack s) {
        if (MoneyItem.isMoney(s)) {
            out.computeIfAbsent(MoneyItem.moneyId(s), k -> new TreeMap<>()).merge(MoneyItem.value(s), (long) s.getCount(),
                    Long::sum);
        }
    }

    /** One kind of thing in the inventory that can pay for a trade, and how to find it. */
    record Held(String key, double each, long count, int max, Predicate<ItemStack> match) {
        boolean same(Held o) {
            return o != null && key.equals(o.key);
        }
    }

    /** What in the inventory pays for this trade: money taken here (or goods he takes, and coins as metal). */
    private List<Held> heldPayers(TradeRules.Deal d) {
        List<Held> out = new ArrayList<>();
        Map<String, long[]> seen = new LinkedHashMap<>();
        Map<String, Predicate<ItemStack>> how = new LinkedHashMap<>();
        Map<String, Double> worth = new LinkedHashMap<>();
        Map<String, Integer> max = new LinkedHashMap<>();
        TradeRules.Ctx c = rules();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) {
                continue;
            }
            TradeRules.Lump l;
            String key;
            Predicate<ItemStack> match;
            if (MoneyItem.isMoney(s)) {
                String id = MoneyItem.moneyId(s);
                long v = MoneyItem.value(s);
                l = TradeRules.Lump.money(0, id, v, s.getCount());
                key = "m:" + id + ":" + v;
                match = x -> MoneyItem.isMoney(x) && MoneyItem.moneyId(x).equals(id) && MoneyItem.value(x) == v;
            } else {
                GoodsTable.Good g = goodOf(s);
                if (g == null) {
                    continue;
                }
                l = TradeRules.Lump.goods(0, g.id(), MarketService.pays(g, here, seller.civ(), terms), s.getCount(), "");
                ResourceLocation rl = ForgeRegistries.ITEMS.getKey(s.getItem());
                key = "g:" + g.id() + ":" + rl;
                ItemStack proto = s.copyWithCount(1);
                match = x -> ItemStack.isSameItemSameTags(x, proto);
            }
            double each = TradeRules.worthIn(l, d, c);
            if (each <= 0) {
                continue;
            }
            seen.computeIfAbsent(key, k -> new long[1])[0] += s.getCount();
            how.putIfAbsent(key, match);
            worth.putIfAbsent(key, each);
            max.putIfAbsent(key, s.getMaxStackSize());
        }
        for (Map.Entry<String, long[]> e : seen.entrySet()) {
            String k = e.getKey();
            long n = e.getValue()[0];
            int m = max.get(k);
            // A kind with more than a stack can fill both slots: it counts as two kinds of the same thing.
            out.add(new Held(k, worth.get(k), Math.min(n, m), m, how.get(k)));
            if (n > m) {
                out.add(new Held(k, worth.get(k), n - m, m, how.get(k)));
            }
        }
        return out;
    }

    private static List<Tender.Kind> kinds(List<Held> held) {
        List<Tender.Kind> out = new ArrayList<>();
        for (Held h : held) {
            out.add(new Tender.Kind(h.key(), h.each(), h.count(), h.max()));
        }
        return out;
    }

    private TradeView.Stack heldStack(List<Held> held, int i, long n) {
        if (i < 0 || i >= held.size()) {
            return NONE;
        }
        Inventory inv = player.getInventory();
        for (int k = 0; k < inv.items.size(); k++) {
            ItemStack s = inv.items.get(k);
            if (!s.isEmpty() && held.get(i).match().test(s)) {
                return stack(s.copyWithCount((int) Math.max(1L, Math.min(64L, n))));
            }
        }
        return NONE;
    }

    /** Moves up to {@code count} matching items from the inventory (main part first, then the hotbar) into a slot. */
    static int move(Inventory inv, Container tray, int slot, Predicate<ItemStack> want, int count) {
        int moved = 0;
        int size = Math.min(36, inv.items.size());
        for (int k = 0; k < size && moved < count; k++) {
            int i = (k + 9) % size;
            ItemStack s = inv.items.get(i);
            if (s.isEmpty() || !want.test(s)) {
                continue;
            }
            ItemStack in = tray.getItem(slot);
            if (!in.isEmpty() && !ItemStack.isSameItemSameTags(in, s)) {
                continue;
            }
            int room = in.isEmpty() ? s.getMaxStackSize() : in.getMaxStackSize() - in.getCount();
            int n = Math.min(Math.min(room, s.getCount()), count - moved);
            if (n <= 0) {
                continue;
            }
            if (in.isEmpty()) {
                tray.setItem(slot, s.copyWithCount(n));
            } else {
                in.grow(n);
            }
            s.shrink(n);
            if (s.isEmpty()) {
                inv.items.set(i, ItemStack.EMPTY);
            }
            moved += n;
        }
        if (moved > 0) {
            inv.setChanged();
        }
        return moved;
    }

    private static int inTray(Container tray, GoodsTable.Good g) {
        int n = 0;
        for (int slot = 0; slot < 2; slot++) {
            ItemStack s = tray.getItem(slot);
            if (!s.isEmpty() && MarketService.isGood(s, g)) {
                n += s.getCount();
            }
        }
        return n;
    }

    @Nullable
    private ItemStack firstOf(Container tray, Predicate<ItemStack> want) {
        for (int slot = 0; slot < 2; slot++) {
            ItemStack s = tray.getItem(slot);
            if (!s.isEmpty() && want.test(s)) {
                return s;
            }
        }
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) {
            if (!s.isEmpty() && want.test(s)) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    private static ItemStack goodsItem(GoodsTable.Good g) {
        net.minecraft.world.item.Item it = ShopService.itemOf(g);
        return it == null ? null : new ItemStack(it);
    }

    /** A piece of money as an item ({@code count} of it), or empty when the money has no piece of that value. */
    ItemStack pieceItem(MoneyTable.Money m, long value, int count) {
        for (Denominations.Piece p : TradeRules.pieces(table, m, here.year())) {
            if (p.value() == value) {
                return MoneyItem.make(m, p, Math.max(1, Math.min(64, count)));
            }
        }
        int[] span = Denominations.span(table, m);
        for (int y : new int[] {here.year(), span[1], (span[0] + span[1]) / 2, span[0]}) {
            for (Denominations.Piece p : Denominations.of(m, y)) {
                if (p.value() == value) {
                    return MoneyItem.make(m, p, Math.max(1, Math.min(64, count)));
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** A picture of pieces the player holds: one of the player's own pieces (struck in its own year), so it looks the same. */
    TradeView.Stack heldPiece(MoneyTable.Money m, long value, long count) {
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) {
            if (MoneyItem.isMoney(s) && MoneyItem.moneyId(s).equals(m.id()) && MoneyItem.value(s) == value) {
                TradeView.Stack one = stack(s);
                return new TradeView.Stack(one.item(), (int) Math.max(1L, Math.min(9999L, count)), one.tag());
            }
        }
        return pieceStack(m, new long[] {value, count});
    }

    /** A picture of {@code vn[1]} pieces of value {@code vn[0]} (the count may be above 64: the picture says so). */
    TradeView.Stack pieceStack(MoneyTable.Money m, long[] vn) {
        ItemStack s = pieceItem(m, vn[0], 1);
        if (s.isEmpty()) {
            return NONE;
        }
        TradeView.Stack one = stack(s);
        return new TradeView.Stack(one.item(), (int) Math.max(1L, Math.min(9999L, vn[1])), one.tag());
    }

    /** A picture of an item: its name, count and the data that names it (a coin's money, a renamed thing). */
    static TradeView.Stack stack(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) {
            return NONE;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(s.getItem());
        CompoundTag tag = null;
        CompoundTag all = s.getTag();
        if (all != null) {
            CompoundTag t = new CompoundTag();
            for (String k : new String[] {MoneyItem.TAG, "display"}) {
                if (all.contains(k)) {
                    t.put(k, all.get(k).copy());
                }
            }
            tag = t.isEmpty() ? null : t;
        }
        return new TradeView.Stack(key == null ? "minecraft:air" : key.toString(), s.getCount(), tag);
    }
}
