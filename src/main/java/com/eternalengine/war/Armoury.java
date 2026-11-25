package com.eternalengine.war;

import com.eternalengine.money.ProductAtlas;
import com.eternalengine.technology.TechnologyAtlas;
import com.google.gson.JsonArray;
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
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 45 part 2 (E9 A): the armoury — what weapons and armour exist at a place in a year.
 *
 * <p>The survey for this step found the raw material nearly all present and **every join missing**.
 * Three registers carry pieces of it, and this class is where they meet:
 *
 * <ul>
 *   <li>the **technology atlas** — 13 weapon technologies across 258 countries, with `needs` and
 *       `replaces` chains, spread-modelled. The precise source, and it is preferred.</li>
 *   <li>the **product atlas** — 33 products in a "weapons and armour" family, dated in 17 areas,
 *       built by step 44 part C. The coarse source.</li>
 *   <li>**this register** — hand-written per-area years for the melee family, because part C
 *       derived those from *materials* rather than from weapons.</li>
 * </ul>
 *
 * <h2>Why the melee family is written by hand</h2>
 *
 * <p>Part C gave each product a date by deriving it from a parent, and for weapons the parents are
 * materials: **spears from bows, clubs from timber, daggers from copper, shields from leather,
 * slings from hemp, pikes from iron**. That is the right heuristic for a product and the wrong one
 * for a weapon. It produced a spear arriving in Australia in 1800 — inherited from the bow, which
 * is right about the bow and wrong about the spear — and a dagger arriving in North America in
 * 1700, when flint knives are ten thousand years older. So those eleven rows carry their dates
 * here, and the atlases are read for the rest.
 *
 * <h2>Two debts this step pays</h2>
 *
 * <p><b>"Australia had no bows; the weapons step decides who may have them."</b> Step 44 part C
 * wrote that note into the `bows` product and left it for this step. The answer is recorded in this
 * register: the bow was never locally invented on the Australian continent and never spread to it,
 * though the Torres Strait — the only part of Australia that had it — used it, and though
 * Australians traded with bow-using peoples to the north. Australian woods test as sub-optimal for
 * self-bows, and a spear from a woomera carries four times the kinetic energy of an arrow from a
 * compound bow, so the bow had nothing to offer. The diffusion even ran backwards: the spearthrower
 * spread to Torres Strait Islanders who had previously used the bow.
 *
 * <p><b>Three armour rows that disagree.</b> "Metal armour" says 2000 BC in West Asia; mail says
 * 116 BC there; plate says 1400 and four areas. Copper plate is genuinely old in West Asia, but a
 * Sumerian of 2000 BC was not wearing a mail shirt. The three are separate rows here.
 *
 * <p>Every kind answers to one of **D10's twelve arms** where a formation names it, so step 40's
 * sixteen dated formations and four casualty bands join to this step without a translation layer.
 */
public final class Armoury {

    public static final String RESOURCE = "/data/eternalengine/eternal/war/armory.json.gz";

    private static volatile Armoury instance;

    /**
     * A kind of weapon or armour.
     *
     * @param arm        D10's word for it, or null when no formation names it
     * @param role       melee, missile, mount, siege or protection
     * @param tech       the technology atlas row, or null
     * @param product    the product atlas row, or null
     * @param years      per-area first years, when written by hand; null means read the atlases
     * @param never      areas where it never existed, each with the reason why
     * @param supersedes kinds this one retires
     * @param vanilla    the item to use when no weapon mod is installed
     */
    public record Kind(String id, @Nullable String arm, String name, String role, String use,
                       @Nullable String tech, @Nullable String product, String material,
                       @Nullable Map<String, Integer> years, Map<String, Integer> end,
                       @Nullable String endTech, Map<String, String> never,
                       List<String> supersedes, String vanilla, String mark, String note,
                       String source) {

        /** The reason this kind never existed in an area, or null when it did. */
        @Nullable
        public String neverIn(String area) {
            return never.get(area);
        }

        /** True when this kind retires the other. */
        public boolean supersedes(Kind other) {
            return supersedes.contains(other.id());
        }
    }

