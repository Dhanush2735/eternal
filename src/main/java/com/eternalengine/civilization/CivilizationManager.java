package com.eternalengine.civilization;

import com.eternalengine.core.WorldStateManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Civilization registry foundation.
 */
public final class CivilizationManager {

    /** Authored table as shipped in the jar; the fallback when the world folder holds none. */
    private static final String PACKAGED_TABLE =
            "/data/eternalengine/eternal/civilizations/foundation_civilizations.json";

    private final ConcurrentHashMap<String, Civilization> civilizations = new ConcurrentHashMap<>();
    private final TerritoryManager territoryManager;

    public CivilizationManager() {
        this(new TerritoryManager());
    }

    public CivilizationManager(TerritoryManager territoryManager) {
        this.territoryManager = territoryManager == null ? new TerritoryManager() : territoryManager;
    }

    public void addCivilization(Civilization civilization, WorldStateManager state) {
        if (civilization == null) {
            return;
        }
        civilizations.put(civilization.id(), civilization);
        syncCivilization(civilization, state);
    }

    public Optional<Civilization> getCivilization(String id) {
        return Optional.ofNullable(civilizations.get(id));
    }

    public Optional<Civilization> getCivilizationAt(double lat, double lon, int year) {
        return com.eternalengine.core.WorldQuery.presence(this, com.eternalengine.core.WorldKey.at(lat, lon, year))
                .occupant()
                .map(HistoricalPresence.Match::civ);
    }

    public List<Civilization> getActiveCivilizations() {
        return List.copyOf(civilizations.values().stream().filter(Civilization::active).toList());
    }

    public List<Civilization> getActiveCivilizations(int year) {
        return List.copyOf(civilizations.values().stream().filter(civ -> civ.isActiveInYear(year)).toList());
    }

    public Civilization spawnCivilization(String civId, int year, WorldStateManager state) {
        Civilization civ = civilizations.computeIfAbsent(civId,
            id -> new Civilization(id, id, year, 0.0d, 0.0d));
        civ.setActive(true);
        civ.setFoundingYear(Math.min(civ.foundingYear(), year));
        syncCivilization(civ, state);
        return civ;
    }

    public void removeCivilization(String id, String reason, int year, WorldStateManager state) {
        Civilization civ = civilizations.get(id);
        if (civ == null) {
            return;
        }
        civ.setActive(false);
        civ.setFallYear(year);
        if (state != null) {
            WorldStateManager.CivilizationState st = state.getCivilization(id);
            if (st != null) {
                st.active = false;
                state.setCivilization(id, st);
            }
            state.setFlag("civ.fall." + id, reason == null ? Integer.toString(year) : reason);
        }
    }

    public Optional<Civilization> mergeCivilizations(String id1, String id2, String newId, WorldStateManager state) {
        Civilization a = civilizations.get(id1);
        Civilization b = civilizations.get(id2);
        if (a == null || b == null) {
            return Optional.empty();
        }
        Civilization merged = new Civilization(newId, a.name() + "-" + b.name(), Math.min(a.foundingYear(), b.foundingYear()),
            a.capitalLat(), a.capitalLon());
        merged.setPopulation(a.population() + b.population());
        merged.setTreasury(a.treasury() + b.treasury());
        civilizations.put(merged.id(), merged);
        a.setActive(false);
        b.setActive(false);
        syncCivilization(merged, state);
        return Optional.of(merged);
    }

    public List<String> ids() {
        return new ArrayList<>(civilizations.keySet());
    }

    /** Every record in id order: the whole table, for dumps, commands and the derived-file write. */
    public List<Civilization> all() {
        List<Civilization> list = new ArrayList<>(civilizations.values());
        list.sort(java.util.Comparator.comparing(Civilization::id));
        return list;
    }

    public int size() {
        return civilizations.size();
    }

    public TerritoryManager territoryManager() {
        return territoryManager;
    }

