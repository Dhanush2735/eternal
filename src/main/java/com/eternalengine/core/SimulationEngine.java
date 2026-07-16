package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.affairs.CurrentAffairsEngine;
import com.eternalengine.agriculture.AgricultureEngine;
import com.eternalengine.anomaly.AnomalyDetectionEngine;
import com.eternalengine.brain.BrainAuditLog;
import com.eternalengine.brain.LlmBrainBridge;
import com.eternalengine.brain.NoopLlmBrainBridge;
import com.eternalengine.brain.RemoteBrainBridge;
import com.eternalengine.bot.BotCurrentAffairsService;
import com.eternalengine.bot.MindcraftBridge;
import com.eternalengine.brain.ServerDecisionQueue;
import com.eternalengine.buildings.BlockUpdateQueue;
import com.eternalengine.buildings.BuildingDateInferenceEngine;
import com.eternalengine.buildings.BuildingHistoryDatabase;
import com.eternalengine.civilization.CivilizationManager;
import com.eternalengine.civilization.FactionRelationshipSystem;
import com.eternalengine.climate.ClimateEngine;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.config.EternalEngineConfig;
import com.eternalengine.disease.DiseaseEngine;
import com.eternalengine.history.EventChainEngine;
import com.eternalengine.history.HistoricalEventDatabase;
import com.eternalengine.incident.IncidentCreatorEngine;
import com.eternalengine.population.ClassSystem;
import com.eternalengine.population.MigrationSystem;
import com.eternalengine.population.PopulationEngine;
import com.eternalengine.population.UrbanizationSystem;
import com.eternalengine.religion.ReligionEngine;
import com.eternalengine.reputation.ReputationEngine;
import com.eternalengine.technology.TechnologyEngine;
import com.eternalengine.timeline.TimelineManager;
import com.eternalengine.trade.EconomyEngine;
import com.eternalengine.trade.TradeRouteEngine;
import com.eternalengine.vehicle.VehicleEngine;
import com.eternalengine.vip.AuthoritySystem;
import com.eternalengine.vip.VipCommandSystem;
import com.eternalengine.vip.VipSystem;
import com.eternalengine.vip.VipTakeoverSystem;
import com.eternalengine.web.WebConsoleService;
import com.eternalengine.villager.VillagerBehaviorSystem;
import com.eternalengine.villager.VillagerManager;
import com.eternalengine.war.WarEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SimulationEngine - Master controller for the Eternal Engine simulation.
 * 
 * This is the central hub that:
 * - Holds references to all simulation systems
 * - Delegates tick updates to appropriate systems
 * - Manages simulation lifecycle (start/stop/pause)
 * - Coordinates cross-system interactions
 * 
 * NO simulation logic should be in this class - it only delegates.
 */
public class SimulationEngine {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static SimulationEngine instance;
    private static boolean initialized = false;
    
    // Server reference
    private MinecraftServer server;

