package com.eternalengine.disease;

import com.eternalengine.buildings.BlockUpdateQueue;
import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
 * Manages physical quarantine checkpoints, visible city gate barricades,
 * braziers, and sanitary cordon inspectors restricting passage during epidemics.
 */
public final class QuarantineCheckpointController {

    public static final class Checkpoint {
        private final String checkpointId;
        private final String regionId;
        private final BlockPos gatePos;
        private final List<BlockPos> barricadePositions = new ArrayList<>();
        private final List<UUID> inspectorUuids = new ArrayList<>();
        private boolean active = true;

        public Checkpoint(String checkpointId, String regionId, BlockPos gatePos,
                          List<BlockPos> barricades, List<UUID> inspectors) {
            this.checkpointId = checkpointId;
            this.regionId = regionId;
            this.gatePos = gatePos;
            if (barricades != null) this.barricadePositions.addAll(barricades);
            if (inspectors != null) this.inspectorUuids.addAll(inspectors);
        }

        public String checkpointId() { return checkpointId; }
        public String regionId() { return regionId; }
        public BlockPos gatePos() { return gatePos; }
        public List<BlockPos> barricadePositions() { return Collections.unmodifiableList(barricadePositions); }
        public List<UUID> inspectorUuids() { return Collections.unmodifiableList(inspectorUuids); }
        public boolean isActive() { return active; }
        public void setActive(boolean a) { this.active = a; }
    }

    private final ConcurrentHashMap<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();

    /**
     * Establishes a fortified quarantine cordon across a roadway or gate.
     */
    public Checkpoint establishCheckpoint(String regionId, BlockPos gatePos, ServerLevel level, BlockUpdateQueue blockQueue) {
        if (level == null || gatePos == null) return null;

        String id = "checkpoint_" + regionId + "_" + System.currentTimeMillis() % 10000;
        int surfaceY = TerraCoordinateHelper.getPlacementY(level, gatePos.getX(), gatePos.getZ());
        BlockPos base = new BlockPos(gatePos.getX(), surfaceY, gatePos.getZ());
        List<BlockPos> barricades = new ArrayList<>();

        // 1. Build physical barricades across 5-block gate width
        for (int dx = -2; dx <= 2; dx++) {
            BlockPos bPos = base.offset(dx, 0, 0);
            int py = TerraCoordinateHelper.getSurfaceY(level, bPos.getX(), bPos.getZ());
            if (bPos.getY() > py) {
                if (blockQueue != null) {
                    blockQueue.enqueue(level.dimension(), bPos, Blocks.OAK_FENCE.defaultBlockState(), 5);
                }
                barricades.add(bPos);
            }
        }

        // Warning braziers at ends
        BlockPos brazierLeft = base.offset(-3, 0, 0);
        BlockPos brazierRight = base.offset(3, 0, 0);
        if (blockQueue != null) {
            blockQueue.enqueue(level.dimension(), brazierLeft, Blocks.CAMPFIRE.defaultBlockState(), 5);
            blockQueue.enqueue(level.dimension(), brazierRight, Blocks.CAMPFIRE.defaultBlockState(), 5);
            barricades.add(brazierLeft);
            barricades.add(brazierRight);
        }

        // 2. Spawn 2 Sanitary Cordon Inspectors
        List<UUID> inspectors = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            HumanNpcEntity inspector = EternalEngineEntities.HUMAN_NPC.get().create(level);
            if (inspector != null) {
                int ix = base.getX() + (i == 0 ? -1 : 1);
                int iz = base.getZ() + 1;
                int iy = TerraCoordinateHelper.getPlacementY(level, ix, iz);
                inspector.moveTo(ix + 0.5D, iy, iz + 0.5D, 180.0F, 0.0F);
                inspector.setRole("quarantine_inspector");
                inspector.setCustomName(net.minecraft.network.chat.Component.literal("Sanitary Inspector"));
                inspector.setCustomNameVisible(true);

                // Outfit inspector
                inspector.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                inspector.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                inspector.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LANTERN));
                inspector.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));

                level.addFreshEntity(inspector);
                inspectors.add(inspector.getUUID());
            }
        }

        Checkpoint cp = new Checkpoint(id, regionId, base, barricades, inspectors);
        checkpoints.put(id, cp);
        return cp;
    }

    public void tick(ServerLevel level, ContagionTransmissionBrain contagionBrain) {
        if (level == null || checkpoints.isEmpty()) return;

        for (Checkpoint cp : checkpoints.values()) {
            if (!cp.isActive()) continue;

            // Keep inspectors stationed and repel symptomatic entities
            AABB cordonZone = new AABB(cp.gatePos()).inflate(6.0D, 3.0D, 6.0D);
            List<HumanNpcEntity> nearby = level.getEntitiesOfClass(HumanNpcEntity.class, cordonZone,
                e -> e.isAlive() && !cp.inspectorUuids().contains(e.getUUID()));

            for (HumanNpcEntity npc : nearby) {
                var infection = contagionBrain != null ? contagionBrain.getInfection(npc.getUUID()) : null;
                if (infection != null && infection.stage() != ContagionTransmissionBrain.InfectionStage.RECOVERED) {
                    // Turn away symptomatic traveler: push back
                    double dx = npc.getX() - cp.gatePos().getX();
                    double dz = npc.getZ() - cp.gatePos().getZ();
                    double len = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
                    npc.setDeltaMovement((dx / len) * 0.4D, 0.1D, (dz / len) * 0.4D);
                }
            }
        }
    }

    public void dismantleCheckpoint(String checkpointId, ServerLevel level, BlockUpdateQueue blockQueue) {
        Checkpoint cp = checkpoints.remove(checkpointId);
        if (cp == null || level == null) return;
        cp.setActive(false);

        // Remove barricades via queue
        if (blockQueue != null) {
            for (BlockPos pos : cp.barricadePositions()) {
                blockQueue.enqueue(level.dimension(), pos, Blocks.AIR.defaultBlockState(), 5);
            }
        }

        // Clean inspectors
        for (UUID uuid : cp.inspectorUuids()) {
            Entity ent = level.getEntity(uuid);
            if (ent != null) ent.discard();
        }
    }

    public List<Checkpoint> getActiveCheckpoints() {
        return List.copyOf(checkpoints.values());
    }

    public int activeCount() {
        return checkpoints.size();
    }
}
