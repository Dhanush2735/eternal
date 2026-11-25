package com.eternalengine.civilization;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.core.WorldStateManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reconciles the civilization table against the history corpus.
 *
 * <h3>The problem this solves</h3>
 * The gazetteer and the border boxes cite <b>101 distinct civilization ids</b>. The
 * authored table in {@code foundation_civilizations.json} holds 132 records. The two sets are
 * mostly disjoint: <b>58 of the 101 cited ids have no record</b>, and every one of the
 * 58 is South Asian -- {@code british_india}, {@code india}, {@code hyderabad_state},
 * {@code qutb_shahi}, {@code bahmani}, {@code kakatiya}, {@code pallava}, {@code chola} and so on.
 *
 * <p>The visible consequence: {@code HistoricalPresence} resolves an occupant by looking the
 * {@code civHint} up in this table, so a site whose hint is missing resolves to <i>nobody</i>. The
 * player standing in Secunderabad in 1948 sees a city with no ruler, and because
 * {@code VillagerNameDatabase} has no name pool for the id either, the crowd is named
 * "Villager Alex Stone".
 *
 * <h3>What this does</h3>
 * Reads every {@code civHint} in the gazetteer sites and every {@code civId} in the border boxes.
 * Any id the table does not already know is registered as a <b>synthesised</b> record:
 * <ul>
 *   <li>years taken from the matching border box when one exists, otherwise left open</li>
 *   <li>name title-cased from the id, so the display is honest about what it is</li>
 *   <li>population deliberately <b>not</b> set -- an unknown is an unknown, and
 *       {@link com.eternalengine.population.PopulationResolver} supplies the derived figure and
 *       labels it</li>
 * </ul>
 *
 * <p>Synthesised records are marked with {@code SYNTHETIC_} in their government type so they can
 * never be mistaken for authored data. A later authoring pass that adds a real record for the same
 * id simply wins, because this runs before the authored load and never overwrites an existing id.
 */
public final class CivSynthesis {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Marker so a synthesised record is never mistaken for an authored one. */
    public static final String SYNTHETIC_MARKER = "SYNTHETIC_UNATTRIBUTED";

    /** Where the derived records are written, beside the authored table in the world folder. */
    public static final String DERIVED_FILE = "civ_synthesis.json";

    /**
     * The earliest year the simulation can represent. Matches {@code TerraTimeManager}'s own floor,
     * so a synthesised span never claims a year the world generator will not produce.
     */
    public static final int EARLIEST_YEAR = -3000;

    public record Summary(int cited, int known, int synthesised, List<String> synthesisedIds) {
        public String display() {
            return "civ synthesis: " + cited + " cited by the corpus, " + known + " already known, "
                    + synthesised + " synthesised";
        }
    }

    private CivSynthesis() {}

    /**
     * Fill the gaps. Safe to call once after the authored table has loaded.
     *
     * @return a summary for logging, never null.
     */
    public static Summary reconcile(CivilizationManager manager, WorldStateManager state) {
        return reconcile(manager, state, null);
    }

