package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.config.EternalEngineConfig;
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
        // Register for server tick events
        MinecraftForge.EVENT_BUS.register(this);
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
                LOGGER.debug("[SimulationEngine] Avg tick time: {:.2f}ms ({} ticks)", 
                    avgTickTime, ticksThisSecond);
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
        
        // Update systems based on their schedules
        // These will be populated as we implement each system
        
        if (scheduler.shouldUpdate("war", tick) && EternalEngineConfig.COMMON.enableWar.get()) {
            // WarEngine.getInstance().tick();
        }
        
        if (scheduler.shouldUpdate("disease", tick) && EternalEngineConfig.COMMON.enableDisease.get()) {
            // DiseaseEngine.getInstance().tick();
        }
        
        if (scheduler.shouldUpdate("economy", tick) && EternalEngineConfig.COMMON.enableTrade.get()) {
            // EconomyEngine.getInstance().tick();
        }
        
        if (scheduler.shouldUpdate("population", tick) && EternalEngineConfig.COMMON.enablePopulation.get()) {
            // PopulationEngine.getInstance().tick();
        }
        
        if (scheduler.shouldUpdate("religion", tick) && EternalEngineConfig.COMMON.enableReligion.get()) {
            // ReligionEngine.getInstance().tick();
        }
        
        if (scheduler.shouldUpdate("technology", tick) && EternalEngineConfig.COMMON.enableTechnology.get()) {
            // TechnologyEngine.getInstance().tick();
        }
        
        if (scheduler.shouldUpdate("climate", tick) && EternalEngineConfig.COMMON.enableClimate.get()) {
            // ClimateEngine.getInstance().tick();
        }
        
        if (scheduler.shouldUpdate("reputation", tick) && EternalEngineConfig.COMMON.enableReputation.get()) {
            // ReputationEngine.getInstance().tick();
        }
        
        if (scheduler.shouldUpdate("era", tick)) {
            // EraSystem.getInstance().tick();
        }
        
        // Process building updates (throttled by BlockUpdateQueue)
        if (EternalEngineConfig.COMMON.enableBuildings.get()) {
            // BlockUpdateQueue.getInstance().processQueue();
        }
        
        // Event chain processing
        // EventChainEngine.getInstance().tick();
        
        // Record tick performance
        PerformanceMonitor.getInstance().recordTick(tick, System.nanoTime() - lastTickStartTime);
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
        
        return sb.toString();
    }
}
