package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import org.slf4j.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * AsyncTaskManager - safe asynchronous execution for Eternal Engine.
 *
 * Rules:
 * - Heavy work runs off the server thread.
 * - Minecraft world edits must be scheduled back through submitToMainThread().
 * - Main-thread callback draining is time-budgeted, not count-only.
 * - Priority queue uses comparable FutureTask objects to avoid runtime queue errors.
 */
public class AsyncTaskManager {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private static AsyncTaskManager instance;
    private static boolean initialized = false;

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 4;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final long MAIN_THREAD_BUDGET_NANOS = 2_000_000L;

    private final ThreadPoolExecutor executor;
    private final ConcurrentLinkedQueue<Runnable> mainThreadCallbacks;
    private final ConcurrentHashMap<String, Future<?>> activeTasks;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final AtomicInteger tasksSubmitted = new AtomicInteger(0);
    private final AtomicInteger tasksCompleted = new AtomicInteger(0);
    private final AtomicInteger tasksFailed = new AtomicInteger(0);
    private final AtomicInteger tasksCancelled = new AtomicInteger(0);

    private AsyncTaskManager() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "EE-Async-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };

        this.executor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy()
        );
        this.mainThreadCallbacks = new ConcurrentLinkedQueue<>();
        this.activeTasks = new ConcurrentHashMap<>();
    }

    public static synchronized void initialize() {
        if (initialized) {
            LOGGER.warn("[AsyncTaskManager] Already initialized");
            return;
        }
        instance = new AsyncTaskManager();
        initialized = true;
        LOGGER.info("[AsyncTaskManager] Initialized with {} threads", CORE_POOL_SIZE);
    }

    public static AsyncTaskManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("AsyncTaskManager not initialized!");
        }
        return instance;
    }

    public void submit(String taskId, Runnable task, Consumer<Boolean> onComplete, int priority) {
        if (task == null) {
            if (onComplete != null) {
                submitToMainThread(() -> onComplete.accept(false));
            }
            return;
        }
        if (shutdown.get()) {
            LOGGER.warn("[AsyncTaskManager] Rejecting task {} because manager is shut down", taskId);
            if (onComplete != null) {
                submitToMainThread(() -> onComplete.accept(false));
            }
            return;
        }

        String safeId = safeTaskId(taskId);
        tasksSubmitted.incrementAndGet();

        PriorityFutureTask<Void> futureTask = new PriorityFutureTask<>(priority, () -> {
            boolean success = false;
            try {
                task.run();
                success = true;
                tasksCompleted.incrementAndGet();
            } catch (CancellationException e) {
                tasksCancelled.incrementAndGet();
                LOGGER.debug("[AsyncTaskManager] Task {} cancelled", safeId);
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                LOGGER.error("[AsyncTaskManager] Task {} failed: {}", safeId, e.getMessage());
            } finally {
                activeTasks.remove(safeId);
                if (onComplete != null) {
                    boolean finalSuccess = success;
                    submitToMainThread(() -> onComplete.accept(finalSuccess));
                }
            }
            return null;
        });

        activeTasks.put(safeId, futureTask);
        executor.execute(futureTask);
    }

    public void submit(String taskId, Runnable task, Consumer<Boolean> onComplete) {
        submit(taskId, task, onComplete, 0);
    }

    public void submit(String taskId, Runnable task) {
        submit(taskId, task, null, 0);
    }

    public <T> CompletableFuture<T> submitCallable(String taskId, Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (task == null) {
            result.completeExceptionally(new IllegalArgumentException("task is null"));
            return result;
        }
        if (shutdown.get()) {
            result.completeExceptionally(new IllegalStateException("AsyncTaskManager is shut down"));
            return result;
        }

        String safeId = safeTaskId(taskId);
        tasksSubmitted.incrementAndGet();
        PriorityFutureTask<T> futureTask = new PriorityFutureTask<>(0, () -> {
            try {
                T value = task.call();
                tasksCompleted.incrementAndGet();
                result.complete(value);
                return value;
            } catch (CancellationException e) {
                tasksCancelled.incrementAndGet();
                result.completeExceptionally(e);
                throw e;
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                LOGGER.error("[AsyncTaskManager] Callable {} failed: {}", safeId, e.getMessage());
                result.completeExceptionally(e);
                throw e;
            } finally {
                activeTasks.remove(safeId);
            }
        });
        activeTasks.put(safeId, futureTask);
        executor.execute(futureTask);
        return result;
    }

    public void submitWithRetry(String taskId, Runnable task, Consumer<Boolean> onComplete,
                                int priority, int maxRetries) {
        submitWithRetryInternal(safeTaskId(taskId), task, onComplete, priority, Math.max(0, maxRetries), 0);
    }

    private void submitWithRetryInternal(String taskId, Runnable task, Consumer<Boolean> onComplete,
                                         int priority, int maxRetries, int attempt) {
        submit(taskId + "_attempt" + attempt, () -> {
            try {
                task.run();
                if (onComplete != null) {
                    submitToMainThread(() -> onComplete.accept(true));
                }
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    long delayMs = (long) Math.pow(2, attempt) * 100L;
                    LOGGER.debug("[AsyncTaskManager] Task {} failed, retrying in {}ms", taskId, delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interrupted);
                    }
                    submitWithRetryInternal(taskId, task, onComplete, priority, maxRetries, attempt + 1);
                } else {
                    LOGGER.error("[AsyncTaskManager] Task {} failed after {} attempts", taskId, maxRetries + 1);
                    if (onComplete != null) {
                        submitToMainThread(() -> onComplete.accept(false));
                    }
                }
            }
        }, null, priority);
    }

    public void submitToMainThread(Runnable task) {
        if (task != null && !shutdown.get()) {
            mainThreadCallbacks.offer(task);
        }
    }

    public boolean cancelTask(String taskId) {
        Future<?> future = activeTasks.remove(safeTaskId(taskId));
        if (future == null) {
            return false;
        }
        boolean cancelled = future.cancel(true);
        if (cancelled) {
            tasksCancelled.incrementAndGet();
        }
        return cancelled;
    }

    /**
     * Drain callbacks within a 2ms budget. Must be called on the server thread.
     */
    public void processMainThreadCallbacks() {
        long start = System.nanoTime();
        int processed = 0;
        Runnable callback;
        while ((callback = mainThreadCallbacks.poll()) != null) {
            try {
                callback.run();
                processed++;
            } catch (Exception e) {
                LOGGER.error("[AsyncTaskManager] Main-thread callback failed: {}", e.getMessage());
            }
            if (System.nanoTime() - start >= MAIN_THREAD_BUDGET_NANOS) {
                break;
            }
        }
        if (processed > 0 && !mainThreadCallbacks.isEmpty()) {
            LOGGER.debug("[AsyncTaskManager] Drained {} callbacks; {} remain", processed, mainThreadCallbacks.size());
        }
    }

    public boolean isTaskRunning(String taskId) {
        Future<?> future = activeTasks.get(safeTaskId(taskId));
        return future != null && !future.isDone();
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    public int getQueuedTaskCount() {
        return executor.getQueue().size();
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    public String getStatistics() {
        return String.format(
            "Tasks - Submitted: %d, Completed: %d, Failed: %d, Cancelled: %d, Active: %d, Queued: %d",
            tasksSubmitted.get(), tasksCompleted.get(), tasksFailed.get(), tasksCancelled.get(),
            getActiveTaskCount(), getQueuedTaskCount()
        );
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("[AsyncTaskManager] Shutting down...");
        for (String taskId : activeTasks.keySet()) {
            cancelTask(taskId);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        processMainThreadCallbacks();
        LOGGER.info("[AsyncTaskManager] Shutdown complete. {}", getStatistics());
    }

    private String safeTaskId(String taskId) {
        return taskId == null || taskId.isBlank() ? "task_" + System.nanoTime() : taskId;
    }

    private static final class PriorityFutureTask<T> extends FutureTask<T> implements Comparable<PriorityFutureTask<?>> {
        private static final AtomicInteger SEQUENCE = new AtomicInteger(0);
        private final int priority;
        private final int sequence;

        private PriorityFutureTask(int priority, Callable<T> callable) {
            super(callable);
            this.priority = priority;
            this.sequence = SEQUENCE.getAndIncrement();
        }

        @Override
        public int compareTo(PriorityFutureTask<?> other) {
            int priorityCompare = Integer.compare(other.priority, this.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Integer.compare(this.sequence, other.sequence);
        }
    }
}
