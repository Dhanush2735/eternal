package com.eternalengine.villager;

import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IEntityCivilizationData;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.eternalengine.vip.VipEntityHelper;
import com.eternalengine.vip.VipSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villager/person manager foundation.
 */
public final class VillagerManager {
    private final ConcurrentHashMap<String, List<UUID>> civVillagers = new ConcurrentHashMap<>();
    private final VillagerNameDatabase nameDatabase;
    private final VillagerVirtualizer virtualizer;

    public VillagerManager() {
        this(new VillagerNameDatabase(), new VillagerVirtualizer());
    }

    public VillagerManager(VillagerNameDatabase nameDatabase, VillagerVirtualizer virtualizer) {
        this.nameDatabase = nameDatabase == null ? new VillagerNameDatabase() : nameDatabase;
        this.virtualizer = virtualizer == null ? new VillagerVirtualizer() : virtualizer;
    }

    public UUID spawnVillager(String civId, String roleType, BlockPos pos, ServerLevel level) {
        if (pos == null || level == null) {
            return null;
        }
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        int y = Math.max(pos.getY(), TerraCoordinateHelper.getPlacementY(level, pos.getX(), pos.getZ()));
        villager.moveTo(pos.getX() + 0.5d, y, pos.getZ() + 0.5d, 0.0f, 0.0f);
        String name = nameDatabase.getRandomName(civId, "", roleType, new Random());
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(villager);
        if (data != null) {
            data.setCivilizationId(civId);
            data.setRole(roleType);
            data.setEntityName(name);
            data.setLoyalty(50);
            data.setVirtual(false);
        }
        level.addFreshEntity(villager);
        civVillagers.computeIfAbsent(civId == null ? "neutral" : civId, id -> new ArrayList<>()).add(villager.getUUID());
        return villager.getUUID();
    }

    public UUID spawnVipVillager(VipSlot vip, BlockPos pos, ServerLevel level) {
        if (vip == null) {
            return null;
        }
        UUID uuid = spawnVillager(vip.civilizationId(), vip.roleType().name(), pos, level);
        if (uuid == null) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        if (entity != null) {
            VipEntityHelper.tagVipEntity(entity, vip);
        }
        return uuid;
    }

    public int getVillagerCount(String civId) {
        return civVillagers.getOrDefault(civId, List.of()).size();
    }

    public List<Entity> getVillagersNearby(BlockPos pos, int radiusBlocks, ServerLevel level) {
        if (pos == null || level == null) {
            return List.of();
        }
        List<Entity> result = new ArrayList<>();
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
            new net.minecraft.world.phys.AABB(pos).inflate(Math.max(1, radiusBlocks)))) {
            result.add(villager);
        }
        return List.copyOf(result);
    }

    public boolean killVillager(UUID villagerUuid, String cause, ServerLevel level) {
        if (villagerUuid == null || level == null) {
            return false;
        }
        Entity entity = level.getEntity(villagerUuid);
        if (entity == null) {
            return false;
        }
        entity.kill();
        return true;
    }

    public VillagerNameDatabase nameDatabase() { return nameDatabase; }
    public VillagerVirtualizer virtualizer() { return virtualizer; }
}