    // Phase-0 foundation systems. These are deterministic/server-authoritative.
    private final AnomalyDetectionEngine anomalyDetectionEngine;
    private final IncidentCreatorEngine incidentCreatorEngine;
    private final AgricultureEngine agricultureEngine;
    private final ClimateEngine climateEngine;
    private final CivilizationManager civilizationManager;
    private final FactionRelationshipSystem factionRelationshipSystem;
    private final PopulationEngine populationEngine;
    private final MigrationSystem migrationSystem;
    private final ClassSystem classSystem;
    private final UrbanizationSystem urbanizationSystem;
    private final ReligionEngine religionEngine;
    private final TechnologyEngine technologyEngine;
    private final ReputationEngine reputationEngine;
    private final AuthoritySystem authoritySystem;
    private final VipCommandSystem vipCommandSystem;
    private final VipSystem vipSystem;
    private final VipTakeoverSystem vipTakeoverSystem;
    private final VillagerManager villagerManager;
    private final VillagerBehaviorSystem villagerBehaviorSystem;
    private final CurrentAffairsEngine currentAffairsEngine;
    private final BotCurrentAffairsService botCurrentAffairsService;
    private final MindcraftBridge mindcraftBridge;
    private final BuildingHistoryDatabase buildingHistoryDatabase;
    private final BuildingDateInferenceEngine buildingDateInferenceEngine;
    private final BlockUpdateQueue blockUpdateQueue;
    private final VehicleEngine vehicleEngine;
    private final TradeRouteEngine tradeRouteEngine;
    private final EconomyEngine economyEngine;
    private final TimelineManager timelineManager;
    private final HistoricalEventDatabase historicalEventDatabase;
    private final EventChainEngine eventChainEngine;
    private final WarEngine warEngine;
    private final DiseaseEngine diseaseEngine;
    private final ServerDecisionQueue serverDecisionQueue;
    private final BrainAuditLog brainAuditLog;
    private volatile LlmBrainBridge brainBridge;
    private final WebConsoleService webConsoleService;
    
    // Simulation state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    
    // Tick counter
    private final AtomicLong tickCounter = new AtomicLong(0);
    
    // Performance tracking
    private long lastTickStartTime;
    private long totalTickTimeMs;
    private int ticksThisSecond;
    private long secondStartTime;
    
    private SimulationEngine() {
        this.anomalyDetectionEngine = new AnomalyDetectionEngine();
        this.incidentCreatorEngine = new IncidentCreatorEngine();
        this.agricultureEngine = new AgricultureEngine();
        this.climateEngine = new ClimateEngine();
        this.civilizationManager = new CivilizationManager();
        this.factionRelationshipSystem = new FactionRelationshipSystem();
        this.populationEngine = new PopulationEngine();
        this.migrationSystem = new MigrationSystem();
        this.classSystem = new ClassSystem();
        this.urbanizationSystem = new UrbanizationSystem();
        this.religionEngine = new ReligionEngine();
        this.technologyEngine = new TechnologyEngine();
        this.reputationEngine = new ReputationEngine();
        this.authoritySystem = new AuthoritySystem();
        this.vipCommandSystem = new VipCommandSystem(this.authoritySystem);
        this.vipSystem = new VipSystem(this.reputationEngine);
        this.vipTakeoverSystem = new VipTakeoverSystem(this.reputationEngine);
        this.villagerManager = new VillagerManager();
        this.villagerBehaviorSystem = new VillagerBehaviorSystem();
        this.currentAffairsEngine = new CurrentAffairsEngine();
        this.botCurrentAffairsService = new BotCurrentAffairsService(this.currentAffairsEngine);
        this.mindcraftBridge = new MindcraftBridge();
        this.buildingHistoryDatabase = new BuildingHistoryDatabase();
        this.buildingDateInferenceEngine = new BuildingDateInferenceEngine();
        this.blockUpdateQueue = new BlockUpdateQueue();
        this.vehicleEngine = new VehicleEngine();
        this.tradeRouteEngine = new TradeRouteEngine();
        this.economyEngine = new EconomyEngine();
        this.timelineManager = new TimelineManager();
        this.historicalEventDatabase = new HistoricalEventDatabase();
        this.eventChainEngine = new EventChainEngine(this.historicalEventDatabase, this.timelineManager);
        this.warEngine = new WarEngine();
        this.diseaseEngine = new DiseaseEngine();
        this.serverDecisionQueue = new ServerDecisionQueue();
        this.brainAuditLog = new BrainAuditLog();
        this.brainBridge = createConfiguredBrainBridge();
        this.webConsoleService = new WebConsoleService();

        // Register for server tick events
        MinecraftForge.EVENT_BUS.register(this);
    }

    private LlmBrainBridge createConfiguredBrainBridge() {
        if (isRemoteBrainEnabledResolved() && hasAnyRemoteGatewayConfigured()) {
            return createRemoteBrainBridge();
        }
        return new NoopLlmBrainBridge();
    }

