package com.eternalengine.knowledge;

import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.money.MarketService;
import com.eternalengine.money.ShopService;
import com.eternalengine.money.TradeView;
import com.eternalengine.news.BookForms;
import com.eternalengine.news.NewsEras;
import com.eternalengine.news.NewsService;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Steps 19c and 19d in the talk box: "Show me your goods." opens the trade screen with a seller; the town crier tells
 * the day's news for nothing; anybody can say where the news is sold.
 */
final class NewsTalk {

    static final String CRIER = "crier";
    static final String WHERE = "papers";
    /** Step 22: "Where can I get something to keep notes in?" (asked by a player with no history book). */
    static final String NOTES = "notes";
    /** Step 37 (C29): "What do people here believe?" */
    static final String BELIEF = "belief";
    private static final double LOOK_BLOCKS = 96.0d;
    private static final Logger LOGGER = LoggerFactory.getLogger(NewsTalk.class);

    private NewsTalk() {
    }

    static String role(TalkService.Ctx c) {
        return c.role == null ? "" : c.role.toLowerCase(Locale.ROOT);
    }

    static void addOptions(TalkService.Ctx c, TalkView v) {
        String role = role(c);
        if (ShopService.sells(role)) {
            // Step 24: a merchant, a trader or a pedlar sells and buys on the same trade screen.
            String label = role.equals(NewsEras.PAPER_SELLER) ? "Show me your papers and maps."
                    : role.equals(NewsEras.NEWS_WRITER) ? "Show me your newsletters." : "Let's trade.";
            v.options.add(0, new TalkView.Option("trade", "", "", label));
        }
        if (role.equals(NewsEras.TOWN_CRIER)) {
            v.options.add(0, new TalkView.Option("topic", CRIER, "", "What is the news today?"));
        } else if (!NewsEras.newsRole(role)) {
            v.options.add(new TalkView.Option("topic", WHERE, "", "Where can I get the news?"));
        }
        if (!ShopService.sells(role) && c.trader != null && BookService.carried(c.trader) == null) {
            // Step 22: right after "Tell me about yourself", so it stays among the nine options the box shows.
            int at = v.options.size();
            for (int i = 0; i < v.options.size(); i++) {
                if (TalkService.SELF.equals(v.options.get(i).a())) {
                    at = i + 1;
                }
            }
            v.options.add(at, new TalkView.Option("topic", NOTES, "", "Where can I get something to keep notes in?"));
        }
    }

    /**
     * Step 22: where to get a history book. With one already: "You have your papyrus roll already." Otherwise the
     * nearest seller and what he sells ("Rani, the merchant, sells papyrus rolls, about 30 m east of here."); with no
     * seller near, the person gives the memory aid of the place (a tally stick, knotted cords, a message stick), and
     * says what the towns sell when people here write.
     */
    static TalkView notesView(TalkService.Ctx c) {
        int year = KnowledgeDate.year(c.date);
        BookForms.Form have = c.trader == null ? null : BookService.carried(c.trader);
        String text;
        String hint = "";
        if (have != null) {
            text = "You have your " + have.title.toLowerCase(Locale.ROOT) + " already. What people tell you goes into it.";
        } else if (BookForms.sells(role(c))) {
            text = "I sell them myself. Ask to see my goods.";
        } else {
            HumanNpcEntity best = null;
            double bestD = Double.MAX_VALUE;
            if (c.npc != null) {
                AABB box = c.npc.getBoundingBox().inflate(LOOK_BLOCKS, 24.0d, LOOK_BLOCKS);
                for (HumanNpcEntity e : c.npc.level().getEntitiesOfClass(HumanNpcEntity.class, box,
                        e -> e != c.npc && e.isAlive() && BookForms.sells(e.getRole()))) {
                    double d = e.distanceToSqr(c.npc);
                    if (d < bestD) {
                        bestD = d;
                        best = e;
                    }
                }
            }
            if (best != null) {
                String name = TalkService.nameOf(best, c.lat, c.lon, c.date);
                String where = MarketTalk.phrase(c.npc, best);
                List<BookForms.Form> forms = BookForms.soldBy(best.getRole(), c.lat, c.lon, year);
                String what = forms.isEmpty() ? "history books" : forms.get(0).plural;
                text = name + ", the " + KnowledgeLevels.job(best.getRole()) + ", sells " + what + ", " + where + ".";
                hint = name + ": " + where;
            } else {
                BookForms.Form aid = BookForms.memoryAid(c.lat, c.lon, year);
                List<BookForms.Form> w = BookForms.writing(c.lat, c.lon, year);
                String towns = w.isEmpty() ? "" : " In the towns they sell " + w.get(0).plural + ".";
                text = "Nobody near here sells such things." + towns;
                if (c.trader != null) {
                    BookService.give(c.trader, aid);
                    String it = aid.title.toLowerCase(Locale.ROOT);
                    boolean many = it.endsWith("s");
                    text += " Take " + (many ? "these " : "this ") + it + ": " + aid.howTo() + ". That is how we remember.";
                    BookService.journal(c.trader.getServer(), c.trader.getUUID(), c.date, "Given " + (many ? "" : "a ") + it
                            + " by " + c.npcName + ", to keep my notes.", "");
                    LOGGER.info("[EE book] {} gave {} a history book: {} ({}, {}, {})", c.npcName,
                            c.trader.getGameProfile().getName(), aid.id, String.format(Locale.ROOT, "%.2f", c.lat),
                            String.format(Locale.ROOT, "%.2f", c.lon), year);
                }
            }
        }
        TalkView v = TalkService.topics(c, text);
        v.hint = hint;
        v.options.removeIf(o -> NOTES.equals(o.a()));
        return v;
    }

