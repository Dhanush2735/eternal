package com.eternalengine;

import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.commands.EternalEngineCommands;
import com.eternalengine.compatibility.ModCompatibilityChecker;
import com.eternalengine.config.EternalEngineConfig;
import com.eternalengine.core.*;
import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.terrabridge.TerraBridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.eternalengine.commands.FirstStepsCommands;
import com.eternalengine.commands.framework.CommandModuleRegistry;

/**
 * Eternal Engine - Historical Earth Simulation for Minecraft
 * 
 * This mod simulates human history on Terra++ Earth. Players can witness
 * the rise and fall of civilizations, play as historical VIP figures,
 * experience major events, and even change the course of history.
 * 
 * MANDATORY DEPENDENCY: Terra++ (terraplusplus)
 * The mod will HARD FAIL on startup if Terra++ is not present.
 */
@Mod(EternalEngineMod.MOD_ID)
public class EternalEngineMod {
    
    public static final String MOD_ID = "eternalengine";
    public static final String MOD_NAME = "Eternal Engine";
    public static final String VERSION = "0.1.0";
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static EternalEngineMod instance;
    
    private boolean initialized = false;
    private String initializationError = null;
    
    public EternalEngineMod() {
        instance = this;
        
        LOGGER.info("===========================================");
        LOGGER.info("  Eternal Engine v{} Initializing...", VERSION);
        LOGGER.info("===========================================");
        
        // CRITICAL: Check for Terra++ FIRST before anything else
        if (!checkTerraPlusPlusPresent()) {
            initializationError = "Terra++ is REQUIRED but not found! Eternal Engine cannot function without Terra++. " +
                    "Please install Terra++ (terraplusplus) version 2.0 or higher.";
            LOGGER.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            LOGGER.error("  FATAL ERROR: TERRA++ NOT FOUND!");
            LOGGER.error("  Eternal Engine REQUIRES Terra++ to function.");
            LOGGER.error("  Please install Terra++ version 2.0+");
            LOGGER.error("  Download from: https://github.com/BuildTheEarth/terraplusplus");
            LOGGER.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            throw new RuntimeException(initializationError);
        }
        
        LOGGER.info("[EE] Terra++ detected. Proceeding with initialization...");
        
        // Register configs
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EternalEngineConfig.COMMON_SPEC, "eternalengine-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, EternalEngineConfig.SERVER_SPEC, "eternalengine-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, EternalEngineConfig.CLIENT_SPEC, "eternalengine-client.toml");
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // Register entities
        EternalEngineEntities.ENTITIES.register(modEventBus);
        com.eternalengine.chrononexus.item.ChronoItems.ITEMS.register(modEventBus);
        // Step 19d/19e: the newspaper, the handwritten newsletter and the map.
        com.eternalengine.news.NewsItems.ITEMS.register(modEventBus);
        com.eternalengine.money.MoneyItems.ITEMS.register(modEventBus); // step 23b: money you can hold
        com.eternalengine.money.TradeMenus.MENUS.register(modEventBus); // step 24: Minecraft's trade screen
        
        // Register mod lifecycle events
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        
        // Register to Forge event bus
        MinecraftForge.EVENT_BUS.register(this);
        
        // Check optional mods
        ModCompatibilityChecker.checkAllMods();
        
