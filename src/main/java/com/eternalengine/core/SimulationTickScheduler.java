package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SimulationTickScheduler - Manages staggered scheduling of system updates.
 *
 * <p>Systems update at different frequencies to spread load (defaults, in ticks; 20 ticks = 1 second):
 * clock 1, war 10, disease 20, events 20, economy 50, trade 50, population 100, agriculture 100,
 * reputation 100, performance 100, religion 150, technology 200, climate 200, autosave check 1200.
 * Offsets spread the updates across different ticks.
 *
 * <h3>Where the intervals come from (step 14)</h3>
 * The intervals of war, disease, economy, trade, population/agriculture, reputation, religion, technology
 * and climate are settings of the server config ({@code eternalengine-server.toml}, per world). The
 * scheduler used to read them while the mod was being set up, before any world (and so before any server
 * config) existed: the real game then quietly used the built-in defaults and a development run crashed.
 * Now the scheduler starts with the defaults written in the config spec and {@link #reloadFromConfig()}
 * reads the world's values when the world starts and again whenever the config file is edited.
 *
 * <p>Removed in step 14: the "era" schedule (there is no era system), "vipAging" (VIP ages are updated in
 * the reputation step) and "buildings" (building changes run every tick through the block queue).
 */
public class SimulationTickScheduler {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private static SimulationTickScheduler instance;
    private static boolean initialized = false;

    // System schedules: interval and offset. Read on the server thread, written there and on the config
    // watcher thread (a config edit), hence concurrent maps.
    private final Map<String, ScheduleEntry> schedules = new ConcurrentHashMap<>();

    // Dynamic frequency reduction when over budget (PerformanceMonitor.evaluate)
    private final Map<String, Integer> frequencyMultipliers = new ConcurrentHashMap<>();

    /** Whether the intervals in use came from the world's server config (false: built-in defaults). */
    private volatile boolean fromWorldConfig = false;

    private SimulationTickScheduler() {
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

        LOGGER.info("[TickScheduler] Initialized with {} scheduled systems (config defaults until a world starts)",
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

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Initialize all system schedules with the defaults of the config spec. Nothing here may read a
     * config value: no world, and so no server config, exists yet when the mod is set up.
     */
    private void initializeSchedules() {
        // Clock updates every tick
        addSchedule("clock", 1, 0);

        // War - most frequent simulation update
        addSchedule("war", spec(EternalEngineConfig.SERVER.warUpdateInterval), 0);

        // Disease
        addSchedule("disease", spec(EternalEngineConfig.SERVER.diseaseUpdateInterval), 3);

        // Economy and trade
        addSchedule("economy", spec(EternalEngineConfig.SERVER.economyUpdateInterval), 7);
        addSchedule("trade", spec(EternalEngineConfig.SERVER.tradeUpdateInterval), 13);

        // Population (and farming, which follows the population setting)
        addSchedule("population", spec(EternalEngineConfig.SERVER.populationUpdateInterval), 17);
        addSchedule("agriculture", spec(EternalEngineConfig.SERVER.populationUpdateInterval), 19);

        // Reputation decay, VIP ages, villager virtualisation
        addSchedule("reputation", spec(EternalEngineConfig.SERVER.reputationDecayInterval), 23);

        // Religion, technology, climate
        addSchedule("religion", spec(EternalEngineConfig.SERVER.religionUpdateInterval), 31);
        addSchedule("technology", spec(EternalEngineConfig.SERVER.technologyUpdateInterval), 37);
        addSchedule("climate", spec(EternalEngineConfig.SERVER.climateUpdateInterval), 43);

        // Event chain processing and the alternative-timeline story pump
        addSchedule("events", 20, 71);

        // Auto-save check (saves when something changed and autoSaveIntervalMinutes passed)
        addSchedule("autosave", 1200, 79); // Every minute

        // Performance check: slows a system that keeps the tick over budget, restores it afterwards
        addSchedule("performance", 100, 83);
    }

    /** The default written in the config spec, readable before any config file is loaded. */
    private static int spec(ForgeConfigSpec.IntValue value) {
        Integer d = value.getDefault();
        return d == null ? 100 : Math.max(1, d);
    }

    /**
     * The world's server config values (step 14). Called when the world starts and when the config file
     * changes. Keeps the offsets; a slowed-down system stays slowed (its multiplier is kept).
     *
     * @return what changed, one "system a->b" per changed interval (empty when nothing changed)
     */
    public synchronized Map<String, String> reloadFromConfig() {
        Map<String, String> changed = new LinkedHashMap<>();
        try {
            set("war", EternalEngineConfig.SERVER.warUpdateInterval.get(), changed);
            set("disease", EternalEngineConfig.SERVER.diseaseUpdateInterval.get(), changed);
            set("economy", EternalEngineConfig.SERVER.economyUpdateInterval.get(), changed);
            set("trade", EternalEngineConfig.SERVER.tradeUpdateInterval.get(), changed);
            set("population", EternalEngineConfig.SERVER.populationUpdateInterval.get(), changed);
            set("agriculture", EternalEngineConfig.SERVER.populationUpdateInterval.get(), changed);
            set("reputation", EternalEngineConfig.SERVER.reputationDecayInterval.get(), changed);
            set("religion", EternalEngineConfig.SERVER.religionUpdateInterval.get(), changed);
            set("technology", EternalEngineConfig.SERVER.technologyUpdateInterval.get(), changed);
            set("climate", EternalEngineConfig.SERVER.climateUpdateInterval.get(), changed);
            fromWorldConfig = true;
        } catch (IllegalStateException notLoaded) {
            // The server config is not loaded (no world yet): keep what is in use.
            LOGGER.debug("[TickScheduler] Server config not loaded yet; keeping the current intervals");
            return changed;
        }
        if (!changed.isEmpty()) {
            LOGGER.info("[TickScheduler] Intervals from the server config: {}", changed);
        }
        return changed;
    }

    private void set(String systemId, int interval, Map<String, String> changed) {
        ScheduleEntry entry = schedules.get(systemId);
        int value = Math.max(1, interval);
        if (entry == null) {
            addSchedule(systemId, value, 0);
            changed.put(systemId, "new->" + value);
        } else if (entry.interval != value) {
            schedules.put(systemId, new ScheduleEntry(value, entry.offset));
            changed.put(systemId, entry.interval + "->" + value);
        }
    }

    /** True once {@link #reloadFromConfig()} read the world's server config. */
    public boolean usesWorldConfig() {
        return fromWorldConfig;
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
     * Get the interval for a system (with any slow-down applied), or -1 when it has no schedule.
     */
    public int getInterval(String systemId) {
        ScheduleEntry entry = schedules.get(systemId);
        if (entry == null) {
            return -1;
        }

        int multiplier = frequencyMultipliers.getOrDefault(systemId, 1);
        return entry.interval * multiplier;
    }

    /** The configured interval, without any slow-down; -1 when the system has no schedule. */
    public int getBaseInterval(String systemId) {
        ScheduleEntry entry = schedules.get(systemId);
        return entry == null ? -1 : entry.interval;
    }

    public int getMultiplier(String systemId) {
        return frequencyMultipliers.getOrDefault(systemId, 1);
    }

    /**
     * Double a system's interval (up to 16x) because it keeps the tick over budget.
     *
     * @return the new multiplier, or 0 when the system has no schedule or is already at 16x
     */
    public int reduceFrequency(String systemId) {
        if (!schedules.containsKey(systemId)) {
            return 0;
        }
        int current = frequencyMultipliers.getOrDefault(systemId, 1);
        if (current >= 16) {
            return 0;
        }
        int newMultiplier = Math.min(current * 2, 16); // Max 16x reduction
        frequencyMultipliers.put(systemId, newMultiplier);
        return newMultiplier;
    }

    /**
     * Halve a slowed-down system's multiplier (back towards its configured interval).
     *
     * @return the new multiplier, or 0 when it was not slowed down
     */
    public int restoreFrequency(String systemId) {
        int current = frequencyMultipliers.getOrDefault(systemId, 1);
        if (current <= 1) {
            return 0;
        }
        int next = Math.max(1, current / 2);
        frequencyMultipliers.put(systemId, next);
        return next;
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
     * Update a schedule's interval
     */
    public void updateInterval(String systemId, int newInterval) {
        ScheduleEntry entry = schedules.get(systemId);
        if (entry != null) {
            schedules.put(systemId, new ScheduleEntry(Math.max(1, newInterval), entry.offset));
        }
    }

    /**
     * Get all scheduled system IDs
     */
    public Set<String> getScheduledSystems() {
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
