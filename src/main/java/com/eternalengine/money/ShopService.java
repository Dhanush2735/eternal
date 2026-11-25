package com.eternalengine.money;

import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.news.MapItem;
import com.eternalengine.news.MapService;
import com.eternalengine.news.NewsEras;
import com.eternalengine.news.NewsService;
import com.eternalengine.news.NewspaperItem;
import com.eternalengine.news.PaperView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Step 19c: sellers and what they sell. A merchant sells the goods he also buys, at full worth (he buys at 70%), a few
 * lots of each a day, and the local products of the place (the product atlas, part A: {@code economy/products.json});
 * a paper seller sells today's paper and a map (step 19d, 19e); before print, a newsletter writer sells a handwritten
 * newsletter and a map drawn by hand. Prices are in the money of the place.
 *
 * <p>Step 19f: sellers take only the money of the place, or a money the place also takes (at the fixed rate). Old
 * money (no longer used anywhere) and foreign money are refused, and the money-changer is named.
 *
 * <p>Step 19g: a town trader sells like a merchant, with fewer kinds of the atlas products; a village pedlar sells only
 * a few small things of the atlas, no bulk goods.
 */
public final class ShopService {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String RESOURCE = "/data/eternalengine/eternal/economy/products.json";
    /** Sellers ask the full worth; merchants buy at {@link MarketService.Terms#buyPercent()}. */
    public static final int SELL_PERCENT = 100;
    /** Lots of one local product a seller has in a Minecraft day. */
    public static final int PRODUCT_LOTS = 4;
    static final String OUT = "out:";
    /** Step 22: the offer id of a history book form ("book:papyrus"). */
    public static final String BOOK = "book:";

    /** A product card that sells (an item in a box and years), or gives a good's lot a local name ({@code good}). */
    public record Product(String id, String name, String shortName, String item, int count, double days,
                          List<String> sellers, int from, int to, double[] box, String lore, String good) {
        public boolean here(double lat, double lon, int year) {
            if (year < from || year > to) {
                return false;
            }
            return box == null || (!Double.isNaN(lat) && lat >= box[0] && lat <= box[1] && lon >= box[2] && lon <= box[3]);
        }

        public boolean soldBy(String role) {
            return sellers.contains(role == null ? "" : role.toLowerCase(Locale.ROOT));
        }
    }

    /** Who sells, where and when (uuid: the seller's, for his day's stock; null in checks). */
    public record Seller(String role, String name, int entityId, @Nullable UUID uuid, String civ, double lat, double lon,
                         int date) {
        public int year() {
            return KnowledgeDate.year(date);
        }

        String roleKey() {
            return role == null ? "" : role.toLowerCase(Locale.ROOT);
        }
    }

    /** An offer and what the buyer gets. */
    record Lot(TradeView.Offer offer, Supplier<ItemStack> give, boolean counted) {
    }

    private static volatile List<Product> products;
    private static volatile List<String> productProblems = List.of();

    private ShopService() {
    }

    // --------------------------------------------------------------------------------------------- the products

    public static List<Product> products() {
        List<Product> p = products;
        if (p == null) {
            synchronized (ShopService.class) {
                if (products == null) {
                    products = load();
                }
                p = products;
            }
        }
        return p;
    }

    /** Read the product cards again (/ee reload). */
    public static List<Product> reloadProducts() {
        synchronized (ShopService.class) {
            products = load();
            return products;
        }
    }

    public static List<String> productProblems() {
        return productProblems;
    }

