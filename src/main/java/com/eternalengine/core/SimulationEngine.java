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
import com.eternalengine.buildings.CivilizationGrammarEngine;
import com.eternalengine.buildings.HistoricalLandmarkGenerator;
import com.eternalengine.buildings.TemporalMorphController;
import com.eternalengine.carto.AmbientCrowdSoundManager;
import com.eternalengine.carto.CartographicOverlayManager;
import com.eternalengine.carto.HeraldryRegistry;
import com.eternalengine.carto.HistoricalAudioDirector;
import com.eternalengine.chrononexus.ChronoNexusCampaignEngine;
import com.eternalengine.civilization.CivilizationManager;
import com.eternalengine.civilization.FactionRelationshipSystem;
import com.eternalengine.climate.ClimateEngine;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.config.EternalEngineConfig;
import com.eternalengine.disease.DiseaseEngine;
import com.eternalengine.ecs.AgentVirtualizationEngine;
import com.eternalengine.history.EventChainEngine;
import com.eternalengine.history.HistoricalEventDatabase;
import com.eternalengine.incident.IncidentCreatorEngine;
import com.eternalengine.population.ClassSystem;
import com.eternalengine.population.MigrationSystem;
import com.eternalengine.population.PopulationEngine;
import com.eternalengine.population.UrbanizationSystem;
import com.eternalengine.religion.ReligionEngine;
import com.eternalengine.reputation.ReputationEngine;
import com.eternalengine.sovereign.DiplomaticTreatyEngine;
import com.eternalengine.sovereign.DynasticSuccessionEngine;
import com.eternalengine.sovereign.ImperialCommandEngine;
import com.eternalengine.sovereign.RoyalCourtManager;
import com.eternalengine.technology.TechnologyEngine;
import com.eternalengine.timeline.ScenarioRegistry;
import com.eternalengine.timeline.SiblingIndex;
import com.eternalengine.timeline.TimelineManager;
import com.eternalengine.trade.EconomyEngine;
import com.eternalengine.trade.LogisticsManager;
import com.eternalengine.trade.TradeRouteEngine;
import com.eternalengine.vehicle.VehicleEngine;
import com.eternalengine.vip.AuthoritySystem;
import com.eternalengine.vip.VipCommandSystem;
import com.eternalengine.vip.VipSystem;
import com.eternalengine.vip.VipTakeoverSystem;
import com.eternalengine.web.WebConsoleService;
import com.eternalengine.villager.PresenceLocalSpawner;
import com.eternalengine.villager.VillagerBehaviorSystem;
import com.eternalengine.villager.VillagerManager;
import com.eternalengine.war.WarEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import com.eternalengine.terrabridge.TerraTimeBridge;
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
    private final RoyalCourtManager royalCourtManager;
    private final DiplomaticTreatyEngine treatyEngine;
    private final DynasticSuccessionEngine successionEngine;
    private final ImperialCommandEngine imperialCommandEngine;
    private final VipTakeoverSystem vipTakeoverSystem;
    private final VillagerManager villagerManager;
    private final PresenceLocalSpawner presenceLocalSpawner;
    private final AgentVirtualizationEngine virtualizationEngine;
    private final VillagerBehaviorSystem villagerBehaviorSystem;
    private final CurrentAffairsEngine currentAffairsEngine;
    private final BotCurrentAffairsService botCurrentAffairsService;
    private final MindcraftBridge mindcraftBridge;
    private final BuildingHistoryDatabase buildingHistoryDatabase;
    private final BuildingDateInferenceEngine buildingDateInferenceEngine;
    private final CivilizationGrammarEngine grammarEngine;
    private final HistoricalLandmarkGenerator landmarkGenerator;
    private final TemporalMorphController temporalMorphController;
    private final BlockUpdateQueue blockUpdateQueue;
    private final VehicleEngine vehicleEngine;
    private final TradeRouteEngine tradeRouteEngine;
    private final EconomyEngine economyEngine;
    private final LogisticsManager logisticsManager;
    private final TimelineManager timelineManager;
    private final HistoricalEventDatabase historicalEventDatabase;
    private final EventChainEngine eventChainEngine;
    private final WarEngine warEngine;
    private final DiseaseEngine diseaseEngine;
    private final CartographicOverlayManager overlayManager;
    private final HistoricalAudioDirector audioDirector;
    private final AmbientCrowdSoundManager ambientCrowdManager;
    private final HeraldryRegistry heraldryRegistry;
    private final ChronoNexusCampaignEngine chronoNexusEngine;
    private final ServerDecisionQueue serverDecisionQueue;
    private final BrainAuditLog brainAuditLog;
    private volatile LlmBrainBridge brainBridge;
    private final WebConsoleService webConsoleService;
    
    // Simulation state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    // Step 14: why history is paused. An operator's pause (/ee pause) is kept in the save; the empty-server
    // pause (config pauseWhenEmpty) follows the player count.
    private final AtomicBoolean adminPaused = new AtomicBoolean(false);
    private volatile boolean emptyPaused = false;
    /** When this world's simulation started, for the single-player grace below. */
    private volatile long startedAtMillis = 0L;
    /**
     * A single-player world ticks for a moment before its own player has joined. Without this grace, every new
     * single-player session paused history for about a second ("nobody is online"), switched the day/night
     * cycle off and on, and saved (seen in the user's log, 2026-10-12). Dedicated servers pause at once, as before.
     */
    private static final long SINGLEPLAYER_JOIN_GRACE_MS = 15_000L;

    /**
     * World flag: this mod switched the day/night cycle off to stop Terra++ time for a pause. Switched back
     * on at resume, and at the next start when the pause is over (a crash cannot leave the sun standing).
     */
    public static final String DAYLIGHT_FLAG = "core.daylight_paused_by_ee";

    // Step 14: every system in its own safety net, timed (SystemRunner)
    private final SystemRunner systems = new SystemRunner();

    /** Scheduled systems that were due in a tick that had used its budget; they run first in the next one. */
    private final java.util.Set<String> deferredSystems = new java.util.LinkedHashSet<>();

    /** The world systems that run on a schedule and move history on, in the order they run. */
    private static final String[] SCHEDULED_SYSTEMS = {
        "war", "disease", "economy", "trade", "population", "agriculture", "religion", "technology", "climate",
        "reputation", "events"
    };

    /** Set by the clock's year signal: the event chain runs in the next tick instead of waiting for its turn. */
    private volatile boolean yearChangedPending = false;
    private boolean yearListenerRegistered = false;
    private long lastBudgetWarnAt = 0L;
    private long budgetWarnsSuppressed = 0L;
    
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
        this.royalCourtManager = new RoyalCourtManager();
        this.treatyEngine = new DiplomaticTreatyEngine();
        this.successionEngine = new DynasticSuccessionEngine();
        this.imperialCommandEngine = new ImperialCommandEngine();
        this.vipTakeoverSystem = new VipTakeoverSystem(this.reputationEngine);
        this.villagerManager = new VillagerManager();
        this.presenceLocalSpawner = new PresenceLocalSpawner();
        this.virtualizationEngine = new AgentVirtualizationEngine();
        this.villagerBehaviorSystem = new VillagerBehaviorSystem();
        this.currentAffairsEngine = new CurrentAffairsEngine();
        this.botCurrentAffairsService = new BotCurrentAffairsService(this.currentAffairsEngine);
        this.mindcraftBridge = new MindcraftBridge();
        this.buildingHistoryDatabase = new BuildingHistoryDatabase();
        this.buildingDateInferenceEngine = new BuildingDateInferenceEngine();
        this.grammarEngine = new CivilizationGrammarEngine();
        this.landmarkGenerator = new HistoricalLandmarkGenerator();
        this.temporalMorphController = new TemporalMorphController();
        this.blockUpdateQueue = new BlockUpdateQueue();
        this.vehicleEngine = new VehicleEngine();
        this.tradeRouteEngine = new TradeRouteEngine();
        this.economyEngine = new EconomyEngine();
        this.logisticsManager = new LogisticsManager();
        this.timelineManager = new TimelineManager();
        this.historicalEventDatabase = new HistoricalEventDatabase();
        this.eventChainEngine = new EventChainEngine(this.historicalEventDatabase, this.timelineManager);
        this.warEngine = new WarEngine();
        this.diseaseEngine = new DiseaseEngine();
        this.overlayManager = new CartographicOverlayManager();
        this.audioDirector = new HistoricalAudioDirector();
        this.ambientCrowdManager = new AmbientCrowdSoundManager();
        this.heraldryRegistry = new HeraldryRegistry();
        this.chronoNexusEngine = new ChronoNexusCampaignEngine();
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
        return EternalEngineConfig.server(EternalEngineConfig.SERVER.remoteBrainEnabled, false)
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
            EternalEngineConfig.server(EternalEngineConfig.SERVER.liteLlmEndpoint, ""));
    }

    private String liteLlmModelResolved() {
        return overrideString("EE_LITELLM_MODEL", "ee.brain.litellm.model",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.liteLlmModel, ""));
    }

    private String liteLlmTokenResolved() {
        return overrideString("EE_LITELLM_TOKEN", "ee.brain.litellm.token",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.liteLlmToken, ""));
    }

    private String liteLlmExtraHeadersResolved() {
        return overrideString("EE_LITELLM_EXTRA_HEADERS", "ee.brain.litellm.extraHeaders",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.liteLlmExtraHeaders, ""));
    }

    private String portkeyEndpointResolved() {
        return overrideString("EE_PORTKEY_ENDPOINT", "ee.brain.portkey.endpoint",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.portkeyEndpoint, ""));
    }

    private String portkeyModelResolved() {
        return overrideString("EE_PORTKEY_MODEL", "ee.brain.portkey.model",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.portkeyModel, ""));
    }

    private String portkeyProviderTokenResolved() {
        return overrideString("EE_PORTKEY_PROVIDER_TOKEN", "ee.brain.portkey.providerToken",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.portkeyProviderToken, ""));
    }

    private String portkeyApiKeyResolved() {
        return overrideString("EE_PORTKEY_API_KEY", "ee.brain.portkey.apiKey",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.portkeyApiKey, ""));
    }

    private String portkeyProviderResolved() {
        return overrideString("EE_PORTKEY_PROVIDER", "ee.brain.portkey.provider",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.portkeyProvider, ""));
    }

    private String portkeyConfigResolved() {
        return overrideString("EE_PORTKEY_CONFIG", "ee.brain.portkey.config",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.portkeyConfig, ""));
    }

    private int remoteBrainTimeoutMsResolved() {
        return overrideInt("EE_BRAIN_TIMEOUT_MS", "ee.brain.timeoutMs",
            EternalEngineConfig.server(EternalEngineConfig.SERVER.remoteBrainTimeoutMs, 10000));
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

        // Step 14: a clean slate for this world (in single player one game opens several worlds), and the
        // intervals of this world's server config, which only exists from now on.
        systems.clear();
        deferredSystems.clear();
        PerformanceMonitor.getInstance().reset();
        SimulationTickScheduler.getInstance().resetAllFrequencies();
        SimulationTickScheduler.getInstance().reloadFromConfig();

        // Load the foundation data at startup. Until now the only caller was the
        // `/ee foundation load` command, so a session where nobody typed it had no civilization
        // table at all: every occupant lookup resolved to nobody, and the 1948 hamlet answered
        // "population 0" with a single generic farmer in it. Civilizations now always load, from
        // the world folder when one is installed and from the packaged copy otherwise.
        // A world nobody ran `/ee data install` in had no authored event chain, vips, buildings or
        // stories, and the stories are only read at start (bootstrapDivergence below), so typing the
        // command later still needed a rejoin. Such a world gets the shipped copy now, exactly what the
        // command does; a world that already has its copy is left untouched (nothing overwritten).
        // Step 14: the same call also brings an installed copy up to date after a mod update, without
        // overwriting a file the server owner changed (FoundationDataInstaller.refreshAtStart).
        try {
            com.eternalengine.data.FoundationDataInstaller.refreshAtStart(server);
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] Automatic foundation data install/update failed: {}", t.getMessage());
        }

        try {
            com.eternalengine.data.FoundationDataLoader.LoadResult foundation =
                com.eternalengine.data.FoundationDataLoader.loadInstalled(server, this);
            LOGGER.info("[SimulationEngine] {}", foundation.summary());
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] Foundation data load failed: {}", t.getMessage());
        }

        // Warm the population resolver now rather than on first use. The load is a few
        // milliseconds, but a pack problem must show up in the startup log beside the others
        // instead of mid-play the first time somebody asks for a population. Pointing it at the
        // world folder also means the pack /ee foundation install wrote is the pack that loads.
        com.eternalengine.population.PopulationResolver.get().loadFromDirectory(
            com.eternalengine.data.EternalDataPaths.population(server));

        // Step 14: what the world was doing when it was saved: wars, outbreaks, trade routes, and which
        // events of the chain already happened or were stopped.
        restoreRunningWorld();

        // Rebuild divergence state. Must run after the world state is loaded, and before the
        // first tick, or a previously created branch will silently revert to prime history.
        bootstrapDivergence(server);

        // Start clock
        SimulationClockManager.getInstance().start();
        
        running.set(true);
        paused.set(false);

        // Step 14: the year signal (the event chain runs as soon as a new year begins), an operator's pause
        // from before the restart, and a day/night cycle this mod switched off for a pause that is over.
        if (!yearListenerRegistered) {
            SimulationClockManager.getInstance().addYearListener((oldYear, newYear) -> yearChangedPending = true);
            yearListenerRegistered = true;
        }
        adminPaused.set(WorldStateManager.getInstance().isAdminPaused());
        emptyPaused = false;
        startedAtMillis = System.currentTimeMillis();
        // Step 17: the history registers load on a background thread now, not on the first villager's tick.
        com.eternalengine.civilization.HistoryChecks.warmUpInBackground();
        applyPauseState(adminPaused.get() ? "an operator paused it before the restart (/ee resume)" : null);

        if (EternalEngineConfig.SERVER.webConsoleEnabled.get()) {
            webConsoleService.start(server);
        }
        
        LOGGER.info("[SimulationEngine] Simulation started at year {}{}", 
            SimulationClockManager.getInstance().getCurrentYear(), paused.get() ? " (paused)" : "");
    }

    /**
     * Step 14: the running world from the save. Each engine kept its state in memory only, so every restart
     * ended the wars, cured the outbreaks, closed the routes and replayed the event chain; in single player
     * the previous world's wars even went on in the next world. Each engine starts from the save (or from
     * nothing, for a new world).
     */
    private void restoreRunningWorld() {
        WorldStateManager wsm = WorldStateManager.getInstance();
        int wars = 0;
        int outbreaks = 0;
        int routes = 0;
        int past = 0;
        try {
            wars = warEngine.restoreFrom(wsm);
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] Wars could not be restored: {}", t.toString());
        }
        try {
            outbreaks = diseaseEngine.restoreFrom(wsm);
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] Outbreaks could not be restored: {}", t.toString());
        }
        try {
            routes = tradeRouteEngine.restoreFrom(wsm);
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] Trade routes could not be restored: {}", t.toString());
        }
        try {
            past = eventChainEngine.restoreFrom(wsm);
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] The event chain's past could not be restored: {}", t.toString());
        }
        LOGGER.info("[SimulationEngine] Running world restored: {} war(s), {} outbreak(s), {} trade route(s), "
            + "{} chain event(s) already happened or stopped", wars, outbreaks, routes, past);
    }

    /**
     * Rebuild everything divergence needs, in order, at server start.
     *
     * <ol>
     *   <li><b>Sibling index</b> — preserves access to every pin ID. Automatic proximity grouping
     *       is disabled because it can join unrelated events; verified identities are not yet wired.</li>
     *   <li><b>Scenarios</b> — authored "what if" files. Sparse by design; most pins have none, and
     *       those diverge without a written consequence.</li>
     *   <li><b>Branch restore</b> — repopulate the in-memory branch registry from the save. Without
     *       this step a player's divergence vanished on restart while the Codex still reported it
     *       as active.</li>
     *   <li><b>Overlay filter</b> — the single hook that hides suppressed pins. Because the event
     *       scene despawns itself when no event is present, hiding the pin here is enough to empty
     *       the place: no crowd, no guards, no rumour.</li>
     * </ol>
     */
    private void bootstrapDivergence(MinecraftServer server) {
        try {
            // 1. Sibling index over the whole corpus.
            java.util.List<SiblingIndex.Pin> pins = new java.util.ArrayList<>();
            for (com.eternalengine.civilization.HistoryGazetteer.Event e
                    : com.eternalengine.civilization.HistoryGazetteer.get().allEvents()) {
                pins.add(new SiblingIndex.Pin(e.id(), e.lat(), e.lon(), e.start(),
                        Math.max(e.end(), e.start() + 1)));
            }
            // Fail closed: proximity and time do not prove identity, even for local pins.
            // Existing saved suppression sets are restored separately and are not rewritten.
            SiblingIndex index = SiblingIndex.buildIndependent(pins);
            timelineManager.setSiblingIndex(index);

            // 1b. Ensure every civ id the corpus cites has a record before anything queries for
            //     an occupant. Runs after the corpus is loaded and before the first presence query.
            civilizationManager.reconcileWithCorpus(WorldStateManager.getInstance());

            // 2. Authored scenarios.
            ScenarioRegistry scenarios = new ScenarioRegistry();
            int loaded = scenarios.loadFromDirectory(
                    com.eternalengine.data.EternalDataPaths.scenarios(server));
            timelineManager.setScenarios(scenarios);

            // 3. Restore any branch that survived the save, into an empty registry (step 14: the manager
            //    outlives a world in single player, so the previous world's forks were still in it).
            timelineManager.clearBranches();
            int restored = timelineManager.restoreFrom(WorldStateManager.getInstance());

            // 4. Point the overlay at the active branch. The same call a fresh fork makes, so a
            //    restored branch and a brand-new one can never drift apart.
            timelineManager.installSuppressionOverlay();

            // The scenario pump measures from "now", so nothing already past re-fires on load.
            timelineManager.resetScenarioCursor(
                    SimulationClockManager.getInstance().getCurrentYear());

            LOGGER.info("[SimulationEngine] Divergence bootstrap: {} pins indexed, {} cluster(s), "
                            + "{} scenario(s), {} branch(es) restored, active={}",
                    index.pinCount(), index.clusterCount(), loaded, restored,
                    timelineManager.activeBranchId());

            if (!scenarios.loadErrors().isEmpty()) {
                LOGGER.warn("[SimulationEngine] Scenario load issues: {}", scenarios.loadErrors());
            }
        } catch (Exception e) {
            // Divergence must never prevent the server from starting. Falling back to prime is
            // correct and safe: no branch means faithful history.
            LOGGER.error("[SimulationEngine] Divergence bootstrap failed; continuing on prime history", e);
            com.eternalengine.civilization.HistoryGazetteer.setSuppressionFilter(null);
        }
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
        deferredSystems.clear();
        
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
     * Pause history ({@code /ee pause}). Kept in the save, so a restart does not quietly resume it.
     */
    public void pause() {
        setOperatorPause(true);
    }
    
    /**
     * Resume history ({@code /ee resume}).
     */
    public void resume() {
        setOperatorPause(false);
    }

    /**
     * An operator pauses or resumes history (step 14: these methods existed, but no command called them).
     * History is the world year and every system that moves time on (wars, disease, trade, population,
     * farming, religion, technology, climate, reputation, the event chain, armies, caravans and contagion).
     * The world around the players goes on: people still appear, sounds play, building work finishes.
     *
     * @return false when the simulation is not running
     */
    public boolean setOperatorPause(boolean on) {
        if (!running.get()) {
            return false;
        }
        adminPaused.set(on);
        WorldStateManager.getInstance().setAdminPaused(on);
        applyPauseState(on ? "an operator paused it (/ee resume)" : "an operator resumed it");
        return true;
    }

    public boolean isOperatorPaused() {
        return adminPaused.get();
    }

    public boolean isEmptyPaused() {
        return emptyPaused;
    }

    /** Why history stands still, in a few words; null when it runs. */
    @Nullable
    public String pauseReason() {
        if (!paused.get()) {
            return null;
        }
        if (adminPaused.get()) {
            return "paused by an operator (/ee resume to go on)";
        }
        if (emptyPaused) {
            return "paused because nobody is online";
        }
        return "paused";
    }

    /**
     * Once a second (also while paused): history stops while nobody is online, if the config says so
     * (pauseWhenEmpty, on by default), and goes on when somebody joins.
     */
    private void checkEmptyServer() {
        boolean empty;
        try {
            empty = EternalEngineConfig.COMMON.pauseWhenEmpty.get() && server != null && server.getPlayerCount() == 0
                    && (server.isDedicatedServer()
                            || System.currentTimeMillis() - startedAtMillis > SINGLEPLAYER_JOIN_GRACE_MS);
        } catch (Throwable t) {
            return;
        }
        if (empty != emptyPaused) {
            emptyPaused = empty;
            applyPauseState(empty ? "nobody is online" : "a player is online");
        }
    }

    /** Make the clock and the day/night cycle agree with the two pause reasons. */
    private void applyPauseState(@Nullable String why) {
        boolean want = adminPaused.get() || emptyPaused;
        boolean was = paused.getAndSet(want);
        if (SimulationClockManager.isInitialized()) {
            if (want) {
                SimulationClockManager.getInstance().pause();
            } else {
                SimulationClockManager.getInstance().resume();
            }
        }
        reconcileTerraDays(want);
        if (was != want) {
            LOGGER.info("[SimulationEngine] History {}{}", want ? "paused" : "running again",
                why == null ? "" : ": " + why);
        }
    }

    /**
     * The date follows the Minecraft day count with both clocks (Terra++ time since step 14, the Eternal Engine
     * clock since step 18d), so the only way to stop it is to stop the days: the sun and the date stop together.
     * The day/night cycle is switched off only when it was on, the world flag {@link #DAYLIGHT_FLAG}
     * remembers it (saved at once), and only this mod's own switch-off is ever undone.
     */
    private void reconcileTerraDays(boolean historyPaused) {
        try {
            ServerLevel level = server == null ? null : server.overworld();
            if (level == null) {
                return;
            }
            WorldStateManager wsm = WorldStateManager.getInstance();
            GameRules.BooleanValue daylight = level.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
            if (historyPaused) {
                if (daylight.get()) {
                    daylight.set(false, server);
                    wsm.setFlag(DAYLIGHT_FLAG, "true");
                    wsm.saveWorldState();
                    LOGGER.info("[SimulationEngine] The day/night cycle is paused with history (the date follows the"
                        + " Minecraft days)");
                }
            } else if (wsm.hasFlag(DAYLIGHT_FLAG)) {
                daylight.set(true, server);
                wsm.removeFlag(DAYLIGHT_FLAG);
                LOGGER.info("[SimulationEngine] The day/night cycle this mod paused is switched back on");
            }
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] Could not change the day/night cycle for the pause: {}", t.toString());
        }
    }

    /**
     * {@code /ee reset confirm} (step 14; the command used to print "FULL RESET EXECUTED" and do nothing).
     * This world's simulation starts over: the year and speed from the config, prime history only (no
     * branches), no wars, outbreaks, trade routes or world flags, the civilizations as the data describes
     * them. The Minecraft world itself (blocks, buildings, items, players) and the players' VIP claims are not
     * changed. Systems that keep
     * a memory of their own that is not saved (armies, caravans, courts, treaties, reputation) start over at
     * the next server restart.
     *
     * @return what was reset, in one line; null when the simulation is not running
     */
    @Nullable
    public String resetSimulation() {
        if (!running.get() || server == null) {
            return null;
        }
        WorldStateManager wsm = WorldStateManager.getInstance();
        int wars = warEngine.getActiveWars().size();
        int outbreaks = diseaseEngine.getActiveDiseases().size();
        int routes = tradeRouteEngine.getActiveRoutes().size();
        int branches = Math.max(0, wsm.getAllTimelineBranches().size() - 1);
        int flags = wsm.getFlagCount();
        boolean daylightPausedByUs = wsm.hasFlag(DAYLIGHT_FLAG);

        wsm.resetToDefaults();
        if (daylightPausedByUs) {
            wsm.setFlag(DAYLIGHT_FLAG, "true"); // so that the resume below switches the day/night cycle back on
        }
        restoreRunningWorld(); // from the empty state: no wars, outbreaks, routes, past events
        timelineManager.clearBranches();
        timelineManager.restoreFrom(wsm);
        timelineManager.installSuppressionOverlay();
        try {
            // Not the VIP files: reading them again would drop the claims players hold (kept by a reset).
            com.eternalengine.data.FoundationDataLoader.loadInstalled(server, this, false);
            civilizationManager.reconcileWithCorpus(wsm);
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] Reset: the civilizations could not be reloaded: {}", t.toString());
        }
        SimulationClockManager clock = SimulationClockManager.getInstance();
        int speed = EternalEngineConfig.COMMON.defaultSpeed.get();
        if (!clock.setBaseSpeed(speed)) {
            clock.setBaseSpeed(10);
        }
        clock.setCurrentYear(wsm.getCurrentYear());
        timelineManager.resetScenarioCursor(clock.getCurrentYear());
        adminPaused.set(false);
        applyPauseState("the simulation was reset");
        systems.clear();
        deferredSystems.clear();
        wsm.saveWorldState();
        // Step 19b: history starts over, and so do the purses (everyone gets new start money) and the merchants' days.
        int purses = 0;
        try {
            purses = com.eternalengine.money.PlayerPurses.get(server).resetAll();
            com.eternalengine.money.MarketLedger.get(server).clear();
        } catch (Throwable t) {
            LOGGER.warn("[SimulationEngine] Reset: the purses could not be emptied: {}", t.toString());
        }
        String summary = "year " + clock.getCurrentYear() + ", speed " + clock.getBaseSpeed() + "x; removed " + wars
            + " war(s), " + outbreaks + " outbreak(s), " + routes + " trade route(s), " + branches
            + " timeline branch(es) and " + flags + " world flag(s); emptied " + purses + " purse(s)";
        LOGGER.warn("[SimulationEngine] Simulation reset: {}", summary);
        return summary;
    }

    /** Every system that failed since this world started (step 14). */
    public SystemRunner systems() {
        return systems;
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
        
        if (!running.get()) {
            return;
        }
        
        if (!EternalEngineConfig.COMMON.simulationEnabled.get()) {
            return;
        }

        // Step 14: history stops while nobody is online (checked once a second, also while paused). A
        // paused tick still runs the world around the players; see processTick.
        if (tickCounter.get() % 20 == 0) {
            checkEmptyServer();
        }

        // Board 17: the year moves on, and the people already standing in the world were drawn under
        // whichever rules held then. Once a minute every loaded person is asked again whose they are,
        // so nobody is left standing after their civilization fell (see WhoBelongsHere).
        if (tickCounter.get() % 1200 == 0) {
            try {
                com.eternalengine.civilization.WhoBelongsHere.reconcileLoaded(event.getServer());
            } catch (Throwable ignored) {
                // never let bookkeeping stop the tick
            }
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
            // Step 18e: which system took longest in this second, so the warning names it.
            String slowest = systems.takeSlowest();
            if (totalTickTimeMs / ticksThisSecond > maxBudget) {
                PerformanceMonitor.getInstance().recordBudgetExceeded();
                // At most one warning a minute (it used to be one every second while the server was busy).
                long now = System.currentTimeMillis();
                if (now - lastBudgetWarnAt >= 60_000L) {
                    LOGGER.warn("[SimulationEngine] Tick budget exceeded! Avg: {}ms, Budget: {}ms{}{} (see /ee performance)",
                        totalTickTimeMs / ticksThisSecond, maxBudget,
                        budgetWarnsSuppressed > 0 ? ", " + budgetWarnsSuppressed + " more second(s) over budget in the last minute" : "",
                        slowest == null ? "" : "; slowest in one tick: " + slowest);
                    lastBudgetWarnAt = now;
                    budgetWarnsSuppressed = 0L;
                } else {
                    budgetWarnsSuppressed++;
                }
            }
            
            // Reset counters
            totalTickTimeMs = 0;
            ticksThisSecond = 0;
            secondStartTime = System.currentTimeMillis();
        }
        
        tickCounter.incrementAndGet();
    }
    
    /**
     * Process a single simulation tick.
     *
     * <p>Step 14: every system runs in its own safety net ({@link SystemRunner}) and is timed, so one failing
     * system costs only its own run. History (the clock and the systems that move time on) runs only when not
     * paused; the world around the players (people appearing, sounds, overlays, building work) and the
     * autosave always run. A scheduled system that is due after this tick has already used maxTickBudgetMs
     * waits for the next tick (at least one scheduled system runs per tick).
     */
    private void processTick() {
        long tick = tickCounter.get();
        SimulationTickScheduler scheduler = SimulationTickScheduler.getInstance();
        WorldStateManager wsm = WorldStateManager.getInstance();
        ServerLevel overworld = server == null ? null : server.overworld();

        // Process async task callbacks on main thread
        systems.run("async-callbacks", () -> AsyncTaskManager.getInstance().processMainThreadCallbacks());

        // Drain validated external-brain decisions. Phase 1A only audits the
        // routing boundary; later systems consume these decisions explicitly.
        systems.run("brain-decisions", this::processValidatedBrainDecisions);

        // Update clock (handles year progression; it stands still by itself while paused, but still tells
        // its listeners about a year an operator set)
        if (scheduler.shouldUpdate("clock", tick)) {
            systems.run("clock", () -> SimulationClockManager.getInstance().tick());
        }

        if (!paused.get()) {
            runScheduledSystems(tick, scheduler, wsm);

            if (overworld != null) {
                // Physicalized warfare: tick military cohorts and active sieges in overworld
                if (EternalEngineConfig.COMMON.enableWar.get()) {
                    systems.run("war-field", () -> warEngine.tickLevel(overworld, blockUpdateQueue));
                }
                // Physicalized trade logistics: tick overland caravans, maritime convoys, and ambushes
                systems.run("logistics", () -> logisticsManager.tick(overworld, tradeRouteEngine, economyEngine));
                // Physicalized epidemics: contagion transmission, quarantine checkpoints, and plague doctors
                if (EternalEngineConfig.COMMON.enableDisease.get()) {
                    systems.run("disease-field", () -> diseaseEngine.tickLevel(overworld, currentYear()));
                }
            }
        }

        int year = currentYear();

        // Process building/world block updates through a safe main-thread queue.
        if (EternalEngineConfig.COMMON.enableBuildings.get()) {
            systems.run("blocks", () -> blockUpdateQueue.tick(server, EternalEngineConfig.SERVER.blockUpdateBudgetPerTick.get()));
        }

        systems.run("morph", temporalMorphController::tick);

        // The world around the players: rifts and timeline echoes, entity virtualisation, the people of the
        // place and year, map overlays, crowd sounds, the Chrono Nexus campaign.
        if (overworld != null) {
            systems.run("rifts", () -> timelineManager.getRiftController().tick(overworld));
            systems.run("virtualization", () -> virtualizationEngine.tick(overworld, year));
            systems.run("spawner", () -> presenceLocalSpawner.tick(overworld, year, villagerManager, civilizationManager));
            // Step 20: a held tracker compass says how far and which way.
            systems.run("compass", () -> com.eternalengine.knowledge.BookService.tickCompasses(server));
            systems.run("overlays", () -> overlayManager.tick(overworld, wsm, warEngine, logisticsManager, diseaseEngine));
            systems.run("crowd-sound", () -> ambientCrowdManager.tick(overworld));
            systems.run("chrono", () -> chronoNexusEngine.tick(overworld));
        }

        if (scheduler.shouldUpdate("autosave", tick)) {
            systems.run("autosave", wsm::checkAutoSave);
        }

        if (scheduler.shouldUpdate("performance", tick)) {
            systems.run("performance", () -> PerformanceMonitor.getInstance().evaluate());
        }
        
        // Record tick performance
        PerformanceMonitor.getInstance().recordTick(tick, System.nanoTime() - lastTickStartTime);
    }

    private int currentYear() {
        return SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getCurrentYear() : 0;
    }

    /**
     * The scheduled world systems: first those that waited since an earlier tick, then those due now (the
     * event chain also when a new year began). Once this tick has used maxTickBudgetMs, the rest wait for the
     * next tick.
     */
    private void runScheduledSystems(long tick, SimulationTickScheduler scheduler, WorldStateManager wsm) {
        long budgetNanos = PerformanceMonitor.tickBudgetMs() * 1_000_000L;
        java.util.List<String> order = new java.util.ArrayList<>(deferredSystems);
        for (String id : SCHEDULED_SYSTEMS) {
            if (deferredSystems.contains(id)) {
                continue;
            }
            if (scheduler.shouldUpdate(id, tick) || ("events".equals(id) && yearChangedPending)) {
                order.add(id);
            }
        }
        int ran = 0;
        for (String id : order) {
            Runnable body = scheduledBody(id, wsm);
            if (body == null) {
                deferredSystems.remove(id); // switched off in the config
                continue;
            }
            if (ran > 0 && System.nanoTime() - lastTickStartTime > budgetNanos) {
                if (deferredSystems.add(id)) {
                    PerformanceMonitor.getInstance().recordDeferred(id);
                }
                continue;
            }
            deferredSystems.remove(id);
            if ("events".equals(id)) {
                yearChangedPending = false;
            }
            systems.run(id, body);
            ran++;
        }
    }

    /** The work of one scheduled system, or null when the config switches it off. */
    @Nullable
    private Runnable scheduledBody(String id, WorldStateManager wsm) {
        return switch (id) {
            case "war" -> EternalEngineConfig.COMMON.enableWar.get()
                ? () -> warEngine.tick(currentYear(), wsm) : null;
            case "disease" -> EternalEngineConfig.COMMON.enableDisease.get()
                ? () -> diseaseEngine.tick(currentYear(), wsm) : null;
            case "economy" -> EternalEngineConfig.COMMON.enableTrade.get()
                ? () -> economyEngine.tick(currentYear(), wsm) : null;
            case "trade" -> EternalEngineConfig.COMMON.enableTrade.get()
                ? () -> tradeRouteEngine.tick(currentYear(), wsm, economyEngine) : null;
            case "population" -> EternalEngineConfig.COMMON.enablePopulation.get() ? () -> {
                int y = currentYear();
                populationEngine.tick(y, wsm);
                migrationSystem.processMigration(y, wsm);
                for (String civId : wsm.getAllCivilizationIds()) {
                    classSystem.updateDistribution(civId, y, y >= 1750 ? "INDUSTRIAL" : "PRE_INDUSTRIAL");
                    urbanizationSystem.updateUrbanization(civId, y, wsm);
                }
            } : null;
            case "agriculture" -> () -> agricultureEngine.tick(currentYear(), wsm);
            case "religion" -> EternalEngineConfig.COMMON.enableReligion.get()
                ? () -> religionEngine.tick(currentYear(), wsm) : null;
            case "technology" -> EternalEngineConfig.COMMON.enableTechnology.get()
                ? () -> technologyEngine.tick(currentYear(), wsm) : null;
            // Step 35: pass the civilization registry so each region is asked about at its own
            // capital. Without it the era table can only count the periods that reached everywhere.
            case "climate" -> EternalEngineConfig.COMMON.enableClimate.get()
                ? () -> climateEngine.tick(currentYear(), wsm, agricultureEngine, civilizationManager) : null;
            case "reputation" -> EternalEngineConfig.COMMON.enableReputation.get() ? () -> {
                int y = currentYear();
                vipSystem.tick(y);
                villagerManager.virtualizer().tick(wsm, y);
            } : null;
            case "events" -> () -> {
                int y = currentYear();
                eventChainEngine.tick(y, wsm);
                // Carry an active divergence's authored story one step further. This is what makes a
                // scenario run "to the end": nothing is pre-computed, the world simply arrives at each
                // year and the consequences become true as the clock crosses them.
                timelineManager.tickScenarios(y, wsm);
            };
            default -> null;
        };
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

    public RoyalCourtManager getRoyalCourtManager() {
        return royalCourtManager;
    }

    public DiplomaticTreatyEngine getTreatyEngine() {
        return treatyEngine;
    }

    public DynasticSuccessionEngine getSuccessionEngine() {
        return successionEngine;
    }

    public ImperialCommandEngine getImperialCommandEngine() {
        return imperialCommandEngine;
    }

    public VipTakeoverSystem getVipTakeoverSystem() {
        return vipTakeoverSystem;
    }

    public VillagerManager getVillagerManager() {
        return villagerManager;
    }

    public AgentVirtualizationEngine getVirtualizationEngine() {
        return virtualizationEngine;
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

    public CivilizationGrammarEngine getGrammarEngine() {
        return grammarEngine;
    }

    public HistoricalLandmarkGenerator getLandmarkGenerator() {
        return landmarkGenerator;
    }

    public TemporalMorphController getTemporalMorphController() {
        return temporalMorphController;
    }

    public CartographicOverlayManager getOverlayManager() {
        return overlayManager;
    }

    public HistoricalAudioDirector getAudioDirector() {
        return audioDirector;
    }

    public AmbientCrowdSoundManager getAmbientCrowdManager() {
        return ambientCrowdManager;
    }

    public HeraldryRegistry getHeraldryRegistry() {
        return heraldryRegistry;
    }

    public ChronoNexusCampaignEngine getChronoNexusEngine() {
        return chronoNexusEngine;
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

    public LogisticsManager getLogisticsManager() {
        return logisticsManager;
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
        sb.append("  Active Caravans: ").append(logisticsManager.activeCaravanCount()).append("\n");
        sb.append("  Active Merchant Ships: ").append(logisticsManager.activeShipCount()).append("\n");
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