    private boolean isRemoteBrainEnabledResolved() {
        String override = overrideString("EE_BRAIN_ENABLED", "ee.brain.enabled", "");
        if (!override.isBlank()) {
            return Boolean.parseBoolean(override);
        }
        // If an admin provides gateway credentials through environment variables
        // or JVM properties, enable the remote bridge automatically even when the
        // generated TOML still has remoteBrainEnabled=false.
        return EternalEngineConfig.SERVER.remoteBrainEnabled.get()
            || !overrideString("EE_LITELLM_ENDPOINT", "ee.brain.litellm.endpoint", "").isBlank()
            || !overrideString("EE_PORTKEY_API_KEY", "ee.brain.portkey.apiKey", "").isBlank();
    }

    private boolean hasAnyRemoteGatewayConfigured() {
        return !liteLlmEndpointResolved().isBlank()
            || !portkeyApiKeyResolved().isBlank();
    }

    private RemoteBrainBridge createRemoteBrainBridge() {
        String portkeyApiKey = portkeyApiKeyResolved();
        return new RemoteBrainBridge(
            liteLlmEndpointResolved(),
            liteLlmModelResolved(),
            liteLlmTokenResolved(),
            liteLlmExtraHeadersResolved(),
            portkeyApiKey.isBlank() ? "" : portkeyEndpointResolved(),
            portkeyModelResolved(),
            portkeyProviderTokenResolved(),
            buildPortkeyExtraHeaders(portkeyApiKey),
            remoteBrainTimeoutMsResolved());
    }

    private String buildPortkeyExtraHeaders(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("x-portkey-api-key=").append(apiKey.trim());
        String provider = portkeyProviderResolved();
        if (provider != null && !provider.isBlank()) {
            sb.append(";x-portkey-provider=").append(provider.trim());
        }
        String config = portkeyConfigResolved();
        if (config != null && !config.isBlank()) {
            sb.append(";x-portkey-config=").append(config.trim());
        }
        return sb.toString();
    }

    private String liteLlmEndpointResolved() {
        return overrideString("EE_LITELLM_ENDPOINT", "ee.brain.litellm.endpoint",
            EternalEngineConfig.SERVER.liteLlmEndpoint.get());
    }

    private String liteLlmModelResolved() {
        return overrideString("EE_LITELLM_MODEL", "ee.brain.litellm.model",
            EternalEngineConfig.SERVER.liteLlmModel.get());
    }

    private String liteLlmTokenResolved() {
        return overrideString("EE_LITELLM_TOKEN", "ee.brain.litellm.token",
            EternalEngineConfig.SERVER.liteLlmToken.get());
    }

    private String liteLlmExtraHeadersResolved() {
        return overrideString("EE_LITELLM_EXTRA_HEADERS", "ee.brain.litellm.extraHeaders",
            EternalEngineConfig.SERVER.liteLlmExtraHeaders.get());
    }

    private String portkeyEndpointResolved() {
        return overrideString("EE_PORTKEY_ENDPOINT", "ee.brain.portkey.endpoint",
            EternalEngineConfig.SERVER.portkeyEndpoint.get());
    }

    private String portkeyModelResolved() {
        return overrideString("EE_PORTKEY_MODEL", "ee.brain.portkey.model",
            EternalEngineConfig.SERVER.portkeyModel.get());
    }

    private String portkeyProviderTokenResolved() {
        return overrideString("EE_PORTKEY_PROVIDER_TOKEN", "ee.brain.portkey.providerToken",
            EternalEngineConfig.SERVER.portkeyProviderToken.get());
    }

    private String portkeyApiKeyResolved() {
        return overrideString("EE_PORTKEY_API_KEY", "ee.brain.portkey.apiKey",
            EternalEngineConfig.SERVER.portkeyApiKey.get());
    }

