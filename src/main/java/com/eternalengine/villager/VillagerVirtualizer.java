package com.eternalengine.villager;

import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IEntityCivilizationData;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraBridge;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villager virtualization engine.
 * Converts proximate entities into lightweight virtual data structs beyond render range
 * and reinstantiates them as HumanNpcEntity actors when players approach.
 */
public final class VillagerVirtualizer {
    private final ConcurrentHashMap<UUID, VirtualVillager> virtualStore = new ConcurrentHashMap<>();

    public void tick(WorldStateManager state, int simYear) {
        for (VirtualVillager villager : virtualStore.values()) {
            villager.updateYear(simYear);
        }
    }

    /** Every attribute a person is: read off the live entity while it still exists. */
    private static VirtualVillager record(LivingEntity entity, int simYear) {
        TerraBridge.GeoPoint point = TerraCoordinateHelper.toGeoPoint(entity.blockPosition());
        IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(entity);
        // Read the person off the person. The capability is a second copy of the same facts and is
        // used only when the entity does not carry them itself — a record that depends on a
        // capability which may not be attached is a record that can lose the person's name, role and
        // standing without anybody noticing.
        String civ = null;
        String role = null;
        int loyalty = 0;
        String gender = null;
        String era = null;
        if (entity instanceof HumanNpcEntity npc) {
            civ = npc.getCivilizationId();
            role = npc.getRole();
            loyalty = npc.getLoyalty();
            gender = npc.getGender();
            era = npc.getEra();
        }
        if (civ == null || civ.isBlank()) {
            civ = data == null ? "neutral" : data.getCivilizationId();
        }
        if (role == null || role.isBlank()) {
            role = data == null ? "citizen" : data.getRole();
        }
        if (loyalty == 0) {
            loyalty = data == null ? 50 : data.getLoyalty();
        }
        String name = entity.getCustomName() == null ? entity.getName().getString() : entity.getCustomName().getString();
        VirtualVillager virtual = new VirtualVillager(entity.getUUID(), civ, role, name,
            point == null ? 0.0d : point.latitude(), point == null ? 0.0d : point.longitude(),
            loyalty, 100, simYear);
        // The two the face and the nameplate are made of as well. Without them the record is a name
        // without a person.
        if (gender != null) {
            virtual.setGender(gender);
        }
        if (era != null) {
            virtual.setEra(era);
        }
        return virtual;
    }

    /**
     * The same removal, for a caller that holds the virtualizer: the record goes into this session's
     * store <em>and</em> into the place's own memory.
     *
     * <p>Both, deliberately. The store is what the caller can read back this session; the memory is
     * what the place keeps after the world has been saved and loaded. A removal that wrote only to
     * the store would lose the person on a restart, which is the one thing "remember" cannot mean.
     */
    public VirtualVillager virtualizeVillager(LivingEntity entity, int simYear) {
        if (entity == null) {
            return null;
        }
        VirtualVillager virtual = record(entity, simYear);
        virtualStore.put(virtual.entityUuid(), virtual);
        if (entity.level() instanceof ServerLevel server) {
            PeopleMemory.get(server).remember(
                    PeopleMemory.placeKey(entity.blockPosition().getX(), entity.blockPosition().getZ()),
                    virtual);
        }
        entity.discard();
        return virtual;
    }

    /**
     * Remove a person from the world and keep them: the one way a living NPC leaves.
     *
     * <p>Every removal in the spawner goes through here, which is what makes the promise hold. The
     * person is written into the place's memory — the same UUID, the same name, the same
     * civilization, role, era and gender — and only then is the entity removed. Removing without
     * remembering is what made the old crowd a crowd of strangers, and it no longer happens
     * anywhere.
     *
     * @param placeX the place the person belongs to, when it is not where they are standing: a
     *               wanderer is remembered at the district they wandered away from
     */
    public static VirtualVillager remember(ServerLevel level, HumanNpcEntity npc, int simYear,
                                           int placeX, int placeZ) {
        if (level == null || npc == null) {
            return null;
        }
        VirtualVillager person = record(npc, simYear);
        PeopleMemory.get(level).remember(PeopleMemory.placeKey(placeX, placeZ), person);
        npc.discard();
        return person;
    }

