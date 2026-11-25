package com.eternalengine.war;

import com.eternalengine.EternalEngineMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Step 45 part 6 (A13): <b>the year gate, wired to Forge</b>.
 *
 * <p>All the deciding is in {@link WarGear} and all the item-fetching is in
 * {@link WarGearStacks}. This class only supplies the two hooks Forge actually offers, and it is
 * honest about how thin they are.
 *
 * <h2>There is no "cancel this craft" event</h2>
 *
 * <p>Forge fires {@code ItemCraftedEvent} <i>after</i> the item has been taken and offers no hook
 * that can refuse a craft beforehand. So the gate does the two things that are possible:
 *
 * <ol>
 *   <li>It <b>empties the result slot</b> while it holds an item that has not reached this place
 *       yet. The recipe still exists; the item simply cannot be taken here. A player in 1200 Paris
 *       can assemble a musket's parts and get nothing for it.</li>
 *   <li>It <b>refuses the gun mods' own crafting blocks</b> before their year, so the bench that
 *       makes the guns is shut in 1400 rather than merely unhelpful.</li>
 * </ol>
 *
 * <p><b>This does not police the player.</b> Someone who is given a musket by an admin, by
 * creative mode or by another mod keeps it and it works. The gate exists so the <i>world's</i>
 * soldiers and the world's crafting obey the year — not to take things out of inventories, which
 * would be both rude and futile.
 *
 * <h2>Why a tick and not an event</h2>
 *
 * <p>The result slot is a {@code Slot} inside a menu, and the only thing that can be known is
 * what it holds <i>now</i>. Policing it on a tick is the standard workaround for the missing
 * cancel event. It is cheap: the gate does nothing at all unless the slot is non-empty, and the
 * verdict is a string comparison until then.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class WeaponYearGate {

    private static final Logger LOG = EternalEngineMod.getLogger();

    /** How often the result slot is checked; every tick of a player's own loop is plenty. */
    private static final int EVERY = 1;

    private WeaponYearGate() {
    }

    /**
     * Empty a crafting result that holds something this place and year has never seen.
     *
     * <p>Only the player's own open menu is touched, and only its result slot.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % EVERY != 0) return;
        try {
            double[] place = WarGearStacks.placeOf(player);
            if (place == null) return;
            int year = WarGearStacks.currentYear();
            String area = WarGearStacks.areaOf(place[0], place[1], year);
            if (area == null) return;
            boolean emptied = WarGearStacks.policeResultSlot(player.containerMenu, place[0],
                    place[1], year, area);
            if (emptied && player.tickCount % 40 == 0) {
                LOG.debug("[EE war] a crafting result in {} was emptied: it had not reached "
                        + "this place by {}", area, WarGear.label(year));
            }
        } catch (Throwable t) {
            // A gate that breaks the player's tick is worse than no gate at all.
            LOG.debug("[EE war] the weapon year gate could not run: {}", t.toString());
        }
    }

    /**
     * Refuse a gun mod's crafting block before the year the gun reaches this place.
     *
     * <p>TaCZ and Scorched Guns each ship a bench of their own; leaving it open in 1400 would
     * hand over everything behind it. The block is identified by the mod that owns it, which is
     * known from its registry id rather than from a hard-coded block reference — this mod cannot
     * hold a class reference to a block in a mod that may not be installed.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            var state = event.getLevel().getBlockState(event.getPos());
            var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (key == null) return;
            String modId = key.getNamespace();
            if (!WarGearStacks.GUN_TABLES.contains(modId)) return;

            double[] place = WarGearStacks.placeOf(player);
            if (place == null) return;
            int year = WarGearStacks.currentYear();
            String area = WarGearStacks.areaOf(place[0], place[1], year);
            if (area == null) return;
            String why = WarGearStacks.blockedTable(modId, place[0], place[1], year, area);
            if (why == null) return;

            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§8" + why + ".§r"), true);
        } catch (Throwable t) {
            LOG.debug("[EE war] the gun bench gate could not run: {}", t.toString());
        }
    }

    /**
     * What one item's verdict is, as a line for a command or a log.
     *
     * @return the reason it may not exist here, or null when it may
     */
    @Nullable
    public static String whyNot(ItemStack stack, double lat, double lon, int year, String area) {
        return WarGearStacks.craftRefusal(stack, lat, lon, year, area);
    }
}
