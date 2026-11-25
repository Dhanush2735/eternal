package com.eternalengine.civilization;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Names and dates for every polity the ruler register can answer with — <b>without</b> giving it a
 * civilization record.
 *
 * <h3>Why this is a separate layer (2026-10-24, requirement 2, step R2-d2)</h3>
 * {@link CivSynthesis} deliberately refuses to register the world record's ~1,500 polities as
 * civilizations, and it is right to: every engine (population, agriculture, economy, migration,
 * technology) iterates every registered civilization, so 1,542 more would cost the frame rate for a
 * display string.
 *
 * <p>But the consequence was user-visible. Of the 1,567 polities the register can name, only 24 have
 * a record, so the code that reads a person's civilization fell through and said things like
 * <i>"no civilization called ahmadnagar_sultanate"</i> — for a sultanate the register knows by name
 * and dates to the year. The person was real, the state was real, and the game called it nothing.
 *
 * <h3>What this class is</h3>
 * A <b>lookup</b>, not a simulation. It reads the register's own polity list and recorded extents and
 * answers three questions — what is this polity called, in which years did it stand, and what should
 * a line of text say about it. Nothing here ticks, iterates per frame, or registers anything.
 *
 * <h3>What it does not claim</h3>
 * A named polity is not a simulated one: it has no treasury, no population of its own and no engine
 * behind it. The display says so where it matters, and {@link #hasRecord} is how a caller asks.
 */
public final class PolityFacts {

    private PolityFacts() {
    }

    /*
     * RulerRegister.polities() is not cheap: it walks all 9,322 timelines and slugifies every span to
     * build its list. Calling it once per polity turned a check that should take a second into one that
     * took over three minutes and timed out the whole /ee check all (measured 2026-10-24). So the two
     * tables are built once per register instance and read from memory after that.
     */
    private static volatile RulerRegister builtFor;
    private static volatile Map<String, String> NAMES = Map.of();
    private static volatile Map<String, int[]> SPANS = Map.of();
    private static volatile Map<String, double[]> CENTRES = Map.of();

    private static void ensureTables(RulerRegister register) {
        if (register == builtFor) {
            return;
        }
        synchronized (PolityFacts.class) {
            if (register == builtFor) {
                return;
            }
            Map<String, String> names = new LinkedHashMap<>();
            Map<String, int[]> spans = new LinkedHashMap<>();
            Map<String, double[]> centres = new LinkedHashMap<>();
            for (RulerRegister.Polity polity : register.polities()) {
                names.putIfAbsent(polity.civHint(), polity.name());
            }
            for (String id : names.keySet()) {
                register.extentOf(id).ifPresent(extent -> {
                    spans.put(id, new int[] {extent.start(), extent.end()});
                    centres.put(id, new double[] {extent.lat(), extent.lon()});
                });
            }
            SPANS = Map.copyOf(spans);
            CENTRES = Map.copyOf(centres);
            NAMES = Map.copyOf(names);
            builtFor = register;
        }
    }

    /**
     * The register's own name for a polity id, or {@code null} when it does not name one.
     *
     * <p>The name is the source's, not a title-cased id: {@code ahmadnagar_sultanate} reads
     * "Ahmadnagar Sultanate" because that is what Cliopatria calls it.
     */
    public static String name(String civId) {
        if (civId == null || civId.isBlank()) {
            return null;
        }
        try {
            RulerRegister register = RulerRegister.get();
            ensureTables(register);
            String fromPrimary = NAMES.get(RulerRegister.civHintFor(civId));
            // Step R2-a2 (2026-10-24): eighteen polities the primary source does not hold are carried
            // by the second register instead of being nameless.
            return fromPrimary != null ? fromPrimary : PolityGazetteer.name(civId);
        } catch (RuntimeException e) {
            return null;    // a display layer never takes the caller down with it
        }
    }

    /** The years a polity stood, as the register records them, or {@code null}. */
    public static int[] span(String civId) {
        if (civId == null || civId.isBlank()) {
            return null;
        }
        try {
            RulerRegister register = RulerRegister.get();
            ensureTables(register);
            int[] fromPrimary = SPANS.get(RulerRegister.civHintFor(civId));
            return fromPrimary != null ? fromPrimary : PolityGazetteer.span(civId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The centre of the cells a polity ruled, as {@code {lat, lon}}, or {@code null}. */
    public static double[] centre(String civId) {
        if (civId == null || civId.isBlank()) {
            return null;
        }
        try {
            RulerRegister register = RulerRegister.get();
            ensureTables(register);
            double[] fromPrimary = CENTRES.get(RulerRegister.civHintFor(civId));
            return fromPrimary != null ? fromPrimary : PolityGazetteer.centre(civId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The pack's own name for this id, or {@code null} when it has no record.
     *
     * <p>Never throws. A manager that cannot answer - the headless harness throws by design, and a
     * console command can run before the engine is up - is treated as "no record", which is the
     * truthful reading and keeps a name plate from taking a command down with it (found by
     * {@code PolityFactsProbe}, 2026-10-24).
     */
    private static String recordName(CivilizationManager manager, String civId) {
        if (manager == null || civId == null || civId.isBlank()) {
            return null;
        }
        try {
            return manager.getCivilization(civId).map(Civilization::name).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** True when the pack carries a full civilization record (money, goods, engines) for this id. */
    public static boolean hasRecord(CivilizationManager manager, String civId) {
        return recordName(manager, civId) != null;
    }

    /**
     * One honest line about a civilization id, for a command or a name plate.
     *
     * <ul>
     *   <li>a pack record: {@code Zhou (1046 BC – 256 BC)}</li>
     *   <li>a named polity with no record: {@code Ahmadnagar Sultanate (1490 – 1636), named only}</li>
     *   <li>an id nobody can resolve: {@code cit-cit} — the id itself, marked so it reads as a gap</li>
     * </ul>
     */
    public static String describe(CivilizationManager manager, String civId) {
        if (civId == null || civId.isBlank()) {
            return "none";
        }
        String recordLabel = recordName(manager, civId);
        boolean recorded = recordLabel != null;
        String label = recordLabel;
        if (label == null) {
            label = name(civId);
        }
        int[] years = span(civId);
        StringBuilder out = new StringBuilder();
        out.append(label == null ? civId : label);
        if (years != null) {
            out.append(" (").append(years[0]).append(" to ").append(years[1]).append(')');
        }
        if (label == null) {
            out.append(" [no record, no name]");
        } else if (!recorded) {
            PolityGazetteer.Entry second = PolityGazetteer.get(civId);
            out.append(" [named only").append(second == null ? ""
                    : (second.recorded() ? ", second source" : ", second source, estimated")).append(']');
        }
        return out.toString();
    }

    /** A short form for a list line: {@code Zhou} or {@code Ahmadnagar Sultanate*}. */
    public static String shortName(CivilizationManager manager, String civId) {
        if (civId == null || civId.isBlank()) {
            return "none";
        }
        String recordLabel = recordName(manager, civId);
        String label = recordLabel != null ? recordLabel : name(civId);
        if (label == null) {
            return civId + "?";
        }
        return recordLabel != null ? label : label + "*";
    }
}
