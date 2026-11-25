package com.eternalengine.vehicle;

import com.google.gson.JsonArray;
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
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 46 part 7 (D14, the second half): <b>herds, and the roads they made.</b>
 *
 * <p>Part 2 built the register of <i>working</i> animals and said plainly what it had left out:
 * <i>"Herd animals are out of scope... Sheep, goats, pigs and poultry are dated in the products
 * atlas and are not yet read as herds."</i> This is that register.
 *
 * <h2>The finding: the herd is the missing reason for the road</h2>
 *
 * <p>Part 5 found that a road is almost never a right of way, and never found <b>why the road is
 * there at all</b>. The answer is not the cart. A Roman road is 4 to 6 m wide, which is two carts.
 * A Spanish {@code cañada real} is <b>75 m wide</b>, because sheep spread out while they graze and
 * a flock has to walk and eat at the same time. The largest road-building institution in early
 * modern Europe was a <b>shepherds' guild</b>, and it moved 2.5 million animals a year on foot.
 *
 * <p><b>A road you can eat is a different object from a road you can drive on.</b> A drove road is
 * the one road built for animals rather than vehicles: part 3's {@code foot} medium, part 5's
 * {@code forced} shape, and part 2's beasts walking rather than pulling.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Herds {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/herds.json.gz";

    /** A road is 4 to 6 m wide in the classical world. A cart is 1.4 to 1.5 m. */
    public static final double VEHICLE_ROAD_M = 6.0;

    private static volatile Herds instance;

    /**
     * A herd: an animal, kept in numbers, in an area, from a year.
     *
     * @param sizeLow  typical head, low end
     * @param sizeHigh typical head, high end
     */
    public record Herd(String id, String animal, String area, int year, int sizeLow, int sizeHigh,
                       List<String> yields, String mark, String note, String source) {

        public boolean hereBy(int atYear) {
            return year <= atYear;
        }

        /** The mark a projection carries: a herd that was here is still here unless it ended. */
        public String markAt(int atYear) {
            if ("bison".equals(animal)) {
                // The one herd in the register that ENDED. A wild herd is not a kept herd, and
                // this is why the two cannot share a projection rule.
                return atYear > 1889 ? "gone" : mark;
            }
            return mark;
        }
    }

    /**
     * A drove: a route that exists because of a herd.
     *
     * @param widthM   how wide the corridor is, which is the whole point
     * @param shape    part 5's road-law shape, or null when no law of that kind applies
     */
    public record Drove(String id, String name, String area, double lat, double lon, int start,
                        @Nullable Integer end, double widthM, int lengthKm, int headPerYear,
                        int kmPerDay, @Nullable String shape, String mark, String note,
                        String source) {

        public boolean open(int year) {
            return year >= start && (end == null || year <= end);
        }

        /** Whether this drove is open here and now, and why not when it is shut. */
        @Nullable
        public String shutWhy(int year) {
            if (year < start) return "the " + name + " is not a road yet; it opens in " + start;
            if (end != null && year > end) return "the " + name + " closed in " + end;
            return null;
        }

        /** How many vehicle-road widths this corridor is. */
        public double roadWidths() {
            return widthM / VEHICLE_ROAD_M;
        }
    }

    private final Map<String, Herd> byId = new LinkedHashMap<>();
    private final Map<String, Drove> drovesById = new LinkedHashMap<>();
    private final List<String> refusals = new ArrayList<>();
    private final String finding;
    private final String problem;

    private Herds(@Nullable String problem) {
        this.finding = null;
        this.problem = problem;
    }

    private Herds(JsonObject root) {
        for (JsonElement e : root.getAsJsonArray("herds")) {
            JsonObject o = e.getAsJsonObject();
            byId.put(str(o, "id"), new Herd(str(o, "id"), str(o, "animal"), str(o, "area"),
                    o.get("year").getAsInt(), o.get("sizeLow").getAsInt(),
                    o.get("sizeHigh").getAsInt(), list(o, "yields"), str(o, "mark"),
                    str(o, "note"), str(o, "source")));
        }
        for (JsonElement e : root.getAsJsonArray("droves")) {
            JsonObject o = e.getAsJsonObject();
            drovesById.put(str(o, "id"), new Drove(str(o, "id"), str(o, "name"), str(o, "area"),
                    o.get("lat").getAsDouble(), o.get("lon").getAsDouble(),
                    o.get("start").getAsInt(),
                    o.get("end").isJsonNull() ? null : o.get("end").getAsInt(),
                    o.get("widthM").getAsDouble(), o.get("lengthKm").getAsInt(),
                    o.get("headPerYear").getAsInt(), o.get("kmPerDay").getAsInt(),
                    o.get("shape").isJsonNull() ? null : str(o, "shape"), str(o, "mark"),
                    str(o, "note"), str(o, "source")));
        }
        for (JsonElement e : root.getAsJsonArray("refusals")) {
            JsonObject o = e.getAsJsonObject();
            refusals.add(str(o, "claim") + ": " + str(o, "answer"));
        }
        this.finding = root.has("finding") ? str(root, "finding") : null;
        this.problem = null;
    }

    public static Herds get() {
        Herds h = instance;
        if (h == null) {
            synchronized (Herds.class) {
                h = instance;
                if (h == null) {
                    h = load();
                    instance = h;
                }
            }
        }
        return h;
    }

    private static Herds load() {
        try (InputStream raw = Herds.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) return new Herds("resource " + RESOURCE + " is absent");
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Herds(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (Exception e) {
            return new Herds("resource " + RESOURCE + " failed: " + e);
        }
    }

    // --------------------------------------------------------------------------------------- herds

    public boolean hasData() {
        return problem == null && !byId.isEmpty();
    }

    @Nullable
    public String problem() {
        return problem;
    }

    @Nullable
    public String finding() {
        return finding;
    }

    public List<Herd> herds() {
        return List.copyOf(byId.values());
    }

    public List<Drove> droves() {
        return List.copyOf(drovesById.values());
    }

    public List<String> refusals() {
        return List.copyOf(refusals);
    }

    @Nullable
    public Herd herd(String id) {
        return byId.get(id);
    }

    /** The herd of one animal in an area in a year, or null when there is none. */
    @Nullable
    public Herd herdOf(String animal, String area, int year) {
        for (Herd h : byId.values()) {
            if (!h.animal().equals(animal) || !h.area().equals(area)) continue;
            if (!h.hereBy(year)) continue;
            if ("gone".equals(h.markAt(year))) continue;
            return h;
        }
        return null;
    }

    /** Every herd kept in an area in a year. */
    public List<Herd> herdsAt(String area, int year) {
        List<Herd> out = new ArrayList<>();
        for (Herd h : byId.values()) {
            if (!h.area().equals(area) || !h.hereBy(year)) continue;
            if ("gone".equals(h.markAt(year))) continue;
            out.add(h);
        }
        return out;
    }

    /** What the herds of an area give in a year, as a sorted set: wool, milk, manure and so on. */
    public Set<String> yieldsAt(String area, int year) {
        Set<String> out = new TreeSet<>();
        for (Herd h : herdsAt(area, year)) out.addAll(h.yields());
        return out;
    }

    /** The animals kept in herds in an area in a year, sorted. */
    public Set<String> herdAnimalsAt(String area, int year) {
        Set<String> out = new TreeSet<>();
        for (Herd h : herdsAt(area, year)) out.add(h.animal());
        return out;
    }

    // -------------------------------------------------------------------------------------- droves

    @Nullable
    public Drove drove(String id) {
        return drovesById.get(id);
    }

    /**
     * The drove route that runs near a place in a year, or null.
     *
     * <p>"Near" is deliberately generous: a drove is a corridor hundreds of metres wide and
     * hundreds of kilometres long, so a point within a degree of its line is on it.
     */
    @Nullable
    public Drove droveAt(double lat, double lon, int year) {
        Drove best = null;
        double bestD = Double.MAX_VALUE;
        for (Drove d : drovesById.values()) {
            if (!d.open(year)) continue;
            double dist = Math.hypot(d.lat() - lat, d.lon() - lon);
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        return bestD <= 1.0 ? best : null;
    }

    /**
     * Whether a road this wide can be a drove road.
     *
     * <p>The question part 5 could not ask: <b>is this a road for animals or for vehicles?</b> A
     * drove needs an order of magnitude more width than a cart, because it must be grazed.
     */
    public boolean isDroveWidth(double widthM) {
        return widthM >= 10.0 * VEHICLE_ROAD_M;
    }

    /** The narrowest drove in the register, for a report. */
    public double narrowestDroveM() {
        double m = Double.MAX_VALUE;
        for (Drove d : drovesById.values()) m = Math.min(m, d.widthM());
        return m;
    }

    /**
     * The herd that explains a drove's width, as a line for a command.
     *
     * <p>This is the join the whole part is for: the widest route in the world is not a road.
     */
    public String widestLine() {
        Drove widest = null;
        for (Drove d : drovesById.values()) {
            if (widest == null || d.widthM() > widest.widthM()) widest = d;
        }
        if (widest == null) return "no droves";
        return widest.name() + ": " + (int) widest.widthM() + " m, "
                + String.format(java.util.Locale.ROOT, "%.1f", widest.roadWidths())
                + " times a vehicle road, " + widest.headPerYear() + " head a year";
    }

    // ------------------------------------------------------------------------------ the join to others

    /**
     * Whether a herd animal can pull a cart, asked of part 2's register.
     *
     * <p>The Andes is the case that matters: <b>six thousand years of herds and no draught
     * animal.</b> The llama carries, and the alpaca gives fibre, and neither pulls, which is why
     * part 3's cart reaches Peru in 1540 and part 6's pull_animals report returns nothing at Cusco.
     */
    public boolean canPull(String animal) {
        for (WorkingAnimals.Animal a : WorkingAnimals.get().animals()) {
            if (!a.id().equals(animal)) continue;
            return a.roles().contains("pull");
        }
        return false;
    }

    /** One line for {@code /ee check}. */
    public String summary() {
        if (!hasData()) return "the herd register has no data: " + problem;
        return byId.size() + " herds over " + herdAnimalCount() + " animals and " + areaCount()
                + " areas; " + drovesById.size() + " droves, the widest "
                + (int) widestDrove() + " m";
    }

    private long herdAnimalCount() {
        return byId.values().stream().map(Herd::animal).distinct().count();
    }

    private long areaCount() {
        return byId.values().stream().map(Herd::area).distinct().count();
    }

    private double widestDrove() {
        double m = 0;
        for (Drove d : drovesById.values()) m = Math.max(m, d.widthM());
        return m;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static List<String> list(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return out;
        JsonArray a = e.getAsJsonArray();
        for (JsonElement x : a) out.add(x.getAsString());
        return out;
    }

    @Override
    public String toString() {
        return "Herds[" + byId.size() + " herds, " + drovesById.size() + " droves, "
                + (problem == null ? RESOURCE : problem) + "]";
    }
}
