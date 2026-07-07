package com.eternalengine.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration for Eternal Engine
 * 
 * Three config files:
 * - Common: Shared settings for client and server
 * - Server: Server-only settings
 * - Client: Client-only settings (UI, rendering)
 */
public class EternalEngineConfig {
    
    // ========== COMMON CONFIG ==========
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;
    
    // ========== SERVER CONFIG ==========
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;
    
    // ========== CLIENT CONFIG ==========
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;
    
    static {
        // Build common config
        final Pair<CommonConfig, ForgeConfigSpec> commonPair = 
            new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();
        
        // Build server config
        final Pair<ServerConfig, ForgeConfigSpec> serverPair = 
            new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER = serverPair.getLeft();
        SERVER_SPEC = serverPair.getRight();
        
        // Build client config
        final Pair<ClientConfig, ForgeConfigSpec> clientPair = 
            new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }
    
    /**
     * Common configuration (shared between client and server)
     */
    public static class CommonConfig {
        // General
        public final ForgeConfigSpec.BooleanValue simulationEnabled;
        public final ForgeConfigSpec.IntValue defaultSpeed;
        public final ForgeConfigSpec.IntValue startYear;
        
        // System toggles
        public final ForgeConfigSpec.BooleanValue enableDisease;
        public final ForgeConfigSpec.BooleanValue enableWar;
        public final ForgeConfigSpec.BooleanValue enableTrade;
        public final ForgeConfigSpec.BooleanValue enableReligion;
        public final ForgeConfigSpec.BooleanValue enableTechnology;
        public final ForgeConfigSpec.BooleanValue enableClimate;
        public final ForgeConfigSpec.BooleanValue enablePopulation;
        public final ForgeConfigSpec.BooleanValue enableVipSystem;
        public final ForgeConfigSpec.BooleanValue enableReputation;
        public final ForgeConfigSpec.BooleanValue enableBuildings;
        public final ForgeConfigSpec.BooleanValue enableAlternativeTimelines;
        
        // Performance
        public final ForgeConfigSpec.IntValue elevationCacheSize;
        public final ForgeConfigSpec.IntValue terrainCacheSize;
        public final ForgeConfigSpec.IntValue buildingCacheSize;
        public final ForgeConfigSpec.IntValue eraCacheSize;
        
        // Virtualization
        public final ForgeConfigSpec.IntValue virtualizationDistance;
        public final ForgeConfigSpec.IntValue maxEntitiesPerPlayer;
        
        // Ollama integration
        public final ForgeConfigSpec.BooleanValue ollamaEnabled;
        public final ForgeConfigSpec.ConfigValue<String> ollamaEndpoint;
        public final ForgeConfigSpec.ConfigValue<String> ollamaModel;
        public final ForgeConfigSpec.IntValue ollamaTimeoutMs;
        
        // OSM/External data
        public final ForgeConfigSpec.IntValue osmTimeoutMs;
        
        // Persistence
        public final ForgeConfigSpec.IntValue backupIntervalMinutes;
        public final ForgeConfigSpec.IntValue maxBackups;
        public final ForgeConfigSpec.IntValue autoSaveIntervalMinutes;
        
        // Debug
        public final ForgeConfigSpec.BooleanValue debugLogging;
        public final ForgeConfigSpec.BooleanValue performanceLogging;
        
        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Eternal Engine Common Configuration")
                   .push("general");
            
            simulationEnabled = builder
                .comment("Enable the historical simulation")
                .define("simulationEnabled", true);
            
            defaultSpeed = builder
                .comment("Default simulation speed (1, 5, 10, 25, or 50)")
                .defineInRange("defaultSpeed", 10, 1, 50);
            
            startYear = builder
                .comment("Starting simulation year (negative for BCE)")
                .defineInRange("startYear", -3000, -300000, 2100);
            
            builder.pop();
            
            // System toggles
            builder.comment("Enable/disable individual simulation systems")
                   .push("systems");
            
            enableDisease = builder.define("enableDisease", true);
            enableWar = builder.define("enableWar", true);
            enableTrade = builder.define("enableTrade", true);
            enableReligion = builder.define("enableReligion", true);
            enableTechnology = builder.define("enableTechnology", true);
            enableClimate = builder.define("enableClimate", true);
            enablePopulation = builder.define("enablePopulation", true);
            enableVipSystem = builder.define("enableVipSystem", true);
            enableReputation = builder.define("enableReputation", true);
            enableBuildings = builder.define("enableBuildings", true);
            enableAlternativeTimelines = builder.define("enableAlternativeTimelines", true);
            
            builder.pop();
            
            // Cache sizes
            builder.comment("Cache size limits (entries)")
                   .push("cache");
            
            elevationCacheSize = builder
                .comment("Maximum elevation cache entries")
                .defineInRange("elevationCacheSize", 32768, 1000, 100000);
            
