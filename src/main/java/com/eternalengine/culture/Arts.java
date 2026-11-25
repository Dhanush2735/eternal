package com.eternalengine.culture;

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
 * Step 47 part 6 (C15): <b>music and the arts</b>, the last part of "culture".
 *
 * <p>The plan's row is "clothes, food, music, games, customs, arts". By the end of part 6 five of
 * those six live somewhere: clothes in part 4's 27 period garments, food in the products atlas and
 * the diet register, games in {@link Games}, customs in {@code CultureAtlas} with its 21,000 holy
 * places, feasts, scripts and measures. <b>Music and the arts had nothing at all</b>, and this is
 * them.
 *
 * <h2>Why this is a register of instruments and not of music</h2>
 *
 * <p>There is no such thing as the year a place got music. Every human group sings, drums and
 * dances, and the oldest instrument known is a bone flute about 40,000 years old, which is
 * thirty-seven thousand years before the span begins. So the register is of <b>the things that carry
 * music and pictures</b>: an instrument, a medium, a way of copying. That is a technology history,
 * it dates honestly, and it has the same shape as part 2's tools, which is not a coincidence: a
 * violin is a manufactured object.
 *
 * <h2>The finding</h2>
 *
 * <p><b>Every art form here is older than the means of copying it, and copying changes what the art
 * is.</b> The two sharpest dates are the <b>phonograph in 1877</b>, which separates a performance
 * from the performer for the first time in the history of the species, and <b>photography in
 * 1839</b>, which arrives after the oil painting it replaces as the way to record a face and before
 * the cinema it makes possible.
 *
 * <p>As in {@link Games}, a spread year is written only where the record gives one, and
 * {@link #UNKNOWN} is not "never".
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Arts {

    public static final String RESOURCE = "/data/eternalengine/eternal/names/arts.json.gz";

    /** Not recorded here, and deliberately not the same as "never". */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    public record Work(String id, String name, String kind, int year, String home,
                       @Nullable String equipment, Map<String, Integer> spread, String note,
                       String source) {

        public int since(String area) {
            Integer y = spread.get(area);
            return y == null ? UNKNOWN : y;
        }

        public boolean recordedIn(String area, int y) {
            int s = since(area);
            return s != UNKNOWN && y >= s;
        }

        /** True for the rows that are in every area from the first year: song, dance, drum, flute. */
        public boolean universal() {
            return spread.size() >= 17 && year <= -3000;
        }

        public boolean copies() {
            return "copy".equals(kind) || "record".equals(kind);
        }
    }

    private static volatile Arts instance;

    private final List<Work> works;
    private final Map<String, Work> byId;
    private final Map<String, Work> byEquipment;
    private final List<String> areas;

    private Arts(JsonObject root) {
        List<Work> list = new ArrayList<>();
        Map<String, Work> byEq = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("arts")) {
            JsonObject o = e.getAsJsonObject();
            Work w = new Work(str(o, "id"), str(o, "name"), str(o, "kind"),
                    o.get("year").getAsInt(), str(o, "home"),
                    o.has("equipment") && !o.get("equipment").isJsonNull()
                            ? o.get("equipment").getAsString() : null,
                    ints(o, "spread"), str(o, "note"), str(o, "source"));
            list.add(w);
            if (w.equipment() != null) byEq.put(w.equipment(), w);
        }
        this.works = List.copyOf(list);
        Map<String, Work> m = new LinkedHashMap<>();
        for (Work w : list) {
            m.put(w.id(), w);
        }
        this.byId = Map.copyOf(m);
        this.byEquipment = Map.copyOf(byEq);
        this.areas = strings(root.getAsJsonArray("areas"));
    }

    public static Arts get() {
        Arts a = instance;
        if (a == null) {
            synchronized (Arts.class) {
                a = instance;
                if (a == null) {
                    a = load();
                    instance = a;
                }
            }
        }
        return a;
    }

    private static Arts load() {
        try (InputStream raw = Arts.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Arts(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    public List<Work> works() {
        return works;
    }

    public List<String> areas() {
        return areas;
    }

    @Nullable
    public Work work(String id) {
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public Work forEquipment(String item) {
        return byEquipment.get(item.toLowerCase(Locale.ROOT));
    }

    /** What an area has to make music or pictures with in a year, oldest first. */
    public List<String> availableIn(String area, int year) {
        List<Work> out = new ArrayList<>();
        for (Work w : works) {
            if (w.recordedIn(area, year)) out.add(w);
        }
        out.sort((a, b) -> Integer.compare(a.year(), b.year()));
        List<String> ids = new ArrayList<>();
        for (Work w : out) ids.add(w.id());
        return ids;
    }

    public String why(String id, String area, int year) {
        Work w = work(id);
        if (w == null) return id + ": not in the register";
        int s = w.since(area);
        if (s == UNKNOWN) {
            return w.name() + ": from " + w.home() + " in " + w.year()
                    + ", and this register does not record when it reached " + area;
        }
        if (year < s) return w.name() + ": from " + w.home() + ", not in " + area + " until " + s;
        return w.name() + ": from " + w.home() + ", here since " + s;
    }

    public int count() {
        return works.size();
    }

    public String summary() {
        return works.size() + " instruments, media and means of copying over " + areas.size()
                + " areas";
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
