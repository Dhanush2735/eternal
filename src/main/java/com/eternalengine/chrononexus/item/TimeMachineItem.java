package com.eternalengine.chrononexus.item;

import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.clock.TimeTravel;
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
 * Portable Time Machine Jump Apparatus, which <b>does not travel in time</b>.
 *
 * <p>Step 48 (F9) made the rule explicit and this item was pointing at a command that does not exist:
 * it told the player to use {@code /ee time set <year>}, and the command that moves the clock is
 * {@code /ee date <year> <month> <day>}, for an operator. So the item now tells the time and says
 * plainly that it does not move it, which is the truth and is also the reason there is only one door
 * to the clock.
 */
public class TimeMachineItem extends Item {

    public TimeMachineItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.4F);

            if (SimulationClockManager.isInitialized()) {
                int currentYear = SimulationClockManager.getInstance().getCurrentYear();
                player.sendSystemMessage(Component.literal("§6[Time Machine] §7It is §e" + currentYear
                        + "§7, and it is the same year for everybody here."));
                player.sendSystemMessage(Component.literal("§7" + TimeTravel.ruleLine(currentYear)));
                player.sendSystemMessage(Component.literal("§8This apparatus reads the clock. It does "
                        + "not move it: the world is the time machine, and one date serves every "
                        + "player in it."));
            } else {
                player.sendSystemMessage(Component.literal("§6[Time Machine] §7The clock is not "
                        + "running in this world, so there is no year to read."));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
