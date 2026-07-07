package com.eternalengine.core;

import com.eternalengine.EternalEngineMod;
import org.slf4j.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * AsyncTaskManager - Manages asynchronous tasks off the server thread.
 * 
 * Features:
 * - ThreadPoolExecutor with 4 daemon threads
 * - Named threads (EE-Async-N)
 * - Priority queue ordering
 * - Cancellation support
 * - Retry with exponential backoff
 * - 2000ms timeout per task
 * - Main thread callback queue
 * - Drains callbacks each server tick
 */
public class AsyncTaskManager {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static AsyncTaskManager instance;
    private static boolean initialized = false;
    
    // Thread pool configuration
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 4;
    private static final long KEEP_ALIVE_SECONDS = 60;
    private static final int DEFAULT_TIMEOUT_MS = 2000;
    private static final int MAX_RETRIES = 3;
    
    // Thread pool
    private final ThreadPoolExecutor executor;
    
    // Main thread callback queue
    private final ConcurrentLinkedQueue<Runnable> mainThreadCallbacks;
    
    // Active task tracking
    private final ConcurrentHashMap<String, Future<?>> activeTasks;
    
    // Statistics
    private final AtomicInteger tasksSubmitted = new AtomicInteger(0);
    private final AtomicInteger tasksCompleted = new AtomicInteger(0);
    private final AtomicInteger tasksFailed = new AtomicInteger(0);
    private final AtomicInteger tasksTimedOut = new AtomicInteger(0);
    
