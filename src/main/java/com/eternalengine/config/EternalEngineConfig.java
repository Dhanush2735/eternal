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
    /**
     * A server config value that is safe to read before the specs are loaded (turn 235: the dev
     * server crashed in SimulationEngine's constructor, which reads config during commonSetup,
     * before Forge loads the TOMLs; production hides the error today but every read must stay
     * legal). Before load - or on any surprise - the given fallback stands in, so callers see the
     * same answer the TOML would have given.
     */
    public static <T> T server(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return SERVER_SPEC.isLoaded() ? value.get() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

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

        // Presence (the people who appear near a player because a place was occupied that year)
        public final ForgeConfigSpec.IntValue presenceMinSpawnBlocks;
        public final ForgeConfigSpec.IntValue presenceSpawnDistance;
        public final ForgeConfigSpec.IntValue presenceCrowdCeiling;
        public final ForgeConfigSpec.BooleanValue presenceDrawnCrowd;
        public final ForgeConfigSpec.IntValue presenceDrawnFiguresMax;
        public final ForgeConfigSpec.DoubleValue presenceCrowdConcentration;
        public final ForgeConfigSpec.IntValue presenceCrowdInterestBlocks;
        public final ForgeConfigSpec.BooleanValue presenceSettleGoals;
        public final ForgeConfigSpec.IntValue presenceSettleBlocks;
        public final ForgeConfigSpec.IntValue presenceNameTagBlocks;
        public final ForgeConfigSpec.IntValue presenceDistrictRadius;
        
        // OSM/External data
        public final ForgeConfigSpec.IntValue osmTimeoutMs;
        
        // Persistence
        public final ForgeConfigSpec.IntValue backupIntervalMinutes;
        public final ForgeConfigSpec.IntValue maxBackups;
        public final ForgeConfigSpec.IntValue autoSaveIntervalMinutes;

        // Clock (step 14)
        public final ForgeConfigSpec.BooleanValue pauseWhenEmpty;
        
        // Market (step 19b)
        public final ForgeConfigSpec.IntValue merchantBuyPercent;
        public final ForgeConfigSpec.IntValue merchantBudgetDays;
        public final ForgeConfigSpec.IntValue moneyChangerFeePercent;
        public final ForgeConfigSpec.IntValue moneyChangerStrangeFeePercent;

        // History book (step 20)
        public final ForgeConfigSpec.BooleanValue historyBookTimeTraveller;
        public final ForgeConfigSpec.BooleanValue historyBookOnJoin;

        // Minecraft's own monsters, raids and traders in the history world (step 21)
        public final ForgeConfigSpec.BooleanValue minecraftMonsters;
        public final ForgeConfigSpec.BooleanValue raidsAndPatrols;
        public final ForgeConfigSpec.BooleanValue wanderingTraders;

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
                .comment("Default simulation speed for a new world or /ee reset: days of history per Minecraft day",
                    "(1, 5, 10, 25 or 50; the same number as the Terra++ time scale). 1 = one day per Minecraft day.")
                .defineInRange("defaultSpeed", 1, 1, 50);
            
            startYear = builder
                .comment("Starting simulation year (negative for BCE)")
                .defineInRange("startYear", -3000, -300000, 2100);

            pauseWhenEmpty = builder
                .comment("Stop history (the world year, wars, disease, trade) while nobody is online, and go on",
                         "when somebody joins. In Terra++ time the year is the Minecraft day count, so the day/night",
                         "cycle is paused too while the server is empty (and switched back on when somebody joins).")
                .define("pauseWhenEmpty", true);
            
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
            
            // Presence
            builder.comment("Presence NPCs: the people who appear because a place was occupied in the simulated year")
                   .push("presence");
            
            presenceMinSpawnBlocks = builder
                .comment("A presence NPC never appears closer to a player than this, in blocks",
                         "Vanilla mobs use 24: they never spawn within 24 blocks of a player",
                         "Lower it to bring people closer; 8 is the minimum that still counts as 'not beside you'")
                .defineInRange("presenceMinSpawnBlocks", 24, 8, 96);
            
            presenceSpawnDistance = builder
                .comment("How far away people may appear, in blocks -- and the distance beyond which",
                         "they despawn immediately, exactly as a vanilla mob does at 128",
                         "Vanilla: 128")
                .defineInRange("presenceSpawnDistance", 128, 48, 192);
            
            presenceDistrictRadius = builder
                .comment("The area a place's crowd occupies, in blocks: people gather inside this",
                         "radius around the site's centre (or a nearby district) and are kept in it",
                         "Smaller means a denser-looking crowd for the same number of people")
                .defineInRange("presenceDistrictRadius", 28, 8, 64);
            
            presenceNameTagBlocks = builder
                .comment("A nameplate floats over a presence NPC only within this distance, in blocks",
                         "Keeps a distant crowd as figures rather than a list of names you can count",
                         "Set it equal to presenceSpawnDistance to always show names")
                .defineInRange("presenceNameTagBlocks", 24, 4, 128);
            
            presenceCrowdCeiling = builder
                .comment("The most people allowed to stand in one place at once, whatever their",
                         "population says. The density curve's own city ceiling is 40; this caps it",
                         "further. Measure with /debug before raising it -- the AI is cheap now, but",
                         "bodies still collide, tick physics and get sent to your client, and a packed",
                         "square is where collisions turn into lag. 8..64")
                .defineInRange("presenceCrowdCeiling", 40, 8, 64);
            
            presenceDrawnCrowd = builder
                .comment("Draw a crowd of figures around the living few. This is the answer to",
                         "\"a group is not a crowd\": a couple of dozen people can think, but a square",
                         "looks busy at a few hundred, so the rest are stood up as figures with their",
                         "AI switched off -- no goals, no navigation, no path search, nothing to tick",
                         "Set false to go back to the living people being all anyone sees")
                .define("presenceDrawnCrowd", true);

            presenceDrawnFiguresMax = builder
                .comment("The most figures drawn in one district, whatever the population says",
                         "The drawn count is the square root of the population: 348 -> 25, 142,174 ->",
                         "377, 980,600 -> 400. This caps it. Figures do not tick, but they are sent to",
                         "your client and drawn, so this is a client-side budget, not a server one.",
                         "Set it to 25 to see the old group of people; 100..400 is a readable crowd")
                .defineInRange("presenceDrawnFiguresMax", 400, 25, 600);

            presenceCrowdConcentration = builder
                .comment("How much more than the average a chunk at the place's heart carries",
                         "The population is divided over the chunks of the place's area: an average",
                         "chunk of Secunderabad, 1948 holds 0.32 people. 0 is exactly that division --",
                         "people evenly spread, which is honest but looks empty. Higher gathers them:",
                         "at 40 the market's chunk holds 13 and a 64-block view holds about 530; at 241",
                         "the market's chunk is packed full (77) and the view holds thousands, which",
                         "needs the drawn-renderer route to afford. 0..400")
                .defineInRange("presenceCrowdConcentration", 40.0d, 0.0d, 400.0d);

            presenceCrowdInterestBlocks = builder
                .comment("How far from a player the crowd is stood up at all, in blocks",
                         "The place exists across its whole area, but figures only exist where a",
                         "player can see them: 64 blocks is about 49 chunks, a few hundred figures",
                         "Raise it to 128 to see further and pay for it; lower it to 32 to pay less")
                .defineInRange("presenceCrowdInterestBlocks", 64, 32, 160);

            presenceSettleGoals = builder
                .comment("A district crowd stands its ground instead of pacing about",
                         "Pacing means pathfinding -- an A* search, the most expensive thing a mob does",
                         "Settled people still watch you and glance around; they just stop picking a",
                         "destination. Set false to restore vanilla wandering at roughly twice the cost")
                .define("presenceSettleGoals", true);
            
            presenceSettleBlocks = builder
                .comment("Past this many blocks from every player, a crowd member stops thinking:",
                         "the AI is switched off and comes back the moment somebody is close again",
                         "Default 32 matches vanilla's 'never randomly despawned' radius, so you pay",
                         "for the thinking of exactly the people the game considers in play. Vanilla",
                         "does the same thing at the simulation-distance boundary. 8..96; a large",
                         "value effectively disables it")
                .defineInRange("presenceSettleBlocks", 32, 8, 96);
            
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
                .comment("Minutes between two backups of the same save file (a save in between overwrites the file",
                         "without a new backup), so maxBackups backups cover maxBackups x this many minutes")
                .defineInRange("backupIntervalMinutes", 30, 5, 180);
            
            maxBackups = builder
                .comment("Maximum number of backups to keep")
                .defineInRange("maxBackups", 10, 1, 50);
            
            autoSaveIntervalMinutes = builder
                .comment("Auto-save interval in minutes")
                .defineInRange("autoSaveIntervalMinutes", 5, 1, 30);
            
            builder.pop();
            
            // Market (step 19b)
            builder.comment("Merchants and the money-changer (step 19b). Amounts follow an ordinary labourer's pay",
                            "for a day in the place and year.")
                   .push("market");

            merchantBuyPercent = builder
                .comment("What share of a good's worth a merchant pays for it (he sells it on for more)")
                .defineInRange("merchantBuyPercent", 70, 10, 100);

            merchantBudgetDays = builder
                .comment("How much one merchant can pay out in one Minecraft day, in days of a labourer's pay here")
                .defineInRange("merchantBudgetDays", 8, 0, 1000);

            moneyChangerFeePercent = builder
                .comment("The money-changer's cut, in every 100, for money with a fixed rate (Indian rupees in",
                         "Hyderabad, pounds into rupees)")
                .defineInRange("moneyChangerFeePercent", 2, 0, 50);

            moneyChangerStrangeFeePercent = builder
                .comment("His cut for money with no fixed rate (old money, money from far away), which he values by",
                         "what it buys")
                .defineInRange("moneyChangerStrangeFeePercent", 10, 0, 50);

            builder.pop();

            // History book (step 20)
            builder.comment("The history book (step 20): your notes of what people told you and what you read.")
                   .push("historyBook");

            historyBookTimeTraveller = builder
                .comment("Time traveller view: the book also shows what comes later (after today's date). Off: the",
                         "book holds only what you have heard or read, as villagers know nothing of the future.")
                .define("historyBookTimeTraveller", false);

            historyBookOnJoin = builder
                .comment("Step 22: give every new player a history book on joining (the step-20 way). Off: the book is",
                         "bought from a seller (a merchant, trader, pedlar, paper seller or newsletter writer), in the",
                         "form people keep notes in there and then; your notes are kept either way.")
                .define("historyBookOnJoin", false);

            builder.pop();

            // Minecraft's own monsters, raids and traders (step 21)
            builder.comment("Minecraft's own monsters, raids and wandering traders in the history world (step 21). The",
                            "world is the real Earth in a real year: zombies, creepers, pillager raids and a wandering",
                            "trader with a llama do not belong in it, so they are off unless you turn them on here. Only",
                            "things that come by themselves are stopped; spawn eggs, spawners and /summon still work.",
                            "The Nether and the End are not touched.")
                   .push("minecraftMobs");

            minecraftMonsters = builder
                .comment("Monsters spawn by themselves: zombies, skeletons, creepers, spiders, slimes, phantoms, drowned,",
                         "witches, zombie sieges at night, and other mods' monsters.")
                .define("minecraftMonsters", false);

            raidsAndPatrols = builder
                .comment("Pillager patrols, and raids on villages (off: nobody gets the Bad Omen, so no raid starts).")
                .define("raidsAndPatrols", false);

            wanderingTraders = builder
                .comment("The wandering trader and his llamas.")
                .define("wanderingTraders", false);

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

        // Remote brain gateway for multiplayer servers
        public final ForgeConfigSpec.BooleanValue remoteBrainEnabled;
        public final ForgeConfigSpec.ConfigValue<String> liteLlmEndpoint;
        public final ForgeConfigSpec.ConfigValue<String> liteLlmModel;
        public final ForgeConfigSpec.ConfigValue<String> liteLlmToken;
        public final ForgeConfigSpec.ConfigValue<String> liteLlmExtraHeaders;
        public final ForgeConfigSpec.ConfigValue<String> portkeyEndpoint;
        public final ForgeConfigSpec.ConfigValue<String> portkeyModel;
        public final ForgeConfigSpec.ConfigValue<String> portkeyProviderToken;
        public final ForgeConfigSpec.ConfigValue<String> portkeyApiKey;
        public final ForgeConfigSpec.ConfigValue<String> portkeyProvider;
        public final ForgeConfigSpec.ConfigValue<String> portkeyConfig;
        public final ForgeConfigSpec.IntValue remoteBrainTimeoutMs;

        // Local developer web console
        public final ForgeConfigSpec.BooleanValue webConsoleEnabled;
        public final ForgeConfigSpec.IntValue webConsolePort;
        public final ForgeConfigSpec.ConfigValue<String> webConsoleToken;
        
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

            builder.comment("Remote advisory brain gateway for multiplayer servers. This is server-side only and advisory-only; the Minecraft server remains authoritative.")
                   .push("brainGateway");

            remoteBrainEnabled = builder
                .comment("Use remote HTTPS brain gateways. Eternal Engine does not use local LLM processes. Can also be set with env EE_BRAIN_ENABLED or JVM -Dee.brain.enabled=true.")
                .define("remoteBrainEnabled", false);

            liteLlmEndpoint = builder
                .comment("PRIMARY brain source: LiteLLM/OpenAI-compatible HTTPS endpoint, usually https://your-litellm/v1/chat/completions. Can also be set with env EE_LITELLM_ENDPOINT or JVM -Dee.brain.litellm.endpoint=...")
                .define("liteLlmEndpoint", "");

            liteLlmModel = builder
                .comment("Primary LiteLLM model name")
                .define("liteLlmModel", "default");

            liteLlmToken = builder
                .comment("Primary LiteLLM virtual key / bearer token. Server-side only; never sent to clients.")
                .define("liteLlmToken", "");

            liteLlmExtraHeaders = builder
                .comment("Optional extra headers for LiteLLM. Format: name=value;name2=value2")
                .define("liteLlmExtraHeaders", "");

            portkeyEndpoint = builder
                .comment("SECONDARY fallback source: Portkey HTTPS endpoint")
                .define("portkeyEndpoint", "https://api.portkey.ai/v1/chat/completions");

            portkeyModel = builder
                .comment("Secondary Portkey model name")
                .define("portkeyModel", "default");

            portkeyProviderToken = builder
                .comment("Provider token forwarded in Authorization: Bearer <token> when using Portkey. Server-side only.")
                .define("portkeyProviderToken", "");

            portkeyApiKey = builder
                .comment("Portkey API key sent as x-portkey-api-key. Leave blank to disable Portkey fallback. Can also be set with env EE_PORTKEY_API_KEY or JVM -Dee.brain.portkey.apiKey=...")
                .define("portkeyApiKey", "");

            portkeyProvider = builder
                .comment("Portkey provider header, e.g. openai, anthropic, google, groq")
                .define("portkeyProvider", "openai");

            portkeyConfig = builder
                .comment("Optional Portkey config id or JSON sent as x-portkey-config")
                .define("portkeyConfig", "");

            remoteBrainTimeoutMs = builder
                .comment("Remote brain request timeout in milliseconds")
                .defineInRange("remoteBrainTimeoutMs", 10000, 1000, 60000);

            builder.pop();

            builder.comment("Localhost-only developer web chat. Advisory/read-only; uses the same server-side brain bridge as Minecraft.")
                   .push("webConsole");

            webConsoleEnabled = builder
                .comment("Start the localhost web console automatically on server start")
                .define("webConsoleEnabled", false);

            webConsolePort = builder
                .comment("Localhost port for the web console")
                .defineInRange("webConsolePort", 8765, 1024, 65535);

            webConsoleToken = builder
                .comment("Access token for the web console. Leave blank to generate one per server run.")
                .define("webConsoleToken", "");

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
