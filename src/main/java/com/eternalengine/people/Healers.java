package com.eternalengine.people;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 47 part 6 (E15): <b>medicine and healers by era</b> — the half that was missing.
 *
 * <p>The other half is done and the plan's row does not know it. Step 38's health register carries
 * 14 remedies, each with a year, a place it was learned and a multiplier saying what it actually
 * did; {@link com.eternalengine.disease.MedicineSystem#tierAt} asks that register by coordinates, so
 * the old fault (the whole planet becoming modern on one day in 1945) is already gone.
 *
 * <h2>A healer is not a remedy</h2>
 *
 * <p>A remedy is <b>a thing that works</b> and it spreads as technology spreads. A healer is <b>a
 * person you can get to</b>, and their data is a different shape: who is available, how far a
 * patient travels, what they will touch, and <b>whether the neighbours approve</b>. The last one is
 * what a game always leaves out and it is the most historical of the four.
 *
 * <h2>Two axes, because one cannot say the midwife's story</h2>
 *
 * <p>The midwife does not arrive in Europe in 1450. She has been at every birth since the first one;
 * in 1450 she becomes <b>prosecutable</b> and by about 1750 she is ordinary again. So a row here has
 * an arrival, and it may also have a list of {@code standingChanges}. A register with a single year
 * in and a single year out cannot say that, and she is the case that proves it needs two.
 *
 * <h2>The finding: the order of events is not the obvious one</h2>
 *
 * <p><b>Surgery is four thousand years older than anaesthesia.</b> Trepanation is Neolithic, cataract
 * couching is described by Sushruta, a Roman army surgeon could ligate an artery and a Caesarean is
 * recorded on a living woman long before anything was sterile. What arrives in <b>1846</b> is only
 * the part where the patient does not feel it. A game that gates the knife on anaesthesia has the
 * order exactly backwards, and {@link #precedes} is the method that proves it.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Healers {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/healers.json.gz";

    /** One change in how a trade is regarded, which is not the same as when it arrives. */
    public record Change(String area, int year, String standing) {
    }

    /** One trade: who, from when, how far you travel, what they touch, and how they are seen. */
    public record Healer(String id, String name, String kind, int year, String reach,
                         List<String> tends, String standing, Map<String, Integer> areas,
                         List<Change> standingChanges, String note, String source) {

        /** The year this trade is available in an area. */
        public int since(String area) {
            Integer y = areas.get(area);
            return y == null ? year : y;
        }

        /** True if this trade will touch that complaint at all. */
        public boolean tends(String complaint) {
            return tends.contains(complaint);
        }

        /** How the trade is regarded in an area in a year, after any changes. */
        public String standingAt(String area, int y) {
            String out = standing;
            for (Change c : standingChanges) {
                if (c.area().equals(area) && y >= c.year()) out = c.standing();
            }
            return out;
        }

        public boolean available(String area, int y) {
            return y >= since(area);
        }
    }

    private static volatile Healers instance;

    private final List<Healer> healers;
    private final Map<String, Healer> byId;
    private final List<String> areas;
    private final List<String> complaints;
    private final List<String> reachOrder;

    private Healers(JsonObject root) {
        List<Healer> list = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("healers")) {
            JsonObject o = e.getAsJsonObject();
            List<Change> changes = new ArrayList<>();
            if (o.has("standingChanges")) {
                for (JsonElement c : o.getAsJsonArray("standingChanges")) {
                    JsonObject co = c.getAsJsonObject();
                    changes.add(new Change(str(co, "area"), co.get("year").getAsInt(),
                            str(co, "standing")));
                }
            }
            list.add(new Healer(str(o, "id"), str(o, "name"), str(o, "kind"),
                    o.get("year").getAsInt(), str(o, "reach"), strings(o.get("tends")),
                    str(o, "standing"), ints(o, "areas"), List.copyOf(changes),
                    str(o, "note"), str(o, "source")));
        }
        this.healers = List.copyOf(list);
        Map<String, Healer> m = new LinkedHashMap<>();
        for (Healer h : list) {
            m.put(h.id(), h);
        }
        this.byId = Map.copyOf(m);
        this.areas = strings(root.getAsJsonArray("areas"));
        this.complaints = strings(root.getAsJsonArray("tends"));
        this.reachOrder = strings(root.getAsJsonArray("reach"));
    }

    public static Healers get() {
        Healers h = instance;
        if (h == null) {
            synchronized (Healers.class) {
                h = instance;
                if (h == null) {
                    h = load();
                    instance = h;
                }
            }
        }
        return h;
    }

    private static Healers load() {
        try (InputStream raw = Healers.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Healers(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    public List<Healer> healers() {
        return healers;
    }

    public List<String> areas() {
        return areas;
    }

    public List<String> complaints() {
        return complaints;
    }

    @Nullable
    public Healer healer(String id) {
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** Everybody in an area in a year who will touch that complaint, nearest reach first. */
    public List<Healer> whoTends(String complaint, String area, int year) {
        List<Healer> out = new ArrayList<>();
        for (Healer h : healers) {
            if (h.available(area, year) && h.tends(complaint)) out.add(h);
        }
        out.sort((a, b) -> Integer.compare(reachOrder.indexOf(a.reach()),
                reachOrder.indexOf(b.reach())));
        return out;
    }

    /** The nearest person who can help, or null. This is the call a village would make. */
    @Nullable
    public Healer nearest(String complaint, String area, int year) {
        List<Healer> all = whoTends(complaint, area, year);
        return all.isEmpty() ? null : all.get(0);
    }

    /** True if the trade is in the area before the other one is. */
    public boolean precedes(String first, String second, String area) {
        Healer a = healer(first);
        Healer b = healer(second);
        return a != null && b != null && a.since(area) < b.since(area);
    }

    /** The trades regarded with suspicion or worse in a place in a year. */
    public List<String> suspectIn(String area, int year) {
        List<String> out = new ArrayList<>();
        for (Healer h : healers) {
            if (!h.available(area, year)) continue;
            String st = h.standingAt(area, year);
            if ("suspect".equals(st) || "prosecuted".equals(st)) out.add(h.id());
        }
        return out;
    }

    public String summary() {
        return healers.size() + " trades over " + areas.size() + " areas; "
                + complaints.size() + " kinds of complaint";
    }

    // ---------------------------------------------------------------------------------------
    private static Map<String, Integer> ints(JsonObject o, String key) {
        Map<String, Integer> out = new LinkedHashMap<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonObject()) return Map.copyOf(out);
        for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
            if (x.getValue().isJsonNull()) continue;
            out.put(x.getKey(), x.getValue().getAsInt());
        }
        return Map.copyOf(out);
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || !e.isJsonArray()) return List.copyOf(out);
        for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
