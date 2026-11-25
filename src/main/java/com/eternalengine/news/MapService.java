package com.eternalengine.news;

import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.civilization.TradeRoutes;
import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.knowledge.KnowledgePacks;
import com.eternalengine.money.PurseService;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.MapViewPacket;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Step 19e: the map item. A knowledge pack may have its own hand-made map (Hyderabad State in 1948, with the towns and
 * the two roads of September 1948); everywhere else the map shows the gazetteer's towns of its year around the town it
 * was bought in, and the roads and trade routes of that year. Papers read while carrying it add their places as marks.
 */
public final class MapService {

    /** A plain map reaches this many degrees north and south of its town (about 245 km). */
    static final double HALF_LAT = 2.2d;
    /** Width to height of the map, in kilometres. */
    static final double ASPECT = 380.0d / 309.0d;
    static final int MAX_TOWNS = 40;

    private MapService() {
    }

    /** The map's title in a place: the pack's ("Map of Hyderabad State"), or "Map of the land around Kodad". */
    public static String title(double lat, double lon, int date) {
        KnowledgePacks.Pack pack = NewsService.pack(lat, lon, date);
        if (pack != null && pack.map() != null && !pack.map().title().isBlank()) {
            return pack.map().title();
        }
        String town = NewsService.town(NewsService.presence(lat, lon, KnowledgeDate.year(date)));
        return town.isBlank() ? "Map of the land around here" : "Map of the land around " + town;
    }

    /** A new map item for this place and day. */
    public static ItemStack item(double lat, double lon, int date) {
        ItemStack stack = new ItemStack(NewsItems.MAP.get());
        CompoundTag t = stack.getOrCreateTagElement(MapItem.TAG);
        t.putString("title", title(lat, lon, date));
        t.putDouble("lat", lat);
        t.putDouble("lon", lon);
        t.putInt("year", KnowledgeDate.year(date));
        t.putInt("date", date);
        t.putString("home", NewsService.town(NewsService.presence(lat, lon, KnowledgeDate.year(date))));
        t.put("marks", new ListTag());
        return stack;
    }

    /** Open a map item for its holder. */
    public static void open(ServerPlayer player, ItemStack stack) {
        CompoundTag t = stack.getTagElement(MapItem.TAG);
        if (t == null) {
            player.displayClientMessage(Component.literal("This map is blank."), true);
            return;
        }
        double[] you = PurseService.placeOf(player);
        EternalEnginePacketHandler.sendToPlayer(new MapViewPacket(view(t, you)), player);
    }

    /** What a map item shows. */
    public static MapView view(CompoundTag t, @Nullable double[] you) {
        double lat = t.getDouble("lat"), lon = t.getDouble("lon");
        int year = t.getInt("year"), date = t.getInt("date");
        MapView v = new MapView();
        v.title = t.getString("title");
        v.year = year;
        v.home = t.getString("home");
        KnowledgePacks.Pack pack = NewsService.pack(lat, lon, date);
        if (pack != null && pack.map() != null) {
            KnowledgePacks.PackMap m = pack.map();
            v.subtitle = m.subtitle().isBlank() ? "towns and main roads" : m.subtitle();
            v.minLat = m.minLat();
            v.maxLat = m.maxLat();
            v.minLon = m.minLon();
            v.maxLon = m.maxLon();
            for (KnowledgePacks.MapTown town : m.towns()) {
                v.towns.add(town(town.name(), town.lat(), town.lon(), town.major()));
            }
            v.roads.addAll(m.roads());
        } else {
            v.subtitle = "towns and roads of " + NewsService.yearText(year);
            double half = HALF_LAT;
            double halfLon = Math.min(60.0d, half * ASPECT / Math.max(0.1d, Math.cos(Math.toRadians(lat))));
            v.minLat = Math.max(-85.0d, lat - half);
            v.maxLat = Math.min(85.0d, lat + half);
            v.minLon = lon - halfLon;
            v.maxLon = lon + halfLon;
            towns(v, lat, lon, year);
            roads(v, year);
            if (v.roads.isEmpty()) {
                v.subtitle = "towns of " + NewsService.yearText(year);
            }
        }
        for (Tag x : t.getList("marks", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) x;
            v.marks.add(new MapView.Mark(m.getString("name"), m.getDouble("lat"), m.getDouble("lon"), m.getInt("date"),
                    m.getString("headline")));
        }
        if (you != null && you[0] >= v.minLat && you[0] <= v.maxLat && you[1] >= v.minLon && you[1] <= v.maxLon) {
            v.youLat = you[0];
            v.youLon = you[1];
        }
        return v;
    }

