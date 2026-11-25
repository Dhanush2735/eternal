package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import org.slf4j.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs every world system in its own safety net and times it (step 14).
 *
 * <p>Before, the whole tick sat in one {@code try/catch}: when one system threw, every system after it
 * was skipped for that tick, including the autosave at the end, and the full error went to the log
 * twenty times a second. One broken system could freeze the rest of the simulation and stop the saves
 * without anybody noticing. Now each system is run by {@link #run}: an error costs that system that one
 * run, the others go on, the first error of a system is logged with its stack trace and later ones at most
 * once a minute with a count. {@code /ee check all} and {@code /ee performance} list every failed system.
 */
public final class SystemRunner {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    /** Later failures of the same system are summarised at most this often. */
    private static final long LOG_EVERY_MS = 60_000L;

    /**
     * Step 18e: one run this long is a freeze the player feels, so it is logged with the system's name (at most once a
     * minute per system). The user's log showed "Tick budget exceeded! Avg: 464ms" and "Can't keep up! Running 8312ms"
     * right after joining, without saying which system.
     */
    private static final long SLOW_RUN_MS = 1000L;

    /** The slowest single run since {@link #takeSlowest()} (server thread only, like {@link #run}). */
    private String slowestId;
    private long slowestNanos;
    private final Map<String, Long> slowLoggedAt = new ConcurrentHashMap<>();

    /** What went wrong in one system since the world started. */
    public static final class Failure {
        private final String systemId;
        private final long firstAt;
        private long count;
        private long lastAt;
        private String lastError = "";
        private long loggedAt;
        private long countAtLog;

        Failure(String systemId, long now) {
            this.systemId = systemId;
            this.firstAt = now;
        }

        public synchronized String systemId() { return systemId; }
        public synchronized long count() { return count; }
        public synchronized long firstAt() { return firstAt; }
        public synchronized long lastAt() { return lastAt; }
        public synchronized String lastError() { return lastError; }
    }

    private final ConcurrentHashMap<String, Failure> failures = new ConcurrentHashMap<>();

    /**
     * Run one system. Never throws, except the errors the JVM cannot recover from (out of memory and the
     * like), which must still stop the server.
     *
     * @return true when the system finished without an error
     */
    public boolean run(String systemId, Runnable body) {
        long start = System.nanoTime();
        try {
            body.run();
            return true;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable t) {
            record(systemId, t);
            return false;
        } finally {
            if (PerformanceMonitor.isInitialized()) {
                PerformanceMonitor.getInstance().endSystem(systemId, start);
            }
            noteDuration(systemId, System.nanoTime() - start);
        }
    }

    private void noteDuration(String systemId, long nanos) {
        if (nanos > slowestNanos) {
            slowestNanos = nanos;
            slowestId = systemId;
        }
        long ms = nanos / 1_000_000L;
        if (ms >= SLOW_RUN_MS) {
            long now = System.currentTimeMillis();
            Long last = slowLoggedAt.get(systemId);
            if (last == null || now - last >= LOG_EVERY_MS) {
                slowLoggedAt.put(systemId, now);
                LOGGER.warn("[SimulationEngine] World system '{}' took {} ms in one tick: the game stood still that long"
                    + " (logged at most once a minute per system; /ee performance has the times)", systemId, ms);
            }
        }
    }

    /** "spawner 9104 ms": the slowest single run since the last call, then it starts again. Null when nothing ran. */
    public String takeSlowest() {
        String out = slowestId == null ? null : slowestId + " " + (slowestNanos / 1_000_000L) + " ms";
        slowestId = null;
        slowestNanos = 0L;
        return out;
    }

    private void record(String systemId, Throwable t) {
        long now = System.currentTimeMillis();
        Failure f = failures.computeIfAbsent(systemId, id -> new Failure(id, now));
        synchronized (f) {
            f.count++;
            f.lastAt = now;
            f.lastError = String.valueOf(t);
            if (f.count == 1) {
                f.loggedAt = now;
                f.countAtLog = 1;
                LOGGER.error("[SimulationEngine] World system '{}' failed; the other systems keep running. Further"
                    + " failures of '{}' are logged at most once a minute.", systemId, systemId, t);
            } else if (now - f.loggedAt >= LOG_EVERY_MS) {
                LOGGER.error("[SimulationEngine] World system '{}' failed {} more time(s) in the last minute ({} in"
                    + " total since the world started). Latest: {}", systemId, f.count - f.countAtLog, f.count,
                    f.lastError);
                f.loggedAt = now;
                f.countAtLog = f.count;
            }
        }
    }

    /** Every system that failed since the world started, by name (a copy). */
    public Map<String, Failure> failures() {
        return new TreeMap<>(failures);
    }

    public long totalFailures() {
        long n = 0;
        for (Failure f : failures.values()) {
            n += f.count();
        }
        return n;
    }

    /** A new world (or /ee reset): nothing has failed yet. */
    public void clear() {
        failures.clear();
    }
}