    /**
     * Step 37 (C29): is anything on record for what people here believe, in this year? The option
     * is only offered when there is: an empty answer is not an invitation to invent one.
     */
    static boolean hasBelief(TalkService.Ctx c) {
        return !Beliefs.get().at(c.lat, c.lon, KnowledgeDate.year(c.date)).isEmpty();
    }

    /**
     * Step 37 (C29): what people here believe, said in the speaker's own standing. A court clerk
     * quotes the learned consensus ("It is held that ..."); a farmer in the bazaar repeats what
     * everyone says. Only what the register has on record, and nothing when it has nothing.
     */
    static TalkView beliefView(TalkService.Ctx c) {
        int year = KnowledgeDate.year(c.date);
        long salt = (long) c.entityId * 31L + c.date;
        String said = Beliefs.get().says(role(c), c.lat, c.lon, year, salt);
        if (said.isEmpty()) {
            said = "I could not tell you what people believe. Ask someone who has been taught.";
        }
        TalkView v = TalkService.topics(c, said);
        v.options.removeIf(o -> BELIEF.equals(o.a()));
        return v;
    }

    /** The town crier calls out the day's news: up to three stories, for nothing. */
    static TalkView crierView(TalkService.Ctx c) {
        List<String> lines = NewsService.crierLines(c.lat, c.lon, c.date);
        StringBuilder text = new StringBuilder("Hear the news! ");
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            text.append(i == 0 ? "" : " ").append(lines.get(i));
        }
        if (lines.isEmpty()) {
            text.append("There is no big news today.");
        }
        TalkView v = TalkService.topics(c, text.toString());
        v.options.removeIf(o -> CRIER.equals(o.a()));
        return v;
    }

    /** "Buy one from Rani, the paper seller, about 30 m east of here." The nearest person who brings the news. */
    static TalkView whereView(TalkService.Ctx c) {
        HumanNpcEntity best = null;
        double bestD = Double.MAX_VALUE;
        if (c.npc != null) {
            AABB box = c.npc.getBoundingBox().inflate(LOOK_BLOCKS, 24.0d, LOOK_BLOCKS);
            for (HumanNpcEntity e : c.npc.level().getEntitiesOfClass(HumanNpcEntity.class, box,
                    e -> e != c.npc && e.isAlive() && NewsEras.newsRole(e.getRole()))) {
                double d = e.distanceToSqr(c.npc);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        String text;
        String hint = "";
        if (best != null) {
            String name = TalkService.nameOf(best, c.lat, c.lon, c.date);
            String where = MarketTalk.phrase(c.npc, best);
            String r = best.getRole() == null ? "" : best.getRole().toLowerCase(Locale.ROOT);
            text = switch (r) {
                case NewsEras.PAPER_SELLER -> "Buy a paper from " + name + ", the paper seller, " + where
                        + ". Maps too.";
                case NewsEras.NEWS_WRITER -> name + ", the newsletter writer, copies the news by hand, " + where
                        + ". It costs a day's pay.";
                default -> "Listen to " + name + ", the town crier, " + where + ". The news costs nothing.";
            };
            hint = name + ": " + where;
        } else {
            text = "Nobody near here sells the news. Papers are sold in the towns and cities.";
        }
        TalkView v = TalkService.topics(c, text);
        v.hint = hint;
        v.options.removeIf(o -> WHERE.equals(o.a()));
        return v;
    }

    /** The wares as text ("trade"), or buy an offer at the price shown ("buy", offer id, price), for {@code /ee talk}. */
    @Nullable
    static TradeView trade(TalkService.Ctx c, String action, String a, String b) {
        if (!ShopService.sells(role(c))) {
            return null;
        }
        ShopService.Seller s = seller(c);
        if ("buy".equals(action) && c.trader != null) {
            long price;
            try {
                price = Long.parseLong(b == null ? "" : b.trim());
            } catch (NumberFormatException e) {
                price = -1L;
            }
            MarketService.Done d = ShopService.buy(c.trader, s, a == null ? "" : a, price);
            return ShopService.view(s, c.trader, d.text(), a, d.ok());
        }
        return ShopService.view(s, c.trader, null, null, false);
    }

    /** The seller in this talk. */
    static ShopService.Seller seller(TalkService.Ctx c) {
        return new ShopService.Seller(role(c), c.npcName, c.entityId, c.npc == null ? null : c.npc.getUUID(),
                c.npc == null ? "market" : c.npc.getCivilizationId(), c.lat, c.lon, c.date);
    }

}
