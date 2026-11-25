package com.eternalengine.civilization;

import java.util.Collection;

/**
 * "Who ruled here in this year?" — the answer, with its provenance stated.
 *
 * <h3>Why (2026-10-24, requirement 2, step R2-a3, driven by the S1 truth audit)</h3>
 * The S1 pilot asked the ruler register who ruled twelve places in twelve years. Eleven answers were
 * right when checked against sources. The twelfth was <b>Athens in 430 BC</b>, where the register said
 * <b>(nobody)</b> — while the mod's own civilization pack carries {@code athens}, capital 37.9838/23.7275,
 * standing −800 to −338. The register's source (Cliopatria) simply does not cover the classical Greek
 * poleis.
 *
 * <p>So the register's silence is not the same as "nobody ruled here", and code that treated the two as
 * one answer was wrong at a place the project has visited (R5 is 430 BC Athens). This layer keeps them
 * apart: it asks the register first, and only <b>when the register has no polity</b> does it ask the pack
 * — nearest capital within {@link #PACK_CAPITAL_KM} whose years include the year asked.
 *
 * <h3>What it never does</h3>
 * It never overrules the register. A polity the register names is the answer, even if a pack capital is
 * nearer. And it never invents: every answer says which layer it came from, so a caller that needs
 * certainty can refuse {@link Source#PACK_CAPITAL}. The capital rule is a <b>declared heuristic</b>, not a
 * border: a polity's capital being 40 km away does not prove it held that exact cell, and the answer says
 * so in its own type.
 */
public final class WhoRuled {

    /** How far from a civilization's capital the pack layer may answer. */
    public static final double PACK_CAPITAL_KM = 50.0d;

    /** One civilization as the pack states it: name, years, capital. No extents. */
    public record Cap(String id, String name, int start, int end, double lat, double lon) {
    }

    /**
     * A dominion or successor state and the year it became responsible for its own territory.
     *
     * <p>Why this table exists (R2-a4a, 2026-10-24): the 200-sample truth audit answered
     * <b>British Colonial Empire</b> for Quebec towns in 1884, 1901 and 1921. Canada had been a dominion
     * since 1867 — the register's own coverage of {@code Canada} simply starts later in those cells. The
     * table is small, sourced and hand-checked on purpose: it is a <b>correction of a known class</b>, not a
     * guess about borders.
     */
    public record Dominion(String successorName, int since, String source) {
    }

    /**
     * Dominions and successor states, each with the year it took over its own territory, and where that
     * year comes from. Names are matched against the register's <b>own later answer for the same cell</b>,
     * so a cell that never resolves to the successor is never touched.
     */
    private static final java.util.List<Dominion> DOMINIONS = java.util.List.of(
            new Dominion("Canada", 1867, "British North America Act, 1867"),
            new Dominion("Commonwealth of Australia", 1901, "Federation of Australia, 1901"),
            new Dominion("Australia", 1901, "Federation of Australia, 1901"),
            new Dominion("New Zealand", 1907, "Dominion status, 26 September 1907"),
            new Dominion("South Africa", 1910, "South Africa Act, 1910"),
            new Dominion("Union of South Africa", 1910, "South Africa Act, 1910"),
            new Dominion("Irish Free State", 1922, "Anglo-Irish Treaty, 1921; state from 6 December 1922"),
            new Dominion("Ireland", 1922, "Anglo-Irish Treaty, 1921; state from 6 December 1922"),
            new Dominion("Dominion of India", 1947, "Indian Independence Act, 1947"),
            new Dominion("India", 1947, "Indian Independence Act, 1947"),
            new Dominion("Dominion of Pakistan", 1947, "Indian Independence Act, 1947"),
            new Dominion("Pakistan", 1947, "Indian Independence Act, 1947"));

    /**
     * A polity that governed part of its overlord's territory in its own right, and the years it did so.
     *
     * <p>Why (R2-a5, 2026-10-24): a source check of eight places found two wrong answers, both naming a
     * suzerain where a state ruled - the Sultan for the Crimean Khan, the Emperor for the Duke of Legnica.
     * The register holds both vassals, but its search sometimes prefers the overlord. This table is short,
     * sourced and hand-checked on purpose: each row needed a source before it went in.
     */
    public record Vassal(String civId, String displayName, java.util.List<String> overlordNames,
            int since, int until, double minLat, double maxLat, double minLon, double maxLon,
            String source) {

        /**
         * Whether this vassal's <b>declared</b> territory box contains the point. A box is not a border: it
         * is the smallest rectangle that holds the territory a source describes, and it is used only when
         * the register's own coverage of the vassal is silent there.
         */
        public boolean inBox(double lat, double lon) {
            return !Double.isNaN(minLat) && lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }
    }

