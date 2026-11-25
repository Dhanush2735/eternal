package com.eternalengine.knowledge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each player has learned, saved with the world: the names they have heard and, per name, notes of
 * what villagers told them (the day, who said it, the question, the answer). The history book (a later
 * step) shows exactly this and nothing more. The player has to go and ask.
 *
 * <p>Step 20: and per player a journal (what they did, by the day), the place their tracker compass points to, and
 * whether they were given their history book and their compass.
 */
public final class PlayerKnowledge extends SavedData {

    public static final String DATA_NAME = "eternalengine_knowledge";
    private static final int MAX_CARDS = 3000;
    private static final int MAX_NOTES_PER_CARD = 16;
    /** Step 20: journal lines kept per player (the newest first). */
    public static final int MAX_JOURNAL = 200;

    public record Note(int date, String by, String question, String text) {
    }

    /** Step 20: one line of the journal: the day, what happened, and the card it is about (or blank). */
    public record Line(int date, String text, String card) {
    }

    /** Step 20: where a player's tracker compass points: a card's place. */
    public record Track(String card, String label, double lat, double lon) {
    }

    /** Step 20: a player's journal, compass target and gifts. */
    static final class Extra {
        final List<Line> journal = new ArrayList<>();
        Track track;
        boolean bookGiven;
        boolean compassGiven;
    }

    public static final class Entry {
        String title;
        String kind;
        boolean asked;
        final List<Note> notes = new ArrayList<>();

        public String title() { return title; }
        public String kind() { return kind; }
        /** True once the player asked about it; false while it is only a name they heard. */
        public boolean asked() { return asked; }
        public List<Note> notes() { return List.copyOf(notes); }
    }

    private final Map<UUID, Map<String, Entry>> players = new ConcurrentHashMap<>();
    private final Map<UUID, Extra> extras = new ConcurrentHashMap<>();

