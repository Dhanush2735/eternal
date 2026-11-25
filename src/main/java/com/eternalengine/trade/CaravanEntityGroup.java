package com.eternalengine.trade;

import com.eternalengine.entity.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a materialized physical overland trade caravan traveling across
 * real-world Terra++ road networks between civilization hubs.
 */
public final class CaravanEntityGroup {

    public enum CaravanStatus {
        TRAVELING,
        DEFENDING,
        DELIVERED,
        LOOTED
    }

    private final String caravanId;
    private final String routeId;
    private final String originCivId;
    private final String destCivId;
    private UUID masterUuid;
    private final List<UUID> packAnimalUuids = new ArrayList<>();
    private final List<UUID> guardUuids = new ArrayList<>();
    private final List<BlockPos> waypoints = new ArrayList<>();
    private int currentWaypointIndex = 0;
    private final Map<String, Integer> cargo = new ConcurrentHashMap<>();
    private double cargoValue;
    private CaravanStatus status = CaravanStatus.TRAVELING;
    private int ticksAlive = 0;

    public CaravanEntityGroup(String caravanId, String routeId, String originCivId, String destCivId,
                              UUID masterUuid, List<UUID> packAnimalUuids, List<UUID> guardUuids,
                              List<BlockPos> waypoints, Map<String, Integer> cargo, double cargoValue) {
        this.caravanId = caravanId;
        this.routeId = routeId;
        this.originCivId = originCivId != null ? originCivId : "neutral";
        this.destCivId = destCivId != null ? destCivId : "neutral";
        this.masterUuid = masterUuid;
        if (packAnimalUuids != null) this.packAnimalUuids.addAll(packAnimalUuids);
        if (guardUuids != null) this.guardUuids.addAll(guardUuids);
        if (waypoints != null) this.waypoints.addAll(waypoints);
        if (cargo != null) this.cargo.putAll(cargo);
        this.cargoValue = cargoValue;
    }

    public void tick(ServerLevel level, EconomyEngine economy) {
        if (level == null || status == CaravanStatus.DELIVERED || status == CaravanStatus.LOOTED) {
            return;
        }
        ticksAlive++;

        // 1. Validate master and handle succession
        HumanNpcEntity master = getMasterEntity(level);
        if (master == null || !master.isAlive()) {
            promoteNextMaster(level);
            master = getMasterEntity(level);
            if (master == null) {
                // Whole caravan leadership collapsed
                this.status = CaravanStatus.LOOTED;
                return;
            }
        }

        // 2. Clean casualties from guards and pack animals
        cleanCasualties(level);

        // 3. Navigation along waypoints
        if (!waypoints.isEmpty() && currentWaypointIndex < waypoints.size()) {
            BlockPos targetWp = waypoints.get(currentWaypointIndex);
            double distSq = master.distanceToSqr(targetWp.getX() + 0.5D, targetWp.getY(), targetWp.getZ() + 0.5D);

            if (distSq < 16.0D) { // Reached within 4 blocks
                currentWaypointIndex++;
                if (currentWaypointIndex >= waypoints.size()) {
                    completeDelivery(level, economy);
                    return;
                }
            } else {
                // Command master towards current waypoint
                master.getNavigation().moveTo(targetWp.getX() + 0.5D, targetWp.getY(), targetWp.getZ() + 0.5D, 1.0D);
            }
        }

        // 4. Tick pack animals to follow master
        tickPackAnimals(level, master);

        // 5. Tick guards to flank and defend master
        tickGuards(level, master);
    }

    private void tickPackAnimals(ServerLevel level, HumanNpcEntity master) {
        for (UUID animalUuid : packAnimalUuids) {
            Entity ent = level.getEntity(animalUuid);
            if (ent instanceof Mob animal && animal.isAlive()) {
                double dist = animal.distanceTo(master);
                if (dist > 25.0D) {
                    // Teleport if severely desynced
                    animal.teleportTo(master.getX() + 1.0D, master.getY(), master.getZ() + 1.0D);
                } else if (dist > 4.5D) {
                    animal.getNavigation().moveTo(master, 1.15D);
                }
            }
        }
    }

