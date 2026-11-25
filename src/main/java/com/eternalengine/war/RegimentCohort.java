package com.eternalengine.war;

import com.eternalengine.entity.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a physical military cohort materialized in the Minecraft world.
 * Soldiers in the cohort maintain tactical formation offsets relative to the commander.
 */
public final class RegimentCohort {

    private final String cohortId;
    private final String armyId;
    private final String civilizationId;
    private final String era;
    private UUID commanderUuid;
    private final CopyOnWriteArrayList<UUID> soldierUuids = new CopyOnWriteArrayList<>();
    private FormationType formation;
    private BlockPos anchorPos;
    private float facingYaw;
    private String targetCohortId;
    private int ticksSinceLastOrder;

    public RegimentCohort(String cohortId, String armyId, String civilizationId, String era,
                          UUID commanderUuid, List<UUID> soldiers, FormationType formation,
                          BlockPos initialAnchor, float initialYaw) {
        this.cohortId = cohortId == null ? "cohort_" + UUID.randomUUID().toString().substring(0, 8) : cohortId;
        this.armyId = armyId == null ? "army_militia" : armyId;
        this.civilizationId = civilizationId == null ? "neutral" : civilizationId;
        this.era = era == null ? "ancient" : era;
        this.commanderUuid = commanderUuid;
        if (soldiers != null) {
            this.soldierUuids.addAll(soldiers);
        }
        this.formation = formation == null ? FormationType.LINE : formation;
        this.anchorPos = initialAnchor == null ? BlockPos.ZERO : initialAnchor;
        this.facingYaw = initialYaw;
    }

    /**
     * Ticks formation holding, entity pathfinding, and target engagement.
     */
    public void tick(ServerLevel level, ArmyManager armyManager) {
        if (level == null) return;
        ticksSinceLastOrder++;

        // 1. Clean dead soldiers
        cleanCasualties(level);

        // 2. Validate commander; promote successor if commander died
        HumanNpcEntity commander = getLivingEntity(level, commanderUuid);
        if (commander == null) {
            if (!soldierUuids.isEmpty()) {
                commanderUuid = soldierUuids.remove(0);
                commander = getLivingEntity(level, commanderUuid);
                if (commander != null) {
                    commander.setRole("commander");
                }
            } else {
                return; // Cohort wiped out
            }
        }

        if (commander != null) {
            this.anchorPos = commander.blockPosition();
            this.facingYaw = commander.getYRot();
        }

        // 3. Coordinate combat with targeted opponent cohort
        RegimentCohort opponent = targetCohortId != null ? armyManager.getCohort(targetCohortId) : null;
        if (opponent != null && opponent.isAlive()) {
            engageOpponent(level, commander, opponent);
        } else {
            // Find nearby hostile cohort within 48 blocks if not engaged
            RegimentCohort hostile = armyManager.findNearestHostileCohort(this.civilizationId, this.anchorPos, 48.0D);
            if (hostile != null) {
                this.targetCohortId = hostile.cohortId();
            }
        }

        // 4. Update formation offsets for all surviving soldiers
        int total = soldierUuids.size() + 1; // including commander
        for (int i = 0; i < soldierUuids.size(); i++) {
            UUID sUuid = soldierUuids.get(i);
            HumanNpcEntity soldier = getLivingEntity(level, sUuid);
            if (soldier == null) continue;

            Vec3 target = CohortFormationBrain.calculateTargetPosition(
                this.anchorPos, this.facingYaw, this.formation, i + 1, total, level);

            double distSq = soldier.distanceToSqr(target.x, target.y, target.z);
            if (distSq > 4.0D) {
                soldier.getNavigation().moveTo(target.x, target.y, target.z, 1.15D);
            }
        }
    }

