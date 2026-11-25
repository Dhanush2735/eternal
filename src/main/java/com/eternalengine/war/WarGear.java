package com.eternalengine.war;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Step 45 part 6 (A13): <b>the year gate and the role kit</b> — what a fighter carries, and what
 * may not yet exist.
 *
 * <p>This class <b>derives nothing that already exists</b>. Part A's armoury already knows when
 * each kind reaches each place ({@code arrivalOf}), when it leaves ({@code endOf}), where it never
 * existed at all ({@code neverIn}), and what retires what ({@code supersedes}). Part 5's resolver
 * already knows which item stands for which kind. This class is the composition of the two, plus
 * the one thing neither does alone: <b>judging an item by the year</b>.
 *
 * <p><b>It touches no Minecraft</b>, so a plain JDK can run every check in seconds. Turning a
 * string into an {@code ItemStack} is the wiring half, kept in {@code WarGearStacks}.
 *
 * <h2>The gate judges items, and the armoury only knows kinds</h2>
 *
 * <p>That gap is closed in three steps: a known mod item is the kind its row says; a vanilla item
 * is every kind it stands for; anything else is <b>guessed</b> from its name by part 5's token
 * table. So {@code musketmod:musket} is blocked in 1200 Paris, and so is
 * {@code mysterymod:khanda}-in-a-place-with-no-metal — the guess is what makes an unlisted mod
 * obey the rules instead of escaping them.
 *
 * <h2>A placeholder cannot be gated to one kind</h2>
 *
 * <p>{@code minecraft:iron_sword} is the vanilla stand-in for <b>eight</b> kinds at once (sword,
 * pike, lance, chariot, siege engine, cannon, elephant, tank). There is no honest way to say which
 * of them a player meant, so the gate takes the <b>most permissive reading</b>: the item is allowed
 * if <i>any</i> kind it stands for is available here. That is not laziness, it is what a
 * placeholder means — a stand-in is not a weapon, and refusing it would punish the player for the
 * register's own economy. The rules bite on <b>mod items</b>, which is exactly where they need to.
 *
 * <h2>An item that cannot be named is allowed</h2>
 *
 * <p>Something must be. Blocking every unknown id would break every mod in the pack that has
 * nothing to do with weapons — a plank from a building mod, a coin from a money mod. An unknown
 * item is therefore allowed <i>and reported</i>, which is the same trade-off as part 5's third
 * rule: a missing weapon is never an error.
 */
public final class WarGear {

    /** The armoury roles, in the order a fighter reaches for them. */
    public static final List<String> ROLES = List.of("melee", "missile", "mount", "protection",
            "siege");

    /**
     * What a named job carries, as armoury roles it will accept in order.
     *
     * <p>The fallback matters more than the preference. An archer in a place with no bow is not an
     * empty-handed archer — he is a spearman. A cavalryman with no stirrup is infantry.
     */
    private static final Map<String, List<String>> ROLE_WANTS = Map.of(
            "commander", List.of("melee", "protection", "mount"),
            "archer", List.of("missile", "melee"),
            "cavalry", List.of("mount", "melee"),
            "infantry", List.of("melee", "missile"),
            "guard", List.of("melee", "protection"),
            "marine", List.of("missile", "melee"),
            "bandit", List.of("melee", "missile"),
            "levy", List.of("melee", "missile"),
            "siege", List.of("siege", "missile", "melee"));

    /** The jobs, in the order they are listed. */
    public static List<String> jobs() {
        return List.copyOf(ROLE_WANTS.keySet());
    }

    /** The armoury role a job will accept, first choice first, or null for an unknown job. */
    @Nullable
    public static List<String> wantsFor(String job) {
        return ROLE_WANTS.get(job == null ? "" : job.toLowerCase(Locale.ROOT));
    }

    // --------------------------------------------------------------------------------------------- the gate

    /** The answer to "may this item exist here and now?" */
    public record Verdict(boolean allowed, @Nullable String kind, @Nullable String why,
                          @Nullable String item, boolean guessed, boolean vanilla) {

        /** A short line for a log or a chat line. */
        public String line() {
            return (allowed ? "allowed" : "REFUSED") + " " + item
                    + (kind == null ? "" : " (" + kind + ")")
                    + (why == null ? "" : " — " + why);
        }
    }

