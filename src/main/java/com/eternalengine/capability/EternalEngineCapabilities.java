package com.eternalengine.capability;

import com.eternalengine.EternalEngineMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Capability registration and management for Eternal Engine.
 * 
 * Capabilities:
 * - PlayerSimulationCapability: VIP data, reputation, action history
 * - EntityCivilizationCapability: Civ membership, role, loyalty
 * - ChunkEraCapability: Era, owner, buildings
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public class EternalEngineCapabilities {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    // Capability instances
    public static final Capability<IPlayerSimulationData> PLAYER_SIMULATION = 
        CapabilityManager.get(new CapabilityToken<>() {});
    
    public static final Capability<IEntityCivilizationData> ENTITY_CIVILIZATION = 
        CapabilityManager.get(new CapabilityToken<>() {});
    
    public static final Capability<IChunkEraData> CHUNK_ERA = 
        CapabilityManager.get(new CapabilityToken<>() {});
    
    // Resource locations for capability attachment
    private static final ResourceLocation PLAYER_CAP_ID = 
        new ResourceLocation(EternalEngineMod.MOD_ID, "player_simulation");
    private static final ResourceLocation ENTITY_CAP_ID = 
        new ResourceLocation(EternalEngineMod.MOD_ID, "entity_civilization");
    private static final ResourceLocation CHUNK_CAP_ID = 
        new ResourceLocation(EternalEngineMod.MOD_ID, "chunk_era");
    
    /**
     * Register capabilities
     */
    public static void register() {
        LOGGER.info("[Capabilities] Registering capabilities...");
        // Capabilities are registered via the event below
    }
    
    /**
     * Register capability classes
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPlayerSimulationData.class);
        event.register(IEntityCivilizationData.class);
        event.register(IChunkEraData.class);
        
        LOGGER.info("[Capabilities] Registered 3 capability classes");
    }
    
    /**
     * Attach capabilities to players
     */
    @SubscribeEvent
    public static void attachPlayerCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            if (!player.getCapability(PLAYER_SIMULATION).isPresent()) {
                event.addCapability(PLAYER_CAP_ID, new PlayerSimulationCapability.Provider());
            }
        }
    }
    
    /**
     * Attach capabilities to entities (villagers, etc.)
     */
    @SubscribeEvent
    public static void attachEntityCapabilities(AttachCapabilitiesEvent<Entity> event) {
        Entity entity = event.getObject();
        // Attach to villagers and other relevant entities
        if (shouldHaveCivilizationCapability(entity)) {
            if (!entity.getCapability(ENTITY_CIVILIZATION).isPresent()) {
                event.addCapability(ENTITY_CAP_ID, new EntityCivilizationCapability.Provider());
            }
        }
    }
    
    /**
     * Attach capabilities to chunks
     */
    @SubscribeEvent
    public static void attachChunkCapabilities(AttachCapabilitiesEvent<LevelChunk> event) {
        if (!event.getObject().getCapability(CHUNK_ERA).isPresent()) {
            event.addCapability(CHUNK_CAP_ID, new ChunkEraCapability.Provider());
        }
    }
    
    /**
     * Check if an entity should have civilization capability
     */
    private static boolean shouldHaveCivilizationCapability(Entity entity) {
        // Add villagers and custom EE entities
        return entity instanceof net.minecraft.world.entity.npc.Villager ||
               entity.getType().getDescriptionId().contains("eternalengine");
    }
    
    /**
     * Get player simulation data
     */
    public static IPlayerSimulationData getPlayerData(Player player) {
        return player.getCapability(PLAYER_SIMULATION)
            .orElseThrow(() -> new IllegalStateException("Player missing simulation capability"));
    }
    
    /**
     * Get entity civilization data
     */
    public static IEntityCivilizationData getEntityData(Entity entity) {
        return entity.getCapability(ENTITY_CIVILIZATION)
            .orElse(null);
    }
    
    /**
     * Get chunk era data
     */
    public static IChunkEraData getChunkData(LevelChunk chunk) {
        return chunk.getCapability(CHUNK_ERA)
            .orElse(null);
    }
}
