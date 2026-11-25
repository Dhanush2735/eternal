package com.eternalengine.clock;

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
 * <b>The obsolescence atlas</b>: what stops being used, which {@link Carried} does not measure.
 *
 * <p>{@link Carried} measures <b>arrival</b>: nothing is invented after 2025, so 2027 to 3000 is the
 * spread of what already exists, and that finishes by 2300. This is the other half, and it was the
 * missing one.
 *
 * <h2>Why it is computed and not invented</h2>
 *
 * <p>`TechnologyAtlas` has always carried the graph that makes it computable: every technology lists
 * what it replaces, and {@code share()} already multiplies a dying technology by the share of what
 * replaced it. So the year a thing goes quiet falls out of two curves the atlas already has, and
 * nobody has to guess it.
 *
 * <h2>The finding, and the limit, which are the same sentence</h2>
 *
 * <p><b>The last technology this register can say goodbye to is the steamship, and it leaves
 * Australia in 2051.</b> Only 11 of the 130 technologies carry a successor, so only 102 pairs have
 * an end at all: 98 before the seam, 4 after it. The other 1,956 pairs have no successor in the
 * data, so nothing computes their end and they persist to 3000. That is the carry rule working and
 * it is also the honest shape of the register: <b>it can say goodbye to eleven things.</b>
 *
 * <p>The general point it does earn: <b>an obsolete technology is not a dead one.</b> The matchlock
 * is superseded in the 1720s and flintlocks are in use in the 1930s; gas light and the kerosene lamp
 * are both superseded by electricity in the 1930s and both are still sold. A world in a single year
 * is not of one era but of several standing next to each other.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Obsolescence {

    public static final String RESOURCE =
            "/data/eternalengine/eternal/history/obsolescence.json.gz";

    /** A record of a technology actually being lost in a place, from the atlas's own `lost` rows. */
    public record Lost(int from, int to) {
    }

    /** What happens to one technology in one area. */
    public record Fate(int arrived, int common, @Nullable Integer gone, String mark,
                       List<Lost> recorded) {

        /** True if the register can put an end to it in this area at all. */
        public boolean ends() {
            return gone != null;
        }

        public boolean goneBy(int year) {
            return gone != null && year >= gone;
        }
    }

    /** One technology: what replaced it, and what happens to it in each area. */
    public record Tech(String id, String name, List<String> replacedBy, Map<String, Fate> areas) {
        public boolean replaced() {
            return !replacedBy.isEmpty();
        }
    }

    private static volatile Obsolescence instance;

    private final List<Tech> techs;
    private final Map<String, Tech> byId;
    private final List<String> refused;
    private final JsonObject counts;

    private Obsolescence(JsonObject root) {
        List<Tech> list = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("technologies")) {
            JsonObject o = e.getAsJsonObject();
            List<String> rep = new ArrayList<>();
            for (JsonElement r : o.getAsJsonArray("replacedBy")) rep.add(r.getAsString());
            Map<String, Fate> areas = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> a : o.getAsJsonObject("areas").entrySet()) {
                JsonObject ao = a.getValue().getAsJsonObject();
                List<Lost> lost = new ArrayList<>();
                JsonElement arr = ao.get("recorded");
                if (arr != null && arr.isJsonArray()) {
                    for (JsonElement x : arr.getAsJsonArray()) {
                        JsonObject xo = x.getAsJsonObject();
                        lost.add(new Lost(xo.get("from").getAsInt(), xo.get("to").getAsInt()));
                    }
                }
                areas.put(a.getKey(), new Fate(
                        ao.get("arrived").getAsInt(),
                        ao.get("common").getAsInt(),
                        ao.get("gone") != null && !ao.get("gone").isJsonNull()
                                ? ao.get("gone").getAsInt() : null,
                        ao.get("mark").getAsString(),
                        List.copyOf(lost)));
            }
            list.add(new Tech(o.get("id").getAsString(), o.get("name").getAsString(),
                    List.copyOf(rep), Map.copyOf(areas)));
        }
        this.techs = List.copyOf(list);
        Map<String, Tech> m = new LinkedHashMap<>();
        for (Tech t : list) m.put(t.id(), t);
        this.byId = Map.copyOf(m);
        List<String> r = new ArrayList<>();
        if (root.has("refused")) {
            for (JsonElement e : root.getAsJsonArray("refused")) r.add(e.getAsString());
        }
        this.refused = List.copyOf(r);
        this.counts = root.getAsJsonObject("counts");
    }

    public static Obsolescence get() {
        Obsolescence o = instance;
        if (o == null) {
            synchronized (Obsolescence.class) {
                o = instance;
                if (o == null) {
                    o = load();
                    instance = o;
                }
            }
        }
        return o;
    }

    private static Obsolescence load() {
        try (InputStream raw = Obsolescence.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Obsolescence(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    public List<Tech> techs() {
        return techs;
    }

    public List<String> refused() {
        return refused;
    }

    @Nullable
    public Tech tech(String id) {
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** What happens to a technology in an area, or null if the register has nothing to say. */
    @Nullable
    public Fate fate(String id, String area) {
        Tech t = tech(id);
        return t == null ? null : t.areas().get(area);
    }

    /** True if the technology has ended in that area by that year. Unknown is false, not true. */
    public boolean gone(String id, String area, int year) {
        Fate f = fate(id, area);
        return f != null && f.goneBy(year);
    }

    /** The things still in use in an area in a year: the interesting question, not the arrivals. */
    public List<String> stillInUse(String area, int year) {
        List<String> out = new ArrayList<>();
        for (Tech t : techs) {
            Fate f = t.areas().get(area);
            if (f == null) continue;
            if (year >= f.arrived() && !f.goneBy(year)) out.add(t.id());
        }
        return out;
    }

    /** The technologies that ended inside a window, with the year. */
    public List<String> endingBetween(int first, int last) {
        List<String> out = new ArrayList<>();
        for (Tech t : techs) {
            for (Map.Entry<String, Fate> e : t.areas().entrySet()) {
                Integer g = e.getValue().gone();
                if (g != null && g >= first && g <= last) {
                    out.add(t.id() + "/" + e.getKey() + "@" + g);
                }
            }
        }
        return out;
    }

    /** The last year the register can put to anything, which is also the register's limit. */
    public int lastGone() {
        JsonElement e = counts.get("lastGone");
        return e == null || e.isJsonNull() ? Timeline.RECORDED_UNTIL : e.getAsInt();
    }

    /** How many technologies carry a successor at all: 11 of 130, and that is the honest number. */
    public int replacedCount() {
        return (int) techs.stream().filter(Tech::replaced).count();
    }

    public String line(String id, String area, int year) {
        Fate f = fate(id, area);
        if (f == null) return id + ": nothing in the register for " + area;
        String mark = year > Timeline.RECORDED_UNTIL ? Timeline.PROJECTED : f.mark();
        if (!f.ends()) {
            return id + ": here since " + f.arrived() + ", and nothing in the data replaces it, so "
                    + "it does not end [" + mark + "]";
        }
        if (f.goneBy(year)) {
            return id + ": gone from " + area + " by " + f.gone() + ", after being common from "
                    + f.common() + " [" + mark + "]";
        }
        return id + ": in use in " + area + " until " + f.gone() + " [" + mark + "]";
    }

    public String summary() {
        return "obsolescence: " + techs.size() + " technologies, " + replacedCount()
                + " with a successor; the last end it can put to anything is " + lastGone();
    }
}
