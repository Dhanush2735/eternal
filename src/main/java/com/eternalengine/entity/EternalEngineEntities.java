package com.eternalengine.entity;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.chrononexus.entity.RivalVariantEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity type registry for Eternal Engine.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EternalEngineEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EternalEngineMod.MOD_ID);

    public static final RegistryObject<EntityType<HumanNpcEntity>> HUMAN_NPC =
        ENTITIES.register("human_npc", () ->
            EntityType.Builder.<HumanNpcEntity>of(HumanNpcEntity::new, MobCategory.MISC)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build(EternalEngineMod.MOD_ID + ":human_npc")
        );

    public static final RegistryObject<EntityType<RivalVariantEntity>> RIVAL_VARIANT =
        ENTITIES.register("rival_variant", () ->
            EntityType.Builder.<RivalVariantEntity>of(RivalVariantEntity::new, MobCategory.MONSTER)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(12)
                .updateInterval(3)
                .build(EternalEngineMod.MOD_ID + ":rival_variant")
        );

    private EternalEngineEntities() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HUMAN_NPC.get(), HumanNpcEntity.createAttributes().build());
        event.put(RIVAL_VARIANT.get(), RivalVariantEntity.createRivalAttributes().build());
    }
}
