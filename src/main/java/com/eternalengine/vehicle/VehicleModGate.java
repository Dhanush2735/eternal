package com.eternalengine.vehicle;

import com.eternalengine.EternalEngineMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Step 46 part 6 (A13, the vehicle half): <b>the mod gates, wired to Forge</b>.
 *
 * <p>All the deciding is in {@link VehicleMods}, which touches no Minecraft. This class supplies
 * the one hook Forge actually offers for a mod's crafting front door, and it is honest about how
 * thin it is — it is the same shape as step 45's {@code WeaponYearGate}, deliberately, because the
 * problem is the same one.
 *
 * <h2>Why a block right-click and not a crafting event</h2>
 *
 * <p><b>Small Ships has no crafting recipes at all.</b> Ships are built at a Dockyard, so there is
 * no result slot to empty and no recipe to refuse: the Dockyard <i>is</i> the front door. Leaving
 * it open in 3000 BC would hand over a cog two thousand years before anyone rigged a sail. So the
 * gate refuses the block, exactly as step 45 refuses a gun bench.
 *
 * <p>The block is identified by the <b>mod that owns it</b>, from its registry id, and not by a
 * class reference — this mod cannot hold a reference to a block in a mod that may not be installed.
 *
 * <h2>What this gate does not do</h2>
 *
 * <p>It does not police the player. A ship given by an admin or by creative mode sails. The gate
 * exists so the <i>world's</i> shipyards obey the year, which is A13's whole remit: optional links,
 * and everything also works without them.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class VehicleModGate {

    private static final Logger LOG = EternalEngineMod.getLogger();

    /** Mods whose own building is their front door, so the block must be refused before the year. */
    private static final String DOCKYARD_MOD = "smallships";

    private VehicleModGate() {
    }

    /**
     * Refuse a shipyard before the sail reaches this place.
     *
     * <p>The year is asked of {@link Vehicles}, which asks the ways grid and the working animals —
     * four parts of this step, in one refusal.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            var state = event.getLevel().getBlockState(event.getPos());
            var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (key == null) return;
            if (!DOCKYARD_MOD.equals(key.getNamespace())) return;

            double[] place = placeOf(player);
            if (place == null) return;
            int year = yearNow();
            String area = areaOf(place[0], place[1], year);
            if (area == null) return;

            String why = VehicleMods.get().dockyardOpens(area, year, place[0], place[1]);
            if (why == null) return;

            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§8" + why + ".§r"), true);
        } catch (Throwable t) {
            // A gate that breaks a player's click is worse than no gate at all.
            LOG.debug("[EE vehicle] the dockyard gate could not run: {}", t.toString());
        }
    }

    /**
     * What a vehicle item's verdict is, as a line for a command or a log.
     *
     * @return the reason it may not exist here, or null when it may
     */
    @Nullable
    public static String whyNot(ItemStack stack, double lat, double lon, int year, String area) {
        if (stack == null || stack.isEmpty()) return null;
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return null;
        String modId = key.getNamespace();
        VehicleMods M = VehicleMods.get();
        for (VehicleMods.Provider p : M.providers()) {
            if (!p.modid().equals(modId)) continue;
            for (String vehicleId : p.items().keySet()) {
                Vehicles.Vehicle v = Vehicles.get().byId(vehicleId);
                if (v == null) continue;
                Vehicles.Verdict d = Vehicles.get().availableAt(v, area, year, lat, lon);
                if (d != null && !d.here()) return d.why();
            }
        }
        return null;
    }

    // --------------------------------------------------------------------------------------- place

    @Nullable
    private static double[] placeOf(net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null) return null;
        var geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint(
                entity.blockPosition());
        return geo == null ? null : new double[]{geo.latitude(), geo.longitude()};
    }

    private static int yearNow() {
        return com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear();
    }

    @Nullable
    private static String areaOf(double lat, double lon, int year) {
        try {
            return com.eternalengine.money.ProductAtlas.get().place(lat, lon, year).area();
        } catch (Throwable t) {
            return null;
        }
    }
}
