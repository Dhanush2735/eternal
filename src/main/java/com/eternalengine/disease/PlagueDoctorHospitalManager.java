package com.eternalengine.disease;

import com.eternalengine.buildings.BlockUpdateQueue;
import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages procedural field hospital tents, plague doctors, isolation cots,
 * and medical intervention treatments curing symptomatic citizens.
 */
public final class PlagueDoctorHospitalManager {

    public static final class HospitalTent {
        private final String hospitalId;
        private final BlockPos centerPos;
        private final List<BlockPos> tentBlocks = new ArrayList<>();
        private final List<UUID> doctorUuids = new ArrayList<>();
        private final List<UUID> patientUuids = new ArrayList<>();

        public HospitalTent(String hospitalId, BlockPos centerPos, List<BlockPos> blocks, List<UUID> doctors) {
            this.hospitalId = hospitalId;
            this.centerPos = centerPos;
            if (blocks != null) this.tentBlocks.addAll(blocks);
            if (doctors != null) this.doctorUuids.addAll(doctors);
        }

        public String hospitalId() { return hospitalId; }
        public BlockPos centerPos() { return centerPos; }
        public List<BlockPos> tentBlocks() { return Collections.unmodifiableList(tentBlocks); }
        public List<UUID> doctorUuids() { return Collections.unmodifiableList(doctorUuids); }
        public List<UUID> patientUuids() { return Collections.unmodifiableList(patientUuids); }
        public void addPatient(UUID u) { if (!patientUuids.contains(u)) patientUuids.add(u); }
    }

    private final ConcurrentHashMap<String, HospitalTent> activeHospitals = new ConcurrentHashMap<>();
    private final List<UUID> independentDoctors = new ArrayList<>();
    private int tickCounter = 0;

    /**
     * Constructs a field hospital isolation tent and garrisons it with medical doctors.
     */
    public HospitalTent erectHospitalTent(BlockPos center, ServerLevel level, BlockUpdateQueue blockQueue, MedicineTier tier) {
        if (level == null || center == null) return null;

        String id = "hospital_" + System.currentTimeMillis() % 10000;
        int surfaceY = TerraCoordinateHelper.getPlacementY(level, center.getX(), center.getZ());
        BlockPos base = new BlockPos(center.getX(), surfaceY, center.getZ());
        List<BlockPos> blocks = new ArrayList<>();

        int size = 5; // 5x5 canvas tent
        int height = 3;

        // 1. Enqueue tent structure via BlockUpdateQueue
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                BlockPos p = base.offset(x, 0, z);
                int py = TerraCoordinateHelper.getSurfaceY(level, p.getX(), p.getZ());
                if (p.getY() > py && blockQueue != null) {
                    blockQueue.enqueue(level.dimension(), p, Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 5);
                    blocks.add(p);
                }
            }
        }

        // Canvas wool roof
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                BlockPos roof = base.offset(x, height, z);
                int py = TerraCoordinateHelper.getSurfaceY(level, roof.getX(), roof.getZ());
                if (roof.getY() > py && blockQueue != null) {
                    blockQueue.enqueue(level.dimension(), roof, Blocks.WHITE_WOOL.defaultBlockState(), 5);
                    blocks.add(roof);
                }
            }
        }

        // Cot beds and cauldron inside
        BlockPos bedPos = base.offset(1, 1, 1);
        BlockPos cauldronPos = base.offset(-1, 1, -1);
        if (blockQueue != null) {
            blockQueue.enqueue(level.dimension(), bedPos, Blocks.WHITE_BED.defaultBlockState(), 5);
            blockQueue.enqueue(level.dimension(), cauldronPos, Blocks.CAULDRON.defaultBlockState(), 5);
            blocks.add(bedPos);
            blocks.add(cauldronPos);
        }

        // 2. Spawn Plague Doctor / Physician
        List<UUID> doctors = new ArrayList<>();
        HumanNpcEntity doctor = spawnDoctor(base.offset(0, 1, 0), level, tier);
        if (doctor != null) {
            doctors.add(doctor.getUUID());
        }

        HospitalTent tent = new HospitalTent(id, base, blocks, doctors);
        activeHospitals.put(id, tent);
        return tent;
    }

    public HumanNpcEntity spawnDoctor(BlockPos pos, ServerLevel level, MedicineTier tier) {
        if (level == null || pos == null) return null;
        HumanNpcEntity doctor = EternalEngineEntities.HUMAN_NPC.get().create(level);
        if (doctor == null) return null;

        int y = TerraCoordinateHelper.getPlacementY(level, pos.getX(), pos.getZ());
        doctor.moveTo(pos.getX() + 0.5D, y, pos.getZ() + 0.5D, 0.0F, 0.0F);
        equipDoctor(doctor, tier);
        level.addFreshEntity(doctor);
        independentDoctors.add(doctor.getUUID());
        return doctor;
    }

    public static void equipDoctor(HumanNpcEntity doctor, MedicineTier tier) {
        if (doctor == null) return;
        doctor.setRole("doctor");

        if (tier == MedicineTier.MEDIEVAL || tier == MedicineTier.RENAISSANCE) {
            doctor.setCustomName(net.minecraft.network.chat.Component.literal("Plague Doctor"));
            doctor.setCustomNameVisible(true);
            doctor.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            doctor.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            doctor.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            doctor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK)); // Examination cane
            doctor.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.POTION));
        } else {
            doctor.setCustomName(net.minecraft.network.chat.Component.literal("Medical Physician"));
            doctor.setCustomNameVisible(true);
            doctor.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            doctor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POTION));
        }
    }

    public void tick(ServerLevel level, ContagionTransmissionBrain contagionBrain) {
        if (level == null || contagionBrain == null) return;
        tickCounter++;

        // Every 30 ticks (1.5s), doctors examine proximate patients and administer cures
        if (tickCounter % 30 != 0) return;

        List<UUID> allDoctors = new ArrayList<>(independentDoctors);
        for (HospitalTent h : activeHospitals.values()) {
            allDoctors.addAll(h.doctorUuids());
        }

        for (UUID dUuid : allDoctors) {
            Entity ent = level.getEntity(dUuid);
            if (!(ent instanceof HumanNpcEntity doctor) || !doctor.isAlive()) continue;

            // Search for infected citizens in 10-block radius
            AABB searchBox = doctor.getBoundingBox().inflate(10.0D, 3.0D, 10.0D);
            List<HumanNpcEntity> patients = level.getEntitiesOfClass(HumanNpcEntity.class, searchBox,
                p -> p != doctor && p.isAlive() && contagionBrain.getInfection(p.getUUID()) != null);

            if (!patients.isEmpty()) {
                HumanNpcEntity patient = patients.get(0);
                double dist = doctor.distanceTo(patient);

                if (dist > 2.0D) {
                    doctor.getNavigation().moveTo(patient, 1.2D);
                } else {
                    // Administer medical treatment
                    var infection = contagionBrain.getInfection(patient.getUUID());
                    if (infection != null && infection.stage() != ContagionTransmissionBrain.InfectionStage.RECOVERED) {
                        infection.setStage(ContagionTransmissionBrain.InfectionStage.RECOVERED);
                        patient.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                        level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getEyeY(), patient.getZ(), 5, 0.3D, 0.3D, 0.3D, 0.05D);
                    }
                }
            }
        }
    }

    public List<HospitalTent> getActiveHospitals() {
        return List.copyOf(activeHospitals.values());
    }

    public int activeHospitalCount() {
        return activeHospitals.size();
    }
}
