package com.eternalengine.disease;

import com.eternalengine.buildings.BlockUpdateQueue;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Historical Disaster Engine.
 * Materializes real-world historical catastrophes (536 CE volcanic winters,
 * crop blight famines, river floods, and tectonic earthquakes) safely through BlockUpdateQueue.
 */
public final class HistoricalDisasterEngine {

    public enum DisasterType {
        FAMINE,
        GREAT_FLOOD,
        VOLCANIC_WINTER,
        EARTHQUAKE,
        GREAT_FIRE
    }

    public static final class DisasterIncident {
        private final String disasterId;
        private final DisasterType type;
        private final BlockPos centerPos;
        private final double radius;
        private final int year;
        private int remainingTicks;
        private boolean active = true;

        public DisasterIncident(String disasterId, DisasterType type, BlockPos centerPos,
                                double radius, int year, int durationTicks) {
            this.disasterId = disasterId;
            this.type = type;
            this.centerPos = centerPos;
            this.radius = radius;
            this.year = year;
            this.remainingTicks = durationTicks;
        }

        public String disasterId() { return disasterId; }
        public DisasterType type() { return type; }
        public BlockPos centerPos() { return centerPos; }
        public double radius() { return radius; }
        public int year() { return year; }
        public int remainingTicks() { return remainingTicks; }
        public boolean isActive() { return active; }
        public void tick() {
            if (remainingTicks > 0) remainingTicks--;
            if (remainingTicks <= 0) active = false;
        }
    }

    private final ConcurrentHashMap<String, DisasterIncident> activeDisasters = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public DisasterIncident triggerDisaster(DisasterType type, BlockPos center, int year,
                                            ServerLevel level, BlockUpdateQueue blockQueue) {
        if (level == null || center == null) return null;

        String id = "disaster_" + type.name().toLowerCase(java.util.Locale.ROOT) + "_" + System.currentTimeMillis() % 10000;
        double radius = 16.0D;
        int duration = 600; // 30 seconds active

        // Materialize physical disaster impact safely via BlockUpdateQueue
        if (blockQueue != null) {
            applyDisasterPhysicalEffects(type, center, radius, level, blockQueue);
        }

        DisasterIncident incident = new DisasterIncident(id, type, center, radius, year, duration);
        activeDisasters.put(id, incident);
        return incident;
    }

    private void applyDisasterPhysicalEffects(DisasterType type, BlockPos center, double radius,
                                              ServerLevel level, BlockUpdateQueue blockQueue) {
        int r = (int) radius;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (dx * dx + dz * dz > radius * radius) continue;

                int wx = center.getX() + dx;
                int wz = center.getZ() + dz;
                int sy = TerraCoordinateHelper.getSurfaceY(level, wx, wz);
                BlockPos surfacePos = new BlockPos(wx, sy + 1, wz);

                switch (type) {
                    case VOLCANIC_WINTER -> {
                        // Frost snow covering and ice sheets
                        if (surfacePos.getY() > sy) {
                            blockQueue.enqueue(level.dimension(), surfacePos, Blocks.SNOW.defaultBlockState(), 5);
                        }
                    }
                    case GREAT_FLOOD -> {
                        // Mud and waterlogging
                        if (surfacePos.getY() > sy) {
                            blockQueue.enqueue(level.dimension(), surfacePos.below(), Blocks.MUD.defaultBlockState(), 5);
                        }
                    }
                    case FAMINE -> {
                        // Farmland degradation into coarse dirt
                        BlockPos ground = surfacePos.below();
                        BlockState cur = level.getBlockState(ground);
                        if (cur.is(Blocks.FARMLAND) || cur.is(Blocks.GRASS_BLOCK)) {
                            blockQueue.enqueue(level.dimension(), ground, Blocks.COARSE_DIRT.defaultBlockState(), 5);
                        }
                    }
                    case EARTHQUAKE -> {
                        // Cracks masonry
                        BlockPos stonePos = surfacePos.below();
                        BlockState cur = level.getBlockState(stonePos);
                        if (cur.is(Blocks.STONE_BRICKS)) {
                            blockQueue.enqueue(level.dimension(), stonePos, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 5);
                        }
                    }
                    case GREAT_FIRE -> {
                        // Fire hazard on flammable surface
                        if (surfacePos.getY() > sy) {
                            blockQueue.enqueue(level.dimension(), surfacePos, Blocks.FIRE.defaultBlockState(), 5);
                        }
                    }
                }
            }
        }
    }

    public void tick(ServerLevel level) {
        if (level == null || activeDisasters.isEmpty()) return;
        tickCounter++;

        activeDisasters.values().removeIf(d -> {
            d.tick();
            if (!d.isActive()) return true;

            // Emit ambient disaster particles
            if (tickCounter % 20 == 0) {
                BlockPos p = d.centerPos();
                if (d.type() == DisasterType.VOLCANIC_WINTER) {
                    level.sendParticles(ParticleTypes.ASH, p.getX(), p.getY() + 3, p.getZ(), 20, 8.0D, 3.0D, 8.0D, 0.05D);
                } else if (d.type() == DisasterType.GREAT_FIRE) {
                    level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 15, 6.0D, 2.0D, 6.0D, 0.02D);
                } else if (d.type() == DisasterType.EARTHQUAKE) {
                    level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 10, 6.0D, 1.0D, 6.0D, 0.05D);
                }
            }
            return false;
        });
    }

    public List<DisasterIncident> getActiveDisasters() {
        return List.copyOf(activeDisasters.values());
    }

    public int activeCount() {
        return activeDisasters.size();
    }
}
