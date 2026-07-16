package com.eternalengine.buildings;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main-thread block placement queue.
 *
 * Safety rules:
 * - Process only from server tick/main thread.
 * - Skip unloaded chunks.
 * - Never place at or below Terra++ surface.
 * - Limit blocks processed per tick.
 * - Allow cancellation by chunk key.
 */
public final class BlockUpdateQueue {

    private static final Logger LOGGER = EternalEngineMod.getLogger();
    public static final int DEFAULT_MAX_BLOCKS_PER_TICK = 500;

    private final PriorityBlockingQueue<BlockPlacementTask> queue = new PriorityBlockingQueue<>();
    private final Set<String> cancelledChunkKeys = ConcurrentHashMap.newKeySet();
    private final AtomicInteger queuedCount = new AtomicInteger(0);
    private final AtomicInteger processedTotal = new AtomicInteger(0);
    private final AtomicInteger skippedTotal = new AtomicInteger(0);
    private final AtomicLong sequence = new AtomicLong(0L);

    public void enqueue(ResourceKey<Level> dimension, BlockPos pos, BlockState state, int priority) {
        if (dimension == null || pos == null || state == null) {
            skippedTotal.incrementAndGet();
            return;
        }
        BlockPlacementTask task = new BlockPlacementTask(pos.immutable(), state, priority, sequence.getAndIncrement(), chunkKey(dimension, pos), dimension);
        queue.offer(task);
        queuedCount.incrementAndGet();
    }

    public void enqueueMany(ResourceKey<Level> dimension, Map<BlockPos, BlockState> blocks, int priority) {
        if (dimension == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            enqueue(dimension, entry.getKey(), entry.getValue(), priority);
        }
    }

    public void cancel(String chunkKey) {
        if (chunkKey != null && !chunkKey.isBlank()) {
            cancelledChunkKeys.add(chunkKey);
        }
    }

    public void cancel(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (dimension != null) {
            cancel(dimension.location() + ":" + chunkX + ":" + chunkZ);
        }
    }

    /**
     * Processes queued placements. Must be called from the main server thread.
     */
    public int tick(MinecraftServer server, int maxBlocks) {
        if (server == null) {
            return 0;
        }
        int budget = Math.max(0, maxBlocks <= 0 ? DEFAULT_MAX_BLOCKS_PER_TICK : maxBlocks);
        int processedThisTick = 0;
        while (processedThisTick < budget) {
            BlockPlacementTask task = queue.poll();
            if (task == null) {
                break;
            }
            queuedCount.updateAndGet(value -> Math.max(0, value - 1));
            if (task.cancelled() || cancelledChunkKeys.contains(task.chunkKey())) {
                skippedTotal.incrementAndGet();
                continue;
            }
            ServerLevel level = server.getLevel(task.dimension());
            if (!canPlace(level, task.pos())) {
                skippedTotal.incrementAndGet();
                continue;
            }
            level.setBlock(task.pos(), task.state(), 3);
            processedThisTick++;
            processedTotal.incrementAndGet();
        }
        return processedThisTick;
    }

    public int tick(MinecraftServer server) {
        return tick(server, DEFAULT_MAX_BLOCKS_PER_TICK);
    }

    public boolean canPlace(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        int surfaceY = TerraCoordinateHelper.getSurfaceY(level, pos.getX(), pos.getZ());
        if (pos.getY() <= surfaceY) {
            LOGGER.debug("[BlockUpdateQueue] Skipped below-surface placement at {} surfaceY={}", pos, surfaceY);
            return false;
        }
        return pos.getY() >= TerraCoordinateHelper.getMinY() && pos.getY() <= TerraCoordinateHelper.getMaxY();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
        queuedCount.set(0);
        cancelledChunkKeys.clear();
    }

    public int queuedCount() { return Math.max(0, queuedCount.get()); }
    public int processedTotal() { return processedTotal.get(); }
    public int skippedTotal() { return skippedTotal.get(); }

    public String stats() {
        return "BlockUpdateQueue queued=" + size()
            + " processed=" + processedTotal.get()
            + " skipped=" + skippedTotal.get()
            + " cancelledChunks=" + cancelledChunkKeys.size();
    }

    public static String chunkKey(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + ":" + Math.floorDiv(pos.getX(), 16) + ":" + Math.floorDiv(pos.getZ(), 16);
    }

    public static final class BlockPlacementTask implements Comparable<BlockPlacementTask> {
        private final BlockPos pos;
        private final BlockState state;
        private final int priority;
        private final long sequence;
        private final String chunkKey;
        private final ResourceKey<Level> dimension;
        private volatile boolean cancelled;

        public BlockPlacementTask(BlockPos pos, BlockState state, int priority, long sequence, String chunkKey, ResourceKey<Level> dimension) {
            this.pos = pos;
            this.state = state;
            this.priority = priority;
            this.sequence = sequence;
            this.chunkKey = chunkKey;
            this.dimension = dimension;
        }

        @Override
        public int compareTo(BlockPlacementTask other) {
            int priorityCompare = Integer.compare(this.priority, other.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(this.sequence, other.sequence);
        }

        public void cancel() { this.cancelled = true; }
        public BlockPos pos() { return pos; }
        public BlockState state() { return state; }
        public int priority() { return priority; }
        public long sequence() { return sequence; }
        public String chunkKey() { return chunkKey; }
        public ResourceKey<Level> dimension() { return dimension; }
        public boolean cancelled() { return cancelled; }
    }
}