    /**
     * As {@link #reconcile(CivilizationManager, WorldStateManager)}, and when {@code dataDir} is
     * given, also writes the derived records to {@link #DERIVED_FILE} under it.
     *
     * <p>Writing them is what makes the derivation persist. Without the file the table is rebuilt
     * from the corpus on every load, which is correct but makes the log lie by omission: the
     * second boot says "103 synthesised" again, so a reader cannot tell a working derivation from
     * one that is being redone because it was thrown away.
     */
    public static Summary reconcile(CivilizationManager manager, WorldStateManager state, Path dataDir) {
        if (manager == null) {
            return new Summary(0, 0, 0, List.of());
        }

        HistoryGazetteer gaz = HistoryGazetteer.get();

        // One ordered pass over everything the corpus cites.
        Set<String> cited = new LinkedHashSet<>();
        for (HistoryGazetteer.Site site : gaz.allSites()) {
            String hint = site.civHint();
            if (hint != null && !hint.isBlank()) {
                cited.add(hint.trim());
            }
        }
        for (HistoryGazetteer.Border border : gaz.allBorders()) {
            String civId = border.civId();
            if (civId != null && !civId.isBlank()) {
                cited.add(civId.trim());
            }
        }
        // The ruler register cites its own polity ids (the 30 polities of the Cliopatria record).
        // They must be synthesised too, or spawnCivId() would name a polity the manager cannot
        // resolve - the same failure this class exists to prevent for the corpus ids.
        // Only the Deccan record's polities, though: the world record names ~1,500 more, and every
        // synthesised civilization becomes a region that each simulation engine iterates
        // (RulerRegister.deccanPolities explains). World polities stay names on NPCs.
        RulerRegister register = RulerRegister.get();
        LOGGER.info("[CivSynthesis] {}", register.summary().display());
        for (RulerRegister.Polity polity : register.deccanPolities()) {
            cited.add(polity.civHint());
        }

        List<String> synthesised = new ArrayList<>();
        int known = 0;
        for (String civId : cited) {
            if (manager.getCivilization(civId).isPresent()) {
                known++;
                continue;
            }
            if (registerStub(manager, state, gaz, civId)) {
                synthesised.add(civId);
            }
        }

        Summary summary = new Summary(cited.size(), known, synthesised.size(), List.copyOf(synthesised));
        LOGGER.info("[CivSynthesis] {}", summary.display());
        if (!synthesised.isEmpty()) {
            LOGGER.info("[CivSynthesis] Synthesised: {}", synthesised);
        }
        if (dataDir != null) {
            int written = writeDerived(dataDir, manager);
            if (written > 0) {
                LOGGER.info("[CivSynthesis] Derived records written to {} ({})",
                        dataDir.resolve(DERIVED_FILE), written);
            }
        }
        return summary;
    }

    /** Records this class owns are the ones it wrote; the marker is the only thing that says so. */
    public static boolean isDerived(Civilization civ) {
        return civ != null && SYNTHETIC_MARKER.equals(civ.governmentType());
    }

