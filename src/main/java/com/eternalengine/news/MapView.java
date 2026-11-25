package com.eternalengine.news;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 19e: a map as the reader sees it: its towns and roads in its year, the marks from the papers read with it, and
 * where the reader stands. Towns carry their block position, for the "Add to Xaero's map" button.
 */
public final class MapView {

    public static final int MAX_TOWNS = 64;
    public static final int MAX_MARKS = 64;
    public static final int MAX_POINTS = 3000;

    public record Town(String name, double lat, double lon, boolean major, int x, int z) {
    }

    /** A place a paper named: the place, the paper's date, and the headline. */
    public record Mark(String name, double lat, double lon, int date, String headline) {
    }

    public String title = "";
    public String subtitle = "";
    public int year;
    public double minLat;
    public double maxLat;
    public double minLon;
    public double maxLon;
    public final List<Town> towns = new ArrayList<>();
    /** Roads as lat, lon pairs. */
    public final List<double[]> roads = new ArrayList<>();
    public final List<Mark> marks = new ArrayList<>();
    /** Where the reader stands (NaN: off the map or no world). */
    public double youLat = Double.NaN;
    public double youLon = Double.NaN;
    /** The town the map was bought in (drawn as a square). */
    public String home = "";

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(PaperView.cut(title, 80), 80);
        buf.writeUtf(PaperView.cut(subtitle, 80), 80);
        buf.writeInt(year);
        buf.writeDouble(minLat);
        buf.writeDouble(maxLat);
        buf.writeDouble(minLon);
        buf.writeDouble(maxLon);
        int n = Math.min(MAX_TOWNS, towns.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Town t = towns.get(i);
            buf.writeUtf(PaperView.cut(t.name(), 48), 48);
            buf.writeDouble(t.lat());
            buf.writeDouble(t.lon());
            buf.writeBoolean(t.major());
            buf.writeVarInt(t.x());
            buf.writeVarInt(t.z());
        }
        List<double[]> keep = new ArrayList<>();
        int points = 0;
        for (double[] r : roads) {
            if (r.length < 4 || points + r.length / 2 > MAX_POINTS) {
                continue;
            }
            keep.add(r);
            points += r.length / 2;
        }
        buf.writeVarInt(keep.size());
        for (double[] r : keep) {
            buf.writeVarInt(r.length / 2);
            for (int i = 0; i + 1 < r.length; i += 2) {
                buf.writeFloat((float) r[i]);
                buf.writeFloat((float) r[i + 1]);
            }
        }
        int m = Math.min(MAX_MARKS, marks.size());
        buf.writeVarInt(m);
        for (int i = 0; i < m; i++) {
            Mark k = marks.get(i);
            buf.writeUtf(PaperView.cut(k.name(), 48), 48);
            buf.writeDouble(k.lat());
            buf.writeDouble(k.lon());
            buf.writeInt(k.date());
            buf.writeUtf(PaperView.cut(k.headline(), 160), 160);
        }
        buf.writeDouble(youLat);
        buf.writeDouble(youLon);
        buf.writeUtf(PaperView.cut(home, 48), 48);
    }

    public static MapView decode(FriendlyByteBuf buf) {
        MapView v = new MapView();
        v.title = buf.readUtf(80);
        v.subtitle = buf.readUtf(80);
        v.year = buf.readInt();
        v.minLat = buf.readDouble();
        v.maxLat = buf.readDouble();
        v.minLon = buf.readDouble();
        v.maxLon = buf.readDouble();
        int n = Math.min(MAX_TOWNS, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            v.towns.add(new Town(buf.readUtf(48), buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt()));
        }
        int r = buf.readVarInt();
        int points = 0;
        for (int i = 0; i < r; i++) {
            int p = buf.readVarInt();
            if (p < 0 || points + p > MAX_POINTS) {
                throw new IllegalStateException("too many road points");
            }
            points += p;
            double[] pts = new double[p * 2];
            for (int j = 0; j < p; j++) {
                pts[2 * j] = buf.readFloat();
                pts[2 * j + 1] = buf.readFloat();
            }
            v.roads.add(pts);
        }
        int m = Math.min(MAX_MARKS, buf.readVarInt());
        for (int i = 0; i < m; i++) {
            v.marks.add(new Mark(buf.readUtf(48), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readUtf(160)));
        }
        v.youLat = buf.readDouble();
        v.youLon = buf.readDouble();
        v.home = buf.readUtf(48);
        return v;
    }

    /** The marks for one town, oldest first. */
    public List<Mark> marksFor(String town) {
        List<Mark> out = new ArrayList<>();
        for (Mark k : marks) {
            if (k.name().equalsIgnoreCase(town)) {
                out.add(k);
            }
        }
        out.sort((a, b) -> Integer.compare(a.date(), b.date()));
        return out;
    }

    /** The different dates of the marks, oldest first (the legend). */
    public List<Integer> markDates() {
        List<Integer> out = new ArrayList<>();
        for (Mark k : marks) {
            if (!out.contains(k.date())) {
                out.add(k.date());
            }
        }
        out.sort(Integer::compare);
        return out;
    }
}
