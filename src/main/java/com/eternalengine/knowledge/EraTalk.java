package com.eternalengine.knowledge;

import com.eternalengine.civilization.ReputationStanding;
import com.eternalengine.entity.HumanNpcEntity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The era brain: what is true of the year and the place, and what the counter keeps, read from
 * the era pack (era_world.json) — the approved prototype, point to point. Forty-two bands from
 * 3000 BC to 3000 AD; twenty-three roles hold a counter in each band, with the goods and prices
 * of that place-year (recorded, tag "r", or estimated, tag "e") — and, where the trade does not
 * exist at that place-year, the honest line instead of goods.
 *
 * <p>The talk asks it: who rules, what place and what year, what stands, who is famous, what the
 * counter keeps, where a person or a trade is to be found. When the pack is missing the era
 * stays quiet and every answer falls back to what the talk already knows — nothing breaks.
 *
 * <p>The trade screen of a counter (the sheet's red box): the six roles the real economy serves
 * open Minecraft's trade screen with real goods and real coin; the other counter roles open the
 * counter itself, as the lane describes it — the goods, the prices in the words of the place, or
 * the honest line where there is no such trade there. What is a record and what is an estimate
 * is the pack's own marking; the talk never dresses an estimate up as a record.
 */
final class EraTalk {

    /** The pack, read once at class load. */
    static final EraWorld ERA = EraWorld.load();

    /** The counter's question and the lane's register. */
    static final String COUNTER = "counter";
    static final String FIND = "find";

    /** The trade's lines by standing (the sheet's trade screen, point to point). */
    static final String TRUSTED_TRADE = "The goods are yours, and your face is a good one to sell to. Have them ready.";
    static final String RELUCT_TRADE = "I am dealing with you for the goods, not for your face. Have the coin ready.";

    /** The mock's items line by role and era class (a = ancient, m = medieval, e = early modern, n = modern). */
    private static final Map<String, String[]> ITEMS = Map.ofEntries(

        Map.entry("artisan", new String[] { "tools · pattern · finished work · clay", "tools · pattern · finished work · gear", "tools · pattern · finished work · loom", "tools · pattern · finished work · machine" }),
        Map.entry("bandit", new String[] { "knife · rope · the mask · coin", "knife · rope · the mask · coin", "pistol · rope · the mask · coin", "pistol · rope · the mask · coin" }),
        Map.entry("caravan_guard", new String[] { "blade · rope · whistle · water skins", "blade · rope · whistle · water skins", "rifle · rope · whistle · water skins", "service weapon · rope · whistle · water skins" }),
        Map.entry("caravan_master", new String[] { "tent · rope · ledger · water skins", "tent · rope · ledger · water skins", "tent · rope · ledger · water skins", "van · rope · ledger · water skins" }),
        Map.entry("citizen", new String[] { "", "", "", "" }),
        Map.entry("clerk", new String[] { "clay tablets · seal · reed pen · the day's orders", "ledgers · seal · ink · the day's orders", "ledgers · seal · ink · the day's orders", "ledgers · seal · ink · screen" }),
        Map.entry("commander", new String[] { "sword · map · signal flags · seal", "sword · map · drums · seal", "sword · map · telegraph · seal", "service weapon · map · radio · seal" }),
        Map.entry("court_chancellor", new String[] { "seal · the decree · reed pen · the letters", "seal · the decree · ink · the letters", "seal · the decree · ink · the letters", "seal · the decree · ink · the screen" }),
        Map.entry("doctor", new String[] { "herbs · salve · bandage · the knife", "medicine · mortar · bandage · tincture", "medicine · mortar · bandage · tincture", "medicine · instruments · bandage · the chart" }),
        Map.entry("farmer", new String[] { "grain · seed · sickle · ox-cart", "grain · seed · plough · bullock-cart", "grain · seed · plough · mill share", "grain · seed · tractor · cart" }),
        Map.entry("forager", new String[] { "herbs · dried fruit · basket · flint knife", "herbs · dried fruit · basket · knife", "herbs · dried fruit · basket · knife", "herbs · dried fruit · basket · knife" }),
        Map.entry("guard", new String[] { "blade · truncheon · lantern · rope", "blade · whistle · truncheon · lantern", "blade · whistle · truncheon · lantern", "baton · whistle · radio · lantern" }),
        Map.entry("healer", new String[] { "herbs · salve · bandage · tincture", "herbs · salve · bandage · tincture", "herbs · salve · bandage · tincture", "herbs · salve · bandage · tincture" }),
        Map.entry("imperial_mason", new String[] { "trowel · chisel · stone · rope", "trowel · chisel · stone · rope", "trowel · chisel · stone · rope", "trowel · chisel · stone · crane ticket" }),
        Map.entry("mason", new String[] { "trowel · chisel · stone · rope", "trowel · chisel · stone · rope", "trowel · chisel · stone · rope", "trowel · chisel · stone · crane ticket" }),
        Map.entry("merchant", new String[] { "stock · account sticks · scale · coin", "stock · account book · scales · coin", "stock · account book · scales · coin", "stock · ledger · scales · phone" }),
        Map.entry("money_changer", new String[] { "coin · small change · scale · rope of bags", "coin · small change · scale · ledger", "coin · small change · scale · ledger", "coin · small change · scale · machine" }),
        Map.entry("news_writer", new String[] { "reed pen · clay · news · ink", "quill · paper · ink · news", "quill · paper · ink · news", "keys · wire · news · ink" }),
        Map.entry("paper_seller", new String[] { "clay tablets · news · reed pen · twine", "paper · news · ink · twine", "paper · news · ink · twine", "paper · news · ink · phone" }),
        Map.entry("pedlar", new String[] { "wares · basket · road pass · coin", "wares · basket · road pass · coin", "wares · basket · road pass · coin", "wares · basket · road pass · phone" }),
        Map.entry("priest", new String[] { "prayer mat · the bell · beads · the scripture", "prayer mat · the bell · beads · the scripture", "prayer mat · the bell · beads · the scripture", "prayer mat · the bell · beads · the scripture" }),
        Map.entry("quarantine_inspector", new String[] { "ledger · bell · rope · the notice", "ledger · bell · chalk · the notice", "ledger · bell · chalk · the notice", "ledger · bell · chalk · the notice" }),
        Map.entry("rival_infiltrator", new String[] { "cloak · knife · token · the false seal", "cloak · knife · token · the false seal", "cloak · knife · token · the false seal", "cloak · knife · token · the false seal" }),
        Map.entry("royal_guard", new String[] { "blade · truncheon · lantern · the standard", "blade · whistle · truncheon · the standard", "blade · whistle · truncheon · the standard", "baton · whistle · radio · the standard" }),
        Map.entry("ruler", new String[] { "seal · standard · the ring · the decree", "seal · standard · the ring · the decree", "crown · seal · the ring · the decree", "crown · seal · the ring · the decree" }),
        Map.entry("scholar", new String[] { "tablet · reed pen · notes · ink", "books · notes · ink · tablet", "books · notes · ink · telescope", "books · notes · ink · screen" }),
        Map.entry("scribe", new String[] { "reed pen · clay tablets · ink · seal", "quill · paper · ink · seal", "quill · paper · ink · seal", "typewriter · paper · ink · seal" }),
        Map.entry("ship_captain", new String[] { "anchor · chart · bell · compass", "anchor · chart · bell · compass", "anchor · chart · bell · compass", "anchor · chart · radio · compass" }),
        Map.entry("ship_marine", new String[] { "blade · rope · oar · mess tin", "blade · rope · pistol · mess tin", "rifle · rope · mess tin", "service weapon · rope · mess tin" }),
        Map.entry("smith", new String[] { "hammer · anvil · bellows · iron", "hammer · anvil · bellows · iron", "hammer · anvil · bellows · iron", "hammer · anvil · forge · machine" }),
        Map.entry("soldier", new String[] { "weapon · shield · spear · mess tin", "weapon · blade · crossbow · mess tin", "rifle · bayonet · ammunition · mess tin", "rifle · ammunition · radio · mess tin" }),
        Map.entry("sovereign_ruler", new String[] { "seal · standard · the ring · the decree", "seal · standard · the ring · the decree", "crown · seal · the ring · the decree", "crown · seal · the ring · the decree" }),
        Map.entry("teacher", new String[] { "clay slates · reed pen · the figures · ink", "books · slates · ink · the figures of the bazaar", "books · slates · ink · the figures of the bazaar", "books · slates · ink · the blackboard" }),
        Map.entry("town_crier", new String[] { "horn · notice · rope · the voice", "horn · notice · rope · the voice", "horn · notice · rope · the voice", "megaphone · notice · rope · the voice" }),
        Map.entry("trader", new String[] { "stock · scale · road pass · coin", "stock · scale · road pass · coin", "stock · scale · road pass · coin", "stock · ledger · road pass · phone" }),
        Map.entry("weaver", new String[] { "cotton · finished cloth · loom tools · dye pots", "cotton · finished cloth · loom tools · dye pots", "cotton · finished cloth · loom tools · coin", "cotton · finished cloth · loom · electric shuttle" })
    );

    private EraTalk() {
    }

    /** True when the pack is with us. */
    static boolean has() {
        return ERA.has();
    }

    /** The game's role word to the pack's counter word: the smith and the blacksmith keep one forge, the
     * healer and the hakim carry one bag. */
    static String norm(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "blacksmith" -> "smith";
            case "hakim" -> "healer";
            default -> r;
        };
    }

    /** This role holds a counter in the pack. */
    static boolean hasCounter(String role) {
        return has() && ERA.roles().contains(norm(role));
    }

    /** The band of the person's place and year, or null when the pack is missing. */
    @Nullable
    static EraWorld.World at(TalkService.Ctx c) {
        if (!has()) {
            return null;
        }
        return ERA.world(c.lat, c.lon, KnowledgeDate.year(c.date));
    }

    /** The counter of the person's role at their place and year, or null. */
    @Nullable
    static EraWorld.Entry counter(TalkService.Ctx c) {
        if (!has()) {
            return null;
        }
        return ERA.counter(norm(c.role), c.lat, c.lon, KnowledgeDate.year(c.date));
    }

    /** The counter of a named role at the person's place and year, or null. */
    @Nullable
    static EraWorld.Entry counterOf(String role, TalkService.Ctx c) {
        if (!has()) {
            return null;
        }
        return ERA.counter(norm(role), c.lat, c.lon, KnowledgeDate.year(c.date));
    }

    /**
     * The red box's word (the sheet's button verb), for the twenty-three counter roles; empty when
     * the role holds no counter. When the pack is missing, the jar's own words stand as before.
     */
    static String actionWord(String role) {
        if (has()) {
            if (!hasCounter(role)) {
                return "";
            }
            return switch (norm(role)) {
                case "farmer" -> "Farm";
                case "forager" -> "Gather";
                case "artisan" -> "Craft";
                case "merchant", "trader", "pedlar", "money_changer", "weaver" -> "Trade";
                case "paper_seller" -> "News";
                case "news_writer" -> "Write";
                case "town_crier" -> "Cry";
                case "smith" -> "Repair";
                case "mason" -> "Build";
                case "teacher" -> "Teach";
                case "doctor", "healer" -> "Treat";
                case "quarantine_inspector" -> "Check";
                case "soldier", "ship_marine" -> "Weapons";
                case "guard", "caravan_guard" -> "Beat";
                case "caravan_master" -> "Roads";
                case "ship_captain" -> "Chart";
                default -> "Trade";
            };
        }
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "merchant", "weaver", "trader", "pedlar", "money_changer", "farmer" -> "Trade";
            case "soldier" -> "Weapons";
            case "doctor", "healer", "hakim" -> "Treat";
            case "quarantine_inspector" -> "Check";
            case "priest", "muezzin" -> "Pray";
            case "teacher" -> "Teach";
            case "guard", "royal_guard", "caravan_guard", "police" -> "Beat";
            case "commander" -> "Orders";
            case "smith", "blacksmith" -> "Repair";
            case "clerk" -> "Papers";
            case "ship_captain" -> "Chart";
            case "caravan_master" -> "Roads";
            default -> "";
        };
    }

    /**
     * The sheet's red box: "" absent (the role holds no counter), "container" it opens Minecraft's
     * trade screen (the real economy serves the role), "counter" it opens the person's counter as
     * the lane describes it. Without the pack, the jar's own rule stands: the six the real economy
     * served, and no box for the rest.
     */
    static String actionKind(String role) {
        if (has()) {
            if (!hasCounter(role)) {
                return "";
            }
            return containerTrade(role) ? "container" : "counter";
        }
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "merchant", "weaver", "trader", "pedlar", "money_changer", "farmer" -> "container";
            default -> "";
        };
    }

    /** The real economy serves this role: a seller, a changer, or somebody the barter reaches. */
    static boolean containerTrade(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "merchant", "trader", "pedlar", "money_changer", "farmer", "weaver",
                 "paper_seller", "news_writer" -> true;
            default -> false;
        };
    }

    /** The era class of a year (the sheet's four: ancient, medieval, early modern, modern). */
    static char eraClass(int year) {
        if (year < 500) {
            return 'a';
        }
        if (year < 1400) {
            return 'm';
        }
        if (year < 1850) {
            return 'e';
        }
        return 'n';
    }

    /** The roles band's items line, dressed for the era; empty when the lane has no word for it. */
    static String itemsLine(TalkService.Ctx c) {
        if (!has()) {
            return "";
        }
        String[] it = ITEMS.get(norm(c.role));
        if (it == null) {
            return "";
        }
        EraWorld.World w = at(c);
        if (w == null) {
            return "";
        }
        return switch (eraClass(w.year())) {
            case 'a' -> it[0];
            case 'm' -> it[1];
            case 'e' -> it[2];
            default -> it[3];
        };
    }

    // ------------------------------------------------------------------ the world lines

    /** "Who rules here?" — the band's ruler, and where the court stands. */
    @Nullable
    static String rulerLine(TalkService.Ctx c) {
        EraWorld.World w = at(c);
        return w == null ? null : w.ruler() + ". The court: " + w.court() + ". The books: " + w.courtClk() + ".";
    }

    /** "Where am I, and what year is it?" — the place, the date, and what the year brings. */
    @Nullable
    static String hereLine(TalkService.Ctx c) {
        EraWorld.World w = at(c);
        return w == null ? null : w.date() + ". This year brings " + w.event() + ".";
    }

    /** "What is this place?" — the place and the sight it keeps. */
    @Nullable
    static String placeLine(TalkService.Ctx c) {
        EraWorld.World w = at(c);
        return w == null ? null : w.place() + ". " + w.landmark() + " stands here — that is the sight people come for.";
    }

    /** "Anyone famous from here?" — the band's famous, and who they are. */
    @Nullable
    static String famousLine(TalkService.Ctx c) {
        EraWorld.World w = at(c);
        if (w == null) {
            return null;
        }
        if (w.famousWho().isEmpty() || w.famousWho().equals(w.famous())) {
            return w.famous() + ".";
        }
        return w.famous() + " — " + w.famousWho() + ".";
    }

    /** The first-meeting greeting, in the words of the band. */
    @Nullable
    static String greetLine(TalkService.Ctx c) {
        EraWorld.World w = at(c);
        return w == null ? null : w.greet();
    }

    // ------------------------------------------------------------- the counter (the trade screen)

    /**
     * The counter, as the sheet's trade screen shows it: the person's line (the standing decides
     * it), and, dim under the band, what the counter gives and takes — or the honest line where
     * there is no trade at that counter.
     */
    static TalkView counterView(TalkService.Ctx c) {
        EraWorld.Entry e = counter(c);
        EraWorld.World w = at(c);
        if (e == null || w == null) {
            return topics(c, "I have nothing to trade.");
        }
        String said;
        String items;
        if (!e.priced()) {
            said = e.note().isEmpty() ? "There is nothing at this counter for the coin." : e.note();
            items = "No trade at this counter — " + (e.reg().isEmpty() ? "the lane keeps its own way" : e.reg());
        } else {
            String tier = tierOf(c);
            if ("trusted".equals(tier)) {
                said = TRUSTED_TRADE;
            } else if ("marked".equals(tier)) {
                said = RELUCT_TRADE;
            } else {
                String maker = eraClass(w.year()) == 'n' ? "the market makes it, not I" : "the season makes it, not I";
                said = "The price is the price — " + maker + ". My trade carries: " + carry(e) + ".";
            }
            items = "Gives " + names(e.sell(), 2) + " · takes " + names(e.buy(), 2);
        }
        // The sheet's trade screen says what you carry, dressed for the era.
        items = items + " · you carry " + pocket(w.year()) + ".";
        TalkView v = topics(c, said);
        v.items = items;
        return v;
    }

    /** What the lane's pocket holds, by the era (the sheet's trade screen line). */
    static String pocket(int year) {
        return switch (eraClass(year)) {
            case 'a' -> "barley and copper";
            case 'm' -> "the coin of the place";
            case 'e' -> "silver of the road";
            default -> "paper money and coin";
        };
    }

    /** "Where are the people of the lane?" — the register's three names, and where to find them. */
    static TalkView findView(TalkService.Ctx c) {
        EraWorld.World w = at(c);
        if (w == null) {
            return topics(c, "Ask where a person or a trade is, and I will say it if the lane tells me.");
        }
        StringBuilder sb = new StringBuilder();
        for (EraWorld.Where e : w.where()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(cap(e.name())).append(" is at ").append(e.place()).append('.');
        }
        return topics(c, sb.toString());
    }

    /**
     * "Where is the <person or trade>?" — the register first (a name, or one of its three roles),
     * then the counter (any role the lane keeps a counter for), and, if the lane has no word,
     * null so the talk answers the way it always has.
     */
    @Nullable
    static String findLine(TalkService.Ctx c, String word) {
        if (!has()) {
            return null;
        }
        EraWorld.World w = at(c);
        if (w == null) {
            return null;
        }
        String t = word.toLowerCase(Locale.ROOT).trim();
        for (EraWorld.Where e : w.where()) {
            if (!e.name().isEmpty() && e.name().toLowerCase(Locale.ROOT).equals(t)) {
                return cap(e.name()) + " is at " + e.place() + ".";
            }
        }
        // The street says "the money changer"; the pack keeps "money_changer".
        String key = norm(stripThe(t)).replace(' ', '_');
        if (key.isEmpty()) {
            return null;
        }
        // The way to them, in the world's own measure: the nearest living person
        // of that role, and how far and which way from here.
        String way = wayTo(c, key);
        if (way != null) {
            return way;
        }
        for (EraWorld.Where e : w.where()) {
            if (e.role().equals(key)) {
                return cap(e.name()) + ", the " + jobWord(key) + ", is at " + e.place() + ".";
            }
        }
        EraWorld.Entry e = ERA.counter(key, c.lat, c.lon, KnowledgeDate.year(c.date));
        if (e != null) {
            if (e.priced()) {
                return "The " + jobWord(key) + " keeps a counter here. It carries: " + carry(e) + ".";
            }
            if (!e.note().isEmpty()) {
                return "The " + jobWord(key) + " is here, but there is no trade at that counter. " + e.note();
            }
            return "The " + jobWord(key) + " is here, but there is no trade at that counter.";
        }
        return "I wouldn't know where to find the " + jobWord(key) + " around here.";
    }

    /**
     * The way to the nearest living person of a role: who it is, and how far and
     * which way from here ("Rajiv, the trader, is about 40 m north-east of
     * here."). Null when there is no such person near, so the register answers.
     */
    @Nullable
    private static String wayTo(TalkService.Ctx c, String key) {
        if (c.npc == null) {
            return null;
        }
        HumanNpcEntity best = null;
        double bestD = Double.MAX_VALUE;
        AABB box = c.npc.getBoundingBox().inflate(256.0d, 64.0d, 256.0d);
        for (HumanNpcEntity e : c.npc.level().getEntitiesOfClass(HumanNpcEntity.class, box,
                e -> e != c.npc && e.isAlive() && key.equalsIgnoreCase(e.getRole()))) {
            double d = e.distanceToSqr(c.npc);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) {
            return null;
        }
        String nm = TalkService.nameOf(best, c.lat, c.lon, c.date);
        String who = (nm == null || nm.isBlank() || "Citizen".equals(nm))
                ? "The " + jobWord(key)
                : cap(nm) + ", the " + jobWord(key);
        return who + " is " + MarketTalk.phrase(c.npc, best) + ".";
    }

    // ------------------------------------------------------------------ the small words

    /** The street's word for the job (the jar's own, not the sheet's). */
    static String jobWord(String role) {
        String j = KnowledgeLevels.job(role);
        return j == null || j.isEmpty() ? role : j;
    }

    /** "the smith" to "smith". */
    static String stripThe(String word) {
        String t = word.trim();
        if (t.startsWith("the ")) {
            t = t.substring(4).trim();
        } else if (t.equals("the")) {
            t = "";
        }
        return t;
    }

    /** "wheat, at four annas; cotton, at two annas; a goat, at eight annas". */
    static String carry(EraWorld.Entry e) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(3, e.sell().size());
        for (int i = 0; i < n; i++) {
            EraWorld.Good g = e.sell().get(i);
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(g.item());
            if (!g.price().isEmpty()) {
                sb.append(", at ").append(g.price());
            }
        }
        return sb.toString();
    }

    /** "wheat, cotton" — the first two of the list, joined the way the street joins them. */
    static String names(List<EraWorld.Good> list, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i).item());
        }
        if (sb.length() == 0) {
            return "nothing";
        }
        return sb.toString();
    }

    /** The street's read of the player at this person (trusted, warm, wary, marked, neutral). */
    static String tierOf(TalkService.Ctx c) {
        if (c.player == null || c.server == null) {
            return "neutral";
        }
        ReputationStanding store = ReputationStanding.get(c.server.overworld());
        ReputationStanding.Standing ps = store.standing(c.player, c.date);
        return ReputationStanding.tier(ps.posRep, ps.negRep);
    }

    static String cap(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    // package to the talk
    static TalkView topics(TalkService.Ctx c, String text) {
        return TalkService.topics(c, text);
    }
}
