package com.eternalengine.knowledge;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 20: what the history book shows (the approved preview 2): on the left every name the player has heard, in
 * groups, with how many notes; on the right the open name's notes (the day, who said it, what they said), or the
 * journal, or a family tree. Built on the server ({@link BookService}), drawn by the client; the client filters the
 * names by itself as the player types.
 */
public final class BookView {

    public static final int MAX_ENTRIES = 1500;
    public static final int MAX_NOTES = 16;
    public static final int MAX_LINKS = 24;
    public static final int MAX_LATER = 8;
    public static final int MAX_JOURNAL = 80;
    public static final int MAX_TREE = 16;
    private static final int TEXT = 1200;
    private static final int SHORT = 160;

    /** A name in the index: its card, title, kind ("Person") and how many notes (0: only the name). */
    public record Entry(String id, String title, String kind, int notes) {
    }

    /** A note: "14 Sep 1948", "Saleem, paper seller", what was said (names marked [[id|words]]). */
    public record Note(String date, String by, String text) {
    }

    /** A linked name the player has heard (it opens). */
    public record Link(String id, String label) {
    }

    /** A journal line: the day, what happened, and the card it is about (blank: none). */
    public record Day(String date, String text, String card) {
    }

    /** A person in a family tree: the card (blank: unknown to the player), the words, and whether it is "now". */
    public record Node(String id, String label, boolean now) {
    }

    public String subtitle = "";
    /** "notes", "journal" or "tree". */
    public String tab = "notes";
    public final List<Entry> entries = new ArrayList<>();
    public int heard;
    /** The open name's card id (blank: none open). */
    public String card = "";
    public String kind = "";
    public String title = "";
    public final List<Note> notes = new ArrayList<>();
    /** When the player only heard the name: what to do ("Anyone in the street can tell you."). */
    public String nothing = "";
    public boolean canTrack;
    public boolean tracking;
    /** "about 3 km north of you" (blank when the name has no place, or the player's place is unknown). */
    public String where = "";
    public final List<Link> links = new ArrayList<>();
    public int unheardLinks;
    /** Time traveller only (a setting): what comes later. */
    public final List<String> later = new ArrayList<>();
    /** The open name has a family tree (the tab shows). */
    public boolean familyTree;
    public String treeNote = "";
    public final List<Node> tree = new ArrayList<>();
    public final List<Day> journal = new ArrayList<>();
    /** What the last action did ("Your compass now points to King Kothi."). */
    public String hint = "";
    /** "Tracking: King Kothi" for the header (blank: the compass points nowhere). */
    public String trackingLabel = "";

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cut(subtitle, SHORT), SHORT);
        buf.writeUtf(cut(tab, 16), 16);
        int n = Math.min(MAX_ENTRIES, entries.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            buf.writeUtf(cut(e.id(), 96), 96);
            buf.writeUtf(cut(e.title(), SHORT), SHORT);
            buf.writeUtf(cut(e.kind(), 32), 32);
            buf.writeVarInt(Math.max(0, e.notes()));
        }
        buf.writeVarInt(Math.max(0, heard));
        buf.writeUtf(cut(card, 96), 96);
        buf.writeUtf(cut(kind, 32), 32);
        buf.writeUtf(cut(title, SHORT), SHORT);
        n = Math.min(MAX_NOTES, notes.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Note x = notes.get(i);
            buf.writeUtf(cut(x.date(), 40), 40);
            buf.writeUtf(cut(x.by(), SHORT), SHORT);
            buf.writeUtf(cut(x.text(), TEXT), TEXT);
        }
        buf.writeUtf(cut(nothing, 400), 400);
        buf.writeBoolean(canTrack);
        buf.writeBoolean(tracking);
        buf.writeUtf(cut(where, SHORT), SHORT);
        n = Math.min(MAX_LINKS, links.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(cut(links.get(i).id(), 96), 96);
            buf.writeUtf(cut(links.get(i).label(), SHORT), SHORT);
        }
        buf.writeVarInt(Math.max(0, unheardLinks));
        n = Math.min(MAX_LATER, later.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(cut(later.get(i), 400), 400);
        }
        buf.writeBoolean(familyTree);
        buf.writeUtf(cut(treeNote, 400), 400);
        n = Math.min(MAX_TREE, tree.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Node t = tree.get(i);
            buf.writeUtf(cut(t.id(), 96), 96);
            buf.writeUtf(cut(t.label(), SHORT), SHORT);
            buf.writeBoolean(t.now());
        }
        n = Math.min(MAX_JOURNAL, journal.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Day d = journal.get(i);
            buf.writeUtf(cut(d.date(), 40), 40);
            buf.writeUtf(cut(d.text(), 400), 400);
            buf.writeUtf(cut(d.card(), 96), 96);
        }
        buf.writeUtf(cut(hint, 400), 400);
        buf.writeUtf(cut(trackingLabel, SHORT), SHORT);
    }

    public static BookView decode(FriendlyByteBuf buf) {
        BookView v = new BookView();
        v.subtitle = buf.readUtf(SHORT);
        v.tab = buf.readUtf(16);
        int n = Math.min(MAX_ENTRIES, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            v.entries.add(new Entry(buf.readUtf(96), buf.readUtf(SHORT), buf.readUtf(32), buf.readVarInt()));
        }
        v.heard = buf.readVarInt();
        v.card = buf.readUtf(96);
        v.kind = buf.readUtf(32);
        v.title = buf.readUtf(SHORT);
        n = Math.min(MAX_NOTES, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            v.notes.add(new Note(buf.readUtf(40), buf.readUtf(SHORT), buf.readUtf(TEXT)));
        }
        v.nothing = buf.readUtf(400);
        v.canTrack = buf.readBoolean();
        v.tracking = buf.readBoolean();
        v.where = buf.readUtf(SHORT);
        n = Math.min(MAX_LINKS, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            v.links.add(new Link(buf.readUtf(96), buf.readUtf(SHORT)));
        }
        v.unheardLinks = buf.readVarInt();
        n = Math.min(MAX_LATER, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            v.later.add(buf.readUtf(400));
        }
        v.familyTree = buf.readBoolean();
        v.treeNote = buf.readUtf(400);
        n = Math.min(MAX_TREE, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            v.tree.add(new Node(buf.readUtf(96), buf.readUtf(SHORT), buf.readBoolean()));
        }
        n = Math.min(MAX_JOURNAL, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            v.journal.add(new Day(buf.readUtf(40), buf.readUtf(400), buf.readUtf(96)));
        }
        v.hint = buf.readUtf(400);
        v.trackingLabel = buf.readUtf(SHORT);
        return v;
    }

    /** The index groups, in the preview's order; other kinds follow under their own name. */
    public static final String[][] GROUPS = {{"Event", "Events"}, {"Person", "People"}, {"Group", "Groups"},
            {"Family", "Families"}, {"State", "States"}, {"Place", "Places"}};

    /** The group heading of a kind ("Person" -> "People"; another kind with an s). */
    public static String groupName(String kind) {
        String k = kind == null || kind.isBlank() ? "Name" : kind;
        for (String[] g : GROUPS) {
            if (g[0].equalsIgnoreCase(k)) {
                return g[1];
            }
        }
        return k.endsWith("s") ? k : k + "s";
    }

    /** The entry of a card, or null. */
    public Entry entry(String id) {
        for (Entry e : entries) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