    /**
     * Vassals verified against a source, with the source. Only polities the register already holds belong
     * here - this table does not invent coverage, it decides priority between two recorded answers.
     */
    /**
     * The two names the register uses for the power above a Bohemian fief: the Empire, and the Bohemian
     * crown itself. Both are the overlord, never the ruler of the town - the Brzeg page is explicit that the
     * Piast dukes held the town "as fiefs of the Bohemian Crown within the Holy Roman Empire".
     */
    private static final java.util.List<String> OVERLORDS_OF_A_BOHEMIAN_FIEF =
            java.util.List.of("Holy Roman Empire", "Kingdom of Bohemia");

    private static final java.util.List<Vassal> VASSALS = java.util.List.of(
            // RETRACTED 2026-10-24, same day it was written, after a Wikipedia cross-check.
            //
            // A crimean_khanate row with the box 44.2-47.6 / 29.0-36.8 was added because Tavriiske (Kherson)
            // 1755 answered "Ottoman Empire" while the khanate, an Ottoman vassal, held the left bank. It
            // produced three corrections - and the cross-check showed two of them were wrong:
            //   * Odesa 1594: Khadjibey "came under DIRECT control of the Ottoman Empire after 1529 ... part
            //     of the Silistra (Ozi) Eyalet" (en.wikipedia.org/wiki/Odesa, /Yedisan). The Ottoman answer
            //     the register gave was RIGHT and my correction broke it.
            //   * Dubossary 1680: Podolia was an Ottoman eyalet 1672-1699, not khanate territory.
            // So the declared box is withdrawn. A box cannot tell an eyalet fortress from a horde's steppe,
            // and one verified place does not license a territory. Only rows with a source that names the
            // vassal FOR THAT PLACE belong below.
            //
            // The Duchy of Legnica row stays: its source is explicit that Lubin "remained part of the
            // Piast-ruled Duchy of Legnica until 1675" (en.wikipedia.org/wiki/Lubin), and the same holds for
            // Legnica itself. The box is deliberately narrow - the duchy's core around Legnica and Lubin -
            // and excludes Brzeg (50.86/17.47), which needs its own row with its own source.
            new Vassal("duchy_of_legnica", "Duchy of Legnica", OVERLORDS_OF_A_BOHEMIAN_FIEF, 1329, 1675,
                    50.7, 51.6, 15.9, 16.7,
                    "Duchy of Legnica (core): Bohemian fief from 1329, Piast-ruled until 1675; "
                            + "Lubin named as part of it until 1675"),
            // Added 2026-10-24 after reading en.wikipedia.org/wiki/Brzeg: "From the early 14th to late
            // 17th centuries, the town was ruled by the Piast dynasty as fiefs of the Bohemian Crown
            // within the Holy Roman Empire." The register has no Duchy of Brzeg at all, so the row names
            // it in its own right - the box is Brzeg and its immediate farmland, so it cannot swallow
            // Olawa (17.29E), Namyslow (17.72E) or Ziebice (17.04E), each a separate duchy.
            new Vassal("duchy_of_brzeg", "Duchy of Brzeg", OVERLORDS_OF_A_BOHEMIAN_FIEF, 1311, 1675,
                    50.75, 51.00, 17.35, 17.65,
                    "Duchy of Brzeg: Piast duchy from 1311, Bohemian fief within the Empire, until 1675"),
            // Added 2026-10-27 after AskProbe found Berlin 1821 answering "German Confederation" and
            // Krakow 1840 answering "Russian Empire". In both cases the register names the umbrella and
            // is silent on the state itself, which is the case VASSAL_DECLARED exists for: the specific
            // polity beats the general, and the box says where.
            //
            // Prussia: en.wikipedia.org/wiki/Kingdom_of_Prussia - "1701-1918", Capital "Berlin",
            // "Koenigsberg (1806-1809)", and the 1815 map is titled "Within the German Confederation".
            // So the row starts in 1815: Berlin in 1806-1814 is a separate question (French occupation,
            // and a register entry claiming Berlin for the Confederation of the Rhine, which never
            // included it) and is deliberately NOT papered over here. The box is Berlin and its
            // immediate surroundings, so it cannot reach Hamburg, Dresden or Stettin.
            new Vassal("kingdom_of_prussia", "Kingdom of Prussia",
                    java.util.List.of("German Confederation", "North German Confederation"), 1815, 1871,
                    52.35, 52.70, 13.15, 13.70,
                    "Kingdom of Prussia: capital Berlin; a member of the German Confederation from 1815 "
                            + "and of the North German Confederation from 1867"),
            // The Free City of Krakow: 1815-1846, "City-state and protectorate of Austria, Prussia, and
            // Russia", capital Cracow. The register records the Free City on only two cells that are not
            // Krakow itself, so Krakow answered "Russian Empire" for 1815-1829 and 1836-1847. The box is
            // the city and its district, not Galicia, so Warsaw and Lviv stay outside it.
            new Vassal("free_city_of_krakow", "Free City of Krakow",
                    java.util.List.of("Russian Empire", "Austrian Empire", "Kingdom of Poland"), 1815, 1846,
                    49.95, 50.15, 19.85, 20.10,
                    "Free City of Krakow: 1815-1846, a strictly neutral city-state under the protection "
                            + "of Austria, Prussia and Russia"));

