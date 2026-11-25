package com.eternalengine.knowledge;

import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.civilization.ReputationStanding;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraBridge;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talking with a villager, the way the approved preview works (step 18).
 *
 * <p>The villager opens with today's news. The player picks a numbered question, clicks a gold name
 * ("Ask about"), picks another topic or types a name. Every answer is dated: the villager says what is
 * known on today's game date, nothing from the future. Court matters (level 2) are not street talk: a
 * street villager sends the player on ("Ask the clerks at King Kothi Palace"). Distances are measured from
 * where this villager stands. Each answer becomes a note in the player's knowledge (who said it, when).
 *
 * <p>Inside a knowledge pack (hand-checked cards for a region and dates) the pack's cards are used; the
 * basic cards from the history data ({@link LocalCards}) are always there as well.
 */
public final class TalkService {

    public static final String NEWS = "news";
    public static final String RULER = "ruler";
    public static final String PLACE = "place";
    /** Turn 221 (the user's doubt 3): where and when the player stands, always answerable. */
    public static final String HERE = "here";
    public static final String LIFE = "life";
    public static final String FAMOUS = "famous";
    public static final String SELF = "self";
    private static final Pattern DIST = Pattern.compile("\\{d:(\\w+)\\}");
    private static final int MAX_NAMES = 8;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, long[]> RATE = new ConcurrentHashMap<>();

    private TalkService() {
    }

    /** Where and when a talk happens, and with whom. */
    public static final class Ctx {
        final @Nullable UUID player;
        final @Nullable MinecraftServer server;
        final int entityId;
        final String npcName;
        final String role;
        final int level;
        final int date;
        final double lat;
        final double lon;
        final List<KnowledgePacks.Pack> packs;
        final LocalCards.Local local;
        /** Step 19b: the player who can sell or change money in this talk, and the villager (null in tests). */
        @Nullable ServerPlayer trader;
        @Nullable HumanNpcEntity npc;

        Ctx(@Nullable UUID player, @Nullable MinecraftServer server, int entityId, String npcName, String role, int level,
            int date, double lat, double lon, List<KnowledgePacks.Pack> packs, LocalCards.Local local) {
            this.player = player;
            this.server = server;
            this.entityId = entityId;
            this.npcName = npcName;
            this.role = role;
            this.level = level;
            this.date = date;
            this.lat = lat;
            this.lon = lon;
            this.packs = packs;
            this.local = local;
        }

        public int date() { return date; }
        /** Step 24: the villager's role ("merchant", "money_changer", ...). */
        public String role() { return role; }
        /** Step 21: the name the player sees for this villager. */
        public String name() { return npcName; }
        public int level() { return level; }
        public double lat() { return lat; }
        public double lon() { return lon; }
        public List<KnowledgePacks.Pack> packs() { return packs; }
        public LocalCards.Local local() { return local; }

        /** Who trades in this talk and with which villager (step 19b); both may be null. Returns this. */
        public Ctx with(@Nullable ServerPlayer trader, @Nullable HumanNpcEntity npc) {
            this.trader = trader;
            this.npc = npc;
            return this;
        }

        /** Step 27: the cards of people and groups worded for this talk (date and spot), by id. */
        private final Map<String, KnowledgeCard> worded = new java.util.HashMap<>();

        @Nullable
        public KnowledgeCard card(String id) {
            if (id == null) {
                return null;
            }
            for (KnowledgePacks.Pack p : packs) {
                KnowledgeCard c = p.cards().get(id);
                if (c != null) {
                    return c;
                }
            }
            KnowledgeCard c = local.cards().get(id);
            if (c != null || !PlaceCards.isStoreId(id)) {
                return c;
            }
            if (worded.containsKey(id)) {
                return worded.get(id);
            }
            KnowledgeCard k = null;
            try {
                k = PlaceCards.resolve(id, date, lat, lon);
            } catch (Throwable ignored) {
                // No card file: the name is unknown here.
            }
            worded.put(id, k);
            return k;
        }

        List<KnowledgeCard> allCards() {
            List<KnowledgeCard> out = new ArrayList<>();
            for (KnowledgePacks.Pack p : packs) {
                out.addAll(p.cards().values());
            }
            out.addAll(local.cards().values());
            for (String id : local.listed()) {
                KnowledgeCard k = card(id);
                if (k != null) {
                    out.add(k);
                }
            }
            return out;
        }

        boolean far(KnowledgeCard c) {
            return c.hasLocation() && !Double.isNaN(lat) && KnowledgePacks.Geo.km(lat, lon, c.lat(), c.lon()) > 15.0d;
        }
    }

    private static final class Session {
        final int entityId;
        String[] now = {"greet", "", ""};
        final Deque<String[]> back = new ArrayDeque<>();
        /** Step 21: who the talk is with, for the closing line when they are gone ("Lakshmi has left."). */
        String name = "";
        String job = "";
        int level = 1;
        String dateLabel = "";
        // The interaction sheet: the free engine's thread. What the talk is about now (the subject),
        // how much of the record has gone out, and whether a marked player just traded.
        String subject = "";
        int askRun;
        int tradeJustMarked;
        FreeEngine.R freeResult;
        /** The question open on the subject card, so "more" knows where it is. */
        String lastX = "";

        Session(int entityId) {
            this.entityId = entityId;
        }
    }

    // ---------------------------------------------------------------- context

    public static Ctx context(@Nullable ServerPlayer player, HumanNpcEntity npc) {
        TerraBridge.GeoPoint geo = null;
        try {
            geo = TerraCoordinateHelper.toGeoPoint(npc.blockPosition());
        } catch (Throwable ignored) {
            // No Terra++ world: no place, only the villager.
        }
        double lat = geo == null ? Double.NaN : geo.latitude();
        double lon = geo == null ? Double.NaN : geo.longitude();
        int today = KnowledgeDate.today();
        return context(player == null ? null : player.getUUID(), player == null ? null : player.getServer(), npc.getId(),
                nameOf(npc, lat, lon, today), npc.getRole(), lat, lon, today, -1).with(player, npc);
    }

    /** The person's role, for the layers outside the package. */
    public static String roleOf(Ctx c) {
        return c.role;
    }

    /** The day key of the talk, for the layers outside the package. */
    public static int dayOf(Ctx c) {
        return c.date;
    }

    /** True when the role holds a counter in the sheet (the red box and the trade screen are his). */
    public static boolean counterRole(String role) {
        return EraTalk.hasCounter(role);
    }

    /** The villager's name; a crowd figure (no name of its own) gets a stable one from the place's name list. */
    public static String nameOf(HumanNpcEntity npc, double lat, double lon, int date) {
        if (npc.hasCustomName() && npc.getCustomName() != null) {
            return npc.getCustomName().getString();
        }
        try {
            if (SimulationEngine.isInitialized() && SimulationEngine.getInstance().getVillagerManager() != null) {
                String n = SimulationEngine.getInstance().getVillagerManager().nameFor(npc.getCivilizationId(), npc.getRole(),
                        "female".equalsIgnoreCase(npc.getGender()), lat, lon, KnowledgeDate.year(date),
                        npc.getUUID().getLeastSignificantBits() ^ npc.getUUID().getMostSignificantBits());
                if (n != null && !n.isBlank()) {
                    return n;
                }
            }
        } catch (Throwable ignored) {
            // No name list here: a plain word.
        }
        return "Citizen";
    }

    /** A talk at a spot and date. {@code level} -1 means: from the role. */
    public static Ctx context(@Nullable UUID player, @Nullable MinecraftServer server, int entityId, String name, String role,
                              double lat, double lon, int date, int level) {
        boolean placed = !Double.isNaN(lat) && !Double.isNaN(lon);
        List<KnowledgePacks.Pack> packs = placed ? KnowledgePacks.covering(lat, lon, date) : List.of();
        LocalCards.Local local = LocalCards.Local.EMPTY;
        if (placed) {
            try {
                var civs = SimulationEngine.isInitialized() ? SimulationEngine.getInstance().getCivilizationManager() : null;
                local = LocalCards.build(com.eternalengine.core.WorldQuery.presence(civs, com.eternalengine.core.WorldKey.onDate(lat, lon, date)), lat, lon,
                        !packs.isEmpty());
            } catch (Throwable ignored) {
                // The history data could not answer: the villager still talks about the pack and itself.
            }
        }
        int lvl = level > 0 ? level : KnowledgeLevels.of(role);
        return new Ctx(player, server, entityId, name, role, lvl, date, lat, lon, packs, local);
    }

    // ---------------------------------------------------------------- the talk

    public static TalkView open(Ctx c) {
        Session s = new Session(c.entityId);
        remember(s, c);
        if (c.player != null) {
            SESSIONS.put(c.player, s);
        }
        return render(c, s, s.now);
    }

    private static void remember(Session s, Ctx c) {
        s.name = c.npcName == null ? "" : c.npcName;
        s.job = KnowledgeLevels.job(c.role);
        s.level = c.level;
        s.dateLabel = dateLabel(c.date);
    }

