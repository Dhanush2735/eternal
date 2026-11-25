package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WorldStateManager - Thread-safe central state store for all simulation data.
 * 
 * This class:
 * - Stores all civilization states, faction relations, resources, etc.
 * - Uses ConcurrentHashMap for thread-safe access
 * - Auto-saves periodically
 * - Registers with PersistenceManager for proper file handling
 */
public class WorldStateManager {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static WorldStateManager instance;
    private static boolean initialized = false;
    
    // Gson for serialization
    private final Gson gson;
    
    // World data path
    private Path worldDataPath;
    
    // State tracking
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong lastSaveTime = new AtomicLong(0);
    private final AtomicInteger saveVersion = new AtomicInteger(1);
    
    // ============ State Storage (all thread-safe) ============
    
    // Simulation state
    private final AtomicInteger currentYear = new AtomicInteger(-3000);
    private final AtomicInteger simulationSpeed = new AtomicInteger(10);
    private final ConcurrentHashMap<String, String> simulationFlags = new ConcurrentHashMap<>();
    
    // Civilization states
    private final ConcurrentHashMap<String, CivilizationState> civilizations = new ConcurrentHashMap<>();
    
    // Faction relations (key = "civId1:civId2", value = relation score)
    private final ConcurrentHashMap<String, Integer> factionRelations = new ConcurrentHashMap<>();
    
    // Territory ownership (key = regionId, value = civId)
    private final ConcurrentHashMap<String, String> territoryOwnership = new ConcurrentHashMap<>();
    
    // Population data (key = regionId, value = population)
    private final ConcurrentHashMap<String, Long> populations = new ConcurrentHashMap<>();
    
    // Active wars
    private final ConcurrentHashMap<String, WarState> activeWars = new ConcurrentHashMap<>();
    
    // Active diseases
    private final ConcurrentHashMap<String, DiseaseState> activeDiseases = new ConcurrentHashMap<>();
    
    // Trade routes (key = routeId)
    private final ConcurrentHashMap<String, TradeRouteState> tradeRoutes = new ConcurrentHashMap<>();
    
    // Technology levels (key = civId, value = set of unlocked techs)
    private final ConcurrentHashMap<String, Set<String>> technologyLevels = new ConcurrentHashMap<>();
    
    // Religion states
    private final ConcurrentHashMap<String, ReligionState> religions = new ConcurrentHashMap<>();
    
    // VIP states
    private final ConcurrentHashMap<String, VipState> vipStates = new ConcurrentHashMap<>();
    
    // Timeline branches
    private final ConcurrentHashMap<String, TimelineBranchState> timelineBranches = new ConcurrentHashMap<>();
    private String activeTimelineBranchId = "main";

    /** Ticks the Eternal Engine clock had counted inside the current year at the last save (step 14). */
    private final AtomicLong clockTicksInYear = new AtomicLong(0);

    /**
     * The world's calendar as last loaded (step 18d, key "calendar"): the Eternal Engine clock's starting point and
     * the last date the guard saw. Null for a world from before step 18d. While a world runs, saves take it from
     * {@link com.eternalengine.clock.WorldCalendar}.
     */
    private volatile JsonObject savedCalendar;

    /** An operator paused history with /ee pause. Kept in the save, so a restart does not quietly resume it. */
    private final AtomicBoolean adminPaused = new AtomicBoolean(false);

    /** One line on what the last load restored, for the log and /ee check all. */
    private volatile String lastLoadSummary = "nothing loaded yet";
    