    /**
     * Holes the register leaves, filled from a named source - and only ever where it is silent.
     *
     * <p>Clamping the Byzantine Empire to 1453 (see {@code RulerRegister.SPAN_WRONG}) removed a false ruler
     * from Istanbul but exposed a real hole underneath: the register's Ottoman coverage of that cell only
     * begins in 1466, so 1454-1465 answered "nobody" at the city the Ottomans had just taken. "Nobody" is
     * an honest report of a gap, but it is still a wrong answer to a player standing in Constantinople.
     *
     * <p>These rows are checked <b>after</b> the register and before the pack, and only when the register
     * has no answer, so a fill cannot displace recorded data. The box is the city wall, not the province.
     */
    private record Fill(String civId, String displayName, int start, int end,
                        double minLat, double maxLat, double minLon, double maxLon, String source) {}

    private static final java.util.List<Fill> FILLED_GAPS = java.util.List.of(
            // en.wikipedia.org/wiki/Constantinople - the fall of Constantinople to Mehmed II on 29 May
            // 1453; en.wikipedia.org/wiki/Byzantine_Empire - "it endured until the fall of Constantinople
            // to the Ottoman Empire in 1453". The register's own Ottoman span at this cell starts 1466.
            new Fill("ottoman_empire", "Ottoman Empire", 1453, 1465,
                    40.95, 41.10, 28.85, 29.10,
                    "Constantinople fell to the Ottoman Empire on 29 May 1453; the register's Ottoman "
                            + "coverage of this cell begins only in 1466"));

    /** The declared fill for a silent cell, or null. */
    private static Answer asFill(double lat, double lon, int year) {
        for (Fill f : FILLED_GAPS) {
            if (year >= f.start() && year <= f.end()
                    && lat >= f.minLat() && lat <= f.maxLat()
                    && lon >= f.minLon() && lon <= f.maxLon()) {
                return new Answer(f.civId(), f.displayName(), f.start(), f.end(), 0.0d, Source.FILLED);
            }
        }
        return null;
    }

    /** The last year any source in the register records. Beyond it, answers are projections. */
    public static final int RECORDED_UNTIL = 2026;

    /** The year the dominion layer looks up to see what the cell resolved to. */
    private static final int RESOLUTION_YEAR = 2020;