        LOGGER.info("[EE] Mod constructor completed successfully");
    }
    
    /**
     * Check if Terra++ is present. This is a hard requirement.
     */
    private boolean checkTerraPlusPlusPresent() {
        boolean present = ModList.get().isLoaded("terraplusplus");
        if (present) {
            ModList.get().getModContainerById("terraplusplus").ifPresent(container -> {
                String version = container.getModInfo().getVersion().toString();
                LOGGER.info("[EE] Found Terra++ version: {}", version);
            });
        }
        return present;
    }
    
    /**
     * Common setup - runs on both client and server
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[EE] Beginning common setup...");
        
        event.enqueueWork(() -> {
            try {
                // Initialize in exact order as specified
                initializePhase1_Foundation();
                initializePhase2_Infrastructure();
                initializePhase3_Core();
                
                initialized = true;
                LOGGER.info("[EE] Common setup completed successfully!");
                
            } catch (Exception e) {
                LOGGER.error("[EE] FATAL: Failed to initialize Eternal Engine!", e);
                initializationError = "Failed to initialize: " + e.getMessage();
                throw new RuntimeException("Eternal Engine initialization failed", e);
            }
        });
    }
    
    /**
     * Phase 1: Foundation systems (Steps 1-8)
     */
    private void initializePhase1_Foundation() {
        LOGGER.info("[EE] Phase 1: Initializing foundation systems...");
        
        // Step 1: Config already registered in constructor
        LOGGER.info("[EE] Step 1: Configuration loaded");
        
        // Step 2: Mod compatibility already checked in constructor
        LOGGER.info("[EE] Step 2: Mod compatibility checked");
        
        // Step 3: TerraBridge initialization
        TerraBridge.initialize();
        LOGGER.info("[EE] Step 3: TerraBridge initialized");
        
        // Step 4: Packet handler
        EternalEnginePacketHandler.register();
        LOGGER.info("[EE] Step 4: Network packets registered");
        
        // Step 5: Capabilities
        EternalEngineCapabilities.register();
        LOGGER.info("[EE] Step 5: Capabilities registered");
        
        // Steps 6-8: Sounds, Particles, Keybindings (registered elsewhere)
        LOGGER.info("[EE] Steps 6-8: Sounds, Particles, Keybindings queued");
    }
    
    /**
     * Phase 2: Infrastructure systems (Steps 9-14)
     */
    private void initializePhase2_Infrastructure() {
        LOGGER.info("[EE] Phase 2: Initializing infrastructure systems...");
        
        // Step 9: PersistenceManager
        PersistenceManager.initialize();
        LOGGER.info("[EE] Step 9: PersistenceManager initialized");
        
        // Step 10: VersionManager
        VersionManager.initialize();
        LOGGER.info("[EE] Step 10: VersionManager initialized");
        
        // Step 11: WorldStateManager
        WorldStateManager.initialize();
        LOGGER.info("[EE] Step 11: WorldStateManager initialized");
        
        // Step 12: AsyncTaskManager
        AsyncTaskManager.initialize();
        LOGGER.info("[EE] Step 12: AsyncTaskManager initialized");
        
        // Step 13: SimulationTickScheduler
        SimulationTickScheduler.initialize();
        LOGGER.info("[EE] Step 13: SimulationTickScheduler initialized");
        
        // Step 14: PerformanceMonitor
        PerformanceMonitor.initialize();
        LOGGER.info("[EE] Step 14: PerformanceMonitor initialized");
    }
    
    /**
     * Phase 3: Core simulation systems (Steps 15+)
     */
    private void initializePhase3_Core() {
        LOGGER.info("[EE] Phase 3: Initializing core simulation systems...");
        
        // This will be expanded as we implement more systems
        // For now, initialize the simulation engine shell
        SimulationEngine.initialize();
        LOGGER.info("[EE] SimulationEngine shell initialized");
        
        // Clock manager
        SimulationClockManager.initialize();
        LOGGER.info("[EE] SimulationClockManager initialized");
    }
    
    /**
     * Client-only setup
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[EE] Beginning client setup...");
        
        event.enqueueWork(() -> {
            // Register keybindings
            // KeybindingRegistry.registerAll();
            
            // Initialize client-side rendering
            // ClientRenderer.initialize();
            
            LOGGER.info("[EE] Client setup completed");
        });
    }
    
    /**
     * Called when server is starting
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[EE] Server starting - loading world state...");
        
        if (!initialized) {
            LOGGER.error("[EE] Cannot start - initialization failed: {}", initializationError);
            return;
        }
        
        try {
            // Bind TerraBridge to the active Terra++ chunk generator now that a server exists.
            // The common setup phase can only verify classes; the real generator is world-bound.
            TerraBridge.initialize(event.getServer());

            // Start simulation. start() loads this world's state itself; loading it here as well (as
            // before step 14) read state.json twice and ran an old save's migration twice.
            SimulationEngine.getInstance().start(event.getServer());
            
            LOGGER.info("[EE] Server startup complete - simulation running");
            
        } catch (Exception e) {
            LOGGER.error("[EE] Failed to start simulation!", e);
        }
    }
    
    /**
     * Called when server is stopping
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[EE] Server stopping - saving state...");
        
        try {
            // Stop simulation (when it was running, stop() saves the state itself)
            boolean wasRunning = SimulationEngine.getInstance().isRunning();
            SimulationEngine.getInstance().stop();

            // Save state, unless stop() just did: until step 16 it was saved twice at every shutdown
            if (!wasRunning) {
                WorldStateManager.getInstance().saveWorldState();
            }
            
            // Shutdown async tasks
            AsyncTaskManager.getInstance().shutdown();
            
            LOGGER.info("[EE] Server shutdown complete - state saved");
            
        } catch (Exception e) {
            LOGGER.error("[EE] Error during shutdown!", e);
        }
    }
    
    /**
     * Register commands
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EternalEngineCommands.register(event.getDispatcher());
        // J5: the modular command tree, starting with the new player's route (/ee firststeps).
        CommandModuleRegistry modules = new CommandModuleRegistry();
        modules.add(new FirstStepsCommands());
        modules.add(new com.eternalengine.commands.RoleCommands()); // step R1: the role day, on demand
        modules.registerAll(event.getDispatcher(), "ee");
        LOGGER.info("[EE] Commands registered");
    }
    
    /**
     * Get the mod instance
     */
    public static EternalEngineMod getInstance() {
        return instance;
    }
    
    /**
     * Check if the mod is fully initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get initialization error if any
     */
    public String getInitializationError() {
        return initializationError;
    }
    
    /**
     * Get the logger
     */
    public static Logger getLogger() {
        return LOGGER;
    }
}
