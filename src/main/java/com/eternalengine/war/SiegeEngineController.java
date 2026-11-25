package com.eternalengine.war;

import com.eternalengine.buildings.BlockUpdateQueue;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tactical siege operations against fortifications.
 * Delivers periodic siege strikes that crack and breach walls safely through BlockUpdateQueue.
 */
public final class SiegeEngineController {

    private final ConcurrentHashMap<String, SiegeOperation> activeSieges = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public void startSiege(String siegeId, String attackerCiv, String defenderCiv, BlockPos targetPos, ResourceKey<Level> dimension) {
        if (siegeId == null || targetPos == null || dimension == null) return;
        SiegeOperation op = new SiegeOperation(siegeId, attackerCiv, defenderCiv, targetPos, dimension, 0);
        activeSieges.put(siegeId, op);
    }

    public void endSiege(String siegeId) {
        if (siegeId != null) {
            activeSieges.remove(siegeId);
        }
    }

    public void tick(ServerLevel level, BlockUpdateQueue blockQueue) {
        if (level == null || activeSieges.isEmpty()) return;

        for (SiegeOperation siege : activeSieges.values()) {
            if (!siege.dimension.equals(level.dimension())) continue;
            siege.ticksActive++;

            // Deliver artillery strike every 100 ticks (5 seconds)
            if (siege.ticksActive % 100 == 0) {
                executeSiegeStrike(level, siege, blockQueue);
            }
        }
    }

    private void executeSiegeStrike(ServerLevel level, SiegeOperation siege, BlockUpdateQueue blockQueue) {
        BlockPos center = siege.targetPos;
        int dx = random.nextInt(11) - 5;
        int dz = random.nextInt(11) - 5;
        int x = center.getX() + dx;
        int z = center.getZ() + dz;

        int surfaceY = TerraCoordinateHelper.getSurfaceY(level, x, z);
        int targetY = surfaceY + 2 + random.nextInt(4);
        BlockPos strikePos = new BlockPos(x, targetY, z);

        if (!level.hasChunkAt(strikePos)) return;
        BlockState current = level.getBlockState(strikePos);

        // Visual and audio effects
        level.sendParticles(ParticleTypes.EXPLOSION, strikePos.getX() + 0.5D, strikePos.getY() + 0.5D, strikePos.getZ() + 0.5D,
            2, 0.2D, 0.2D, 0.2D, 0.05D);
        level.playSound(null, strikePos, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7F, 0.8F);

        // Structural damage via BlockUpdateQueue (safe above surface)
        if (blockQueue != null && strikePos.getY() > surfaceY && !current.isAir()) {
            BlockState damaged = getDamagedVariant(current);
            blockQueue.enqueue(level.dimension(), strikePos, damaged, 5);
        }
    }

    private BlockState getDamagedVariant(BlockState state) {
        if (state.is(Blocks.STONE_BRICKS)) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        } else if (state.is(Blocks.COBBLESTONE)) {
            return Blocks.GRAVEL.defaultBlockState();
        } else if (state.is(Blocks.CRACKED_STONE_BRICKS)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        // Breached block becomes air
        return Blocks.AIR.defaultBlockState();
    }

    public List<SiegeOperation> getActiveSieges() {
        return List.copyOf(activeSieges.values());
    }

    public int activeCount() {
        return activeSieges.size();
    }

    public static final class SiegeOperation {
        private final String siegeId;
        private final String attackerCiv;
        private final String defenderCiv;
        private final BlockPos targetPos;
        private final ResourceKey<Level> dimension;
        private int ticksActive;

        public SiegeOperation(String siegeId, String attackerCiv, String defenderCiv,
                              BlockPos targetPos, ResourceKey<Level> dimension, int ticksActive) {
            this.siegeId = siegeId;
            this.attackerCiv = attackerCiv;
            this.defenderCiv = defenderCiv;
            this.targetPos = targetPos;
            this.dimension = dimension;
            this.ticksActive = ticksActive;
        }

        public String siegeId() { return siegeId; }
        public String attackerCiv() { return attackerCiv; }
        public String defenderCiv() { return defenderCiv; }
        public BlockPos targetPos() { return targetPos; }
        public ResourceKey<Level> dimension() { return dimension; }
        public int ticksActive() { return ticksActive; }
    }
}
