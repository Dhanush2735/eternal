package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PerformanceMonitor - Tracks performance metrics for all systems.
 * 
 * Features:
 * - AtomicLong timer per system
 * - Warning when system exceeds budget
 * - Rolling average over 100 ticks
 * - Peak tracking per system
 * - /ee performance command output
 */
public class PerformanceMonitor {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static PerformanceMonitor instance;
    private static boolean initialized = false;
    
    // Rolling average window size
    private static final int ROLLING_WINDOW = 100;
    
    // System timers (in nanoseconds)
    private final ConcurrentHashMap<String, SystemMetrics> systemMetrics;
    
    // Global metrics
    private final AtomicLong totalTickTime = new AtomicLong(0);
    private final AtomicLong tickCount = new AtomicLong(0);
    private final AtomicLong budgetExceededCount = new AtomicLong(0);
    private final AtomicLong peakTickTime = new AtomicLong(0);
    
    // Tick time history for rolling average
    private final long[] tickTimeHistory = new long[ROLLING_WINDOW];
    private int historyIndex = 0;
    
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
        
        // Check budget
        double durationMs = duration / 1_000_000.0;
        double budget = getSystemBudget(systemId);
        
        if (durationMs > budget) {
            metrics.recordOverBudget();
            
            if (EternalEngineConfig.COMMON.debugLogging.get()) {
                LOGGER.warn("[PerformanceMonitor] {} exceeded budget: {:.2f}ms > {:.2f}ms",
                    systemId, durationMs, budget);
            }
            
            // Request frequency reduction if consistently over budget
            if (metrics.getOverBudgetPercent() > 0.1) { // More than 10% of ticks
                SimulationTickScheduler.getInstance().reduceFrequency(systemId);
            }
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
        if (durationNanos > currentPeak) {
            peakTickTime.compareAndSet(currentPeak, durationNanos);
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
    
    /**
     * Get budget for a system (in ms)
     */
    private double getSystemBudget(String systemId) {
        // Total budget from config, distributed by weight
        int totalBudget = EternalEngineConfig.SERVER.maxTickBudgetMs.get();
        
        // System weights
        return switch (systemId) {
            case "events" -> totalBudget * 0.2;    // 20%
            case "war" -> totalBudget * 0.15;      // 15%
            case "disease" -> totalBudget * 0.1;   // 10%
            case "population" -> totalBudget * 0.1; // 10%
            case "economy" -> totalBudget * 0.05;   // 5%
            case "reputation" -> totalBudget * 0.05; // 5%
            case "buildings" -> totalBudget * 0.05;  // 5%
            case "blocks" -> totalBudget * 0.1;      // 10%
            default -> totalBudget * 0.02;          // 2% for others
        };
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
     * Get full performance report
     */
    public String getPerformanceReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Eternal Engine Performance Report ===\n");
        sb.append(String.format("Ticks Processed: %d\n", tickCount.get()));
        sb.append(String.format("Average Tick Time: %.2f ms\n", getAverageTickTime()));
        sb.append(String.format("Rolling Average (last %d): %.2f ms\n", ROLLING_WINDOW, getRollingAverageTickTime()));
        sb.append(String.format("Peak Tick Time: %.2f ms\n", getPeakTickTime()));
        sb.append(String.format("Budget Exceeded: %d times\n", budgetExceededCount.get()));
        sb.append("\n--- System Breakdown ---\n");
        
        for (String systemId : systemMetrics.keySet()) {
            SystemMetrics metrics = systemMetrics.get(systemId);
            sb.append(String.format("  %s: avg=%.2fms, peak=%.2fms, samples=%d, overBudget=%.1f%%\n",
                systemId,
                metrics.getAverageTime(),
                metrics.getPeakTime(),
                metrics.getSampleCount(),
                metrics.getOverBudgetPercent() * 100
            ));
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
        historyIndex = 0;
        
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
        
        // Rolling samples
        private final long[] samples = new long[100];
        private int sampleIndex = 0;
        
        public void addSample(long durationNanos) {
            totalTime.addAndGet(durationNanos);
            sampleCount.incrementAndGet();
            
            // Update peak
            long currentPeak = peakTime.get();
            if (durationNanos > currentPeak) {
                peakTime.compareAndSet(currentPeak, durationNanos);
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
        
        public double getPeakTime() {
            return peakTime.get() / 1_000_000.0;
        }
        
        public long getSampleCount() {
            return sampleCount.get();
        }
        
        public double getOverBudgetPercent() {
            long count = sampleCount.get();
            if (count == 0) return 0;
            return (double) overBudgetCount.get() / count;
        }
    }
}