    /** The same, for a person remembered where they stand. */
    public static VirtualVillager remember(ServerLevel level, HumanNpcEntity npc, int simYear) {
        if (npc == null) {
            return null;
        }
        return remember(level, npc, simYear, npc.blockPosition().getX(), npc.blockPosition().getZ());
    }

    /**
     * A person this place remembers, ready to be put back in the world, or null if it remembers
     * nobody.
     *
     * <p>Called before anybody new is invented: the memory is the first resort and an invented
     * person the last, which is the whole of "spawn the exact replica again".
     */
    public static VirtualVillager remembered(ServerLevel level, int x, int z, String roleType) {
        if (level == null) {
            return null;
        }
        return PeopleMemory.get(level).recall(PeopleMemory.placeKey(x, z), roleType);
    }

    /** Step 25: a remembered person of this place who {@code fits} (see {@link PeopleMemory#recall(String, String,
     * java.util.function.Predicate)}). */
    public static VirtualVillager remembered(ServerLevel level, int x, int z, String roleType,
                                             java.util.function.Predicate<VirtualVillager> fits) {
        if (level == null) {
            return null;
        }
        return PeopleMemory.get(level).recall(PeopleMemory.placeKey(x, z), roleType, fits);
    }

    public UUID instantiateVillager(VirtualVillager virtual, BlockPos pos, ServerLevel level) {
        if (virtual == null || pos == null || level == null || virtual.dead()) {
            return null;
        }
        // Step 21: the same person never stands twice. Minecraft refuses an entity whose UUID is already in the level,
        // and the caller would then have tagged the one already standing (a crowd figure, perhaps) as this person.
        if (level.getEntity(virtual.entityUuid()) != null) {
            return null;
        }

        // Prefer HumanNpcEntity
        HumanNpcEntity humanNpc = null;
        try {
            if (EternalEngineEntities.HUMAN_NPC != null && EternalEngineEntities.HUMAN_NPC.isPresent()) {
                humanNpc = EternalEngineEntities.HUMAN_NPC.get().create(level);
            }
        } catch (Throwable ignored) {}

        if (humanNpc != null) {
            humanNpc.moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 0.0f, 0.0f);
            // The same person, not a lookalike: the entity's own UUID is the one the person had, and
            // the client chooses the skin from it, so the face that comes back is the face that
            // left. This one line is the difference between a replica and a replacement.
            humanNpc.setUUID(virtual.entityUuid());
            humanNpc.setCustomName(Component.literal(HumanNpcEntity.withRoleMark(virtual.displayName(), virtual.roleType())));
            humanNpc.setCustomNameVisible(true);
            humanNpc.setCivilizationId(virtual.civId());
            humanNpc.setRole(virtual.roleType());
            humanNpc.setGender(virtual.gender());
            humanNpc.setEra(virtual.era());
            humanNpc.setLoyalty(virtual.loyaltyScore());

            IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(humanNpc);
            if (data != null) {
                data.setCivilizationId(virtual.civId());
                data.setRole(virtual.roleType());
                data.setEntityName(virtual.displayName());
                data.setLoyalty(virtual.loyaltyScore());
                data.setVirtual(false);
                data.setLastKnownYear(virtual.lastUpdatedYear());
            }
            level.addFreshEntity(humanNpc);
            virtualStore.remove(virtual.entityUuid());
            return humanNpc.getUUID();
        }

        // Fallback to Villager
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        villager.moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 0.0f, 0.0f);
        villager.setUUID(virtual.entityUuid());
        villager.setCustomName(Component.literal(HumanNpcEntity.withRoleMark(virtual.displayName(), virtual.roleType())));
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
