package com.eternalengine.client;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.entity.EternalEngineEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client renderer registration for Eternal Engine entities.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EternalEngineClientRenderers {

    private EternalEngineClientRenderers() {}

    /** Step 24: Minecraft's trade screen with a townsperson. */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                com.eternalengine.money.TradeMenus.TRADE.get(), com.eternalengine.client.gui.TradeMenuScreen::new));
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EternalEngineEntities.HUMAN_NPC.get(), HumanNpcRenderer::new);
        event.registerEntityRenderer(EternalEngineEntities.RIVAL_VARIANT.get(), HumanNpcRenderer::new);
    }
}