    public static PlayerKnowledge get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlayerKnowledge::load, PlayerKnowledge::new, DATA_NAME);
    }

    /** A name the player heard (a gold name in a villager's answer). */
    public void heard(UUID player, String cardId, String title, String kind) {
        Map<String, Entry> m = players.computeIfAbsent(player, k -> new LinkedHashMap<>());
        synchronized (m) {
            if (!m.containsKey(cardId) && m.size() >= MAX_CARDS) {
                return;
            }
            Entry e = m.computeIfAbsent(cardId, k -> new Entry());
            if (e.title == null) {
                e.title = title;
                e.kind = kind;
                setDirty();
            }
        }
    }

    /**
     * A note: the player asked and was told. The same answer from the same person is kept once. Returns true when
     * this is the first note about the name (step 20: the journal says so).
     */
    public boolean note(UUID player, String cardId, String title, String kind, Note note) {
        Map<String, Entry> m = players.computeIfAbsent(player, k -> new LinkedHashMap<>());
        synchronized (m) {
            if (!m.containsKey(cardId) && m.size() >= MAX_CARDS) {
                return false;
            }
            Entry e = m.computeIfAbsent(cardId, k -> new Entry());
            e.title = title;
            e.kind = kind;
            e.asked = true;
            for (Note n : e.notes) {
                if (n.text().equals(note.text()) && n.by().equals(note.by())) {
                    setDirty();
                    return false;
                }
            }
            boolean first = e.notes.isEmpty();
            e.notes.add(note);
            while (e.notes.size() > MAX_NOTES_PER_CARD) {
                e.notes.remove(0);
            }
            setDirty();
            return first;
        }
    }

    // ------------------------------------------------------------------------------------------- step 20

    private Extra extra(UUID player) {
        return extras.computeIfAbsent(player, k -> new Extra());
    }

    /** Step 20: a line in the player's journal (the newest first). */
    public void journal(UUID player, int date, String text, String card) {
        if (player == null || text == null || text.isBlank()) {
            return;
        }
        Extra x = extra(player);
        synchronized (x) {
            x.journal.add(0, new Line(date, text, card == null ? "" : card));
            while (x.journal.size() > MAX_JOURNAL) {
                x.journal.remove(x.journal.size() - 1);
            }
        }
        setDirty();
    }

    /** Step 20: the player's journal, the newest first. */
    public List<Line> journalOf(UUID player) {
        Extra x = extras.get(player);
        if (x == null) {
            return List.of();
        }
        synchronized (x) {
            return List.copyOf(x.journal);
        }
    }

    /** Step 20: where the player's compass points, or null. */
    public Track trackOf(UUID player) {
        Extra x = extras.get(player);
        return x == null ? null : x.track;
    }

    /** Step 20: point the player's compass at a place (null: at nothing). */
    public void track(UUID player, Track t) {
        extra(player).track = t;
        setDirty();
    }

    /** Step 20: true the first time it is asked for this player (the history book is given once). */
    public boolean giveBookOnce(UUID player) {
        Extra x = extra(player);
        if (x.bookGiven) {
            return false;
        }
        x.bookGiven = true;
        setDirty();
        return true;
    }

    /** Step 20: true the first time it is asked for this player (one tracker compass is given once). */
    public boolean giveCompassOnce(UUID player) {
        Extra x = extra(player);
        if (x.compassGiven) {
            return false;
        }
        x.compassGiven = true;
        setDirty();
        return true;
    }

    public Map<String, Entry> of(UUID player) {
        Map<String, Entry> m = players.get(player);
        if (m == null) {
            return Map.of();
        }
        synchronized (m) {
            return Map.copyOf(m);
        }
    }

    public int playerCount() {
        return players.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<String, Entry>> p : players.entrySet()) {
            CompoundTag pt = new CompoundTag();
            pt.putUUID("player", p.getKey());
            ListTag cards = new ListTag();
            synchronized (p.getValue()) {
                for (Map.Entry<String, Entry> c : p.getValue().entrySet()) {
                    CompoundTag ct = new CompoundTag();
                    ct.putString("id", c.getKey());
                    ct.putString("title", c.getValue().title == null ? c.getKey() : c.getValue().title);
                    ct.putString("kind", c.getValue().kind == null ? "" : c.getValue().kind);
                    ct.putBoolean("asked", c.getValue().asked);
                    ListTag notes = new ListTag();
                    for (Note n : c.getValue().notes) {
                        CompoundTag nt = new CompoundTag();
                        nt.putInt("date", n.date());
                        nt.putString("by", n.by());
                        nt.putString("q", n.question());
                        nt.putString("text", n.text());
                        notes.add(nt);
                    }
                    ct.put("notes", notes);
                    cards.add(ct);
                }
            }
            pt.put("cards", cards);
            list.add(pt);
        }
        tag.put("players", list);
        // Step 20: journals, compass targets and gifts.
        ListTag more = new ListTag();
        for (Map.Entry<UUID, Extra> p : extras.entrySet()) {
            Extra x = p.getValue();
            CompoundTag xt = new CompoundTag();
            xt.putUUID("player", p.getKey());
            xt.putBoolean("book", x.bookGiven);
            xt.putBoolean("compass", x.compassGiven);
            if (x.track != null) {
                CompoundTag tt = new CompoundTag();
                tt.putString("card", x.track.card());
                tt.putString("label", x.track.label());
                tt.putDouble("lat", x.track.lat());
                tt.putDouble("lon", x.track.lon());
                xt.put("track", tt);
            }
            ListTag lines = new ListTag();
            synchronized (x) {
                for (Line l : x.journal) {
                    CompoundTag lt = new CompoundTag();
                    lt.putInt("date", l.date());
                    lt.putString("text", l.text());
                    lt.putString("card", l.card());
                    lines.add(lt);
                }
            }
            xt.put("journal", lines);
            more.add(xt);
        }
        tag.put("extras", more);
        tag.putInt("format", 2);
        return tag;
    }

    public static PlayerKnowledge load(CompoundTag tag) {
        PlayerKnowledge k = new PlayerKnowledge();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag pt = (CompoundTag) t;
            if (!pt.hasUUID("player")) {
                continue;
            }
            Map<String, Entry> m = new LinkedHashMap<>();
            for (Tag ctag : pt.getList("cards", Tag.TAG_COMPOUND)) {
                CompoundTag ct = (CompoundTag) ctag;
                Entry e = new Entry();
                e.title = ct.getString("title");
                e.kind = ct.getString("kind");
                e.asked = ct.getBoolean("asked");
                for (Tag ntag : ct.getList("notes", Tag.TAG_COMPOUND)) {
                    CompoundTag nt = (CompoundTag) ntag;
                    e.notes.add(new Note(nt.getInt("date"), nt.getString("by"), nt.getString("q"), nt.getString("text")));
                }
                m.put(ct.getString("id"), e);
            }
            k.players.put(pt.getUUID("player"), m);
        }
        for (Tag t : tag.getList("extras", Tag.TAG_COMPOUND)) {
            CompoundTag xt = (CompoundTag) t;
            if (!xt.hasUUID("player")) {
                continue;
            }
            Extra x = new Extra();
            x.bookGiven = xt.getBoolean("book");
            x.compassGiven = xt.getBoolean("compass");
            if (xt.contains("track", Tag.TAG_COMPOUND)) {
                CompoundTag tt = xt.getCompound("track");
                x.track = new Track(tt.getString("card"), tt.getString("label"), tt.getDouble("lat"), tt.getDouble("lon"));
            }
            for (Tag lt : xt.getList("journal", Tag.TAG_COMPOUND)) {
                CompoundTag l = (CompoundTag) lt;
                x.journal.add(new Line(l.getInt("date"), l.getString("text"), l.getString("card")));
            }
            k.extras.put(xt.getUUID("player"), x);
        }
        return k;
    }
}