    /**
     * Step 21: the closing view of a talk whose person is gone ({@code walkedAway} false: "Lakshmi has left.") or out of
     * reach ("You are too far away from Lakshmi."). The talk is forgotten; the screen shows the line and Goodbye.
     */
    public static TalkView ended(@Nullable UUID player, int entityId, boolean walkedAway) {
        Session s = player == null ? null : SESSIONS.get(player);
        boolean known = s != null && s.entityId == entityId && !s.name.isBlank();
        String name = known ? s.name : "";
        String line = walkedAway
                ? (known ? "You are too far away from " + name + "." : "You are too far away.")
                : (known ? name + " has left." : "The person you were talking to has left.");
        TalkView v = TalkView.ended(entityId, name, known ? s.job : "", known ? s.level : 1, known ? s.dateLabel : "", line);
        if (player != null) {
            forget(player);
        }
        return v;
    }

    /**
     * The player did something on the talk screen. Returns the next view, or null when the talk ends.
     * Actions: ask (card, question), card (card), topic (topic id), topics, back, free (typed words), bye; step 19b:
     * sell (good id, "count:amount" as offered) and swap (money id, "to-money:amount" as offered).
     */
    @Nullable
    public static TalkView act(Ctx c, String action, String a, String b) {
        if ("bye".equals(action)) {
            if (c.player != null) {
                SESSIONS.remove(c.player);
            }
            return null;
        }
        Session s = c.player == null ? null : SESSIONS.get(c.player);
        if (s == null || s.entityId != c.entityId) {
            s = new Session(c.entityId);
            if (c.player != null) {
                SESSIONS.put(c.player, s);
            }
        }
        remember(s, c);
        if ("back".equals(action)) {
            s.now = s.back.isEmpty() ? new String[]{"greet", "", ""} : s.back.pop();
            if ("free".equals(s.now[0])) {
                s.freeResult = null; // an old line is re-derived from its own words, not the new decision
            }
            return render(c, s, s.now);
        }
        if ("show".equals(action)) {
            // Step 19c: back from the trade screen to where the talk was. A marked player hears it as
            // it is done.
            TalkView v = render(c, s, s.now);
            if (s.tradeJustMarked > 0) {
                v.text = RELUCT_TRADE_LINE + " " + v.text;
                s.tradeJustMarked = 0;
            }
            return v;
        }
        if ("track".equals(action)) {
            // Step 20: the tracker compass points to the place just named; the talk stays where it was.
            String said = c.trader == null ? "Only somebody standing here can track it." : BookService.track(c.trader, a);
            TalkView v = render(c, s, s.now);
            v.hint = said;
            v.canBack = !s.back.isEmpty();
            return v;
        }
        if ("chip".equals(action)) {
            // The sheet's chip row (the approved prototype's chips), in its own words: the event
            // by its first word, the ruler, the day's sight, how the trade is, the lane's trades,
            // the family. "swap" and "bye" travel as their own actions ("trade", "goodbye").
            String id = a == null ? "" : a;
            switch (id) {
                case "more" -> {
                    // The typed words themselves, re-derived the way the free field derives them.
                    s.freeResult = null;
                    s.back.push(s.now);
                    s.now = new String[]{"free", "go on", ""};
                    return render(c, s, s.now);
                }
                case "event" -> {
                    return topic(c, NEWS);
                }
                case "ruler" -> {
                    return topic(c, RULER);
                }
                case "landmark" -> {
                    return topic(c, PLACE);
                }
                case "howtrade" -> {
                    return topics(c, EraChips.tradeLine(c));
                }
                case "lane" -> {
                    return topics(c, EraChips.laneLine(c));
                }
                case "family" -> {
                    return topics(c, EraChips.familyLine(c));
                }
                default -> {
                    return topics(c, "What word is that? The lane has a name for it, and I would know it.");
                }
            }
        }
        if ("goodbye".equals(action)) {
            // The sheet's parting (the prototype's doBye): the line, and the chips clear until
            // the next word.
            TalkView v = topics(c, EraChips.BYE);
            v.chips.clear();
            return v;
        }
        if ("findrole".equals(action)) {
            // The sheet's "Ask where" pick: the player chose a role, and the lane says where.
            return topics(c, EraTalk.findLine(c, a == null ? "" : a));
        }
        if ("free".equals(action) && c.player != null && c.server != null) {
            // The sheet's two free decisions the talk itself must make: parting, and the trade (a real
            // container opens from here).
            ReputationStanding store = ReputationStanding.get(c.server.overworld());
            ReputationStanding.Standing ps = store.standing(c.player, c.date);
            FreeEngine.R r = FreeEngine.free(c, s, ps, person(c), a);
            if (FreeEngine.BYE.equals(r.k)) {
                forget(c.player);
                // A typed goodbye closes the talk the way the close does — with a line, never with a stuck box.
                return TalkView.ended(c.entityId, c.npcName, c.role, c.level, dateLabel(c.date), "Go well.");
            }
            if (FreeEngine.TRADE.equals(r.k)) {
                ServerPlayer trader = c.trader;
                Entity e = trader != null ? trader.level().getEntity(c.entityId) : null;
                boolean marked = "marked".equals(ReputationStanding.tier(ps.posRep, ps.negRep));
                // The old trade screen first (owner's choice, 2026-11-16): Minecraft's
                // trade menu wherever this person can trade on it.
                if (trader != null && e instanceof HumanNpcEntity npc && npc.isAlive()
                        && com.eternalengine.money.TradeMenu.open(trader, npc)) {
                    store.tradeDone(c.player, npc.getUUID(), c.date, 10);
                    if (marked) {
                        s.tradeJustMarked = 1;
                    }
                    s.now = new String[]{"greet", "", ""};
                    return render(c, s, s.now);
                }
                // Where the old screen cannot open: the sheet's trade screen for a
                // counter role — the person's line (the standing decides it), the
                // goods and prices of the place-year, or the honest line. Opening
                // it is the trade.
                if (EraTalk.hasCounter(c.role)) {
                    TradePanelView pv = EraTradePanel.panel(c, ReputationStanding.tier(ps.posRep, ps.negRep));
                    if (pv != null) {
                        EraTradePanel.exec(c, person(c));
                        s.now = new String[]{"greet", "", ""};
                        TalkView v = render(c, s, s.now);
                        v.tradePanel = pv;
                        return v;
                    }
                }
                // The era's rule where the sheet has no counter: the counter line in the talk box,
                // with the goods and prices of the place-year, or the honest line.
                TalkView era = eraTrade(c);
                if (era != null) {
                    return era;
                }
                s.now = new String[]{"greet", "", ""};
                TalkView v = render(c, s, s.now);
                v.text = (marked ? RELUCT_TRADE_LINE + " " : "") + "I have nothing to trade.";
                return v;
            }
            s.freeResult = r;
        }
        String[] next = switch (action == null ? "" : action) {
            case "ask", "card", "topic", "free", "topics" -> new String[]{action, a == null ? "" : a, b == null ? "" : b};
            default -> new String[]{"greet", "", ""};
        };
        s.back.push(s.now);
        while (s.back.size() > 24) {
            s.back.removeLast();
        }
        s.now = next;
        // The sheet's thread: what the talk is about now, and the question open on it.
        if ("ask".equals(next[0]) || "card".equals(next[0])) {
            s.subject = next[1];
            s.lastX = "ask".equals(next[0]) ? next[2] : "";
        }
        return render(c, s, next);
    }

    /**
     * Step 19c: a seller's wares as text for {@code /ee talk ... trade}, and "buy" (offer id, price as shown) buys
     * without the trade screen. Null when this villager sells nothing. (Players trade on Minecraft's trade screen.)
     */
    @Nullable
    public static com.eternalengine.money.TradeView trade(Ctx c, String action, String a, String b) {
        return NewsTalk.trade(c, action, a, b);
    }

    // ------------------------------------------------------------------------- step 24: the trade screen

    /** The seller in a talk (who, where, when), for the trade screen. */
    public static com.eternalengine.money.ShopService.Seller seller(Ctx c) {
        return NewsTalk.seller(c);
    }

    /** What this person pays and keeps (a merchant's terms, less for a trader, a pedlar or anybody else). */
    public static com.eternalengine.money.MarketService.Terms terms(Ctx c) {
        return MarketTalk.terms(c);
    }

    /** True where people have no money and this person swaps a little (anybody but news people and changers). */
    public static boolean swapsALittle(Ctx c) {
        return MarketTalk.swapsALittle(c);
    }

    /** What a money-changer says of his trade: into which money, his cut, and the money taken here. */
    public static String changerWords(Ctx c) {
        return MarketTalk.changerText(c);
    }

    /** True if this player may act now (at most 8 actions a second). */
    public static boolean allow(UUID player) {
        long now = System.currentTimeMillis();
        long[] r = RATE.computeIfAbsent(player, k -> new long[]{now, 0});
        synchronized (r) {
            if (now - r[0] > 1000) {
                r[0] = now;
                r[1] = 0;
            }
            return ++r[1] <= 8;
        }
    }

    public static void forget(UUID player) {
        SESSIONS.remove(player);
        RATE.remove(player);
        TalkHold.release(player); // step 21: the person is free to go
    }

