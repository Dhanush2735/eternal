package com.eternalengine.wild;

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
 * Step 47 part 5 (B5): <b>wild animals by era</b>.
 *
 * <p>The plan asks for "lions and cheetahs in India, aurochs in Europe, no horses in the Americas
 * before 1493". Before this class the <b>only</b> wild animal anywhere in the engine was the bison,
 * and it was there because it was hunted in herds (step 46), which is not the same thing as being
 * in a place.
 *
 * <h2>What a wild animal's data actually is</h2>
 *
 * <p>It is not a birth date. It is <b>a range and the year the range ended</b>, and almost every
 * row in this register is one of three stories:
 *
 * <ol>
 *   <li><b>Gone from here, alive there.</b> The lion is in the Iliad as a predator of the Greek
 *       hills, fights in Roman arenas, and is gone from Greece by about 100 BC; in India it
 *       survives outside Gujarat until <b>1884</b>. Same species, two different endings.</li>
 *   <li><b>Gone everywhere.</b> The aurochs at Jaktorów in <b>1627</b>, the passenger pigeon in
 *       Cincinnati in <b>1914</b>, the thylacine in Hobart in <b>1936</b>. Nobody had to guess any
 *       of those years, because somebody was watching the last one.</li>
 *   <li><b>Never there.</b> This is the plan's own example and it is the one a game usually gets
 *       wrong. The horse died out in the Americas at the end of the Pleistocene and the continent
 *       had none for ten thousand years, so <b>a horse in the Americas in 1200 is not a small
 *       anachronism, it is the largest biological fact about the continent</b>. An absence with a
 *       reason is data, and a missing row is not.</li>
 * </ol>
 *
 * <h2>Why the reason matters more than the year</h2>
 *
 * <p>{@link #why} never answers "no" and stops. It says which of the three stories applies, because
 * the three need different things from a game: an extirpated animal can be reintroduced, an extinct
 * one cannot, and an animal that was never there is not a conservation problem at all.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Wildlife {

    public static final String RESOURCE = "/data/eternalengine/eternal/names/wildlife.json.gz";

    /** The answer for "it was never there", which is not the same as "before the record". */
    public static final int NEVER = Integer.MIN_VALUE;

    /** One species: where it is, from when, and gone when. A {@code null} gone year means alive. */
    public record Species(String id, String name, String mark, Map<String, Integer> from,
                          Map<String, Integer> gone, Map<String, String> never, String note,
                          String source) {

        /** The year it stops being here, or {@code null} if it has not stopped. */
        @Nullable
        public Integer goneYear(String area) {
            return gone.get(area);
        }

        /** True if this species is recorded as never having been in the area at all. */
        public boolean neverHere(String area) {
            return never.containsKey(area);
        }
    }

    /** A one-line reading for a species in a place in a year. */
    public record Answer(String species, String area, int year, boolean here, String kind,
                         String why) {
        public boolean recorded() {
            return "here".equals(kind) || "gone".equals(kind);
        }
    }

    private static volatile Wildlife instance;

    private final List<Species> species;
    private final Map<String, Species> byId;
    private final List<String> areas;

    private Wildlife(JsonObject root) {
        List<Species> list = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("species")) {
            JsonObject o = e.getAsJsonObject();
            list.add(new Species(str(o, "id"), str(o, "name"), str(o, "mark"),
                    ints(o, "from"), ints(o, "gone"), strings(o, "never"),
                    str(o, "note"), str(o, "source")));
        }
        this.species = List.copyOf(list);
        Map<String, Species> m = new LinkedHashMap<>();
        for (Species s : list) {
            m.put(s.id(), s);
        }
        this.byId = Map.copyOf(m);
        this.areas = strings(root.getAsJsonArray("areas"));
    }

    public static Wildlife get() {
        Wildlife w = instance;
        if (w == null) {
            synchronized (Wildlife.class) {
                w = instance;
                if (w == null) {
                    w = load();
                    instance = w;
                }
            }
        }
        return w;
    }

    private static Wildlife load() {
        try (InputStream raw = Wildlife.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Wildlife(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    public List<Species> species() {
        return species;
    }

    public List<String> areas() {
        return areas;
    }

    @Nullable
    public Species species(String id) {
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean has(String id) {
        return byId.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /** True if the species is in the area in that year. Never-there areas are false, not unknown. */
    public boolean present(String id, String area, int year) {
        Species s = species(id);
        if (s == null || s.neverHere(area)) return false;
        Integer from = s.from().get(area);
        if (from == null) return false;
        if (year < from) return false;
        Integer gone = s.gone().get(area);
        return gone == null || year < gone;
    }

    /**
     * The full answer, with the reason. This is the method a caller should use: "no" on its own
     * cannot tell an animal that was hunted out from one that was never there.
     */
    public Answer why(String id, String area, int year) {
        Species s = species(id);
        if (s == null) {
            return new Answer(id, area, year, false, "unknown", "no such species in the register");
        }
        if (s.neverHere(area)) {
            return new Answer(id, area, year, false, "never", s.never().get(area));
        }
        Integer from = s.from().get(area);
        if (from == null) {
            return new Answer(id, area, year, false, "never",
                    "the register does not place this animal in " + area);
        }
        if (year < from) {
            return new Answer(id, area, year, false, "not yet",
                    "the register's span for " + area + " starts in " + from);
        }
        Integer gone = s.gone().get(area);
        if (gone != null && year >= gone) {
            return new Answer(id, area, year, false, "gone",
                    "gone from " + area + " by " + gone + "; it was here from " + from);
        }
        return new Answer(id, area, year, true, "here",
                "here since " + from + (gone == null ? ", and not gone in the record"
                        : ", and gone by " + gone));
    }

    /** Every species recorded in an area in a year, in register order. */
    public List<String> inArea(String area, int year) {
        List<String> out = new ArrayList<>();
        for (Species s : species) {
            if (present(s.id(), area, year)) out.add(s.id());
        }
        return out;
    }

    /** Every area a species has been lost from inside the record, with the year. */
    public Map<String, Integer> losses(String id) {
        Species s = species(id);
        if (s == null) return Map.of();
        Map<String, Integer> out = new LinkedHashMap<>();
        s.gone().forEach((a, y) -> out.put(a, y));
        return out;
    }

    public int count() {
        return species.size();
    }

    /** How many species are gone from somewhere inside the record: the register's own subject. */
    public int extirpatedSomewhere() {
        int n = 0;
        for (Species s : species) {
            if (!s.gone().isEmpty()) n++;
        }
        return n;
    }

    /** One line for a screen or a log. */
    public String summary() {
        return species.size() + " wild species over " + areas.size()
                + " areas; " + extirpatedSomewhere() + " lost from somewhere in the record";
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

    private static Map<String, String> strings(JsonObject o, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonObject()) return Map.copyOf(out);
        for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
            out.put(x.getKey(), x.getValue().getAsString());
        }
        return Map.copyOf(out);
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
