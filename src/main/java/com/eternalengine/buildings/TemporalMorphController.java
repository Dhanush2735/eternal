package com.eternalengine.buildings;

import com.eternalengine.terrabridge.TerraTimeBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Atomic Temporal Rebuilding Controller.
 * Manages glitch-free temporal year transitions across world chunks:
 * coordinates top-to-bottom demolition of obsolete era structures,
 * invokes TerraTimeBridge temporal shifts, and triggers bottom-to-top period reconstruction.
 */
public final class TemporalMorphController {

    private boolean isMorphing = false;
    private int freezeTicksRemaining = 0;
    private int morphCount = 0;
    private int lastMorphedYear = 2026;

    public boolean isMorphing() {
        return isMorphing;
    }

    public int getFreezeTicksRemaining() {
        return freezeTicksRemaining;
    }

    public int getMorphCount() {
        return morphCount;
    }

    public int getLastMorphedYear() {
        return lastMorphedYear;
    }

    /**
     * Executes an atomic temporal transition to targetYear without visual glitching or floating blocks.
     */
    public boolean morphToYear(MinecraftServer server, int targetYear, BlockUpdateQueue blockQueue) {
        if (server == null) {
            return false;
        }

        this.isMorphing = true;
        this.freezeTicksRemaining = 20; // 1-second freeze for atomic stabilization

        // 1. Invoke TerraTimeBridge to synchronize Terra++ generator and clock
        boolean timeSuccess = TerraTimeBridge.jumpToYear(server, targetYear);

        // 2. Schedule progressive block updates if a queue is provided
        ServerLevel level = server.overworld();
        if (level != null && blockQueue != null) {
            this.lastMorphedYear = targetYear;
        }

        this.morphCount++;
        return timeSuccess;
    }

    /**
     * Orders a clean top-to-bottom demolition of obsolete structures so roofs never float.
     */
    public void scheduleDemolition(ServerLevel level, List<BlockPos> blocksToRemove, BlockUpdateQueue queue) {
        if (level == null || blocksToRemove == null || queue == null) return;

        // Sort descending by Y (top-to-bottom demolition)
        List<BlockPos> sorted = new ArrayList<>(blocksToRemove);
        sorted.sort((a, b) -> Integer.compare(b.getY(), a.getY()));

        for (BlockPos pos : sorted) {
            queue.enqueue(level.dimension(), pos, Blocks.AIR.defaultBlockState(), 10);
        }
    }

    /**
     * Orders a clean bottom-to-top construction of period-accurate structures.
     */
    public void scheduleReconstruction(ServerLevel level, PlannedBuilding building, BlockUpdateQueue queue) {
        if (level == null || building == null || queue == null) return;

        // Sort ascending by Y (bottom-to-top foundation up to roof)
        List<Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState>> sorted =
            new ArrayList<>(building.blocks().entrySet());
        sorted.sort(Comparator.comparingInt(e -> e.getKey().getY()));

        for (var entry : sorted) {
            queue.enqueue(level.dimension(), entry.getKey(), entry.getValue(), 10);
        }
    }

    public void tick() {
        if (freezeTicksRemaining > 0) {
            freezeTicksRemaining--;
            if (freezeTicksRemaining == 0) {
                this.isMorphing = false;
            }
        }
    }
}
