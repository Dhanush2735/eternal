package com.eternalengine.knowledge;

import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.money.GoodsTable;
import com.eternalengine.money.MarketLedger;
import com.eternalengine.money.MarketService;
import com.eternalengine.money.MoneyFormat;
import com.eternalengine.money.MoneyTable;
import com.eternalengine.money.PlayerPurses;
import com.eternalengine.money.PurseService;
import com.eternalengine.news.NewsEras;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 19b: trading on the talk screen. A merchant buys the goods the player carries ("I have goods to sell."); the
 * money-changer swaps money ("Can you change my money?"); anyone else says who buys goods nearby ("Who buys goods
 * here?"). Selling and swapping really happen (purse and inventory); they are actions of their own, so going back
 * never repeats one.
 *
 * <p>Step 19g: a town trader buys and changes money, a village pedlar buys, and where people swap and nobody trades for
 * a living anybody swaps a little; each pays less than a city merchant. Buyers take the atlas products people here make
 * or bring in, too.
 */
final class MarketTalk {

    /** Topic: the merchant's goods and prices. */
    static final String SELL = "sell";
    /** Topic: the money-changer's offers. */
    static final String SWAP = "swap";
    /** Topic: who buys goods (or changes money) near here. */
    static final String WHERE = "market";
    /** How far a villager looks for a merchant or a money-changer, in blocks. */
    private static final double LOOK_BLOCKS = 96.0d;
    private static final String[] DIRS = {"north", "north-east", "east", "south-east", "south", "south-west", "west",
            "north-west"};
    /** Step 19g: how many atlas products the price words name before "and N more". */
    private static final int ATLAS_NAMED = 4;
    private static final java.util.Set<String> BUYERS = java.util.Set.of(MarketService.MERCHANT, MarketService.TRADER,
            MarketService.PEDLAR);
    private static final java.util.Set<String> CHANGERS = java.util.Set.of(MarketService.MONEY_CHANGER,
            MarketService.TRADER);

    private MarketTalk() {
    }

    static String role(TalkService.Ctx c) {
        return c.role == null ? "" : c.role.toLowerCase(Locale.ROOT);
    }

    /** Step 19g: this person buys goods: a merchant, a trader, a pedlar, or anybody where people swap a little. */
    static boolean buyer(TalkService.Ctx c) {
        return MarketService.buyer(c.role) || swapsALittle(c);
    }

    /** This person changes money: the money-changer, or (step 19g) a town trader. */
    static boolean changer(TalkService.Ctx c) {
        return MarketService.changes(c.role);
    }

    /**
     * Step 19g: where people swap and nobody trades for a living, anybody swaps a little (not a money-changer or
     * somebody who brings the news).
     */
    static boolean swapsALittle(TalkService.Ctx c) {
        String r = role(c);
        if (MarketService.buyer(r) || MarketService.changes(r) || NewsEras.newsRole(r)) {
            return false;
        }
        try {
            MoneyTable.Here here = here(c);
            if (!here.money().barter()) {
                return false;
            }
            return MarketService.swapsALittle(com.eternalengine.core.WorldQuery.presence(null, com.eternalengine.core.WorldKey.at(c.lat, c.lon, here.year())).spawnRoles(),
                    here);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Step 19g: what this person pays and keeps: a merchant's terms, less for a trader, a pedlar or anybody else. */
    static MarketService.Terms terms(TalkService.Ctx c) {
        MarketService.Terms base = MarketService.Terms.current();
        String r = role(c);
        return MarketService.buyer(r) || MarketService.changes(r) ? base.as(r) : base.as(MarketService.LOCAL);
    }

    private static boolean barter(TalkService.Ctx c) {
        try {
            return here(c).money().barter();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The trade options in a topic list: first for a merchant, a trader, a pedlar or a money-changer (and anybody who
     * swaps a little), last for anyone else.
     */
    static void addOptions(TalkService.Ctx c, TalkView v) {
        String r = role(c);
        boolean barter = barter(c);
        // Step 24: buying, selling, swapping and changing happen on Minecraft's trade screen ("Let's trade.", from the
        // news options); the talk keeps one question about prices, so it no longer fills up with one option per good.
        if (MarketService.buyer(r)) {
            v.options.add(0, new TalkView.Option("topic", SELL, "", barter ? "What do you swap for?" : "What do you buy?"));
            if (!barter && !MarketService.changes(r)) {
                // Right behind "What do you buy?": the talk screen draws nine options and this one
                // was appended to the end of the list, so at a busy place it was the tenth and could
                // not be chosen at all (the market probe found it 10th of 11).
                v.options.add(Math.min(1, v.options.size()),
                        new TalkView.Option("topic", WHERE, "", "Who changes money here?"));
            }
        } else if (changer(c)) {
            v.options.add(0, new TalkView.Option("trade", "", "", "Can you change my money?"));
            v.options.add(new TalkView.Option("topic", WHERE, "", "Who buys goods here?"));
        } else if (swapsALittle(c)) {
            v.options.add(0, new TalkView.Option("trade", "", "", "Let's swap goods."));
        } else {
            v.options.add(new TalkView.Option("topic", WHERE, "", "Who buys goods here?"));
        }
    }

    /** Step 19g: what a trader or a pedlar says of himself, after his name and job (empty for anyone else). */
    static String selfLine(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case MarketService.MERCHANT -> " I buy and sell in the market.";
            case MarketService.TRADER -> " I buy and sell for the market of this town, and I change money.";
            case MarketService.PEDLAR -> " I walk from village to village with my pack, buying and selling.";
            case MarketService.MONEY_CHANGER -> " I change money in the market.";
            default -> "";
        };
    }

    private static MoneyTable.Here here(TalkService.Ctx c) {
        return MoneyTable.get().at(c.lat, c.lon, KnowledgeDate.year(c.date));
    }

    private static String civ(TalkService.Ctx c) {
        return c.npc == null ? "market" : c.npc.getCivilizationId();
    }

    // ------------------------------------------------------------------------------------------- the merchant

    /**
     * What the buyer takes and pays (step 19g: the atlas products too), and (step 24) which of it the player carries:
     * the selling itself is on the trade screen, so the talk no longer lists one option per good.
     */
    static TalkView sellView(TalkService.Ctx c, @Nullable String receipt) {
        TalkView v = TalkService.base(c);
        MoneyTable.Here here = here(c);
        MoneyTable.Money money = here.money();
        int year = here.year();
        List<GoodsTable.Good> goods = MarketService.bought(c.lat, c.lon, year);
        v.hint = receipt == null ? "" : receipt;
        if (goods.isEmpty()) {
            v.text = "Nobody here buys goods.";
            return v;
        }
        MarketService.Terms t = terms(c);
        boolean barter = money.barter();
        String civ = civ(c);
        List<String> names = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<String> atlas = new ArrayList<>();
        double[] each = new double[goods.size()];
        for (int i = 0; i < goods.size(); i++) {
            GoodsTable.Good g = goods.get(i);
            each[i] = MarketService.pays(g, here, civ, t);
            if (g.id().startsWith(MarketService.ATLAS)) {
                atlas.add(g.name());
                continue;
            }
            names.add(g.name());
            prices.add(MarketService.priceEntry(g, each[i], money, year));
        }
        StringBuilder text = new StringBuilder(opening(role(c), barter));
        if (!names.isEmpty()) {
            text.append(barter ? "I swap my goods for " : "I buy ").append(list(names));
            if (!atlas.isEmpty()) {
                text.append(", and things people here make or bring in: ").append(some(atlas));
            }
            text.append(". ").append(barter ? "I give " : "I pay ").append(String.join(", ", prices)).append('.');
        } else {
            text.append(barter ? "I swap my goods for " : "I buy ").append("things people here make or bring in: ")
                    .append(some(atlas)).append('.');
        }
        text.append(" Full price for the first few of each, half for more.");
        if (c.trader != null && c.npc != null && c.server != null) {
            MarketLedger.Day day = MarketLedger.get(c.server).today(c.npc.getUUID(), MarketService.marketDay(c.server),
                    money.id());
            long left = MarketService.budget(here, t) - day.spent();
            if (left <= 0) {
                text.append(" I have paid out all I can today. Come back tomorrow.");
            } else {
                text.append(" Today I can still ").append(barter ? "give " : "pay out ")
                        .append(MoneyFormat.amount(money, left, year)).append('.');
            }
            // Step 24: the selling itself happens on the trade screen; the talk says what of his you carry.
            List<String> carried = new ArrayList<>();
            List<String> enough = new ArrayList<>();
            for (GoodsTable.Good g : goods) {
                if (MarketService.carried(c.trader, g) <= 0) {
                    continue;
                }
                carried.add(g.name());
                if (day.sold(g.id()) >= 2 * g.want()) {
                    enough.add(g.name());
                }
            }
            if (!enough.isEmpty()) {
                text.append(" I have enough ").append(list(enough)).append(" for today.");
            }
            if (carried.isEmpty()) {
                text.append(" You have nothing I buy.");
            } else {
                text.append(" You have ").append(some(carried)).append(" that I buy: let's trade.");
            }
        }
        v.text = text.toString();
        return v;
    }

    /** Step 19g: the buyer's first words: who he is, when he is not a city merchant. */
    private static String opening(String role, boolean barter) {
        return switch (role) {
            case MarketService.TRADER -> "I buy for the market of this town, so I pay a little less than a city merchant. ";
            case MarketService.PEDLAR -> "I carry what I buy to the towns, so I pay less than a trader there. ";
            case MarketService.MERCHANT -> "";
            default -> barter ? "I can swap a little. " : "";
        };
    }

    /** Step 19g: "paper, torches, iron axes, eggs and 9 more". */
    static String some(List<String> names) {
        if (names.size() <= ATLAS_NAMED + 1) {
            return list(names);
        }
        return String.join(", ", names.subList(0, ATLAS_NAMED)) + " and " + (names.size() - ATLAS_NAMED) + " more";
    }

    // -------------------------------------------------------------------------------------- the money-changer

    /**
     * Step 24: what a money-changer (or a town trader) says of his trade: into which money, his cut, the money taken
     * here; where people swap, that a trader takes coins as metal. The changing itself is on the trade screen.
     */
    static String changerText(TalkService.Ctx c) {
        MoneyTable table = MoneyTable.get();
        MoneyTable.Here here = here(c);
        MoneyTable.Money money = here.money();
        boolean trader = MarketService.TRADER.equals(role(c));
        MarketService.Terms t = terms(c);
        if (money.barter()) {
            return trader ? "Coins are only metal here. I take them for goods, by what they buy, and keep "
                    + t.strangeFeePercent() + " in every 100." : "People here have no money. They swap goods.";
        }
        if (t.feePercent() == t.strangeFeePercent()) {
            return "I change money into " + money.called() + " and keep " + t.feePercent() + " in every 100, whatever the"
                    + " money. A money-changer in a city keeps less where the rate is fixed. " + PurseService.moneyHere(table, here);
        }
        return "I change money into " + money.called() + ". Where the rate is fixed I keep " + t.feePercent()
                + " in every 100; for old money, or money from far away, " + t.strangeFeePercent() + " in every 100. "
                + PurseService.moneyHere(table, here);
    }

    /** What the money-changer does and keeps (the talk's words; the changing is on the trade screen). */
    static TalkView swapView(TalkService.Ctx c, @Nullable String receipt) {
        TalkView v = TalkService.base(c);
        v.hint = receipt == null ? "" : receipt;
        v.text = changerText(c);
        return v;
    }

    // ----------------------------------------------------------------------------------------- who buys here

    /**
     * "Ask Ramesh, a merchant, about 30 m north of here." The nearest buyer (merchant, trader or pedlar) and the nearest
     * money-changer (or trader), by name.
     */
    static TalkView whereView(TalkService.Ctx c) {
        boolean wantBuyer = !MarketService.buyer(c.role);
        boolean wantChanger = !changer(c);
        HumanNpcEntity m = wantBuyer ? nearest(c, BUYERS) : null;
        HumanNpcEntity x = wantChanger ? nearest(c, CHANGERS) : null;
        StringBuilder text = new StringBuilder();
        String hint = "";
        if (m != null) {
            String name = TalkService.nameOf(m, c.lat, c.lon, c.date);
            String job = KnowledgeLevels.job(m.getRole());
            text.append("Ask ").append(name).append(", ").append(TalkService.article(job)).append(' ').append(job)
                    .append(", ").append(phrase(c.npc, m)).append('.');
            if (m == x) {
                text.append(" He changes money too.");
                x = null;
            }
            hint = name + ": " + phrase(c.npc, m);
        }
        if (x != null) {
            String name = TalkService.nameOf(x, c.lat, c.lon, c.date);
            text.append(text.length() > 0 ? " " : "").append("To change money, see ").append(name)
                    .append(", the ").append(KnowledgeLevels.job(x.getRole())).append(", ").append(phrase(c.npc, x))
                    .append('.');
            if (hint.isEmpty()) {
                hint = name + ": " + phrase(c.npc, x);
            }
        }
        if (text.length() == 0) {
            text.append(nobodyNear(here(c).money().barter(), wantBuyer));
        }
        TalkView v = TalkService.topics(c, text.toString());
        v.hint = hint;
        v.options.removeIf(o -> WHERE.equals(o.a()));
        return v;
    }

    /** Step 19g: what people say when nobody near buys goods or changes money (no wrong hint about towns). */
    static String nobodyNear(boolean barter, boolean wantBuyer) {
        if (barter) {
            return "People here swap what they have. Nobody buys for money.";
        }
        if (wantBuyer) {
            return "Nobody near here buys goods. Every town and city has a market, and pedlars go round the villages.";
        }
        return "Nobody near here changes money. A money-changer in a city will, or a trader in a town market.";
    }

    @Nullable
    private static HumanNpcEntity nearest(TalkService.Ctx c, java.util.Set<String> roles) {
        if (c.npc == null) {
            return null;
        }
        HumanNpcEntity best = null;
        double bestD = Double.MAX_VALUE;
        AABB box = c.npc.getBoundingBox().inflate(LOOK_BLOCKS, 24.0d, LOOK_BLOCKS);
        for (HumanNpcEntity e : c.npc.level().getEntitiesOfClass(HumanNpcEntity.class, box,
                e -> e != c.npc && e.isAlive() && e.getRole() != null && roles.contains(e.getRole().toLowerCase(Locale.ROOT)))) {
            double d = e.distanceToSqr(c.npc);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** "about 30 m north-east of here" (north is -Z), or "right here". */
    static String phrase(@Nullable HumanNpcEntity from, HumanNpcEntity to) {
        if (from == null) {
            return "not far from here";
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 6.0d) {
            return "right here";
        }
        long m = d < 50 ? Math.round(d / 5.0d) * 5 : Math.round(d / 10.0d) * 10;
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0d) % 360.0d;
        return "about " + m + " m " + DIRS[(int) Math.round(deg / 45.0d) % 8] + " of here";
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** "a, b and c". */
    static String list(List<String> words) {
        if (words.isEmpty()) {
            return "";
        }
        if (words.size() == 1) {
            return words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
    }
}