    /**
     * Whether this item may exist at this place in this year.
     *
     * @param itemId      an item id or tag; a tag is judged by the kinds it could name
     * @param loadedMods  the modids actually loaded, or null for none — decides whether the item
     *                    is a known mod item at all
     */
    public Verdict verdictFor(String itemId, String area, int year, double lat, double lon,
                              @Nullable Set<String> loadedMods) {
        if (itemId == null || itemId.isBlank()) {
            return new Verdict(true, null, "nothing to judge", itemId, false, false);
        }
        String id = itemId.trim();

        // 1. A known mod item: the kind its row says, and nothing else.
        for (WeaponItems.Entry e : WeaponItems.get().entries()) {
            if (id.equals(e.item()) || id.equals(e.tag())) {
                return judge(e.kind(), id, area, year, lat, lon, false, false);
            }
        }

        // 2. A vanilla item: every kind it stands for, most permissive reading.
        List<String> vanillaKinds = vanillaKinds(id);
        if (!vanillaKinds.isEmpty()) {
            return judgeAny(vanillaKinds, id, area, year, lat, lon, true);
        }

        // 3. Anything else: guess the kind from its name. This is what makes an UNLISTED mod obey
        //    the rules rather than slip past them.
        String guessed = WeaponItems.get().guessKind(id);
        if (guessed != null) {
            return judge(guessed, id, area, year, lat, lon, true, false);
        }

        // 4. Unnameable. Allowed, and reported - see the class javadoc.
        return new Verdict(true, null, "not a weapon this register can name, so left alone", id,
                false, false);
    }

    /** Judge one kind. */
    private Verdict judge(String kindId, String item, String area, int year, double lat,
                          double lon, boolean guessed, boolean vanilla) {
        Armoury.Kind k = Armoury.get().kind(kindId);
        if (k == null) {
            return new Verdict(true, kindId, "an unknown kind, so left alone", item, guessed,
                    vanilla);
        }
        String why = k.neverIn(area);
        if (why != null) {
            return new Verdict(false, kindId, "it " + firstClause(why) + " here", item, guessed,
                    vanilla);
        }
        int a = Armoury.get().arrivalOf(k, lat, lon, area);
        if (a == NEVER) {
            return new Verdict(false, kindId, "it never reaches this place", item, guessed,
                    vanilla);
        }
        if (a > year) {
            return new Verdict(false, kindId, "it does not reach here until " + label(a), item,
                    guessed, vanilla);
        }
        int e = Armoury.get().endOf(k, lat, lon, area);
        if (year > e) {
            return new Verdict(false, kindId, "it left the field by " + label(e), item, guessed,
                    vanilla);
        }
        return new Verdict(true, kindId, "it reaches here in " + label(a), item, guessed, vanilla);
    }

    /** Judge a stand-in item that names several kinds: allow it if ANY of them is available. */
    private Verdict judgeAny(List<String> kindIds, String item, String area, int year, double lat,
                             double lon, boolean vanilla) {
        String last = null;
        for (String kid : kindIds) {
            Verdict v = judge(kid, item, area, year, lat, lon, false, vanilla);
            if (v.allowed()) {
                return new Verdict(true, kid,
                        "a stand-in for " + kindIds.size() + " kinds, of which " + kid
                                + " is available here", item, false, vanilla);
            }
            last = v.why();
        }
        return new Verdict(false, kindIds.isEmpty() ? null : kindIds.get(0), last, item, false,
                vanilla);
    }

    /** The armoury's own marker for "this never reaches here", value and all. */
    public static final int NEVER = com.eternalengine.technology.TechnologyAtlas.NEVER;

    // --------------------------------------------------------------------------------------------- the kit

    /** One thing carried: the kind that was chosen, and the item that stands for it. */
    public record Slot(Armoury.Kind kind, WeaponItems.Resolution item) {
        public String describe() {
            return kind.id() + "=" + item.describe();
        }
    }

    /** What one fighter carries. */
    public record Kit(String job, Map<String, Slot> slots, List<String> notes) {

        public boolean empty() {
            return slots.isEmpty();
        }

        /** The item in one slot, or null. */
        @Nullable
        public WeaponItems.Resolution item(String armouryRole) {
            Slot s = slots.get(armouryRole);
            return s == null ? null : s.item();
        }

        /** The kind chosen for one slot, or null. */
        @Nullable
        public Armoury.Kind kind(String armouryRole) {
            Slot s = slots.get(armouryRole);
            return s == null ? null : s.kind();
        }

        /** The slots as one line, for a log. */
        public String line() {
            if (empty()) return job + ": nothing";
            StringBuilder b = new StringBuilder(job).append(": ");
            boolean first = true;
            for (Map.Entry<String, Slot> e : slots.entrySet()) {
                if (!first) b.append(", ");
                first = false;
                b.append(e.getKey()).append("=").append(e.getValue().describe());
            }
            return b.toString();
        }
    }

