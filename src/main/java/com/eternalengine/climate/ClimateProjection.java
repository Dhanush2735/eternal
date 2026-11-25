package com.eternalengine.climate;

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
 * <b>Climate to AD 3000, as arithmetic under a stated assumption.</b>
 *
 * <h2>Why this is allowed when a price is not</h2>
 *
 * <p>The guardrail refuses six kinds of invention after the seam: a person, a ruler, an event, a war,
 * a disaster, a price and a population figure. Climate is not among them, because it is
 * <b>arithmetic and not prophecy</b>. Carbon leaves the air at rates measured from the isotope
 * record, temperature follows the logarithm of carbon with a thermal lag, and sea level follows
 * temperature with a slower lag still.
 *
 * <p>None of that says what people will do. So this pack does not forecast. It answers:
 * <b>if</b> emissions follow this path, <b>then</b> this is what the physics does. The assumption is a
 * field on every scenario and {@link #assumption} returns it, because a screen that shows a number
 * without its assumption is misusing the pack.
 *
 * <h2>Every value is a band, which is the lesson from the backtest</h2>
 *
 * <p>Step 47c hid the data after 1970 and found that the same kind of projection is wrong by about
 * twenty points when printed as one number. So nothing here is a point: each value carries
 * {@code low}, {@code mid} and {@code high}, from running the same physics at the low and high ends
 * of the two parameters nobody knows, climate sensitivity and the rate at which ice responds.
 *
 * <p>Carbon is the exception and it is worth knowing why: the carbon band is <b>flat</b>, because
 * carbon dioxide in the air is chemistry rather than a sensitivity, and no setting of the climate
 * parameters changes it. Only the emissions path does.
 *
 * <h2>The finding</h2>
 *
 * <p><b>Sea level is the one number in this project that only ever goes up.</b> Even the scenario
 * with emissions stopping dead in 2027 leaves 0.47 m of sea level by 3000, and by then the gap
 * between that path and the delayed one has <b>widened rather than closed</b>, because the ice term
 * keeps integrating after the temperature has stopped falling.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class ClimateProjection {

    public static final String RESOURCE =
            "/data/eternalengine/eternal/history/climate_projection.json.gz";

    /** A band, because a point estimate of this kind is wrong by about twenty points. */
    public record Band(double low, double mid, double high) {
        public boolean contains(double v) {
            return v >= low - 1e-9 && v <= high + 1e-9;
        }

        /** True when the band is degenerate, which for carbon it is, and that is not a mistake. */
        public boolean flat() {
            return Math.abs(high - low) < 1e-9;
        }

        public String one() {
            return String.format(Locale.ROOT, "%.2f (%.2f to %.2f)", mid, low, high);
        }
    }

    /** One mark in a scenario. */
    public record Point(int year, Band co2, Band temp, Band slr) {
    }

    /** A path and the assumption that produces it. Never a prediction. */
    public record Scenario(String id, String name, String assumption, List<Point> points,
                           String mark) {

        @Nullable
        public Point at(int year) {
            Point best = null;
            for (Point p : points) {
                if (p.year() == year) return p;
                if (p.year() <= year) best = p;
            }
            return best;
        }
    }

    private static volatile ClimateProjection instance;

    private final Map<String, Scenario> scenarios;
    private final List<Integer> marks;
    private final List<String> refused;
    private final JsonObject start;

    private ClimateProjection(JsonObject root) {
        Map<String, Scenario> m = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("scenarios")) {
            JsonObject o = e.getAsJsonObject();
            List<Point> pts = new ArrayList<>();
            for (JsonElement p : o.getAsJsonArray("points")) {
                JsonObject po = p.getAsJsonObject();
                pts.add(new Point(po.get("year").getAsInt(),
                        band(po.getAsJsonObject("co2")), band(po.getAsJsonObject("temp")),
                        band(po.getAsJsonObject("slr"))));
            }
            m.put(o.get("id").getAsString(), new Scenario(o.get("id").getAsString(),
                    o.get("name").getAsString(), o.get("assumption").getAsString(),
                    List.copyOf(pts), o.get("mark").getAsString()));
        }
        this.scenarios = Map.copyOf(m);
        List<Integer> ys = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("marks")) ys.add(e.getAsInt());
        this.marks = List.copyOf(ys);
        List<String> r = new ArrayList<>();
        if (root.has("refused")) {
            for (JsonElement e : root.getAsJsonArray("refused")) r.add(e.getAsString());
        }
        this.refused = List.copyOf(r);
        this.start = root.getAsJsonObject("start");
    }

    public static ClimateProjection get() {
        ClimateProjection c = instance;
        if (c == null) {
            synchronized (ClimateProjection.class) {
                c = instance;
                if (c == null) {
                    c = load();
                    instance = c;
                }
            }
        }
        return c;
    }

    private static ClimateProjection load() {
        try (InputStream raw = ClimateProjection.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new ClimateProjection(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    public List<String> scenarios() {
        return new ArrayList<>(scenarios.keySet());
    }

    public List<Integer> marks() {
        return marks;
    }

    public List<String> refused() {
        return refused;
    }

    @Nullable
    public Scenario scenario(String id) {
        return scenarios.get(id.toLowerCase(Locale.ROOT));
    }

    /** The assumption behind a scenario, in words, which must be shown with any number from it. */
    public String assumption(String id) {
        Scenario s = scenario(id);
        return s == null ? id + ": no such scenario" : s.assumption();
    }

    /** Sea level above the 2026 line, as a band. The one figure here that only ever rises. */
    @Nullable
    public Band seaLevel(String scenario, int year) {
        Scenario s = scenario(scenario);
        Point p = s == null ? null : s.at(year);
        return p == null ? null : p.slr();
    }

    @Nullable
    public Band temperature(String scenario, int year) {
        Scenario s = scenario(scenario);
        Point p = s == null ? null : s.at(year);
        return p == null ? null : p.temp();
    }

    @Nullable
    public Band carbon(String scenario, int year) {
        Scenario s = scenario(scenario);
        Point p = s == null ? null : s.at(year);
        return p == null ? null : p.co2();
    }

    /** True if sea level at that mark is above the one before it, which is the claim worth testing. */
    public boolean rising(String scenario) {
        Scenario s = scenario(scenario);
        if (s == null) return false;
        double prev = -1;
        for (Point p : s.points()) {
            if (p.slr().mid() < prev - 1e-9) return false;
            prev = p.slr().mid();
        }
        return true;
    }

    /**
     * The sentence a screen should show. It always carries the assumption and the band, because a
     * number from this pack without either is a misuse of it.
     */
    public String line(String scenario, int year) {
        Scenario s = scenario(scenario);
        if (s == null) return scenario + ": no such scenario";
        Point p = s.at(year);
        if (p == null) return s.name() + ": before the projection begins";
        return String.format(Locale.ROOT,
                "%s [%s]: IF %s THEN in %d the air is %.0f ppm, the world is %s C warmer and the "
                        + "sea is %s m higher (mid, then low to high)",
                s.name(), s.mark(), s.assumption(), year, p.co2().mid(), p.temp().one(),
                p.slr().one());
    }

    public String summary() {
        return "climate projection: " + scenarios.size() + " scenarios over " + marks.size()
                + " marks to " + marks.get(marks.size() - 1) + "; every value a band; refused: "
                + String.join(", ", refused);
    }

    // ---------------------------------------------------------------------------------------
    private static Band band(JsonObject o) {
        return new Band(o.get("low").getAsDouble(), o.get("mid").getAsDouble(),
                o.get("high").getAsDouble());
    }
}