    private static TalkView render(Ctx c, Session s, String[] state) {
        TalkView v = switch (state[0]) {
            case "ask" -> answer(c, state[1], state[2]);
            case "card" -> answer(c, state[1], null);
            case "topic" -> topic(c, state[1]);
            case "free" -> free(c, s, state[1]);
            case "topics" -> topics(c, "What would you like to know?");
            default -> greet(c);
        };
        v.canBack = !s.back.isEmpty();
        v.debugAskRun = s.askRun;
        // The sheet's chips are the sheet's way to ask: when they are here, the numbered
        // questions are not (the approved prototype's box has the chip row, not a numbered list).
        if (!v.chips.isEmpty()) {
            v.options.clear();
        }
        // The screen draws nine options (TalkLayout: Math.min(9, view.options.size())) and the rest
        // were unreachable - the opening at Hyderabad 1948 offered eleven. The list is built in
        // priority order (the trade, the news, the land, the person), so the tail is what goes.
        while (v.options.size() > SCREEN_OPTIONS) {
            v.options.remove(v.options.size() - 1);
        }
        return v;
    }

    /** How many numbered choices the talk screen has room for. */
    static final int SCREEN_OPTIONS = 9;

    // ---------------------------------------------------------------- the sheet's grey lines
    // The interaction sheet's human layer, era-neutral: the record (news, cards, the date, the ruler)
    // is the pack's own; these are the street's own words around it.
    static final String BOUND_LINE = "I told you — I keep this bench, I am no court scribe. Ask the clerks if it is court business.";
    static final String NEWS3_LINE = "That is all I have. The rest is court talk — ask the clerks, not me.";
    static final String RELUCT_LINE = "Fine. I will tell you — for the trade, not for the road.";
    static final String RELUCT_TRADE_LINE = "I am dealing with you for the goods, not for your face. Have the coin ready.";
    static final String TRADE_MEM_LINE = " And the goods you took — the price held, and that is the best I can say for them.";
    static final String SEEN_LINE = "I was at my bench all day, and the lane was my witness. Ask me what, and I will say it straight.";
    static final String ROUNDS_LINE = "Up before the light, to the work; the work while the heat is on; and the count at dusk. That is the day of my trade in this city.";
    static final String FAMILY_LINE = "The house is small, and the temper of it is steady. That is all I ask of it.";
    static final String COTTON_LINE = "The price is dear this week — everyone is buying. I want it to settle, and so does the lane.";

    static TalkView base(Ctx c) {
        TalkView v = new TalkView();
        v.entityId = c.entityId;
        v.npcName = c.npcName;
        v.job = KnowledgeLevels.job(c.role);
        v.level = c.level;
        v.dateLabel = dateLabel(c.date);
        // The big button (the sheet's red box): its word, and what it does. The era's rule —
        // the twenty-three counter roles hold a box, the other thirteen do not.
        v.actionKind = EraTalk.actionKind(c.role);
        v.actionLabel = EraTalk.actionWord(c.role);
        // What the trade carries, dim under the roles band (the sheet's items), dressed for the era.
        String eraItems = EraTalk.itemsLine(c);
        v.items = eraItems.isEmpty() ? HumanNpcEntity.roleItems(c.role) : eraItems;
        // The sheet's mood line: the time of day, and how the street reads the player. The values go to
        // the debug screen only — for now.
        if (c.player != null && c.server != null) {
            int hour = (int) (c.server.overworld().getDayTime() / 1000L + 6L) % 24;
            ReputationStanding.Standing ps = standing(c);
            ReputationStanding.Standing.Person p = person(c);
            String tier = ps == null ? "neutral" : ReputationStanding.tier(ps.posRep, ps.negRep);
            // Grounded in the day (the town's news, when there is any) and the bond, then toward the player.
            String moodBaseLine = packNews(c) != null ? "the day is on his mind" : moodBase(hour);
            v.mood = moodBaseLine + bondTail(p == null ? 0 : p.bond) + ReputationStanding.tierSuffix(tier);
            v.moodDot = FreeEngine.moodDot(tier);
            if (ps != null) {
                v.posRep = ps.posRep;
                v.negRep = ps.negRep;
                v.tier = tier;
                v.debugYou = ps.playerName == null || ps.playerName.isEmpty()
                        ? (c.trader != null ? c.trader.getName().getString() : "")
                        : ps.playerName;
            }
            if (p != null) {
                v.meetings = p.meetings;
                v.debugLastQ = p.lastQ == null ? "" : p.lastQ;
                v.debugNews = p.newsCount;
                v.debugBond = p.bond;
            }
            // The sheet's chips (the approved prototype's chip row): when they are here, the
            // numbered questions are not — the chips are the sheet's way to ask.
            java.util.List<TalkView.Chip> chips = EraChips.chips(c, p);
            if (chips != null) {
                v.chips.addAll(chips);
            }
        }
        return v;
    }

    /** The mood's bond tail: how long the two have been talking. */
    static String bondTail(int bond) {
        if (bond >= 6) {
            return " · glad you came by";
        }
        if (bond >= 3) {
            return " · your familiar face";
        }
        return "";
    }

    /** The mood's base line, by the hour of the day. */
    static String moodBase(int hour) {
        if (hour >= 6 && hour < 11) {
            return "early, busy";
        }
        if (hour < 16) {
            return "settled, busy";
        }
        if (hour < 20) {
            return "quiet, watchful";
        }
        return "tired, glad of it";
    }

    /**
     * The sheet's opening: proportional, and <b>this person's</b>. First meeting with this man, one line
     * and no menu — the field is open. Same day with him, the short line. The next day, his remembered
     * line (what the player asked him, what the two traded, how the street reads the player now — the
     * meters are the city's, the memory is his).
     */
    static TalkView greet(Ctx c) {
        ReputationStanding.Standing ps = standing(c);
        ReputationStanding.Standing.Person p = person(c);
        String line;
        if (p == null) {
            line = firstGreeting(c);
        } else if (p.meetings == 0) {
            line = firstGreeting(c);
            p.meetings++;
        } else if (p.greetedDay == c.date) {
            line = "Still here? Ask the rest, then — the day is going on.";
        } else {
            boolean hadMem = false;
            line = "Good to see you again.";
            if (p.lastQ != null && !p.lastQ.isEmpty() && p.lastQDay == c.date - 1) {
                line += " " + memLine(c, p);
                if (p.tradeUsed && !"trade".equals(p.lastQ)) {
                    line += TRADE_MEM_LINE;
                }
                hadMem = true;
            } else if (p.lastQ != null && !p.lastQ.isEmpty()) {
                line += " You came to the lane the other day.";
            }
            String tier = ps == null ? "neutral" : ReputationStanding.tier(ps.posRep, ps.negRep);
            if (!hadMem) {
                if ("marked".equals(tier)) {
                    line += " I keep my answers short with you now. The road is watched, and so am I.";
                } else if ("trusted".equals(tier)) {
                    line += " You are one of the good faces in this street — they say so.";
                }
            }
            if (ps != null && ps.playerName != null && !ps.playerName.isEmpty()) {
                line = line.replaceFirst("^Good to see you again\\.",
                        "Good to see you again, " + capw(ps.playerName) + ".");
            }
        }
        if (p != null) {
            p.greetedDay = c.date;
            p.greetText = line;
        }
        TalkView v = topics(c, line);
        // Proportional: the first meeting offers no menu — the words are open.
        v.options.clear();
        return v;
    }

    /**
     * The first-meeting greeting, in proportion to the place and the year: the era's own word first
     * (the approved prototype's greeting for the band), then the pack's, then the plain word.
     */
    private static String firstGreeting(Ctx c) {
        String era = EraTalk.greetLine(c);
        if (era != null && !era.isBlank()) {
            return era;
        }
        String line = c.local.greeting();
        if (line == null || line.isBlank()) {
            return "Good day to you.";
        }
        return line;
    }

    /** The remembered line: what the player asked this man the other day. */
    private static String memLine(Ctx c, ReputationStanding.Standing.Person p) {
        String title = "news".equals(p.lastQ) ? "the news"
                : "ruler".equals(p.lastQ) ? "who rules"
                : cardTitle(c, p.lastQ);
        return "You asked about " + (title == null ? p.lastQ : title) + " yesterday.";
    }

    private static String cardTitle(Ctx c, String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        KnowledgeCard k = c.card(id);
        return k == null ? null : k.title();
    }

