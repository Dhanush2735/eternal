package com.eternalengine.vehicle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 46 part 6 (A13, the vehicle half): <b>which mod supplies which vehicle, and what stands in
 * when nothing is installed.</b>
 *
 * <p>A13's line in the plan is <i>"optional links; everything also works without them"</i>. That
 * imposes three duties and this class keeps all of them:
 *
 * <ol>
 *   <li><b>Never hand back nothing.</b> Every vehicle in {@link Vehicles}' register resolves to a
 *       mod, to a vanilla stand-in, or to an explicit statement of why there is neither.</li>
 *   <li><b>Never pretend a stand-in is the real thing.</b> A boat is not a ship; a minecart is not
 *       a cart. {@link Answer#substitute()} exists so a caller can say which it got.</li>
 *   <li><b>Never invent an id.</b> Every provider is marked {@code read} (the id came from the
 *       mod's own source), {@code content} (the ids come from JSON content packs, so no id is safe)
 *       or {@code vanilla}.</li>
 * </ol>
 *
 * <h2>The two config lists that are this project's own questions</h2>
 *
 * <p>AstikorCarts Redux ships a <b>{@code pull_animals}</b> list — which entities may draw a cart —
 * and Small Ships ships a <b>{@code dockyardBuildableShips}</b> list. Both are exactly the question
 * step 46 part 2 already answers (has this beast arrived here) and part 5 answers for a route. So
 * {@link #pullAnimals} and {@link #buildableShips} <i>report what those lists should hold here and
 * now</i> rather than editing them: <b>change the other mod through its own hooks, never by
 * patching its jar.</b>
 *
 * <h2>The Dockyard gate</h2>
 *
 * <p>Small Ships has <b>no crafting recipes at all</b> — ships are built at a Dockyard — so a year
 * gate cannot empty a result slot for a ship. It has to refuse the Dockyard, the same shape as step
 * 45's gun bench. {@link #dockyardOpens} answers it, and the answer comes from
 * {@link Vehicles#availableAt}, which comes from the ways grid and the working animals: <b>four
 * parts of this step in one refusal.</b>
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class VehicleMods {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/vehicle_mods.json.gz";

    private static volatile VehicleMods instance;

    /** A mod, or Minecraft itself, that can supply a vehicle. */
    public record Provider(String id, String name, String modid, String licence, String mark,
                           String gate, Map<String, String> entities, Map<String, String> items,
                           Map<String, String> kinds, Map<String, String> parts,
                           Map<String, String> config, String note, String source) {

        /** Whether the ids here were read from the mod's own source rather than guessed. */
        public boolean idsAreRead() {
            return "read".equals(mark) || "vanilla".equals(mark);
        }

        /** Whether this mod takes its ids from content packs, so no id can be named safely. */
        public boolean idsComeFromContent() {
            return "content".equals(mark);
        }
    }

    /**
     * What one vehicle resolves to.
     *
     * @param provider  the mod or vanilla
     * @param id        the registry id, or null when the provider's ids come from a content pack
     * @param substitute true when this is a stand-in and {@link #whyNot()} says why
     * @param whyNot    why this is only a stand-in, or why there is nothing at all
     */
    public record Answer(String vehicle, @Nullable String provider, @Nullable String id,
                         boolean substitute, @Nullable String whyNot) {

        public boolean found() {
            return provider != null;
        }

        public String line() {
            if (!found()) return vehicle + ": nothing - " + whyNot;
            return vehicle + ": " + provider + (id == null ? " (an id from a content pack)" : " " + id)
                    + (substitute ? "  [stand-in: " + whyNot + "]" : "");
        }
    }

    private final Map<String, Provider> providers;
    private final Map<String, List<Answer>> byVehicle;
    private final Map<String, String> nothing;
    private final String finding;
    private final String problem;

    private VehicleMods(Map<String, Provider> providers, Map<String, List<Answer>> byVehicle,
                        Map<String, String> nothing, String finding, String problem) {
        this.providers = providers;
        this.byVehicle = byVehicle;
        this.nothing = nothing;
        this.finding = finding;
        this.problem = problem;
    }

    public static VehicleMods get() {
        VehicleMods v = instance;
        if (v == null) {
            synchronized (VehicleMods.class) {
                v = instance;
                if (v == null) {
                    v = load();
                    instance = v;
                }
            }
        }
        return v;
    }

    public static VehicleMods reload() {
        synchronized (VehicleMods.class) {
            instance = load();
            return instance;
        }
    }

    private static VehicleMods load() {
        Map<String, Provider> providers = new LinkedHashMap<>();
        Map<String, List<Answer>> byVehicle = new LinkedHashMap<>();
        Map<String, String> nothing = new LinkedHashMap<>();
        try (InputStream raw = VehicleMods.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new VehicleMods(providers, byVehicle, nothing, null,
                        "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (JsonElement pe : root.getAsJsonArray("providers")) {
                JsonObject o = pe.getAsJsonObject();
                providers.put(str(o, "id"), new Provider(str(o, "id"), str(o, "name"),
                        str(o, "modid"), str(o, "licence"), str(o, "mark"), str(o, "gate"),
                        strings(o, "entities"), strings(o, "items"), strings(o, "kinds"),
                        strings(o, "parts"), strings(o, "config"), str(o, "note"),
                        str(o, "source")));
            }
            for (JsonElement ve : root.getAsJsonArray("vehicles")) {
                JsonObject o = ve.getAsJsonObject();
                String id = str(o, "id");
                List<Answer> chain = new ArrayList<>();
                for (JsonElement se : o.getAsJsonArray("suppliedBy")) {
                    JsonObject s = se.getAsJsonObject();
                    String mark = str(s, "mark");
                    String sid = str(s, "id");
                    boolean substitute = "vanilla".equals(mark);
                    chain.add(new Answer(id, str(s, "provider"),
                            sid.startsWith("(") ? null : sid, substitute,
                            substitute ? standInWhy(str(s, "provider")) : null));
                }
                byVehicle.put(id, chain);
                String nil = nullableStr(o, "nothing");
                if (nil != null) nothing.put(id, nil);
            }
            return new VehicleMods(providers, byVehicle, nothing,
                    root.has("finding") ? str(root, "finding") : null, null);
        } catch (Exception e) {
            return new VehicleMods(providers, byVehicle, nothing, null,
                    "resource " + RESOURCE + " failed: " + e);
        }
    }

    /** Why a vanilla entity is a stand-in and not the thing itself. */
    private static String standInWhy(String providerId) {
        return switch (providerId) {
            case "vanilla" -> "Minecraft has no such vehicle; this is the nearest entity it has";
            default -> "a stand-in from " + providerId;
        };
    }

    // --------------------------------------------------------------------------------------- asking

    public boolean hasData() {
        return problem == null && !byVehicle.isEmpty();
    }

    @Nullable
    public String problem() {
        return problem;
    }

    @Nullable
    public String finding() {
        return finding;
    }

    public List<Provider> providers() {
        return List.copyOf(providers.values());
    }

    @Nullable
    public Provider provider(String id) {
        return providers.get(id);
    }

    /**
     * The best answer for a vehicle, given which mods are actually installed.
     *
     * <p>Order: a mod whose ids were read, then a mod whose ids come from content packs, then a
     * vanilla stand-in, then the recorded reason there is nothing. An empty {@code installed} set
     * means vanilla only, which is the case A13 is written for.
     */
    public Answer resolve(String vehicleId, Set<String> installed) {
        List<Answer> chain = byVehicle.getOrDefault(vehicleId, List.of());
        Set<String> have = installed == null ? Set.of() : installed;
        Answer content = null;
        Answer vanilla = null;
        for (Answer a : chain) {
            if (a.provider() == null) continue;
            Provider p = providers.get(a.provider());
            if (p == null) continue;
            if (p.idsAreRead() && "minecraft".equals(p.modid())) {
                vanilla = a;
            } else if (p.idsAreRead() && have.contains(p.modid())) {
                return a;
            } else if (p.idsComeFromContent() && have.contains(p.modid())) {
                if (content == null) content = a;
            }
        }
        if (content != null) return content;
        if (vanilla != null) return vanilla;
        String why = nothing.get(vehicleId);
        return new Answer(vehicleId, null, null, false,
                why == null ? "no provider, and no statement of why" : why);
    }

    /** Every vehicle that resolves to something, for a command or a log. */
    public List<Answer> resolveAll(Set<String> installed) {
        List<Answer> out = new ArrayList<>();
        for (String id : byVehicle.keySet()) out.add(resolve(id, installed));
        return out;
    }

    /** The vehicles a given mod supplies, as {@code mapOf(vehicle -> id)}. */
    public Map<String, String> suppliedBy(String providerId) {
        Map<String, String> out = new LinkedHashMap<>();
        Provider p = providers.get(providerId);
        if (p == null) return out;
        for (Map.Entry<String, String> e : p.entities().entrySet()) out.put(e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : p.items().entrySet()) out.put(e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : p.kinds().entrySet()) out.put(e.getKey(), "(content pack)");
        return out;
    }

    /**
     * Which animals the AstikorCarts {@code pull_animals} list should hold <b>here and now</b>.
     *
     * <p>This does not edit the other mod's config. It is a report, through the same public
     * surface the mod exposes, of a question step 46 part 2 already answers — and it is the join
     * worth having: the cart mod knows how to hitch a beast to a cart, and this mod knows which
     * beasts were ever in this place.
     */
    public List<String> pullAnimals(String area, int year) {
        Set<String> out = new LinkedHashSet<>();
        for (WorkingAnimals.Animal a : WorkingAnimals.get().animals()) {
            // A BEAST, not a vehicle. D14's register carries a sled in its `gear` kind, and a sled
            // pulling a cart is a category error — it is itself the thing being pulled.
            if (!"beast".equals(a.kind())) continue;
            if (a.vanilla() == null) continue;      // nothing in Minecraft to name
            if (a.roles().contains("pull") && a.hereBy(area, year)) {
                out.add(a.vanilla());
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * The beasts that can pull a cart here and now <b>but have no entity in vanilla</b>.
     *
     * <p>The honest other half of {@link #pullAnimals}: the ox, the water buffalo and the reindeer
     * all pull, and Minecraft has none of them. A player with a mod that adds one should put these
     * in AstikorCarts' {@code pull_animals} list; a player without one cannot, and should know.
     */
    public List<String> pullBeastsWithNoEntity(String area, int year) {
        List<String> out = new ArrayList<>();
        for (WorkingAnimals.Animal a : WorkingAnimals.get().animals()) {
            if (!"beast".equals(a.kind())) continue;
            if (a.vanilla() != null) continue;
            if (a.roles().contains("pull") && a.hereBy(area, year)) out.add(a.id());
        }
        return out;
    }

    /**
     * Which ships the Small Ships {@code dockyardBuildableShips} list should hold here and now.
     *
     * <p>Small Ships 2.0.0 ships a Cog, a Brigg, a Galley, a Drakkar, a Dhow and a Caravel. Two of
     * those — the Dhow and the Caravel — are vehicles step 46 part 3 dates, so the Dockyard's list
     * is not a constant: the dhow reaches the Indian Ocean in AD 200 and the Atlantic in 1492.
     */
    public List<String> buildableShips(String area, int year, double lat, double lon) {
        List<String> out = new ArrayList<>();
        Vehicles V = Vehicles.get();
        for (String[] pair : new String[][]{{"cog", "sailing_ship"}, {"brigg", "sailing_ship"},
                {"drakkar", "sailing_ship"}, {"galley", "galley"}, {"dhow", "dhow"},
                {"caravel", "sailing_ship"}}) {
            Vehicles.Vehicle v = V.byId(pair[1]);
            if (v == null) continue;
            Vehicles.Verdict d = V.availableAt(v, area, year, lat, lon);
            if (d != null && d.here()) out.add(pair[0]);
        }
        return out;
    }

    /**
     * Whether Small Ships' Dockyard may be used here and now.
     *
     * <p><b>The refusal that carries four parts of this step.</b> A ship is available when the
     * sailing ship has reached this place (part 3), on water that is here (part 3's {@code needs}),
     * which is a place the sea or a canal reaches (step 34's ways grid). The Dockyard is the front
     * door — there are no crafting recipes — so this is where the year gate lives.
     *
     * @return null when it may open; otherwise the reason it may not, in words a player can read
     */
    @Nullable
    public String dockyardOpens(String area, int year, double lat, double lon) {
        Vehicles VE = Vehicles.get();
        Vehicles.Vehicle ship = VE.byId("sailing_ship");
        if (ship == null) return "the vehicle register is not loaded";
        Vehicles.Verdict d = VE.availableAt(ship, area, year, lat, lon);
        if (d == null) return "the vehicle register is not loaded";
        if (!d.here()) {
            return "the Dockyard does not open: " + d.why();
        }
        return null;
    }

    /** Whether a mod's own crafting front door is gated, and where. */
    public String gateOf(String providerId) {
        Provider p = providers.get(providerId);
        return p == null ? "unknown" : p.gate();
    }

    /** One line for {@code /ee check}. */
    public String summary() {
        if (!hasData()) return "the vehicle-mod resolver has no data: " + problem;
        int content = 0, read = 0;
        for (Provider p : providers.values()) {
            if (p.idsAreRead()) read++;
            else content++;
        }
        return providers.size() + " providers (" + read + " with ids read from source, "
                + content + " drawing ids from content packs); " + byVehicle.size()
                + " vehicles placed; " + nothing.size() + " with no provider and a stated reason";
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
        return String.format(Locale.ROOT, "VehicleMods[%d providers, %s]", providers.size(),
                problem == null ? RESOURCE : problem);
    }
}