            terrainCacheSize = builder
                .comment("Maximum terrain classification cache entries")
                .defineInRange("terrainCacheSize", 16384, 1000, 50000);
            
            buildingCacheSize = builder
                .comment("Maximum building record cache entries")
                .defineInRange("buildingCacheSize", 8192, 500, 25000);
            
            eraCacheSize = builder
                .comment("Maximum era assignment cache entries")
                .defineInRange("eraCacheSize", 8192, 500, 25000);
            
            builder.pop();
            
            // Virtualization
            builder.comment("Entity virtualization settings")
                   .push("virtualization");
            
            virtualizationDistance = builder
                .comment("Distance in blocks beyond which entities are virtualized")
                .defineInRange("virtualizationDistance", 128, 32, 512);
            
            maxEntitiesPerPlayer = builder
                .comment("Maximum real entities per player")
                .defineInRange("maxEntitiesPerPlayer", 50, 10, 200);
            
            builder.pop();
            
            // Ollama
            builder.comment("Ollama LLM integration (OPTIONAL - for narrative text only)")
                   .push("ollama");
            
            ollamaEnabled = builder
                .comment("Enable Ollama integration for narrative text generation")
                .define("ollamaEnabled", false);
            
            ollamaEndpoint = builder
                .comment("Ollama API endpoint (localhost only)")
                .define("ollamaEndpoint", "http://localhost:11434/api/generate");
            
            ollamaModel = builder
                .comment("Ollama model to use")
                .define("ollamaModel", "llama2");
            
            ollamaTimeoutMs = builder
                .comment("Ollama request timeout in milliseconds")
                .defineInRange("ollamaTimeoutMs", 2000, 500, 10000);
            
            builder.pop();
            
            // External data
            builder.comment("External data settings")
                   .push("external");
            
            osmTimeoutMs = builder
                .comment("OSM query timeout in milliseconds")
                .defineInRange("osmTimeoutMs", 8000, 1000, 30000);
            
            builder.pop();
            
            // Persistence
            builder.comment("Data persistence settings")
                   .push("persistence");
            
            backupIntervalMinutes = builder
                .comment("Backup interval in minutes")
                .defineInRange("backupIntervalMinutes", 30, 5, 180);
            
            maxBackups = builder
                .comment("Maximum number of backups to keep")
                .defineInRange("maxBackups", 10, 1, 50);
            
            autoSaveIntervalMinutes = builder
                .comment("Auto-save interval in minutes")
                .defineInRange("autoSaveIntervalMinutes", 5, 1, 30);
            
            builder.pop();
            
            // Debug
            builder.comment("Debug settings")
                   .push("debug");
            
            debugLogging = builder
                .comment("Enable verbose debug logging")
                .define("debugLogging", false);
            
            performanceLogging = builder
                .comment("Enable performance metric logging")
                .define("performanceLogging", false);
            
