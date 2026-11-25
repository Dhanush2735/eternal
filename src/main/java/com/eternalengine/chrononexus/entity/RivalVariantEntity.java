package com.eternalengine.chrononexus.entity;

import com.eternalengine.entity.HumanNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The Rival: An intelligent, mirror-variant entity from the authoritarian Rival Dimension.
 * Possesses identical reflexes, hostile target acquisition, and futuristic obsidian armor.
 */
public class RivalVariantEntity extends HumanNpcEntity {

    private UUID mirroredPlayerUuid;
    private String mirroredPlayerName = "Variant-Omega";

    public RivalVariantEntity(EntityType<? extends HumanNpcEntity> type, Level level) {
        super(type, level);
        this.setCivilizationId("rival_dimension");
        this.setRole("rival_infiltrator");
        this.setEra("FUTURE");
        this.setCustomName(Component.literal("§c[Rival] §f" + mirroredPlayerName));
        this.setCustomNameVisible(true);
        equipRivalGear();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createRivalAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 50.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.35D)
            .add(Attributes.ATTACK_DAMAGE, 7.0D)
            .add(Attributes.ARMOR, 12.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
            .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    private void equipRivalGear() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
    }

    public void setMirroredPlayer(Player player) {
        if (player != null) {
            this.mirroredPlayerUuid = player.getUUID();
            this.mirroredPlayerName = player.getName().getString();
            this.setCustomName(Component.literal("§c[Rival] §f" + this.mirroredPlayerName));
        }
    }

    public UUID getMirroredPlayerUuid() {
        return mirroredPlayerUuid;
    }

    public String getMirroredPlayerName() {
        return mirroredPlayerName;
    }
}
