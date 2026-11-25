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
 * Chrono Black Box: Ticking audio and telemetry recorder from Dimension Prime.
 */
public class ChronoBlackBoxItem extends Item {

    public ChronoBlackBoxItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.PLAYERS, 0.7F, 1.8F);
            player.sendSystemMessage(Component.literal("§b[Chrono Black Box] §7Decrypting flight telemetry log #001..."));
            player.sendSystemMessage(Component.literal("§8\"...The Rivals struck our facility at the moment of invention. Our only escape was the emergency jump to -3000 BC...\""));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