    private void engageOpponent(ServerLevel level, HumanNpcEntity commander, RegimentCohort opponent) {
        HumanNpcEntity enemyCommander = opponent.getCommander(level);
        if (enemyCommander == null) return;

        // Advance towards enemy anchor
        if (commander != null && commander.distanceToSqr(enemyCommander) > 64.0D) {
            commander.getNavigation().moveTo(enemyCommander, 1.0D);
        }

        // Assign opposing targets to soldiers
        List<UUID> enemySoldiers = opponent.soldierUuids();
        for (int i = 0; i < soldierUuids.size(); i++) {
            HumanNpcEntity soldier = getLivingEntity(level, soldierUuids.get(i));
            if (soldier == null) continue;

            if (soldier.getTarget() == null || !soldier.getTarget().isAlive()) {
                if (!enemySoldiers.isEmpty()) {
                    UUID targetUuid = enemySoldiers.get(i % enemySoldiers.size());
                    HumanNpcEntity enemy = getLivingEntity(level, targetUuid);
                    if (enemy != null && enemy.isAlive()) {
                        soldier.setTarget(enemy);
                    }
                } else {
                    soldier.setTarget(enemyCommander);
                }
            }
        }
    }

    /**
     * Equip a soldier for their job.
     *
     * <p>Step 45 part 6 replaced eight hardcoded {@code Items.IRON_SWORD}, {@code BOW} and
     * {@code SHIELD} calls with one question: <i>what had reached this place by this year?</i>
     * The answer comes from the armoury (what exists here now) through the item resolver (which
     * item stands for it, from a mod if one is installed and from vanilla if not).
     *
     * <p>The {@code era} argument is kept for the callers that still have one, but it no longer
     * decides anything - a year and a place do, which is a stricter question than an era ever
     * was.
     */
    public static void equipForMilitary(HumanNpcEntity entity, String role, String era) {
        equipForMilitary(entity, role, WarGearStacks.currentYear());
    }

    /** Equip a soldier for their job in a given year. */
    public static void equipForMilitary(HumanNpcEntity entity, String role, int year) {
        if (entity == null) return;
        WarGearStacks.equip(entity, job(role), year);
    }

    /**
     * The job a role name means, as the war system spells it.
     *
     * <p>The old code branched on four strings and silently treated anything else as infantry.
     * Naming the jobs in one place is what lets a caller add "marine" or "levy" without touching
     * the equipment.
     */
    public static String job(@Nullable String role) {
        if (role == null) return "infantry";
        String r = role.toLowerCase(java.util.Locale.ROOT);
        if (r.contains("command")) return "commander";
        if (r.contains("arch") || r.contains("bow") || r.contains("gunner")) return "archer";
        if (r.contains("caval") || r.contains("horse") || r.contains("mount")) return "cavalry";
        if (r.contains("guard")) return "guard";
        if (r.contains("marine") || r.contains("sail")) return "marine";
        if (r.contains("bandit") || r.contains("raid")) return "bandit";
        if (r.contains("siege")) return "siege";
        return "infantry";
    }

    private void cleanCasualties(ServerLevel level) {
        soldierUuids.removeIf(uuid -> {
            HumanNpcEntity soldier = getLivingEntity(level, uuid);
            return soldier == null || !soldier.isAlive();
        });
    }

    private HumanNpcEntity getLivingEntity(ServerLevel level, UUID uuid) {
        if (uuid == null || level == null) return null;
        Entity e = level.getEntity(uuid);
        return (e instanceof HumanNpcEntity npc && npc.isAlive()) ? npc : null;
    }

    public boolean isAlive() {
        return commanderUuid != null || !soldierUuids.isEmpty();
    }

    public int activeStrength() {
        return (commanderUuid != null ? 1 : 0) + soldierUuids.size();
    }

    public HumanNpcEntity getCommander(ServerLevel level) {
        return getLivingEntity(level, commanderUuid);
    }

    // ── GETTERS & SETTERS ──────────────────────────────────────────

    public String cohortId() { return cohortId; }
    public String armyId() { return armyId; }
    public String civilizationId() { return civilizationId; }
    public String era() { return era; }
    public UUID commanderUuid() { return commanderUuid; }
    public List<UUID> soldierUuids() { return Collections.unmodifiableList(soldierUuids); }
    public FormationType formation() { return formation; }
    public void setFormation(FormationType formation) { this.formation = formation; }
    public BlockPos anchorPos() { return anchorPos; }
    public float facingYaw() { return facingYaw; }
    public void setFacingYaw(float yaw) { this.facingYaw = yaw; }
    public String targetCohortId() { return targetCohortId; }
    public void setTargetCohortId(String targetCohortId) { this.targetCohortId = targetCohortId; }
}