    private static List<Product> load() {
        List<Product> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try (InputStream in = ShopService.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                problems.add("missing " + RESOURCE);
            } else {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("products")) {
                    JsonObject o = e.getAsJsonObject();
                    try {
                        List<String> sellers = new ArrayList<>();
                        if (o.has("sellers")) {
                            for (JsonElement s : o.getAsJsonArray("sellers")) {
                                sellers.add(s.getAsString().toLowerCase(Locale.ROOT));
                            }
                        }
                        double[] box = null;
                        if (o.has("box")) {
                            var b = o.getAsJsonArray("box");
                            box = new double[]{b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                                    b.get(3).getAsDouble()};
                        }
                        String name = str(o, "name");
                        out.add(new Product(str(o, "id"), name, o.has("short") ? str(o, "short") : name, str(o, "item"),
                                o.has("count") ? o.get("count").getAsInt() : 1, o.has("days") ? o.get("days").getAsDouble() : 0,
                                List.copyOf(sellers), o.has("from") ? o.get("from").getAsInt() : Integer.MIN_VALUE,
                                o.has("to") ? o.get("to").getAsInt() : Integer.MAX_VALUE, box, str(o, "lore"), str(o, "good")));
                    } catch (Exception ex) {
                        problems.add(str(o, "id") + ": " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            problems.add(RESOURCE + ": " + e);
        }
        productProblems = List.copyOf(problems);
        LOGGER.info("[Money] product cards: {}{}", out.size(), problems.isEmpty() ? "" : "; problems: " + problems);
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    // ------------------------------------------------------------------------------------------------ the offers

    /**
     * True for the roles that sell: the merchant, the paper seller and the newsletter writer (step 19g: and the town
     * trader and the village pedlar).
     */
    public static boolean sells(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return MarketService.buyer(r) || r.equals(NewsEras.PAPER_SELLER) || r.equals(NewsEras.NEWS_WRITER);
    }

    /** Step 19g: how many kinds of atlas products a seller of this role carries: merchant 8, trader 6, pedlar 4. */
    public static int atlasKinds(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case MarketService.TRADER -> 6;
            case MarketService.PEDLAR -> 4;
            default -> ProductAtlas.MERCHANT_MAX;
        };
    }

    static MoneyTable.Here here(Seller s) {
        return MoneyTable.get().at(s.lat(), s.lon(), s.year());
    }

    /**
     * A seller's price in round money: to the biggest coin no more than an eighth of the price (so it is off by at most
     * a sixteenth). 95 pies is 8 annas (96); 77 pai stays 6 annas 5 pai. Days of work and barter are not rounded.
     */
    public static long nice(long price, MoneyTable.Money money, int year) {
        if (price <= 0) {
            return 1L;
        }
        if (money.inDays() || money.barter()) {
            return price;
        }
        long step = 1L;
        for (MoneyTable.Unit u : money.unitsIn(year)) {
            if (u.size() <= price / 8 && u.size() > step) {
                step = u.size();
            }
        }
        long out = Math.max(step, Math.round(price / (double) step) * step);
        long coin = Denominations.smallest(money, year); // step 23b: a price people can pay in pieces
        return coin > 1 && out % coin != 0 ? Math.max(coin, Math.round(out / (double) coin) * coin) : out;
    }

    /** How many of a good make one lot: the same steps the merchant's price list uses (1, 16 or 64). */
    public static int lotSize(double each, MoneyTable.Money money, int year) {
        double threshold = money.inDays() ? money.dayPay(year) / 4.0d : Math.max(4.0d, Math.min(20.0d,
                money.dayPay(year) / 8.0d));
        return each >= threshold ? 1 : each * 16 >= threshold ? 16 : 64;
    }

    /** The item a good's lot is: its first item, or a plain one for a tag (oak logs, white wool). */
    @Nullable
    static Item itemOf(GoodsTable.Good g) {
        for (String id : g.items()) {
            if (id.startsWith("#")) {
                if (id.contains("log")) {
                    return Items.OAK_LOG;
                }
                if (id.contains("wool")) {
                    return Items.WHITE_WOOL;
                }
                continue;
            }
            Item it = item(id);
            if (it != null) {
                return it;
            }
        }
        return null;
    }

    @Nullable
    static Item item(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            return it == null || it == Items.AIR ? null : it;
        } catch (Throwable t) {
            return null;
        }
    }

    static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A product's local name for a good here (Singareni coal), or null. */
    @Nullable
    static Product localName(GoodsTable.Good g, double lat, double lon, int year) {
        for (Product p : products()) {
            if (g.id().equals(p.good()) && p.here(lat, lon, year) && !p.lore().isBlank()) {
                return p;
            }
        }
        return null;
    }

    static List<Lot> lots(Seller s, @Nullable ServerPlayer p, @Nullable MinecraftServer server) {
        List<Lot> out = new ArrayList<>();
        MoneyTable.Here here = here(s);
        MoneyTable.Money money = here.money();
        int year = s.year();
        String role = s.roleKey();
        long day = server == null ? 0L : MarketService.marketDay(server);
        MarketLedger.Day today = server == null || s.uuid() == null ? null
                : MarketLedger.get(server).today(s.uuid(), day, money.id());
        if (MarketService.buyer(role)) {
            // Step 19g: the pedlar carries no bulk goods (wheat, logs, stone): only small things of the atlas, below.
            List<GoodsTable.Good> bulk = role.equals(MarketService.PEDLAR) ? List.of()
                    : GoodsTable.get().boughtAt(s.lat(), s.lon(), year);
            for (GoodsTable.Good g : bulk) {
                Item it = itemOf(g);
                if (it == null) {
                    continue;
                }
                double each = MarketService.worth(g, here, MarketService.enginePrice(g.id(), s.civ())) * SELL_PERCENT / 100.0d;
                int q = lotSize(each, money, year);
                // Step 24: in round money of the place, or in its small change when it is cheap (cowries in Calabar).
                TradeRules.Price cost = TradeRules.price(each * q, MoneyTable.get(), here, true);
                int perDay = Math.max(1, g.want() / q);
                String id = "good:" + g.id();
                int left = Math.max(0, perDay - (today == null ? 0 : today.sold(OUT + id)));
                Product local = localName(g, s.lat(), s.lon(), year);
                String name = local != null ? q + " × " + local.name() : cap(g.some(q));
                String lore = (local != null ? local.lore() + " " : "") + "He has " + perDay + (perDay == 1 ? " lot" : " lots")
                        + " like this a day; he buys it back for less.";
                TradeView.Offer o = new TradeView.Offer(id, key(it), Math.min(64, q), name,
                        local != null ? local.shortName() : cap(g.name()), lore,
                        MoneyFormat.amount(cost.money(), cost.amount(), year), cost.amount(), left, false, cost.money().id());
                final Product localName = local;
                final int count = q;
                out.add(new Lot(o, () -> named(new ItemStack(it, count), localName), true));
            }
            out.addAll(bookLots(s, p, here)); // step 22: the history book, in the form people here keep notes in
            for (Product pr : products()) {
                if (pr.item().isBlank() || !pr.soldBy(role) || !pr.here(s.lat(), s.lon(), year)) {
                    continue;
                }
                Item it = item(pr.item());
                if (it == null || pr.days() <= 0) {
                    continue;
                }
                TradeRules.Price cost = TradeRules.price(pr.days() * here.dayPay(), MoneyTable.get(), here, false);
                String id = "prod:" + pr.id();
                int left = Math.max(0, PRODUCT_LOTS - (today == null ? 0 : today.sold(OUT + id)));
                String name = pr.count() > 1 ? pr.count() + " × " + pr.name() : pr.name();
                TradeView.Offer o = new TradeView.Offer(id, key(it), pr.count(), name, pr.shortName(), pr.lore(),
                        MoneyFormat.amount(cost.money(), cost.amount(), year), cost.amount(), left, false, cost.money().id());
                out.add(new Lot(o, () -> named(new ItemStack(it, Math.max(1, pr.count())), pr), true));
            }
            out.addAll(atlasLots(s, here, today, out));
        } else if (role.equals(NewsEras.PAPER_SELLER) || role.equals(NewsEras.NEWS_WRITER)) {
            boolean hand = role.equals(NewsEras.NEWS_WRITER);
            PaperView paper = NewsService.paper(s.lat(), s.lon(), s.date(), hand);
            long price = paperPrice(here, hand);
            String name = paper.name + ", " + KnowledgeDate.shortLabel(s.date()) + " " + NewsService.yearText(year);
            boolean own = p != null && hasPaper(p, paper.name, s.date());
            String lore = hand ? "Today's news, copied by hand for those who can pay. Its names go into your history book, "
                    + "and its places onto your map."
                    : "Today's news. Its names go into your history book, and its places onto your map.";
            out.add(new Lot(new TradeView.Offer("paper", hand ? "eternalengine:newsletter" : "eternalengine:newspaper", 1,
                    name, hand ? "Today's newsletter" : "Today's paper", lore, MoneyFormat.amount(money, price, year), price,
                    -1, own), () -> NewsService.item(paper), false));
            String title = MapService.title(s.lat(), s.lon(), s.date());
            long mapPrice = nice(Math.max(1L, Math.round(here.dayPay() * NewsEras.mapDays(!hand))), money, year);
            boolean ownMap = p != null && hasMap(p, title, year);
            String mapLore = (hand ? "Drawn by hand: " : "") + "Towns and main roads. It also marks the places from every "
                    + "paper you read while you carry it.";
            out.add(new Lot(new TradeView.Offer("map", "eternalengine:map", 1, title,
                    title.contains("State") ? "Map of the state" : "Map of the land", mapLore,
                    MoneyFormat.amount(money, mapPrice, year), mapPrice, -1, ownMap),
                    () -> MapService.item(s.lat(), s.lon(), s.date()), false));
            out.addAll(bookLots(s, p, here)); // step 22
        }
        return out;
    }

    /**
     * Step 22: the history book, in the forms this seller has here ({@link com.eternalengine.news.BookForms#soldBy}):
     * the price is its days of pay in the money of the place; one a player already carries in that form shows as
     * owned. Any copy reads the same notes.
     */
    static List<Lot> bookLots(Seller s, @Nullable ServerPlayer p, MoneyTable.Here here) {
        List<Lot> out = new ArrayList<>();
        int year = s.year();
        for (com.eternalengine.news.BookForms.Form f : com.eternalengine.news.BookForms.soldBy(s.roleKey(), s.lat(), s.lon(),
                year)) {
            Item it;
            try {
                it = com.eternalengine.news.NewsItems.book(f);
            } catch (Throwable t) {
                continue; // the items are not registered (a check outside the game)
            }
            TradeRules.Price cost = TradeRules.price(com.eternalengine.news.BookForms.days(f, year) * here.dayPay(),
                    MoneyTable.get(), here, false); // step 24: cowries where they are the small change
            boolean own = p != null && com.eternalengine.knowledge.BookService.carries(p, f);
            String lore = f.lore + (f.writing ? " Your history book: it keeps what people tell you and what you read."
                    : " Your history book, as people here keep it: what people tell you, and what you read.");
            out.add(new Lot(new TradeView.Offer(BOOK + f.id, key(it), 1, f.offerName(), "History book", lore,
                    MoneyFormat.amount(cost.money(), cost.amount(), year), cost.amount(), -1, own, cost.money().id()),
                    () -> com.eternalengine.knowledge.BookService.stack(f), false));
        }
        return out;
    }

    /** Step 22: a book's name in a sentence: "a papyrus roll (history book)", "wax tablets (history book)". */
    static String bookWords(String name) {
        if (name == null || name.isEmpty()) {
            return "a history book";
        }
        String low = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        String head = low.contains(" (") ? low.substring(0, low.indexOf(" (")) : low;
        if (head.endsWith("s")) {
            return low;
        }
        return ("aeiou".indexOf(head.charAt(0)) >= 0 ? "an " : "a ") + low;
    }

    /** Step 22: what a history book of this form costs here, in round money. */
    public static long bookPrice(com.eternalengine.news.BookForms.Form f, MoneyTable.Here here) {
        long raw = Math.max(1L, Math.round(com.eternalengine.news.BookForms.days(f, here.year()) * here.dayPay()));
        return nice(raw, here.money(), here.year());
    }

    /**
     * Product atlas, part B: what is made here or brought from far, for a merchant. One offer per item he does not sell
     * already, at most {@link ProductAtlas#MERCHANT_MAX} kinds (picked by who he is), {@link ProductAtlas#LOTS} lots a
     * day; brought from far costs {@link ProductAtlas#BROUGHT_MARKUP} times as much.
     */
    static List<Lot> atlasLots(Seller s, MoneyTable.Here here, @Nullable MarketLedger.Day today, List<Lot> already) {
        return atlasLots(s, here, today, already, atlasKinds(s.roleKey()), false);
    }

    /**
     * Step 24: the same with a number of kinds, and (for somebody who swaps a little where people have no money) only
     * what is made here.
     */
    static List<Lot> atlasLots(Seller s, MoneyTable.Here here, @Nullable MarketLedger.Day today, List<Lot> already,
                               int kinds, boolean madeOnly) {
        List<Lot> out = new ArrayList<>();
        int year = s.year();
        java.util.Set<String> shown = new java.util.HashSet<>();
        for (Lot l : already) {
            shown.add(l.offer().item());
        }
        List<ProductAtlas.Answer> sale = new ArrayList<>();
        // Step 19g: a trader and a pedlar sell what a merchant sells, fewer kinds.
        boolean pack = MarketService.PEDLAR.equals(s.roleKey());
        for (ProductAtlas.Answer a : ProductAtlas.get().forSale(MarketService.MERCHANT, s.lat(), s.lon(), year)) {
            if (pack && heavy(a.product())) {
                continue; // a pedlar carries small things: no bricks, no saddles
            }
            if (madeOnly && (a.status() == ProductAtlas.Status.BROUGHT || heavy(a.product()))) {
                continue; // step 24: a farmer who swaps a little gives what he makes himself
            }
            Item it = item(a.product().item());
            if (it != null && !shown.contains(key(it))) {
                sale.add(a);
            }
        }
        long salt = s.uuid() == null ? s.name().hashCode() : s.uuid().getLeastSignificantBits();
        int room = Math.min(kinds, TradeView.MAX_OFFERS - already.size());
        for (ProductAtlas.Answer a : ProductAtlas.carried(sale, salt, Math.max(0, room))) {
            ProductAtlas.Product pr = a.product();
            Item it = item(pr.item());
            if (it == null || !shown.add(key(it))) {
                continue;
            }
            boolean far = a.status() == ProductAtlas.Status.BROUGHT;
            TradeRules.Price cost = TradeRules.price(pr.days() * here.dayPay() * (far ? ProductAtlas.BROUGHT_MARKUP : 1),
                    MoneyTable.get(), here, false);
            String id = "atlas:" + pr.id();
            int left = Math.max(0, ProductAtlas.LOTS - (today == null ? 0 : today.sold(OUT + id)));
            String name = pr.count() > 1 ? pr.count() + " × " + pr.shortName() : pr.shortName();
            String lore = (far ? "Brought from far away, so it costs more." : "Made here.") + " He has " + ProductAtlas.LOTS
                    + " lots like this a day.";
            TradeView.Offer o = new TradeView.Offer(id, key(it), pr.count(), name, pr.shortName(), lore,
                    MoneyFormat.amount(cost.money(), cost.amount(), year), cost.amount(), left, false, cost.money().id());
            out.add(new Lot(o, () -> {
                ItemStack st = new ItemStack(it, Math.max(1, pr.count()));
                if (pr.rename()) {
                    st.setHoverName(Component.literal(pr.shortName()).withStyle(x -> x.withItalic(false)));
                }
                return st;
            }, true));
        }
        return out;
    }

    /** Step 19g: too heavy for a pedlar's pack: building goods (bricks, glass) and transport (saddles). */
    static boolean heavy(ProductAtlas.Product p) {
        return p.family().equals("building") || p.family().equals("transport");
    }

    /** What today's paper (or a handwritten newsletter) costs here, in round money. */
    public static long paperPrice(MoneyTable.Here here, boolean handwritten) {
        long raw = Math.max(1L, Math.round(here.dayPay() * (handwritten ? NewsEras.newsletterDays()
                : NewsEras.paperDays(here.year()))));
        return nice(raw, here.money(), here.year());
    }

    private static String key(Item it) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(it);
        return rl == null ? "minecraft:air" : rl.toString();
    }