    /**
     * The kit for a named job at a place in a year.
     *
     * <p>The weapon is whatever has arrived and not been retired: the bow until the crossbow
     * reaches this place, then the matchlock, then the flintlock, then the rifle. That chain is
     * the armoury's own {@code supersedes} data, not a list written here.
     */
    public Kit forRole(String job, String area, int year, double lat, double lon,
                       @Nullable Set<String> loadedMods) {
        Map<String, Slot> slots = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        List<String> wants = wantsFor(job);
        if (wants == null) {
            notes.add("an unknown job, so nothing was chosen");
            return new Kit(job, slots, notes);
        }
        Armoury.Kit kit = Armoury.get().at(lat, lon, year);
        for (String want : wants) {
            Armoury.Kind best = bestOfRole(kit, want, lat, lon, area, year);
            if (best == null) continue;
            WeaponItems.Resolution r = WeaponItems.get().resolve(best.id(), area, year,
                    loadedMods);
            if (r == null) continue;
            slots.put(want, new Slot(best, r));
            if (wants.indexOf(want) > 0) {
                notes.add("no " + wants.get(0) + " here, so this fighter carries " + best.name()
                        + " instead");
            }
            break;
        }
        // Protection is not a fallback, it is a second slot: a fighter with a spear may still
        // have a shield, and a helmet is what makes a guard look like a guard.
        Armoury.Kind armour = bestOfRole(kit, "protection", lat, lon, area, year);
        if (armour != null) {
            WeaponItems.Resolution r = WeaponItems.get().resolve(armour.id(), area, year,
                    loadedMods);
            if (r != null) slots.put("protection", new Slot(armour, r));
        }
        if (slots.isEmpty()) {
            notes.add("the armoury reaches this place and year with nothing for this job");
        }
        return new Kit(job, slots, notes);
    }

    /**
     * The newest kind of this armoury role that has arrived and not been retired.
     *
     * <p>"Newest" is read off the armoury's own supersession chain, not from a list written here:
     * the kind that retires the most of its rivals wins. A tie is broken by the later arrival,
     * which is the same answer reached the other way round.
     */
    @Nullable
    public Armoury.Kind bestOfRole(Armoury.Kit kit, String armouryRole, double lat, double lon,
                                   String area, int year) {
        List<Armoury.Kind> cands = new ArrayList<>();
        for (Armoury.Kind k : kit.carried()) {
            if (armouryRole.equals(k.role())) cands.add(k);
        }
        if (cands.isEmpty()) return null;
        if (cands.size() == 1) return cands.get(0);
        // The count is over EVERY kind, not only the surviving candidates. Armoury.at() has
        // already moved the retired ones out of `here`, so a rifle in 1900 would score zero if
        // it were only compared against what is left - and the machine gun, which retires
        // nothing at all, would win the slot by default. Counting against the whole register is
        // what makes "the newest thing that has arrived" mean anything.
        int bestScore = -1;
        int bestYear = NEVER;   // an arrival of NEVER is the worst possible, not a big year
        Armoury.Kind best = null;
        for (Armoury.Kind k : cands) {
            int score = 0;
            for (Armoury.Kind other : Armoury.get().kinds()) {
                if (other != k && k.supersedes(other)) score++;
            }
            int a = Armoury.get().arrivalOf(k, lat, lon, area);
            if (score > bestScore || (score == bestScore && laterThan(a, bestYear))) {
                bestScore = score;
                bestYear = a;
                best = k;
            }
        }
        return best;
    }

    /** Every kind a vanilla item stands for. */
    public static List<String> vanillaKinds(String itemId) {
        List<String> out = new ArrayList<>();
        for (Armoury.Kind k : Armoury.get().kinds()) {
            if (itemId.equals(k.vanilla())) out.add(k.id());
        }
        return out;
    }

    // --------------------------------------------------------------------------------------------- /ee check