    /** Where an answer came from. */
    public enum Source {
        /**
         * The register is <b>silent</b> for this cell and year, and a named source fills the hole inside a
         * declared box. Used only where the register has no answer at all, so it can never overwrite one.
         */
        FILLED,
        /** The ruler register named a polity for this cell and year. Recorded. */
        REGISTER,
        /**
         * The register named a <b>suzerain</b>, and the same cell lies within a vassal's own <b>recorded</b>
         * coverage in a year the vassal held it. Recorded evidence: the specific polity beats the general.
         */
        VASSAL,
        /**
         * The register named a suzerain and its own coverage of the vassal is silent here, but the cell lies
         * inside a <b>declared</b> territory box for that vassal, printed with its source. A heuristic, and
         * the answer says so - a caller that needs certainty can refuse this source.
         */
        VASSAL_DECLARED,
        /**
         * The register named a <b>colonial</b> polity, but the same cell resolves to a dominion in the
         * register's own later answer, and the year is after that dominion took over. The colonial record is
         * corrected by the register's own successor, and the correction names the dominion's founding.
         */
        DOMINION,
        /** The register was silent; a civilization whose capital is near and whose years include the year. */
        PACK_CAPITAL,
        /**
         * Nothing recorded - and this is <b>not</b> the same as nobody ruling there.
         *
         * <p>Added 2026-10-24 after the undated-town audit: 68.5% of samples in the 20,356 undated towns
         * came back "nobody", and Geneva in 121 BC was one of them - a place the sources say Rome took
         * that year. Saying "nobody" there is a <b>claim</b>. The honest answer is "the source does not
         * cover this". Callers that know the spot was inhabited (a town stands there, now or then) must
         * ask for {@link #answerForSettlement} so silence is reported as {@code UNKNOWN}, not as emptiness.
         */
        UNKNOWN,
        /**
         * The year asked is beyond the last year any source records (the register stops at 2026), so the
         * answer is the last recorded state of the world <b>carried forward</b>. Nothing records 2400 or
         * 3000; this says what the world would look like if nothing changed, and says so.
         */
        PROJECTED,
        /** Nothing: no record, and this spot is recorded as empty (open water, ice, desert). */
        NONE
    }

    /** The answer, never null. {@code name} is null only when the source is {@link Source#NONE}. */
    public record Answer(String civId, String name, int start, int end, double distanceKm, Source source) {
        public boolean known() {
            return source != Source.NONE;
        }

        public String display() {
            if (source == Source.NONE) {
                return "(nobody)";
            }
            if (source == Source.UNKNOWN) {
                return "(not recorded - the source does not cover this place and year)";
            }
            if (source == Source.PROJECTED) {
                return name + " [as it stood in " + RECORDED_UNTIL + "] (carried forward - no source records "
                        + "this year; it is a projection, not a record)";
            }
            String years = start == 0 && end == 0 ? "" : " [" + start + "-" + end + "]";
            String how = switch (source) {
                case REGISTER -> "";
                case VASSAL -> " (the register named the overlord; this vassal governed here)";
                case VASSAL_DECLARED -> " (the register named the overlord and does not cover the vassal here;"
                        + " this is the vassal's declared territory, not a recorded one)";
                case DOMINION -> " (the register said a colonial power; the same cell is this state's own from "
                        + start + ", so the colonial record is corrected)";
                case FILLED -> " (the register is silent here; a named source fills the gap)";
                case PACK_CAPITAL -> " (from the pack, nearest capital " + Math.round(distanceKm) + " km)";
                case PROJECTED, UNKNOWN, NONE -> "";
            };
            return name + years + how;
        }
    }

    private WhoRuled() {
    }

    /**
     * Ask who ruled a spot that <b>was inhabited</b> - a town stands there, then or now.
     *
     * <p>The difference matters: {@link #answer} reports a silent register as {@link Source#NONE}, which
     * reads as "nobody ruled here". At a known settlement that is a false claim, so this entry point turns
     * silence into {@link Source#UNKNOWN}. Every caller that has a place - rather than a coordinate - should
     * use this one.
     */
    public static Answer answerForSettlement(Collection<Cap> caps, double lat, double lon, int year) {
        Answer a = answer(caps, lat, lon, year);
        return a.source() == Source.NONE
                ? new Answer(null, null, 0, 0, 0.0d, Source.UNKNOWN)
                : a;
    }

