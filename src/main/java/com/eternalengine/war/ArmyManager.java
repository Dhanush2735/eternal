package com.eternalengine.war;

import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages both virtual armies and physical regimental cohorts in the world.
 */
public final class ArmyManager {
    private final ConcurrentHashMap<String, Army> armies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RegimentCohort> activeCohorts = new ConcurrentHashMap<>();

    public Optional<Army> getArmy(String armyId) {
        return Optional.ofNullable(armies.get(armyId));
    }

    public List<Army> getArmiesOf(String civId) {
        List<Army> result = new ArrayList<>();
        for (Army army : armies.values()) {
            if (army.ownerCivId().equals(civId)) {
                result.add(army);
            }
        }
        return List.copyOf(result);
    }

    public void addArmy(Army army) {
        if (army != null) {
            armies.put(army.armyId(), army);
        }
    }

    public void removeArmy(String armyId) {
        if (armyId != null) {
            armies.remove(armyId);
        }
    }

    public void tick(int simYear) {
        for (Army army : armies.values()) {
            Army.MovementOrder order = army.currentOrder();
            if (order != null) {
                army.moveToward(order.targetLat(), order.targetLon(), 1.0d / 365.0d);
            }
        }
    }

    /**
     * Ticks all physicalized cohorts in the given server level.
     */
    public void tickCohorts(ServerLevel level) {
        if (level == null || activeCohorts.isEmpty()) return;
        activeCohorts.values().removeIf(cohort -> !cohort.isAlive());
        for (RegimentCohort cohort : activeCohorts.values()) {
            cohort.tick(level, this);
        }
    }

    /**
     * Physically deploys a regiment of HumanNpcEntity soldiers in formation into the world.
     */
    public RegimentCohort deployCohort(Army army, BlockPos anchorPos, ServerLevel level,
                                       FormationType formation, int unitCount) {
        if (army == null || anchorPos == null || level == null) {
            return null;
        }

        int count = Math.max(3, Math.min(64, unitCount <= 0 ? 12 : unitCount));
        FormationType f = formation == null ? FormationType.LINE : formation;

        // 1. Spawn Commander at anchor position
        int cmdY = TerraCoordinateHelper.getPlacementY(level, anchorPos.getX(), anchorPos.getZ());
        HumanNpcEntity commander = null;
        try {
            if (EternalEngineEntities.HUMAN_NPC != null && EternalEngineEntities.HUMAN_NPC.isPresent()) {
                commander = EternalEngineEntities.HUMAN_NPC.get().create(level);
            }
        } catch (Throwable ignored) {}

        if (commander == null) {
            return null;
        }

        commander.moveTo(anchorPos.getX() + 0.5D, cmdY, anchorPos.getZ() + 0.5D, 0.0F, 0.0F);
        commander.setCustomName(Component.literal(army.ownerCivId() + " Commander"));
        commander.setCustomNameVisible(true);
        commander.setCivilizationId(army.ownerCivId());
        commander.setRole("commander");
        commander.setEra(army.equipmentEra());
        RegimentCohort.equipForMilitary(commander, "commander", army.equipmentEra());
        level.addFreshEntity(commander);

        // 2. Spawn Soldiers in formation offsets
        List<UUID> soldiers = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            HumanNpcEntity soldier = EternalEngineEntities.HUMAN_NPC.get().create(level);
            if (soldier == null) continue;

            Vec3 pos = CohortFormationBrain.calculateTargetPosition(anchorPos, 0.0F, f, i, count, level);
            soldier.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);

            String role = (i % 4 == 0) ? "archer" : (i % 5 == 0) ? "cavalry" : "infantry";
            soldier.setCustomName(Component.literal(army.ownerCivId() + " " + role));
            soldier.setCustomNameVisible(true);
            soldier.setCivilizationId(army.ownerCivId());
            soldier.setRole(role);
            soldier.setEra(army.equipmentEra());
            RegimentCohort.equipForMilitary(soldier, role, army.equipmentEra());

            level.addFreshEntity(soldier);
            soldiers.add(soldier.getUUID());
        }

        String cohortId = "cohort_" + army.armyId() + "_" + System.currentTimeMillis() % 10000;
        RegimentCohort cohort = new RegimentCohort(cohortId, army.armyId(), army.ownerCivId(),
            army.equipmentEra(), commander.getUUID(), soldiers, f, anchorPos, 0.0F);

        activeCohorts.put(cohortId, cohort);
        return cohort;
    }

    public RegimentCohort getCohort(String cohortId) {
        return activeCohorts.get(cohortId);
    }

    public RegimentCohort findNearestHostileCohort(String myCivId, BlockPos pos, double maxRadius) {
        if (pos == null) return null;
        double minD2 = maxRadius * maxRadius;
        RegimentCohort nearest = null;

        for (RegimentCohort cohort : activeCohorts.values()) {
            if (cohort.civilizationId().equalsIgnoreCase(myCivId)) continue;
            if (!cohort.isAlive()) continue;

            double d2 = cohort.anchorPos().distSqr(pos);
            if (d2 < minD2) {
                minD2 = d2;
                nearest = cohort;
            }
        }
        return nearest;
    }

    public List<RegimentCohort> getActiveCohorts() {
        return List.copyOf(activeCohorts.values());
    }

    public double calculateSupplyLine(String armyId) {
        Army army = armies.get(armyId);
        if (army == null) {
            return 0.0d;
        }
        return army.supplyLevel() / 100.0d;
    }

    public void issueOrder(String armyId, Army.MovementOrder order) {
        Army army = armies.get(armyId);
        if (army != null) {
            army.setCurrentOrder(order);
        }
    }

    public int size() {
        return armies.size();
    }
}
