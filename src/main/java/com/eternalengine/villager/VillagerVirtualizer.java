package com.eternalengine.villager;

import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IEntityCivilizationData;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.terrabridge.TerraBridge;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villager virtualization foundation.
 */
public final class VillagerVirtualizer {
    private final ConcurrentHashMap<UUID, VirtualVillager> virtualStore = new ConcurrentHashMap<>();

    public void tick(WorldStateManager state, int simYear) {
        for (VirtualVillager villager : virtualStore.values()) {
            villager.updateYear(simYear);
        }
    }

    public VirtualVillager virtualizeVillager(Villager entity, int simYear) {
        if (entity == null) {
            return null;
        }
        TerraBridge.GeoPoint point = TerraCoordinateHelper.toGeoPoint(entity.blockPosition());
        IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(entity);
        String civ = data == null ? "neutral" : data.getCivilizationId();
        String role = data == null ? "VILLAGER" : data.getRole();
        String name = entity.getCustomName() == null ? entity.getName().getString() : entity.getCustomName().getString();
        VirtualVillager virtual = new VirtualVillager(entity.getUUID(), civ, role, name,
            point == null ? 0.0d : point.latitude(), point == null ? 0.0d : point.longitude(),
            data == null ? 50 : data.getLoyalty(), 100, simYear);
        virtualStore.put(virtual.entityUuid(), virtual);
        entity.discard();
        return virtual;
    }

    public UUID instantiateVillager(VirtualVillager virtual, BlockPos pos, ServerLevel level) {
        if (virtual == null || pos == null || level == null || virtual.dead()) {
            return null;
        }
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        villager.moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 0.0f, 0.0f);
        villager.setCustomName(Component.literal(virtual.displayName()));
        villager.setCustomNameVisible(true);
        IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(villager);
        if (data != null) {
            data.setCivilizationId(virtual.civId());
            data.setRole(virtual.roleType());
            data.setEntityName(virtual.displayName());
            data.setLoyalty(virtual.loyaltyScore());
            data.setVirtual(false);
            data.setLastKnownYear(virtual.lastUpdatedYear());
        }
        level.addFreshEntity(villager);
        virtualStore.remove(virtual.entityUuid());
        return villager.getUUID();
    }

    public Map<UUID, VirtualVillager> virtualStore() {
        return Map.copyOf(virtualStore);
    }

    public int size() {
        return virtualStore.size();
    }
}
