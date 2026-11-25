package com.eternalengine.chrononexus.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * Dimension Machine: Multiversal Breach Drive.
 * Traverses vertical planes between parallel dimensions to breach the Rival Citadel.
 */
public class DimensionMachineItem extends Item {

    public DimensionMachineItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.playNotifySound(SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.2F);
            player.sendSystemMessage(Component.literal("§d[Dimension Machine] §5Multiversal rift harmonics engaged."));
            player.sendSystemMessage(Component.literal("§7Target Locked: §cThe Citadel of Zero (The Rival Dimension - The Red Line)"));
            player.sendSystemMessage(Component.literal("§7Execute breach raid with §f/ee chrono phase 6"));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