    /**
     * Mirror a record into the world state.
     *
     * <p>The subtlety is the direction of the runtime values. A record is rebuilt from the pack on
     * every load and therefore starts at the pack's values -- population 0 for anything the pack
     * does not carry -- while the state entry is what the last save wrote down. Writing the
     * record's zeros over an existing entry during a load would destroy exactly the values the save
     * exists to keep, and would do it before {@link #adoptRestoredState} could read them. So an
     * entry that already exists keeps its runtime values; only the derived fields (name, active,
     * government type) are refreshed. A brand-new id gets a full entry, as before.
     */
    private void syncCivilization(Civilization civ, WorldStateManager state) {
        if (state == null || civ == null) {
            return;
        }
        WorldStateManager.CivilizationState st = state.getCivilization(civ.id());
        if (st == null) {
            st = new WorldStateManager.CivilizationState();
            st.population = civ.population();
            st.treasury = civ.treasury();
            st.techLevel = civ.technologyLevel();
            st.religionId = civ.religionId();
        }
        st.id = civ.id();
        st.name = civ.name();
        st.active = civ.active();
        st.governmentType = civ.governmentType();
        state.setCivilization(civ.id(), st);
    }

    public void loadCivilizations(Path dataDir, WorldStateManager state) {
        Path derived = dataDir == null ? null : dataDir.resolve(CivSynthesis.DERIVED_FILE);

        // Derived records first, authored records second. The order is the precedence rule:
        // synthesis never overwrites an id the table already knows, so loading the derived file
        // ahead of the authored one means an author who writes a real record for
        // `hyderabad_state` wins over the record that was synthesised for it last time.
        if (derived != null && Files.isRegularFile(derived)) {
            loadCivilizationFile(derived, state);
        }
        int authored = 0;
        if (dataDir != null && Files.exists(dataDir)) {
            try (var stream = Files.walk(dataDir)) {
                for (Path path : stream.filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> derived == null || !p.equals(derived)).toList()) {
                    authored += loadCivilizationFile(path, state);
                }
            } catch (Exception ignored) {
                // Keep existing on load failure.
            }
        }
        // Then the table inside the jar, for every id the world copy does not already answer for.
        // This is what keeps the packaged table a moving authority rather than a snapshot taken on
        // install day: a record edited in the world folder still wins, and a record added to the
        // jar afterwards -- a corrected end year, a polity that used to be derived -- still
        // arrives, without anybody having to reinstall their world. It is also why a world that
        // was never installed at all gets a full table, which is what a bare `1948, where am I`
        // needs in order to answer with a ruler instead of nobody.
        int packaged = loadPackagedTable(state);
        if (packaged > 0) {
            com.eternalengine.EternalEngineMod.getLogger().info(
                "[CivilizationManager] Packaged table contributed {} record(s) the world copy did not answer for",
                packaged);
        }

        // Reconcile unconditionally, not only on the success path. The corpus cites 101 distinct
        // civ ids and the authored table held 132 records that were mostly disjoint from them --
        // 58 cited ids had no record at all, every one of them South Asian. Without this, an
        // occupant lookup for a site whose hint is missing resolves to nobody, which is why
        // Secunderabad in 1948 had no ruler and its crowd used the generic name pool.
        // The call also rewrites the derived file, so the next boot logs "N already known"
        // instead of re-synthesising the same records from scratch.
        reconcileWithCorpus(state, dataDir);