    private String portkeyProviderResolved() {
        return overrideString("EE_PORTKEY_PROVIDER", "ee.brain.portkey.provider",
            EternalEngineConfig.SERVER.portkeyProvider.get());
    }

    private String portkeyConfigResolved() {
        return overrideString("EE_PORTKEY_CONFIG", "ee.brain.portkey.config",
            EternalEngineConfig.SERVER.portkeyConfig.get());
    }

    private int remoteBrainTimeoutMsResolved() {
        return overrideInt("EE_BRAIN_TIMEOUT_MS", "ee.brain.timeoutMs",
            EternalEngineConfig.SERVER.remoteBrainTimeoutMs.get());
    }

    private static String overrideString(String envName, String propertyName, String fallback) {
        String prop = System.getProperty(propertyName);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static int overrideInt(String envName, String propertyName, int fallback) {
        String value = overrideString(envName, propertyName, "");
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
    
    /**
     * Initialize the simulation engine
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[SimulationEngine] Already initialized");
            return;
        }
        
        instance = new SimulationEngine();
        initialized = true;
        
        LOGGER.info("[SimulationEngine] Initialized");
    }
    
    /**
     * Get the singleton instance
     */
    public static SimulationEngine getInstance() {
        if (!initialized) {
            throw new IllegalStateException("SimulationEngine not initialized!");
        }
        return instance;
    }
    
    /**
     * Check if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Start the simulation
     */
    public void start(MinecraftServer server) {
        if (running.get()) {
            LOGGER.warn("[SimulationEngine] Already running");
            return;
        }
        
        this.server = server;
        
        // Load world state if not already loaded
        WorldStateManager.getInstance().loadWorldState(server);
        
        // Start clock
        SimulationClockManager.getInstance().start();
        
        running.set(true);
        paused.set(false);

        if (EternalEngineConfig.SERVER.webConsoleEnabled.get()) {
            webConsoleService.start(server);
        }
        
        LOGGER.info("[SimulationEngine] Simulation started at year {}", 
            SimulationClockManager.getInstance().getCurrentYear());
    }
    
    /**
     * Stop the simulation
     */
    public void stop() {
        if (!running.get()) {
            return;
        }
        
        LOGGER.info("[SimulationEngine] Stopping simulation...");
        
        running.set(false);
        
        // Stop clock
        SimulationClockManager.getInstance().stop();
        
        // Save state
        WorldStateManager.getInstance().saveWorldState();

        // Stop local developer web console before shutting down the shared brain.
        webConsoleService.stop();

        // Shutdown external/non-world brain bridge if present.
        brainBridge.shutdown();
        
        LOGGER.info("[SimulationEngine] Simulation stopped");
    }
    
    /**
     * Pause the simulation
     */
    public void pause() {
        if (!running.get() || paused.get()) {
            return;
        }
        
        paused.set(true);
        SimulationClockManager.getInstance().pause();
        LOGGER.info("[SimulationEngine] Simulation paused");
    }
    
    /**
     * Resume the simulation
     */
    public void resume() {
        if (!running.get() || !paused.get()) {
            return;
        }
        
        paused.set(false);
        SimulationClockManager.getInstance().resume();
        LOGGER.info("[SimulationEngine] Simulation resumed");
    }
    
    /**
     * Server tick handler - called every tick (20 times per second)
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // Only process at END phase to ensure all vanilla processing is done
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        if (!running.get() || paused.get()) {
            return;
        }
        
        if (!EternalEngineConfig.COMMON.simulationEnabled.get()) {
            return;
        }
        
        lastTickStartTime = System.nanoTime();
        
        try {
            processTick();
        } catch (Exception e) {
            LOGGER.error("[SimulationEngine] Error during tick processing!", e);
        }
        
        // Performance tracking
        long tickTime = (System.nanoTime() - lastTickStartTime) / 1_000_000; // Convert to ms
        totalTickTimeMs += tickTime;
        ticksThisSecond++;
        
        // Log performance every second
        if (System.currentTimeMillis() - secondStartTime >= 1000) {
            if (EternalEngineConfig.COMMON.performanceLogging.get()) {
                double avgTickTime = (double) totalTickTimeMs / ticksThisSecond;
                LOGGER.debug("[SimulationEngine] Avg tick time: {}ms ({} ticks)", 
                    String.format("%.2f", avgTickTime), ticksThisSecond);
            }
            
            // Check if we're over budget
            int maxBudget = EternalEngineConfig.SERVER.maxTickBudgetMs.get();
            if (totalTickTimeMs / ticksThisSecond > maxBudget) {
                LOGGER.warn("[SimulationEngine] Tick budget exceeded! Avg: {}ms, Budget: {}ms",
                    totalTickTimeMs / ticksThisSecond, maxBudget);
                PerformanceMonitor.getInstance().recordBudgetExceeded();
            }
            
            // Reset counters
            totalTickTimeMs = 0;
            ticksThisSecond = 0;
            secondStartTime = System.currentTimeMillis();
        }
        
        tickCounter.incrementAndGet();
    }
    
    /**
     * Process a single simulation tick
     */
    private void processTick() {
        long tick = tickCounter.get();
        
        // Get the scheduler to determine which systems to update this tick
        SimulationTickScheduler scheduler = SimulationTickScheduler.getInstance();
        
        // Update clock (handles year progression)
        if (scheduler.shouldUpdate("clock", tick)) {
            SimulationClockManager.getInstance().tick();
        }
        
        // Process async task callbacks on main thread
        AsyncTaskManager.getInstance().processMainThreadCallbacks();

        // Drain validated external-brain decisions. Phase 1A only audits the
        // routing boundary; later systems consume these decisions explicitly.
        processValidatedBrainDecisions();
        
        // Update systems based on their schedules
        // These will be populated as we implement each system
        
        if (scheduler.shouldUpdate("war", tick) && EternalEngineConfig.COMMON.enableWar.get()) {
            warEngine.tick(SimulationClockManager.getInstance().getCurrentYear(), WorldStateManager.getInstance());
        }
        
        if (scheduler.shouldUpdate("disease", tick) && EternalEngineConfig.COMMON.enableDisease.get()) {
            diseaseEngine.tick(SimulationClockManager.getInstance().getCurrentYear(), WorldStateManager.getInstance());
        }
        
        if (scheduler.shouldUpdate("economy", tick) && EternalEngineConfig.COMMON.enableTrade.get()) {
            economyEngine.tick(SimulationClockManager.getInstance().getCurrentYear(), WorldStateManager.getInstance());
        }
        
        if (scheduler.shouldUpdate("trade", tick) && EternalEngineConfig.COMMON.enableTrade.get()) {
            tradeRouteEngine.tick(SimulationClockManager.getInstance().getCurrentYear(), WorldStateManager.getInstance(), economyEngine);
        }

        if (scheduler.shouldUpdate("population", tick) && EternalEngineConfig.COMMON.enablePopulation.get()) {
            int year = SimulationClockManager.getInstance().getCurrentYear();
            populationEngine.tick(year, WorldStateManager.getInstance());
            migrationSystem.processMigration(year, WorldStateManager.getInstance());
            for (String civId : WorldStateManager.getInstance().getAllCivilizationIds()) {
                classSystem.updateDistribution(civId, year, year >= 1750 ? "INDUSTRIAL" : "PRE_INDUSTRIAL");
                urbanizationSystem.updateUrbanization(civId, year, WorldStateManager.getInstance());
            }
        }

        if (scheduler.shouldUpdate("agriculture", tick)) {
            agricultureEngine.tick(SimulationClockManager.getInstance().getCurrentYear(), WorldStateManager.getInstance());
        }
        
        if (scheduler.shouldUpdate("religion", tick) && EternalEngineConfig.COMMON.enableReligion.get()) {
            religionEngine.tick(SimulationClockManager.getInstance().getCurrentYear(), WorldStateManager.getInstance());
        }
        
        if (scheduler.shouldUpdate("technology", tick) && EternalEngineConfig.COMMON.enableTechnology.get()) {
            technologyEngine.tick(SimulationClockManager.getInstance().getCurrentYear(), WorldStateManager.getInstance());
        }
        
        if (scheduler.shouldUpdate("climate", tick) && EternalEngineConfig.COMMON.enableClimate.get()) {
            climateEngine.tick(SimulationClockManager.getInstance().getCurrentYear(), WorldStateManager.getInstance(), agricultureEngine);
        }
        
        if (scheduler.shouldUpdate("reputation", tick) && EternalEngineConfig.COMMON.enableReputation.get()) {
            int year = SimulationClockManager.getInstance().getCurrentYear();
            vipSystem.tick(year);
            villagerManager.virtualizer().tick(WorldStateManager.getInstance(), year);
        }
        
        if (scheduler.shouldUpdate("era", tick)) {
            // EraSystem.getInstance().tick();
        }
        
        // Process building/world block updates through a safe main-thread queue.
        if (EternalEngineConfig.COMMON.enableBuildings.get()) {
            blockUpdateQueue.tick(server, EternalEngineConfig.SERVER.blockUpdateBudgetPerTick.get());
        }
        
        if (scheduler.shouldUpdate("events", tick)) {
            int year = SimulationClockManager.getInstance().getCurrentYear();
            eventChainEngine.tick(year, WorldStateManager.getInstance());
        }

        if (scheduler.shouldUpdate("autosave", tick)) {
            WorldStateManager.getInstance().checkAutoSave();
        }
        
        // Record tick performance
        PerformanceMonitor.getInstance().recordTick(tick, System.nanoTime() - lastTickStartTime);
    }

    private void processValidatedBrainDecisions() {
        var decisions = serverDecisionQueue.drain(8);
        if (decisions.isEmpty()) {
            return;
        }
        for (var decision : decisions) {
            LOGGER.debug("[SimulationEngine] Queued brain decision awaiting subsystem handler: {}",
                decision.validation().normalizedAction());
        }
    }
    
    /**
     * Check if simulation is running
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Check if simulation is paused
     */
    public boolean isPaused() {
        return paused.get();
    }
    
    /**
     * Get current tick count
     */
    public long getTickCount() {
        return tickCounter.get();
    }
    
    /**
     * Get the server instance
     */
    @Nullable
    public MinecraftServer getServer() {
        return server;
    }

    public AnomalyDetectionEngine getAnomalyDetectionEngine() {
        return anomalyDetectionEngine;
    }

    public IncidentCreatorEngine getIncidentCreatorEngine() {
        return incidentCreatorEngine;
    }

    public AgricultureEngine getAgricultureEngine() {
        return agricultureEngine;
    }

    public ClimateEngine getClimateEngine() {
        return climateEngine;
    }

    public CivilizationManager getCivilizationManager() {
        return civilizationManager;
    }

    public FactionRelationshipSystem getFactionRelationshipSystem() {
        return factionRelationshipSystem;
    }

    public PopulationEngine getPopulationEngine() {
        return populationEngine;
    }

    public MigrationSystem getMigrationSystem() {
        return migrationSystem;
    }

    public ClassSystem getClassSystem() {
        return classSystem;
    }

    public UrbanizationSystem getUrbanizationSystem() {
        return urbanizationSystem;
    }

    public ReligionEngine getReligionEngine() {
        return religionEngine;
    }

    public TechnologyEngine getTechnologyEngine() {
        return technologyEngine;
    }

    public ReputationEngine getReputationEngine() {
        return reputationEngine;
    }

    public AuthoritySystem getAuthoritySystem() {
        return authoritySystem;
    }

    public VipCommandSystem getVipCommandSystem() {
        return vipCommandSystem;
    }

    public VipSystem getVipSystem() {
        return vipSystem;
    }

    public VipTakeoverSystem getVipTakeoverSystem() {
        return vipTakeoverSystem;
    }

    public VillagerManager getVillagerManager() {
        return villagerManager;
    }

    public VillagerBehaviorSystem getVillagerBehaviorSystem() {
        return villagerBehaviorSystem;
    }

    public CurrentAffairsEngine getCurrentAffairsEngine() {
        return currentAffairsEngine;
    }

    public BotCurrentAffairsService getBotCurrentAffairsService() {
        return botCurrentAffairsService;
    }

    public MindcraftBridge getMindcraftBridge() {
        return mindcraftBridge;
    }

    public BuildingHistoryDatabase getBuildingHistoryDatabase() {
        return buildingHistoryDatabase;
    }

    public BuildingDateInferenceEngine getBuildingDateInferenceEngine() {
        return buildingDateInferenceEngine;
    }

    public BlockUpdateQueue getBlockUpdateQueue() {
        return blockUpdateQueue;
    }

    public VehicleEngine getVehicleEngine() {
        return vehicleEngine;
    }

    public TradeRouteEngine getTradeRouteEngine() {
        return tradeRouteEngine;
    }

    public EconomyEngine getEconomyEngine() {
        return economyEngine;
    }

    public TimelineManager getTimelineManager() {
        return timelineManager;
    }

    public HistoricalEventDatabase getHistoricalEventDatabase() {
        return historicalEventDatabase;
    }

    public EventChainEngine getEventChainEngine() {
        return eventChainEngine;
    }

    public WarEngine getWarEngine() {
        return warEngine;
    }

    public DiseaseEngine getDiseaseEngine() {
        return diseaseEngine;
    }

    public ServerDecisionQueue getServerDecisionQueue() {
        return serverDecisionQueue;
    }

    public BrainAuditLog getBrainAuditLog() {
        return brainAuditLog;
    }

    public LlmBrainBridge getBrainBridge() {
        return brainBridge;
    }

    public String getBrainConfigSummary() {
        return "Brain configuration:\n"
            + "  activeBridge=" + brainBridge.getClass().getSimpleName() + "\n"
            + "  remoteEnabledResolved=" + isRemoteBrainEnabledResolved() + "\n"
            + "  LiteLLM primary configured=" + !liteLlmEndpointResolved().isBlank() + "\n"
            + "  LiteLLM model=" + liteLlmModelResolved() + "\n"
            + "  LiteLLM token configured=" + !liteLlmTokenResolved().isBlank() + "\n"
            + "  LiteLLM extra headers configured=" + !liteLlmExtraHeadersResolved().isBlank() + "\n"
            + "  Portkey secondary configured=" + !portkeyApiKeyResolved().isBlank() + "\n"
            + "  Portkey model=" + portkeyModelResolved() + "\n"
            + "  Portkey provider=" + portkeyProviderResolved() + "\n"
            + "  Portkey provider token configured=" + !portkeyProviderTokenResolved().isBlank() + "\n"
            + "  Portkey config configured=" + !portkeyConfigResolved().isBlank() + "\n"
            + "  timeoutMs=" + remoteBrainTimeoutMsResolved() + "\n"
            + "  env/JVM override examples: EE_LITELLM_ENDPOINT or -Dee.brain.litellm.endpoint=...";
    }

    public synchronized LlmBrainBridge enableRemoteBrainBridge() {
        LlmBrainBridge oldBridge = this.brainBridge;
        if (oldBridge != null) {
            oldBridge.shutdown();
        }
        this.brainBridge = createRemoteBrainBridge();
        LOGGER.info("[SimulationEngine] Runtime remote brain bridge enabled LiteLLM={} Portkey={} primaryModel={} secondaryModel={}",
            !liteLlmEndpointResolved().isBlank(),
            !portkeyApiKeyResolved().isBlank(),
            liteLlmModelResolved(),
            portkeyModelResolved());
        return this.brainBridge;
    }

    public WebConsoleService getWebConsoleService() {
        return webConsoleService;
    }
    
    /**
     * Force a state save
     */
    public void forceSave() {
        WorldStateManager.getInstance().saveWorldState();
    }
    
    /**
     * Get simulation status summary
     */
    public String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Eternal Engine Status:\n");
        sb.append("  Running: ").append(running.get()).append("\n");
        sb.append("  Paused: ").append(paused.get()).append("\n");
        sb.append("  Tick Count: ").append(tickCounter.get()).append("\n");
        
        if (SimulationClockManager.isInitialized()) {
            sb.append("  Year: ").append(SimulationClockManager.getInstance().getCurrentYear()).append("\n");
            sb.append("  Speed: ").append(SimulationClockManager.getInstance().getEffectiveSpeed()).append("x\n");
        }
        sb.append("  Timeline Branch: ").append(timelineManager.activeBranchId()).append("\n");
        sb.append("  Historical Events: ").append(historicalEventDatabase.getSize()).append("\n");
        sb.append("  Triggered Events: ").append(eventChainEngine.getTriggeredEvents().size()).append("\n");
        sb.append("  Prevented Events: ").append(eventChainEngine.getPreventedEvents().size()).append("\n");
        sb.append("  Active Wars: ").append(warEngine.getActiveWars().size()).append("\n");
        sb.append("  Virtual Armies: ").append(warEngine.armyManager().size()).append("\n");
        sb.append("  Active Disease Outbreaks: ").append(diseaseEngine.getActiveDiseases().size()).append("\n");
        sb.append("  Active Trade Routes: ").append(tradeRouteEngine.getActiveRoutes().size()).append("\n");
        sb.append("  Active Famines: ").append(agricultureEngine.famineSystem().activeFamineRegions().size()).append("\n");
        sb.append("  Active Climate Events: ").append(climateEngine.getActiveClimateEvents(SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getCurrentYear() : 0).size()).append("\n");
        sb.append("  Civilizations: ").append(civilizationManager.ids().size()).append("\n");
        sb.append("  Territories: ").append(civilizationManager.territoryManager().size()).append("\n");
        sb.append("  VIP Slots: ").append(vipSystem.allVips().size()).append("\n");
        sb.append("  Virtual Villagers: ").append(villagerManager.virtualizer().size()).append("\n");
        sb.append("  Population Regions: ").append(populationEngine.regionPopulations().size()).append("\n");
        sb.append("  Migration Flows: ").append(migrationSystem.flows().size()).append("\n");
        sb.append("  Religions: ").append(religionEngine.allReligions().size()).append("\n");
        sb.append("  Technologies: ").append(technologyEngine.tree().all().size()).append("\n");
        sb.append("  Brain Available: ").append(brainBridge.isAvailable()).append("\n");
        sb.append("  Web Console: ").append(webConsoleService.isRunning() ? "running on localhost:" + webConsoleService.port() : "stopped").append("\n");
        sb.append("  Mindcraft Bots: ").append(mindcraftBridge.connectedCount()).append("\n");
        sb.append("  Bot Briefings: ").append(botCurrentAffairsService.cachedBriefingCount()).append("\n");
        sb.append("  Buildings: ").append(buildingHistoryDatabase.size()).append("\n");
        sb.append("  Block Queue: ").append(blockUpdateQueue.size()).append("\n");
        sb.append("  Brain Decision Queue: ").append(serverDecisionQueue.size()).append("\n");
        sb.append("  Async: ").append(AsyncTaskManager.getInstance().getStatistics()).append("\n");
        
        return sb.toString();
    }
}
