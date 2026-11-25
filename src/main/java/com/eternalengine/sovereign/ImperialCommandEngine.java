package com.eternalengine.sovereign;

import com.eternalengine.buildings.BlockUpdateQueue;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.eternalengine.trade.EconomyEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Imperial Command Dashboard & Grand Strategy Engine.
 * Empowers sovereign rulers and players who claim VIP thrones to commission
 * imperial megaprojects, set military strategic doctrines, and execute fiscal/monetary policies.
 */
public final class ImperialCommandEngine {

    public enum MegaprojectType {
        GRAND_COLOSSEUM(5000L),
        CITY_WALL_CIRCUIT(3500L),
        ROYAL_CATHEDRAL(4500L),
        AQUEDUCT_VIADUCT(3000L);

        private final long treasuryCost;

        MegaprojectType(long cost) {
            this.treasuryCost = cost;
        }

        public long treasuryCost() {
            return treasuryCost;
        }
    }

    public enum MilitaryDoctrine {
        SCORCHED_EARTH,
        ATTRITION_DEFENSE,
        DECISIVE_BLITZKRIEG,
        COMMERCE_RAIDING
    }

    public static final class MegaprojectCommission {
        private final String projectId;
        private final String civId;
        private final MegaprojectType type;
        private final BlockPos origin;
        private final int totalBlocks;
        private boolean completed = false;

        public MegaprojectCommission(String projectId, String civId, MegaprojectType type,
                                     BlockPos origin, int totalBlocks) {
            this.projectId = projectId;
            this.civId = civId;
            this.type = type;
            this.origin = origin;
            this.totalBlocks = totalBlocks;
        }

        public String projectId() { return projectId; }
        public String civId() { return civId; }
        public MegaprojectType type() { return type; }
        public BlockPos origin() { return origin; }
        public int totalBlocks() { return totalBlocks; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean c) { this.completed = c; }
    }

    private final ConcurrentHashMap<String, MegaprojectCommission> activeMegaprojects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MilitaryDoctrine> civDoctrines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> civTaxRates = new ConcurrentHashMap<>();

    /**
     * Commissions an Imperial Megaproject, deploying scaffolding and stonecutters.
     */
    public MegaprojectCommission commissionMegaproject(String civId, MegaprojectType type, BlockPos origin,
                                                       ServerLevel level, BlockUpdateQueue blockQueue,
                                                       EconomyEngine economy) {
        if (civId == null || type == null || origin == null || level == null) return null;

        String id = "megaproject_" + type.name().toLowerCase(Locale.ROOT) + "_" + System.currentTimeMillis() % 10000;
        int surfaceY = TerraCoordinateHelper.getPlacementY(level, origin.getX(), origin.getZ());
        BlockPos base = new BlockPos(origin.getX(), surfaceY, origin.getZ());

        int size = 11;
        int height = 5;
        int blocksCount = 0;

        // 1. Enqueue foundation & wooden scaffolding via BlockUpdateQueue
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                BlockPos p = base.offset(x, 1, z);
                int py = TerraCoordinateHelper.getSurfaceY(level, p.getX(), p.getZ());
                if (p.getY() > py && blockQueue != null) {
                    boolean corner = (x == -size / 2 || x == size / 2) && (z == -size / 2 || z == size / 2);
                    if (corner) {
                        blockQueue.enqueue(level.dimension(), p, Blocks.SCAFFOLDING.defaultBlockState(), 5);
                    } else {
                        blockQueue.enqueue(level.dimension(), p, Blocks.STONE_BRICKS.defaultBlockState(), 5);
                    }
                    blocksCount++;
                }
            }
        }

        // 2. Spawn 2 Imperial Builders / Stonecutters
        for (int i = 0; i < 2; i++) {
            HumanNpcEntity mason = EternalEngineEntities.HUMAN_NPC.get().create(level);
            if (mason != null) {
                int mx = base.getX() + (i == 0 ? -1 : 1);
                int my = TerraCoordinateHelper.getPlacementY(level, mx, base.getZ());
                mason.moveTo(mx + 0.5D, my, base.getZ() + 0.5D, 0.0F, 0.0F);
                mason.setRole("imperial_mason");
                mason.setCivilizationId(civId);
                mason.setCustomName(Component.literal("Imperial Mason"));
                mason.setCustomNameVisible(true);
                mason.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                mason.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                level.addFreshEntity(mason);
            }
        }

        // 3. Deduct treasury cost
        if (economy != null) {
            economy.applyExpense(civId, type.treasuryCost(), "megaproject:" + type.name(), null);
        }

        MegaprojectCommission commission = new MegaprojectCommission(id, civId, type, base, blocksCount);
        activeMegaprojects.put(id, commission);
        return commission;
    }

    public void setDoctrine(String civId, MilitaryDoctrine doctrine) {
        if (civId != null && doctrine != null) {
            civDoctrines.put(civId.toLowerCase(Locale.ROOT), doctrine);
        }
    }

    public MilitaryDoctrine getDoctrine(String civId) {
        if (civId == null) return MilitaryDoctrine.ATTRITION_DEFENSE;
        return civDoctrines.getOrDefault(civId.toLowerCase(Locale.ROOT), MilitaryDoctrine.ATTRITION_DEFENSE);
    }

    public void setTaxRate(String civId, double rate) {
        if (civId != null) {
            civTaxRates.put(civId.toLowerCase(Locale.ROOT), Math.max(0.05D, Math.min(0.50D, rate)));
        }
    }

    public double getTaxRate(String civId) {
        if (civId == null) return 0.15D;
        return civTaxRates.getOrDefault(civId.toLowerCase(Locale.ROOT), 0.15D);
    }

    /**
     * Debases currency purity in the mint: provides instant gold injection but sparks economic inflation.
     */
    public boolean debaseCurrency(String civId, double dilutionPercent, EconomyEngine economy, WorldStateManager state) {
        if (civId == null) return false;

        long goldInjection = 4000L;
        if (economy != null) {
            economy.applyIncome(civId, goldInjection, "currency_debasement", state);
        }
        if (state != null) {
            state.setFlag("economy.inflation." + civId.toLowerCase(Locale.ROOT), "true");
            state.setFlag("economy.debasement_rate." + civId.toLowerCase(Locale.ROOT), Double.toString(dilutionPercent));
        }
        return true;
    }

    public List<MegaprojectCommission> getActiveMegaprojects() {
        return List.copyOf(activeMegaprojects.values());
    }
}
