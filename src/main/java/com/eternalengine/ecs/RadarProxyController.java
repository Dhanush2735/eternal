package com.eternalengine.ecs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tier-2 Radar Proxy Controller.
 * Manages lightweight non-ticking positional representations for agents situated
 * 64 to 192 blocks from active players. Zero entity allocations and zero AI overhead.
 */
public final class RadarProxyController {

    public record RadarProxyPoint(
        long agentId,
        int x,
        int y,
        int z,
        byte cultureId,
        byte roleId
    ) {}

    public List<RadarProxyPoint> collectProxiesNearPlayer(ServerPlayer player, StructOfArraysPopulationEcs ecs) {
        if (player == null || ecs == null || ecs.size() == 0) {
            return List.of();
        }

        BlockPos pPos = player.blockPosition();
        int px = pPos.getX();
        int pz = pPos.getZ();

        double innerRadiusSq = 64.0D * 64.0D;
        double outerRadiusSq = 192.0D * 192.0D;

        List<RadarProxyPoint> proxies = new ArrayList<>();
        int count = ecs.size();

        for (int i = 0; i < count; i++) {
            if (!ecs.isActive(i) || ecs.isMaterialized(i)) continue;

            double dx = ecs.getX(i) - px;
            double dz = ecs.getZ(i) - pz;
            double distSq = dx * dx + dz * dz;

            if (distSq >= innerRadiusSq && distSq <= outerRadiusSq) {
                proxies.add(new RadarProxyPoint(
                    ecs.getEntityId(i),
                    ecs.getX(i),
                    ecs.getY(i),
                    ecs.getZ(i),
                    ecs.getCulture(i),
                    ecs.getRole(i)
                ));

                if (proxies.size() >= 500) {
                    break; // Cap proxy transmission budget per player
                }
            }
        }

        return Collections.unmodifiableList(proxies);
    }
}
