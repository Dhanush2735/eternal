package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 33 (B25) and step 35 part 4 (B17, B2): <b>the works people built for water, by year</b>.
 *
 * <p>Wells, stepwells, cisterns, qanats, aqueducts, fountains, reservoirs, water towers, bath
 * houses and hammams — the water supply, bathing and sanitation of a settlement — and then the
 * works of the field: dams, canals, irrigation canals, weirs, polders, levees, watermills, oases,
 * acequias, terraces and ditches. 3,884 works with a map point and the year they were made,
 * fetched from Wikidata at a notability floor of three Wikipedias.
 *
 * <p>{@code history/water_works.json.gz} (built by {@code worldfill/build_water_works.py}). Each
 * row is one work: its name, its point, the narrowest Wikidata class it belongs to, the year it
 * was made, the year it ended where that is known, how famous it is, and where the year came from
 * ({@code inception}, {@code opening} or {@code gazetteer}).
 *
 * <p><b>The rule for a caller: a work stands here only in its own years.</b> A reservoir must not
 * be drawn before the dam that made it — that is step 35's rule and the pack obeys it, because
 * the reservoir's own year comes after the dam's. A work Wikidata could not date at all is not
 * here at all: the gazetteer window is allowed only for a work that is itself the place, so the
 * field's works take no window, and Dujiangyan, built in 256 BCE, is not given a Sui county seat's
 * window of 568 CE.
 *
 * <p>Sanitation is nearly absent, and that is recorded rather than hidden: Wikidata has articles
 * about sewers, not about individual ones.
 */
public final class WaterWorks {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/water_works.json.gz";

    /** The end year of a work that is still standing, or whose end is unknown. */
    public static final int STILL = Integer.MAX_VALUE;

    private static volatile WaterWorks instance;

    private final List<Work> works;
    private final Map<String, String> groups;
    private final String problem;

    /**
     * One work: what it is called, where it is, what kind of work it is, the years it stood, how
     * famous it is (Wikipedia sitelinks) and where the year came from.
     */
    public record Work(String name, double lat, double lon, String kind,
                       int from, int to, int sitelinks, String source) {

        public boolean isStandingIn(int year) {
            return year >= from && year <= (to == STILL ? Integer.MAX_VALUE : to);
        }

        /** True when the year came from Wikidata itself rather than from the gazetteer's window. */
        public boolean dated() {
            return !"gazetteer".equals(source);
        }

        public double distanceKm(double atLat, double atLon) {
            return WaterChanges.haversineKm(lat, lon, atLat, atLon);
        }
    }

    private WaterWorks(List<Work> works, Map<String, String> groups, String problem) {
        this.works = List.copyOf(works);
        this.groups = Map.copyOf(groups);
        this.problem = problem;
    }

    public static WaterWorks get() {
        WaterWorks w = instance;
        if (w == null) {
            synchronized (WaterWorks.class) {
                if (instance == null) {
                    instance = load();
                }
                w = instance;
            }
        }
        return w;
    }

    public boolean loaded() {
        return problem == null && !works.isEmpty();
    }

    public String problem() {
        return problem;
    }

    public int count() {
        return works.size();
    }

    public List<Work> works() {
        return works;
    }

    /** What a kind of work is for: supply, bathing, sanitation, conveyance, storage, and so on. */
    public String groupOf(String kind) {
        return groups.getOrDefault(kind, "");
    }

    public Map<String, String> groups() {
        return groups;
    }

    /**
     * The works standing within this many kilometres in this year, nearest first. This is the
     * question a settlement asks: what water works did this place have, in the year it is?
     */
    public List<Work> near(double lat, double lon, int year, double withinKm) {
        List<Work> out = new ArrayList<>();
        for (Work w : works) {
            if (w.isStandingIn(year) && w.distanceKm(lat, lon) <= withinKm) {
                out.add(w);
            }
        }
        out.sort(Comparator.comparingDouble((Work w) -> w.distanceKm(lat, lon))
                .thenComparing(Comparator.comparingInt(Work::sitelinks).reversed()));
        return List.copyOf(out);
    }

    /** The works of one kind standing near here in this year. */
    public List<Work> nearOfGroup(double lat, double lon, int year, double withinKm, String group) {
        List<Work> out = new ArrayList<>();
        for (Work w : near(lat, lon, year, withinKm)) {
            if (group.equals(groupOf(w.kind()))) {
                out.add(w);
            }
        }
        return List.copyOf(out);
    }

    /** Did this place have a supply of water — a well, a cistern, a qanat, an aqueduct — by now? */
    public boolean hadSupplyBy(double lat, double lon, int year, double withinKm) {
        for (Work w : near(lat, lon, year, withinKm)) {
            String g = groupOf(w.kind());
            if ("supply".equals(g) || "conveyance".equals(g) || "storage".equals(g)) {
                return true;
            }
        }
        return false;
    }

    /** One sentence for a player standing here in this year, or empty when nothing is known. */
    public String describe(double lat, double lon, int year, double withinKm) {
        List<Work> here = near(lat, lon, year, withinKm);
        if (here.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, here.size()); i++) {
            Work w = here.get(i);
            if (i > 0) {
                sb.append(i + 1 == Math.min(4, here.size()) ? ", and " : ", ");
            }
            sb.append(w.name()).append(" (").append(w.kind()).append(", ")
                    .append(Math.round(w.distanceKm(lat, lon))).append(" km)");
        }
        return sb.toString();
    }

    private static WaterWorks load() {
        List<Work> out = new ArrayList<>();
        Map<String, String> groups = new HashMap<>();
        try (InputStream raw = WaterWorks.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new WaterWorks(List.of(), Map.of(), "resource " + RESOURCE + " absent");
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(
                    new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (root.has("groups")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("groups").entrySet()) {
                    groups.put(e.getKey(), e.getValue().getAsString());
                }
            }
            for (JsonElement el : root.getAsJsonArray("works")) {
                JsonArray r = el.getAsJsonArray();
                // [name, lat*1000, lon*1000, kind, from, to, sitelinks, source]
                out.add(new Work(
                        r.get(0).getAsString(),
                        r.get(1).getAsDouble() / 1000.0d,
                        r.get(2).getAsDouble() / 1000.0d,
                        r.get(3).getAsString(),
                        r.get(4).getAsInt(),
                        r.get(5).isJsonNull() ? STILL : r.get(5).getAsInt(),
                        r.get(6).getAsInt(),
                        r.get(7).getAsString()));
            }
            if (out.isEmpty()) {
                return new WaterWorks(List.of(), groups, RESOURCE + " held no works");
            }
            return new WaterWorks(out, groups, null);
        } catch (Exception e) {
            return new WaterWorks(List.of(), groups, "resource " + RESOURCE + " failed: " + e.getMessage());
        }
    }
}