    /**
     * What exists at a place in a year.
     *
     * @param area  the way-of-life area, which is the coarsest thing this register reasons over
     * @param here  the kinds that have arrived, nearest-retired-first
     * @param goneIn the kinds that have arrived and been superseded, with what replaced them
     * @param never the kinds that never existed here, with the reason
     */
    public record Kit(String area, List<Kind> here, Map<Kind, List<Kind>> goneIn,
                      Map<Kind, String> never) {

        public boolean empty() {
            return here.isEmpty();
        }

        /** The kinds a fighter would carry: whatever has arrived and is not retired. */
        public List<Kind> carried() {
            return here;
        }
    }

    private final List<Kind> kinds;
    private final Map<String, Kind> byId;
    private final List<String> areas;
    private final List<String> d10Arms;
    private final String problem;

    private Armoury(List<Kind> kinds, List<String> areas, List<String> d10Arms, String problem) {
        this.kinds = kinds;
        this.areas = areas;
        this.d10Arms = d10Arms;
        Map<String, Kind> m = new LinkedHashMap<>();
        for (Kind k : kinds) {
            m.put(k.id(), k);
        }
        this.byId = m;
        this.problem = problem;
    }

    // -------------------------------------------------------------------------------------------------- reading

    public static Armoury get() {
        Armoury a = instance;
        if (a == null) {
            synchronized (Armoury.class) {
                a = instance;
                if (a == null) {
                    a = load();
                    instance = a;
                }
            }
        }
        return a;
    }

    /** Forget the pack and read it again. */
    public static Armoury reload() {
        synchronized (Armoury.class) {
            instance = load();
            return instance;
        }
    }

