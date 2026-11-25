package com.eternalengine.news;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 19d: one newspaper (or handwritten newsletter) as the reader sees it. The server writes it when the paper is
 * bought and keeps it on the item, so a paper bought before history changed still tells the old news. Story text may
 * hold links {@code [[cardId|shown words]]} like the talk box.
 */
public final class PaperView {

    public static final int MAX_STORIES = 10;

    /** One story: a headline, its text, and the places it names (for the map). */
    public record Story(String headline, String text, List<String> places) {
    }

    /** True for a handwritten newsletter, false for a printed paper. */
    public boolean handwritten;
    public String name = "";
    /** "HYDERABAD · TUESDAY, 14 SEPTEMBER 1948 · ONE ANNA". */
    public String line = "";
    public int date;
    /** Where it was bought: the place, and its position (for the map's marks). */
    public String place = "";
    public double lat = Double.NaN;
    public double lon = Double.NaN;
    public final List<Story> stories = new ArrayList<>();
    /** A box with prices, e.g. "Wheat: 4 annas 5 pai for 16 sheaves". */
    public final List<String> prices = new ArrayList<>();
    public String pricesTitle = "";
    /** A small advertisement at the end, or "". */
    public String ad = "";
    /** Step 19f: a banner when history has changed here (a fork), or "". */
    public String fork = "";
    /** The note under the paper, filled in when it is read ("Names in this paper are now in your history book..."). */
    public String note = "";

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(handwritten);
        buf.writeUtf(cut(name, 80), 80);
        buf.writeUtf(cut(line, 160), 160);
        buf.writeInt(date);
        buf.writeUtf(cut(place, 80), 80);
        buf.writeDouble(lat);
        buf.writeDouble(lon);
        int n = Math.min(MAX_STORIES, stories.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Story s = stories.get(i);
            buf.writeUtf(cut(s.headline(), 160), 160);
            buf.writeUtf(cut(s.text(), 1200), 1200);
            int p = Math.min(8, s.places().size());
            buf.writeVarInt(p);
            for (int j = 0; j < p; j++) {
                buf.writeUtf(cut(s.places().get(j), 64), 64);
            }
        }
        int m = Math.min(8, prices.size());
        buf.writeVarInt(m);
        for (int i = 0; i < m; i++) {
            buf.writeUtf(cut(prices.get(i), 120), 120);
        }
        buf.writeUtf(cut(pricesTitle, 80), 80);
        buf.writeUtf(cut(ad, 200), 200);
        buf.writeUtf(cut(fork, 300), 300);
        buf.writeUtf(cut(note, 300), 300);
    }

    public static PaperView decode(FriendlyByteBuf buf) {
        PaperView v = new PaperView();
        v.handwritten = buf.readBoolean();
        v.name = buf.readUtf(80);
        v.line = buf.readUtf(160);
        v.date = buf.readInt();
        v.place = buf.readUtf(80);
        v.lat = buf.readDouble();
        v.lon = buf.readDouble();
        int n = Math.min(MAX_STORIES, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            String h = buf.readUtf(160);
            String t = buf.readUtf(1200);
            int p = Math.min(8, buf.readVarInt());
            List<String> places = new ArrayList<>();
            for (int j = 0; j < p; j++) {
                places.add(buf.readUtf(64));
            }
            v.stories.add(new Story(h, t, List.copyOf(places)));
        }
        int m = Math.min(8, buf.readVarInt());
        for (int i = 0; i < m; i++) {
            v.prices.add(buf.readUtf(120));
        }
        v.pricesTitle = buf.readUtf(80);
        v.ad = buf.readUtf(200);
        v.fork = buf.readUtf(300);
        v.note = buf.readUtf(300);
        return v;
    }

    /** The paper as item data, so it keeps its news. */
    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("handwritten", handwritten);
        t.putString("name", name);
        t.putString("line", line);
        t.putInt("date", date);
        t.putString("place", place);
        t.putDouble("lat", lat);
        t.putDouble("lon", lon);
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(MAX_STORIES, stories.size()); i++) {
            Story s = stories.get(i);
            CompoundTag st = new CompoundTag();
            st.putString("h", cut(s.headline(), 160));
            st.putString("t", cut(s.text(), 1200));
            ListTag places = new ListTag();
            for (String p : s.places()) {
                places.add(StringTag.valueOf(cut(p, 64)));
            }
            st.put("places", places);
            list.add(st);
        }
        t.put("stories", list);
        ListTag pr = new ListTag();
        for (String p : prices) {
            pr.add(StringTag.valueOf(cut(p, 120)));
        }
        t.put("prices", pr);
        t.putString("pricesTitle", pricesTitle);
        t.putString("ad", ad);
        t.putString("fork", fork);
        return t;
    }

    public static PaperView fromTag(CompoundTag t) {
        PaperView v = new PaperView();
        if (t == null) {
            return v;
        }
        v.handwritten = t.getBoolean("handwritten");
        v.name = t.getString("name");
        v.line = t.getString("line");
        v.date = t.getInt("date");
        v.place = t.getString("place");
        v.lat = t.contains("lat") ? t.getDouble("lat") : Double.NaN;
        v.lon = t.contains("lon") ? t.getDouble("lon") : Double.NaN;
        for (Tag x : t.getList("stories", Tag.TAG_COMPOUND)) {
            CompoundTag st = (CompoundTag) x;
            List<String> places = new ArrayList<>();
            for (Tag p : st.getList("places", Tag.TAG_STRING)) {
                places.add(p.getAsString());
            }
            v.stories.add(new Story(st.getString("h"), st.getString("t"), List.copyOf(places)));
        }
        for (Tag p : t.getList("prices", Tag.TAG_STRING)) {
            v.prices.add(p.getAsString());
        }
        v.pricesTitle = t.getString("pricesTitle");
        v.ad = t.getString("ad");
        v.fork = t.getString("fork");
        return v;
    }

    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
