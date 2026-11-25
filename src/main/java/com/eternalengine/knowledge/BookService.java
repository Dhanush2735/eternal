package com.eternalengine.knowledge;

import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.BookViewPacket;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Step 20: the history book and the tracker compass. The book holds only what the player has heard or read
 * ({@link PlayerKnowledge}): every name, the notes about it (the day, who said it, what they said), linked names, the
 * journal, a family tree once the family is known; what comes later only with the time traveller setting. A name with
 * a place can be tracked: the player's compass then points to it (vanilla's lodestone needle, no lodestone needed), and
 * while it is held the action bar says how far and which way.
 */
public final class BookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookService.class);
    /** On a compass: the card it points to. */
    public static final String TRACK_TAG = "EeTrack";
    static final String[][] GROUPS = BookView.GROUPS;
    private static final String[] DIRS = {"north", "north-east", "east", "south-east", "south", "south-west", "west",
            "north-west"};

    private BookService() {
    }

    // ---------------------------------------------------------------------------------------- the player's side

    /** The book opens (the item, or {@code /ee book}): the last name the player learned about, its notes. */
    public static void open(ServerPlayer p) {
        send(p, view(p, "", "notes", ""));
    }

    /**
     * Something done in the book: open a name ("card"), a tab ("tab", with the open name), track a name's place
     * ("track"), or stop ("untrack"). Returns the next view (sent to the player too).
     */
    public static BookView act(ServerPlayer p, String action, String card, String tab) {
        String hint = "";
        String a = action == null ? "" : action;
        String c = card == null ? "" : card;
        String t = tab == null || tab.isBlank() ? "notes" : tab;
        if (a.equals("track")) {
            hint = track(p, c);
        } else if (a.equals("untrack")) {
            hint = untrack(p);
        } else if (a.equals("card")) {
            t = "notes";
        }
        BookView v = view(p, c, t, hint);
        send(p, v);
        return v;
    }

    static void send(ServerPlayer p, BookView v) {
        try {
            EternalEnginePacketHandler.sendToPlayer(new BookViewPacket(v), p);
        } catch (Throwable t) {
            LOGGER.warn("[EE book] Could not send the book to {}: {}", p.getGameProfile().getName(), t.toString());
        }
    }

    /** The book of this player now. */
    public static BookView view(ServerPlayer p, String card, String tab, String hint) {
        MinecraftServer server = p.getServer();
        PlayerKnowledge pk = server == null ? new PlayerKnowledge() : PlayerKnowledge.get(server);
        double[] at = com.eternalengine.money.PurseService.placeOf(p);
        return build(pk, p.getUUID(), at, KnowledgeDate.today(), card, tab, hint, timeTraveller());
    }

    static boolean timeTraveller() {
        try {
            return com.eternalengine.config.EternalEngineConfig.COMMON.historyBookTimeTraveller.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Step 22: the words a new player gets once, when the book is not given on joining. */
    public static final String WELCOME = "What people tell you and what you read goes into your notes. To read them, buy"
            + " a history book from a seller in the market; anybody can tell you where.";

    /**
     * Once per player, on joining: step 22 gives no book (it is bought from a seller) but says how to get one; with the
     * setting {@code historyBookOnJoin} the book is given, as in step 20.
     */
    public static void welcome(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        try {
            if (PlayerKnowledge.get(server).giveBookOnce(p.getUUID())) {
                if (onJoin()) {
                    give(p);
                    p.displayClientMessage(Component.literal("You have a history book. It keeps what people tell you and"
                            + " what you read, with the day and who said it."), false);
                } else {
                    p.displayClientMessage(Component.literal(WELCOME), false);
                    LOGGER.info("[EE book] {} is new: told where to buy a history book (none given)",
                            p.getGameProfile().getName());
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[EE book] Could not welcome {}: {}", p.getGameProfile().getName(), t.toString());
        }
    }

    /** Step 22: the setting that gives the book on joining (off: it is bought). */
    public static boolean onJoin() {
        try {
            return com.eternalengine.config.EternalEngineConfig.COMMON.historyBookOnJoin.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** A history book (the notebook) into the player's inventory (or at their feet). */
    public static void give(ServerPlayer p) {
        give(p, com.eternalengine.news.BookForms.Form.NOTEBOOK);
    }

    /** Step 22: a history book of one form into the player's inventory (or at their feet). */
    public static void give(ServerPlayer p, com.eternalengine.news.BookForms.Form form) {
        ItemStack book = stack(form);
        if (!p.getInventory().add(book)) {
            p.drop(book, false);
        }
    }

    /** Step 22: one history book of this form. */
    public static ItemStack stack(com.eternalengine.news.BookForms.Form form) {
        return new ItemStack(com.eternalengine.news.NewsItems.book(form));
    }

    /** Step 22: the form of the first history book the player carries (hands, inventory, armour slots), or null. */
    @Nullable
    public static com.eternalengine.news.BookForms.Form carried(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof com.eternalengine.news.HistoryBookItem b) {
                return b.form();
            }
        }
        return null;
    }

    /** Step 22: true if the player carries a history book of this form. */
    public static boolean carries(ServerPlayer p, com.eternalengine.news.BookForms.Form form) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof com.eternalengine.news.HistoryBookItem b && b.form() == form) {
                return true;
            }
        }
        return false;
    }

    /**
     * Step 22: a player reads the notes with a history book in hand or inventory (any form); an operator may always
     * (tests). The notes are kept either way.
     */
    public static boolean mayRead(ServerPlayer p) {
        return carried(p) != null || p.hasPermissions(2);
    }

    /** Step 22: what a player without a book is told (with how many names the notes hold). */
    public static String noBook(ServerPlayer p) {
        int names = 0;
        try {
            MinecraftServer server = p.getServer();
            names = server == null ? 0 : PlayerKnowledge.get(server).of(p.getUUID()).size();
        } catch (Throwable t) {
            names = 0;
        }
        return "You have no history book. Your notes are kept (" + names + (names == 1 ? " name" : " names")
                + "); buy a book from a seller to read them. Anybody can tell you where.";
    }

    /** A line in a player's journal (never fails the caller). */
    public static void journal(@Nullable MinecraftServer server, UUID player, int date, String text, String card) {
        if (server == null || player == null) {
            return;
        }
        try {
            PlayerKnowledge.get(server).journal(player, date, text, card);
        } catch (Throwable t) {
            LOGGER.debug("[EE book] journal: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------------------------------------ the view

    /**
     * The book, with no world: the player's names (grouped, with note counts), the open name ({@code card}; blank: the
     * last one learned about), the tab, the journal and the compass. {@code at} is where the player stands (null:
     * unknown), {@code later} the time traveller setting.
     */
    public static BookView build(PlayerKnowledge pk, UUID player, @Nullable double[] at, int date, String card, String tab,
                                 String hint, boolean later) {
        BookView v = new BookView();
        v.subtitle = "your notes, " + TalkService.dateLabel(date);
        v.tab = tab == null || tab.isBlank() ? "notes" : tab;
        v.hint = hint == null ? "" : hint;
        Map<String, PlayerKnowledge.Entry> entries = pk.of(player);
        List<Map.Entry<String, PlayerKnowledge.Entry>> sorted = new ArrayList<>(entries.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, PlayerKnowledge.Entry>>comparingInt(e -> group(e.getValue().kind()))
                .thenComparing(e -> kindName(e.getValue().kind()))
                .thenComparing(e -> title(e.getKey(), e.getValue()).toLowerCase(Locale.ROOT)));
        for (Map.Entry<String, PlayerKnowledge.Entry> e : sorted) {
            if (v.entries.size() >= BookView.MAX_ENTRIES) {
                break;
            }
            v.entries.add(new BookView.Entry(e.getKey(), title(e.getKey(), e.getValue()), kindName(e.getValue().kind()),
                    e.getValue().notes().size()));
        }
        v.heard = entries.size();
        List<PlayerKnowledge.Line> lines = pk.journalOf(player);
        for (PlayerKnowledge.Line l : lines) {
            if (v.journal.size() >= BookView.MAX_JOURNAL) {
                break;
            }
            v.journal.add(new BookView.Day(day(l.date()), l.text(), entries.containsKey(l.card()) ? l.card() : ""));
        }
        PlayerKnowledge.Track track = pk.trackOf(player);
        v.trackingLabel = track == null ? "" : "Tracking: " + track.label();

        String open = card == null ? "" : card;
        if (open.isBlank() || !entries.containsKey(open)) {
            open = "";
            for (PlayerKnowledge.Line l : lines) {
                if (entries.containsKey(l.card())) {
                    open = l.card();
                    break;
                }
            }
            if (open.isEmpty()) {
                for (BookView.Entry e : v.entries) {
                    if (e.notes() > 0) {
                        open = e.id();
                        break;
                    }
                }
            }
            if (open.isEmpty() && !v.entries.isEmpty()) {
                open = v.entries.get(0).id();
            }
        }
        if (open.isEmpty()) {
            return v;
        }
        PlayerKnowledge.Entry e = entries.get(open);
        KnowledgeCard k = card(open);
        v.card = open;
        v.title = title(open, e);
        v.kind = kindName(e.kind());
        List<PlayerKnowledge.Note> notes = e.notes();
        for (int i = notes.size() - 1; i >= 0 && v.notes.size() < BookView.MAX_NOTES; i--) {
            PlayerKnowledge.Note n = notes.get(i);
            v.notes.add(new BookView.Note(day(n.date()), n.by(), n.text()));
        }
        if (notes.isEmpty()) {
            v.nothing = "You have heard this name, but you know nothing more about it yet. " + askHint(k);
        }
        if (k != null) {
            if (k.hasLocation()) {
                v.canTrack = true;
                v.tracking = track != null && track.card().equals(open);
                if (at != null && !Double.isNaN(at[0]) && !Double.isNaN(at[1])) {
                    v.where = where(at[0], at[1], k.lat(), k.lon()) + " of you";
                }
            }
            for (String l : k.links()) {
                PlayerKnowledge.Entry le = entries.get(l);
                if (le != null) {
                    if (v.links.size() < BookView.MAX_LINKS) {
                        v.links.add(new BookView.Link(l, shortTitle(l, le)));
                    }
                } else {
                    KnowledgeCard lk = card(l);
                    if (lk != null && lk.knownOn(date)) {
                        v.unheardLinks++;
                    }
                }
            }
            if (later) {
                for (KnowledgeCard.Later l : k.later()) {
                    if (l.at() > date && v.later.size() < BookView.MAX_LATER) {
                        v.later.add(l.text());
                    }
                }
            }
            KnowledgeCard family = k.familyTree() ? k : null;
            for (String l : k.links()) {
                KnowledgeCard lk = family == null ? card(l) : null;
                if (lk != null && lk.familyTree()) {
                    family = lk;
                }
            }
            if (family != null) {
                v.familyTree = true;
                tree(v, family, entries, date, later);
            }
        }
        return v;
    }

    /** The family tree of a family card: known once the player has a note about the family (or time traveller). */
    static void tree(BookView v, KnowledgeCard family, Map<String, PlayerKnowledge.Entry> entries, int date, boolean later) {
        PlayerKnowledge.Entry fe = entries.get(family.id());
        boolean known = later || (fe != null && !fe.notes().isEmpty());
        if (!known) {
            v.treeNote = "You don't know this family yet. " + askHint(family);
            return;
        }
        v.treeNote = family.title() + ": the people of this family you have heard of. Open a name to read your notes.";
        for (String l : treeIds(family)) {
            if (v.tree.size() >= BookView.MAX_TREE) {
                break;
            }
            PlayerKnowledge.Entry le = entries.get(l);
            KnowledgeCard lk = card(l);
            if (le != null) {
                v.tree.add(new BookView.Node(l, title(l, le), false));
            } else if (lk != null && (later || lk.knownOn(date))) {
                v.tree.add(new BookView.Node(later ? l : "", later ? lk.title() : "a name you have not heard yet", false));
            }
        }
    }

    /**
     * Step 41 (C11): the names in a family tree.
     *
     * <p>A prose family — the Asaf Jahi dynasty — is a hand-written list written for one city and
     * one century, and it stays exactly as it was written. But when a name in it carries a Wikidata
     * number, the kinship register knows that person's blood relatives, and they go in the tree
     * beside it: <b>a family tree drawn from card links alone is a tree of people who were not
     * related</b>, which is what this step set out to fix.
     */
    static List<String> treeIds(KnowledgeCard family) {
        List<String> ids = new ArrayList<>(family.links());
        com.eternalengine.civilization.Kinship kin = com.eternalengine.civilization.Kinship.get();
        for (String l : family.links()) {
            int qid = qidIn(l);
            if (qid < 0) {
                continue;
            }
            for (com.eternalengine.civilization.Kinship.Kinsman k : kin.familyOf(qid, 4)) {
                if (!k.named()) {
                    continue;
                }
                String id = com.eternalengine.knowledge.PlaceCards.PERSON_ID + k.qid();
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** The Wikidata number in a card id of the form {@code pc_1234}, or -1 when it is prose. */
    static int qidIn(String id) {
        if (id == null || !id.startsWith(com.eternalengine.knowledge.PlaceCards.PERSON_ID)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(
                    com.eternalengine.knowledge.PlaceCards.PERSON_ID.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String askHint(@Nullable KnowledgeCard k) {
        if (k == null) {
            return "Ask people about it: somebody may know more.";
        }
        return k.level() >= 2 ? "The palace clerks would know: ask at the palace." : "Anyone in the street can tell you: ask about it.";
    }

    static String title(String id, PlayerKnowledge.Entry e) {
        return e == null || e.title() == null || e.title().isBlank() ? id : e.title();
    }

    static String shortTitle(String id, PlayerKnowledge.Entry e) {
        KnowledgeCard k = card(id);
        return k != null && k.shortName() != null && !k.shortName().isBlank() ? k.shortName() : title(id, e);
    }

    static String kindName(String kind) {
        return kind == null || kind.isBlank() ? "Name" : kind;
    }

    static int group(String kind) {
        for (int i = 0; i < GROUPS.length; i++) {
            if (GROUPS[i][0].equalsIgnoreCase(kindName(kind))) {
                return i;
            }
        }
        return GROUPS.length;
    }

    /** The group heading of a kind ("Person" -> "People"). */
    public static String groupName(String kind) {
        return BookView.groupName(kind);
    }

    /** "14 Sep 1948". */
    static String day(int date) {
        int y = KnowledgeDate.year(date);
        return KnowledgeDate.shortLabel(date) + " " + (y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y));
    }

    /** A card of any knowledge pack by its id, or null. */
    @Nullable
    public static KnowledgeCard card(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            for (KnowledgePacks.Pack p : KnowledgePacks.all()) {
                KnowledgeCard c = p.cards().get(id);
                if (c != null) {
                    return c;
                }
            }
            // Step 27: people and groups from the card file have a place too (a home or a seat), so the book can track them.
            if (PlaceCards.isStoreId(id)) {
                return PlaceCards.resolve(id, KnowledgeDate.today(), Double.NaN, Double.NaN);
            }
        } catch (Throwable ignored) {
            // No packs: no card.
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ distance words

    /** Great-circle distance in km. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0088d;
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }

    /** The compass direction from one point to another, 0 = north, clockwise. */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0d) % 360.0d;
    }

    /** "about 3.5 km north", "about 40 m east", "right here". */
    public static String where(double fromLat, double fromLon, double toLat, double toLon) {
        double km = distanceKm(fromLat, fromLon, toLat, toLon);
        if (km < 0.03d) {
            return "right here";
        }
        String dir = DIRS[(int) Math.round(bearing(fromLat, fromLon, toLat, toLon) / 45.0d) % 8];
        String d;
        if (km < 1.0d) {
            d = Math.round(km * 100.0d) * 10 + " m";
        } else if (km < 10.0d) {
            d = String.format(Locale.ROOT, "%.1f km", km);
        } else {
            d = String.format(Locale.ROOT, "%,d km", Math.round(km));
        }
        return "about " + d + " " + dir;
    }

    // ------------------------------------------------------------------------------------------- the compass

    /** Track a name's place: the compass points there. Returns what to tell the player. */
    static String track(ServerPlayer p, String cardId) {
        MinecraftServer server = p.getServer();
        KnowledgeCard k = card(cardId);
        if (server == null || k == null || !k.hasLocation()) {
            return "That name has no place to point to.";
        }
        PlayerKnowledge pk = PlayerKnowledge.get(server);
        // From the book: a name in it. From a talk: the place the villager just named (it may not be noted yet).
        PlayerKnowledge.Entry e = pk.of(p.getUUID()).get(cardId);
        String label = e != null ? shortTitle(cardId, e)
                : k.shortName() != null && !k.shortName().isBlank() ? k.shortName() : k.title();
        PlayerKnowledge.Track t = new PlayerKnowledge.Track(cardId, label, k.lat(), k.lon());
        ItemStack compass = findCompass(p);
        boolean given = false;
        if (compass == null && pk.giveCompassOnce(p.getUUID())) {
            compass = new ItemStack(Items.COMPASS);
            if (!p.getInventory().add(compass)) {
                p.drop(compass, false);
            }
            compass = findCompass(p);
            given = true;
        }
        if (compass == null) {
            return "You need a compass to follow it. Make one, or buy one, and press Track again.";
        }
        if (!point(compass, t)) {
            return "Your compass cannot find that place on this map.";
        }
        pk.track(p.getUUID(), t);
        p.containerMenu.broadcastChanges();
        BookService.journal(server, p.getUUID(), KnowledgeDate.today(), "Set the compass to " + label + ".", cardId);
        return (given ? "Here is a tracker compass. " : "") + "Your compass now points to " + label + ".";
    }

    static String untrack(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return "";
        }
        PlayerKnowledge.get(server).track(p.getUUID(), null);
        for (ItemStack s : p.getInventory().items) {
            unpoint(s);
        }
        unpoint(p.getOffhandItem());
        p.containerMenu.broadcastChanges();
        return "Your compass points north again.";
    }

    /** The first compass: the hands, then the inventory. */
    @Nullable
    static ItemStack findCompass(ServerPlayer p) {
        if (p.getMainHandItem().is(Items.COMPASS)) {
            return p.getMainHandItem();
        }
        if (p.getOffhandItem().is(Items.COMPASS)) {
            return p.getOffhandItem();
        }
        for (ItemStack s : p.getInventory().items) {
            if (s.is(Items.COMPASS)) {
                return s;
            }
        }
        return null;
    }

    /** Vanilla's lodestone needle, pointing at a place on the map with no lodestone (it is never checked). */
    static boolean point(ItemStack compass, PlayerKnowledge.Track t) {
        double[] xz;
        try {
            xz = TerraCoordinateHelper.fromGeo(t.lat(), t.lon());
        } catch (Throwable e) {
            xz = null;
        }
        if (xz == null) {
            return false;
        }
        BlockPos pos = new BlockPos((int) Math.floor(xz[0]), 64, (int) Math.floor(xz[1]));
        if (compass.getCount() > 1) {
            compass.setCount(1);
        }
        CompoundTag tag = compass.getOrCreateTag();
        tag.put("LodestonePos", NbtUtils.writeBlockPos(pos));
        tag.putString("LodestoneDimension", "minecraft:overworld");
        tag.putBoolean("LodestoneTracked", false);
        tag.putString(TRACK_TAG, t.card());
        compass.setHoverName(Component.literal("Tracker compass: " + t.label()).withStyle(s -> s.withItalic(false)));
        return true;
    }

    static void unpoint(ItemStack s) {
        if (s == null || !s.is(Items.COMPASS) || s.getTag() == null || !s.getTag().contains(TRACK_TAG)) {
            return;
        }
        CompoundTag tag = s.getTag();
        tag.remove("LodestonePos");
        tag.remove("LodestoneDimension");
        tag.remove("LodestoneTracked");
        tag.remove(TRACK_TAG);
        s.resetHoverName();
    }

    /** Once a second: a player holding a tracker compass reads how far and which way on the action bar. */
    public static void tickCompasses(MinecraftServer server) {
        if (server == null || server.getTickCount() % 20 != 0) {
            return;
        }
        PlayerKnowledge pk = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ItemStack held = p.getMainHandItem().is(Items.COMPASS) ? p.getMainHandItem()
                    : p.getOffhandItem().is(Items.COMPASS) ? p.getOffhandItem() : null;
            if (held == null || held.getTag() == null || !held.getTag().contains(TRACK_TAG)) {
                continue;
            }
            if (pk == null) {
                pk = PlayerKnowledge.get(server);
            }
            PlayerKnowledge.Track t = pk.trackOf(p.getUUID());
            if (t == null || !t.card().equals(held.getTag().getString(TRACK_TAG))) {
                continue;
            }
            double[] at = com.eternalengine.money.PurseService.placeOf(p);
            if (at == null) {
                continue;
            }
            String w = where(at[0], at[1], t.lat(), t.lon());
            p.displayClientMessage(Component.literal(w.equals("right here") ? "You are at " + t.label() + "."
                    : t.label() + ": " + w), true);
        }
    }

    // ------------------------------------------------------------------------------------------------- checks

    /**
     * The book's own check, with no world: a player who heard of the Nizam from Saleem and of King Kothi and the Asaf
     * Jahi family gets those three names in their groups, the Nizam's note with its day and teller, a place to track
     * (King Kothi, about 3.5 km north of Charminar), the family tree only once the family is asked about, the journal,
     * and the same book after sending. Null when all is well.
     */
    public static String selfTest() {
        KnowledgeCard nizam = card("nizam");
        KnowledgeCard family = card("asaf_jahi");
        if (nizam == null || family == null || !nizam.hasLocation()) {
            return "the Hyderabad pack lacks the Nizam (with his place) or the Asaf Jahi family";
        }
        PlayerKnowledge pk = new PlayerKnowledge();
        UUID who = new UUID(20L, 1L);
        int date = KnowledgeDate.key(1948, 9, 14);
        pk.heard(who, "nizam", nizam.title(), nizam.kind());
        boolean first = pk.note(who, "nizam", nizam.title(), nizam.kind(), new PlayerKnowledge.Note(date,
                "Saleem, paper seller", "who", "He lives at [[king_kothi|King Kothi]]."));
        boolean again = pk.note(who, "nizam", nizam.title(), nizam.kind(), new PlayerKnowledge.Note(date,
                "Saleem, paper seller", "who", "He lives at [[king_kothi|King Kothi]]."));
        pk.heard(who, "king_kothi", "King Kothi", "Place");
        pk.heard(who, "asaf_jahi", family.title(), family.kind());
        pk.journal(who, date, "Learned about " + nizam.title() + " from Saleem.", "nizam");
        if (!first || again) {
            return "the first note should count as first, the same note again not";
        }
        double[] charminar = {17.3616, 78.4747};
        BookView v = build(pk, who, charminar, date, "", "notes", "", false);
        if (v.entries.size() != 3 || v.heard != 3 || !"nizam".equals(v.card) || v.notes.size() != 1
                || !v.notes.get(0).by().equals("Saleem, paper seller") || !v.notes.get(0).date().equals("14 Sep 1948")) {
            return "the book opened at " + v.card + " with " + v.entries.size() + " names and " + v.notes.size() + " notes";
        }
        if (!v.canTrack || !v.where.startsWith("about 3.") || !v.where.contains("north") || v.tracking) {
            return "the Nizam's place should be about 3.5 km north of Charminar: \"" + v.where + "\"";
        }
        if (v.links.stream().noneMatch(l -> l.id().equals("king_kothi")) || v.unheardLinks <= 0 || !v.later.isEmpty()) {
            return "linked names: " + v.links + ", " + v.unheardLinks + " not heard yet, later " + v.later;
        }
        if (!v.familyTree || !v.tree.isEmpty() || !v.treeNote.startsWith("You don't know this family yet.")) {
            return "the family tree should be unknown until the family is asked about: " + v.treeNote;
        }
        BookView f = build(pk, who, charminar, date, "asaf_jahi", "notes", "", false);
        if (!f.notes.isEmpty() || !f.nothing.startsWith("You have heard this name, but you know nothing more about it yet.")
                || !f.nothing.contains("palace")) {
            return "a name only heard should say so and send you to the palace: \"" + f.nothing + "\"";
        }
        pk.note(who, "asaf_jahi", family.title(), family.kind(), new PlayerKnowledge.Note(date, "Venkat Rao, court clerk",
                "what", "The Nizams of Hyderabad."));
        BookView t = build(pk, who, charminar, date, "asaf_jahi", "tree", "", false);
        if (t.tree.isEmpty() || t.tree.stream().noneMatch(n -> n.id().equals("nizam"))) {
            return "the known family tree should show the Nizam: " + t.tree;
        }
        BookView later = build(pk, who, charminar, date, "nizam", "notes", "", true);
        if (later.later.isEmpty()) {
            return "the time traveller view should show what comes later for the Nizam";
        }
        if (v.journal.size() != 1 || !v.journal.get(0).card().equals("nizam")) {
            return "the journal: " + v.journal;
        }
        String groups = v.entries.get(0).kind() + "," + v.entries.get(1).kind() + "," + v.entries.get(2).kind();
        if (!groups.equals("Person,Family,Place")) {
            return "groups in the wrong order: " + groups;
        }
        io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(raw);
        v.encode(buf);
        BookView back = BookView.decode(buf);
        raw.release();
        if (!back.card.equals(v.card) || back.entries.size() != v.entries.size() || !back.where.equals(v.where)
                || back.notes.size() != 1 || !back.notes.get(0).text().equals(v.notes.get(0).text())) {
            return "the book came back different after sending";
        }
        String w = where(17.3616, 78.4747, 17.3616, 78.4747);
        String far = where(17.3616, 78.4747, 18.94, 72.83);
        if (!w.equals("right here") || !far.startsWith("about 6") || !far.endsWith("west")) {
            return "distance words: \"" + w + "\", \"" + far + "\"";
        }
        return null;
    }
}
