package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PerformanceMonitor - Tracks performance metrics for all systems.
 *
 * <ul>
 *   <li>Time of every world system, every time it runs ({@link #endSystem}, called by {@link SystemRunner}).
 *       Until step 14 nothing called it, so only the whole tick was measured and {@code /ee performance}
 *       could not say which system was slow.</li>
 *   <li>Rolling averages over the last 100 ticks and the last 100 runs of each system, and peaks.</li>
 *   <li>{@link #evaluate()} (every 5 seconds, the "performance" schedule): when the world systems keep the
 *       tick over {@code maxTickBudgetMs} for 15 seconds, the scheduled system that costs the most per tick
 *       runs half as often (at most 16 times less often); after 30 seconds back under half the budget, the
 *       most slowed system gets one step of its rate back. Every change is logged.</li>
 *   <li>{@code /ee performance}: {@link #getPerformanceReport()}.</li>
 * </ul>
 */
public class PerformanceMonitor {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private static PerformanceMonitor instance;
    private static boolean initialized = false;

    // Rolling average window size
    private static final int ROLLING_WINDOW = 100;

    /** Consecutive evaluations (5 s apart) over budget before a system is slowed. */
    private static final int SLOW_AFTER = 3;
    /** Consecutive evaluations under half the budget before a slowed system gets a step back. */
    private static final int RESTORE_AFTER = 6;

    // System timers (in nanoseconds)
    private final ConcurrentHashMap<String, SystemMetrics> systemMetrics;

    // Global metrics
    private final AtomicLong totalTickTime = new AtomicLong(0);
    private final AtomicLong tickCount = new AtomicLong(0);
    private final AtomicLong budgetExceededCount = new AtomicLong(0);
    private final AtomicLong peakTickTime = new AtomicLong(0);
    private final AtomicLong deferredCount = new AtomicLong(0);
    private final AtomicLong adaptiveChanges = new AtomicLong(0);

    // Tick time history for rolling average
    private final long[] tickTimeHistory = new long[ROLLING_WINDOW];
    private int historyIndex = 0;

    private int overBudgetStreak = 0;
    private int underBudgetStreak = 0;

    private PerformanceMonitor() {
        this.systemMetrics = new ConcurrentHashMap<>();
    }

    /**
     * Initialize the performance monitor
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[PerformanceMonitor] Already initialized");
            return;
        }

        instance = new PerformanceMonitor();
        initialized = true;

        LOGGER.info("[PerformanceMonitor] Initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the singleton instance
     */
    public static PerformanceMonitor getInstance() {
        if (!initialized) {
            throw new IllegalStateException("PerformanceMonitor not initialized!");
        }
        return instance;
    }

    /**
     * Record the start of a system's processing
     * @return Start time in nanoseconds
     */
    public long startSystem(String systemId) {
        return System.nanoTime();
    }

    /**
     * Record the end of a system's processing
     */
    public void endSystem(String systemId, long startTime) {
        long duration = System.nanoTime() - startTime;

        SystemMetrics metrics = systemMetrics.computeIfAbsent(systemId, k -> new SystemMetrics());
        metrics.addSample(duration);

        if (duration / 1_000_000.0 > getSystemBudget(systemId)) {
            metrics.recordOverBudget();
        }
    }

    /**
     * Record a complete tick
     */
    public void recordTick(long tickNumber, long durationNanos) {
        tickCount.incrementAndGet();
        totalTickTime.addAndGet(durationNanos);

        // Update peak
        long currentPeak = peakTickTime.get();
        while (durationNanos > currentPeak && !peakTickTime.compareAndSet(currentPeak, durationNanos)) {
            currentPeak = peakTickTime.get();
        }

        // Add to rolling history
        tickTimeHistory[historyIndex] = durationNanos;
        historyIndex = (historyIndex + 1) % ROLLING_WINDOW;
    }

    /**
     * Record budget exceeded event
     */
    public void recordBudgetExceeded() {
        budgetExceededCount.incrementAndGet();
    }

    /** A scheduled system waited for the next tick because this tick had used its budget. */
    public void recordDeferred(String systemId) {
        deferredCount.incrementAndGet();
        systemMetrics.computeIfAbsent(systemId, k -> new SystemMetrics()).deferred.incrementAndGet();
    }

    /** The tick budget of the world systems in ms ({@code maxTickBudgetMs}, 10 until a world config is loaded). */
    public static int tickBudgetMs() {
        try {
            return EternalEngineConfig.SERVER.maxTickBudgetMs.get();
        } catch (IllegalStateException notLoaded) {
            Integer d = EternalEngineConfig.SERVER.maxTickBudgetMs.getDefault();
            return d == null ? 10 : d;
        }
    }

    /**
     * Get budget for a system (in ms)
     */
    private double getSystemBudget(String systemId) {
        // Total budget from config, distributed by weight
        int totalBudget = tickBudgetMs();

        // System weights
        return switch (systemId) {
            case "events" -> totalBudget * 0.2;    // 20%
            case "war" -> totalBudget * 0.15;      // 15%
            case "disease" -> totalBudget * 0.1;   // 10%
            case "population" -> totalBudget * 0.1; // 10%
            case "economy" -> totalBudget * 0.05;   // 5%
            case "reputation" -> totalBudget * 0.05; // 5%
            case "buildings", "blocks" -> totalBudget * 0.1;      // 10%
            case "spawner" -> totalBudget * 0.3;    // runs every 2 s for every player
            default -> totalBudget * 0.02;          // 2% for others
        };
    }

    /**
     * Every 5 seconds (the "performance" schedule): slow down or restore one scheduled system, see the class
     * comment. Runs on the server thread.
     */
    public void evaluate() {
        if (tickCount.get() < ROLLING_WINDOW) {
            return;
        }
        double average = getRollingAverageTickTime();
        int budget = tickBudgetMs();
        SimulationTickScheduler scheduler = SimulationTickScheduler.getInstance();
        if (average > budget) {
            underBudgetStreak = 0;
            if (++overBudgetStreak < SLOW_AFTER) {
                return;
            }
            overBudgetStreak = 0;
            // The scheduled system that costs the most per tick: recent ms per run / ticks between runs.
            String worst = null;
            double worstCost = 0.0d;
            for (Map.Entry<String, SystemMetrics> e : systemMetrics.entrySet()) {
                int interval = scheduler.getInterval(e.getKey());
                if (interval <= 1 || "autosave".equals(e.getKey()) || "performance".equals(e.getKey())) {
                    continue; // unscheduled, the clock, or housekeeping: never slowed
                }
                double cost = e.getValue().getRecentAverage() / interval;
                if (cost > worstCost) {
                    worstCost = cost;
                    worst = e.getKey();
                }
            }
            if (worst == null) {
                return;
            }
            int before = scheduler.getInterval(worst);
            if (scheduler.reduceFrequency(worst) > 0) {
                adaptiveChanges.incrementAndGet();
                LOGGER.info("[PerformanceMonitor] The world systems used {} ms per tick for 15 s (budget {} ms):"
                        + " '{}' now runs every {} ticks instead of every {}", String.format("%.2f", average),
                    budget, worst, scheduler.getInterval(worst), before);
            }
        } else {
            overBudgetStreak = 0;
            if (average >= budget * 0.5d) {
                underBudgetStreak = 0;
                return;
            }
            if (++underBudgetStreak < RESTORE_AFTER) {
                return;
            }
            underBudgetStreak = 0;
            String mostSlowed = null;
            int highest = 1;
            for (String id : scheduler.getScheduledSystems()) {
                int m = scheduler.getMultiplier(id);
                if (m > highest) {
                    highest = m;
                    mostSlowed = id;
                }
            }
            if (mostSlowed != null && scheduler.restoreFrequency(mostSlowed) > 0) {
                adaptiveChanges.incrementAndGet();
                LOGGER.info("[PerformanceMonitor] The tick is back under budget: '{}' runs every {} ticks again",
                    mostSlowed, scheduler.getInterval(mostSlowed));
            }
        }
    }

    /**
     * Get rolling average tick time (ms)
     */
    public double getRollingAverageTickTime() {
        long count = Math.min(tickCount.get(), ROLLING_WINDOW);
        if (count == 0) return 0;

        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += tickTimeHistory[i];
        }

        return (sum / count) / 1_000_000.0;
    }

    /**
     * Get overall average tick time (ms)
     */
    public double getAverageTickTime() {
        long count = tickCount.get();
        if (count == 0) return 0;

        return (totalTickTime.get() / count) / 1_000_000.0;
    }

    /**
     * Get peak tick time (ms)
     */
    public double getPeakTickTime() {
        return peakTickTime.get() / 1_000_000.0;
    }

    public long getDeferredCount() {
        return deferredCount.get();
    }

    public long getAdaptiveChanges() {
        return adaptiveChanges.get();
    }

    /**
     * Get metrics for a specific system
     */
    public SystemMetrics getSystemMetrics(String systemId) {
        return systemMetrics.get(systemId);
    }

    /**
     * Get all system IDs with metrics
     */
    public java.util.Set<String> getTrackedSystems() {
        return systemMetrics.keySet();
    }

    /**
     * Get full performance report ({@code /ee performance}): the tick, then every system, the costliest first.
     */
    public String getPerformanceReport() {
        StringBuilder sb = new StringBuilder();
        int budget = tickBudgetMs();
        sb.append("=== Eternal Engine performance ===\n");
        sb.append(String.format("Ticks measured: %d · average %.2f ms · last %d ticks %.2f ms · peak %.2f ms · budget %d ms%n",
            tickCount.get(), getAverageTickTime(), ROLLING_WINDOW, getRollingAverageTickTime(), getPeakTickTime(), budget));
        sb.append(String.format("Seconds over budget: %d · systems moved to the next tick (tick full): %d · rate changes: %d%n",
            budgetExceededCount.get(), deferredCount.get(), adaptiveChanges.get()));
        Map<String, SystemRunner.Failure> failures = SimulationEngine.isInitialized()
            ? SimulationEngine.getInstance().systems().failures() : Map.of();
        SimulationTickScheduler scheduler = SimulationTickScheduler.isInitialized()
            ? SimulationTickScheduler.getInstance() : null;

        List<Map.Entry<String, SystemMetrics>> rows = new ArrayList<>(systemMetrics.entrySet());
        // Most time per tick first: recent ms per run divided by the ticks between runs.
        rows.sort(Comparator.comparingDouble((Map.Entry<String, SystemMetrics> e) ->
            -e.getValue().getRecentAverage() / Math.max(1, scheduler == null ? 1 : Math.max(1, scheduler.getInterval(e.getKey())))));
        sb.append("--- Per system (most time per tick first) ---\n");
        for (Map.Entry<String, SystemMetrics> e : rows) {
            SystemMetrics m = e.getValue();
            String id = e.getKey();
            int interval = scheduler == null ? -1 : scheduler.getInterval(id);
            int mult = scheduler == null ? 1 : scheduler.getMultiplier(id);
            SystemRunner.Failure f = failures.get(id);
            sb.append(String.format("  %s: %.3f ms per run (peak %.2f), %d runs, %s%s%s%s%n",
                id, m.getRecentAverage(), m.getPeakTime(), m.getSampleCount(),
                interval > 1 ? "every " + interval + " ticks" : "every tick",
                mult > 1 ? " (slowed " + mult + "x)" : "",
                m.deferred.get() > 0 ? ", waited " + m.deferred.get() + "x" : "",
                f == null ? "" : ", FAILED " + f.count() + "x: " + f.lastError()));
        }
        for (Map.Entry<String, SystemRunner.Failure> f : failures.entrySet()) {
            if (!systemMetrics.containsKey(f.getKey())) {
                sb.append("  ").append(f.getKey()).append(": FAILED ").append(f.getValue().count()).append("x: ")
                    .append(f.getValue().lastError()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Reset all metrics
     */
    public void reset() {
        systemMetrics.clear();
        totalTickTime.set(0);
        tickCount.set(0);
        budgetExceededCount.set(0);
        peakTickTime.set(0);
        deferredCount.set(0);
        historyIndex = 0;
        overBudgetStreak = 0;
        underBudgetStreak = 0;

        LOGGER.info("[PerformanceMonitor] Metrics reset");
    }

    /**
     * Per-system metrics tracking
     */
    public static class SystemMetrics {
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicLong sampleCount = new AtomicLong(0);
        private final AtomicLong peakTime = new AtomicLong(0);
        private final AtomicLong overBudgetCount = new AtomicLong(0);
        final AtomicLong deferred = new AtomicLong(0);

        // Rolling samples
        private final long[] samples = new long[100];
        private int sampleIndex = 0;

        public void addSample(long durationNanos) {
            totalTime.addAndGet(durationNanos);
            sampleCount.incrementAndGet();

            // Update peak
            long currentPeak = peakTime.get();
            while (durationNanos > currentPeak && !peakTime.compareAndSet(currentPeak, durationNanos)) {
                currentPeak = peakTime.get();
            }

            // Add to rolling samples
            synchronized (samples) {
                samples[sampleIndex] = durationNanos;
                sampleIndex = (sampleIndex + 1) % samples.length;
            }
        }

        public void recordOverBudget() {
            overBudgetCount.incrementAndGet();
        }

        public double getAverageTime() {
            long count = sampleCount.get();
            if (count == 0) return 0;
            return (totalTime.get() / count) / 1_000_000.0;
        }

        /** Average of the last (up to) 100 runs, in ms. */
        public double getRecentAverage() {
            long count = Math.min(sampleCount.get(), samples.length);
            if (count == 0) return 0;
            long sum = 0;
            synchronized (samples) {
                for (int i = 0; i < count; i++) {
                    sum += samples[i];
                }
            }
            return (sum / (double) count) / 1_000_000.0;
        }

        public double getPeakTime() {
            return peakTime.get() / 1_000_000.0;
        }

        public long getSampleCount() {
            return sampleCount.get();
        }

        public long getDeferredCount() {
            return deferred.get();
        }

        public double getOverBudgetPercent() {
            long count = sampleCount.get();
            if (count == 0) return 0;
            return (double) overBudgetCount.get() / count;
        }
    }
}