    static String capw(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static TalkView topics(Ctx c, String text) {
        TalkView v = base(c);
        v.text = text;
        addTopicOptions(c, v);
        addNames(c, v, null);
        heardNames(c, v.text);
        return v;
    }

    private static void addTopicOptions(Ctx c, TalkView v) {
        if (packNews(c) != null || c.local.newsCard() != null) {
            v.options.add(new TalkView.Option("topic", NEWS, "", "What's the news?"));
        }
        // Turn 221 (doubt 3: "less options for interaction"): the ruler and place questions used to
        // appear only where a knowledge card existed, so the empty places - exactly where a player
        // has most questions - offered the fewest options. The registers answer these world-wide,
        // so the questions are always here now, backed by RulerRegister and HereAndNow when no card
        // does. The same for "where am I": the reading of the ground underfoot.
        v.options.add(new TalkView.Option("topic", HERE, "", "Where am I, and what year is it?"));
        v.options.add(new TalkView.Option("topic", RULER, "", "Who rules here?"));
        v.options.add(new TalkView.Option("topic", PLACE, "", "What is this place?"));
        KnowledgeCard place = c.card(c.local.placeCard());
        if (place != null && place.question("life") != null) {
            v.options.add(new TalkView.Option("topic", LIFE, "", "How do people live here?"));
        }
        if (c.local.famousCard() != null) {
            v.options.add(new TalkView.Option("topic", FAMOUS, "", "Anyone famous from here?"));
        }
        if (NewsTalk.hasBelief(c)) {
            // Step 37 (C29): what people here believe, in this year
            v.options.add(new TalkView.Option("topic", NewsTalk.BELIEF, "", "What do people here believe?"));
        }
        v.options.add(new TalkView.Option("topic", SELF, "", "Tell me about yourself."));
        MarketTalk.addOptions(c, v);
        NewsTalk.addOptions(c, v);
        // The lane's register (the sheet's find-a-role): who is where, in this place and year. Last,
        // so the box's nine keep the trade and the person; the words always reach it, too.
        if (EraTalk.has()) {
            v.options.add(new TalkView.Option("topic", EraTalk.FIND, "", "Who is where, around here?"));
        }
    }

    static TalkView topic(Ctx c, String t) {
        switch (t) {
            case NEWS -> {
                KnowledgePacks.News news = packNews(c);
                if (news != null) {
                    TalkView v = topics(c, expand(c, news.text()));
                    v.options.removeIf(o -> NEWS.equals(o.a()));
                    return v;
                }
                if (c.local.newsCard() != null) {
                    return answer(c, c.local.newsCard(), "what");
                }
                return topics(c, "Nothing much this year, that I know of.");
            }
            case EraTalk.COUNTER -> {
                return EraTalk.counterView(c);
            }
            case EraTalk.FIND -> {
                return EraTalk.findView(c);
            }
            case RULER, PLACE -> {
                // The era's record first (the approved prototype's own words); the registers where it is silent.
                String era = RULER.equals(t) ? EraTalk.rulerLine(c) : EraTalk.placeLine(c);
                if (era != null) {
                    return topics(c, era);
                }
                String[] pt = packTopic(c, t);
                if (pt != null) {
                    return answer(c, pt[0], pt[1]);
                }
                String card = RULER.equals(t) ? c.local.rulerCard() : c.local.placeCard();
                if (card != null) {
                    return answer(c, card, RULER.equals(t) ? "who" : "what");
                }
                // Turn 221 (doubt 3): no card is no longer a dead end. The registers answer where
                // the stories are silent, and say so in their own marked words.
                int year = com.eternalengine.knowledge.KnowledgeDate.year(c.date);
                if (RULER.equals(t)) {
                    var hit = com.eternalengine.civilization.WhoRuled.hit(c.lat, c.lon, year);
                    return topics(c, hit.map(h -> "The stories I know do not name who holds this land,"
                            + " but the land answers to " + h.ruler() + ".")
                            .orElse("I couldn't tell you who rules here."));
                }
                com.eternalengine.here.HereAndNow.Reading page =
                        com.eternalengine.here.HereAndNow.read(c.lat, c.lon, year);
                return topics(c, page.lines().size() > 1
                        ? "This place? " + page.lines().get(1)
                        : "Just a place. People live here.");
            }
            case HERE -> {
                String eraHere = EraTalk.hereLine(c);
                if (eraHere != null) {
                    return topics(c, eraHere);
                }
                com.eternalengine.here.HereAndNow.Reading page = com.eternalengine.here.HereAndNow.read(
                        c.lat, c.lon, com.eternalengine.knowledge.KnowledgeDate.year(c.date));
                StringBuilder say = new StringBuilder(page.shortLine());
                for (String line : page.lines()) {
                    if (line.startsWith("The ruler the data names here is")
                            || line.startsWith("The register names no ruler")) {
                        say.append(' ').append(line);
                    }
                }
                return topics(c, say.toString());
            }
            case LIFE -> {
                return c.local.placeCard() == null ? topics(c, "We work, we eat, we sleep.") : answer(c, c.local.placeCard(), "life");
            }
            case FAMOUS -> {
                String eraFamous = EraTalk.famousLine(c);
                if (eraFamous != null) {
                    return topics(c, eraFamous);
                }
                return c.local.famousCard() == null ? topics(c, "No one I could name.") : answer(c, c.local.famousCard(), "who");
            }
            case MarketTalk.SELL -> {
                return MarketTalk.buyer(c) ? MarketTalk.sellView(c, null) : topics(c, "I don't buy goods. Ask in the market.");
            }
            case MarketTalk.SWAP -> {
                return MarketTalk.changer(c) ? MarketTalk.swapView(c, null) : topics(c, "I don't change money. Ask a money-changer.");
            }
            case MarketTalk.WHERE -> {
                return MarketTalk.whereView(c);
            }
            case NewsTalk.CRIER -> {
                return com.eternalengine.news.NewsEras.TOWN_CRIER.equals(NewsTalk.role(c)) ? NewsTalk.crierView(c) : topics(c, "I am no crier. Listen in the market.");
            }
            case NewsTalk.WHERE -> {
                return NewsTalk.whereView(c);
            }
            case NewsTalk.NOTES -> {
                return NewsTalk.notesView(c);
            }
            case NewsTalk.BELIEF -> {
                return NewsTalk.hasBelief(c) ? NewsTalk.beliefView(c)
                        : topics(c, "I could not tell you what people believe here.");
            }
            default -> {
                String job = KnowledgeLevels.job(c.role);
                String text = "I am " + c.npcName + ", " + article(job) + " " + job + "." + MarketTalk.selfLine(c.role)
                        + (c.level >= 2 ? " I serve at court, so I hear the reports." : " I hear what people say in the street.");
                TalkView v = topics(c, text);
                v.options.removeIf(o -> SELF.equals(o.a()));
                return v;
            }
        }
    }

    /**
     * The interaction sheet's free field: what the person does with anything you say. Words, not sentence
     * shapes — any order, any filler. The engine (FreeEngine) decides, in the sheet's own order: parting, the
     * trade, "more", greetings, the person, the place, the paper, what he saw, the news, the date, the mood,
     * the record (a card, or an opinion, or a "how do you know"), thanks, weather, the bell, the water, the
     * price, the city's deeds (the standing moves), the road, the cotton, the day, the family, the ruler, the
     * player's own name and work, a backchannel, and — if nothing else, a parry.
     */
    static TalkView free(Ctx c, Session s, String words) {
        if (s.freeResult == null) {
            s.freeResult = FreeEngine.free(c, s, standing(c), person(c), words);
        }
        return freeView(c, s, s.freeResult);
    }

    /**
     * The counter, opened as a trade (the sheet's trade screen for the counter roles the real
     * container does not serve): the goods and prices of the place-year, or the honest line. The
     * lane remembers the trade, as it remembers a real one (+10). Null when the role holds no
     * counter.
     */
    public static TalkView eraTrade(Ctx c) {
        if (!EraTalk.hasCounter(c.role)) {
            return null;
        }
        if (c.player != null && c.server != null && c.npc != null) {
            ReputationStanding.get(c.server.overworld()).tradeDone(c.player, c.npc.getUUID(), c.date, 10);
        }
        return EraTalk.counterView(c);
    }

    /** The standing of the player in this city (the sheet's two meters), read for today. */
    private static ReputationStanding.Standing standing(Ctx c) {
        return c.player == null || c.server == null
                ? null
                : ReputationStanding.get(c.server.overworld()).standing(c.player, c.date);
    }

    /**
     * What <b>this person</b> remembers of the player: the meetings, the last question, the trade, the
     * greeting. Null when the person is unknown (test talks) — then there is simply nobody to remember.
     */
    private static ReputationStanding.Standing.Person person(Ctx c) {
        if (c.player == null || c.server == null || c.npc == null) {
            return null;
        }
        return ReputationStanding.get(c.server.overworld()).person(c.player, c.npc.getUUID(), c.date);
    }

    /** The view the engine's decision becomes on the box. */
    private static TalkView freeView(Ctx c, Session s, FreeEngine.R r) {
        ReputationStanding.Standing ps = standing(c);
        ReputationStanding.Standing.Person p = person(c);
        switch (r.k) {
            case FreeEngine.GREET -> {
                String g = p == null || p.greetText.isEmpty() ? "Good evening." : p.greetText;
                return topics(c, g);
            }
            case FreeEngine.KNOW -> {
                s.askRun = 0;
                if (s.subject.isEmpty()) {
                    return topics(c, "I have lived in this city long enough. What I know, I know from the lane.");
                }
                return answer(c, s.subject, "how");
            }
            case FreeEngine.MORE -> {
                return doMore(c, s, r);
            }
            case FreeEngine.HUMAN -> {
                boolean bound = s.askRun >= 3;
                s.askRun = 0;
                if (p != null && r.refer) {
                    p.courtPush++;
                    if (p.courtPush >= 2) {
                        return topics(c, BOUND_LINE);
                    }
                }
                String t = r.refer ? expand(c, r.text) : r.text;
                if (bound) {
                    t = t + " " + BOUND_LINE;
                }
                return topics(c, t);
            }
            case FreeEngine.CARD -> {
                s.subject = r.card;
                s.lastX = r.x == null ? "" : r.x;
                s.askRun++;
                return answer(c, r.card, r.x);
            }
            case FreeEngine.TOPIC -> {
                if ("news".equals(r.x)) {
                    s.askRun++;
                    s.subject = "news";
                    if (p != null) {
                        p.lastQ = "news";
                        p.lastQDay = c.date;
                        p.bond++;
                    }
                    String n = newsLine(c, p);
                    return topics(c, n != null ? expand(c, n) : NEWS3_LINE);
                }
                if ("ruler".equals(r.x)) {
                    s.askRun++;
                    s.subject = "ruler";
                    if (p != null) {
                        p.lastQ = "ruler";
                        p.lastQDay = c.date;
                        p.bond++;
                    }
                    return topic(c, RULER);
                }
                s.askRun = 0;
                s.subject = r.x;
                return switch (r.x) {
                    case "wherehere" -> topic(c, HERE);
                    case "self" -> {
                        // The id card, the approved prototype's words: the trade kept in the place,
                        // or the lane, where there is no trade.
                        String idCard = EraChips.idCard(c);
                        yield topics(c, idCard != null ? idCard : "I am " + c.npcName + ", " + KnowledgeLevels.job(c.role)
                                + ". I have kept this bench in this city, and the city knows me by it.");
                    }
                    case "newswhere" -> topics(c, newsWhereLine(c));
                    case "seen" -> topics(c, SEEN_LINE);
                    case "rounds" -> topics(c, ROUNDS_LINE);
                    case "family" -> topics(c, FAMILY_LINE);
                    case "cotton" -> topics(c, COTTON_LINE);
                    case "famous" -> topic(c, FAMOUS);
                    case EraTalk.COUNTER -> topic(c, EraTalk.COUNTER);
                    case EraTalk.FIND -> topic(c, EraTalk.FIND);
                    default -> topics(c, "I do not know that word. Is it court talk?");
                };
            }
            default -> {
                return topics(c, "I don't know anything about that.");
            }
        }
    }

    /** "More": on a card, the next answer or the next question; on the news, the second line; else, the end. */
    private static TalkView doMore(Ctx c, Session s, FreeEngine.R r) {
        if (r.card != null && !r.card.isEmpty()) {
            KnowledgeCard card = c.card(r.card);
            if (card != null) {
                List<KnowledgeCard.Question> qs = card.questions();
                int idx = -1;
                for (int i = 0; i < qs.size(); i++) {
                    if (qs.get(i).id().equals(r.x)) {
                        idx = i;
                        break;
                    }
                }
                String next = idx >= 0 && idx + 1 < qs.size() ? qs.get(idx + 1).id()
                        : (idx > 0 ? qs.get(idx - 1).id() : null);
                if (next != null) {
                    return answer(c, card.id(), next);
                }
                s.askRun = 0;
                return topics(c, "That is all I have on that. The rest is court talk.");
            }
        }
        ReputationStanding.Standing.Person p = person(c);
        if (p != null && p.newsCount >= 1) {
            String n = newsLine(c, p);
            return topics(c, n != null ? expand(c, n) : NEWS3_LINE);
        }
        s.askRun = 0;
        return topics(c, NEWS3_LINE);
    }

    /** The town's news, line by line: the first for, then the second, then nothing but the court talk. */
    private static String newsLine(Ctx c, ReputationStanding.Standing.Person p) {
        int want;
        if (p == null) {
            want = 1;
        } else if (p.newsDay != c.date) {
            p.newsDay = c.date;
            p.newsCount = 1;
            want = 1;
        } else {
            p.newsCount++;
            want = p.newsCount;
        }
        List<KnowledgePacks.News> live = new ArrayList<>();
        for (KnowledgePacks.Pack pk : c.packs) {
            for (KnowledgePacks.News n : pk.news()) {
                if (n.from() <= c.date && n.level() <= c.level && c.date <= n.to()) {
                    boolean dup = false;
                    for (KnowledgePacks.News o : live) {
                        if (o.text().equals(n.text())) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) {
                        live.add(n);
                    }
                }
            }
        }
        live.sort((a, b) -> Integer.compare(b.from(), a.from()));
        return want >= 1 && want <= live.size() ? live.get(want - 1).text() : null;
    }

    /** Where the news comes from, in this town (the paper, and what it costs). */
    private static String newsWhereLine(Ctx c) {
        String paper = null;
        for (KnowledgePacks.Pack p : c.packs) {
            if (p.paperName() != null && !p.paperName().isBlank()) {
                paper = p.paperName();
                break;
            }
        }
        return paper == null
                ? "The paper seller at the chowk. Ask him, not me."
                : "The paper seller at the chowk. The " + paper + ".";
    }

    static TalkView answer(Ctx c, String cardId, @Nullable String qid) {
        KnowledgeCard card = c.card(cardId);
        if (card == null) {
            return topics(c, "I don't know anything about that.");
        }
        if (!card.knownOn(c.date)) {
            return topics(c, "I haven't heard of that.");
        }
        TalkView v = base(c);
        v.cardTitle = card.title();
        v.cardKind = card.kind();
        if ("how".equals(qid)) {
            v.text = KnowledgeLevels.howIKnow(c.level, card.kind(), c.far(card));
            addQuestionOptions(c, v, card, "how");
            addNames(c, v, card);
            return v;
        }
        KnowledgeCard.Question q = qid == null ? firstQuestion(c, card) : card.question(qid);
        if (q == null) {
            q = firstQuestion(c, card);
        }
        boolean court = card.level() > c.level || (q != null && q.level() > c.level);
        if (court) {
            String[] refer = refer(c);
            v.text = expand(c, refer[0]);
            v.hint = trackHint(c, refer[1]);
            addTopicOptions(c, v);
            addNames(c, v, null);
            heardNames(c, v.text);
            return v;
        }
        KnowledgeCard.Answer ans = q == null ? null : q.at(c.date);
        if (ans == null) {
            v.text = "Nobody knows much about that yet.";
            addQuestionOptions(c, v, card, q == null ? "" : q.id());
            addNames(c, v, card);
            return v;
        }
        v.text = expand(c, ans.text());
        String track = ans.track() != null && !ans.track().isEmpty() ? ans.track()
                : ("where".equals(q.id()) && card.hasLocation() ? card.id() : null);
        v.hint = trackHint(c, track);
        addQuestionOptions(c, v, card, q.id());
        v.options.add(new TalkView.Option("ask", card.id(), "how", "How do you know?"));
        if (!v.hint.isEmpty() && track != null) {
            // Step 20: ask "Where?", then track it: the compass points there.
            v.options.add(new TalkView.Option("track", track, "", "Track it on my compass"));
        }
        addNames(c, v, card);
        heardNames(c, v.text);
        note(c, card, q.label(), v.text);
        // The marked player: the answer is given, and the way it is given is its own line — the record
        // above stays what the record was.
        ReputationStanding.Standing ps = standing(c);
        if (ps != null && "marked".equals(ReputationStanding.tier(ps.posRep, ps.negRep))) {
            v.text = RELUCT_LINE + " " + v.text;
        }
        return v;
    }

    private static KnowledgeCard.Question firstQuestion(Ctx c, KnowledgeCard card) {
        for (KnowledgeCard.Question q : card.questions()) {
            if (q.level() <= c.level && q.at(c.date) != null) {
                return q;
            }
        }
        return card.questions().isEmpty() ? null : card.questions().get(0);
    }

    private static void addQuestionOptions(Ctx c, TalkView v, KnowledgeCard card, String current) {
        for (KnowledgeCard.Question q : card.questions()) {
            if (!q.id().equals(current) && q.at(c.date) != null) {
                v.options.add(new TalkView.Option("ask", card.id(), q.id(), q.label()));
            }
        }
    }

    /** "Ask about": names in the text first, then the card's own links that are known by now. */
    private static void addNames(Ctx c, TalkView v, @Nullable KnowledgeCard card) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String[] l : TalkView.links(v.text)) {
            KnowledgeCard k = c.card(l[0]);
            if (k != null && k.knownOn(c.date) && (card == null || !k.id().equals(card.id()))) {
                names.put(k.id(), k.shortName());
            }
        }
        if (card != null) {
            for (String id : card.links()) {
                KnowledgeCard k = c.card(id);
                if (k != null && k.knownOn(c.date) && !k.id().equals(card.id())) {
                    names.putIfAbsent(k.id(), k.shortName());
                }
            }
        }
        names.entrySet().stream().limit(MAX_NAMES).forEach(e -> v.names.add(new TalkView.Name(e.getKey(), e.getValue())));
    }

    // ---------------------------------------------------------------- notes

    private static void heardNames(Ctx c, String text) {
        PlayerKnowledge pk = knowledge(c);
        if (pk == null) {
            return;
        }
        for (String[] l : TalkView.links(text)) {
            KnowledgeCard k = c.card(l[0]);
            if (k != null) {
                pk.heard(c.player, k.id(), k.title(), k.kind());
            }
        }
    }

    private static void note(Ctx c, KnowledgeCard card, String question, String text) {
        PlayerKnowledge pk = knowledge(c);
        if (pk != null) {
            boolean first = pk.note(c.player, card.id(), card.title(), card.kind(),
                    new PlayerKnowledge.Note(c.date, c.npcName + ", " + KnowledgeLevels.job(c.role), question, text));
            if (first) {
                // Step 20: the journal.
                pk.journal(c.player, c.date, "Learned about " + card.title() + " from " + c.npcName + ".", card.id());
            }
        }
        // This man's memory: what the player last asked him, on what day, and the bond of register answers.
        ReputationStanding.Standing.Person p = person(c);
        if (p != null) {
            p.lastQ = card.id();
            p.lastQDay = c.date;
            p.bond++;
        }
    }

    @Nullable
    private static PlayerKnowledge knowledge(Ctx c) {
        if (c.player == null || c.server == null) {
            return null;
        }
        try {
            return PlayerKnowledge.get(c.server);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    @Nullable
    private static KnowledgePacks.News packNews(Ctx c) {
        for (KnowledgePacks.Pack p : c.packs) {
            KnowledgePacks.News n = p.newsFor(c.date, c.level);
            if (n != null) {
                return n;
            }
        }
        return null;
    }

    /** {card, question} for an everyday topic inside a pack ("ruler" → "hyderabad_state.who"), or null. */
    @Nullable
    private static String[] packTopic(Ctx c, String topic) {
        for (KnowledgePacks.Pack p : c.packs) {
            String ref = p.topics().get(topic);
            if (ref != null && ref.contains(".")) {
                String[] parts = ref.split("\\.", 2);
                KnowledgeCard k = p.cards().get(parts[0]);
                if (k != null && k.knownOn(c.date)) {
                    return parts;
                }
            }
        }
        return null;
    }

    private static String[] refer(Ctx c) {
        for (KnowledgePacks.Pack p : c.packs) {
            if (p.referText() != null && !p.referText().isBlank()) {
                return new String[]{p.referText(), p.referTrack()};
            }
        }
        return new String[]{"That's court business. I wouldn't know. Someone who serves at court might.", null};
    }

    private static String trackHint(Ctx c, @Nullable String cardId) {
        KnowledgeCard t = cardId == null ? null : c.card(cardId);
        if (t == null || !t.hasLocation() || Double.isNaN(c.lat)) {
            return "";
        }
        return t.shortName() + ": " + KnowledgePacks.Geo.phrase(c.lat, c.lon, t.lat(), t.lon());
    }

    /** Fills in {@code {d:card}} with the distance from this villager: "about 240 km west of here". */
    static String expand(Ctx c, String text) {
        if (text == null || !text.contains("{d:")) {
            return text == null ? "" : text;
        }
        Matcher m = DIST.matcher(text);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            KnowledgeCard t = c.card(m.group(1));
            String phrase = t != null && t.hasLocation() && !Double.isNaN(c.lat)
                    ? KnowledgePacks.Geo.phrase(c.lat, c.lon, t.lat(), t.lon()) : "not far from here";
            m.appendReplacement(b, Matcher.quoteReplacement(phrase));
        }
        m.appendTail(b);
        return b.toString();
    }

    public static String dateLabel(int date) {
        String label = KnowledgeDate.label(date);
        int y = KnowledgeDate.year(date);
        if (y >= 1583 && y <= 9999) {
            try {
                LocalDate d = LocalDate.of(y, KnowledgeDate.month(date), KnowledgeDate.day(date));
                return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", " + label;
            } catch (Exception ignored) {
                // Not a real day: just the label.
            }
        }
        return label;
    }

    static String article(String word) {
        return word != null && !word.isEmpty() && "aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an" : "a";
    }

    /**
     * The sheet's free engine (npc_interaction_gui.html, ported point for point): what the person does
     * with anything you say. Words, not sentence shapes — any order, any filler. The decisions come in
     * the sheet's own order: parting, the trade, "more", greetings, the person, the place, the paper,
     * what he saw, the news, the date, the mood, "how do you know", thanks, weather, the bell, the
     * water, the price, the city's deeds (the standing moves here), the road, the cotton, the day, the
     * family, the ruler, the player's own name and work, a backchannel, and — if nothing else, the
     * sentence engine (entity + question + pronoun + "yesterday" + opinion), a refer, and a parry.
     */
    static final class FreeEngine {
        static final String BYE = "bye";
        static final String TRADE = "trade";
        static final String MORE = "more";
        static final String GREET = "greet";
        static final String CARD = "card";
        static final String HUMAN = "human";
        static final String TOPIC = "topic";
        static final String KNOW = "know";

        private static final String HEM_LINE = "It is nothing. You are welcome on this lane.";
        private static final String HAGGLE_LINE = "The price is the price — the market makes it, not I. If you have the coin, we are done in a minute.";
        private static final String BELL_LINE = "The bell at dusk, the call from the mosque — the lane keeps its time by them. I go, if the work lets me.";
        private static final String TEA_LINE = "My lunch is gone. The water seller at the chowk has cool water — a glass for the price of a paper, and he remembers your face.";

        private static final Set<String> PRON = Set.of("he", "him", "his", "she", "hers", "they", "them", "their", "it");
        private static final Set<String> SELFWORDS = Set.of("tired", "new", "here", "going", "back", "late", "early", "fine", "well",
                "ok", "okay", "hungry", "cold", "hot", "sorry", "happy", "sad", "angry", "leaving", "coming", "not", "just",
                "still", "only", "very", "really", "busy", "ready", "afraid", "glad", "proud", "worried", "sick", "weary", "home", "from");
        private static final Pattern FROM_PAT = Pattern.compile("^(?:i am|im|i'm)\\s+from\\s+([a-z][a-z' ]{1,25})$");
        private static final Pattern JOB_PAT = Pattern.compile("^(?:i am|im|i'm)\\s+(?:a |an |the )([a-z]{2,15})$");
        private static final Pattern NAME_PAT = Pattern.compile("^(?:my name is|my name|call me|i am|im|i'm)\\s+([a-z][a-z' ]{1,25})$");
        /** The lane's register, in words: "where is Gani", "who is the smith", "is there a paper seller". */
        private static final Pattern FIND_PAT = Pattern.compile(
                "^(?:where|who) (?:is|are) (?:the )?([a-z][a-z' -]{1,29})$|^is there (?:a|an) (?:the )?([a-z][a-z' -]{1,29})$");
        private static final Pattern BACK_PAT = Pattern.compile("^(really|seriously|oh|okay|ok|hmm|right|i see|are you sure|no way)\\??$");

        /** The engine's decision, and what goes with it. */
        static final class R {
            final String k;
            String card;
            String x;
            String text = "";
            boolean refer;

            R(String k) {
                this.k = k;
            }

            R card(String card, String x) {
                this.card = card;
                this.x = x;
                return this;
            }

            R x(String x) {
                this.x = x;
                return this;
            }

            R text(String text) {
                this.text = text;
                return this;
            }

            R refer(boolean refer) {
                this.refer = refer;
                return this;
            }
        }

        static R free(Ctx c, Session s, ReputationStanding.Standing st, ReputationStanding.Standing.Person p, String raw) {
            String t = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
            if (t.isEmpty()) {
                return new R(HUMAN).text("…?");
            }
            List<String> tokens = tokenize(t);
            int hour = c.server == null ? 12 : (int) (c.server.overworld().getDayTime() / 1000L + 6L) % 24;
            if (has(tokens, "bye", "goodbye") || adj(tokens, "see", "you") || all(tokens, "going", "now")
                    || all(tokens, "good", "night")) {
                return new R(BYE);
            }
            if (EraTalk.hasCounter(c.role) && has(tokens, "what") && has(tokens, "buy", "take") && !has(tokens, "me")) {
                return new R(TOPIC).x(EraTalk.COUNTER);
            }
            if (has(tokens, "buy", "swap", "trade", "purchase", "exchange") || (has(tokens, "sell") && has(tokens, "me"))) {
                return new R(TRADE);
            }
            if (has(tokens, "more", "continue", "next") || (has(tokens, "go") && has(tokens, "on"))
                    || (has(tokens, "and") && has(tokens, "then"))) {
                return new R(MORE).card(s.subject.isEmpty() ? "" : s.subject, s.lastX == null ? "" : s.lastX);
            }
            if (has(tokens, "hi", "hello", "namaste") || (has(tokens, "good")
                    && has(tokens, "evening", "morning", "afternoon"))) {
                return new R(GREET);
            }
            if (has(tokens, "yourself", "introduce") || (has(tokens, "your") && has(tokens, "name"))
                    || (has(tokens, "who") && has(tokens, "you"))) {
                return new R(TOPIC).x("self");
            }
            if (has(tokens, "year") || (has(tokens, "where") && has(tokens, "we", "i"))
                    || (has(tokens, "this") && has(tokens, "place"))) {
                return new R(TOPIC).x("wherehere");
            }
            if (has(tokens, "paper", "newspaper", "herald")) {
                return new R(TOPIC).x("newswhere");
            }
            if (has(tokens, "eyewitness", "see", "saw") || (has(tokens, "happened") && has(tokens, "today"))
                    || (has(tokens, "station") && has(tokens, "afternoon"))) {
                return new R(TOPIC).x("seen");
            }
            if (has(tokens, "news", "heard", "hear", "happen", "happened")) {
                return new R(TOPIC).x("news");
            }
            if (has(tokens, "date") || ((has(tokens, "day") && has(tokens, "what", "which"))
                    && !has(tokens, "your") && !has(tokens, "hot") && !has(tokens, "like"))) {
                return new R(HUMAN).text(dateLabel(c.date) + ".");
            }
            if ((has(tokens, "old") && has(tokens, "you")) || has(tokens, "age") || has(tokens, "born", "birth")) {
                return new R(HUMAN).text("My years are the register's business, not the bench's. The lane knows me by my work, not my count.");
            }
            if (all(tokens, "how", "you") && has(tokens, "are", "do", "doing") && !has(tokens, "know")) {
                return new R(HUMAN).text(howLine(c, hour, p == null ? 0 : p.bond));
            }
            if (all(tokens, "how", "know") && !s.subject.isEmpty()) {
                return new R(KNOW);
            }
            if (has(tokens, "thanks", "thank", "shukriya")) {
                return new R(HUMAN).text(HEM_LINE);
            }
            if (has(tokens, "hot", "heat", "weather", "rain", "raining", "cold", "sun")) {
                return new R(HUMAN).text(weatherLine(c));
            }
            if (has(tokens, "temple", "god", "pray", "mosque")) {
                return new R(HUMAN).text(BELL_LINE);
            }
            if (has(tokens, "tea", "water", "eat", "food", "lunch", "hungry")) {
                return new R(HUMAN).text(TEA_LINE);
            }
            if (has(tokens, "cheap", "cheaper", "reduce", "discount")
                    || (has(tokens, "price") && has(tokens, "high", "much", "lower", "cut", "down"))) {
                return new R(HUMAN).text(HAGGLE_LINE);
            }
            /* the player's city deeds: he reports what he did, the city's values move */
            if ((has(tokens, "stayed") && has(tokens, "home", "indoor", "indoors"))
                    || (has(tokens, "home") && has(tokens, "guns", "shooting", "firing", "curfew"))) {
                rep(c, 10, false);
                return new R(HUMAN).text("A wise man in a watched city. The lane notes that, even when the road forgets.");
            }
            if (has(tokens, "out") && has(tokens, "guns", "shooting", "zone", "front")) {
                rep(c, 20, true);
                return new R(HUMAN).text("You were out when the trouble walked the city. I would not have been. The city will remember that longer than I will.");
            }
            if (has(tokens, "checkpoint") && has(tokens, "stop", "stopped", "search", "searched", "question", "detain")) {
                rep(c, 20, true);
                return new R(HUMAN).text("A face the men keep looking at. I would walk to the lit side of the street, if I were you.");
            }
            if (has(tokens, "checkpoint") && has(tokens, "clean", "smooth", "passed", "through", "quick")) {
                rep(c, 10, false);
                return new R(HUMAN).text("The men let you through clean. That is a thing the city carries about a man.");
            }
            if ((has(tokens, "white") && has(tokens, "shirts"))
                    || (has(tokens, "razakar") && has(tokens, "deal", "talk", "meet", "know", "with"))) {
                rep(c, 10, true);
                return new R(HUMAN).text("Those names are watched. Say them to the new eyes, and they will not forget it.");
            }
            if ((has(tokens, "road", "street") && has(tokens, "safe", "safety"))) {
                String tier = st == null ? "neutral" : ReputationStanding.tier(st.posRep, st.negRep);
                return new R(HUMAN).text(switch (tier) {
                    case "trusted" -> "The road is open for you — my face walks with yours, and the men know my face.";
                    case "marked" -> "Walk fast, and keep to the lit side. I would not carry your face through a checkpoint.";
                    default -> "The road is calm today — keep to it, and it will keep to you.";
                });
            }
            if (has(tokens, "price", "cotton", "cloth", "weave", "loom")) {
                return new R(TOPIC).x("cotton");
            }
            if (has(tokens, "day", "work", "rounds", "routine", "wake", "sleep")) {
                return new R(TOPIC).x("rounds");
            }
            if (has(tokens, "wife", "family", "home", "house", "children")) {
                return new R(TOPIC).x("family");
            }
            if (has(tokens, "ruler") || (has(tokens, "who") && has(tokens, "rules"))) {
                return new R(TOPIC).x("ruler");
            }
            if (has(tokens, "famous")) {
                return new R(TOPIC).x("famous");
            }
            /* the counter, in the player's words: "what is at your counter", "your price", "what do you sell" */
            if (EraTalk.hasCounter(c.role) && (has(tokens, "counter") || (has(tokens, "price") && has(tokens, "your"))
                    || (has(tokens, "what") && has(tokens, "sell") && !has(tokens, "me")))) {
                return new R(TOPIC).x(EraTalk.COUNTER);
            }
            /* the lane's register: "where is Gani", "where is the smith", "is there a paper seller" */
            String ask = t.endsWith("?") || t.endsWith(".") ? t.substring(0, t.length() - 1).trim() : t;
            Matcher find = FIND_PAT.matcher(ask);
            if (find.matches()) {
                String word = find.group(1) == null ? find.group(2) : find.group(1);
                if (word != null && word.endsWith(" here")) {
                    word = word.substring(0, word.length() - 5).trim();
                }
                String line = EraTalk.findLine(c, word);
                if (line != null) {
                    return new R(HUMAN).text(line);
                }
            }
            /* remembered: "my name is …", "i'm from …", "i'm a soldier" */
            Matcher fr = FROM_PAT.matcher(t);
            if (fr.matches()) {
                String pl = fr.group(1).trim();
                if (st != null) {
                    st.playerFrom = pl;
                }
                boolean known = c.card(pl) != null;
                if (!known) {
                    for (KnowledgeCard k : c.allCards()) {
                        if (k.id().equals(pl) || k.aliases().contains(pl)) {
                            known = true;
                            break;
                        }
                    }
                }
                return new R(HUMAN).text(known
                        ? "Then you have walked down from " + capw(pl) + " to see what changed. Sit if you like — the bench does not mind."
                        : "I have not heard of " + capw(pl) + ". Far, then.");
            }
            Matcher jb = JOB_PAT.matcher(t);
            if (jb.matches() && !SELFWORDS.contains(jb.group(1))) {
                if ("soldier".equals(jb.group(1))) {
                    return new R(HUMAN).text("A soldier, then. Take your time — I am only a "
                            + KnowledgeLevels.job(c.role) + ", and the day is mine.");
                }
                return new R(HUMAN).text(capw(jb.group(1)) + "? Then we have work in common, if it is trade.");
            }
            Matcher nm = NAME_PAT.matcher(t);
            if (nm.matches() && nm.group(1).trim().split(" ").length <= 2 && !SELFWORDS.contains(nm.group(1).trim())) {
                if (st != null) {
                    st.playerName = nm.group(1).trim();
                }
                return new R(HUMAN).text(capw(nm.group(1).trim()) + ". I shall remember it — the market remembers a face, and so will I.");
            }
            /* backchannel: one word, and he keeps the thread */
            if (BACK_PAT.matcher(t).matches()) {
                String d = "";
                if (!s.subject.isEmpty()) {
                    String title = "news".equals(s.subject) ? "The news"
                            : "ruler".equals(s.subject) ? "Who rules"
                            : cardTitle(c, s.subject);
                    d = (title == null ? "" : capw(title)) + " — that is so.";
                }
                return new R(HUMAN).text("I would not waste your time with a story." + (d.isEmpty() ? "" : " " + d));
            }
            /* the personal questions (the sheet's own): the hour, home, married, friends,
               know/like me, earn, money, fear, tomorrow, help, mood, hope, the work,
               who trades here. Each in the sheet's words, as far as the jar's record reaches. */
            if (has(tokens, "time", "clock") || (has(tokens, "hour") && has(tokens, "what"))) {
                String tl = hour < 12 ? "The morning — the work is already going, and the lane is waking."
                        : hour < 17 ? "Midday — the work rests and wakes again, and the town is loud with it."
                        : hour < 21 ? "The evening — the work is done, and the town is at its own business."
                        : "Late — the lamp is low, and the work waits for the morning.";
                return new R(HUMAN).text(tl);
            }
            if (has(tokens, "where") && has(tokens, "live")) {
                return new R(HUMAN).text("I live in the lane, in a small house the lane knows. The house knows me, and that is enough of an address.");
            }
            if (has(tokens, "married", "spouse")) {
                return new R(HUMAN).text("Yes — the house keeps its temper, and so do I. That is all I will say of it.");
            }
            if (has(tokens, "friend", "friends") && (has(tokens, "your") || has(tokens, "have") || has(tokens, "who"))) {
                return new R(HUMAN).text("Friendship is the lane's, not mine to name — I do not name friends in the market. You, if we keep meeting, are learning your way onto that lane.");
            }
            if ((has(tokens, "know") && has(tokens, "me") && !has(tokens, "how")) || (has(tokens, "who") && has(tokens, "am"))) {
                String tier = st == null ? "neutral" : ReputationStanding.tier(st.posRep, st.negRep);
                return new R(HUMAN).text(switch (tier) {
                    case "trusted" -> "Know you? You are one of the good faces — the men at the checkpoint wave at you, and that is rarer than rain.";
                    case "warm" -> "Know you? Your face is on the lane's list of kind ones, even if the name is still new to me.";
                    case "marked" -> "Know you? Better not. Walk the lit side, and keep your name out of the men's mouths.";
                    case "wary" -> "Know you? The lane has its eyes on you this week. I would not put my name where the men are reading.";
                    default -> "Know you? We are learning each other, one talk at a time. That is how a name gets onto a lane.";
                });
            }
            if ((has(tokens, "like") && has(tokens, "me")) || (has(tokens, "trust") && has(tokens, "me")) || has(tokens, "friend", "friends")) {
                String tier = st == null ? "neutral" : ReputationStanding.tier(st.posRep, st.negRep);
                return new R(HUMAN).text(switch (tier) {
                    case "trusted" -> "You are one of the good faces in this lane. The lane says so, and so do I.";
                    case "warm" -> "You are a kind face — the lane is learning you, and I am learning you with it.";
                    case "marked" -> "I deal with you for the goods, not for the face. That is where the road has us.";
                    case "wary" -> "The lane is watchful this week, and I am watchful with it. Ask me again when the road is calm.";
                    default -> "We are learning each other, one talk at a time. That is how the lane works.";
                });
            }
            if (has(tokens, "earn", "earned", "wage", "wages", "pay", "paid", "salary")) {
                return new R(HUMAN).text("A day's work is a day's work — paper money and coin for the day, and it is spent before the dark. I would rather it was honest.");
            }
            if (has(tokens, "money", "coin", "rich", "poor", "annas")) {
                return new R(HUMAN).text("Coin is coin. I keep enough for the work and the meal of the day — and I would rather it was honest.");
            }
            if (has(tokens, "afraid", "scared", "fear")) {
                return new R(HUMAN).text("A man listens to what the town talks of, and then he keeps to the lane. That is the whole of my fear, and it is enough.");
            }
            if (has(tokens, "tomorrow")) {
                return new R(HUMAN).text("Tomorrow the work goes first, then the market, then the dark — and the town is still on its own business, and none of it changes for a holiday.");
            }
            if ((has(tokens, "help", "teach", "carry", "lend") && has(tokens, "me"))
                    || (has(tokens, "show") && has(tokens, "me") && !has(tokens, "card", "register", "page"))) {
                return new R(HUMAN).text("Help? The lane helps the way it can — I will tell you the price of the day and point you down the street. Beyond that, ask the man who deals in what you need.");
            }
            if (has(tokens, "happy", "sad", "tired", "mood", "feel")) {
                return new R(HUMAN).text(capw(moodBase(hour)) + ", as the hour asks of me.");
            }
            if (has(tokens, "hope", "wish")) {
                return new R(HUMAN).text("One hope, held quietly: that the price settles and the lane stays calm. I will be glad to be told once it comes true.");
            }
            if (has(tokens, "job", "shop", "stall") || (has(tokens, "what") && has(tokens, "do") && has(tokens, "you"))) {
                return new R(HUMAN).text("I keep my trade — that is my life in this city. The work is the same work in every year of it, and the city knows me by it.");
            }
            if (has(tokens, "who") && has(tokens, "trade", "trades", "sell", "buy")) {
                return new R(TOPIC).x(EraTalk.FIND);
            }
            /* the sentence engine: entity + question + pronoun + "yesterday" + opinion */
            R e = engine(c, t, tokens, s);
            if (e != null) {
                return e;
            }
            if (t.indexOf('?') >= 0) {
                String[] refer = refer(c);
                return new R(HUMAN).text("I wouldn't know. " + refer[0]).refer(true);
            }
            String[] par = parries(c.role);
            return new R(HUMAN).text(par[p == null ? 0 : p.par++ % par.length]);
        }

        /** The sentence engine over the town's record: a card, or an opinion, or "I do not know that word." */
        private static R engine(Ctx c, String t, List<String> tokens, Session s) {
            String wh;
            if (has(tokens, "how") && has(tokens, "many", "much")) {
                wh = "howmany";
            } else if (has(tokens, "what")) {
                wh = "what";
            } else if (has(tokens, "where")) {
                wh = "where";
            } else if (has(tokens, "when")) {
                wh = "when";
            } else if (has(tokens, "who", "whom")) {
                wh = "who";
            } else if (has(tokens, "why")) {
                wh = "why";
            } else if (has(tokens, "how")) {
                wh = "how";
            } else if (has(tokens, "which")) {
                wh = "what";
            } else {
                wh = null;
            }
            String best = null;
            int bestScore = 0;
            for (KnowledgeCard k : c.allCards()) {
                int sc = 0;
                for (String a : k.aliases()) {
                    if (a.indexOf(' ') >= 0) {
                        if (t.indexOf(a) >= 0) {
                            sc += 10;
                        }
                    } else if (tokens.contains(a)) {
                        sc += 8;
                    }
                }
                if (sc > bestScore) {
                    bestScore = sc;
                    best = k.id();
                }
            }
            boolean hasPron = tokens.stream().anyMatch(PRON::contains);
            boolean opinion = has(tokens, "think", "believe", "hope") || (has(tokens, "what") && has(tokens, "make"));
            if (best != null && bestScore >= 4) {
                KnowledgeCard card = c.card(best);
                if (card == null) {
                    return null;
                }
                if (opinion && wh == null) {
                    return new R(HUMAN).text("Ask a " + KnowledgeLevels.job(c.role) + " an opinion of "
                            + (card.title().isEmpty() ? best : card.title().toLowerCase(Locale.ROOT))
                            + " — I hope for quiet, and that the price settles.");
                }
                String qid = qidForWh(card, wh);
                if (t.indexOf("yesterday") >= 0) {
                    KnowledgeCard.Question q = card.question(qid);
                    KnowledgeCard.Answer aY = q == null ? null : q.at(c.date - 1);
                    KnowledgeCard.Answer aT = q == null ? null : q.at(c.date);
                    if (aY != null && (aT == null || !aT.text().equals(aY.text()))) {
                        // What it was yesterday, said as it was yesterday.
                        return new R(HUMAN).text(expand(c, aY.text()));
                    }
                }
                return new R(CARD).card(best, qid);
            }
            if (hasPron && !s.subject.isEmpty()) {
                KnowledgeCard card = c.card(s.subject);
                if (card != null) {
                    return new R(CARD).card(s.subject, qidForWh(card, wh));
                }
            }
            if (bestScore > 0) {
                return new R(HUMAN).text("I do not know that word. Is it court talk?");
            }
            return null;
        }

        /** The question of a card a wh-word points at, by the first word of its label. */
        static String qidForWh(KnowledgeCard card, String wh) {
            List<KnowledgeCard.Question> qs = card.questions();
            if (qs.isEmpty()) {
                return "";
            }
            String lead = qs.get(0).id();
            if (wh == null) {
                return lead;
            }
            if ("howmany".equals(wh)) {
                for (String id : new String[]{"size", "many"}) {
                    if (card.question(id) != null) {
                        return id;
                    }
                }
            } else if ("how".equals(wh)) {
                for (String id : new String[]{"size", "age"}) {
                    if (card.question(id) != null) {
                        return id;
                    }
                }
            }
            String fw = switch (wh) {
                case "where" -> "where";
                case "when" -> "when";
                case "who" -> "who";
                case "why", "which", "what" -> "what";
                default -> null;
            };
            if (fw != null) {
                for (KnowledgeCard.Question q : qs) {
                    String first = q.label().split(" ")[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                    if (first.equals(fw)) {
                        return q.id();
                    }
                }
            }
            return lead;
        }

        /** The mood's "how" line, grounded in the hour of the day, the day, and the bond. */
        static String howLine(Ctx c, int hour, int bond) {
            String line;
            if (hour >= 6 && hour < 11) {
                line = "Up before the light, as always. The day is still quiet, and I mean to keep it that way.";
            } else if (hour < 16) {
                line = "The middle of the day — the work is on me, and I am glad of it.";
            } else if (hour < 20) {
                line = "Quiet, and watchful. I keep one eye on the road and one on the price.";
            } else {
                line = "Tired, and glad of it. The day ran long, and the night is for the count.";
            }
            if (packNews(c) != null) {
                line += " The day is on my mind.";
            }
            if (bond >= 2) {
                line += " You are becoming a familiar face in this lane.";
            }
            return line;
        }

        /** The mood's dot, by the tier the street reads. */
        static int moodDot(String tier) {
            return switch (tier) {
                case "trusted" -> 0xFF5D8A5D;
                case "warm" -> 0xFF6E9A5D;
                case "wary" -> 0xFF8A7A5D;
                case "marked" -> 0xFF8A5D5D;
                default -> 0xFF5D7A8A;
            };
        }

        /** The weather, as it is, not as it was in the story. */
        static String weatherLine(Ctx c) {
            boolean rain = c.server != null && c.server.overworld().isRaining();
            return rain
                    ? "It is raining. The lane runs brown to the drains, and the paper is kept under the counter."
                    : "The heat breaks after four. Today the air is plain — you can hear the lane.";
        }

        /** A deed, recorded on the city's standing for the player. */
        private static void rep(Ctx c, int dv, boolean neg) {
            if (c.player == null || c.server == null) {
                return;
            }
            ReputationStanding store = ReputationStanding.get(c.server.overworld());
            ReputationStanding.Standing ps = store.standing(c.player, c.date);
            if (neg) {
                store.addNeg(ps, dv);
            } else {
                store.addPos(ps, dv);
            }
        }

        private static String[] parries(String role) {
            return new String[]{
                    "That word is above my head. Work, road, market — that is my country.",
                    "I am a " + KnowledgeLevels.job(role) + ", not a scribe. Ask the clerks if it is court talk.",
                    "Say it in one breath — my mind is on the work.",
            };
        }

        static List<String> tokenize(String t) {
            List<String> out = new ArrayList<>();
            for (String w : t.split("[^a-z0-9']+")) {
                w = w.trim();
                if (!w.isEmpty()) {
                    out.add(w);
                }
            }
            return out;
        }

        private static boolean has(List<String> tokens, String... ws) {
            for (String w : ws) {
                if (tokens.contains(w)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean all(List<String> tokens, String... ws) {
            for (String w : ws) {
                if (!tokens.contains(w)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean adj(List<String> tokens, String a, String b) {
            int i = tokens.indexOf(a);
            return i >= 0 && i + 1 < tokens.size() && tokens.get(i + 1).equals(b);
        }
    }
}