    /**
     * Write every synthesised record to {@code <dataDir>/civ_synthesis.json}.
     *
     * <p>The file is loaded before the authored packs on the next start, so it is the persistence
     * layer for derived records; an authored record for the same id still wins, because the
     * authored pass runs after it and synthesis never overwrites a known id. Records that have
     * since been authored are dropped from the file, and an empty set deletes it, so the file
     * cannot outlive its own content. It is rewritten only when the text changes, so a load that
     * changes nothing does not touch the disk.
     *
     * @return how many records the file holds afterwards, 0 when it was removed.
     */
    public static int writeDerived(Path dataDir, CivilizationManager manager) {
        if (dataDir == null || manager == null) {
            return 0;
        }
        Path target = dataDir.resolve(DERIVED_FILE);
        List<Civilization> derived = new ArrayList<>();
        for (Civilization civ : manager.all()) {
            if (isDerived(civ)) {
                derived.add(civ);
            }
        }
        if (derived.isEmpty()) {
            try {
                Files.deleteIfExists(target);
            } catch (Exception e) {
                LOGGER.warn("[CivSynthesis] Could not remove {}: {}", target, e.getMessage());
            }
            return 0;
        }
        derived.sort(Comparator.comparing(Civilization::id));

        JsonArray records = new JsonArray();
        for (Civilization civ : derived) {
            records.add(toJson(civ));
        }
        JsonObject root = new JsonObject();
        root.addProperty("_derived", true);
        root.addProperty("_generatedBy", "com.eternalengine.civilization.CivSynthesis");
        root.addProperty("_records", derived.size());
        root.addProperty("_note", "Derived from the shipped corpus, not authored. Safe to delete: it is "
                + "rebuilt from the corpus on the next load. Safe to edit: a record in "
                + "foundation_civilizations.json with the same id always wins.");
        root.add("records", records);

        try {
            String text = GSON.toJson(root);
            if (Files.isRegularFile(target) && text.equals(Files.readString(target, StandardCharsets.UTF_8))) {
                return derived.size();
            }
            Files.createDirectories(dataDir);
            Path tmp = dataDir.resolve(DERIVED_FILE + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.warn("[CivSynthesis] Could not write {}: {}", target, e.getMessage());
            return 0;
        }
        return derived.size();
    }

    /** The fields {@link Civilization#fromJson} reads, written back out in the same names. */
    static JsonObject toJson(Civilization civ) {
        JsonObject o = new JsonObject();
        o.addProperty("id", civ.id());
        o.addProperty("name", civ.name());
        o.addProperty("foundingYear", civ.foundingYear());
        o.addProperty("capitalLat", civ.capitalLat());
        o.addProperty("capitalLon", civ.capitalLon());
        o.addProperty("active", civ.active());
        o.addProperty("governmentType", civ.governmentType());
        o.addProperty("population", civ.population());
        o.addProperty("treasury", civ.treasury());
        o.addProperty("technologyLevel", civ.technologyLevel());
        o.addProperty("religionId", civ.religionId());
        if (civ.fallYear() != Integer.MAX_VALUE) {
            o.addProperty("fallYear", civ.fallYear());
        }
        if (civ.peakYear() != 0) {
            o.addProperty("peakYear", civ.peakYear());
        }
        return o;
    }

    private static boolean registerStub(CivilizationManager manager, WorldStateManager state,
                                        HistoryGazetteer gaz, String civId) {
        // Years come from the widest border box that names this civ, when one exists. That is a real
        // span on record, not an invention -- which is the whole point of preferring it to a guess.
        int start = Integer.MIN_VALUE;
        int end = Integer.MAX_VALUE;
        double lat = 0.0d;
        double lon = 0.0d;
        boolean haveSpan = false;

        for (HistoryGazetteer.Border border : gaz.allBorders()) {
            if (!civId.equalsIgnoreCase(border.civId())) {
                continue;
            }
            if (!haveSpan) {
                start = border.start();
                end = border.end();
                lat = (border.minLat() + border.maxLat()) / 2.0d;
                lon = (border.minLon() + border.maxLon()) / 2.0d;
                haveSpan = true;
            } else {
                start = Math.min(start, border.start());
                end = Math.max(end, border.end());
            }
        }

        String name = titleCase(civId);
        java.util.Optional<RulerRegister.Extent> extent = haveSpan
                ? java.util.Optional.empty() : RulerRegister.get().extentOf(civId);
        if (extent.isPresent()) {
            // A ruler-register polity: its own recorded years (spans are inclusive, fallYear is
            // exclusive) and the mean position of the places it ruled, under the name the source
            // gives it -- not an open-ended stub sitting at 0,0.
            RulerRegister.Extent e = extent.get();
            start = Math.max(EARLIEST_YEAR, e.start());
            end = Math.max(start + 1, e.end() + 1);
            lat = e.lat();
            lon = e.lon();
            name = e.name();
            haveSpan = true;
        }

        if (!haveSpan) {
            // No box names it. The hint still proves the polity is referenced, so register it
            // open-ended rather than dropping it: HistoricalPresence then finds it, and
            // isActiveInYear() will not exclude it for an arbitrary reason.
            //
            // The floor is EARLIEST_YEAR, not Integer.MIN_VALUE. Two reasons. It is the same floor
            // the rest of the system uses -- TerraTimeManager refuses to simulate before -3000 --
            // so a synthesised span can never claim a year the simulation cannot reach. And it
            // displays as a year rather than as -2147483648, which reads as a bug.
            start = EARLIEST_YEAR;
            end = Integer.MAX_VALUE;
        }

        try {
            Civilization civ = new Civilization(civId, name, start, lat, lon);
            civ.setActive(true);
            civ.setGovernmentType(SYNTHETIC_MARKER);
            if (end != Integer.MAX_VALUE) {
                civ.setFallYear(end);
            }
            manager.addCivilization(civ, state);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[CivSynthesis] Could not synthesise {}: {}", civId, t.getMessage());
            return false;
        }
    }

    /**
     * {@code british_india} to {@code British India}. Deliberately mechanical: a synthesised name
     * should look derived, because it is. An author writing a real record replaces it.
     */
    static String titleCase(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        StringBuilder sb = new StringBuilder(id.length());
        boolean upper = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                sb.append(' ');
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString().trim();
    }
}