    /** An item with a product's local name and its words (a normal item that is called what people here call it). */
    static ItemStack named(ItemStack stack, @Nullable Product p) {
        if (p != null && !p.name().isBlank()) {
            stack.setHoverName(Component.literal(p.name()).withStyle(s -> s.withItalic(false)));
        }
        return stack;
    }

    static boolean hasPaper(ServerPlayer p, String name, int date) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            CompoundTag t = s.getItem() instanceof NewspaperItem ? s.getTagElement(NewspaperItem.TAG) : null;
            if (t != null && t.getInt("date") == date && t.getString("name").equals(name)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasMap(ServerPlayer p, String title, int year) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            CompoundTag t = s.getItem() instanceof MapItem ? s.getTagElement(MapItem.TAG) : null;
            if (t != null && t.getString("title").equals(title) && t.getInt("year") == year) {
                return true;
            }
        }
        return false;
    }

    /** The seller's opening words. */
    static String greeting(Seller s) {
        return switch (s.roleKey()) {
            case NewsEras.PAPER_SELLER -> "Papers and maps! Have a look.";
            case NewsEras.NEWS_WRITER -> "News copied by hand, for those who can pay.";
            case MarketService.PEDLAR -> "A few good things from the towns. Have a look.";
            default -> "Have a look. These are today's prices.";
        };
    }

    static String title(Seller s) {
        return switch (s.roleKey()) {
            case NewsEras.PAPER_SELLER -> s.name() + "'s paper stand";
            case NewsEras.NEWS_WRITER -> s.name() + "'s newsletters";
            case MarketService.PEDLAR -> s.name() + "'s pack";
            default -> s.name() + "'s goods";
        };
    }

    /**
     * The trade screen. With a player, a purse that never had money gets its start money first (a day's pay in the
     * money of the place), and says so.
     */
    public static TradeView view(Seller s, @Nullable ServerPlayer p, @Nullable String says, @Nullable String selected,
                                 boolean sold) {
        MinecraftServer server = p == null ? null : p.getServer();
        MoneyTable.Here here = here(s);
        String start = null;
        if (p != null && server != null) {
            start = PurseService.startIfNew(PlayerPurses.get(server), p.getUUID(), here, KnowledgeDate.today());
            CoinPurse.of(p); // step 23b: the start money (and any written purse money) becomes pieces
        }
        TradeView v = new TradeView();
        v.entityId = s.entityId();
        v.npcName = s.name();
        v.title = title(s);
        v.job = s.roleKey().replace('_', ' ');
        v.dateLabel = KnowledgeDate.label(s.date());
        for (Lot l : lots(s, p, server)) {
            v.offers.add(l.offer());
        }
        String words = says == null || says.isBlank() ? greeting(s) : says;
        if (v.offers.isEmpty()) {
            words = "I have nothing to sell today.";
        }
        v.says = start == null ? words : start + " " + words;
        v.purse = purseLine(server, p, here);
        v.sound = sold ? 1 : 0;
        return v;
    }

    static String purseLine(@Nullable MinecraftServer server, @Nullable ServerPlayer p, MoneyTable.Here here) {
        if (server == null || p == null) {
            return "";
        }
        return "Your purse: " + PurseService.holdings(MoneyTable.get(), CoinPurse.of(p).view(), here.year());
    }

    /**
     * The player buys an offer at the price shown, without the trade screen ({@code /ee shop ... buy}): paid from the
     * pieces carried, with change. Nothing changes when the offer is gone, its price changed, it is sold out today, the
     * player has it already (a paper, a map) or cannot pay in money taken here.
     */
    public static MarketService.Done buy(ServerPlayer p, Seller s, String id, long price) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return new MarketService.Done(false, "There is nobody to sell to you.");
        }
        Lot lot = null;
        for (Lot l : lots(s, p, server)) {
            if (l.offer().id().equals(id)) {
                lot = l;
            }
        }
        if (lot == null) {
            return new MarketService.Done(false, "I don't have that any more.");
        }
        TradeView.Offer o = lot.offer();
        if (o.price() != price) {
            return new MarketService.Done(false, "That price has changed. Here is what I sell now.");
        }
        if (o.owned()) {
            return new MarketService.Done(false, "You have that already.");
        }
        if (o.left() == 0) {
            return new MarketService.Done(false, "That is all I have today. Come back tomorrow.");
        }
        MoneyTable.Here here = here(s);
        MoneyTable table = MoneyTable.get();
        PlayerPurses purses = PlayerPurses.get(server);
        String start = PurseService.startIfNew(purses, p.getUUID(), here, KnowledgeDate.today());
        String prefix = start == null ? "" : start + " ";
        MoneyTable.Money in = o.money().isEmpty() ? here.money() : table.money(o.money());
        String refused = pay(CoinPurse.of(p), here, in == null ? here.money() : in, o.price(), table);
        if (refused != null) {
            return new MarketService.Done(false, prefix + refused);
        }
        ItemStack stack = lot.give().get();
        if (!p.getInventory().add(stack)) {
            p.drop(stack, false);
        }
        p.containerMenu.broadcastChanges();
        return new MarketService.Done(true, prefix + sold(p, s, lot, o.priceText()) + " " + purseLine(server, p, here)
                + ".");
    }

    /**
     * Step 24: the trade screen has taken the money for a lot (the thing itself went to the player from the result
     * slot): the seller's stock for the day, the log and the history book's journal. The receipt, in his words.
     */
    static String sold(ServerPlayer p, Seller s, Lot lot, String paid) {
        MinecraftServer server = p.getServer();
        TradeView.Offer o = lot.offer();
        String id = o.id();
        MoneyTable.Here here = here(s);
        if (server != null && lot.counted() && s.uuid() != null) {
            MarketLedger.get(server).record(s.uuid(), MarketService.marketDay(server), here.money().id(), OUT + id, 1, 0L);
        }
        LOGGER.info("[EE market] {} bought {} ({}) from {} {} for {} {} ({}) in {}", p.getGameProfile().getName(), id,
                o.name(), s.roleKey(), s.name(), o.price(), o.money().isEmpty() ? here.money().id() : o.money(), paid,
                s.year());
        String what = (id.startsWith("good:") || id.startsWith(MarketService.ATLAS)) && !o.name().isEmpty()
                ? Character.toLowerCase(o.name().charAt(0)) + o.name().substring(1) : o.name();
        String after = "";
        if (id.startsWith(BOOK)) {
            what = bookWords(o.name()); // step 22: "a papyrus roll (history book)"
            after = " Right-click it to read your notes.";
        }
        String verb = here.money().inDays() ? "Swapped for " : "Bought "; // step 24: where people swap
        String line = here.money().inDays() ? verb + what + ", goods worth " + o.priceText().replaceFirst("^goods worth ", "")
                : verb + what + " for " + o.priceText();
        if (server != null) {
            com.eternalengine.knowledge.BookService.journal(server, p.getUUID(), KnowledgeDate.today(), line + " from "
                    + s.name() + ".", "");
        }
        return line + "." + after;
    }

    // ----------------------------------------------------------------------------------- step 19f: paying

    /**
     * Pay {@code price} (in the money of the place): from that money first, then from a money the place also takes,
     * at the fixed rate (rounded up). Returns null when paid, else what the seller says: old money is not taken any
     * more, foreign money is not taken here, or there is too little.
     */
    @Nullable
    public static String pay(PlayerPurses purses, UUID player, MoneyTable.Here here, long price, MoneyTable table) {
        return pay(new Wallet.PurseWallet(purses, player), here, price, table);
    }

    /**
     * Step 23b: the same from any wallet: in the game the pieces in the inventory (handed over, change back), in
     * checks the purse of numbers.
     */
    @Nullable
    public static String pay(Wallet wallet, MoneyTable.Here here, long price, MoneyTable table) {
        return pay(wallet, here, here.money(), price, table);
    }

    /**
     * Step 24: the same for a price in any money taken here (cheap things are priced in the small change beside the
     * money of the place): from that money first, then from another money taken here, at the fixed rate or (the small
     * change and the money of the place) by what it buys, rounded up.
     */
    @Nullable
    public static String pay(Wallet wallet, MoneyTable.Here here, MoneyTable.Money money, long price, MoneyTable table) {
        int year = here.year();
        PlayerPurses.Purse purse = wallet.view();
        long have = purse.amount(money.id());
        if (have >= price) {
            return wallet.pay(money.id(), price, year) ? null : "Your purse could not pay that.";
        }
        MoneyTable.Money small = TradeRules.smallChange(table, here);
        List<MoneyTable.Money> others = new ArrayList<>();
        others.add(here.money());
        others.addAll(here.accepts());
        for (MoneyTable.Money m : others) {
            boolean smallPair = small != null && (m.id().equals(small.id()) || money.id().equals(small.id()));
            if (m.id().equals(money.id()) || !(table.fixed(money.id(), m.id(), year) || smallPair)) {
                continue;
            }
            long need = needIn(table, money, price, m, year);
            if (need > 0 && purse.amount(m.id()) >= need) {
                return wallet.pay(m.id(), need, year) ? null : "Your purse could not pay that.";
            }
        }
        String priceWords = MoneyFormat.amount(money, price, year);
        for (var e : purse.money().entrySet()) {
            if (e.getValue() <= 0 || here.takes(e.getKey())) {
                continue;
            }
            MoneyTable.Money m = table.money(e.getKey());
            if (m == null) {
                continue;
            }
            String why = old(table, m, year) ? "That old " + m.name() + " is not taken any more."
                    : "We don't take the " + m.name() + " here.";
            return "That is " + priceWords + ". " + why + " A money-changer will change it for you."
                    + (have > 0 ? " In money of here you have only " + MoneyFormat.amount(money, have, year) + "." : "");
        }
        return "That is " + priceWords + ". You have only " + MoneyFormat.amount(money, Math.max(0L, have), year) + ".";
    }

    /** How much of money {@code m} pays {@code price} of the place's money at the fixed rate, rounded up. */
    static long needIn(MoneyTable table, MoneyTable.Money money, long price, MoneyTable.Money m, int year) {
        long need = table.convert(money, price, m, year);
        for (int i = 0; i < 4 && need >= 0 && table.convert(m, need, money, year) < price; i++) {
            need++;
        }
        return need;
    }

    /** True when no place uses this money any more in this year (every row with it has ended). */
    public static boolean old(MoneyTable table, MoneyTable.Money m, int year) {
        boolean any = false;
        for (MoneyTable.Row r : table.rows()) {
            if (r.money() != null && r.money().id().equals(m.id())) {
                any = true;
                if (r.to() >= year) {
                    return false;
                }
            }
        }
        return any;
    }

    // ------------------------------------------------------------------------------------------------- checks

    /**
     * The shop's own check, with no world: Hyderabad 1948 prices (a paper 1 anna, a map 8 annas, 16 sheaves of wheat
     * dearer than the merchant pays), Osmania biscuits sold there and not in Rome, the product cards read, and paying:
     * Indian rupees taken at 7 for 6, Mughal dams refused as old money, pounds as foreign money. Null when all is well.
     */
    public static String selfTest() {
        if (products().size() < 10 || !productProblems.isEmpty()) {
            return "product cards: " + products().size() + " read, problems " + productProblems;
        }
        MoneyTable table = MoneyTable.get();
        MoneyTable.Here hyd = table.at(17.3616, 78.4747, 1948);
        if (!"osmania_sicca".equals(hyd.money().id())) {
            return "the money of Hyderabad in 1948 should be the Osmania sicca, not " + hyd.money().id();
        }
        long paper = paperPrice(hyd, false);
        long map = nice(Math.round(hyd.dayPay() * NewsEras.mapDays(true)), hyd.money(), 1948);
        MoneyTable.Here bom = table.at(18.94, 72.83, 1948);
        if (nice(95, bom.money(), 1948) != 96 || nice(77, hyd.money(), 1948) != 77 || nice(576, hyd.money(), 1948) != 576) {
            return "round prices: 95 pies should be 8 annas, 77 pai and 3 rupees should stay";
        }
        if (paper != 12 || map != 96) {
            return "a paper should be 1 anna (12 pai) and a map 8 annas (96 pai), not " + paper + " and " + map;
        }
        GoodsTable.Good wheat = GoodsTable.get().good("WHEAT");
        double each = MarketService.worth(wheat, hyd, MarketService.basePrice("WHEAT"));
        int q = lotSize(each, hyd.money(), 1948);
        long sell = (long) Math.ceil(each * q - 1e-9);
        long pays = (long) Math.floor(MarketService.pays(wheat, hyd, "market", MarketService.Terms.DEFAULT) * q + 1e-9);
        if (q != 16 || sell <= pays || sell != 77) {
            return "16 sheaves of wheat should sell for 6 annas 5 pai (77 pai) and be bought for less: lot " + q + ", sell "
                    + sell + ", pays " + pays;
        }
        boolean biscuitsHyd = false, biscuitsRome = false;
        for (Product p : products()) {
            if (p.id().equals("osmania_biscuits")) {
                biscuitsHyd = p.here(17.3616, 78.4747, 1948);
                biscuitsRome = p.here(41.89, 12.49, 1948) || p.here(17.3616, 78.4747, 1900);
            }
        }
        if (!biscuitsHyd || biscuitsRome) {
            return "Osmania biscuits should be sold in Hyderabad in 1948, not in Rome or in 1900";
        }
        // Paying, on a purse that is not saved anywhere.
        PlayerPurses purses = new PlayerPurses();
        UUID u = new UUID(1L, 2L);
        purses.add(u, "indian_rupee_anna", 192L);
        if (pay(purses, u, hyd, 12L, table) != null) {
            return "a seller in Hyderabad in 1948 should take Indian rupees";
        }
        long left = purses.view(u).amount("indian_rupee_anna");
        if (left != 192L - needIn(table, hyd.money(), 12L, table.money("indian_rupee_anna"), 1948) || left >= 182L) {
            return "1 anna (O.S.) should cost 11 pies or more in Indian money, left " + left;
        }
        PlayerPurses old = new PlayerPurses();
        old.add(u, "mughal_rupee", 75L);
        String r = pay(old, u, hyd, 12L, table);
        if (r == null || !r.contains("old Mughal rupee") || old.view(u).amount("mughal_rupee") != 75L) {
            return "Mughal money should be refused as old money in 1948: " + r;
        }
        PlayerPurses gb = new PlayerPurses();
        gb.add(u, "pound_lsd", 240L);
        String r2 = pay(gb, u, hyd, 12L, table);
        if (r2 == null || !r2.contains("don't take")) {
            return "pounds should be refused as foreign money in Hyderabad: " + r2;
        }
        return null;
    }
}
