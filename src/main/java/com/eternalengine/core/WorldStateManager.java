package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
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
        
        // Complex subsystem state is loaded by each subsystem from its own state files.
    }
    
    /**
     * Load default state for new world
     */
    private void loadDefaults() {
        currentYear.set(EternalEngineConfig.COMMON.startYear.get());
        simulationSpeed.set(EternalEngineConfig.COMMON.defaultSpeed.get());
        activeTimelineBranchId = "main";
        
        // Create main timeline branch
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
            JsonObject state = new JsonObject();
            
            // Save basic state
            state.addProperty("currentYear", currentYear.get());
            state.addProperty("simulationSpeed", simulationSpeed.get());
            state.addProperty("activeTimelineBranchId", activeTimelineBranchId);
            state.addProperty("saveVersion", saveVersion.incrementAndGet());
            state.addProperty("savedAt", System.currentTimeMillis());
            
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
    }
    
    public static class DiseaseState {
        public String diseaseId;
        public String name;
        public int startYear;
        public double spreadRadius;
        public Set<String> affectedRegions;
        public boolean active;
    }
    
    public static class TradeRouteState {
        public String routeId;
        public String name;
        public String originCivId;
        public String destCivId;
        public int value;
        public boolean active;
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
    
    public static class TimelineBranchState {
        public String branchId;
        public String parentId;
        public String divergenceEventId;
        public int divergenceYear;
        public String cause;
        public boolean isMain;
    }
}
