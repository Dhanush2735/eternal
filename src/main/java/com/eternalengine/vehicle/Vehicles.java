package com.eternalengine.vehicle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 46 part 3 (E10 A): <b>the vehicle register</b> — every way there has been of getting
 * somewhere, and <b>what each one runs on</b>.
 *
 * <p>The plan's E10 asks for *porters, palanquins, carts, chariots, horses, camels, elephants,
 * bicycles, rickshaws, trams, buses, cars, lorries; rafts, dhows, galleys, junks, sailing ships,
 * steamships; trains; balloons, airships, planes* — and this register carries all of them.
 *
 * <p><b>The dates are read, not rewritten.</b> Three registers in the mod already owned them: the
 * products atlas (per area), the technology atlas (spread curves for 258 countries) and the
 * armoury (the war chariot, with its never-map). This register names which one answers each
 * vehicle and lets that register answer it. A fourth copy would be a fourth thing to keep in step.
 *
 * <h2>What is new: the {@code needs} column</h2>
 *
 * <p>The other registers say <i>when a thing exists</i>. None of them says <b>what it runs on</b>,
 * and that question is the whole of E11. A train is not "the railway exists" — it is **the railway
 * exists here**. A canal boat needs a canal here. A cart needs a beast that has arrived here,
 * which is D14's question (part 2). So:
 *
 * <pre>
 *   availableAt(lorry, Paris, 1920)   -> the lorry has arrived AND there is a road
 *   availableAt(train, Kabul, 1900)   -> false, whatever the railway's own year says
 * </pre>
 *
 * <p>Step 34's {@code WaysGrid} already answers the road, rail and canal half for 124,240 squares;
 * this class is what joins a vehicle to it.
 *
 * <h2>A systematic artefact, recorded rather than patched around</h2>
 *
 * <p>The products atlas dates {@code rickshaws} and {@code wheelbarrows} at EUR −3000 — the exact
 * date of {@code carts} — and {@code oars} at −10000 in all 17 areas. Those are template copies
 * from step 44 part C, where a child inherited a parent's row unchanged. The same artefact put
 * reindeer in West Africa and gave donkeys the horse's date. Six vehicles here carry explicit
 * sourced dates instead, and every one is listed in the pack under {@code corrections}.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Vehicles {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/vehicles.json.gz";

    private static volatile Vehicles instance;

    /** What a vehicle moves over or through. */
    public static final List<String> MEDIUMS = List.of("foot", "beast", "wheel", "rail", "water",
            "air");

    /** What a vehicle needs that its own date does not tell you. */
    public static final List<String> NEEDS = List.of("none", "beast", "road", "rail", "tramline",
            "water", "canal", "air");

    /**
     * A way of getting somewhere.
     *
     * @param needs    what it runs on: none, beast, road, rail, tramline, water, canal, air
     * @param beast    the animal that draws it, or null — answered against D14, part 2
     * @param years    the year it reaches each area, or empty when the technology atlas answers
     * @param atlasId  the technology whose spread curve dates it, or null
     * @param end      the year it leaves the field in each area, when the source knows
     */
    public record Vehicle(String id, String name, String medium, List<String> needs,
                          @Nullable String beast, int people, int loadKg, int kmPerDay,
                          Map<String, Integer> years, Map<String, String> never,
                          Map<String, Integer> end, @Nullable String atlasId, String yearSource,
                          List<String> supersedes, @Nullable String vanilla, String mark,
                          String note, @Nullable String corrected) {

        /** The year it reaches an area, or null when this register does not date it by area. */
        @Nullable
        public Integer yearIn(String area) {
            return years.get(area);
        }

        /** Why it never reached an area, or null when it did. */
        @Nullable
        public String neverIn(String area) {
            return never.get(area);
        }

        /** The year it leaves the field in an area, or null when it never does. */
        @Nullable
        public Integer endIn(String area) {
            return end.get(area);
        }

        /** Whether it is a vehicle for people rather than for goods. */
        public boolean carriesPeople() {
            return people > 0;
        }

        /** Whether it is a vehicle for goods rather than for people. */
        public boolean carriesGoods() {
            return loadKg > 0 && people == 0 || loadKg > 500;
        }

        /** Whether it needs a particular surface or waterway. */
        public boolean needs(String what) {
            return needs.contains(what);
        }

        public String describe() {
            return name + " (" + kmPerDay + " km a day, " + loadKg + " kg, on "
                    + String.join("+", needs) + ")";
        }
    }

    /** Why a vehicle is or is not here, for a command or a log. */
    public record Verdict(Vehicle vehicle, boolean here, @Nullable String why) {

        public String line() {
            return vehicle.name() + ": " + (here ? "here" : "NOT here — " + why);
        }
    }

    private final List<Vehicle> vehicles;
    private final List<String> corrections;
    private final String finding;
    private final String problem;

    private Vehicles(List<Vehicle> vehicles, List<String> corrections, String finding,
                     String problem) {
        this.vehicles = vehicles;
        this.corrections = corrections;
        this.finding = finding;
        this.problem = problem;
    }

    public static Vehicles get() {
        Vehicles v = instance;
        if (v == null) {
            synchronized (Vehicles.class) {
                v = instance;
                if (v == null) {
                    v = load();
                    instance = v;
                }
            }
        }
        return v;
    }

    /** Forget the pack and read it again. */
    public static Vehicles reload() {
        synchronized (Vehicles.class) {
            instance = load();
            return instance;
        }
    }

    private static Vehicles load() {
        List<Vehicle> out = new ArrayList<>();
        List<String> corrections = new ArrayList<>();
        try (InputStream raw = Vehicles.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new Vehicles(List.of(), List.of(), null,
                        "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (JsonElement ve : root.getAsJsonArray("vehicles")) {
                JsonObject o = ve.getAsJsonObject();
                List<String> needs = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("needs")) needs.add(e.getAsString());
                List<String> sup = new ArrayList<>();
                if (o.has("supersedes")) {
                    for (JsonElement e : o.getAsJsonArray("supersedes")) sup.add(e.getAsString());
                }
                out.add(new Vehicle(str(o, "id"), str(o, "name"), str(o, "medium"), needs,
                        nullableStr(o, "beast"), o.get("people").getAsInt(),
                        o.get("loadKg").getAsInt(), o.get("kmPerDay").getAsInt(),
                        ints(o, "years"), strings(o, "never"), ints(o, "end"),
                        nullableStr(o, "atlasId"), str(o, "yearSource"), sup,
                        nullableStr(o, "vanilla"), str(o, "mark"), str(o, "note"),
                        nullableStr(o, "corrected")));
            }
            if (root.has("corrections")) {
                for (JsonElement e : root.getAsJsonArray("corrections")) {
                    corrections.add(e.getAsString());
                }
            }
            return new Vehicles(out, corrections,
                    root.has("finding") ? str(root, "finding") : null, null);
        } catch (Exception e) {
            return new Vehicles(List.of(), List.of(), null,
                    "resource " + RESOURCE + " failed: " + e);
        }
    }

    // -------------------------------------------------------------------------------------------------- asking

    public List<Vehicle> vehicles() {
        return List.copyOf(vehicles);
    }

    public boolean hasData() {
        return problem == null && !vehicles.isEmpty();
    }

    /** Null when the pack is healthy; otherwise why not. */
    @Nullable
    public String problem() {
        return problem;
    }

    /** The corrected dates, so a reader can see which rows do not come from a register. */
    public List<String> corrections() {
        return List.copyOf(corrections);
    }

    /** The systematic finding about step 44 part C, as one line. */
    @Nullable
    public String finding() {
        return finding;
    }

    /** One vehicle by id, or null. */
    @Nullable
    public Vehicle byId(String id) {
        for (Vehicle v : vehicles) {
            if (v.id().equals(id)) return v;
        }
        return null;
    }

    /** Every vehicle of one medium: all the rail vehicles, all the ships, and so on. */
    public List<Vehicle> onMedium(String medium) {
        List<Vehicle> out = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.medium().equals(medium)) out.add(v);
        }
        return out;
    }

    /**
     * Whether this vehicle has reached this area by this year, by its own dates alone.
     *
     * <p>This is the <i>arrival</i> question. It does not ask whether the road, the rail or the
     * beast is here — that is {@link #availableAt}, and it is the difference that matters.
     */
    public boolean arrivedBy(Vehicle v, String area, int year) {
        if (v.neverIn(area) != null) return false;
        Integer a = v.yearIn(area);
        if (a == null) return false;                 // dated by the technology atlas instead
        if (a > year) return false;
        Integer e = v.endIn(area);
        return e == null || year <= e;
    }

    /**
     * The year this vehicle reaches a point on the map, by whichever register dates it.
     *
     * <p>Most vehicles carry a flat year per area. The chariot does not — the technology atlas
     * holds a spread curve per country for it, and only that atlas can say when it reaches a
     * point. {@link com.eternalengine.technology.TechnologyAtlas#NEVER} when it never does.
     */
    public int arrivalAt(Vehicle v, String area, double lat, double lon) {
        Integer a = v.yearIn(area);
        if (a != null) return a;
        if (v.atlasId() != null) {
            var atlas = com.eternalengine.technology.TechnologyAtlas.get();
            if (atlas.loaded()) {
                return atlas.arrivalAt(atlas.tech(v.atlasId()), lat, lon);
            }
        }
        return com.eternalengine.technology.TechnologyAtlas.NEVER;
    }

    /**
     * Whether this vehicle can actually be used here and now — arrived <i>and</i> with whatever
     * it runs on actually present.
     *
     * <p><b>This is the join E11 is built on.</b> A train that has arrived is not the same as a
     * train on a railway; step 34's {@code WaysGrid} knows whether the rail reaches this point,
     * and D14 knows whether the beast has.
     */
    public Verdict availableAt(Vehicle v, String area, int year, double lat, double lon) {
        if (v == null) return null;
        if (v.neverIn(area) != null) {
            return new Verdict(v, false, "it never existed here: " + v.neverIn(area));
        }
        // When did it get here? Either its own flat year for the area, or the technology atlas's
        // spread curve for the point. The END year applies either way: a vehicle that has left the
        // field has left it, whatever dated its arrival — the chariot left South Asia by AD 550
        // and is not lurking there in the twenty-first century.
        int a = arrivalAt(v, area, lat, lon);
        if (a == com.eternalengine.technology.TechnologyAtlas.NEVER) {
            return new Verdict(v, false, "it never reached this place");
        }
        if (a > year) return new Verdict(v, false, "it does not reach here until " + a);
        Integer e = v.endIn(area);
        if (e != null && year > e) {
            return new Verdict(v, false, "it left the field by " + e);
        }
        // The needs. This is the part no other register answers.
        for (String need : v.needs()) {
            if ("none".equals(need)) continue;
            if ("air".equals(need)) continue;             // the air is everywhere
            String why = needMissing(need, v, area, year, lat, lon);
            if (why != null) return new Verdict(v, false, why);
        }
        return new Verdict(v, true, null);
    }

    /** Why a need is not met here, or null when it is met. */
    @Nullable
    private String needMissing(String need, Vehicle v, String area, int year, double lat,
                               double lon) {
        switch (need) {
            case "beast" -> {
                // D14's question: has the animal that draws it arrived here yet?
                WorkingAnimals.Animal beast = v.beast() == null ? null
                        : WorkingAnimals.get().byId(v.beast());
                if (beast == null) return "no animal that can draw it is in the register";
                if (!beast.hereBy(area, year)) {
                    Integer by = beast.yearIn(area);
                    return "it needs " + beast.name() + ", which "
                            + (by == null ? "never reached this place" : "does not reach here "
                            + "until " + by);
                }
                return null;
            }
            case "road" -> {
                var ways = com.eternalengine.civilization.WaysGrid.get();
                if (!ways.loaded() || !ways.roadBy(lat, lon, year)) {
                    return "there is no dated road here yet";
                }
                return null;
            }
            case "rail" -> {
                var ways = com.eternalengine.civilization.WaysGrid.get();
                if (!ways.loaded() || !ways.railBy(lat, lon, year)) {
                    return "the railway has not reached here yet";
                }
                return null;
            }
            case "tramline" -> {
                var ways = com.eternalengine.civilization.WaysGrid.get();
                if (!ways.loaded() || !ways.railBy(lat, lon, year)) {
                    return "there is no tram line here yet";
                }
                return null;
            }
            case "canal" -> {
                var ways = com.eternalengine.civilization.WaysGrid.get();
                if (!ways.loaded() || !ways.canalBy(lat, lon, year)) {
                    return "there is no canal here yet";
                }
                return null;
            }
            case "water" -> {
                // Water is mostly a place question rather than a date one, but it is not ONLY the
                // caller's business: a caravan router asking for the fastest vehicle at Paris must
                // not be handed a sailing ship. The same test the technology atlas uses for the
                // steamship (a port town, the sea within three 11 km squares), or a dated canal.
                var ways = com.eternalengine.civilization.WaysGrid.get();
                if (ways.loaded() && ways.canalBy(lat, lon, year)) return null;
                if (!com.eternalengine.civilization.CountryGrid.get().nearSea(lat, lon, 3)) {
                    return "there is no open water here to float it on";
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * The fastest vehicle that can actually be used here now.
     *
     * @param forPeople true to want a people-carrier, false for goods
     */
    @Nullable
    public Vehicle fastestAt(String area, int year, double lat, double lon, boolean forPeople) {
        Vehicle best = null;
        for (Vehicle v : vehicles) {
            if (forPeople && !v.carriesPeople()) continue;
            if (!forPeople && !v.carriesGoods()) continue;
            Verdict d = availableAt(v, area, year, lat, lon);
            if (d == null || !d.here()) continue;
            if (best == null || v.kmPerDay() > best.kmPerDay()) best = v;
        }
        return best;
    }

    /** Every vehicle that can actually be used here now, fastest first. */
    public List<Vehicle> usableAt(String area, int year, double lat, double lon) {
        List<Vehicle> out = new ArrayList<>();
        for (Vehicle v : vehicles) {
            Verdict d = availableAt(v, area, year, lat, lon);
            if (d != null && d.here()) out.add(v);
        }
        out.sort(Comparator.comparingInt(Vehicle::kmPerDay).reversed());
        return out;
    }

    /** One line for {@code /ee check}. */
    public String summary() {
        if (!hasData()) return "the vehicle register has no data: " + problem;
        long byAtlas = vehicles.stream().filter(v -> v.atlasId() != null).count();
        long corrected = vehicles.stream().filter(v -> v.corrected() != null).count();
        return vehicles.size() + " vehicles over " + MEDIUMS.size() + " mediums; " + byAtlas
                + " dated by the technology atlas; " + corrected + " dates corrected";
    }

    private static Map<String, Integer> ints(JsonObject o, String key) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!o.has(key)) return out;
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
            out.put(e.getKey(), e.getValue().getAsInt());
        }
        return out;
    }

    private static Map<String, String> strings(JsonObject o, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!o.has(key)) return out;
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
            out.put(e.getKey(), e.getValue().getAsString());
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Nullable
    private static String nullableStr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Vehicles[%d vehicles, %s]", vehicles.size(),
                problem == null ? RESOURCE : problem);
    }
}