    static MapView.Town town(String name, double lat, double lon, boolean major) {
        int x = 0, z = 0;
        try {
            double[] xz = TerraCoordinateHelper.fromGeo(lat, lon);
            x = (int) Math.round(xz[0]);
            z = (int) Math.round(xz[1]);
        } catch (Throwable ignored) {
            // No Terra++ projection: no block position.
        }
        return new MapView.Town(name, lat, lon, major, x, z);
    }

    /**
     * The gazetteer's towns of the year inside the map: cities first, then towns, then (when there are few) villages
     * nearest the middle. Today's places (present-only rows, whose start is estimated) only from 1950 on, and never
     * as villages.
     */
    static void towns(MapView v, double lat, double lon, int year) {
        List<Object[]> found = new ArrayList<>();
        try {
            for (HistoryGazetteer.Site s : HistoryGazetteer.get().allSites()) {
                if (!s.occupiedIn(year) || s.lat() < v.minLat || s.lat() > v.maxLat || s.lon() < v.minLon
                        || s.lon() > v.maxLon || !HistoryGazetteer.isPinVisible(s.id())) {
                    continue;
                }
                SettlementRank rank = s.rankAt(year);
                if (s.presentOnly() && (year < 1950 || rank == SettlementRank.VILLAGE)) {
                    continue;
                }
                double km = KnowledgePacks.Geo.km(lat, lon, s.lat(), s.lon());
                found.add(new Object[]{s, rank, km});
            }
        } catch (Throwable ignored) {
            return;
        }
        found.sort((a, b) -> {
            int ra = ((SettlementRank) a[1]).ordinal(), rb = ((SettlementRank) b[1]).ordinal();
            return ra != rb ? Integer.compare(rb, ra) : Double.compare((double) a[2], (double) b[2]);
        });
        int townCount = 0;
        for (Object[] f : found) {
            townCount += f[1] == SettlementRank.VILLAGE ? 0 : 1;
        }
        boolean villages = townCount < 12;
        // First one town in each part of the map (6 by 5 parts), so the whole map has names and not only the middle;
        // then the rest, nearest the middle first.
        Map<String, Boolean> seen = new HashMap<>();
        List<Object[]> picked = new ArrayList<>();
        java.util.Set<Integer> cells = new java.util.HashSet<>();
        for (Object[] f : found) {
            HistoryGazetteer.Site s = (HistoryGazetteer.Site) f[0];
            if (!villages && f[1] == SettlementRank.VILLAGE) {
                continue;
            }
            int cx = (int) Math.min(5, Math.floor((s.lon() - v.minLon) / (v.maxLon - v.minLon) * 6));
            int cy = (int) Math.min(4, Math.floor((v.maxLat - s.lat()) / (v.maxLat - v.minLat) * 5));
            if (cells.add(cy * 6 + cx)) {
                picked.add(f);
            }
        }
        for (Object[] f : found) {
            if (picked.size() >= MAX_TOWNS * 2) {
                break;
            }
            if ((villages || f[1] != SettlementRank.VILLAGE) && !picked.contains(f)) {
                picked.add(f);
            }
        }
        for (Object[] f : picked) {
            HistoryGazetteer.Site s = (HistoryGazetteer.Site) f[0];
            SettlementRank rank = (SettlementRank) f[1];
            if (v.towns.size() >= MAX_TOWNS) {
                break;
            }
            String name = nice(s.displayName());
            if (name.isBlank() || seen.putIfAbsent(name.toLowerCase(Locale.ROOT), true) != null) {
                continue;
            }
            // Big towns in bold: the cities of the hand-made history files (a large list of ancient places calls
            // every small walled town a city, and forty bold names would bury the map).
            boolean major = rank == SettlementRank.CITY && !"pleiades".equalsIgnoreCase(s.confidence());
            v.towns.add(town(name, s.lat(), s.lon(), major));
        }
    }