    private static Armoury load() {
        List<Kind> kinds = new ArrayList<>();
        List<String> areas = new ArrayList<>();
        List<String> d10Arms = new ArrayList<>();
        try (InputStream raw = Armoury.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new Armoury(List.of(), List.of(), List.of(),
                        "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (JsonElement e : root.getAsJsonArray("areas")) {
                areas.add(e.getAsString());
            }
            for (JsonElement e : root.getAsJsonArray("d10Arms")) {
                d10Arms.add(e.getAsString());
            }
            for (JsonElement re : root.getAsJsonArray("rows")) {
                JsonObject o = re.getAsJsonObject();
                Map<String, Integer> years = null;
                if (!o.get("years").isJsonNull()) {
                    years = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> e
                            : o.getAsJsonObject("years").entrySet()) {
                        // an area with no year here is one recorded as never; `never` carries why
                        if (!e.getValue().isJsonNull()) {
                            years.put(e.getKey(), e.getValue().getAsInt());
                        }
                    }
                }
                Map<String, String> never = new LinkedHashMap<>();
                if (o.has("never") && !o.get("never").isJsonNull()) {
                    for (Map.Entry<String, JsonElement> e
                            : o.getAsJsonObject("never").entrySet()) {
                        never.put(e.getKey(), e.getValue().getAsString());
                    }
                }
                List<String> sup = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("supersedes")) {
                    sup.add(e.getAsString());
                }
                Map<String, Integer> end = new LinkedHashMap<>();
                if (o.has("end") && !o.get("end").isJsonNull()) {
                    for (Map.Entry<String, JsonElement> e
                            : o.getAsJsonObject("end").entrySet()) {
                        if (!e.getValue().isJsonNull()) {
                            end.put(e.getKey(), e.getValue().getAsInt());
                        }
                    }
                }
                String endTech = o.has("endTech") && !o.get("endTech").isJsonNull()
                        ? o.get("endTech").getAsString() : null;
                kinds.add(new Kind(o.get("id").getAsString(),
                        o.get("arm").isJsonNull() ? null : o.get("arm").getAsString(),
                        o.get("name").getAsString(), o.get("role").getAsString(),
                        o.get("use").getAsString(),
                        o.get("tech").isJsonNull() ? null : o.get("tech").getAsString(),
                        o.get("product").isJsonNull() ? null : o.get("product").getAsString(),
                        o.get("material").getAsString(), years, end, endTech, never, sup,
                        o.get("vanilla").getAsString(), o.get("mark").getAsString(),
                        o.get("note").getAsString(), o.get("source").getAsString()));
            }
            return new Armoury(kinds, areas, d10Arms, null);
        } catch (Exception e) {
            return new Armoury(List.of(), List.of(), List.of(),
                    "read of " + RESOURCE + " failed: " + e);
        }
    }

    /** Why the pack cannot be read, or null when it read cleanly. */
    @Nullable
    public String problem() {
        return problem;
    }

    public boolean loaded() {
        return problem == null && !kinds.isEmpty();
    }

    public int count() {
        return kinds.size();
    }

    public List<Kind> kinds() {
        return List.copyOf(kinds);
    }

    /** The kind with this id, or null. */
    @Nullable
    public Kind kind(String id) {
        return byId.get(id);
    }

    /** D10's twelve arms, so a caller can check the join holds. */
    public List<String> d10Arms() {
        return List.copyOf(d10Arms);
    }

    public String summary() {
        long hand = kinds.stream().filter(k -> k.years() != null).count();
        long absent = kinds.stream().mapToLong(k -> k.never().size()).sum();
        return kinds.size() + " kinds (" + hand + " hand-dated, " + (kinds.size() - hand)
                + " from the atlases, " + absent + " recorded absences)"
                + (loaded() ? "" : " (" + problem + ")");
    }

    // -------------------------------------------------------------------------------------------------- the answer

    /**
     * The year a kind reaches this point, or {@link TechnologyAtlas#NEVER}.
     *
     * <p>Three sources, in order of how much they know.
     *
     * <ol>
     *   <li><b>Hand-written per-area years</b>, when the row carries them. These exist precisely
     *       because the product atlas derived the melee weapons from materials and got them wrong,
     *       so they outrank everything.</li>
     *   <li><b>The technology atlas</b>, for the thirteen kinds that have a tech row: 258
     *       countries, an arrival year each, and the time it took to spread from where it began
     *       there.</li>
     *   <li><b>The product atlas</b>, area-granular, as the last resort.</li>
     * </ol>
     *
     * <p>A recorded absence outranks all three.
     */
    public int arrivalOf(Kind k, double lat, double lon, String area) {
        if (k.never().containsKey(area)) {
            return TechnologyAtlas.NEVER;
        }
        if (k.years() != null) {
            Integer y = k.years().get(area);
            return y == null ? TechnologyAtlas.NEVER : y;
        }
        if (k.tech() != null) {
            TechnologyAtlas.Tech t = TechnologyAtlas.get().tech(k.tech());
            if (t != null) {
                int a = TechnologyAtlas.get().arrivalAt(t, lat, lon);
                if (a != TechnologyAtlas.NEVER) {
                    return a;
                }
            }
        }
        if (k.product() != null) {
            ProductAtlas.Product p = ProductAtlas.get().product(k.product());
            if (p != null) {
                int a = p.firstIn(area);
                if (a != ProductAtlas.NEVER) {
                    return a;
                }
            }
        }
        return TechnologyAtlas.NEVER;
    }

    /**
     * The last year this kind is carried here, or {@link TechnologyAtlas#NEVER} when it never
     * leaves.
     *
     * <p>Two ways a weapon goes. Some left on a date of their own - the war chariot, the war
     * elephant - and those carry a per-area `end`. Most simply became obsolete when something
     * better arrived, and those name the **technology that ended them**: the rifle ended the
     * bow, the sword, the lance and the shield in every army that could get one, the flintlock
     * ended the pike and mail, the cannon ended the trebuchet. Reading the end from the
     * technology atlas means it happens at a different time in every country, which is the
     * honest answer - a bow was still a weapon in the highlands long after it had left the
     * parade ground.
     */
    public int endOf(Kind k, double lat, double lon, String area) {
        Integer fixed = k.end().get(area);
        if (fixed != null) {
            return fixed;
        }
        if (k.endTech() != null) {
            TechnologyAtlas.Tech t = TechnologyAtlas.get().tech(k.endTech());
            if (t != null) {
                // give it a generation: the old weapon is still in the armoury after the new one
                // arrives, and a man of sixty has not unlearned the bow
                int a = TechnologyAtlas.get().arrivalAt(t, lat, lon);
                if (a != TechnologyAtlas.NEVER) {
                    return a + 40;
                }
            }
        }
        return TechnologyAtlas.NEVER;
    }

    /**
     * What exists at a point in a year.
     *
     * <p>A kind that has arrived but been superseded is listed separately rather than dropped: an
     * army in 1700 could still field a matchlock, and saying it could not would be wrong. The
     * `replaces` chain the technology atlas carries is what decides.
     */
    public Kit at(double lat, double lon, int year) {
        String area = ProductAtlas.get().place(lat, lon, year).area();
        List<Kind> here = new ArrayList<>();
        Map<Kind, List<Kind>> goneIn = new LinkedHashMap<>();
        Map<Kind, String> never = new LinkedHashMap<>();

        for (Kind k : kinds) {
            String why = k.neverIn(area);
            if (why != null) {
                never.put(k, why);
                continue;
            }
            int a = arrivalOf(k, lat, lon, area);
            if (a == TechnologyAtlas.NEVER || a > year) {
                continue;
            }
            // a war chariot did not vanish the day it stopped being made; it left the field, and
            // a register with no end year will put an elephant in the mud of Flanders in 1917
            if (year > endOf(k, lat, lon, area)) {
                continue;
            }
            List<Kind> by = new ArrayList<>();
            for (Kind other : kinds) {
                if (other == k || other.never().containsKey(area)) {
                    continue;
                }
                if (other.supersedes(k)) {
                    int oa = arrivalOf(other, lat, lon, area);
                    if (oa != TechnologyAtlas.NEVER && oa <= year) {
                        by.add(other);
                    }
                }
            }
            if (by.isEmpty()) {
                here.add(k);
            } else {
                goneIn.put(k, by);
            }
        }
        here.sort(Comparator.comparing(Kind::role).thenComparing(Kind::id));
        return new Kit(area, here, goneIn, never);
    }

    /**
     * The kit as a sentence.
     *
     * <p>Named in the order a person would put them on: what protects you, what you hold, what
     * you throw. The absences come last and are stated as findings — a player standing in
     * Australia in 1700 is told there was no bow, and why, which is more interesting than a
     * shorter list would have been.
     */
    public static String sentence(Kit kit) {
        if (kit == null || kit.empty()) {
            return "nothing in the armoury reaches this place and year";
        }
        List<String> parts = new ArrayList<>();
        for (String role : new String[]{"protection", "melee", "missile", "mount", "siege"}) {
            List<String> names = new ArrayList<>();
            for (Kind k : kit.here()) {
                if (role.equals(k.role())) {
                    names.add(k.name());
                }
            }
            if (!names.isEmpty()) {
                parts.add(andList(names));
            }
        }
        StringBuilder b = new StringBuilder("here they carry " + String.join("; ", parts));
        if (!kit.goneIn().isEmpty()) {
            StringBuilder g = new StringBuilder();
            for (Map.Entry<Kind, List<Kind>> e : kit.goneIn().entrySet()) {
                if (g.length() > 0) {
                    g.append("; ");
                }
                List<String> ns = new ArrayList<>();
                for (Kind o : e.getValue()) {
                    ns.add(o.name());
                }
                g.append(e.getKey().name()).append(", though ").append(andList(ns))
                        .append(oneOf(ns) ? " has" : " have").append(" supplanted it");
            }
            b.append(". Still to be had: ").append(g);
        }
        return b.toString();
    }

    /** True when the list is one thing, so the verb agrees. */
    private static boolean oneOf(List<String> ns) {
        return ns.size() == 1;
    }

    private static String andList(List<String> ns) {
        if (ns.size() == 1) {
            return ns.get(0);
        }
        return String.join(", ", ns.subList(0, ns.size() - 1)) + " and "
                + ns.get(ns.size() - 1);
    }

    /** The areas this register reasons over. */
    public List<String> areas() {
        return List.copyOf(areas);
    }

    /** Every kind recorded as never having existed in an area. */
    public Set<String> absentAreas() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (Kind k : kinds) {
            out.addAll(k.never().keySet());
        }
        return out;
    }

    /** A phrase for a role, for a listing command. */
    public static String roleWord(String role) {
        return switch (role == null ? "" : role.toLowerCase(Locale.ROOT)) {
            case "melee" -> "held in the hand";
            case "missile" -> "thrown or shot";
            case "mount" -> "borne by a horse or an elephant";
            case "siege" -> "thrown at a wall";
            case "protection" -> "worn";
            default -> role;
        };
    }
}
