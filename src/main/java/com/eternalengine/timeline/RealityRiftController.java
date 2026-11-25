package com.eternalengine.timeline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages physicalized spatial reality rifts appearing at historical pivot points
 * where players have diverged from the Prime Timeline.
 */
public final class RealityRiftController {

    public record RealityRift(
        String riftId,
        String pivotEventId,
        String branchId,
        int divergenceYear,
        BlockPos centerPos,
        double instability,
        String primeTimelineEcho
    ) {}

    private final ConcurrentHashMap<String, RealityRift> activeRifts = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public RealityRift spawnRift(String pivotEventId, String branchId, int year,
                                 BlockPos pos, String primeEcho, ServerLevel level) {
        if (pos == null) return null;

        String id = "rift_" + pivotEventId + "_" + System.currentTimeMillis() % 10000;
        RealityRift rift = new RealityRift(id, pivotEventId, branchId, year, pos, 0.75D, primeEcho);
        activeRifts.put(id, rift);
        return rift;
    }

    public void tick(ServerLevel level) {
        if (level == null || activeRifts.isEmpty()) return;
        tickCounter++;

        for (RealityRift rift : activeRifts.values()) {
            BlockPos p = rift.centerPos();

            // Emit shimmering reality rift particles every 5 ticks
            if (tickCounter % 5 == 0) {
                level.sendParticles(ParticleTypes.PORTAL,
                    p.getX() + 0.5D, p.getY() + 1.2D, p.getZ() + 0.5D,
                    12, 0.4D, 0.8D, 0.4D, 0.05D);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    p.getX() + 0.5D, p.getY() + 1.2D, p.getZ() + 0.5D,
                    6, 0.3D, 0.6D, 0.3D, 0.02D);
                level.sendParticles(ParticleTypes.END_ROD,
                    p.getX() + 0.5D, p.getY() + 1.5D, p.getZ() + 0.5D,
                    2, 0.1D, 0.2D, 0.1D, 0.01D);
            }

            // Proximity whisper when a player gets close (< 3.5 blocks)
            if (tickCounter % 60 == 0) {
                AABB whisperZone = new AABB(p).inflate(3.5D);
                List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, whisperZone);
                for (ServerPlayer player : players) {
                    player.sendSystemMessage(Component.literal("§d[Reality Rift] §7Through the shimmering tear in spacetime, you perceive: §f"
                        + rift.primeTimelineEcho()));
                }
            }
        }
    }

    public List<RealityRift> getActiveRifts() {
        return List.copyOf(activeRifts.values());
    }

    public RealityRift getRift(String id) {
        return activeRifts.get(id);
    }

    public int activeRiftCount() {
        return activeRifts.size();
    }
}
