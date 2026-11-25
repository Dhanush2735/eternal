package com.eternalengine.news;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.civilization.EventNews;
import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.knowledge.Calendars;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.knowledge.KnowledgeCard;
import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.knowledge.KnowledgePacks;
import com.eternalengine.knowledge.PlayerKnowledge;
import com.eternalengine.knowledge.TalkView;
import com.eternalengine.money.GoodsTable;
import com.eternalengine.money.MarketService;
import com.eternalengine.money.MoneyFormat;
import com.eternalengine.money.MoneyTable;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.PaperViewPacket;
import com.eternalengine.timeline.BranchChronicle;
import com.eternalengine.timeline.TimelineBranch;
import com.eternalengine.timeline.TimelineManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 19d: what a paper prints on one day in one place, and what happens when it is read.
 *
 * <p>Only checked news: a knowledge pack's own papers for its days (hand-checked), and otherwise the history data's
 * events near the place and who rules it. Inside a pack, events that start this year are left out (the data dates
 * them by year only, so they might not have happened yet on the paper's day), as the villagers do. Step 19f: when
 * history has taken another road (a fork), the paper prints the new history first, and a pack's papers stop where the
 * change happened inside the pack's land.
 */
public final class NewsService {

    /** Events this close are local news. */
    static final double LOCAL_KM = 400.0d;
    /** Events this close are news from afar. */
    static final double FAR_KM = 1500.0d;

    private NewsService() {
    }

    /** History's other road, when the world is on one: the year it turned, its record, and where it turned. */
    public record Fork(int year, List<BranchChronicle.Entry> entries, double lat, double lon, String cause) {
        boolean located() {
            return !Double.isNaN(lat) && !Double.isNaN(lon);
        }
    }

    /** The fork the world is on now, or null on the original timeline. */
    @Nullable
    public static Fork fork() {
        try {
            if (!SimulationEngine.isInitialized()) {
                return null;
            }
            TimelineManager tm = SimulationEngine.getInstance().getTimelineManager();
            TimelineBranch b = tm == null ? null : tm.getActiveBranch();
            if (b == null || b.isMainTimeline()) {
                return null;
            }
            double lat = Double.NaN, lon = Double.NaN;
            String pivot = b.divergenceEventId();
            if (pivot != null && !pivot.isBlank()) {
                for (HistoryGazetteer.Event e : HistoryGazetteer.get().allEvents()) {
                    if (pivot.equals(e.id())) {
                        lat = e.lat();
                        lon = e.lon();
                        break;
                    }
                }
            }
            return new Fork(b.divergenceYear(), tm.activeChronicle().all(), lat, lon, b.divergenceCause());
        } catch (Throwable t) {
            return null;
        }
    }

    /** The paper's name: a pack's own, or a plain one for the town and the time. */
    static String paperName(@Nullable KnowledgePacks.Pack pack, String town, int year, boolean handwritten) {
        if (!handwritten && pack != null && pack.paperName() != null && !pack.paperName().isBlank()) {
            return pack.paperName();
        }
        String t = town == null || town.isBlank() ? "Local" : town;
        if (handwritten) {
            return "The " + t + " newsletter";
        }
        return year < 1850 ? "The " + t + " Gazette" : "The " + t + " News";
    }

    /** "TUESDAY, 14 SEPTEMBER 1948" (no weekday before the Gregorian calendar). */
    static String dateLine(int date) {
        int y = KnowledgeDate.year(date), m = KnowledgeDate.month(date), d = KnowledgeDate.day(date);
        String label = KnowledgeDate.label(date).toUpperCase(Locale.ROOT);
        if (y >= 1583 && m >= 1 && m <= 12 && d >= 1) {
            try {
                return java.time.LocalDate.of(y, m, d).getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL,
                        Locale.ENGLISH).toUpperCase(Locale.ROOT) + ", " + label;
            } catch (Exception ignored) {
                // A day the calendar does not have: no weekday.
            }
        }
        return label;
    }

    /**
     * The date line in <b>the calendar this place actually uses</b> (step 37, A2): a paper printed
     * in Hyderabad in 1948 was dated in the Fasli year 1358, and the Muslims of the same city
     * would have given 10 Dhu al-Qadah 1367, and the Hindus 2005 of Vikram Samvat. The weekday
     * is only offered where the place dates by a Christian calendar, because the seven-day week
     * is unbroken only in those.
     */
    static String dateLine(double lat, double lon, int date) {
        int y = KnowledgeDate.year(date), m = KnowledgeDate.month(date), d = KnowledgeDate.day(date);
        long epochDay;
        try {
            epochDay = java.time.LocalDate.of(y, m, d).toEpochDay();
        } catch (Exception ignored) {
            return dateLine(date);
        }
        Calendars register = Calendars.get();
        String local = register.dateline(lat, lon, epochDay);
        Calendars.System sys = register.primaryAt(lat, lon, y);
        if ((sys == Calendars.System.GREGORIAN || sys == Calendars.System.JULIAN) && y >= 1583
                && m >= 1 && m <= 12 && d >= 1) {
            try {
                return java.time.LocalDate.ofEpochDay(epochDay).getDayOfWeek()
                        .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                        .toUpperCase(Locale.ROOT) + ", " + local;
            } catch (Exception ignored) {
                // A day the calendar does not have: no weekday.
            }
        }
        return local;
    }

    /** The year as this place would have written it: "Fasli 1358", "1367 AH", "1948". */
    public static String yearText(double lat, double lon, int y) {
        long epochDay;
        try {
            epochDay = java.time.LocalDate.of(y, 6, 15).toEpochDay();
        } catch (Exception ignored) {
            return yearText(y);
        }
        return Calendars.in(Calendars.get().primaryAt(lat, lon, y), epochDay).yearText();
    }

    /** The covering pack with papers (or a map), else any covering pack, else null. */
    @Nullable
    static KnowledgePacks.Pack pack(double lat, double lon, int date) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return null;
        }
        KnowledgePacks.Pack any = null;
        for (KnowledgePacks.Pack p : KnowledgePacks.covering(lat, lon, date)) {
            if (!p.papers().isEmpty()) {
                return p;
            }
            any = any == null ? p : any;
        }
        return any;
    }

    /** The town a paper is bought in, nicely written ("Hyderabad"), or "". */
    static String town(@Nullable HistoricalPresence.Result r) {
        if (r == null) {
            return "";
        }
        String n = r.site().map(h -> h.site().displayName()).orElse("");
        if (n.isBlank()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (String w : n.replace('_', ' ').split(" ")) {
            if (!w.isEmpty()) {
                b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return b.toString();
    }

    @Nullable
    static HistoricalPresence.Result presence(double lat, double lon, int year) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return null;
        }
        try {
            var civs = SimulationEngine.isInitialized() ? SimulationEngine.getInstance().getCivilizationManager() : null;
            return com.eternalengine.core.WorldQuery.presence(civs, com.eternalengine.core.WorldKey.at(lat, lon, year));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Today's paper in a place: printed or handwritten. A handwritten newsletter holds fewer stories (each one copied
     * by hand) and no prices.
     */
    public static PaperView paper(double lat, double lon, int date, boolean handwritten) {
        int year = KnowledgeDate.year(date);
        HistoricalPresence.Result r = presence(lat, lon, year);
        KnowledgePacks.Pack pack = pack(lat, lon, date);
        String town = town(r);
        PaperView v = new PaperView();
        v.handwritten = handwritten;
        v.date = date;
        v.place = town;
        v.lat = lat;
        v.lon = lon;
        v.name = paperName(pack, town, year, handwritten);
        MoneyTable.Here here = MoneyTable.get().at(lat, lon, year);
        long price = com.eternalengine.money.ShopService.paperPrice(here, handwritten);
        String priceWords = MoneyFormat.amount(here.money(), price, year).toUpperCase(Locale.ROOT);
        String townWord = town.isBlank() ? "" : town.toUpperCase(Locale.ROOT) + " · ";
        v.line = townWord + dateLine(lat, lon, date) + (handwritten ? " · WRITTEN BY HAND" : " · " + priceWords);
        int max = handwritten ? 3 : 8;

        // Step 19f: the new history first, when the world is on another road and has been since before this paper.
        Fork fork = fork();
        boolean forked = fork != null && year >= fork.year();
        if (forked) {
            v.fork = "History took another road in " + yearText(fork.year()) + ". This paper prints the news of the "
                    + "new history.";
            List<BranchChronicle.Entry> recent = new ArrayList<>();
            for (BranchChronicle.Entry e : fork.entries()) {
                // Not the record's bookkeeping lines ("1 other site(s) of the same event ... wpr_9adfdf3997").
                if (e.year() <= year && !e.body().contains("site(s)")) {
                    recent.add(e);
                }
            }
            for (int i = recent.size() - 1; i >= 0 && v.stories.size() < Math.min(3, max); i--) {
                BranchChronicle.Entry e = recent.get(i);
                v.stories.add(new PaperView.Story(clean(e.title()), clean(named(e.body())), List.of()));
            }
        }
        boolean packDropped = pack != null && forked && fork.located()
                && KnowledgePacks.Geo.km(pack.lat(), pack.lon(), fork.lat(), fork.lon()) <= pack.radiusKm();

        // A pack's own paper of this day; on a day without one, the pack's checked news line of the day.
        if (pack != null && !packDropped) {
            boolean ownPaper = false;
            for (KnowledgePacks.PaperDay d : pack.papers()) {
                ownPaper |= d.date() == date;
            }
            KnowledgePacks.News line = ownPaper ? null : pack.newsFor(date, 2);
            if (line != null && !line.text().isBlank()) {
                List<String> places = new ArrayList<>();
                for (String[] l : TalkView.links(line.text())) {
                    String p = placed(l[1], pack, lat, lon);
                    if (placeAt(p) != null) {
                        places.add(p);
                    }
                }
                v.stories.add(new PaperView.Story("News of the day", line.text(), List.copyOf(places)));
            }
            for (KnowledgePacks.PaperDay d : pack.papers()) {
                if (d.date() != date) {
                    continue;
                }
                for (KnowledgePacks.Story s : d.stories()) {
                    if (v.stories.size() >= max) {
                        break;
                    }
                    List<String> places = new ArrayList<>();
                    for (String p : s.places()) {
                        places.add(placed(p, pack, lat, lon));
                    }
                    v.stories.add(new PaperView.Story(s.headline(), s.text(), List.copyOf(places)));
                }
            }
        }

        // The history data: events near here, then from afar. Only what has surely happened by the paper's day: events
        // still going on, and last year's. The data dates events by year only, so this year's might not have happened
        // yet (the same rule the villagers keep inside a knowledge pack).
        if (r != null) {
            List<HistoryGazetteer.EventHit> near = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            try {
                for (HistoryGazetteer.EventHit h : HistoryGazetteer.get().eventsNear(lat, lon, year, FAR_KM)) {
                    if (h.event().start() < year && seen.add(h.event().id())) {
                        near.add(h);
                    }
                }
                for (HistoryGazetteer.EventHit h : HistoryGazetteer.get().eventsNear(lat, lon, year - 1, FAR_KM)) {
                    if (h.event().start() == year - 1 && seen.add(h.event().id())) {
                        near.add(h);
                    }
                }
            } catch (Throwable ignored) {
                // No events loaded: the paper has the rest.
            }
            near.sort((a, b) -> Double.compare(a.distanceKm(), b.distanceKm()));
            int far = 0;
            for (HistoryGazetteer.EventHit h : near) {
                HistoryGazetteer.Event e = h.event();
                if (v.stories.size() >= max - 1 || e.name() == null || e.name().isBlank() || yearClash(e)) {
                    continue;
                }
                boolean local = h.distanceKm() <= LOCAL_KM;
                if (!local && (handwritten || far >= 2)) {
                    continue;
                }
                far += local ? 0 : 1;
                v.stories.add(eventStory(e, h.distanceKm(), lat, lon, year, local));
            }
            // A pack's checked answer; on another road of history, the answer from before the change.
            String ruler = pack == null ? null : packRuler(pack, packDropped ? KnowledgeDate.ALWAYS + 1 : date);
            if (ruler == null) {
                ruler = EventNews.rulerLine(r);
            }
            if (!ruler.isBlank() && v.stories.size() < max) {
                v.stories.add(new PaperView.Story("Who rules this land", ruler, List.of()));
            }
        }
        if (v.stories.isEmpty()) {
            v.stories.add(new PaperView.Story("A quiet day", "There is no big news today.", List.of()));
        }

        // What merchants pay today (printed papers only: early papers printed current prices).
        if (!handwritten && !here.money().barter()) {
            try {
                MarketService.Terms t = MarketService.Terms.current();
                for (GoodsTable.Good g : GoodsTable.get().boughtAt(lat, lon, year)) {
                    if (v.prices.size() >= 5 || g.ids().isEmpty()) {
                        continue;
                    }
                    double each = MarketService.pays(g, here, "market", t);
                    String entry = MarketService.priceEntry(g, each, here.money(), year);
                    v.prices.add(Character.toUpperCase(entry.charAt(0)) + entry.substring(1));
                }
                if (!v.prices.isEmpty()) {
                    v.pricesTitle = "What merchants here pay today";
                }
            } catch (Throwable ignored) {
                // No market: no prices.
            }
            String map = MapService.title(lat, lon, date);
            v.ad = map.toUpperCase(Locale.ROOT).replace("MAP OF ", "MAPS OF ") + " · for travellers · at the paper stand";
        }
        return v;
    }

    /** A pack's checked answer to "Who rules here?" on this day ("Hyderabad State: The Nizam, ..."), or null. */
    @Nullable
    static String packRuler(KnowledgePacks.Pack pack, int date) {
        String ref = pack.topics().get("ruler");
        if (ref == null || !ref.contains(".")) {
            return null;
        }
        String[] parts = ref.split("\\.", 2);
        KnowledgeCard c = pack.cards().get(parts[0]);
        KnowledgeCard.Question q = c == null ? null : c.question(parts[1]);
        if (q == null) {
            return null;
        }
        KnowledgeCard.Answer best = null;
        for (KnowledgeCard.Answer a : q.answers()) {
            boolean from = a.from() == KnowledgeDate.ALWAYS || a.from() <= date;
            boolean to = a.to() == KnowledgeDate.ALWAYS || a.to() == 0 || a.to() >= date;
            if (from && to && (best == null || a.from() >= best.from())) {
                best = a;
            }
        }
        return best == null ? null : c.title() + ": " + best.text();
    }

    /**
     * True when an event's name starts with a year that is not its own ("1898: Mandingo Wars ..." dated 1850): a slip
     * in the data, which a paper should not print as news.
     */
    static boolean yearClash(HistoryGazetteer.Event e) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{3,4})(:|\\s)").matcher(e.name().trim());
        if (!m.find()) {
            return false;
        }
        int y = Integer.parseInt(m.group(1));
        return y != e.start() && y != e.start() + 1;
    }

    /** An event's name made fit for a paper: no wiki leftovers, and a headline of at most about 60 letters. */
    static String[] headline(String raw) {
        String n = raw.replaceAll("thumb\\|[^:]*:", " ").replaceAll("\\[\\[|\\]\\]", "").replaceAll("\\s+", " ").trim();
        n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        int stop = n.indexOf(". ");
        String first = stop > 0 ? n.substring(0, stop) : n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
        if (first.length() <= 64) {
            return new String[]{first, n};
        }
        int cut = first.lastIndexOf(' ', 58);
        return new String[]{first.substring(0, cut > 20 ? cut : 58).replaceAll("[,;:]$", "") + "…", n};
    }

    private static PaperView.Story eventStory(HistoryGazetteer.Event e, double km, double lat, double lon, int year,
                                              boolean local) {
        String[] h = headline(e.name().trim());
        String headline = h[0];
        String full = h[1].endsWith(".") ? h[1] : h[1] + ".";
        String where = HistoryGazetteer.isRegional(e) ? "All over the land."
                : km < 1.0d ? "Here, in this town." : "About " + KnowledgePacks.Geo.phrase(lat, lon, e.lat(), e.lon())
                .replaceFirst("^about ", "") + ".";
        String fx = e.effect() == null ? "" : e.effect().toLowerCase(Locale.ROOT);
        String what = fx.contains("military") ? " Soldiers are on the roads."
                : fx.contains("disruption") ? " Markets and travel are disrupted."
                : fx.contains("lockdown") ? " People are told to stay off the streets."
                : fx.contains("memorial") ? " People remember it."
                : fx.contains("political") ? " Everyone is talking about it." : "";
        String when = e.start() == year - 1 && e.end() <= year + 1 ? " That was last year."
                : " It started in " + yearText(e.start()) + (e.end() > year + 1 ? ", and it is still going on." : ".");
        String text = (h[0].equals(h[1]) || h[0].equals(full.substring(0, full.length() - 1)) ? "" : full + " ")
                + where + what + when;
        List<String> places = HistoryGazetteer.isRegional(e) ? List.of()
                : List.of(shortPlace(headline) + "@" + fmt(e.lat()) + "," + fmt(e.lon()));
        return new PaperView.Story(local ? headline : "From afar: " + headline, text, places);
    }

    /** A mark's name on the map: the headline cut short (map labels are small). */
    static String shortPlace(String headline) {
        String s = headline.replace("…", "");
        if (s.length() <= 40) {
            return s;
        }
        int cut = s.lastIndexOf(' ', 38);
        return s.substring(0, cut > 10 ? cut : 38) + "…";
    }

    /** "Naldurg" with its position ("Naldurg@17.8167,76.2833"): from the pack's map, its cards, or the gazetteer. */
    static String placed(String name, @Nullable KnowledgePacks.Pack pack, double lat, double lon) {
        if (name == null || name.isBlank() || name.contains("@")) {
            return name == null ? "" : name;
        }
        if (pack != null && pack.map() != null) {
            for (KnowledgePacks.MapTown t : pack.map().towns()) {
                if (t.name().equalsIgnoreCase(name)) {
                    return name + "@" + fmt(t.lat()) + "," + fmt(t.lon());
                }
            }
        }
        if (pack != null) {
            for (KnowledgeCard c : pack.cards().values()) {
                if (!Double.isNaN(c.lat()) && c.title() != null && c.title().toLowerCase(Locale.ROOT)
                        .startsWith(name.toLowerCase(Locale.ROOT))) {
                    return name + "@" + fmt(c.lat()) + "," + fmt(c.lon());
                }
            }
        }
        try {
            HistoryGazetteer.Site best = null;
            double bestKm = 600.0d;
            for (HistoryGazetteer.Site s : HistoryGazetteer.get().allSites()) {
                if (s.displayName().equalsIgnoreCase(name)) {
                    double km = KnowledgePacks.Geo.km(lat, lon, s.lat(), s.lon());
                    if (km < bestKm) {
                        best = s;
                        bestKm = km;
                    }
                }
            }
            if (best != null) {
                return name + "@" + fmt(best.lat()) + "," + fmt(best.lon());
            }
        } catch (Throwable ignored) {
            // No gazetteer: the place has no mark.
        }
        return name;
    }

    /** The name part of a place ("Naldurg@17.8,76.3" is "Naldurg"). */
    public static String placeName(String place) {
        int at = place == null ? -1 : place.indexOf('@');
        return at < 0 ? (place == null ? "" : place) : place.substring(0, at);
    }

    /** The position of a place, or null. */
    @Nullable
    public static double[] placeAt(String place) {
        int at = place == null ? -1 : place.indexOf('@');
        if (at < 0) {
            return null;
        }
        try {
            String[] p = place.substring(at + 1).split(",");
            return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])};
        } catch (Exception e) {
            return null;
        }
    }

    static String fmt(double d) {
        return String.format(Locale.ROOT, "%.4f", d);
    }

    public static String yearText(int y) {
        return y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y);
    }

    /** Pin ids in a chronicle line made into the event's name ("operation_polo_1948" is "Operation Polo (Hyderabad)"). */
    static String named(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([a-z0-9_]+)\"").matcher(body);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = null;
            try {
                for (HistoryGazetteer.Event e : HistoryGazetteer.get().allEvents()) {
                    if (m.group(1).equals(e.id())) {
                        name = e.name();
                        break;
                    }
                }
            } catch (Throwable ignored) {
                // No gazetteer: the id stays.
            }
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(name == null ? m.group(0) : name));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Chronicle words for a paper: no "branch". */
    static String clean(String s) {
        return s == null ? "" : s.replace(" does not occur in this branch", " did not happen")
                .replace(" in this branch", " now").replace("this branch", "this new history").replace("branch", "history");
    }

    /** A paper item holding this paper. */
    public static ItemStack item(PaperView v) {
        ItemStack stack = new ItemStack(v.handwritten ? NewsItems.NEWSLETTER.get() : NewsItems.NEWSPAPER.get());
        stack.getOrCreateTag().put(NewspaperItem.TAG, v.toTag());
        return stack;
    }

    /** The paper on an item, or null for a blank one. */
    @Nullable
    public static PaperView of(ItemStack stack) {
        CompoundTag t = stack.getTagElement(NewspaperItem.TAG);
        return t == null ? null : PaperView.fromTag(t);
    }

    /**
     * The player reads a paper: its names go into what the player knows (the history book of step 20), its places
     * onto every map the player carries, and the paper opens on the screen.
     */
    public static void read(ServerPlayer player, ItemStack stack) {
        PaperView v = of(stack);
        if (v == null) {
            player.displayClientMessage(Component.literal("The paper is blank."), true);
            return;
        }
        int names = 0;
        try {
            PlayerKnowledge pk = PlayerKnowledge.get(player.getServer());
            KnowledgePacks.Pack pack = pack(v.lat, v.lon, v.date);
            for (PaperView.Story s : v.stories) {
                for (String[] l : TalkView.links(s.text())) {
                    KnowledgeCard c = pack == null ? null : pack.cards().get(l[0]);
                    String title = c == null ? l[1] : c.title();
                    String kind = c == null ? "Name" : c.kind();
                    pk.heard(player.getUUID(), l[0], title, kind);
                    pk.note(player.getUUID(), l[0], title, kind, new PlayerKnowledge.Note(v.date, v.name, s.headline(),
                            TalkView.plain(s.text())));
                    names++;
                }
            }
            if (names > 0) {
                // Step 20: the journal.
                pk.journal(player.getUUID(), v.date, "Read " + v.name + ": its names are in your book.", "");
            }
        } catch (Throwable t) {
            EternalEngineMod.getLogger().warn("[EE news] Could not note the paper's names: {}", t.toString());
        }
        int maps = MapService.markAll(player, v);
        boolean anyPlace = false;
        for (PaperView.Story s : v.stories) {
            for (String p : s.places()) {
                anyPlace |= placeAt(p) != null;
            }
        }
        StringBuilder note = new StringBuilder(names > 0 ? "Names in this paper are now in your history book"
                : "You have read the paper");
        if (anyPlace) {
            note.append(maps > 0 ? ", and its places are marked on your map." : ". With a map, its places would be "
                    + "marked on it.");
        } else {
            note.append(".");
        }
        v.note = note.toString();
        EternalEnginePacketHandler.sendToPlayer(new PaperViewPacket(v), player);
    }

    /**
     * What a town crier calls out today: each story as "Headline! Text." (names keep their links for the talk box;
     * never a price). Cached for the day and place, since the crier repeats himself.
     */
    public static List<String> crierLines(double lat, double lon, int date) {
        String key = date + ":" + Math.round(lat * 20.0d) + ":" + Math.round(lon * 20.0d) + ":" + branchKey();
        List<String> cached = CRIER.get(key);
        if (cached != null) {
            return cached;
        }
        PaperView v = paper(lat, lon, date, false);
        List<String> out = new ArrayList<>();
        for (PaperView.Story s : v.stories) {
            // A crier calls out what happens near here, not who rules (everybody knows) or news from afar.
            if (s.headline().equals("A quiet day") || s.headline().equals("Who rules this land")
                    || s.headline().startsWith("From afar") || out.size() >= 4) {
                continue;
            }
            out.add(s.headline() + "! " + s.text());
        }
        if (CRIER.size() > 64) {
            CRIER.clear();
        }
        CRIER.put(key, List.copyOf(out));
        return out;
    }

    private static final java.util.Map<String, List<String>> CRIER = new java.util.concurrent.ConcurrentHashMap<>();

    /** One of today's stories for the crier's speech bubble, in plain words ("" when there is no news). */
    public static String crierCall(double lat, double lon, int date, long salt) {
        List<String> lines = crierLines(lat, lon, date);
        if (lines.isEmpty()) {
            return "";
        }
        String line = "Hear ye! " + TalkView.plain(lines.get((int) Math.floorMod(salt, (long) lines.size())));
        return line.length() <= 220 ? line : line.substring(0, 219) + "…";
    }

    /** The timeline the world is on, with the size of its record (a new entry changes the news). */
    static String branchKey() {
        try {
            if (!SimulationEngine.isInitialized()) {
                return "main";
            }
            TimelineManager tm = SimulationEngine.getInstance().getTimelineManager();
            return tm == null ? "main" : tm.activeBranchId() + "/" + tm.activeChronicle().size();
        } catch (Throwable t) {
            return "main";
        }
    }

    /** Forget the crier's lines (a new day of history, a reload or a fork). */
    public static void forgetCalls() {
        CRIER.clear();
    }

    /** Places named by a paper, with positions, as map marks. */
    static List<MapView.Mark> marks(PaperView v) {
        List<MapView.Mark> out = new ArrayList<>();
        for (PaperView.Story s : v.stories) {
            for (String p : s.places()) {
                double[] at = placeAt(p);
                if (at != null) {
                    out.add(new MapView.Mark(placeName(p), at[0], at[1], v.date, s.headline()));
                }
            }
        }
        return out;
    }

    /** True if a list of item tags holds this paper already (same name and date). */
    static boolean samePaper(ListTag marks, PaperView v) {
        for (Tag x : marks) {
            CompoundTag m = (CompoundTag) x;
            if (m.getInt("date") == v.date && m.getString("paper").equals(v.name)) {
                return true;
            }
        }
        return false;
    }
}