    /**
     * The two checks part 6 promises, as text.
     *
     * <p>1. <b>every role gets a weapon in the sample years</b> — a job with an empty kit is a
     * hole in the register, not a design choice.
     * <p>2. <b>no weapon appears before its first year</b> — the gate is asked about a musket in
     * 1200 and must say no.
     *
     * @return null when all is well, otherwise the problems, one per line
     */
    @Nullable
    public static String selfTest() {
        List<String> bad = new ArrayList<>();
        WeaponItems W = WeaponItems.get();
        if (!W.hasData()) {
            return "the item resolver has no data: " + W.problem();
        }
        List<int[]> sample = List.of(
                new int[]{-3000, 0}, new int[]{-1000, 1}, new int[]{-200, 2}, new int[]{800, 3},
                new int[]{1200, 4}, new int[]{1600, 5}, new int[]{1750, 6}, new int[]{1900, 7},
                new int[]{2000, 8});
        // Nine sample years over the whole span x the named places of the world-wide test set.
        Object[][] places = {
                {"Ur", 30.9625, 46.1031}, {"Mohenjo-daro", 27.3292, 68.1389},
                {"Anyang", 36.1269, 114.3152}, {"Athens", 37.9715, 23.7257},
                {"Rome", 41.8925, 12.4853}, {"Teotihuacan", 19.6925, -98.8438},
                {"Timbuktu", 16.7666, -3.0026}, {"Tenochtitlan", 19.4326, -99.1332},
                {"Cusco", -13.5320, -71.9675}, {"the Amazon", -3.4653, -62.2159},
                {"Great Zimbabwe", -20.2674, 30.9338}, {"Angkor", 13.4125, 103.8670},
                {"Greenland", 61.1500, -45.5167}, {"Cahokia", 38.6550, -90.0620},
                {"Sydney", -33.8688, 151.2093}, {"Vijayanagara", 15.3350, 76.4600},
                {"Edo", 35.6895, 139.6917}, {"Ulundi", -28.3344, 31.4178},
                {"Karabalgasun", 47.4289, 102.7064}, {"Isfahan", 32.6525, 51.6746},
        };
        int kits = 0, empty = 0;
        for (Object[] p : places) {
            double lat = ((Number) p[1]).doubleValue();
            double lon = ((Number) p[2]).doubleValue();
            for (int[] sy : sample) {
                int year = sy[0];
                String area = com.eternalengine.money.ProductAtlas.get().place(lat, lon, year)
                        .area();
                for (String job : jobs()) {
                    Kit k = get().forRole(job, area, year, lat, lon, Set.of());
                    kits++;
                    if (k.empty()) {
                        empty++;
                        if (bad.size() < 12) {
                            bad.add(p[0] + " " + label(year) + " " + area + ": " + job
                                    + " gets nothing");
                        }
                    }
                }
            }
        }
        if (empty > 0) {
            bad.add(0, empty + " of " + kits + " job-place-years get no weapon at all");
        }

        // 2. No weapon before its year. Ask about the gun the register dates most sharply.
        String[] guns = {"musketmod:musket", "musketmod:pistol", "tacz:ak47",
                "mysterymod:toradar"};
        for (String g : guns) {
            Verdict early = get().verdictFor(g, "EUR", 1200, 48.8566, 2.3522,
                    Set.of("musketmod", "tacz"));
            if (early.allowed()) {
                bad.add(g + " is allowed in 1200 Europe, and must not be");
            }
            // The year each gun IS allowed is the year the register gives its kind, not a round
            // number invented here: the rifle reaches Europe in 1866, so 1700 would be a lie.
            int lateYear = 1700;
            String kind = early.kind();
            if (kind != null) {
                com.eternalengine.money.ProductAtlas.Place pl =
                        com.eternalengine.money.ProductAtlas.get().place(48.8566, 2.3522, 1200);
                int arrive = Armoury.get().arrivalOf(Armoury.get().kind(kind), 48.8566, 2.3522,
                        pl.area());
                if (arrive != NEVER) lateYear = arrive + 50;
            }
            Verdict late = get().verdictFor(g, "EUR", lateYear, 48.8566, 2.3522,
                    Set.of("musketmod", "tacz"));
            if (!late.allowed()) {
                bad.add(g + " is refused in " + lateYear + " Europe, and should not be: "
                        + late.why());
            }
        }
        return bad.isEmpty() ? null : String.join("\n", bad);
    }

    /** One line for {@code /ee check}: the mods seen and how many kinds fall back. */
    public static String modLine(@Nullable Set<String> loadedMods) {
        WeaponItems W = WeaponItems.get();
        List<String> fb = W.fallingBackToVanilla(loadedMods);
        return W.modSummary(loadedMods) + "; " + fb.size() + " of "
                + Armoury.get().count() + " kinds fall back to vanilla";
    }

    private static final WarGear ONE = new WarGear();

    public static WarGear get() {
        return ONE;
    }

    /** Later than, where an arrival of {@link #NEVER} is the worst possible rather than the best. */
    private static boolean laterThan(int a, int b) {
        if (a == NEVER) return false;
        if (b == NEVER) return true;
        return a > b;
    }

    /** "1200" for AD, "300 BC" for BC — the mod's own convention. */
    public static String label(int year) {
        return year < 0 ? (-year) + " BC" : "AD " + year;
    }

    /** The first clause of a "never" note, which reads as a sentence on its own. */
    private static String firstClause(String why) {
        int dot = why.indexOf('.');
        String head = dot > 0 ? why.substring(0, dot) : why;
        return head.trim();
    }
}
