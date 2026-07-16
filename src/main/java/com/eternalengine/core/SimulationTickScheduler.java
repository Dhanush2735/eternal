package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * SimulationTickScheduler - Manages staggered scheduling of system updates.
 * 
 * Systems update at different frequencies to spread load:
 * - War: every 10 ticks
 * - Disease: every 20 ticks
 * - Economy: every 50 ticks
 * - Trade: every 50 ticks
 * - Population: every 100 ticks
 * - Reputation decay: every 100 ticks
 * - Religion: every 150 ticks
 * - Technology: every 200 ticks
 * - Climate: every 200 ticks
 * - Era check: every 1000 ticks
 * 
 * Offsets are used to spread updates across different ticks.
 */
public class SimulationTickScheduler {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static SimulationTickScheduler instance;
    private static boolean initialized = false;
    
    // System schedules: interval and offset
    private final Map<String, ScheduleEntry> schedules;
    
    // Dynamic frequency reduction when over budget
    private final Map<String, Integer> frequencyMultipliers;
    
    private SimulationTickScheduler() {
        this.schedules = new HashMap<>();
        this.frequencyMultipliers = new HashMap<>();
        
        initializeSchedules();
    }
    
    /**
     * Initialize the scheduler
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[TickScheduler] Already initialized");
            return;
        }
        
        instance = new SimulationTickScheduler();
        initialized = true;
        
        LOGGER.info("[TickScheduler] Initialized with {} scheduled systems", 
            instance.schedules.size());
    }
    
    /**
     * Get the singleton instance
     */
    public static SimulationTickScheduler getInstance() {
        if (!initialized) {
            throw new IllegalStateException("SimulationTickScheduler not initialized!");
        }
        return instance;
    }
    
    /**
     * Initialize all system schedules
     */
    private void initializeSchedules() {
        // Clock updates every tick
        addSchedule("clock", 1, 0);
        
        // War - most frequent simulation update
        addSchedule("war", EternalEngineConfig.SERVER.warUpdateInterval.get(), 0);
        
        // Disease - spread needs frequent updates
        addSchedule("disease", EternalEngineConfig.SERVER.diseaseUpdateInterval.get(), 3);
        
        // Economy and trade
        addSchedule("economy", EternalEngineConfig.SERVER.economyUpdateInterval.get(), 7);
        addSchedule("trade", EternalEngineConfig.SERVER.tradeUpdateInterval.get(), 13);
        
        // Population - less frequent, heavier computation
        addSchedule("population", EternalEngineConfig.SERVER.populationUpdateInterval.get(), 17);

        // Agriculture/famine uses the same cadence as population by default.
        addSchedule("agriculture", EternalEngineConfig.SERVER.populationUpdateInterval.get(), 19);
        
        // Reputation decay
        addSchedule("reputation", EternalEngineConfig.SERVER.reputationDecayInterval.get(), 23);
        
        // Religion
        addSchedule("religion", EternalEngineConfig.SERVER.religionUpdateInterval.get(), 31);
        
        // Technology
        addSchedule("technology", EternalEngineConfig.SERVER.technologyUpdateInterval.get(), 37);
        
        // Climate - infrequent, historical data
        addSchedule("climate", EternalEngineConfig.SERVER.climateUpdateInterval.get(), 43);
        
        // Era check - very infrequent
        addSchedule("era", EternalEngineConfig.SERVER.eraCheckInterval.get(), 53);
        
        // VIP aging - once per simulation year
        addSchedule("vipAging", 200, 61);
        
        // Building evolution
        addSchedule("buildings", 100, 67);
        
        // Event chain processing
        addSchedule("events", 20, 71);
        
        // Auto-save check
        addSchedule("autosave", 1200, 79); // Every minute
        
        // Performance metrics
        addSchedule("performance", 100, 83);
    }
    
    /**
     * Add a schedule entry
     */
    private void addSchedule(String systemId, int interval, int offset) {
        schedules.put(systemId, new ScheduleEntry(interval, offset));
        frequencyMultipliers.put(systemId, 1);
    }
    
    /**
     * Check if a system should update this tick
     */
    public boolean shouldUpdate(String systemId, long tick) {
        ScheduleEntry entry = schedules.get(systemId);
        if (entry == null) {
            return false;
        }
        
        int multiplier = frequencyMultipliers.getOrDefault(systemId, 1);
        int effectiveInterval = entry.interval * multiplier;
        
        return (tick + entry.offset) % effectiveInterval == 0;
    }
    
    /**
     * Get the interval for a system
     */
    public int getInterval(String systemId) {
        ScheduleEntry entry = schedules.get(systemId);
        if (entry == null) {
            return -1;
        }
        
        int multiplier = frequencyMultipliers.getOrDefault(systemId, 1);
        return entry.interval * multiplier;
    }
    
    /**
     * Increase frequency multiplier (reduce update frequency) for a system
     * Used when system is over budget
     */
    public void reduceFrequency(String systemId) {
        int current = frequencyMultipliers.getOrDefault(systemId, 1);
        int newMultiplier = Math.min(current * 2, 16); // Max 16x reduction
        frequencyMultipliers.put(systemId, newMultiplier);
        
        LOGGER.warn("[TickScheduler] Reduced {} frequency by {}x due to performance", 
            systemId, newMultiplier);
    }
    
    /**
     * Reset frequency multiplier to normal
     */
    public void resetFrequency(String systemId) {
        frequencyMultipliers.put(systemId, 1);
    }
    
    /**
     * Reset all frequencies to normal
     */
    public void resetAllFrequencies() {
        frequencyMultipliers.replaceAll((k, v) -> 1);
    }
    
    /**
     * Update a schedule's interval (from config reload)
     */
    public void updateInterval(String systemId, int newInterval) {
        ScheduleEntry entry = schedules.get(systemId);
        if (entry != null) {
            schedules.put(systemId, new ScheduleEntry(newInterval, entry.offset));
        }
    }
    
    /**
     * Get all scheduled system IDs
     */
    public java.util.Set<String> getScheduledSystems() {
        return schedules.keySet();
    }
    
    /**
     * Get schedule info for debugging
     */
    public String getScheduleInfo(String systemId) {
        ScheduleEntry entry = schedules.get(systemId);
        if (entry == null) {
            return "Not scheduled";
        }
        
        int multiplier = frequencyMultipliers.getOrDefault(systemId, 1);
        return String.format("Interval: %d (x%d = %d), Offset: %d", 
            entry.interval, multiplier, entry.interval * multiplier, entry.offset);
    }
    
    /**
     * Schedule entry class
     */
    private static class ScheduleEntry {
        final int interval;
        final int offset;
        
        ScheduleEntry(int interval, int offset) {
            this.interval = interval;
            this.offset = offset;
        }
    }
}
