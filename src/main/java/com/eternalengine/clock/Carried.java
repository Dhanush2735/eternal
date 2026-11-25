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
 * <b>The projection atlas</b>: what the engine can honestly say about AD 2027 to 3000.
 *
 * <h2>The question this answers</h2>
 *
 * <p>There is a great deal of data for 3000 BC to AD 2026 and very little for 2027 to 3000, and the
 * reason is not an unfinished pack. {@link Projector} refuses to invent a person, a ruler, an event,
 * a war, a disaster, a price or a population figure after the last recorded year, so the shape of
 * the future this mod can state is bounded by what may be <b>computed</b> and what may be
 * <b>carried</b>.
 *
 * <h2>The one genuinely computable change</h2>
 *
 * <p><b>No technology in the atlas arrives after 2025.</b> Every technology the mod knows was
 * invented before the seam, so the only honest change between 2027 and 3000 is the <b>spread</b> of
 * what already exists, and the engine's diffusion curve never clamped at 2026:
 *
 * <pre>
 *   share = maxShare / (1 + exp(-(year - arrived - d) / (d / 4)))
 * </pre>
 *
 * <p>This class reads that forward. Its finding is that <b>the future runs out in 2300</b>: 42
 * area-and-technology pairs are short of common in 2026, all 42 clear by 2300, and after that the
 * curve is flat for seven hundred years. So the honest answer to "what is the world like in 2400"
 * is "the world of 2026, fully spread", and everything a story adds past that point is an invention
 * this engine will not make.
 *
 * <h2>What it will not do</h2>
 *
 * <p>It names nobody, dates no event, prices nothing and counts no population. {@link #refused()}
 * returns that list so a caller can print the refusal rather than a blank.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Carried {

    public static final String RESOURCE =
            "/data/eternalengine/eternal/history/projection.json.gz";

    /** One step of the projection: how much of what is already known has reached people. */
    public record Step(int year, double mean, int common, int saturated) {
    }

    /** What had not yet gone common in 2026, and when it does. */
    public record Backlog(String id, int arrived, double share2026, @Nullable Integer commonBy) {
        public boolean clears() {
            return commonBy != null;
        }
    }

    private static volatile Carried instance;

    private final Map<String, List<Step>> steps;
    private final Map<String, List<Backlog>> backlog;
    private final List<Integer> years;
    private final List<String> refused;
    private final JsonObject counts;

    private Carried(JsonObject root) {
        Map<String, List<Step>> st = new LinkedHashMap<>();
        Map<String, List<Backlog>> bl = new LinkedHashMap<>();
        List<Integer> ys = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("steps")) ys.add(e.getAsInt());
        JsonObject areas = root.getAsJsonObject("areas");
        for (Map.Entry<String, JsonElement> a : areas.entrySet()) {
            JsonObject o = a.getValue().getAsJsonObject();
            List<Step> list = new ArrayList<>();
            JsonObject series = o.getAsJsonObject("series");
            for (Map.Entry<String, JsonElement> s : series.entrySet()) {
                JsonObject so = s.getValue().getAsJsonObject();
                list.add(new Step(Integer.parseInt(s.getKey()), so.get("mean").getAsDouble(),
                        so.get("common").getAsInt(), so.get("saturated").getAsInt()));
            }
            list.sort((x, y) -> Integer.compare(x.year(), y.year()));
            st.put(a.getKey(), List.copyOf(list));

            List<Backlog> b = new ArrayList<>();
            JsonElement arr = o.get("backlog");
            if (arr != null && arr.isJsonArray()) {
                for (JsonElement e : arr.getAsJsonArray()) {
                    JsonObject bo = e.getAsJsonObject();
                    b.add(new Backlog(bo.get("id").getAsString(),
                            bo.get("arrived").getAsInt(),
                            bo.get("share2026").getAsDouble(),
                            bo.has("commonBy") && !bo.get("commonBy").isJsonNull()
                                    ? bo.get("commonBy").getAsInt() : null));
                }
            }
            bl.put(a.getKey(), List.copyOf(b));
        }
        this.steps = Map.copyOf(st);
        this.backlog = Map.copyOf(bl);
        this.years = List.copyOf(ys);
        List<String> r = new ArrayList<>();
        if (root.has("refused")) {
            for (JsonElement e : root.getAsJsonArray("refused")) r.add(e.getAsString());
        }
        this.refused = List.copyOf(r);
        this.counts = root.getAsJsonObject("counts");
    }

    public static Carried get() {
        Carried c = instance;
        if (c == null) {
            synchronized (Carried.class) {
                c = instance;
                if (c == null) {
                    c = load();
                    instance = c;
                }
            }
        }
        return c;
    }

    private static Carried load() {
        try (InputStream raw = Carried.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Carried(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    public List<Integer> steps() {
        return years;
    }

    /** The six kinds that are never invented, so a caller can print the refusal and not a blank. */
    public List<String> refused() {
        return refused;
    }

    public boolean has(String area) {
        return steps.containsKey(area);
    }

    /**
     * The mean spread of already-invented technology in an area, in a year. Between the recorded
     * steps it is read as the earlier step, which is a floor and not a forecast: the atlas is a set
     * of ten-year marks and pretending to a finer curve would be inventing one.
     */
    public double spread(String area, int year) {
        List<Step> list = steps.get(area);
        if (list == null || list.isEmpty()) return 0.0d;
        Step best = list.get(0);
        for (Step s : list) {
            if (s.year() <= year) best = s;
        }
        return best.mean();
    }

    /** The count of technologies that have gone common in an area by a year. */
    public int common(String area, int year) {
        List<Step> list = steps.get(area);
        if (list == null || list.isEmpty()) return 0;
        Step best = list.get(0);
        for (Step s : list) {
            if (s.year() <= year) best = s;
        }
        return best.common();
    }

    /** What had still not gone common in 2026 in an area. */
    public List<Backlog> backlog(String area) {
        return backlog.getOrDefault(area, List.of());
    }

    /** The last year anything in this pack changes, which is also the year the projection ends. */
    public int flatFrom() {
        JsonElement e = counts.get("lastBacklogLands");
        return e == null ? Timeline.RECORDED_UNTIL : e.getAsInt();
    }

    /**
     * True when the year is past the point where anything in this pack still changes. A caller can
     * use it to say "nothing further is computed" instead of showing the same number without a word.
     */
    public boolean flat(int year) {
        return year >= flatFrom();
    }

    /** The mark a value from this pack must be shown with. Never {@code recorded}. */
    public String mark(int year) {
        return year > Timeline.RECORDED_UNTIL ? Timeline.PROJECTED : Timeline.RECORDED;
    }

    /** One line for a screen, which says what is carried and what is not. */
    public String line(String area, int year) {
        if (!has(area)) return area + ": not in the projection atlas";
        double s = spread(area, year);
        String body = String.format(Locale.ROOT,
                "%s %d: %.0f%% of what was already known in %d has reached people here, %d "
                        + "technologies common",
                area, year, s * 100, Timeline.RECORDED_UNTIL, common(area, year));
        if (flat(year)) {
            body += ". Nothing further is computed after " + flatFrom()
                    + ": the backlog of already-invented things has finished arriving, and this "
                    + "engine does not invent what comes next.";
        }
        return body + " [" + mark(year) + "]";
    }

    public String summary() {
        return "projection atlas: " + steps.size() + " areas, steps " + years.get(0) + " to "
                + years.get(years.size() - 1) + ", flat from " + flatFrom()
                + "; refused: " + String.join(", ", refused);
    }
}
