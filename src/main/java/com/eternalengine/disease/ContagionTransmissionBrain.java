package com.eternalengine.disease;

import com.eternalengine.entity.HumanNpcEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative physical contagion transmission engine.
 * Simulates real-time airborne and contact contagion between proximate
 * HumanNpcEntity instances, tracking infection progression and medicine mitigations.
 */
public final class ContagionTransmissionBrain {

    public enum InfectionStage {
        INCUBATING,
        SYMPTOMATIC,
        CRITICAL,
        RECOVERED
    }

    public static final class InfectionRecord {
        private final UUID entityUuid;
        private final String diseaseId;
        private InfectionStage stage;
        private int ticksInfected;
        private double severity;

        public InfectionRecord(UUID entityUuid, String diseaseId, InfectionStage stage, double severity) {
            this.entityUuid = entityUuid;
            this.diseaseId = diseaseId;
            this.stage = stage;
            this.severity = severity;
            this.ticksInfected = 0;
        }

        public UUID entityUuid() { return entityUuid; }
        public String diseaseId() { return diseaseId; }
        public InfectionStage stage() { return stage; }
        public void setStage(InfectionStage stage) { this.stage = stage; }
        public int ticksInfected() { return ticksInfected; }
        public void incrementTicks() { this.ticksInfected++; }
        public double severity() { return severity; }
        public void setSeverity(double severity) { this.severity = severity; }
    }

    private final ConcurrentHashMap<UUID, InfectionRecord> activeInfections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> immuneEntities = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public void infect(UUID entityUuid, String diseaseId, double severity) {
        if (entityUuid == null || diseaseId == null) return;
        if (immuneEntities.containsKey(entityUuid)) return; // Acquired immunity

        activeInfections.putIfAbsent(entityUuid,
            new InfectionRecord(entityUuid, diseaseId, InfectionStage.INCUBATING, severity));
    }

    public void tick(ServerLevel level, MedicineTier medicineTier) {
        if (level == null || activeInfections.isEmpty()) return;
        tickCounter++;

        double medicineProtection = getMedicineProtection(medicineTier);

        activeInfections.values().removeIf(record -> {
            Entity ent = level.getEntity(record.entityUuid());
            if (!(ent instanceof HumanNpcEntity npc) || !npc.isAlive()) {
                return true; // NPC died or was unloaded
            }

            record.incrementTicks();

            // 1. Stage Progression
            int ticks = record.ticksInfected();
            if (ticks < 200) {
                record.setStage(InfectionStage.INCUBATING);
            } else if (ticks < 800) {
                record.setStage(InfectionStage.SYMPTOMATIC);
                // Apply symptomatic debuffs & sneeze particles
                npc.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
                if (tickCounter % 40 == 0) {
                    level.sendParticles(ParticleTypes.ITEM_SLIME, npc.getX(), npc.getEyeY(), npc.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.05D);
                }
            } else if (ticks < 1400) {
                record.setStage(InfectionStage.CRITICAL);
                npc.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
                if (tickCounter % 30 == 0) {
                    level.sendParticles(ParticleTypes.SMOKE, npc.getX(), npc.getEyeY(), npc.getZ(), 6, 0.2D, 0.2D, 0.2D, 0.02D);
                }
            } else {
                // Recovery roll: higher medicine tiers increase survival
                double survivalChance = 0.5D + (medicineProtection * 0.45D);
                if (Math.random() < survivalChance) {
                    record.setStage(InfectionStage.RECOVERED);
                    immuneEntities.put(record.entityUuid(), record.diseaseId());
                    return true;
                } else {
                    npc.hurt(level.damageSources().magic(), 100.0F); // Fatal complication
                    return true;
                }
            }

            // 2. Proximity Contagion Transmission to proximate NPCs (every 40 ticks)
            if (tickCounter % 40 == 0 && (record.stage() == InfectionStage.SYMPTOMATIC || record.stage() == InfectionStage.CRITICAL)) {
                AABB transmissionBox = npc.getBoundingBox().inflate(3.5D, 2.0D, 3.5D);
                List<HumanNpcEntity> nearby = level.getEntitiesOfClass(HumanNpcEntity.class, transmissionBox,
                    other -> other != npc && other.isAlive() && !activeInfections.containsKey(other.getUUID()) && !immuneEntities.containsKey(other.getUUID()));

                double baseSpreadChance = 0.06D * (1.0D - medicineProtection);
                for (HumanNpcEntity target : nearby) {
                    if (Math.random() < baseSpreadChance) {
                        infect(target.getUUID(), record.diseaseId(), record.severity());
                    }
                }
            }

            return false;
        });
    }

    private double getMedicineProtection(MedicineTier tier) {
        if (tier == null) return 0.1D;
        return switch (tier) {
            case STONE_AGE -> 0.05D;
            case ANCIENT -> 0.15D;
            case MEDIEVAL -> 0.20D;
            case RENAISSANCE -> 0.40D;
            case INDUSTRIAL -> 0.65D;
            case MODERN -> 0.90D;
        };
    }

    public InfectionRecord getInfection(UUID uuid) {
        return activeInfections.get(uuid);
    }

    public Map<UUID, InfectionRecord> getActiveInfections() {
        return Collections.unmodifiableMap(activeInfections);
    }

    public int activeInfectionCount() {
        return activeInfections.size();
    }

    public int immuneCount() {
        return immuneEntities.size();
    }
}
