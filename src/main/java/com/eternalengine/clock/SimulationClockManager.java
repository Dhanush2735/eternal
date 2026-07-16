package com.eternalengine.clock;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.terrabridge.TerraTimeBridge;
import com.eternalengine.network.packets.SimulationStatePacket;
import com.eternalengine.network.packets.SpeedChangePacket;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SimulationClockManager - Controls simulation time and speed.
 * 
 * Speed Options:
 * - 1x  = 1 sim year per MC day
 * - 5x  = 5 sim years per MC day
 * - 10x = 10 sim years per MC day (DEFAULT)
 * - 25x = 25 sim years per MC day
 * - 50x = 50 sim years per MC day
 * 
 * 1 MC day = 20 real minutes = 24000 ticks
 * 
 * Speed Priority (highest to lowest):
 * 1. Unoverridable VIP lock
 * 2. Player override active
 * 3. Auto slowdown from events
 * 4. Overridable VIP lock
 * 5. Base speed setting
 */
public class SimulationClockManager {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static SimulationClockManager instance;
    private static boolean initialized = false;
    
    // Ticks per MC day
    public static final int TICKS_PER_DAY = 24000;
    
    // Valid speed values
    public static final int[] VALID_SPEEDS = {1, 5, 10, 25, 50};
    
    // Current state
    private final AtomicInteger currentYear = new AtomicInteger(-3000);
    private final AtomicInteger baseSpeed = new AtomicInteger(10);
    private final AtomicInteger effectiveSpeed = new AtomicInteger(10);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    
    // Tick tracking for year progression
    private final AtomicLong ticksInCurrentYear = new AtomicLong(0);
    
    // Auto slowdown
    private final Map<String, SlowdownEntry> activeSlowdowns = new ConcurrentHashMap<>();
    
    // VIP locks
    private final Map<String, VipLockEntry> vipLocks = new ConcurrentHashMap<>();
    
    // Player override
    private final AtomicBoolean overrideActive = new AtomicBoolean(false);
    private final AtomicInteger overrideSpeed = new AtomicInteger(0);
    private final AtomicLong overrideExpireTime = new AtomicLong(0);
    private final Map<UUID, Integer> speedVotes = new ConcurrentHashMap<>();
    
    // Auto slowdown event definitions
    private static final Map<String, SlowdownConfig> EVENT_SLOWDOWNS = new HashMap<>();
    
    static {
        // Event -> (maxSpeed, minRealMinutes)
        EVENT_SLOWDOWNS.put("WW1", new SlowdownConfig(5, 120));
        EVENT_SLOWDOWNS.put("WW2", new SlowdownConfig(5, 180));
        EVENT_SLOWDOWNS.put("HOLOCAUST", new SlowdownConfig(2, 60));
        EVENT_SLOWDOWNS.put("BLACK_DEATH", new SlowdownConfig(5, 60));
        EVENT_SLOWDOWNS.put("CUBAN_MISSILE_CRISIS", new SlowdownConfig(1, 20));
        EVENT_SLOWDOWNS.put("MONGOL_INVASION", new SlowdownConfig(5, 45));
        EVENT_SLOWDOWNS.put("NAPOLEONIC_WARS", new SlowdownConfig(5, 90));
        EVENT_SLOWDOWNS.put("FALL_OF_ROME", new SlowdownConfig(5, 60));
        EVENT_SLOWDOWNS.put("RUSSIAN_REVOLUTION", new SlowdownConfig(5, 45));
        EVENT_SLOWDOWNS.put("FRENCH_REVOLUTION", new SlowdownConfig(5, 45));
        EVENT_SLOWDOWNS.put("COLD_WAR", new SlowdownConfig(5, 120));
        EVENT_SLOWDOWNS.put("INDUSTRIAL_REVOLUTION", new SlowdownConfig(10, 60));
        EVENT_SLOWDOWNS.put("RENAISSANCE", new SlowdownConfig(10, 60));
    }
    