    /** Roads (old roads until 1500) and the trade routes of the year that cross the map. */
    static void roads(MapView v, int year) {
        try {
            TradeRoutes tr = TradeRoutes.get();
            if (year <= TradeRoutes.OLD_ROAD_UNTIL) {
                for (TradeRoutes.Road r : tr.roads()) {
                    if (year >= r.start() && crosses(v, r.points())) {
                        v.roads.add(r.points());
                    }
                }
            }
            for (TradeRoutes.Route r : tr.routes()) {
                if (year >= r.start() && year <= r.end() && r.points() != null && crosses(v, r.points())) {
                    v.roads.add(r.points());
                }
            }
        } catch (Throwable ignored) {
            // No roads loaded: towns only.
        }
    }

    static boolean crosses(MapView v, double[] p) {
        if (p == null) {
            return false;
        }
        for (int i = 0; i + 1 < p.length; i += 2) {
            if (p[i] >= v.minLat && p[i] <= v.maxLat && p[i + 1] >= v.minLon && p[i + 1] <= v.maxLon) {
                return true;
            }
        }
        return false;
    }

    static String nice(String n) {
        if (n == null) {
            return "";
        }
        String s = n.replace('_', ' ').trim();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * A paper was read: its places go onto every map the player carries (a paper is marked once). Returns how many
     * maps the player carries.
     */
    public static int markAll(ServerPlayer player, PaperView paper) {
        List<MapView.Mark> marks = NewsService.marks(paper);
        int maps = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof MapItem)) {
                continue;
            }
            maps++;
            CompoundTag t = s.getOrCreateTagElement(MapItem.TAG);
            ListTag list = t.getList("marks", Tag.TAG_COMPOUND);
            if (marks.isEmpty() || NewsService.samePaper(list, paper)) {
                continue;
            }
            for (MapView.Mark m : marks) {
                CompoundTag c = new CompoundTag();
                c.putString("name", m.name());
                c.putDouble("lat", m.lat());
                c.putDouble("lon", m.lon());
                c.putInt("date", m.date());
                c.putString("headline", m.headline());
                c.putString("paper", paper.name);
                list.add(c);
            }
            while (list.size() > MapView.MAX_MARKS) {
                list.remove(0);
            }
            t.put("marks", list);
        }
        return maps;
    }

    /** The check with no world: the pack's map has its towns, and a paper's places land on it; null when all is well. */
    public static String selfTest() {
        int date = KnowledgeDate.key(1948, 9, 14);
        KnowledgePacks.Pack pack = NewsService.pack(17.3616, 78.4747, date);
        if (pack == null || pack.map() == null) {
            return "the Hyderabad 1948 pack has no map";
        }
        if (!"Map of Hyderabad State".equals(title(17.3616, 78.4747, date))) {
            return "the map in Hyderabad in 1948 should be the Map of Hyderabad State, not " + title(17.3616, 78.4747, date);
        }
        if (pack.map().towns().size() < 20 || pack.map().roads().size() != 2) {
            return "the Hyderabad State map should have its 24 towns and 2 roads";
        }
        String naldurg = NewsService.placed("Naldurg", pack, 17.3616, 78.4747);
        if (NewsService.placeAt(naldurg) == null) {
            return "Naldurg should be found on the pack's map";
        }
        return null;
    }
}