    private void tickGuards(ServerLevel level, HumanNpcEntity master) {
        boolean threatsDetected = false;
        // Search for hostile threats in 16-block radius around master
        AABB searchBox = master.getBoundingBox().inflate(16.0D, 6.0D, 16.0D);
        List<Mob> hostiles = level.getEntitiesOfClass(Mob.class, searchBox,
            m -> m.isAlive() && m != master && !guardUuids.contains(m.getUUID()) && !packAnimalUuids.contains(m.getUUID())
                && (m.getTarget() == master || (m instanceof HumanNpcEntity h && "bandit".equals(h.getRole()))));

        Mob nearestThreat = hostiles.isEmpty() ? null : hostiles.get(0);
        if (nearestThreat != null) {
            threatsDetected = true;
            this.status = CaravanStatus.DEFENDING;
        } else if (this.status == CaravanStatus.DEFENDING) {
            this.status = CaravanStatus.TRAVELING;
        }

        for (int i = 0; i < guardUuids.size(); i++) {
            Entity ent = level.getEntity(guardUuids.get(i));
            if (ent instanceof HumanNpcEntity guard && guard.isAlive()) {
                if (nearestThreat != null) {
                    guard.setTarget(nearestThreat);
                    guard.getNavigation().moveTo(nearestThreat, 1.25D);
                } else {
                    // Maintain formation around master
                    double angle = (i * 2.0D * Math.PI) / Math.max(1, guardUuids.size());
                    double flankX = master.getX() + Math.cos(angle) * 3.0D;
                    double flankZ = master.getZ() + Math.sin(angle) * 3.0D;
                    if (guard.distanceTo(master) > 5.0D) {
                        guard.getNavigation().moveTo(flankX, master.getY(), flankZ, 1.1D);
                    }
                }
            }
        }
    }

    private void completeDelivery(ServerLevel level, EconomyEngine economy) {
        this.status = CaravanStatus.DELIVERED;
        if (economy != null && cargoValue > 0.0D) {
            long profit = Math.round(cargoValue);
            economy.applyIncome(destCivId, profit, "caravan_delivered:" + routeId, null);
        }
        // Disband guards and animals cleanly
        for (UUID animalUuid : packAnimalUuids) {
            Entity ent = level.getEntity(animalUuid);
            if (ent != null) ent.discard();
        }
        for (UUID guardUuid : guardUuids) {
            Entity ent = level.getEntity(guardUuid);
            if (ent != null) ent.discard();
        }
        HumanNpcEntity master = getMasterEntity(level);
        if (master != null) master.discard();
    }

    private void promoteNextMaster(ServerLevel level) {
        while (!guardUuids.isEmpty()) {
            UUID next = guardUuids.remove(0);
            Entity ent = level.getEntity(next);
            if (ent instanceof HumanNpcEntity guard && guard.isAlive()) {
                this.masterUuid = next;
                guard.setRole("caravan_master");
                return;
            }
        }
        this.masterUuid = null;
    }

    private void cleanCasualties(ServerLevel level) {
        guardUuids.removeIf(uuid -> {
            Entity ent = level.getEntity(uuid);
            return ent == null || !ent.isAlive();
        });
        packAnimalUuids.removeIf(uuid -> {
            Entity ent = level.getEntity(uuid);
            return ent == null || !ent.isAlive();
        });
    }

    public static void equipGuard(HumanNpcEntity guard, String era) {
        if (guard == null) return;
        guard.setRole("caravan_guard");
        guard.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        guard.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        guard.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        // Step 45 part 6: a caravan guard crossing the Sahara in 1300 is not a man in chainmail.
        // What he carries is asked of the armoury for this place and year.
        com.eternalengine.war.WarGearStacks.equip(guard, "guard",
                com.eternalengine.war.WarGearStacks.currentYear());
    }

    public static void equipMaster(HumanNpcEntity master) {
        if (master == null) return;
        master.setRole("caravan_master");
        master.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        master.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        master.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMPASS));
    }

    public HumanNpcEntity getMasterEntity(ServerLevel level) {
        if (level == null || masterUuid == null) return null;
        Entity ent = level.getEntity(masterUuid);
        return ent instanceof HumanNpcEntity h ? h : null;
    }

    public String caravanId() { return caravanId; }
    public String routeId() { return routeId; }
    public String originCivId() { return originCivId; }
    public String destCivId() { return destCivId; }
    public UUID masterUuid() { return masterUuid; }
    public List<UUID> packAnimalUuids() { return Collections.unmodifiableList(packAnimalUuids); }
    public List<UUID> guardUuids() { return Collections.unmodifiableList(guardUuids); }
    public List<BlockPos> waypoints() { return Collections.unmodifiableList(waypoints); }
    public int currentWaypointIndex() { return currentWaypointIndex; }
    public Map<String, Integer> cargo() { return Collections.unmodifiableMap(cargo); }
    public double cargoValue() { return cargoValue; }
    public CaravanStatus status() { return status; }
    public void setStatus(CaravanStatus s) { this.status = s; }
    public int ticksAlive() { return ticksAlive; }
    public boolean isDelivered() { return status == CaravanStatus.DELIVERED; }
    public boolean isLooted() { return status == CaravanStatus.LOOTED; }
    public boolean isAlive() { return status != CaravanStatus.DELIVERED && status != CaravanStatus.LOOTED; }
}
