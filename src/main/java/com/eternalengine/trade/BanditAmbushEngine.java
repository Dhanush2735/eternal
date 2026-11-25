package com.eternalengine.trade;

import com.eternalengine.clock.SimulationClockManager;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles bandit and highwaymen ambushes on trade caravans traversing
 * low-authority or perilous wilderness territories.
 */
public final class BanditAmbushEngine {

    public static final class AmbushRecord {
        private final String ambushId;
        private final String caravanId;
        private final List<UUID> banditUuids = new ArrayList<>();
        private final int triggerTick;
        private boolean resolved = false;

        public AmbushRecord(String ambushId, String caravanId, List<UUID> banditUuids, int triggerTick) {
            this.ambushId = ambushId;
            this.caravanId = caravanId;
            if (banditUuids != null) this.banditUuids.addAll(banditUuids);
            this.triggerTick = triggerTick;
        }

        public String ambushId() { return ambushId; }
        public String caravanId() { return caravanId; }
        public List<UUID> banditUuids() { return Collections.unmodifiableList(banditUuids); }
        public boolean isResolved() { return resolved; }
        public void setResolved(boolean r) { this.resolved = r; }
    }

    private final ConcurrentHashMap<String, AmbushRecord> activeAmbushes = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    /**
     * Spawns a coordinated bandit squad to intercept and raid a trade caravan.
     */
    public AmbushRecord triggerAmbush(CaravanEntityGroup caravan, ServerLevel level) {
        if (caravan == null || level == null || !caravan.isAlive()) {
            return null;
        }

        HumanNpcEntity master = caravan.getMasterEntity(level);
        if (master == null) return null;

        BlockPos mPos = master.blockPosition();
        String ambushId = "ambush_" + caravan.caravanId() + "_" + System.currentTimeMillis() % 10000;
        List<UUID> bandits = new ArrayList<>();

        int count = 2 + (int) (Math.random() * 3); // 2 to 4 bandits
        double spawnAngle = Math.random() * Math.PI * 2.0D;
        double spawnDist = 14.0D;

        for (int i = 0; i < count; i++) {
            double angle = spawnAngle + (i - count / 2.0D) * 0.4D;
            int x = (int) Math.floor(mPos.getX() + Math.cos(angle) * spawnDist);
            int z = (int) Math.floor(mPos.getZ() + Math.sin(angle) * spawnDist);
            int y = TerraCoordinateHelper.getPlacementY(level, x, z);

            HumanNpcEntity bandit = EternalEngineEntities.HUMAN_NPC.get().create(level);
            if (bandit != null) {
                bandit.moveTo(x + 0.5D, y, z + 0.5D, (float) Math.toDegrees(angle + Math.PI), 0.0F);
                bandit.setRole("bandit");
                bandit.setCivilizationId("outlaw");
                bandit.setCustomName(net.minecraft.network.chat.Component.literal("Highwayman"));
                bandit.setCustomNameVisible(true);

                // Arm bandit
                bandit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                bandit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                // Step 45 part 6: a bandit carries what a poor fighter here and now would carry.
                // The alternation is kept - half of them are bowmen - but the weapon is asked
                // for, so an iron axe does not appear where no iron has reached.
                com.eternalengine.war.WarGearStacks.equip(bandit, i % 2 == 0 ? "bandit" : "archer",
                        com.eternalengine.war.WarGearStacks.currentYear());

                bandit.setTarget(master);
                level.addFreshEntity(bandit);
                bandits.add(bandit.getUUID());
            }
        }

        AmbushRecord record = new AmbushRecord(ambushId, caravan.caravanId(), bandits, tickCounter);
        activeAmbushes.put(ambushId, record);
        caravan.setStatus(CaravanEntityGroup.CaravanStatus.DEFENDING);
        return record;
    }

    public void tick(ServerLevel level, TradeRouteEngine tradeRoutes, LogisticsManager logistics) {
        if (level == null) return;
        tickCounter++;

        activeAmbushes.values().removeIf(record -> {
            if (record.isResolved()) return true;

            CaravanEntityGroup caravan = logistics != null ? logistics.getCaravan(record.caravanId()) : null;

            // Clean dead bandits
            List<UUID> livingBandits = new ArrayList<>();
            for (UUID bUuid : record.banditUuids()) {
                Entity ent = level.getEntity(bUuid);
                if (ent instanceof HumanNpcEntity b && b.isAlive()) {
                    livingBandits.add(bUuid);
                }
            }

            if (livingBandits.isEmpty()) {
                // All bandits eliminated! Caravan defended successfully.
                record.setResolved(true);
                if (caravan != null && caravan.isAlive()) {
                    caravan.setStatus(CaravanEntityGroup.CaravanStatus.TRAVELING);
                }
                return true;
            }

            if (caravan == null || !caravan.isAlive() || caravan.isLooted()) {
                // Caravan was overwhelmed and looted!
                record.setResolved(true);
                if (tradeRoutes != null && caravan != null) {
                    int simYear = SimulationClockManager.isInitialized()
                        ? SimulationClockManager.getInstance().getCurrentYear()
                        : 0;
                    tradeRoutes.disruptRoute(caravan.routeId(), "Bandit raid looted caravan " + caravan.caravanId(), simYear);
                }
                return true;
            }

            return false;
        });
    }

    public List<AmbushRecord> getActiveAmbushes() {
        return List.copyOf(activeAmbushes.values());
    }

    public int activeCount() {
        return activeAmbushes.size();
    }
}
