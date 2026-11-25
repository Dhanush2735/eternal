package com.eternalengine.chrononexus.item;

import com.eternalengine.EternalEngineMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Deferred Registry for Chrono-Nexus Items.
 */
public final class ChronoItems {

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, EternalEngineMod.MOD_ID);

    public static final RegistryObject<Item> TIME_MACHINE =
        ITEMS.register("time_machine", TimeMachineItem::new);

    public static final RegistryObject<Item> DIMENSION_MACHINE =
        ITEMS.register("dimension_machine", DimensionMachineItem::new);

    public static final RegistryObject<Item> CHRONO_BLACK_BOX =
        ITEMS.register("chrono_black_box", ChronoBlackBoxItem::new);

    private ChronoItems() {}
}