            builder.pop();
        }
    }
    
    /**
     * Server-only configuration
     */
    public static class ServerConfig {
        // War
        public final ForgeConfigSpec.IntValue maxActiveWars;
        public final ForgeConfigSpec.IntValue warUpdateInterval;
        
        // Disease
        public final ForgeConfigSpec.IntValue maxActiveDiseases;
        public final ForgeConfigSpec.IntValue diseaseUpdateInterval;
        
        // Update intervals (in ticks)
        public final ForgeConfigSpec.IntValue populationUpdateInterval;
        public final ForgeConfigSpec.IntValue economyUpdateInterval;
        public final ForgeConfigSpec.IntValue tradeUpdateInterval;
        public final ForgeConfigSpec.IntValue climateUpdateInterval;
        public final ForgeConfigSpec.IntValue religionUpdateInterval;
        public final ForgeConfigSpec.IntValue technologyUpdateInterval;
        public final ForgeConfigSpec.IntValue reputationDecayInterval;
        public final ForgeConfigSpec.IntValue eraCheckInterval;
        
        // VIP
        public final ForgeConfigSpec.IntValue maxVipsPerServer;
        public final ForgeConfigSpec.BooleanValue allowMultipleVipsPerPlayer;
        
        // Performance
        public final ForgeConfigSpec.IntValue maxTickBudgetMs;
        public final ForgeConfigSpec.IntValue blockUpdateBudgetPerTick;
        
        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Eternal Engine Server Configuration")
                   .push("war");
            
            maxActiveWars = builder
                .comment("Maximum number of active wars")
                .defineInRange("maxActiveWars", 20, 1, 100);
            
            warUpdateInterval = builder
                .comment("War system update interval (ticks)")
                .defineInRange("warUpdateInterval", 10, 1, 100);
            
            builder.pop();
            
            builder.push("disease");
            
            maxActiveDiseases = builder
                .comment("Maximum number of active disease outbreaks")
                .defineInRange("maxActiveDiseases", 10, 1, 50);
            
            diseaseUpdateInterval = builder
                .comment("Disease system update interval (ticks)")
                .defineInRange("diseaseUpdateInterval", 20, 1, 100);
            
            builder.pop();
            
            builder.comment("System update intervals in ticks (20 ticks = 1 second)")
                   .push("intervals");
            
            populationUpdateInterval = builder
                .defineInRange("populationUpdateInterval", 100, 10, 1000);
            
            economyUpdateInterval = builder
                .defineInRange("economyUpdateInterval", 50, 10, 500);
            
            tradeUpdateInterval = builder
                .defineInRange("tradeUpdateInterval", 50, 10, 500);
            
            climateUpdateInterval = builder
                .defineInRange("climateUpdateInterval", 200, 20, 2000);
            
            religionUpdateInterval = builder
                .defineInRange("religionUpdateInterval", 150, 20, 1500);
            
            technologyUpdateInterval = builder
                .defineInRange("technologyUpdateInterval", 200, 20, 2000);
            
            reputationDecayInterval = builder
                .defineInRange("reputationDecayInterval", 100, 10, 1000);
            
            eraCheckInterval = builder
                .defineInRange("eraCheckInterval", 1000, 100, 10000);
            
            builder.pop();
            
            builder.push("vip");
            
            maxVipsPerServer = builder
                .comment("Maximum VIP slots that can be claimed on the server")
                .defineInRange("maxVipsPerServer", 50, 1, 200);
            
            allowMultipleVipsPerPlayer = builder
                .comment("Allow players to claim multiple VIP slots")
                .define("allowMultipleVipsPerPlayer", false);
            
            builder.pop();
            
            builder.push("performance");
            
            maxTickBudgetMs = builder
                .comment("Maximum milliseconds per tick for EE processing")
                .defineInRange("maxTickBudgetMs", 10, 1, 50);
            
            blockUpdateBudgetPerTick = builder
                .comment("Maximum block updates per tick")
                .defineInRange("blockUpdateBudgetPerTick", 500, 50, 2000);
            
            builder.pop();
        }
    }
    
    /**
     * Client-only configuration
     */
    public static class ClientConfig {
        // HUD
        public final ForgeConfigSpec.BooleanValue showHuds;
        public final ForgeConfigSpec.DoubleValue hudScale;
        public final ForgeConfigSpec.ConfigValue<String> hudPosition;
        
        // Map overlay
        public final ForgeConfigSpec.BooleanValue showMapOverlay;
        public final ForgeConfigSpec.BooleanValue showTerritoryOverlay;
        public final ForgeConfigSpec.BooleanValue showWarOverlay;
        public final ForgeConfigSpec.BooleanValue showTradeOverlay;
        public final ForgeConfigSpec.BooleanValue showDiseaseOverlay;
        
        // Sound
        public final ForgeConfigSpec.DoubleValue soundVolume;
        public final ForgeConfigSpec.BooleanValue playEraAmbience;
        public final ForgeConfigSpec.BooleanValue playEventSounds;
        
        // Visual
        public final ForgeConfigSpec.BooleanValue showVipNameTags;
        public final ForgeConfigSpec.BooleanValue showBuildingInfo;
        public final ForgeConfigSpec.BooleanValue showEraIndicator;
        
        public ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Eternal Engine Client Configuration")
                   .push("hud");
            
            showHuds = builder
                .comment("Show Eternal Engine HUD elements")
                .define("showHuds", true);
            
            hudScale = builder
                .comment("HUD element scale")
                .defineInRange("hudScale", 1.0, 0.5, 2.0);
            
            hudPosition = builder
                .comment("HUD position (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)")
                .define("hudPosition", "TOP_RIGHT");
            
            builder.pop();
            
            builder.push("mapOverlay");
            
            showMapOverlay = builder
                .comment("Show map overlays (requires Journeymap or Xaeros)")
                .define("showMapOverlay", true);
            
            showTerritoryOverlay = builder
                .define("showTerritoryOverlay", true);
            
            showWarOverlay = builder
                .define("showWarOverlay", true);
            
            showTradeOverlay = builder
                .define("showTradeOverlay", false);
            
            showDiseaseOverlay = builder
                .define("showDiseaseOverlay", false);
            
            builder.pop();
            
            builder.push("sound");
            
            soundVolume = builder
                .comment("Eternal Engine sound volume")
                .defineInRange("soundVolume", 1.0, 0.0, 1.0);
            
            playEraAmbience = builder
                .comment("Play era-appropriate ambient sounds")
                .define("playEraAmbience", true);
            
            playEventSounds = builder
                .comment("Play sounds for major events")
                .define("playEventSounds", true);
            
            builder.pop();
            
            builder.push("visual");
            
            showVipNameTags = builder
                .comment("Show special name tags for VIP entities")
                .define("showVipNameTags", true);
            
            showBuildingInfo = builder
                .comment("Show building information on hover")
                .define("showBuildingInfo", true);
            
            showEraIndicator = builder
                .comment("Show current era indicator")
                .define("showEraIndicator", true);
            
            builder.pop();
        }
    }
}