    /**
     * The register's overlord answer, replaced by a recorded vassal that governed the same cell that year.
     *
     * @return the vassal's answer, or null when the rule does not apply (the usual case)
     */
    private static Answer asVassal(RulerRegister register, String ruler, double lat, double lon, int year) {
        if (ruler == null) {
            return null;
        }
        for (Vassal v : VASSALS) {
            if (!v.overlordNames().contains(ruler) || year < v.since() || year > v.until()) {
                continue;
            }
            var near = register.rulerNear(v.civId(), lat, lon, year);
            if (near.isPresent()) {
                var n = near.get();
                return new Answer(n.civHint(), n.ruler(), n.start(), n.end(), n.distanceKm(), Source.VASSAL);
            }
            if (v.inBox(lat, lon)) {
                return new Answer(v.civId(), namedOr(register, v), v.since(), v.until(),
                        distanceToBoxCentre(v, lat, lon), Source.VASSAL_DECLARED);
            }
        }
        return null;
    }

    private static java.util.List<Cap> packCache;

    /**
     * The civilization pack's capitals, read from the mod's own resource and cached.
     *
     * <p>Added for the wiring (2026-10-24): until this existed, {@link #answer} could apply the vassal and
     * dominion layers only when a caller passed the pack in by hand - which my probes did and the game did
     * not. Reading the resource directly keeps the layer available headless, with no manager and no world.
     */
    public static java.util.List<Cap> packCapitals() {
        if (packCache != null) {
            return packCache;
        }
        java.util.List<Cap> out = new java.util.ArrayList<>();
        String path = "/data/eternalengine/eternal/civilizations/foundation_civilizations.json";
        try (java.io.InputStream in = WhoRuled.class.getResourceAsStream(path)) {
            if (in != null) {
                try (java.io.Reader reader = new java.io.InputStreamReader(in,
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    var root = com.google.gson.JsonParser.parseReader(reader);
                    var list = root.isJsonArray() ? root.getAsJsonArray()
                            : root.getAsJsonObject().getAsJsonArray("civilizations");
                    for (var el : list) {
                        if (!el.isJsonObject()) {
                            continue;
                        }
                        var row = el.getAsJsonObject();
                        if (!row.has("id") || !row.has("capitalLat") || !row.has("capitalLon")) {
                            continue;
                        }
                        out.add(new Cap(row.get("id").getAsString(),
                                row.has("name") ? row.get("name").getAsString() : row.get("id").getAsString(),
                                row.has("foundingYear") ? row.get("foundingYear").getAsInt() : -3000,
                                row.has("fallYear") ? row.get("fallYear").getAsInt() : 3000,
                                row.get("capitalLat").getAsDouble(), row.get("capitalLon").getAsDouble()));
                    }
                }
            }
        } catch (Exception e) {
            out.clear();
        }
        packCache = java.util.List.copyOf(out);
        return packCache;
    }

    /**
     * The layered answer in the register's own {@link RulerRegister.Hit} shape, for the 22 call sites.
     *
     * <p>This is the wiring (2026-10-24, R2-a8). Before it, every subsystem read
     * {@code RulerRegister.rulerAt} directly, so the vassal, dominion and pack layers existed only in probes:
     * the game still answered "Holy Roman Empire" at Legnica. A caller that swaps to this method gets the
     * same type and the same shape, and the layers with it.
     *
     * @return the answer, or empty when nothing is known - never "nobody" for a place that is inhabited, so
     *     callers that need to tell the two apart must use {@link #answerForSettlement}
     */
    public static java.util.Optional<RulerRegister.Hit> hit(double lat, double lon, int year) {
        Answer a = answer(packCapitals(), lat, lon, year);
        if (!a.known()) {
            return java.util.Optional.empty();
        }
        RulerRegister register = RulerRegister.get();
        if (a.source() == Source.REGISTER) {
            return register.rulerAt(lat, lon, year);
        }
        String wikidata = register.wikidataOf(a.civId());
        return java.util.Optional.of(new RulerRegister.Hit("(layered)", a.distanceKm(), a.name(),
                a.start(), a.end(), a.civId(), wikidata));
    }

    /**
     * The row's own name, unless the register knows the polity and spells it differently - the register's
     * spelling wins when it has one, so a declared row never renames recorded data.
     */
    private static String namedOr(RulerRegister register, Vassal v) {
        String fromRegister = nameOf(register, v.civId());
        return fromRegister.equals(v.civId()) ? v.displayName() : fromRegister;
    }

    /** Distance between two points, for callers that need to compare recorded centres. */
    public static double distanceKmStatic(double lat1, double lon1, double lat2, double lon2) {
        double dLat = (lat1 - lat2) * 110.57d;
        double dLon = (lon1 - lon2) * 111.32d * Math.cos(Math.toRadians((lat1 + lat2) / 2.0d));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    /** The source's own spelling of a polity, or the id when the register does not have it. */
    private static String nameOf(RulerRegister register, String civId) {
        for (var p : register.polities()) {
            if (p.civHint().equals(civId)) {
                return p.name();
            }
        }
        return civId;
    }

    /** Rough distance from a point to the centre of a declared territory box, for the answer's detail. */
    private static double distanceToBoxCentre(Vassal v, double lat, double lon) {
        double dLat = (v.minLat() + v.maxLat()) / 2.0d - lat;
        double dLon = (v.minLon() + v.maxLon()) / 2.0d - lon;
        double kmLat = dLat * 110.57d;
        double kmLon = dLon * 111.32d * Math.cos(Math.toRadians(lat));
        return Math.sqrt(kmLat * kmLat + kmLon * kmLon);
    }

    /**
     * The register's colonial answer, corrected by the register's own successor for the same cell.
     *
     * @return the corrected answer, or null when the rule does not apply (the usual case)
     */
    private static Answer asDominion(RulerRegister register, String ruler, double lat, double lon, int year) {
        if (ruler == null || !isColonial(ruler)) {
            return null;
        }
        var later = register.rulerAt(lat, lon, RESOLUTION_YEAR);
        if (later.isEmpty()) {
            return null;
        }
        String resolved = later.get().ruler();
        for (Dominion d : DOMINIONS) {
            if (d.successorName().equals(resolved) && year >= d.since()) {
                return new Answer(later.get().civHint(), d.successorName(), d.since(), later.get().end(),
                        later.get().distanceKm(), Source.DOMINION);
            }
        }
        return null;
    }

    /** Whether the register's polity is a colonial body whose successor should be asked for. */
    private static boolean isColonial(String ruler) {
        String lower = ruler.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("colonial") || lower.contains("colony");
    }

    /**
     * Answer who ruled a place in a year.
     *
     * @param caps the pack's civilizations, may be null or empty (then only the register answers)
     */
    public static Answer answer(Collection<Cap> caps, double lat, double lon, int year) {
        if (year > RECORDED_UNTIL) {
            // R2-8, the far end (2026 -> 3000 AD). No source records these years, so the only honest answer
            // is the last recorded state carried forward, labelled as a projection. Asking at a clamped year
            // would silently pretend a 2026 answer was a 2400 answer.
            Answer now = answer(caps, lat, lon, RECORDED_UNTIL);
            if (!now.known()) {
                return now;
            }
            return new Answer(now.civId(), now.name(), now.start(), RECORDED_UNTIL, now.distanceKm(),
                    Source.PROJECTED);
        }
        RulerRegister register = RulerRegister.get();
        var hit = register.rulerAt(lat, lon, year);
        if (hit.isPresent()) {
            var h = hit.get();
            Answer vassal = asVassal(register, h.ruler(), lat, lon, year);
            if (vassal != null) {
                return vassal;
            }
            Answer corrected = asDominion(register, h.ruler(), lat, lon, year);
            if (corrected != null) {
                return corrected;
            }
            return new Answer(h.civHint(), h.ruler(), h.start(), h.end(), h.distanceKm(), Source.REGISTER);
        }
        // The register is silent here. A named source may fill the hole; only then the pack.
        Answer fill = asFill(lat, lon, year);
        if (fill != null) {
            return fill;
        }
        Answer fromPack = nearestCapital(caps, lat, lon, year);
        return fromPack != null ? fromPack : new Answer(null, null, 0, 0, 0.0d, Source.NONE);
    }

    private static Answer nearestCapital(Collection<Cap> caps, double lat, double lon, int year) {
        if (caps == null || caps.isEmpty()) {
            return null;
        }
        Cap best = null;
        double bestKm = Double.MAX_VALUE;
        for (Cap cap : caps) {
            if (year < cap.start() || year > cap.end()) {
                continue;
            }
            double km = RulerRegister.distanceKm(lat, lon, cap.lat(), cap.lon());
            if (km <= PACK_CAPITAL_KM && km < bestKm) {
                best = cap;
                bestKm = km;
            }
        }
        if (best == null) {
            return null;
        }
        return new Answer(best.id(), best.name(), best.start(), best.end(), bestKm, Source.PACK_CAPITAL);
    }
}