    private WorldStateManager() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .create();
    }
    
    /**
     * Initialize the world state manager
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[WorldStateManager] Already initialized");
            return;
        }
        
        instance = new WorldStateManager();
        initialized = true;
        
        LOGGER.info("[WorldStateManager] Initialized");
    }
    
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the singleton instance
     */
    public static WorldStateManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("WorldStateManager not initialized!");
        }
        return instance;
    }
    
    /**
     * Load world state from disk
     */
    public void loadWorldState(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        this.worldDataPath = worldPath.resolve("eternalengine");
        
        LOGGER.info("[WorldStateManager] Loading world state from {}", worldDataPath);

        // Start from nothing. In single player the same game opens several worlds one after another,
        // and this manager is one object for all of them: without the reset the wars, flags and
        // civilization values of the world played before leaked into the next one.
        clearInMemory();
        
        // Ensure directory exists
        try {
            java.nio.file.Files.createDirectories(worldDataPath);
        } catch (Exception e) {
            LOGGER.error("[WorldStateManager] Failed to create data directory!", e);
            return;
        }
        
        // Load main state file through PersistenceManager so FileLock, validation,
        // backup recovery, and corruption handling are applied consistently.
        Path stateFile = worldDataPath.resolve("state.json");
        if (java.nio.file.Files.exists(stateFile)) {
            try {
                JsonObject state = PersistenceManager.getInstance().loadJson(stateFile);
                if (state != null) {
                    // Saves older than this mod version are brought up to date first (VersionManager
                    // keeps a backup of the file before it changes anything).
                    if (VersionManager.isReady()) {
                        state = VersionManager.getInstance().migrateIfNeeded(state, stateFile);
                    }
                    loadFromJson(state);
                    LOGGER.info("[WorldStateManager] Loaded existing state");
                } else {
                    LOGGER.warn("[WorldStateManager] State file unavailable/corrupt, using defaults");
                    loadDefaults();
                }
            } catch (Exception e) {
                LOGGER.error("[WorldStateManager] Failed to load state, using defaults!", e);
                loadDefaults();
            }
        } else {
            LOGGER.info("[WorldStateManager] No existing state, loading defaults");
            loadDefaults();
        }
        
        lastSaveTime.set(System.currentTimeMillis());
    }
    
    /**
     * Load state from JSON
     */
    private void loadFromJson(JsonObject state) {
        // Load basic simulation state
        if (state.has("currentYear")) {
            currentYear.set(state.get("currentYear").getAsInt());
        }
        if (state.has("simulationSpeed")) {
            simulationSpeed.set(state.get("simulationSpeed").getAsInt());
        }
        if (state.has("activeTimelineBranchId")) {
            activeTimelineBranchId = state.get("activeTimelineBranchId").getAsString();
        }
        if (state.has("saveVersion")) {
            saveVersion.set(state.get("saveVersion").getAsInt());
        }

        // Restore timeline branches. Older saves have no "timelineBranches" key; in that case the
        // branch map keeps whatever loadDefaults() seeded, which is a pristine main timeline.
        if (state.has("timelineBranches") && state.get("timelineBranches").isJsonArray()) {
            timelineBranches.clear();
            for (JsonElement el : state.getAsJsonArray("timelineBranches")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject bo = el.getAsJsonObject();
                TimelineBranchState b = new TimelineBranchState();
                b.branchId = optString(bo, "branchId", "main");
                b.parentId = bo.has("parentId") && !bo.get("parentId").isJsonNull()
                        ? bo.get("parentId").getAsString() : null;
                b.divergenceEventId = optString(bo, "divergenceEventId", "");
                b.divergenceYear = bo.has("divergenceYear") ? bo.get("divergenceYear").getAsInt() : 0;
                b.cause = optString(bo, "cause", "");
                b.isMain = bo.has("isMain") && bo.get("isMain").getAsBoolean();
                b.scenarioId = optString(bo, "scenarioId", "");
                b.historyLogEntry = optString(bo, "historyLogEntry", "");
                b.authoredUntilYear = bo.has("authoredUntilYear")
                        ? bo.get("authoredUntilYear").getAsInt() : 0;
                if (bo.has("suppressedEventIds") && bo.get("suppressedEventIds").isJsonArray()) {
                    for (JsonElement pin : bo.getAsJsonArray("suppressedEventIds")) {
                        try {
                            b.suppressedEventIds.add(pin.getAsString());
                        } catch (Exception ignored) {
                            // Skip a malformed pin id rather than losing the whole branch.
                        }
                    }
                }
                if (b.branchId != null && !b.branchId.isBlank()) {
                    timelineBranches.put(b.branchId, b);
                }
            }
            // A save must never come back without a main timeline to fall back on.
            if (!timelineBranches.containsKey("main")) {
                TimelineBranchState mainBranch = new TimelineBranchState();
                mainBranch.branchId = "main";
                mainBranch.parentId = null;
                mainBranch.isMain = true;
                mainBranch.divergenceYear = currentYear.get();
                timelineBranches.put("main", mainBranch);
            }
            // If the active branch did not survive, fall back rather than dangling.
            if (!timelineBranches.containsKey(activeTimelineBranchId)) {
                LOGGER.warn("[WorldStateManager] Active branch {} missing from save; reverting to main",
                        activeTimelineBranchId);
                activeTimelineBranchId = "main";
            }
        }

        // Complex subsystem state is loaded by each subsystem from its own state files.

        // Restore civilization runtime values. The records themselves are rebuilt from the pack
        // on every load; what can only come from here is what the simulation did to them.
        if (state.has("civilizations") && state.get("civilizations").isJsonArray()) {
            for (JsonElement el : state.getAsJsonArray("civilizations")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject co = el.getAsJsonObject();
                String id = optString(co, "id", null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                CivilizationState cs = new CivilizationState();
                cs.id = id;
                cs.name = optString(co, "name", id);
                cs.active = !co.has("active") || co.get("active").getAsBoolean();
                cs.population = co.has("population") ? co.get("population").getAsLong() : 0L;
                cs.treasury = co.has("treasury") ? co.get("treasury").getAsLong() : 0L;
                cs.techLevel = co.has("techLevel") ? co.get("techLevel").getAsInt() : 0;
                cs.religionId = optString(co, "religionId", null);
                cs.governmentType = optString(co, "governmentType", null);
                civilizations.put(id, cs);
            }
            LOGGER.info("[WorldStateManager] Restored {} civilization state record(s)",
                    civilizations.size());
        }

        // The running world (step 14). Until now only the year, the speed, the timeline branches and
        // the civilizations were written, so a restart ended every war and outbreak, closed every trade
        // route, forgot every world flag (famine, quarantine, which events had already happened) and
        // the event chain ran the whole past again. Each part is read on its own: one damaged entry
        // costs that entry, not the rest of the save.
        clockTicksInYear.set(readLong(state, "clockTicksInYear"));
        savedCalendar = state.has("calendar") && state.get("calendar").isJsonObject()
            ? state.getAsJsonObject("calendar").deepCopy() : null;
        adminPaused.set(readBoolean(state, "adminPaused"));
        readMap(state, "flags", simulationFlags, JsonElement::getAsString);
        readMap(state, "relations", factionRelations, JsonElement::getAsInt);
        readMap(state, "territory", territoryOwnership, JsonElement::getAsString);
        readMap(state, "populations", populations, JsonElement::getAsLong);
        readList(state, "wars", WarState.class, w -> w.warId, activeWars);
        readList(state, "diseases", DiseaseState.class, d -> d.diseaseId, activeDiseases);
        readList(state, "tradeRoutes", TradeRouteState.class, r -> r.routeId, tradeRoutes);
        lastLoadSummary = activeWars.size() + " war(s), " + activeDiseases.size() + " outbreak(s), "
                + tradeRoutes.size() + " trade route(s), " + simulationFlags.size() + " world flag(s)"
                + (adminPaused.get() ? ", paused by an operator" : "");
        LOGGER.info("[WorldStateManager] Restored the running world: {}", lastLoadSummary);
    }

    private static long readLong(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static boolean readBoolean(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static <V> void readMap(JsonObject o, String key, Map<String, V> into, Function<JsonElement, V> value) {
        if (!o.has(key) || !o.get(key).isJsonObject()) {
            return;
        }
        int bad = 0;
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
            try {
                V v = value.apply(e.getValue());
                if (e.getKey() != null && v != null) {
                    into.put(e.getKey(), v);
                }
            } catch (Exception ex) {
                bad++;
            }
        }
        if (bad > 0) {
            LOGGER.warn("[WorldStateManager] Skipped {} unreadable entr(ies) in '{}' of the save", bad, key);
        }
    }

    private <T> void readList(JsonObject o, String key, Class<T> type, Function<T, String> id, Map<String, T> into) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return;
        }
        int bad = 0;
        for (JsonElement el : o.getAsJsonArray(key)) {
            try {
                T t = gson.fromJson(el, type);
                String k = t == null ? null : id.apply(t);
                if (k == null || k.isBlank()) {
                    bad++;
                    continue;
                }
                into.put(k, t);
            } catch (Exception ex) {
                bad++;
            }
        }
        if (bad > 0) {
            LOGGER.warn("[WorldStateManager] Skipped {} unreadable entr(ies) in '{}' of the save", bad, key);
        }
    }

    /** Everything this manager holds for a world, gone; the next load or {@link #loadDefaults} fills it. */
    private void clearInMemory() {
        simulationFlags.clear();
        civilizations.clear();
        factionRelations.clear();
        territoryOwnership.clear();
        populations.clear();
        activeWars.clear();
        activeDiseases.clear();
        tradeRoutes.clear();
        technologyLevels.clear();
        religions.clear();
        vipStates.clear();
        timelineBranches.clear();
        activeTimelineBranchId = "main";
        clockTicksInYear.set(0L);
        savedCalendar = null;
        adminPaused.set(false);
        lastLoadSummary = "nothing loaded yet";
    }

    /**
     * {@code /ee reset confirm}: the simulation of this world starts over (year and speed from the config,
     * no branches, wars, outbreaks, routes or flags, the civilizations as the data describes them). The
     * caller saves afterwards. The Minecraft world itself (blocks, players, items) is not touched.
     */
    public void resetToDefaults() {
        clearInMemory();
        loadDefaults();
        lastLoadSummary = "reset by an operator";
    }

    private static String optString(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }
    
    /**
     * Load default state for new world
     */
    private void loadDefaults() {
        currentYear.set(EternalEngineConfig.COMMON.startYear.get());
        simulationSpeed.set(EternalEngineConfig.COMMON.defaultSpeed.get());
        activeTimelineBranchId = "main";

        // Create main timeline branch. Clearing first matters when initialising a world that
        // previously held branches in memory.
        timelineBranches.clear();
        TimelineBranchState mainBranch = new TimelineBranchState();
        mainBranch.branchId = "main";
        mainBranch.parentId = null;
        mainBranch.divergenceYear = currentYear.get();
        mainBranch.isMain = true;
        timelineBranches.put("main", mainBranch);
        
        markDirty();
    }
    
    /**
     * Save world state to disk
     */
    public void saveWorldState() {
        if (worldDataPath == null) {
            LOGGER.warn("[WorldStateManager] Cannot save - no world path set");
            return;
        }
        
        LOGGER.info("[WorldStateManager] Saving world state...");
        
        try {
            JsonObject state = buildStateJson(true);

            // Use PersistenceManager for atomic write
            PersistenceManager.getInstance().saveJsonAtomic(
                worldDataPath.resolve("state.json"), 
                state
            );
            
            dirty.set(false);
            lastSaveTime.set(System.currentTimeMillis());
            
            LOGGER.info("[WorldStateManager] State saved successfully");
            
        } catch (Exception e) {
            LOGGER.error("[WorldStateManager] Failed to save state!", e);
        }
    }

    /**
     * Exactly what {@link #saveWorldState()} would write now, without writing it and without counting a
     * save (step 15). {@code /ee check all} compares this with the running world, so the check writes no file.
     */
    public JsonObject previewStateJson() {
        return buildStateJson(false);
    }

    /** The content of state.json. {@code forSave}: count the save and remember the clock's position. */
    private JsonObject buildStateJson(boolean forSave) {
        JsonObject state = new JsonObject();
        
        // Save basic state
        state.addProperty("currentYear", currentYear.get());
        state.addProperty("simulationSpeed", simulationSpeed.get());
        state.addProperty("activeTimelineBranchId", activeTimelineBranchId);
        state.addProperty("saveVersion", forSave ? saveVersion.incrementAndGet() : saveVersion.get());
        state.addProperty("savedAt", System.currentTimeMillis());

        // Save timeline branches. Without this a fork does not survive a restart: the branch
        // registry is rebuilt empty and getActiveBranch() silently reverts to prime.
        JsonArray branches = new JsonArray();
        for (TimelineBranchState b : timelineBranches.values()) {
            JsonObject bo = new JsonObject();
            bo.addProperty("branchId", b.branchId);
            bo.addProperty("parentId", b.parentId);
            bo.addProperty("divergenceEventId", b.divergenceEventId);
            bo.addProperty("divergenceYear", b.divergenceYear);
            bo.addProperty("cause", b.cause);
            bo.addProperty("isMain", b.isMain);
            bo.addProperty("scenarioId", b.scenarioId == null ? "" : b.scenarioId);
            bo.addProperty("historyLogEntry", b.historyLogEntry == null ? "" : b.historyLogEntry);
            bo.addProperty("authoredUntilYear", b.authoredUntilYear);
            JsonArray suppressed = new JsonArray();
            for (String pin : b.suppressedEventIds) {
                suppressed.add(pin);
            }
            bo.add("suppressedEventIds", suppressed);
            branches.add(bo);
        }
        state.add("timelineBranches", branches);

        // Save civilization state. This was missing, and its absence was invisible: the map
        // was populated in memory on every load, so nothing looked wrong until something
        // changed a civ at runtime (a population rose, a treasury was spent, a polity fell)
        // and the change was gone after a restart. The static table can always be rebuilt
        // from the pack; only the runtime values need a file.
        JsonArray civs = new JsonArray();
        for (CivilizationState cs : civilizations.values()) {
            if (cs == null || cs.id == null || cs.id.isBlank()) {
                continue;
            }
            JsonObject co = new JsonObject();
            co.addProperty("id", cs.id);
            co.addProperty("name", cs.name == null ? cs.id : cs.name);
            co.addProperty("active", cs.active);
            co.addProperty("population", cs.population);
            co.addProperty("treasury", cs.treasury);
            co.addProperty("techLevel", cs.techLevel);
            co.addProperty("religionId", cs.religionId == null ? "" : cs.religionId);
            co.addProperty("governmentType", cs.governmentType == null ? "" : cs.governmentType);
            civs.add(co);
        }
        state.add("civilizations", civs);

        // The running world (step 14; read back in loadFromJson). Sorted maps so two saves of the
        // same world are the same file.
        state.addProperty("dataVersion", VersionManager.CURRENT_VERSION);
        long ticksInYear = clockTicksInYear.get();
        if (com.eternalengine.clock.SimulationClockManager.isInitialized()) {
            ticksInYear = com.eternalengine.clock.SimulationClockManager.getInstance().getTicksInCurrentYear();
            if (forSave) {
                clockTicksInYear.set(ticksInYear);
            }
        }
        state.addProperty("clockTicksInYear", ticksInYear);
        JsonObject calendar = com.eternalengine.clock.WorldCalendar.toJson();
        if (calendar == null) {
            calendar = savedCalendar;
        } else if (forSave) {
            savedCalendar = calendar;
        }
        if (calendar != null) {
            state.add("calendar", calendar.deepCopy());
        }
        state.addProperty("adminPaused", adminPaused.get());
        state.add("flags", gson.toJsonTree(new TreeMap<>(simulationFlags)));
        state.add("relations", gson.toJsonTree(new TreeMap<>(factionRelations)));
        state.add("territory", gson.toJsonTree(new TreeMap<>(territoryOwnership)));
        state.add("populations", gson.toJsonTree(new TreeMap<>(populations)));
        state.add("wars", gson.toJsonTree(new ArrayList<>(new TreeMap<>(activeWars).values())));
        state.add("diseases", gson.toJsonTree(new ArrayList<>(new TreeMap<>(activeDiseases).values())));
        state.add("tradeRoutes", gson.toJsonTree(new ArrayList<>(new TreeMap<>(tradeRoutes).values())));

        return state;
    }
    
    /**
     * Check and perform auto-save if needed
     */
    public void checkAutoSave() {
        if (!dirty.get()) {
            return;
        }
        
        long interval = EternalEngineConfig.COMMON.autoSaveIntervalMinutes.get() * 60 * 1000L;
        if (System.currentTimeMillis() - lastSaveTime.get() >= interval) {
            saveWorldState();
        }
    }
    
    /**
     * Mark state as dirty (needs saving)
     */
    public void markDirty() {
        dirty.set(true);
    }
    
    // ============ Simulation flags ============

    public boolean hasFlag(String key) {
        return key != null && simulationFlags.containsKey(key);
    }

    public String getFlag(String key) {
        return key == null ? null : simulationFlags.get(key);
    }

    public void setFlag(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        simulationFlags.put(key, value == null ? "" : value);
        markDirty();
    }

    public void removeFlag(String key) {
        if (key == null) {
            return;
        }
        simulationFlags.remove(key);
        markDirty();
    }

    /** Every flag whose key starts with {@code prefix} (a copy). */
    public Map<String, String> getFlagsWithPrefix(String prefix) {
        Map<String, String> out = new TreeMap<>();
        if (prefix == null) {
            return out;
        }
        simulationFlags.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                out.put(k, v);
            }
        });
        return out;
    }

    public int getFlagCount() {
        return simulationFlags.size();
    }

    // ============ Clock position, operator pause, last load (step 14) ============

    public long getClockTicksInYear() {
        return clockTicksInYear.get();
    }

    /** The calendar from the save (step 18d), or null for a world from before it. */
    @Nullable
    public JsonObject getSavedCalendar() {
        return savedCalendar;
    }

    public boolean isAdminPaused() {
        return adminPaused.get();
    }

    public void setAdminPaused(boolean paused) {
        adminPaused.set(paused);
        markDirty();
    }

    public String getLastLoadSummary() {
        return lastLoadSummary;
    }

    /** {@code <world>/eternalengine/state.json}, or null before a world loaded. */
    @Nullable
    public Path getStateFile() {
        return worldDataPath == null ? null : worldDataPath.resolve("state.json");
    }

    public Map<String, DiseaseState> getAllActiveDiseases() {
        return new ConcurrentHashMap<>(activeDiseases);
    }

    // ============ Year/Speed accessors ============
    
    public int getCurrentYear() {
        return currentYear.get();
    }
    
    public void setCurrentYear(int year) {
        currentYear.set(year);
        markDirty();
    }
    
    public int getSimulationSpeed() {
        return simulationSpeed.get();
    }
    
    public void setSimulationSpeed(int speed) {
        simulationSpeed.set(speed);
        markDirty();
    }
    
    // ============ Civilization state ============
    
    @Nullable
    public CivilizationState getCivilization(String civId) {
        return civilizations.get(civId);
    }
    
    public void setCivilization(String civId, CivilizationState state) {
        civilizations.put(civId, state);
        markDirty();
    }
    
    public void removeCivilization(String civId) {
        civilizations.remove(civId);
        markDirty();
    }
    
    public Set<String> getAllCivilizationIds() {
        return civilizations.keySet();
    }
    
    // ============ Faction relations ============
    
    public int getRelation(String civId1, String civId2) {
        String key = makeRelationKey(civId1, civId2);
        return factionRelations.getOrDefault(key, 0);
    }
    
    public void setRelation(String civId1, String civId2, int score) {
        String key = makeRelationKey(civId1, civId2);
        factionRelations.put(key, Math.max(-100, Math.min(100, score)));
        markDirty();
    }
    
    public void modifyRelation(String civId1, String civId2, int delta) {
        int current = getRelation(civId1, civId2);
        setRelation(civId1, civId2, current + delta);
    }
    
    private String makeRelationKey(String civId1, String civId2) {
        // Ensure consistent key regardless of order
        if (civId1.compareTo(civId2) < 0) {
            return civId1 + ":" + civId2;
        }
        return civId2 + ":" + civId1;
    }
    
    // ============ Territory ============
    
    @Nullable
    public String getTerritoryOwner(String regionId) {
        return territoryOwnership.get(regionId);
    }
    
    public void setTerritoryOwner(String regionId, String civId) {
        territoryOwnership.put(regionId, civId);
        markDirty();
    }
    
    // ============ Population ============
    
    public long getPopulation(String regionId) {
        return populations.getOrDefault(regionId, 0L);
    }
    
    public void setPopulation(String regionId, long population) {
        populations.put(regionId, Math.max(0, population));
        markDirty();
    }
    
    // ============ Wars ============
    
    @Nullable
    public WarState getWar(String warId) {
        return activeWars.get(warId);
    }
    
    public void addWar(String warId, WarState war) {
        activeWars.put(warId, war);
        markDirty();
    }
    
    public void removeWar(String warId) {
        activeWars.remove(warId);
        markDirty();
    }
    
    public Map<String, WarState> getAllActiveWars() {
        return new ConcurrentHashMap<>(activeWars);
    }
    
    // ============ Diseases ============
    
    @Nullable
    public DiseaseState getDisease(String diseaseId) {
        return activeDiseases.get(diseaseId);
    }
    
    public void addDisease(String diseaseId, DiseaseState disease) {
        activeDiseases.put(diseaseId, disease);
        markDirty();
    }
    
    public void removeDisease(String diseaseId) {
        activeDiseases.remove(diseaseId);
        markDirty();
    }
    
    // ============ Treasury ============

    public long getTreasury(String civId) {
        CivilizationState civ = civilizations.get(civId);
        return civ == null ? 0L : civ.treasury;
    }

    public void setTreasury(String civId, long amount) {
        if (civId == null || civId.isBlank()) {
            return;
        }
        CivilizationState civ = civilizations.computeIfAbsent(civId, id -> {
            CivilizationState created = new CivilizationState();
            created.id = id;
            created.name = id;
            created.active = true;
            return created;
        });
        civ.treasury = amount;
        markDirty();
    }

    // ============ Trade Routes ============
    
    @Nullable
    public TradeRouteState getTradeRoute(String routeId) {
        return tradeRoutes.get(routeId);
    }
    
    public void addTradeRoute(String routeId, TradeRouteState route) {
        tradeRoutes.put(routeId, route);
        markDirty();
    }
    
    public void removeTradeRoute(String routeId) {
        tradeRoutes.remove(routeId);
        markDirty();
    }

    public Map<String, TradeRouteState> getAllTradeRoutes() {
        return new ConcurrentHashMap<>(tradeRoutes);
    }
    
    // ============ Timeline ============
    
    public String getActiveTimelineBranchId() {
        return activeTimelineBranchId;
    }
    
    public void setActiveTimelineBranch(String branchId) {
        if (timelineBranches.containsKey(branchId)) {
            this.activeTimelineBranchId = branchId;
            markDirty();
        }
    }
    
    @Nullable
    public TimelineBranchState getTimelineBranch(String branchId) {
        return timelineBranches.get(branchId);
    }
    
    public void addTimelineBranch(TimelineBranchState branch) {
        timelineBranches.put(branch.branchId, branch);
        markDirty();
    }

    /**
     * Every persisted branch. Used by {@code TimelineManager} to rebuild its in-memory registry
     * after a load — the step whose absence caused forks to silently revert to prime.
     */
    public Map<String, TimelineBranchState> getAllTimelineBranches() {
        return new ConcurrentHashMap<>(timelineBranches);
    }
    
    // ============ State classes ============
    
    public static class CivilizationState {
        public String id;
        public String name;
        public boolean active;
        public long population;
        public long treasury;
        public int techLevel;
        public String religionId;
        public String governmentType;
    }
    
    public static class WarState {
        public String warId;
        public String name;
        public Set<String> attackers;
        public Set<String> defenders;
        public int startYear;
        public int casualties;
        public boolean active;
        /** Why it started (the war's trigger), and how tired of it both sides are, 0-100 (step 14). */
        public String cause;
        public double exhaustion;
    }
    
    public static class DiseaseState {
        public String diseaseId;
        public String name;
        public int startYear;
        public double spreadRadius;
        public Set<String> affectedRegions;
        public boolean active;
        /** Where it began, how hard each region is hit (0-1) and the dead so far (step 14). */
        public String origin;
        public Map<String, Double> severity;
        public long deaths;
    }
    
    public static class TradeRouteState {
        public String routeId;
        public String name;
        public String originCivId;
        public String destCivId;
        public int value;
        public boolean active;
        /** Enough to open the same route again after a restart (step 14). */
        public java.util.List<String> goods;
        public String transport;
        public double danger;
        public int openedYear;
        public double originLat;
        public double originLon;
        public double destLat;
        public double destLon;
    }
    
    public static class ReligionState {
        public String religionId;
        public String name;
        public int followers;
        public Set<String> regions;
    }
    
    public static class VipState {
        public String vipId;
        public String assignedPlayerUuid;
        public boolean alive;
        public int age;
        public int health;
    }
    
    /**
     * Persisted record of one timeline branch.
     *
     * <p><b>Why these fields exist.</b> Divergence used to save only identity — id, parent, pivot,
     * year, cause. The branch <em>contents</em> lived only in memory, so on reload
     * {@code TimelineManager} was rebuilt with {@code main} alone, {@code getActiveBranch()}
     * silently fell back to prime, and a player's fork vanished while the Codex still reported it
     * as active. Suppression and the scenario id are what make a fork survive a restart.
     *
     * <p>Deliberately does not persist the scenario's consequence list: it is re-derivable from
     * {@code scenarioId}, and storing it twice invites the two copies to disagree.
     */
    public static class TimelineBranchState {
        public String branchId;
        public String parentId;
        public String divergenceEventId;
        public int divergenceYear;
        public String cause;
        public boolean isMain;

        /** Scenario that defines this branch's authored consequences, or "" for none. */
        public String scenarioId = "";

        /** Pins that do not happen in this branch. Siblings included at fork time. */
        public final java.util.List<String> suppressedEventIds = new java.util.ArrayList<>();

        /** Human-readable one-line summary shown by the Codex. */
        public String historyLogEntry = "";

        /** Year the authored record stops and simulation takes over, or 0 if unbounded. */
        public int authoredUntilYear;
    }
}
