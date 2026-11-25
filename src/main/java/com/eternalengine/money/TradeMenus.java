package com.eternalengine.money;

import com.eternalengine.EternalEngineMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Step 24: the trade screen's menu type (Minecraft's trade screen with a townsperson). */
public final class TradeMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            EternalEngineMod.MOD_ID);

    public static final RegistryObject<MenuType<TradeMenu>> TRADE = MENUS.register("trade",
            () -> IForgeMenuType.create(TradeMenu::client));

    private TradeMenus() {
    }
}
