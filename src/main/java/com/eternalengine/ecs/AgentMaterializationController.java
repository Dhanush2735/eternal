package com.eternalengine.ecs;

import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tier-3 Local Agent Materialization & State Flushing Controller.
 * Seamlessly materializes agents into full ServerLevel HumanNpcEntity instances
 * when players approach within 64 blocks, and cleanly flushes state back into the
 * 32-byte ECS arrays when players depart beyond 72 blocks.
 */
public final class AgentMaterializationController {

    private final ConcurrentHashMap<UUID, Integer> materializedEntities = new ConcurrentHashMap<>();
    private static final double MATERIALIZE_RADIUS_SQ = 64.0D * 64.0D;
    private static final double DEMATERIALIZE_RADIUS_SQ = 72.0D * 72.0D; // Hysteresis buffer
    private static final int MAX_MATERIALIZATIONS_PER_TICK = 10;

    public void tickMaterialization(ServerLevel level, List<ServerPlayer> players, StructOfArraysPopulationEcs ecs) {
        if (level == null || players == null || players.isEmpty() || ecs == null) {
            return;
        }

        // 1. Dematerialize and flush entities beyond player radius
        List<UUID> toDematerialize = new ArrayList<>();
        for (var entry : materializedEntities.entrySet()) {
            UUID uuid = entry.getKey();
            int ecsIndex = entry.getValue();

            Entity ent = level.getEntity(uuid);
            if (!(ent instanceof HumanNpcEntity npc) || !npc.isAlive()) {
                toDematerialize.add(uuid);
                ecs.setMaterialized(ecsIndex, false);
                continue;
            }

            // Check if any player is still nearby
            boolean playerNear = false;
            for (ServerPlayer player : players) {
                if (player.distanceToSqr(npc) <= DEMATERIALIZE_RADIUS_SQ) {
                    playerNear = true;
                    break;
                }
            }

            if (!playerNear) {
                // Flush state back into ECS
                ecs.setPosition(ecsIndex, (int) Math.floor(npc.getX()), (int) Math.floor(npc.getY()), (int) Math.floor(npc.getZ()));
                ecs.setHealth(ecsIndex, (byte) Math.max(0, Math.min(100, Math.round(npc.getHealth() * 5.0F))));
                ecs.setMaterialized(ecsIndex, false);
                npc.discard();
                toDematerialize.add(uuid);
            }
        }
        for (UUID u : toDematerialize) {
            materializedEntities.remove(u);
        }

        // 2. Materialize eligible agents within 64 blocks of players
        int materializedThisTick = 0;
        int count = ecs.size();

        for (ServerPlayer player : players) {
            BlockPos pPos = player.blockPosition();
            int px = pPos.getX();
            int pz = pPos.getZ();

            for (int i = 0; i < count; i++) {
                if (!ecs.isActive(i) || ecs.isMaterialized(i)) continue;

                double dx = ecs.getX(i) - px;
                double dz = ecs.getZ(i) - pz;
                double distSq = dx * dx + dz * dz;

                if (distSq <= MATERIALIZE_RADIUS_SQ) {
                    HumanNpcEntity npc = EternalEngineEntities.HUMAN_NPC.get().create(level);
                    if (npc != null) {
                        int y = TerraCoordinateHelper.getPlacementY(level, ecs.getX(i), ecs.getZ(i));
                        npc.moveTo(ecs.getX(i) + 0.5D, y, ecs.getZ(i) + 0.5D, 0.0F, 0.0F);
                        npc.setRole(roleFromByte(ecs.getRole(i)));
                        npc.setCivilizationId(cultureFromByte(ecs.getCulture(i)));
                        npc.setCustomName(Component.literal(npc.getRole() + " #" + ecs.getEntityId(i)));
                        npc.setCustomNameVisible(false);
                        npc.setHealth(Math.max(1.0F, ecs.getHealth(i) / 5.0F));

                        level.addFreshEntity(npc);
                        ecs.setMaterialized(i, true);
                        materializedEntities.put(npc.getUUID(), i);

                        materializedThisTick++;
                        if (materializedThisTick >= MAX_MATERIALIZATIONS_PER_TICK) {
                            return; // Obey tick budget
                        }
                    }
                }
            }
        }
    }

    public void flushAll(ServerLevel level, StructOfArraysPopulationEcs ecs) {
        if (level == null || ecs == null) return;
        for (var entry : materializedEntities.entrySet()) {
            UUID uuid = entry.getKey();
            int index = entry.getValue();
            Entity ent = level.getEntity(uuid);
            if (ent instanceof HumanNpcEntity npc && npc.isAlive()) {
                ecs.setPosition(index, (int) Math.floor(npc.getX()), (int) Math.floor(npc.getY()), (int) Math.floor(npc.getZ()));
                ecs.setHealth(index, (byte) Math.max(0, Math.min(100, Math.round(npc.getHealth() * 5.0F))));
                npc.discard();
            }
            ecs.setMaterialized(index, false);
        }
        materializedEntities.clear();
    }

    private String roleFromByte(byte r) {
        return switch (r) {
            case 1 -> "farmer";
            case 2 -> "artisan";
            case 3 -> "merchant";
            case 4 -> "soldier";
            case 5 -> "scholar";
            default -> "citizen";
        };
    }

    private String cultureFromByte(byte c) {
        return switch (c) {
            case 1 -> "rome";
            case 2 -> "carthage";
            case 3 -> "greece";
            case 4 -> "persia";
            case 5 -> "china";
            default -> "neutral";
        };
    }

    public int activeMaterializedCount() {
        return materializedEntities.size();
    }
}