        adoptRestoredState(state);
    }

    /**
     * Copy runtime values the last save recorded onto the records just built.
     *
     * <p>The record is rebuilt from the pack every load, so it starts at the pack's values --
     * population 0 for anything the pack does not carry. The saved state is the only place a
     * runtime change survives, so it is applied last and wins. Runs after the load and the
     * reconciliation, so it does not matter whether the world state file was read before or
     * after this call.
     */
    private void adoptRestoredState(WorldStateManager state) {
        if (state == null) {
            return;
        }
        int adopted = 0;
        for (String id : new ArrayList<>(state.getAllCivilizationIds())) {
            Civilization civ = civilizations.get(id);
            WorldStateManager.CivilizationState st = state.getCivilization(id);
            if (civ == null || st == null) {
                continue;
            }
            // A polity the save recorded as no longer active stays inactive: without this, a civ
            // that fell mid-game came back to life on the next load, because `active` is not
            // recoverable from the pack's years alone.
            if (!st.active) {
                civ.setActive(false);
            }
            if (st.population > 0) {
                civ.setPopulation(st.population);
            }
            if (st.treasury != 0) {
                civ.setTreasury(st.treasury);
            }
            if (st.techLevel > 0) {
                civ.setTechnologyLevel(st.techLevel);
            }
            if (st.religionId != null && !st.religionId.isBlank() && !"none".equals(st.religionId)) {
                civ.setReligionId(st.religionId);
            }
            adopted++;
        }
        if (adopted > 0) {
            com.eternalengine.EternalEngineMod.getLogger().info(
                "[CivilizationManager] Reapplied saved state to {} record(s)", adopted);
        }
    }

    /** Register a record for every civ id the history corpus cites but the table lacks. */
    public void reconcileWithCorpus(WorldStateManager state) {
        reconcileWithCorpus(state, null);
    }

    /** As above, and refresh the derived-records file under {@code dataDir} when one is given. */
    public void reconcileWithCorpus(WorldStateManager state, Path dataDir) {
        try {
            CivSynthesis.reconcile(this, state, dataDir);
        } catch (Throwable t) {
            // A synthesis failure must never prevent the server from starting.
            com.eternalengine.EternalEngineMod.getLogger().error(
                "[CivilizationManager] Civ synthesis failed: {}", t.getMessage());
        }
    }

    /** @return how many records the file contributed, 0 on any failure. */
    private int loadCivilizationFile(Path file, WorldStateManager state) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return readCivilizations(reader, state);
        } catch (Exception ignored) {
            // Keep existing on error.
            return 0;
        }
    }

    /**
     * The copy of the authored table inside the jar. Authored truth beats derived truth, so a
     * record here replaces one that {@link CivSynthesis} wrote for the same id, but never one that
     * came from the world folder.
     */
    private int loadPackagedTable(WorldStateManager state) {
        try (java.io.InputStream in = CivilizationManager.class.getResourceAsStream(PACKAGED_TABLE)) {
            if (in == null) {
                return 0;
            }
            return readCivilizations(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8),
                    state, true);
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Reads either a bare record, an array of them, or the {@code {records:[...]}} derived file. */
    private int readCivilizations(Reader reader, WorldStateManager state) {
        return readCivilizations(reader, state, false);
    }

    /**
     * @param onlyOverDerived when true, an id that already has a record from the world folder is
     *                        left alone, while an id that only has a derived record is replaced.
     *                        That is the precedence rule, in one place: world-authored, then
     *                        jar-authored, then derived.
     */
    private int readCivilizations(Reader reader, WorldStateManager state, boolean onlyOverDerived) {
        JsonElement root = JsonParser.parseReader(reader);
        int n = 0;
        if (root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                if (element.isJsonObject()
                        && addFromJson(element.getAsJsonObject(), state, onlyOverDerived)) {
                    n++;
                }
            }
            return n;
        }
        if (!root.isJsonObject()) {
            return 0;
        }
        JsonObject obj = root.getAsJsonObject();
        // The derived file is an object with a `records` array, not a bare record, so the marker
        // on it can never be mistaken for a civilization called `_derived`.
        if (obj.has("records") && obj.get("records").isJsonArray()) {
            for (JsonElement element : obj.getAsJsonArray("records")) {
                if (element.isJsonObject()
                        && addFromJson(element.getAsJsonObject(), state, onlyOverDerived)) {
                    n++;
                }
            }
            return n;
        }
        return addFromJson(obj, state, onlyOverDerived) ? 1 : 0;
    }

    private boolean addFromJson(JsonObject json, WorldStateManager state, boolean onlyOverDerived) {
        Civilization civ = Civilization.fromJson(json);
        if (onlyOverDerived) {
            Civilization existing = civilizations.get(civ.id());
            if (existing != null && !CivSynthesis.isDerived(existing)) {
                return false;
            }
        }
        addCivilization(civ, state);
        return true;
    }
}