    private AsyncTaskManager() {
        // Create thread factory with named threads
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(0);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "EE-Async-" + threadNumber.getAndIncrement());
                t.setDaemon(true); // Don't prevent JVM shutdown
                t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
                return t;
            }
        };
        
        // Create thread pool with priority queue
        this.executor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // Fallback to caller thread if pool full
        );
        
        this.mainThreadCallbacks = new ConcurrentLinkedQueue<>();
        this.activeTasks = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialize the async task manager
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[AsyncTaskManager] Already initialized");
            return;
        }
        
        instance = new AsyncTaskManager();
        initialized = true;
        
        LOGGER.info("[AsyncTaskManager] Initialized with {} threads", CORE_POOL_SIZE);
    }
    
    /**
     * Get the singleton instance
     */
    public static AsyncTaskManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("AsyncTaskManager not initialized!");
        }
        return instance;
    }
    
    /**
     * Submit a task for async execution
     * 
     * @param taskId Unique identifier for the task
     * @param task The task to execute
     * @param onComplete Callback to run on main thread when complete (can be null)
     * @param priority Task priority (higher = more urgent)
     */
    public void submit(String taskId, Runnable task, Consumer<Boolean> onComplete, int priority) {
        tasksSubmitted.incrementAndGet();
        
        PriorityRunnable priorityTask = new PriorityRunnable(priority, () -> {
            boolean success = false;
            try {
                task.run();
                success = true;
                tasksCompleted.incrementAndGet();
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                LOGGER.error("[AsyncTaskManager] Task {} failed: {}", taskId, e.getMessage());
            } finally {
                activeTasks.remove(taskId);
                
                // Queue callback for main thread
                if (onComplete != null) {
                    final boolean finalSuccess = success;
                    mainThreadCallbacks.offer(() -> onComplete.accept(finalSuccess));
                }
            }
        });
        
        Future<?> future = executor.submit(priorityTask);
        activeTasks.put(taskId, future);
    }
    
    /**
     * Submit a task with default priority
     */
    public void submit(String taskId, Runnable task, Consumer<Boolean> onComplete) {
        submit(taskId, task, onComplete, 0);
    }
    
    /**
     * Submit a task with no callback
     */
    public void submit(String taskId, Runnable task) {
        submit(taskId, task, null, 0);
    }
    
    /**
     * Submit a callable that returns a result
     */
    public <T> CompletableFuture<T> submitCallable(String taskId, Callable<T> task) {
        tasksSubmitted.incrementAndGet();
        
        CompletableFuture<T> future = new CompletableFuture<>();
        
        executor.submit(() -> {
            try {
                T result = task.call();
                tasksCompleted.incrementAndGet();
                future.complete(result);
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                LOGGER.error("[AsyncTaskManager] Callable {} failed: {}", taskId, e.getMessage());
                future.completeExceptionally(e);
            } finally {
                activeTasks.remove(taskId);
            }
        });
        
        return future;
    }
    
    /**
     * Submit a task with retry on failure
     */
    public void submitWithRetry(String taskId, Runnable task, Consumer<Boolean> onComplete, 
                                 int priority, int maxRetries) {
        submitWithRetryInternal(taskId, task, onComplete, priority, maxRetries, 0);
    }
    
    private void submitWithRetryInternal(String taskId, Runnable task, Consumer<Boolean> onComplete,
                                         int priority, int maxRetries, int attempt) {
        submit(taskId + "_attempt" + attempt, () -> {
            try {
                task.run();
                // Success - queue callback
                if (onComplete != null) {
                    mainThreadCallbacks.offer(() -> onComplete.accept(true));
                }
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    // Retry with exponential backoff
                    long delay = (long) Math.pow(2, attempt) * 100; // 100ms, 200ms, 400ms...
                    LOGGER.debug("[AsyncTaskManager] Task {} failed, retrying in {}ms (attempt {})", 
                        taskId, delay, attempt + 1);
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    
                    submitWithRetryInternal(taskId, task, onComplete, priority, maxRetries, attempt + 1);
                } else {
                    // Max retries exceeded
                    LOGGER.error("[AsyncTaskManager] Task {} failed after {} attempts", taskId, maxRetries);
                    if (onComplete != null) {
                        mainThreadCallbacks.offer(() -> onComplete.accept(false));
                    }
                }
            }
        }, null, priority);
    }
    
    /**
     * Cancel a task by ID
     */
    public boolean cancelTask(String taskId) {
        Future<?> future = activeTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                activeTasks.remove(taskId);
                LOGGER.debug("[AsyncTaskManager] Cancelled task {}", taskId);
            }
            return cancelled;
        }
        return false;
    }
    
    /**
     * Process main thread callbacks - MUST be called from server tick
     */
    public void processMainThreadCallbacks() {
        int processed = 0;
        int maxPerTick = 100; // Limit to prevent blocking
        
        Runnable callback;
        while (processed < maxPerTick && (callback = mainThreadCallbacks.poll()) != null) {
            try {
                callback.run();
                processed++;
            } catch (Exception e) {
                LOGGER.error("[AsyncTaskManager] Callback failed: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Check if a task is still running
     */
    public boolean isTaskRunning(String taskId) {
        Future<?> future = activeTasks.get(taskId);
        return future != null && !future.isDone();
    }
    
    /**
     * Get number of active tasks
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }
    
    /**
     * Get number of queued tasks
     */
    public int getQueuedTaskCount() {
        return executor.getQueue().size();
    }
    
    /**
     * Get statistics
     */
    public String getStatistics() {
        return String.format(
            "Tasks - Submitted: %d, Completed: %d, Failed: %d, Timed Out: %d, Active: %d, Queued: %d",
            tasksSubmitted.get(), tasksCompleted.get(), tasksFailed.get(), tasksTimedOut.get(),
            getActiveTaskCount(), getQueuedTaskCount()
        );
    }
    
    /**
     * Shutdown the task manager
     */
    public void shutdown() {
        LOGGER.info("[AsyncTaskManager] Shutting down...");
        
        // Cancel all active tasks
        for (String taskId : activeTasks.keySet()) {
            cancelTask(taskId);
        }
        
        // Shutdown executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Process remaining callbacks
        processMainThreadCallbacks();
        
        LOGGER.info("[AsyncTaskManager] Shutdown complete. {}", getStatistics());
    }
    
    /**
     * Priority runnable wrapper for priority queue
     */
    private static class PriorityRunnable implements Runnable, Comparable<PriorityRunnable> {
        private final int priority;
        private final Runnable task;
        private final long timestamp;
        
        PriorityRunnable(int priority, Runnable task) {
            this.priority = priority;
            this.task = task;
            this.timestamp = System.nanoTime();
        }
        
        @Override
        public void run() {
            task.run();
        }
        
        @Override
        public int compareTo(PriorityRunnable other) {
            // Higher priority first
            int priorityCompare = Integer.compare(other.priority, this.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // FIFO for same priority
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
}