    private SimulationClockManager() {}
    
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[Clock] Already initialized");
            return;
        }
        
        instance = new SimulationClockManager();
        initialized = true;
        
        LOGGER.info("[Clock] Initialized");
    }
    
    public static SimulationClockManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("SimulationClockManager not initialized!");
        }
        return instance;
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Start the clock
     */
    public void start() {
        // Terra++ historical time is the source of truth when available.
        WorldStateManager wsm = WorldStateManager.getInstance();
        currentYear.set(TerraTimeBridge.getCurrentYear(wsm.getCurrentYear()));
        baseSpeed.set(TerraTimeBridge.getTimeScale(wsm.getSimulationSpeed()));
        
        running.set(true);
        paused.set(false);
        
        calculateEffectiveSpeed();
        
        LOGGER.info("[Clock] Started at year {}, speed {}x", currentYear.get(), effectiveSpeed.get());
    }
    
    /**
     * Stop the clock
     */
    public void stop() {
        running.set(false);
        
        // Save state
        WorldStateManager.getInstance().setCurrentYear(currentYear.get());
        WorldStateManager.getInstance().setSimulationSpeed(baseSpeed.get());
        
        LOGGER.info("[Clock] Stopped at year {}", currentYear.get());
    }
    
    /**
     * Pause the clock
     */
    public void pause() {
        paused.set(true);
    }
    
    /**
     * Resume the clock
     */
    public void resume() {
        paused.set(false);
    }
    
    /**
     * Process a tick - called every server tick
     */
    public void tick() {
        if (!running.get() || paused.get()) {
            return;
        }
        
        // Check if override has expired
        if (overrideActive.get() && System.currentTimeMillis() > overrideExpireTime.get()) {
            overrideActive.set(false);
            overrideSpeed.set(0);
            speedVotes.clear();
            calculateEffectiveSpeed();
            broadcastSpeedChange("Override expired");
        }
        
        if (TerraTimeBridge.isAvailable()) {
            int terraYear = TerraTimeBridge.getCurrentYear(currentYear.get());
            if (terraYear != currentYear.get()) {
                currentYear.set(terraYear);
                WorldStateManager.getInstance().setCurrentYear(terraYear);
                checkSlowdownExpiration();
            }
            int terraScale = TerraTimeBridge.getTimeScale(baseSpeed.get());
            if (terraScale != baseSpeed.get()) {
                baseSpeed.set(terraScale);
                WorldStateManager.getInstance().setSimulationSpeed(terraScale);
                calculateEffectiveSpeed();
            }
            return;
        }

        // Fallback mode only if Terra++ time is unavailable.
        ticksInCurrentYear.incrementAndGet();
        int ticksPerYear = TICKS_PER_DAY / Math.max(1, effectiveSpeed.get());
        if (ticksInCurrentYear.get() >= ticksPerYear) {
            advanceYear();
            ticksInCurrentYear.set(0);
        }
    }
    
    /**
     * Advance to next year
     */
    private void advanceYear() {
        int newYear = currentYear.incrementAndGet();
        
        // Update world state
        WorldStateManager.getInstance().setCurrentYear(newYear);
        
        // Custom Forge year-advance event integration is handled in a later event phase.
        LOGGER.debug("[Clock] Advanced to year {}", newYear);
        
        // Check for expired slowdowns
        checkSlowdownExpiration();
    }
    
    /**
     * Set base speed (admin only)
     */
    public void setBaseSpeed(int speed) {
        if (!isValidSpeed(speed)) {
            LOGGER.warn("[Clock] Invalid speed: {}", speed);
            return;
        }
        
        int actualSpeed = TerraTimeBridge.isAvailable()
            ? TerraTimeBridge.setTimeScale(speed, speed)
            : speed;
        baseSpeed.set(actualSpeed);
        WorldStateManager.getInstance().setSimulationSpeed(actualSpeed);
        calculateEffectiveSpeed();
        broadcastSpeedChange("Admin set base speed");
    }
    
    /**
     * Register an event slowdown
     */
    public void registerSlowdown(String eventId, int maxSpeed, long durationMs) {
        SlowdownEntry entry = new SlowdownEntry(eventId, maxSpeed, 
            System.currentTimeMillis() + durationMs, false);
        activeSlowdowns.put(eventId, entry);
        
        calculateEffectiveSpeed();
        broadcastSpeedChange("Event: " + eventId);
        
        LOGGER.info("[Clock] Slowdown registered for {} (max {}x, {}ms)", 
            eventId, maxSpeed, durationMs);
    }
    
    /**
     * Register a VIP lock
     */
    public void registerVipLock(String lockId, int maxSpeed, boolean overridable) {
        VipLockEntry entry = new VipLockEntry(lockId, maxSpeed, overridable);
        vipLocks.put(lockId, entry);
        
        calculateEffectiveSpeed();
        broadcastSpeedChange("VIP: " + lockId);
        
        LOGGER.info("[Clock] VIP lock registered: {} (max {}x, overridable: {})",
            lockId, maxSpeed, overridable);
    }
    
    /**
     * Remove a VIP lock
     */
    public void removeVipLock(String lockId) {
        vipLocks.remove(lockId);
        calculateEffectiveSpeed();
        broadcastSpeedChange("VIP lock removed: " + lockId);
    }
    
    /**
     * Process a speed vote from a VIP player
     */
    public void processVote(ServerPlayer player, int votedSpeed) {
        if (!isValidSpeed(votedSpeed)) {
            return;
        }
        
        speedVotes.put(player.getUUID(), votedSpeed);
        
        // Majority vote resolution is handled by the VIP/player authority layer.
        // Foundation behavior stores the vote for later resolution.
    }
    
    /**
     * Activate player override
     */
    public void activateOverride(int speed, long durationMs) {
        if (!isValidSpeed(speed)) {
            return;
        }
        
        // Check for unoverridable VIP locks
        for (VipLockEntry lock : vipLocks.values()) {
            if (!lock.overridable) {
                LOGGER.info("[Clock] Cannot override - unoverridable VIP lock active");
                return;
            }
        }
        
        overrideActive.set(true);
        overrideSpeed.set(speed);
        overrideExpireTime.set(System.currentTimeMillis() + durationMs);
        
        calculateEffectiveSpeed();
        broadcastSpeedChange("Player override");
    }
    
    /**
     * Calculate effective speed based on priority
     */
    private void calculateEffectiveSpeed() {
        int speed = baseSpeed.get();
        String reason = "Base speed";
        
        // Priority 5: Base speed (already set)
        
        // Priority 4: Overridable VIP locks
        for (VipLockEntry lock : vipLocks.values()) {
            if (lock.overridable && lock.maxSpeed < speed) {
                speed = lock.maxSpeed;
                reason = "VIP lock: " + lock.lockId;
            }
        }
        
        // Priority 3: Auto slowdowns
        for (SlowdownEntry entry : activeSlowdowns.values()) {
            if (entry.maxSpeed < speed) {
                speed = entry.maxSpeed;
                reason = "Event: " + entry.eventId;
            }
        }
        
        // Priority 2: Player override
        if (overrideActive.get()) {
            speed = overrideSpeed.get();
            reason = "Player override";
        }
        
        // Priority 1: Unoverridable VIP locks
        for (VipLockEntry lock : vipLocks.values()) {
            if (!lock.overridable && lock.maxSpeed < speed) {
                speed = lock.maxSpeed;
                reason = "VIP lock (unoverridable): " + lock.lockId;
            }
        }
        
        int oldSpeed = effectiveSpeed.getAndSet(speed);
        if (oldSpeed != speed) {
            LOGGER.info("[Clock] Speed changed: {}x -> {}x ({})", oldSpeed, speed, reason);
        }
    }
    
    /**
     * Check for expired slowdowns
     */
    private void checkSlowdownExpiration() {
        long now = System.currentTimeMillis();
        
        activeSlowdowns.entrySet().removeIf(entry -> {
            if (entry.getValue().expireTime < now) {
                LOGGER.info("[Clock] Slowdown expired: {}", entry.getKey());
                return true;
            }
            return false;
        });
        
        calculateEffectiveSpeed();
    }
    
    /**
     * Broadcast speed change to all players
     */
    private void broadcastSpeedChange(String reason) {
        SpeedChangePacket packet = new SpeedChangePacket(
            effectiveSpeed.get(),
            baseSpeed.get(),
            reason,
            !activeSlowdowns.isEmpty(),
            !vipLocks.isEmpty(),
            vipLocks.values().stream().allMatch(l -> l.overridable)
        );
        
        EternalEnginePacketHandler.sendToAll(packet);
    }
    
    /**
     * Check if speed value is valid
     */
    private boolean isValidSpeed(int speed) {
        for (int valid : VALID_SPEEDS) {
            if (valid == speed) return true;
        }
        return false;
    }
    
    // Getters
    
    public int getCurrentYear() {
        int year = TerraTimeBridge.getCurrentYear(currentYear.get());
        if (year != currentYear.get()) {
            currentYear.set(year);
            WorldStateManager.getInstance().setCurrentYear(year);
        }
        return year;
    }
    
    public void setCurrentYear(int year) {
        boolean jumped = SimulationEngine.isInitialized()
            && TerraTimeBridge.jumpToYear(SimulationEngine.getInstance().getServer(), year);
        int actualYear = jumped ? TerraTimeBridge.getCurrentYear(year) : year;
        currentYear.set(actualYear);
        WorldStateManager.getInstance().setCurrentYear(actualYear);
    }
    
    public int getBaseSpeed() {
        int speed = TerraTimeBridge.getTimeScale(baseSpeed.get());
        if (speed != baseSpeed.get()) {
            baseSpeed.set(speed);
            WorldStateManager.getInstance().setSimulationSpeed(speed);
        }
        return speed;
    }
    
    public int getEffectiveSpeed() {
        return effectiveSpeed.get();
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public boolean isPaused() {
        return paused.get();
    }
    
    public boolean isSlowdownActive() {
        return !activeSlowdowns.isEmpty();
    }
    
    public boolean isVipLockActive() {
        return !vipLocks.isEmpty();
    }
    
    public boolean isOverrideActive() {
        return overrideActive.get();
    }
    
    // Inner classes
    
    private record SlowdownEntry(String eventId, int maxSpeed, long expireTime, boolean overridable) {}
    
    private record VipLockEntry(String lockId, int maxSpeed, boolean overridable) {}
    
    private record SlowdownConfig(int maxSpeed, int minRealMinutes) {}
}
