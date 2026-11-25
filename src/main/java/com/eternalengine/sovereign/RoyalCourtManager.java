package com.eternalengine.sovereign;

import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sovereign Throne Room & Royal Court Manager.
 * Garrisons capital palaces with resident sovereign rulers, elite royal bodyguards,
 * and court ministers receiving foreign ambassadors and player petitions.
 */
public final class RoyalCourtManager {

    public static final class ThroneRoom {
        private final String courtId;
        private final String civId;
        private final BlockPos thronePos;
        private final UUID rulerUuid;
        private final List<UUID> bodyguardUuids = new ArrayList<>();
        private final UUID chancellorUuid;

        public ThroneRoom(String courtId, String civId, BlockPos thronePos,
                          UUID rulerUuid, List<UUID> bodyguards, UUID chancellorUuid) {
            this.courtId = courtId;
            this.civId = civId;
            this.thronePos = thronePos;
            this.rulerUuid = rulerUuid;
            if (bodyguards != null) this.bodyguardUuids.addAll(bodyguards);
            this.chancellorUuid = chancellorUuid;
        }

        public String courtId() { return courtId; }
        public String civId() { return civId; }
        public BlockPos thronePos() { return thronePos; }
        public UUID rulerUuid() { return rulerUuid; }
        public List<UUID> bodyguardUuids() { return Collections.unmodifiableList(bodyguardUuids); }
        public UUID chancellorUuid() { return chancellorUuid; }
    }

    private final ConcurrentHashMap<String, ThroneRoom> activeCourts = new ConcurrentHashMap<>();

    public ThroneRoom establishThroneRoom(String civId, BlockPos thronePos, ServerLevel level) {
        if (civId == null || thronePos == null || level == null) return null;

        String id = "court_" + civId + "_" + System.currentTimeMillis() % 10000;
        int surfaceY = TerraCoordinateHelper.getPlacementY(level, thronePos.getX(), thronePos.getZ());
        BlockPos base = new BlockPos(thronePos.getX(), surfaceY, thronePos.getZ());

        // 1. Spawn Sovereign Ruler
        HumanNpcEntity ruler = EternalEngineEntities.HUMAN_NPC.get().create(level);
        if (ruler == null) return null;

        ruler.moveTo(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
        ruler.setRole("sovereign_ruler");
        ruler.setCivilizationId(civId);
        ruler.setCustomName(Component.literal("Sovereign of " + civId.toUpperCase(java.util.Locale.ROOT)));
        ruler.setCustomNameVisible(true);

        // Equip royal regalia
        ruler.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET)); // Crown
        ruler.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        ruler.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD)); // Scepter
        level.addFreshEntity(ruler);

        // 2. Spawn 2 Elite Royal Bodyguards
        List<UUID> bodyguards = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            HumanNpcEntity guard = EternalEngineEntities.HUMAN_NPC.get().create(level);
            if (guard != null) {
                int gx = base.getX() + (i == 0 ? -2 : 2);
                int gy = TerraCoordinateHelper.getPlacementY(level, gx, base.getZ());
                guard.moveTo(gx + 0.5D, gy, base.getZ() + 0.5D, 0.0F, 0.0F);
                guard.setRole("royal_guard");
                guard.setCivilizationId(civId);
                guard.setCustomName(Component.literal("Praetorian Guard"));
                guard.setCustomNameVisible(true);

                // Step 45 part 6: a guard's gear is asked for, not assumed. The armoury decides
                // what a guard in this place and year would carry; the resolver decides which
                // item stands for it. In Gaul in 200 BC that is not an iron helmet.
                com.eternalengine.war.WarGearStacks.equip(guard, "guard",
                        com.eternalengine.war.WarGearStacks.currentYear());
                level.addFreshEntity(guard);
                bodyguards.add(guard.getUUID());
            }
        }

        // 3. Spawn Chancellor
        HumanNpcEntity chancellor = EternalEngineEntities.HUMAN_NPC.get().create(level);
        UUID chanUuid = null;
        if (chancellor != null) {
            int cy = TerraCoordinateHelper.getPlacementY(level, base.getX() + 1, base.getZ() + 2);
            chancellor.moveTo(base.getX() + 1.5D, cy, base.getZ() + 2.5D, 180.0F, 0.0F);
            chancellor.setRole("court_chancellor");
            chancellor.setCivilizationId(civId);
            chancellor.setCustomName(Component.literal("Imperial Chancellor"));
            chancellor.setCustomNameVisible(true);
            chancellor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
            level.addFreshEntity(chancellor);
            chanUuid = chancellor.getUUID();
        }

        ThroneRoom court = new ThroneRoom(id, civId, base, ruler.getUUID(), bodyguards, chanUuid);
        activeCourts.put(civId.toLowerCase(java.util.Locale.ROOT), court);
        return court;
    }

    public boolean petitionAudience(ServerPlayer player, String civId, String petitionType) {
        if (player == null || civId == null) return false;
        ThroneRoom court = activeCourts.get(civId.toLowerCase(java.util.Locale.ROOT));
        if (court == null) {
            player.sendSystemMessage(Component.literal("§c[Court] No established throne room found for " + civId));
            return false;
        }

        player.sendSystemMessage(Component.literal("§6[Royal Audience] §fThe Sovereign of "
            + civId.toUpperCase(java.util.Locale.ROOT) + " hears your petition for: §e" + petitionType));
        player.sendSystemMessage(Component.literal("§a[Royal Audience] §fThe petition is granted! Royal court recognition bestowed."));
        return true;
    }

    public List<ThroneRoom> getActiveCourts() {
        return List.copyOf(activeCourts.values());
    }

    public ThroneRoom getCourt(String civId) {
        if (civId == null) return null;
        return activeCourts.get(civId.toLowerCase(java.util.Locale.ROOT));
    }
}
