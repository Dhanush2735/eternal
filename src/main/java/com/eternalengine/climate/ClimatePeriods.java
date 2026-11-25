package com.eternalengine.climate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Step 35 (B4 climate): <b>the climate of every era, read out of history instead of made up</b>.
 *
 * <p>Before this step the whole of the mod's climate was two events typed into
 * {@link ClimateEngine#registerDefaults()} — the Little Ice Age and the Year Without a Summer —
 * and nothing at all for the four thousand three hundred years before them. A Roman farmer in
 * 100 BC, a Maya maize grower in 900, a Chinese peasant in the 1630s: all three farmed in a
 * climate the mod did not know existed.
 *
 * <p>{@code history/climate_periods.json.gz} (built by {@code worldfill/build_climate_periods.py}
 * from the hand-written table in {@code worldfill/climate_periods_table.py}) carries eighty-three
 * periods: the warm and cold ages, the solar minima, the volcanic winters, the megadroughts, the
 * failed monsoons and the famines, from the end of the African humid period to the drought of
 * 2022.
 *
 * <p><b>Every row has a centre and a radius, and that is the point.</b> A 2019 synthesis of more
 * than ninety proxy records found that the Roman Warm Period, the Medieval Warm Period and the
 * Little Ice Age were regional phenomena, not globally coherent episodes: they did not begin and
 * end together, and in some centuries the Pacific ran the other way from the Atlantic. The old
 * code applied the Little Ice Age to the whole world at once; this register does not. Only the
 * volcanic winters are marked as everywhere (radius 0), and those really are hemispheric within a
 * year or two. A civ in the Andes in 1650 therefore feels the Andean Little Ice Age and not
 * Europe's, which is what the evidence says.
 *
 * <p>Temperatures are degrees Celsius against the twentieth-century mean over the region; rainfall
 * and harvest are percentages. They stack additively (temperature, rainfall) or multiplicatively
 * (harvest), so the Maunder Minimum inside the Little Ice Age is colder than either alone.
 *
 * <p>Loaded once, lazily, and kept. Nothing here touches Minecraft.
 */
public final class ClimatePeriods {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/climate_periods.json.gz";

    /** Floor and ceiling on the harvest multiplier, as {@code ClimateEngine} already used. */
    public static final double YIELD_MIN = 0.05d;
    public static final double YIELD_MAX = 3.0d;

    private static volatile ClimatePeriods instance;

    private final List<Period> periods;
    private final String problem;
    private final String source;

    private ClimatePeriods(List<Period> periods, String source, String problem) {
        this.periods = List.copyOf(periods);
        this.source = source;
        this.problem = problem;
    }

    /** One era's weather, somewhere, with the dates and the numbers the record gives it. */
    public record Period(String id, String name, String type, int startYear, int endYear,
                         double lat, double lon, double radiusKm,
                         double tempChange, double precipChange, double yieldImpact,
                         double severity, String note, boolean estimated) {

        public boolean isActiveInYear(int year) {
            return year >= startYear && year <= endYear;
        }

        /** True when the period reaches this point. Radius 0 means it reached everywhere. */
        public boolean covers(double atLat, double atLon) {
            return radiusKm <= 0.0d || distanceKm(lat, lon, atLat, atLon) <= radiusKm;
        }

        public String span() {
            return startYear == endYear ? yearLabel(startYear)
                    : yearLabel(startYear) + " to " + yearLabel(endYear);
        }

        public static String yearLabel(int year) {
            return year < 0 ? (-year) + " BC" : "AD " + year;
        }

        static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
            double r = 6371.0d;
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
            return r * 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        }
    }

    public static ClimatePeriods get() {
        ClimatePeriods p = instance;
        if (p == null) {
            synchronized (ClimatePeriods.class) {
                if (instance == null) {
                    instance = load();
                }
                p = instance;
            }
        }
        return p;
    }

    public boolean loaded() {
        return problem == null && !periods.isEmpty();
    }

    /** Why the table could not be read, or null when it could. */
    public String problem() {
        return problem;
    }

    public String source() {
        return source;
    }

    public int count() {
        return periods.size();
    }

    public List<Period> periods() {
        return periods;
    }

    /**
     * The periods that were running in this year and that reached this point. Sorted worst first,
     * so a caller that wants one answer can take the first.
     */
    public List<Period> active(double lat, double lon, int year) {
        List<Period> out = new ArrayList<>();
        for (Period p : periods) {
            if (p.isActiveInYear(year) && p.covers(lat, lon)) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble(Period::severity).reversed()
                .thenComparing(Period::startYear));
        return List.copyOf(out);
    }

    /**
     * The periods that were running in this year and reached everywhere. This is the honest answer
     * for a caller with no location: the volcanic winters, and nothing regional, because a regional
     * period may not have reached a place whose position is unknown.
     */
    public List<Period> activeEverywhere(int year) {
        List<Period> out = new ArrayList<>();
        for (Period p : periods) {
            if (p.radiusKm() <= 0.0d && p.isActiveInYear(year)) {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }

    /** Degrees Celsius of warming (positive) or cooling (negative) at this point in this year. */
    public double temperatureModifier(double lat, double lon, int year) {
        double sum = 0.0d;
        for (Period p : active(lat, lon, year)) {
            sum += p.tempChange();
        }
        return sum;
    }

    public double temperatureModifierEverywhere(int year) {
        double sum = 0.0d;
        for (Period p : activeEverywhere(year)) {
            sum += p.tempChange();
        }
        return sum;
    }

    /** Percent change in rainfall at this point in this year. */
    public double precipModifier(double lat, double lon, int year) {
        double sum = 0.0d;
        for (Period p : active(lat, lon, year)) {
            sum += p.precipChange();
        }
        return sum;
    }

    public double precipModifierEverywhere(int year) {
        double sum = 0.0d;
        for (Period p : activeEverywhere(year)) {
            sum += p.precipChange();
        }
        return sum;
    }

    /**
     * The harvest multiplier at this point in this year: 1.0 is a normal year, 0.5 is half a crop.
     * Periods stack multiplicatively, so a drought inside a cold century costs more than either
     * alone, and the result is clamped to {@code [0.05, 3.0]} as it always was.
     */
    public double yieldModifier(double lat, double lon, int year) {
        double m = 1.0d;
        for (Period p : active(lat, lon, year)) {
            m *= Math.max(0.0d, 1.0d + p.yieldImpact() / 100.0d);
        }
        return clamp(m);
    }

    public double yieldModifierEverywhere(int year) {
        double m = 1.0d;
        for (Period p : activeEverywhere(year)) {
            m *= Math.max(0.0d, 1.0d + p.yieldImpact() / 100.0d);
        }
        return clamp(m);
    }

    /** True when the period with this id was running at this point in this year. */
    public boolean isActive(String periodId, double lat, double lon, int year) {
        if (periodId == null) {
            return false;
        }
        for (Period p : active(lat, lon, year)) {
            if (periodId.equals(p.id())) {
                return true;
            }
        }
        return false;
    }

    /** The period with this id, or null when there is none. */
    public Period byId(String periodId) {
        if (periodId == null) {
            return null;
        }
        for (Period p : periods) {
            if (periodId.equals(p.id())) {
                return p;
            }
        }
        return null;
    }

    /** Every period of one kind, worst first. Kinds: WARMING, COOLING, VOLCANIC, DROUGHT, PLUVIAL,
     *  MONSOON, SOLAR. */
    public List<Period> ofKind(String kind) {
        List<Period> out = new ArrayList<>();
        for (Period p : periods) {
            if (p.type().equalsIgnoreCase(kind)) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble(Period::severity).reversed()
                .thenComparing(Period::startYear));
        return List.copyOf(out);
    }

    /** The ten worst periods in the whole table, for a summary that cannot print all of them. */
    public List<Period> worst(int limit) {
        List<Period> out = new ArrayList<>(periods);
        out.sort(Comparator.comparingDouble(Period::severity).reversed()
                .thenComparing(Period::startYear));
        return List.copyOf(out.subList(0, Math.min(limit, out.size())));
    }

    /** The ids of everything running at this point in this year, worst first, comma-joined. */
    public String activeIds(double lat, double lon, int year) {
        StringBuilder sb = new StringBuilder();
        for (Period p : active(lat, lon, year)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p.id());
        }
        return sb.toString();
    }

    /**
     * One sentence for a player standing here in this year: the worst thing the sky was doing, and
     * what it did. Empty when history has nothing to say about this place and year.
     */
    public String describe(double lat, double lon, int year) {
        List<Period> here = active(lat, lon, year);
        if (here.isEmpty()) {
            return "";
        }
        Period worst = here.get(0);
        return worst.name() + " (" + worst.span() + "): " + worst.note();
    }

    private static double clamp(double v) {
        return Math.max(YIELD_MIN, Math.min(YIELD_MAX, v));
    }

    private static ClimatePeriods load() {
        List<Period> out = new ArrayList<>();
        try (InputStream raw = ClimatePeriods.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new ClimatePeriods(List.of(), "", "resource " + RESOURCE + " absent");
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(
                    new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonArray rows = root.has("periods") ? root.getAsJsonArray("periods") : new JsonArray();
            for (JsonElement el : rows) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("id") || !o.has("startYear")) {
                    continue;
                }
                out.add(new Period(
                        str(o, "id"),
                        str(o, "name"),
                        str(o, "type"),
                        o.get("startYear").getAsInt(),
                        o.get("endYear").getAsInt(),
                        dbl(o, "lat"),
                        dbl(o, "lon"),
                        dbl(o, "radiusKm"),
                        dbl(o, "tempChange"),
                        dbl(o, "precipChange"),
                        dbl(o, "yieldImpact"),
                        dbl(o, "severity"),
                        str(o, "note"),
                        "estimated".equals(str(o, "certainty"))));
            }
            String src = root.has("source") ? root.get("source").getAsString() : RESOURCE;
            if (out.isEmpty()) {
                return new ClimatePeriods(List.of(), src, RESOURCE + " held no periods");
            }
            return new ClimatePeriods(out, src, null);
        } catch (Exception e) {
            return new ClimatePeriods(List.of(), "",
                    "resource " + RESOURCE + " failed: " + e.getMessage());
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static double dbl(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : 0.0d;
    }
}
