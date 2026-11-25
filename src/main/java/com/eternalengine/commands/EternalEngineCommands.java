package com.eternalengine.commands;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.climate.ClimateEvent;
import com.eternalengine.climate.ClimatePeriods;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.AsyncTaskManager;
import com.eternalengine.core.PerformanceMonitor;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.anomaly.AnomalyDetectionEngine;
import com.eternalengine.anomaly.AnomalyScanResult;
import com.eternalengine.anomaly.BuildingAnomalyDetector;
import com.eternalengine.affairs.AffairsScope;
import com.eternalengine.affairs.BotAffairsRole;
import com.eternalengine.agriculture.FoodData;
import com.eternalengine.affairs.CurrentAffairsBriefing;
import com.eternalengine.affairs.CurrentAffairsEngine;
import com.eternalengine.bot.BotIdentity;
import com.eternalengine.brain.LlmProposal;
import com.eternalengine.buildings.BuildingRecord;
import com.eternalengine.buildings.BuildingStateChange;
import com.eternalengine.buildings.BuildingDateInferenceEngine;
import com.eternalengine.buildings.BuildingStyle;
import com.eternalengine.buildings.CivilizationGrammarEngine;
import com.eternalengine.buildings.EraBuildingPlanner;
import com.eternalengine.buildings.HistoricalLandmarkGenerator;
import com.eternalengine.buildings.PlannedBuilding;
import com.eternalengine.buildings.TemporalMorphController;
import com.eternalengine.carto.CartographicOverlayManager;
import com.eternalengine.carto.HeraldryRegistry;
import com.eternalengine.carto.HistoricalAudioDirector;
import com.eternalengine.chrononexus.ChronoEnding;
import com.eternalengine.chrononexus.ChronoGameMode;
import com.eternalengine.chrononexus.ChronoNexusCampaignEngine;
import com.eternalengine.chrononexus.ChronoNexusCampaignPhase;
import com.eternalengine.brain.LlmValidationContext;
import com.eternalengine.brain.LlmValidationResult;
import com.eternalengine.civilization.Civilization;
import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.civilization.RelationshipType;
import com.eternalengine.civilization.TerritoryManager;
import com.eternalengine.data.EternalDataPaths;
import com.eternalengine.data.FoundationDataInstaller;
import com.eternalengine.data.FoundationDataLoader;
import com.eternalengine.disease.ContagionTransmissionBrain;
import com.eternalengine.disease.DiseaseOutbreak;
import com.eternalengine.disease.EpidemicScale;
import com.eternalengine.disease.HistoricalDisasterEngine;
import com.eternalengine.disease.MedicineTier;
import com.eternalengine.disease.PlagueDoctorHospitalManager;
import com.eternalengine.disease.QuarantineCheckpointController;
import com.eternalengine.ecs.AgentVirtualizationEngine;
import com.eternalengine.ecs.StructOfArraysPopulationEcs;
import com.eternalengine.history.HistoricalEvent;
import com.eternalengine.incident.IncidentContext;
import com.eternalengine.incident.IncidentRecord;
import com.eternalengine.population.MigrationFlow;
import com.eternalengine.population.PopulationResolver;
import com.eternalengine.player.DivergenceDetector;
import com.eternalengine.player.PlayerAction;
import com.eternalengine.player.PlayerActionDetector;
import com.eternalengine.population.PopulationData;
import com.eternalengine.religion.Religion;
import com.eternalengine.reputation.ReputationEffect;
import com.eternalengine.sovereign.DiplomaticTreatyEngine;
import com.eternalengine.sovereign.DynasticSuccessionEngine;
import com.eternalengine.sovereign.ImperialCommandEngine;
import com.eternalengine.sovereign.RoyalCourtManager;
import com.eternalengine.technology.Technology;
import com.eternalengine.telemetry.ChunkTelemetryScanner;
import com.eternalengine.player.Permissions;
import com.eternalengine.timeline.ChronosCodexData;
import com.eternalengine.timeline.FixedPointRegister;
import com.eternalengine.timeline.FixedPoints;
import com.eternalengine.timeline.ParadoxEngine;
import com.eternalengine.timeline.Repairs;
import com.eternalengine.timeline.DivergenceCascadeEngine;
import com.eternalengine.timeline.RealityRiftController;
import com.eternalengine.timeline.TimelineBranch;
import com.eternalengine.timeline.TimelineSeed;
import com.eternalengine.trade.CaravanEntityGroup;
import com.eternalengine.trade.LogisticsManager;
import com.eternalengine.trade.MerchantShipConvoy;
import com.eternalengine.trade.TradeRoute;
import com.eternalengine.trade.TransportType;
import com.eternalengine.vehicle.VehicleEra;
import com.eternalengine.vehicle.VehicleRoute;
import com.eternalengine.vehicle.VehicleRouteReport;
import com.eternalengine.vehicle.VehicleType;
import com.eternalengine.vip.VipBotRecord;
import com.eternalengine.vip.VipEntityHelper;
import com.eternalengine.vip.VipSlot;
import com.eternalengine.vip.VipTakeoverContext;
import com.eternalengine.vip.VipTakeoverMethod;
import com.eternalengine.vip.VipTakeoverResult;
import com.eternalengine.web.WebConsoleService;
import com.eternalengine.villager.VirtualVillager;
import com.eternalengine.war.Army;
import com.eternalengine.war.FormationType;
import com.eternalengine.war.RegimentCohort;
import com.eternalengine.war.War;
import com.eternalengine.telemetry.WorldDebugSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;

/**
 * All Eternal Engine commands.
 * 
 * Permission Levels:
 * - All players: status, year, help, building, rep, history, lists
 * - VIP players: vip claim/release, observer, speed vote, override
 * - Admin (OP 2+): speed set, year set, event control, debug, reset
 */
public class EternalEngineCommands {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ee")
                // ========== ALL PLAYERS ==========
                .then(Commands.literal("status")
                    .executes(ctx -> executeStatus(ctx.getSource())))
                
                .then(Commands.literal("year")
                    .executes(ctx -> executeYear(ctx.getSource()))
                    .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("year", IntegerArgumentType.integer(-300000, 3000))
                            .executes(ctx -> executeYearSet(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "year"))))))
                
                .then(Commands.literal("help")
                    .executes(ctx -> executeHelp(ctx.getSource())))
                
                // /ee check all — is the simulation actually alive and wired on THIS server, and does the
                // world-history data give the answers checked by hand? Every check at once, no single
                // checks (/ee check alone does the same). Different from /ee test, which only fires
                // every command and reports whether it threw. See EeOperationalCheck.node().
                .then(EeOperationalCheck.node())

                // Read-only 2026 OSM sample projected by the active Terra++ world; no block writes.
                .then(com.eternalengine.hyderabad.HyderabadPilotCommand.node())
                
                .then(Commands.literal("test")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> EeCommandSelfTest.execute(ctx.getSource(), "help"))
                    .then(Commands.literal("all")
                        .executes(ctx -> EeCommandSelfTest.execute(ctx.getSource(), "all"))
                        .then(Commands.literal("verbose")
                            .executes(ctx -> EeCommandSelfTest.execute(ctx.getSource(), "all-verbose"))))
                    .then(Commands.literal("everything")
                        .executes(ctx -> com.eternalengine.testing.RuntimeSuite.executeEverything(ctx.getSource())))
                    .then(Commands.literal("movement")
                        .executes(ctx -> com.eternalengine.testing.RuntimeSuite.executeMovement(ctx.getSource())))
                    .then(Commands.literal("list")
                        .executes(ctx -> EeCommandSelfTest.execute(ctx.getSource(), "list")))
                    .then(Commands.literal("part")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1, 20))
                            .executes(ctx -> EeCommandSelfTest.executePart(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "number")))))
                    // board 18: one command for the whole stop procedure -- clock, year, jump, hold the
                    // ground open until it exists, then the card, probe, people, buildings, civilisation.
                    .then(EeStopTest.hereNode())
                    .then(EeStopTest.stopNode())
                    .then(EeStopTest.listNode()))
                
                .then(Commands.literal("rep")
                    .executes(ctx -> executeRep(ctx.getSource()))
                    .then(Commands.literal("apply")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("action", StringArgumentType.word())
                            .executes(ctx -> executeRepApply(ctx.getSource(),
                                StringArgumentType.getString(ctx, "action"))))))

                .then(Commands.literal("probe")
                    .executes(ctx -> executeProbeHere(ctx.getSource()))
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(ctx -> executeProbe(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "x"),
                                IntegerArgumentType.getInteger(ctx, "z"))))))
                
                .then(Commands.literal("here")
                    .executes(ctx -> executeHere(ctx.getSource())))
                
                .then(Commands.literal("scan")
                    .then(Commands.literal("chunk")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeScanChunk(ctx.getSource(), 4))
                        .then(Commands.argument("step", IntegerArgumentType.integer(1, 16))
                            .executes(ctx -> executeScanChunk(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "step"))))))

                .then(Commands.literal("anomaly")
                    .executes(ctx -> executeAnomalyProbe(ctx.getSource()))
                    .then(Commands.literal("probe")
                        .executes(ctx -> executeAnomalyProbe(ctx.getSource())))
                    .then(Commands.literal("chunk")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeAnomalyChunk(ctx.getSource(), 4))
                        .then(Commands.argument("step", IntegerArgumentType.integer(1, 16))
                            .executes(ctx -> executeAnomalyChunk(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "step"))))))

                .then(Commands.literal("affairs")
                    .executes(ctx -> executeAffairs(ctx.getSource(), AffairsScope.LOCAL, BotAffairsRole.VILLAGER))
                    .then(Commands.literal("global")
                        .executes(ctx -> executeAffairs(ctx.getSource(), AffairsScope.GLOBAL, BotAffairsRole.HISTORIAN)))
                    .then(Commands.literal("war")
                        .executes(ctx -> executeAffairs(ctx.getSource(), AffairsScope.WAR, BotAffairsRole.SOLDIER)))
                    .then(Commands.literal("disease")
                        .executes(ctx -> executeAffairs(ctx.getSource(), AffairsScope.DISEASE, BotAffairsRole.HEALER)))
                    .then(Commands.literal("timeline")
                        .executes(ctx -> executeAffairs(ctx.getSource(), AffairsScope.TIMELINE, BotAffairsRole.HISTORIAN)))
                    .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeAffairs(ctx.getSource(), AffairsScope.DEBUG, BotAffairsRole.ADVISOR))))

                .then(Commands.literal("incident")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("test")
                        .executes(ctx -> executeIncidentTest(ctx.getSource()))))

                .then(Commands.literal("vehicle")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("test")
                        .executes(ctx -> executeVehicleTest(ctx.getSource()))))

                .then(Commands.literal("brain")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("availability")
                        .executes(ctx -> executeBrainAvailability(ctx.getSource())))
                    .then(Commands.literal("remote-start")
                        .executes(ctx -> executeBrainRemoteStart(ctx.getSource())))
                    .then(Commands.literal("config")
                        .executes(ctx -> executeBrainConfig(ctx.getSource())))
                    .then(Commands.literal("queue")
                        .executes(ctx -> executeBrainQueue(ctx.getSource())))
                    .then(Commands.literal("audit")
                        .executes(ctx -> executeBrainAudit(ctx.getSource())))
                    .then(Commands.literal("test")
                        .executes(ctx -> executeBrainTest(ctx.getSource())))
                    .then(Commands.literal("affairs-propose")
                        .executes(ctx -> executeBrainAffairsPropose(ctx.getSource())))
                    .then(Commands.literal("anomaly-propose")
                        .executes(ctx -> executeBrainAnomalyPropose(ctx.getSource()))))

                .then(Commands.literal("web")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("status")
                        .executes(ctx -> executeWebStatus(ctx.getSource())))
                    .then(Commands.literal("start")
                        .executes(ctx -> executeWebStart(ctx.getSource())))
                    .then(Commands.literal("stop")
                        .executes(ctx -> executeWebStop(ctx.getSource())))
                    .then(Commands.literal("url")
                        .executes(ctx -> executeWebUrl(ctx.getSource()))))

                .then(Commands.literal("bot")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("connect")
                        .then(Commands.argument("botId", StringArgumentType.word())
                            .executes(ctx -> executeBotConnect(ctx.getSource(),
                                StringArgumentType.getString(ctx, "botId")))))
                    .then(Commands.literal("briefing")
                        .then(Commands.argument("botId", StringArgumentType.word())
                            .then(Commands.argument("role", StringArgumentType.word())
                                .executes(ctx -> executeBotBriefing(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "botId"),
                                    StringArgumentType.getString(ctx, "role"))))))
                    .then(Commands.literal("proposals")
                        .then(Commands.argument("botId", StringArgumentType.word())
                            .executes(ctx -> executeBotProposals(ctx.getSource(),
                                StringArgumentType.getString(ctx, "botId")))))
                    .then(Commands.literal("proposal-test")
                        .then(Commands.argument("botId", StringArgumentType.word())
                            .then(Commands.argument("action", StringArgumentType.word())
                                .executes(ctx -> executeBotProposalTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "botId"),
                                    StringArgumentType.getString(ctx, "action")))))))
                
                .then(Commands.literal("blockqueue")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("status")
                        .executes(ctx -> executeBlockQueueStatus(ctx.getSource())))
                    .then(Commands.literal("clear")
                        .executes(ctx -> executeBlockQueueClear(ctx.getSource()))))

                .then(Commands.literal("building")
                    .executes(ctx -> executeBuilding(ctx.getSource()))
                    .then(Commands.literal("history")
                        .executes(ctx -> executeBuildingHistory(ctx.getSource())))
                    .then(Commands.literal("add-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBuildingAddTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "name"))))))
                    .then(Commands.literal("nearby")
                        .then(Commands.argument("radiusMeters", IntegerArgumentType.integer(1, 10000))
                            .executes(ctx -> executeBuildingNearby(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radiusMeters")))))
                    .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeBuildingInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("infer")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeBuildingInfer(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("damage")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("percent", IntegerArgumentType.integer(-100, 100))
                                .then(Commands.argument("cause", StringArgumentType.greedyString())
                                    .executes(ctx -> executeBuildingDamage(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "percent"),
                                        StringArgumentType.getString(ctx, "cause")))))))
                    .then(Commands.literal("anomalies")
                        .then(Commands.argument("radiusMeters", IntegerArgumentType.integer(1, 10000))
                            .executes(ctx -> executeBuildingAnomalies(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radiusMeters")))))
                    .then(Commands.literal("plan-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> executeBuildingPlanTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "type")))))))

                .then(Commands.literal("morph")
                    .then(Commands.literal("status")
                        .executes(ctx -> executeMorphStatus(ctx.getSource())))
                    .then(Commands.literal("jump")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("year", IntegerArgumentType.integer(-3000, 3000))
                            .executes(ctx -> executeMorphJump(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "year")))))
                    .then(Commands.literal("landmark")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                            .executes(ctx -> executeMorphLandmark(ctx.getSource(),
                                StringArgumentType.getString(ctx, "type")))))
                    .then(Commands.literal("grammar")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("culture", StringArgumentType.word())
                            .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> executeMorphGrammar(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "culture"),
                                    StringArgumentType.getString(ctx, "type")))))))
                
                .then(Commands.literal("history")
                    .then(Commands.argument("year", IntegerArgumentType.integer(-300000, 3000))
                        .executes(ctx -> executeHistory(ctx.getSource(), 
                            IntegerArgumentType.getInteger(ctx, "year")))))
                
                .then(Commands.literal("timeline")
                    .executes(ctx -> executeTimeline(ctx.getSource()))
                    .then(Commands.literal("branch")
                        .executes(ctx -> executeTimelineBranch(ctx.getSource())))
                    .then(Commands.literal("divergences")
                        .executes(ctx -> executeTimelineDivergences(ctx.getSource())))
                    // FINAL_TEST G5 (marking board 14): the fixed-point register had no door at all.
                    .then(Commands.literal("fixed")
                        .executes(ctx -> executeTimelineFixed(ctx.getSource(), null))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeTimelineFixed(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("force")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("why", StringArgumentType.greedyString())
                                .executes(ctx -> executeTimelineForce(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "why"))))))
                    .then(Commands.literal("rebuild")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("why", StringArgumentType.greedyString())
                                .executes(ctx -> executeTimelineRebuild(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "why"))))))
                    .then(Commands.literal("roll")
                        .then(Commands.argument("eventKey", StringArgumentType.word())
                            .executes(ctx -> executeTimelineRoll(ctx.getSource(),
                                StringArgumentType.getString(ctx, "eventKey")))))
                    .then(Commands.literal("diverge")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("eventId", StringArgumentType.word())
                            .then(Commands.argument("cause", StringArgumentType.greedyString())
                                .executes(ctx -> executeTimelineDiverge(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "eventId"),
                                    StringArgumentType.getString(ctx, "cause"))))))
                    .then(Commands.literal("cascade")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("eventId", StringArgumentType.word())
                            .then(Commands.argument("cause", StringArgumentType.greedyString())
                                .executes(ctx -> executeTimelineCascade(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "eventId"),
                                    StringArgumentType.getString(ctx, "cause"))))))
                    .then(Commands.literal("rift")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeTimelineRift(ctx.getSource(), "pivot_anomaly"))
                        .then(Commands.argument("eventId", StringArgumentType.word())
                            .executes(ctx -> executeTimelineRift(ctx.getSource(),
                                StringArgumentType.getString(ctx, "eventId")))))
                    .then(Commands.literal("rifts")
                        .executes(ctx -> executeTimelineRifts(ctx.getSource()))))

                .then(Commands.literal("codex")
                    .executes(ctx -> executeCodex(ctx.getSource(), "tree"))
                    .then(Commands.argument("tab", StringArgumentType.word())
                        .executes(ctx -> executeCodex(ctx.getSource(),
                            StringArgumentType.getString(ctx, "tab")))))

                .then(Commands.literal("sovereign")
                    .then(Commands.literal("court")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .executes(ctx -> executeSovereignCourt(ctx.getSource(),
                                StringArgumentType.getString(ctx, "civId")))))
                    .then(Commands.literal("petition")
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("petition", StringArgumentType.word())
                                .executes(ctx -> executeSovereignPetition(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    StringArgumentType.getString(ctx, "petition"))))))
                    .then(Commands.literal("treaty")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civA", StringArgumentType.word())
                            .then(Commands.argument("civB", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                    .executes(ctx -> executeSovereignTreaty(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "civA"),
                                        StringArgumentType.getString(ctx, "civB"),
                                        StringArgumentType.getString(ctx, "type")))))))
                    .then(Commands.literal("megaproject")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                            .executes(ctx -> executeSovereignMegaproject(ctx.getSource(),
                                StringArgumentType.getString(ctx, "type")))))
                    .then(Commands.literal("doctrine")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("doctrine", StringArgumentType.word())
                                .executes(ctx -> executeSovereignDoctrine(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    StringArgumentType.getString(ctx, "doctrine"))))))
                    .then(Commands.literal("tax")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("rate", IntegerArgumentType.integer(5, 50))
                                .executes(ctx -> executeSovereignTax(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    IntegerArgumentType.getInteger(ctx, "rate"))))))
                    .then(Commands.literal("debase")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .executes(ctx -> executeSovereignDebase(ctx.getSource(),
                                StringArgumentType.getString(ctx, "civId")))))
                    .then(Commands.literal("coup")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("pretender", StringArgumentType.word())
                                .executes(ctx -> executeSovereignCoup(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    StringArgumentType.getString(ctx, "pretender")))))))

                .then(Commands.literal("ecs")
                    .then(Commands.literal("status")
                        .executes(ctx -> executeEcsStatus(ctx.getSource())))
                    .then(Commands.literal("populate")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> executeEcsPopulate(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    IntegerArgumentType.getInteger(ctx, "count"))))))
                    .then(Commands.literal("benchmark")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeEcsBenchmark(ctx.getSource(), 100000))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1000, 200000))
                            .executes(ctx -> executeEcsBenchmark(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "count")))))
                    .then(Commands.literal("flush")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeEcsFlush(ctx.getSource()))))

                .then(Commands.literal("map")
                    .then(Commands.literal("sync")
                        .executes(ctx -> executeMapSync(ctx.getSource()))))

                .then(Commands.literal("audio")
                    .then(Commands.literal("atmosphere")
                        .executes(ctx -> executeAudioAtmosphere(ctx.getSource()))))

                .then(Commands.literal("heraldry")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeHeraldryList(ctx.getSource())))
                    .then(Commands.literal("give")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .executes(ctx -> executeHeraldryGive(ctx.getSource(),
                                StringArgumentType.getString(ctx, "civId"))))))

                .then(Commands.literal("chrono")
                    .then(Commands.literal("status")
                        .executes(ctx -> executeChronoStatus(ctx.getSource())))
                    .then(Commands.literal("start")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeChronoStart(ctx.getSource())))
                    .then(Commands.literal("mode")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .executes(ctx -> executeChronoMode(ctx.getSource(),
                                StringArgumentType.getString(ctx, "mode")))))
                    .then(Commands.literal("phase")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("number", IntegerArgumentType.integer(1, 7))
                            .executes(ctx -> executeChronoPhase(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "number")))))
                    .then(Commands.literal("advance")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeChronoAdvance(ctx.getSource())))
                    .then(Commands.literal("rival")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeChronoRivalSpawn(ctx.getSource()))
                        .then(Commands.literal("spawn")
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> executeChronoRivalSpawn(ctx.getSource()))))
                    .then(Commands.literal("paradox")
                        .then(Commands.literal("start")
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> executeChronoParadoxStart(ctx.getSource(), 900))
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 3600))
                                .executes(ctx -> executeChronoParadoxStart(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("deliver")
                            .executes(ctx -> executeChronoParadoxDeliver(ctx.getSource()))))
                    .then(Commands.literal("ending")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                            .executes(ctx -> executeChronoEnding(ctx.getSource(),
                                StringArgumentType.getString(ctx, "type"))))))
                
                .then(Commands.literal("event")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeEventList(ctx.getSource())))
                    .then(Commands.literal("pending")
                        .executes(ctx -> executeEventPending(ctx.getSource())))
                    .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeEventInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("trigger")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeEventTrigger(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("prevent")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("cause", StringArgumentType.greedyString())
                                .executes(ctx -> executeEventPrevent(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "cause"))))))
                    .then(Commands.literal("load")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeEventLoad(ctx.getSource()))))

                .then(Commands.literal("data")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("paths")
                        .executes(ctx -> executeDataPaths(ctx.getSource())))
                    .then(Commands.literal("install")
                        .executes(ctx -> executeDataInstall(ctx.getSource(), false)))
                    .then(Commands.literal("install-overwrite")
                        .executes(ctx -> executeDataInstall(ctx.getSource(), true)))
                    .then(Commands.literal("load")
                        .executes(ctx -> executeDataLoad(ctx.getSource())))
                    .then(Commands.literal("status")
                        .executes(ctx -> executeDataStatus(ctx.getSource()))))
                
                .then(Commands.literal("disease")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeDiseaseList(ctx.getSource())))
                    .then(Commands.literal("info")
                        .then(Commands.argument("outbreakId", StringArgumentType.word())
                            .executes(ctx -> executeDiseaseInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "outbreakId")))))
                    .then(Commands.literal("trigger")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("diseaseId", StringArgumentType.word())
                            .then(Commands.argument("regionId", StringArgumentType.word())
                                .executes(ctx -> executeDiseaseTrigger(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "diseaseId"),
                                    StringArgumentType.getString(ctx, "regionId"))))))
                    .then(Commands.literal("quarantine")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .executes(ctx -> executeDiseaseQuarantine(ctx.getSource(),
                                StringArgumentType.getString(ctx, "regionId")))))
                    .then(Commands.literal("cordon")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .executes(ctx -> executeDiseaseCordon(ctx.getSource(),
                                StringArgumentType.getString(ctx, "regionId")))))
                    .then(Commands.literal("doctor-test")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeDiseaseDoctorTest(ctx.getSource())))
                    .then(Commands.literal("hospital-test")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeDiseaseHospitalTest(ctx.getSource())))
                    .then(Commands.literal("infect-test")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeDiseaseInfectTest(ctx.getSource(), "black_death"))
                        .then(Commands.argument("diseaseId", StringArgumentType.word())
                            .executes(ctx -> executeDiseaseInfectTest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "diseaseId")))))
                    .then(Commands.literal("status")
                        .executes(ctx -> executeDiseaseStatus(ctx.getSource()))))

                .then(Commands.literal("disaster")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("type", StringArgumentType.word())
                        .executes(ctx -> executeDisasterTrigger(ctx.getSource(),
                            StringArgumentType.getString(ctx, "type")))))
                
                .then(Commands.literal("war")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeWarList(ctx.getSource())))
                    .then(Commands.literal("info")
                        .then(Commands.argument("warId", StringArgumentType.word())
                            .executes(ctx -> executeWarInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "warId")))))
                    .then(Commands.literal("declare")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("attacker", StringArgumentType.word())
                            .then(Commands.argument("defender", StringArgumentType.word())
                                .then(Commands.argument("cause", StringArgumentType.greedyString())
                                    .executes(ctx -> executeWarDeclare(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "attacker"),
                                        StringArgumentType.getString(ctx, "defender"),
                                        StringArgumentType.getString(ctx, "cause")))))))
                    .then(Commands.literal("end")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("warId", StringArgumentType.word())
                            .executes(ctx -> executeWarEnd(ctx.getSource(),
                                StringArgumentType.getString(ctx, "warId")))))
                    .then(Commands.literal("army")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("test")
                            .executes(ctx -> executeArmyTest(ctx.getSource()))))
                    .then(Commands.literal("deploy")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("armyId", StringArgumentType.word())
                            .executes(ctx -> executeWarDeploy(ctx.getSource(),
                                StringArgumentType.getString(ctx, "armyId"), "LINE", 12))
                            .then(Commands.argument("formation", StringArgumentType.word())
                                .executes(ctx -> executeWarDeploy(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "armyId"),
                                    StringArgumentType.getString(ctx, "formation"), 12))
                                .then(Commands.argument("count", IntegerArgumentType.integer(3, 64))
                                    .executes(ctx -> executeWarDeploy(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "armyId"),
                                        StringArgumentType.getString(ctx, "formation"),
                                        IntegerArgumentType.getInteger(ctx, "count")))))))
                    .then(Commands.literal("battle-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civ1", StringArgumentType.word())
                            .then(Commands.argument("civ2", StringArgumentType.word())
                                .executes(ctx -> executeWarBattleTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civ1"),
                                    StringArgumentType.getString(ctx, "civ2"))))))
                    .then(Commands.literal("siege-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("attacker", StringArgumentType.word())
                            .then(Commands.argument("defender", StringArgumentType.word())
                                .executes(ctx -> executeWarSiegeTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "attacker"),
                                    StringArgumentType.getString(ctx, "defender"))))))
                    .then(Commands.literal("cohorts")
                        .executes(ctx -> executeWarCohorts(ctx.getSource()))))

                .then(Commands.literal("trade")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeTradeList(ctx.getSource())))
                    .then(Commands.literal("info")
                        .then(Commands.argument("routeId", StringArgumentType.word())
                            .executes(ctx -> executeTradeInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "routeId")))))
                    .then(Commands.literal("open")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("origin", StringArgumentType.word())
                            .then(Commands.argument("dest", StringArgumentType.word())
                                .then(Commands.argument("transport", StringArgumentType.word())
                                    .executes(ctx -> executeTradeOpen(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "origin"),
                                        StringArgumentType.getString(ctx, "dest"),
                                        StringArgumentType.getString(ctx, "transport")))))))
                    .then(Commands.literal("close")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("routeId", StringArgumentType.word())
                            .executes(ctx -> executeTradeClose(ctx.getSource(),
                                StringArgumentType.getString(ctx, "routeId")))))
                    .then(Commands.literal("disrupt")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("routeId", StringArgumentType.word())
                            .then(Commands.argument("cause", StringArgumentType.greedyString())
                                .executes(ctx -> executeTradeDisrupt(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "routeId"),
                                    StringArgumentType.getString(ctx, "cause"))))))
                    .then(Commands.literal("caravan")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("routeId", StringArgumentType.word())
                            .executes(ctx -> executeTradeCaravan(ctx.getSource(),
                                StringArgumentType.getString(ctx, "routeId")))))
                    .then(Commands.literal("ambush-test")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeTradeAmbushTest(ctx.getSource())))
                    .then(Commands.literal("ship-test")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeTradeShipTest(ctx.getSource(), "naval_demo")))
                    .then(Commands.literal("logistics")
                        .executes(ctx -> executeTradeLogistics(ctx.getSource())))
                    .then(Commands.literal("prices")
                        .executes(ctx -> executeTradePrices(ctx.getSource(), "rome"))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .executes(ctx -> executeTradePrices(ctx.getSource(),
                                StringArgumentType.getString(ctx, "civId"))))))

                .then(Commands.literal("economy")
                    .then(Commands.literal("treasury")
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .executes(ctx -> executeEconomyTreasury(ctx.getSource(),
                                StringArgumentType.getString(ctx, "civId")))))
                    .then(Commands.literal("add")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeEconomyAdd(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    IntegerArgumentType.getInteger(ctx, "amount"))))))
                    .then(Commands.literal("subtract")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeEconomySubtract(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    IntegerArgumentType.getInteger(ctx, "amount")))))))

                .then(Commands.literal("agriculture")
                    .then(Commands.literal("food")
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .executes(ctx -> executeAgricultureFood(ctx.getSource(),
                                StringArgumentType.getString(ctx, "regionId")))))
                    .then(Commands.literal("boost")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .executes(ctx -> executeAgricultureBoost(ctx.getSource(),
                                StringArgumentType.getString(ctx, "regionId")))))
                    .then(Commands.literal("climate")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .then(Commands.argument("modifierPercent", IntegerArgumentType.integer(0, 300))
                                .executes(ctx -> executeAgricultureClimate(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "regionId"),
                                    IntegerArgumentType.getInteger(ctx, "modifierPercent")))))))

                .then(Commands.literal("climate")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeClimateList(ctx.getSource())))
                    .then(Commands.literal("active")
                        .executes(ctx -> executeClimateActive(ctx.getSource())))
                    .then(Commands.literal("region")
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .executes(ctx -> executeClimateRegion(ctx.getSource(),
                                StringArgumentType.getString(ctx, "regionId")))))
                    .then(Commands.literal("temp")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .then(Commands.argument("modifierTimes100", IntegerArgumentType.integer(-5000, 5000))
                                .executes(ctx -> executeClimateTemp(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "regionId"),
                                    IntegerArgumentType.getInteger(ctx, "modifierTimes100") / 100.0d)))))
                    .then(Commands.literal("precip")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .then(Commands.argument("modifierTimes100", IntegerArgumentType.integer(-10000, 10000))
                                .executes(ctx -> executeClimatePrecip(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "regionId"),
                                    IntegerArgumentType.getInteger(ctx, "modifierTimes100") / 100.0d))))))

                .then(Commands.literal("famine")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeFamineList(ctx.getSource())))
                    .then(Commands.literal("trigger")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .executes(ctx -> executeFamineTrigger(ctx.getSource(),
                                StringArgumentType.getString(ctx, "regionId")))))
                    .then(Commands.literal("end")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .executes(ctx -> executeFamineEnd(ctx.getSource(),
                                StringArgumentType.getString(ctx, "regionId"))))))

                .then(Commands.literal("population")
                    .then(Commands.literal("explain")
                        .executes(ctx -> executePopulationExplain(ctx.getSource())))
                    .then(Commands.literal("at")
                        .then(Commands.argument("lat", DoubleArgumentType.doubleArg(-90.0d, 90.0d))
                            .then(Commands.argument("lon", DoubleArgumentType.doubleArg(-180.0d, 180.0d))
                                .executes(ctx -> executePopulationAt(ctx.getSource(),
                                    DoubleArgumentType.getDouble(ctx, "lat"),
                                    DoubleArgumentType.getDouble(ctx, "lon"),
                                    com.eternalengine.clock.SimulationClockManager.isInitialized()
                                        ? com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear()
                                        : 0))
                                .then(Commands.argument("year", IntegerArgumentType.integer(-3000, 9999))
                                    .executes(ctx -> executePopulationAt(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "lat"),
                                        DoubleArgumentType.getDouble(ctx, "lon"),
                                        IntegerArgumentType.getInteger(ctx, "year")))))))
                    .then(Commands.literal("get")
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .executes(ctx -> executePopulationGet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "regionId")))))
                    .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("regionId", StringArgumentType.word())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> executePopulationSet(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "regionId"),
                                    IntegerArgumentType.getInteger(ctx, "amount")))))))

                .then(Commands.literal("migration")
                    .then(Commands.literal("process")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeMigrationProcess(ctx.getSource())))
                    .then(Commands.literal("list")
                        .executes(ctx -> executeMigrationList(ctx.getSource()))))

                .then(Commands.literal("class")
                    .then(Commands.literal("tension")
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .executes(ctx -> executeClassTension(ctx.getSource(),
                                StringArgumentType.getString(ctx, "civId"))))))

                .then(Commands.literal("urban")
                    .then(Commands.literal("ratio")
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .executes(ctx -> executeUrbanRatio(ctx.getSource(),
                                StringArgumentType.getString(ctx, "civId"))))))

                .then(Commands.literal("religion")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeReligionList(ctx.getSource())))
                    .then(Commands.literal("convert")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("religionId", StringArgumentType.word())
                                .executes(ctx -> executeReligionConvert(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    StringArgumentType.getString(ctx, "religionId"))))))
                    .then(Commands.literal("holywar")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("targetCivId", StringArgumentType.word())
                                .then(Commands.argument("religionId", StringArgumentType.word())
                                    .executes(ctx -> executeReligionHolyWar(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "civId"),
                                        StringArgumentType.getString(ctx, "targetCivId"),
                                        StringArgumentType.getString(ctx, "religionId")))))))
                    .then(Commands.literal("excommunicate")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("targetCivId", StringArgumentType.word())
                                .executes(ctx -> executeReligionExcommunicate(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    StringArgumentType.getString(ctx, "targetCivId")))))))

                .then(Commands.literal("tech")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeTechList(ctx.getSource())))
                    .then(Commands.literal("status")
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .executes(ctx -> executeTechStatus(ctx.getSource(),
                                StringArgumentType.getString(ctx, "civId")))))
                    .then(Commands.literal("unlock")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("techId", StringArgumentType.word())
                                .executes(ctx -> executeTechUnlock(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    StringArgumentType.getString(ctx, "techId")))))))
                
                .then(Commands.literal("civ")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeCivList(ctx.getSource())))
                    .then(Commands.literal("active")
                        .executes(ctx -> executeCivActive(ctx.getSource())))
                    .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeCivInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("create")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeCivCreate(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "name")))))))

                .then(Commands.literal("relation")
                    .then(Commands.literal("score")
                        .then(Commands.argument("civ1", StringArgumentType.word())
                            .then(Commands.argument("civ2", StringArgumentType.word())
                                .executes(ctx -> executeRelationScore(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civ1"),
                                    StringArgumentType.getString(ctx, "civ2"))))))
                    .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civ1", StringArgumentType.word())
                            .then(Commands.argument("civ2", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                    .executes(ctx -> executeRelationSet(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "civ1"),
                                        StringArgumentType.getString(ctx, "civ2"),
                                        StringArgumentType.getString(ctx, "type"))))))))

                .then(Commands.literal("territory")
                    .then(Commands.literal("owner")
                        .then(Commands.argument("latTimes10000", IntegerArgumentType.integer(-850000, 850000))
                            .then(Commands.argument("lonTimes10000", IntegerArgumentType.integer(-1800000, 1800000))
                                .executes(ctx -> executeTerritoryOwner(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "latTimes10000") / 10000.0d,
                                    IntegerArgumentType.getInteger(ctx, "lonTimes10000") / 10000.0d)))))
                    .then(Commands.literal("create-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("territoryId", StringArgumentType.word())
                            .then(Commands.argument("civId", StringArgumentType.word())
                                .executes(ctx -> executeTerritoryCreateTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "territoryId"),
                                    StringArgumentType.getString(ctx, "civId")))))))
                
                .then(Commands.literal("vip")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeVipList(ctx.getSource())))
                    .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeVipInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    // VIP player commands
                    .then(Commands.literal("claim")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeVipClaim(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("release")
                        .executes(ctx -> executeVipRelease(ctx.getSource())))
                    .then(Commands.literal("takeover-test")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("method", StringArgumentType.word())
                                .executes(ctx -> executeVipTakeoverTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "method"))))))
                    .then(Commands.literal("kill")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("cause", StringArgumentType.greedyString())
                                .executes(ctx -> executeVipKill(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "cause"))))))
                    .then(Commands.literal("successors")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeVipSuccessors(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("tag-self")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeVipTagSelf(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("clear-self-tag")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeVipClearSelfTag(ctx.getSource()))))

                .then(Commands.literal("villager")
                    .then(Commands.literal("nearby")
                        .executes(ctx -> executeVillagerNearby(ctx.getSource(), 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                            .executes(ctx -> executeVillagerNearby(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius")))))
                    .then(Commands.literal("virtual-count")
                        .executes(ctx -> executeVillagerVirtualCount(ctx.getSource())))
                    .then(Commands.literal("explain")
                        .then(Commands.argument("role", StringArgumentType.word())
                            .executes(ctx -> executeVillagerExplain(ctx.getSource(),
                                StringArgumentType.getString(ctx, "role")))))
                    .then(Commands.literal("spawn-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("role", StringArgumentType.word())
                                .executes(ctx -> executeVillagerSpawnTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    StringArgumentType.getString(ctx, "role"))))))
                    .then(Commands.literal("who")
                        .executes(ctx -> executeVillagerWho(ctx.getSource())))
                    .then(Commands.literal("here")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeVillagerHere(ctx.getSource(), "farmer"))
                        .then(Commands.argument("role", StringArgumentType.word())
                            .executes(ctx -> executeVillagerHere(ctx.getSource(),
                                StringArgumentType.getString(ctx, "role")))))
                    .then(Commands.literal("vip-spawn-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("vipId", StringArgumentType.word())
                            .executes(ctx -> executeVillagerVipSpawnTest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "vipId"))))))

                .then(Commands.literal("npc")
                    .then(Commands.literal("nearby")
                        .executes(ctx -> executeVillagerNearby(ctx.getSource(), 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                            .executes(ctx -> executeVillagerNearby(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius")))))
                    .then(Commands.literal("virtual-count")
                        .executes(ctx -> executeVillagerVirtualCount(ctx.getSource())))
                    .then(Commands.literal("explain")
                        .then(Commands.argument("role", StringArgumentType.word())
                            .executes(ctx -> executeVillagerExplain(ctx.getSource(),
                                StringArgumentType.getString(ctx, "role")))))
                    .then(Commands.literal("spawn-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civId", StringArgumentType.word())
                            .then(Commands.argument("role", StringArgumentType.word())
                                .executes(ctx -> executeVillagerSpawnTest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "civId"),
                                    StringArgumentType.getString(ctx, "role"))))))
                    .then(Commands.literal("who")
                        .executes(ctx -> executeVillagerWho(ctx.getSource())))
                    .then(Commands.literal("here")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeVillagerHere(ctx.getSource(), "farmer"))
                        .then(Commands.argument("role", StringArgumentType.word())
                            .executes(ctx -> executeVillagerHere(ctx.getSource(),
                                StringArgumentType.getString(ctx, "role")))))
                    .then(Commands.literal("vip-spawn-test")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("vipId", StringArgumentType.word())
                            .executes(ctx -> executeVillagerVipSpawnTest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "vipId"))))))
                
                .then(Commands.literal("achievements")
                    .executes(ctx -> executeAchievements(ctx.getSource())))

                .then(Commands.literal("actions")
                    .then(Commands.literal("recent")
                        .executes(ctx -> executeActionsRecent(ctx.getSource()))))

                .then(Commands.literal("divergence")
                    .then(Commands.literal("checks")
                        .executes(ctx -> executeDivergenceChecks(ctx.getSource())))
                    .then(Commands.literal("confidence")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                            .executes(ctx -> executeDivergenceConfidence(ctx.getSource(),
                                StringArgumentType.getString(ctx, "eventId")))))
                    .then(Commands.literal("mark")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("eventId", StringArgumentType.word())
                            .then(Commands.argument("confidencePercent", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> executeDivergenceMark(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "eventId"),
                                    IntegerArgumentType.getInteger(ctx, "confidencePercent")))))))
                
                // ========== VIP PLAYERS ==========
                .then(Commands.literal("observer")
                    .executes(ctx -> executeObserver(ctx.getSource())))
                
                .then(Commands.literal("speed")
                    .then(Commands.literal("status")
                        .executes(ctx -> executeSpeedStatus(ctx.getSource())))
                    .then(Commands.literal("vote")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 50))
                            .executes(ctx -> executeSpeedVote(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "value")))))
                    .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 50))
                            .executes(ctx -> executeSpeedSet(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "value"))))))
                
                .then(Commands.literal("override")
                    .executes(ctx -> executeOverride(ctx.getSource())))
                
                .then(Commands.literal("slowdown")
                    .executes(ctx -> executeSlowdown(ctx.getSource())))
                
                // ========== ADMIN ==========
                .then(Commands.literal("debug")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("system", StringArgumentType.word())
                        .executes(ctx -> executeDebug(ctx.getSource(),
                            StringArgumentType.getString(ctx, "system")))))
                
                .then(Commands.literal("performance")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executePerformance(ctx.getSource())))
                
                .then(Commands.literal("reload")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeReload(ctx.getSource())))

                .then(Commands.literal("pause")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executePause(ctx.getSource(), true)))

                .then(Commands.literal("resume")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executePause(ctx.getSource(), false)))

                // Step 18: the game date villagers know (day, month, year), and what a villager would say.
                .then(Commands.literal("date")
                    .executes(ctx -> executeDateShow(ctx.getSource()))
                    .then(Commands.argument("year", IntegerArgumentType.integer(-300000, 3000))
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("month", IntegerArgumentType.integer(1, 12))
                            .then(Commands.argument("day", IntegerArgumentType.integer(1, 31))
                                .executes(ctx -> executeDate(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "year"),
                                    IntegerArgumentType.getInteger(ctx, "month"),
                                    IntegerArgumentType.getInteger(ctx, "day")))))))

                .then(Commands.literal("talk")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeTalk(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeTalk(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 19a: the purse (everyone) and the money of a place and year (admin).
                .then(Commands.literal("purse")
                    .executes(ctx -> executePurse(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executePurse(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                .then(Commands.literal("money")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeMoney(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeMoney(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 19b: what merchants pay at a place and year (admin).
                .then(Commands.literal("market")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeMarket(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeMarket(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 42 (C18, D13): the law at a place and year, and how the people there speak of it.
                .then(Commands.literal("law")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeLaw(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeLaw(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 42 (F3): who keeps the peace here, who judges, and what can be done to you.
                .then(Commands.literal("justice")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeJustice(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeJustice(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 42 (F2): the oaths a player has sworn, and swearing a new one.
                .then(Commands.literal("oath")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeOath(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeOath(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 42 (F8): what the crossing demands of you, and where the line runs.
                .then(Commands.literal("border")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeBorder(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeBorder(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 42 (F2): what a land thinks of you, how far the tale has run, and your oaths.
                .then(Commands.literal("fame")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeFame(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeFame(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 42 (D13, D18): how the people of a place react to you, and how they greet you.
                .then(Commands.literal("standing")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeStanding(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeStanding(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 43 (J3, E13): what is written here, in what script, and what is cut in stone.
                .then(Commands.literal("signs")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeSigns(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeSigns(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 43 (J4): what the sky looks like here - smoke, dust and haze.
                .then(Commands.literal("air")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeAir(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeAir(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 44 (E4): what things cost here and now, measured in silver, and whether
                // this is a dear year. The prices are Allen and Unger's, not invented.
                .then(Commands.literal("prices")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executePrices(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executePrices(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 44 part E (E5): what is short here now, and what people use instead.
                .then(Commands.literal("shortage")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeShortage(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeShortage(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 44 part 8 (E19): what it costs to borrow, and who lends.
                .then(Commands.literal("credit")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeCredit(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeCredit(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 44 part 7 (D16): what a workplace turns out, and what it is short of.
                .then(Commands.literal("workshop")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeWorkshop(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeWorkshop(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 44 part 6 (E6): what is eaten here now.
                .then(Commands.literal("diet")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeDiet(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeDiet(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Step 43 (E13): a letter from here to there, and everything about it.
                .then(Commands.literal("letter")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeLetter(ctx.getSource(), ""))
                    .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> executeLetter(ctx.getSource(), StringArgumentType.getString(ctx, "what")))))

                // Steps 19c-19e: what sellers sell, today's paper and the map at a place (admin and tests).
                .then(NewsCommands.shop())
                .then(NewsCommands.paper())
                .then(NewsCommands.map())
                .then(NewsCommands.products())
                .then(NewsCommands.book())
                
                .then(Commands.literal("backup")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeBackup(ctx.getSource())))
                
                .then(Commands.literal("reset")
                    .requires(src -> src.hasPermission(4)) // Require highest permission
                    .executes(ctx -> executeResetPreview(ctx.getSource()))
                    .then(Commands.literal("confirm")
                        .executes(ctx -> executeReset(ctx.getSource()))))
        );
        
        LOGGER.info("[Commands] Registered /ee commands");
    }
    
    // ========== ALL PLAYER COMMANDS ==========
    
    private static int executeStatus(CommandSourceStack source) {
        if (!SimulationEngine.isInitialized()) {
            source.sendFailure(Component.literal("Eternal Engine not initialized"));
            return 0;
        }
        
        source.sendSuccess(() -> Component.literal(SimulationEngine.getInstance().getStatusSummary()), false);
        return 1;
    }
    
    private static int executeYear(CommandSourceStack source) {
        int year = SimulationClockManager.getInstance().getCurrentYear();
        String yearStr = year < 0 ? Math.abs(year) + " BCE" : year + " CE";
        // The span this year sits in, named the way the source index names it (2026-10-27). The player is told
        // the decade, the century and the millennium, so "the 1940s" and "the 20th century" are answered by the
        // game rather than left to be worked out - and the awkward boundary years are answered correctly:
        // 2000 is the 20th century and the 2nd millennium, 2001 the 21st and the 3rd.
        com.eternalengine.clock.SpanName.Span decade = com.eternalengine.clock.SpanName.decade(year);
        com.eternalengine.clock.SpanName.Span century = com.eternalengine.clock.SpanName.century(year);
        com.eternalengine.clock.SpanName.Span millennium = com.eternalengine.clock.SpanName.millennium(year);
        source.sendSuccess(() -> Component.literal("Current simulation year: " + yearStr), false);
        if (decade != null && century != null && millennium != null) {
            source.sendSuccess(() -> Component.literal("  " + decade.inWords() + ", " + century.inWords()
                    + ", " + millennium.inWords()), false);
            if (decade.ambiguous()) {
                source.sendSuccess(() -> Component.literal("  (\"" + decade.name() + "\" can also name a"
                        + " whole century - said here as a decade: " + decade.years() + ")"), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("  No decade, century or millennium is named for this"
                    + " year: the index stops at 15,000 BC and AD 3000."), false);
        }
        return 1;
    }
    
    private static int executeHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== Eternal Engine Commands ==="), false);
        source.sendSuccess(() -> Component.literal("/ee firststeps - New here? Start with the first steps (done | graduate)"), false);
        source.sendSuccess(() -> Component.literal("/ee status - Simulation status"), false);
        source.sendSuccess(() -> Component.literal("/ee year - Current year"), false);
        source.sendSuccess(() -> Component.literal("/ee rep - Your reputation"), false);
        source.sendSuccess(() -> Component.literal("/ee probe - F3-style Terra++ debug snapshot here"), false);
        source.sendSuccess(() -> Component.literal("/ee here - What the record says of where you stand this year"), false);
        source.sendSuccess(() -> Component.literal("/ee test everything - The full runtime suite; writes the report into the world folder (admin)"), false);
        source.sendSuccess(() -> Component.literal("/ee blockqueue [status|clear] - Block placement queue diagnostics"), false);
        source.sendSuccess(() -> Component.literal("/ee building [history|add-test|nearby|info|infer|damage|anomalies|plan-test] - Building tools"), false);
        source.sendSuccess(() -> Component.literal("/ee scan chunk [step] - Admin chunk telemetry scan"), false);
        source.sendSuccess(() -> Component.literal("/ee anomaly [probe|chunk] - Detect likely world anomalies"), false);
        source.sendSuccess(() -> Component.literal("/ee affairs [global|war|disease|timeline|debug] - Current affairs briefing"), false);
        source.sendSuccess(() -> Component.literal("/ee timeline [branch|divergences|roll|diverge] - Timeline tools"), false);
        source.sendSuccess(() -> Component.literal("/ee event [list|pending|info|trigger|prevent|load] - Historical events"), false);
        source.sendSuccess(() -> Component.literal("/ee data [paths|status|install|install-overwrite|load] - Foundation data tools"), false);
        source.sendSuccess(() -> Component.literal("/ee speed [status|vote|set] - How fast history runs, and which clock (admin: set)"), false);
        source.sendSuccess(() -> Component.literal("/ee pause, /ee resume - Stop and go on with history (admin; kept over a restart)"), false);
        source.sendSuccess(() -> Component.literal("/ee date - Today's game date; /ee date <year> <month> <day> goes to a day and pauses (admin)"), false);
        source.sendSuccess(() -> Component.literal("/ee talk - What the nearest villager would say, as text (admin, for testing; sell and swap really trade)"), false);
        source.sendSuccess(() -> Component.literal("/ee purse - Your money, and the money people use here this year; admins: /ee purse [at <lat> <lon>] [add <amount> [unit] | reset]"), false);
        source.sendSuccess(() -> Component.literal("/ee money [at <lat> <lon> [year]] - Which money a place used in a year, its coins and a day's pay (admin)"), false);
        source.sendSuccess(() -> Component.literal("/ee market [at <lat> <lon> [year]] - What merchants pay there for each good, and the money-changer's cut (admin)"), false);
        source.sendSuccess(() -> Component.literal("/ee reload - Read the config and the world's data files again (admin)"), false);
        source.sendSuccess(() -> Component.literal("/ee performance - Time used by every world system (admin)"), false);
        source.sendSuccess(() -> Component.literal("/ee reset - Start this world's simulation over (owner; asks to confirm)"), false);
        source.sendSuccess(() -> Component.literal("/ee disease [list|info|trigger|quarantine] - Disease tools"), false);
        source.sendSuccess(() -> Component.literal("/ee war [list|info|declare|end|army test] - War tools"), false);
        source.sendSuccess(() -> Component.literal("/ee trade [list|info|open|close|disrupt] - Trade tools"), false);
        source.sendSuccess(() -> Component.literal("/ee economy [treasury|add|subtract] - Economy tools"), false);
        source.sendSuccess(() -> Component.literal("/ee agriculture [food|boost|climate] - Agriculture tools"), false);
        source.sendSuccess(() -> Component.literal("/ee climate [list|active|region|temp|precip] - Climate tools"), false);
        source.sendSuccess(() -> Component.literal("/ee famine [list|trigger|end] - Famine tools"), false);
        source.sendSuccess(() -> Component.literal("/ee population [get|set] - Population tools"), false);
        source.sendSuccess(() -> Component.literal("/ee migration [process|list] - Migration tools"), false);
        source.sendSuccess(() -> Component.literal("/ee class tension <civId> - Class tension"), false);
        source.sendSuccess(() -> Component.literal("/ee urban ratio <civId> - Urbanization ratio"), false);
        source.sendSuccess(() -> Component.literal("/ee religion [list|convert|holywar|excommunicate] - Religion tools"), false);
        source.sendSuccess(() -> Component.literal("/ee tech [list|status|unlock] - Technology tools"), false);
        source.sendSuccess(() -> Component.literal("/ee civ [list|active|info|create] - Civilization tools"), false);
        source.sendSuccess(() -> Component.literal("/ee vip [list|info|claim|release|takeover-test|kill|successors|tag-self|clear-self-tag] - VIP tools"), false);
        source.sendSuccess(() -> Component.literal("/ee villager [nearby|virtual-count|explain|who|here|spawn-test|vip-spawn-test] - Villager/bot tools"), false);
        source.sendSuccess(() -> Component.literal("/ee relation [score|set] - Faction relationship tools"), false);
        source.sendSuccess(() -> Component.literal("/ee territory [owner|create-test] - Territory tools"), false);
        source.sendSuccess(() -> Component.literal("/ee incident test - Admin test incident generation"), false);
        source.sendSuccess(() -> Component.literal("/ee vehicle test - Admin test vehicle route evaluation"), false);
        source.sendSuccess(() -> Component.literal("/ee brain [availability|config|remote-start|queue|audit|test|affairs-propose|anomaly-propose] - Remote brain diagnostics"), false);
        source.sendSuccess(() -> Component.literal("/ee web [status|start|stop|url] - Localhost brain web chat"), false);
        source.sendSuccess(() -> Component.literal("/ee bot [connect|briefing|proposals|proposal-test] - Bot bridge diagnostics"), false);
        source.sendSuccess(() -> Component.literal("/ee actions recent - Recent player actions"), false);
        source.sendSuccess(() -> Component.literal("/ee divergence [checks|confidence|mark] - Divergence diagnostics"), false);
        source.sendSuccess(() -> Component.literal("/ee check all - Every runtime check at once: live system and history data; every line goes to the Minecraft log, no files are made (admin)"), false);
        source.sendSuccess(() -> Component.literal("/ee rep apply <action> - Apply a registered reputation action"), false);
        source.sendSuccess(() -> Component.literal("/ee vip list - Available VIPs"), false);
        source.sendSuccess(() -> Component.literal("/ee civ list - Civilizations"), false);
        source.sendSuccess(() -> Component.literal("Use tab completion for more commands."), false);
        return 1;
    }
    
    private static int executeRep(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player"));
            return 0;
        }
        
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        
        StringBuilder sb = new StringBuilder("=== Your Reputation ===\n");
        sb.append("Positive:\n");
        data.getAllPositiveRep().forEach((dim, val) -> 
            sb.append("  ").append(dim).append(": ").append(val).append("\n"));
        sb.append("Negative:\n");
        data.getAllNegativeRep().forEach((dim, val) -> 
            sb.append("  ").append(dim).append(": ").append(val).append("\n"));
        
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
    
    private static int executeProbeHere(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("/ee probe requires a player. Use /ee probe <x> <z> from console."));
            return 0;
        }
        return executeProbe(source, player.blockPosition().getX(), player.blockPosition().getZ());
    }

    /**
     * {@code /ee here} (step 62, "history at your feet"): the page of where you stand and what year it is, read
     * from the registers in plain lines - the place and its name this year, its people and its rulers, what the
     * record of these years names hereabouts, and whose land the border data counts it. Every line is a register's
     * own claim with its own marks ("About ... people", "an estimate", "Nobody the data records"); the page adds
     * no fact of its own and merges no two registers into one claim. Beyond the record the page thins out rather
     * than inventing, which is the AD 3000 guardrail at work.
     */
    private static int executeHere(CommandSourceStack source) {
        try {
            double[] geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeo(
                    net.minecraft.core.BlockPos.containing(source.getPosition()));
            if (geo == null) {
                source.sendFailure(Component.literal("Terra++ cannot say where this is."));
                return 0;
            }
            int year = SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
            com.eternalengine.here.HereAndNow.Reading reading =
                com.eternalengine.here.HereAndNow.read(geo[1], geo[0], year);
            for (String line : reading.lines()) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] here failed: {}", e.getMessage());
            source.sendFailure(Component.literal("The reading failed: " + e.getMessage()));
            return 0;
        }
    }


    /**
     * A command that needs the world, on a source that has none: say so in words and return, instead
     * of answering with a NullPointerException. Found by the command surface run of 2026-10-23, which
     * read eight commands that either threw or printed a Java message ("because \"server\" is null").
     */
    private static boolean noWorld(CommandSourceStack source) {
        if (source.getServer() != null && source.getLevel() != null) {
            return false;
        }
        source.sendFailure(Component.literal("This needs a running world: the server or the level is not up yet."));
        return true;
    }

    private static int executeProbe(CommandSourceStack source, int blockX, int blockZ) {
        if (noWorld(source)) {
            return 0;
        }
        try {
            WorldDebugSnapshot snapshot = WorldDebugSnapshot.capture(source.getLevel(), blockX, blockZ);
            if (!snapshot.valid()) {
                source.sendFailure(Component.literal(snapshot.toMultilineString()));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(snapshot.toMultilineString()), false);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] probe failed at {},{}: {}", blockX, blockZ, e.getMessage());
            source.sendFailure(Component.literal("Probe failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeScanChunk(CommandSourceStack source, int step) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("/ee scan chunk requires a player position."));
            return 0;
        }
        try {
            var pos = player.blockPosition();
            ChunkTelemetryScanner.ScanResult result = ChunkTelemetryScanner.scanChunkContaining(
                source.getLevel(), pos.getX(), pos.getZ(), step);
            if (!result.valid()) {
                source.sendFailure(Component.literal(result.toSummaryString()));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(result.toSummaryString()), false);
            for (String line : result.firstSnapshotLines(4)) {
                source.sendSuccess(() -> Component.literal("  " + line), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] scan chunk failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Scan failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeAnomalyProbe(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("/ee anomaly probe requires a player position."));
            return 0;
        }
        try {
            var pos = player.blockPosition();
            WorldDebugSnapshot snapshot = WorldDebugSnapshot.capture(source.getLevel(), pos.getX(), pos.getZ());
            int year = SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
            AnomalyScanResult result = new AnomalyDetectionEngine().detectSnapshot(snapshot, year);
            source.sendSuccess(() -> Component.literal(result.toSummaryString()), false);
            if (!result.hasAnomalies()) {
                source.sendSuccess(() -> Component.literal("  " + snapshot.toShortLine()), false);
                return 1;
            }
            result.topLines(5).forEach(line ->
                source.sendSuccess(() -> Component.literal("  " + line), false));
            source.sendSuccess(() -> Component.literal(result.anomalies().get(0).toDetailedString()), false);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] anomaly probe failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Anomaly probe failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeAnomalyChunk(CommandSourceStack source, int step) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("/ee anomaly chunk requires a player position."));
            return 0;
        }
        try {
            var pos = player.blockPosition();
            ChunkTelemetryScanner.ScanResult scan = ChunkTelemetryScanner.scanChunkContaining(
                source.getLevel(), pos.getX(), pos.getZ(), step);
            int year = SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
            AnomalyScanResult result = new AnomalyDetectionEngine().detectChunk(scan, year);
            source.sendSuccess(() -> Component.literal(scan.toSummaryString()), false);
            source.sendSuccess(() -> Component.literal(result.toSummaryString()), false);
            result.topLines(8).forEach(line ->
                source.sendSuccess(() -> Component.literal("  " + line), false));
            if (result.hasAnomalies()) {
                source.sendSuccess(() -> Component.literal("Top anomaly detail:\n" + result.anomalies().get(0).toDetailedString()), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] anomaly chunk failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Anomaly chunk scan failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeAffairs(CommandSourceStack source, AffairsScope scope, BotAffairsRole role) {
        try {
            ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
            var position = player == null ? null : player.blockPosition();
            CurrentAffairsBriefing briefing = SimulationEngine.getInstance().getCurrentAffairsEngine().createBriefing(
                WorldStateManager.getInstance(), source.getLevel(), position, role, scope, java.util.List.of());
            source.sendSuccess(() -> Component.literal(briefing.toDetailedString()), false);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] affairs failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Current affairs unavailable: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeIncidentTest(CommandSourceStack source) {
        if (noWorld(source)) {
            return 0;
        }
        try {
            int year = SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
            IncidentContext context = IncidentContext.builder()
                .worldSeed(source.getLevel().getSeed())
                .branchId(WorldStateManager.getInstance().getActiveTimelineBranchId())
                .year(year)
                .regionId(source.getLevel().dimension().location().toString())
                .difficulty(source.getLevel().getDifficulty())
                .hardcore(false)
                .instability(0.45d)
                .warPressure(WorldStateManager.getInstance().getAllActiveWars().isEmpty() ? 0.0d : 0.7d)
                .diseasePressure(0.25d)
                .faminePressure(0.20d)
                .prosperity(0.35d)
                .build();
            java.util.List<IncidentRecord> incidents = SimulationEngine.getInstance()
                .getIncidentCreatorEngine().createIncidents(context, 5);
            if (incidents.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Incident test produced no incidents for this deterministic roll."), false);
                return 1;
            }
            source.sendSuccess(() -> Component.literal("Incident test generated " + incidents.size() + " incident(s):"), false);
            for (IncidentRecord incident : incidents) {
                source.sendSuccess(() -> Component.literal("  " + incident.toSummaryString()), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] incident test failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Incident test failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeVehicleTest(CommandSourceStack source) {
        if (noWorld(source)) {
            return 0;
        }
        try {
            int year = SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
            VehicleEra era = VehicleEra.fromYear(year);
            // Step 21: the fastest land transport that exists anywhere in this year (a train only from 1825); step 30a:
            // where the one who asks stands, the fastest in use there (the technology atlas).
            VehicleType type = VehicleType.fastestLand(year);
            double[] geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeo(
                    net.minecraft.core.BlockPos.containing(source.getPosition()));
            if (geo != null) {
                type = VehicleType.fastestLandAt(geo[1], geo[0], year);
            }
            VehicleRoute route = new VehicleRoute("test_route", type, era,
                0.0d, 0.0d, 1.0d, 1.0d, 700.0d, 50.0d,
                "test_civ", true, false, year);
            VehicleRouteReport report = SimulationEngine.getInstance().getVehicleEngine().evaluateRoute(
                route, source.getLevel().getDifficulty(), false, 0.25d, 0.10d, 0.20d,
                source.getEntity() instanceof ServerPlayer);
            source.sendSuccess(() -> Component.literal(report.toSummaryString()), false);
            source.sendSuccess(() -> Component.literal("  " + report.explanation()), false);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] vehicle test failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Vehicle test failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeBrainAvailability(CommandSourceStack source) {
        boolean available = SimulationEngine.getInstance().getBrainBridge().isAvailable();
        source.sendSuccess(() -> Component.literal("Brain bridge="
            + SimulationEngine.getInstance().getBrainBridge().getClass().getSimpleName()
            + " available=" + available
            + " queue=" + SimulationEngine.getInstance().getServerDecisionQueue().size()), false);
        return 1;
    }

    private static int executeBrainRemoteStart(CommandSourceStack source) {
        try {
            var bridge = SimulationEngine.getInstance().enableRemoteBrainBridge();
            boolean available = bridge.isAvailable();
            source.sendSuccess(() -> Component.literal("Brain bridge switched to "
                + bridge.getClass().getSimpleName()
                + " available=" + available
                + ". Primary=LiteLLM, secondary=Portkey. Configure [brainGateway] in serverconfig/eternalengine-server.toml."), false);
            source.sendSuccess(() -> Component.literal("Remote brain is advisory-only. The Minecraft server still validates proposals and owns all world changes."
                + (available ? "" : " Gateways are not configured yet (available=false).")), false);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] brain remote-start failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Brain remote-start failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeBrainConfig(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(SimulationEngine.getInstance().getBrainConfigSummary()), false);
        return 1;
    }

    private static int executeBrainQueue(CommandSourceStack source) {
        var queue = SimulationEngine.getInstance().getServerDecisionQueue();
        source.sendSuccess(() -> Component.literal("Brain queue size=" + queue.size()
            + " accepted=" + queue.acceptedCount() + " rejected=" + queue.rejectedCount()), false);
        return 1;
    }

    private static int executeBrainAudit(CommandSourceStack source) {
        java.util.List<String> lines = SimulationEngine.getInstance().getBrainAuditLog().recent(10);
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Brain audit log is empty."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Recent brain audit entries:"), false);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return 1;
    }

    private static int executeBrainTest(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
            IPlayerSimulationData data = player == null ? null : EternalEngineCapabilities.getPlayerData(player);
            LlmProposal proposal = new LlmProposal("cmd_test_" + System.currentTimeMillis(),
                player == null ? "console" : player.getUUID().toString(),
                "REQUEST_CURRENT_AFFAIRS",
                java.util.Map.of("scope", "LOCAL"),
                "Command test proposal", 0.95d);
            LlmValidationContext context = new LlmValidationContext(
                player == null ? "" : player.getUUID().toString(), data,
                SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getCurrentYear() : WorldStateManager.getInstance().getCurrentYear(),
                source.hasPermission(2), true);
            LlmValidationResult result = SimulationEngine.getInstance().getServerDecisionQueue().submit(proposal, context);
            SimulationEngine.getInstance().getBrainAuditLog().record(proposal, result);
            source.sendSuccess(() -> Component.literal("Brain test proposal accepted=" + result.accepted()
                + " reason=" + result.reason()), false);
            return result.accepted() ? 1 : 0;
        } catch (Exception e) {
            LOGGER.warn("[Commands] brain test failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Brain test failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeBrainAffairsPropose(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
            var position = player == null ? null : player.blockPosition();
            CurrentAffairsBriefing briefing = SimulationEngine.getInstance().getCurrentAffairsEngine()
                .createBriefing(WorldStateManager.getInstance(), source.getLevel(), position,
                    BotAffairsRole.ADVISOR, AffairsScope.LOCAL, java.util.List.of());
            source.sendSuccess(() -> Component.literal("Submitted affairs facts to brain bridge: "
                + SimulationEngine.getInstance().getBrainBridge().getClass().getSimpleName()), false);
            SimulationEngine.getInstance().getBrainBridge().proposeFromAffairs(briefing)
                .whenComplete((proposals, error) -> AsyncTaskManager.getInstance().submitToMainThread(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Brain affairs proposal failed: " + error.getMessage()));
                        return;
                    }
                    handleBrainProposalResults(source, proposals, createBrainValidationContext(source));
                }));
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] brain affairs-propose failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Brain affairs-propose failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeBrainAnomalyPropose(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("/ee brain anomaly-propose requires a player position."));
            return 0;
        }
        try {
            WorldDebugSnapshot snapshot = WorldDebugSnapshot.capture(source.getLevel(),
                player.blockPosition().getX(), player.blockPosition().getZ());
            int year = SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
            AnomalyScanResult result = SimulationEngine.getInstance().getAnomalyDetectionEngine()
                .detectSnapshot(snapshot, year);
            if (!result.hasAnomalies()) {
                source.sendSuccess(() -> Component.literal("No anomalies at current position; no brain proposal requested."), false);
                return 1;
            }
            source.sendSuccess(() -> Component.literal("Submitted " + result.anomalies().size()
                + " anomaly record(s) to brain bridge."), false);
            SimulationEngine.getInstance().getBrainBridge().proposeFromAnomalies(result.anomalies())
                .whenComplete((proposals, error) -> AsyncTaskManager.getInstance().submitToMainThread(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Brain anomaly proposal failed: " + error.getMessage()));
                        return;
                    }
                    handleBrainProposalResults(source, proposals, createBrainValidationContext(source));
                }));
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] brain anomaly-propose failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Brain anomaly-propose failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeWebStatus(CommandSourceStack source) {
        WebConsoleService web = SimulationEngine.getInstance().getWebConsoleService();
        source.sendSuccess(() -> Component.literal("Web console running=" + web.isRunning()
            + " port=" + web.port()
            + " brain=" + SimulationEngine.getInstance().getBrainBridge().getClass().getSimpleName()
            + " brainAvailable=" + SimulationEngine.getInstance().getBrainBridge().isAvailable()), false);
        if (web.isRunning()) {
            source.sendSuccess(() -> webLinkComponent("URL: ", web.accessUrl()), false);
        } else {
            source.sendSuccess(() -> Component.literal("Use /ee web start to start the localhost-only diagnostic chat."), false);
        }
        return 1;
    }

    private static int executeWebStart(CommandSourceStack source) {
        WebConsoleService web = SimulationEngine.getInstance().getWebConsoleService();
        boolean started = web.start(source.getServer());
        if (started) {
            source.sendSuccess(() -> webLinkComponent("Web console started: ", web.accessUrl()), false);
            source.sendSuccess(() -> Component.literal("Localhost only. Token: " + web.tokenForDisplay()), false);
            return 1;
        }
        source.sendFailure(Component.literal("Failed to start web console. Check logs and webConsolePort."));
        return 0;
    }

    private static int executeWebStop(CommandSourceStack source) {
        SimulationEngine.getInstance().getWebConsoleService().stop();
        source.sendSuccess(() -> Component.literal("Web console stopped."), false);
        return 1;
    }

    private static int executeWebUrl(CommandSourceStack source) {
        WebConsoleService web = SimulationEngine.getInstance().getWebConsoleService();
        if (!web.isRunning()) {
            source.sendFailure(Component.literal("Web console is not running. Use /ee web start first."));
            return 0;
        }
        source.sendSuccess(() -> webLinkComponent("Open: ", web.accessUrl()), false);
        source.sendSuccess(() -> Component.literal("Token: " + web.tokenForDisplay()), false);
        return 1;
    }

    private static Component webLinkComponent(String prefix, String url) {
        return Component.literal(prefix == null ? "" : prefix)
            .append(Component.literal(url == null ? "" : url)
                .withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url == null ? "" : url))));
    }

    private static LlmValidationContext createBrainValidationContext(CommandSourceStack source) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
        IPlayerSimulationData data = player == null ? null : EternalEngineCapabilities.getPlayerData(player);
        return new LlmValidationContext(
            player == null ? "" : player.getUUID().toString(),
            data,
            SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear(),
            source.hasPermission(2),
            true);
    }

    private static void handleBrainProposalResults(CommandSourceStack source, java.util.List<LlmProposal> proposals,
                                                   LlmValidationContext context) {
        if (proposals == null || proposals.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Brain returned no proposals."), false);
            return;
        }
        for (LlmProposal proposal : proposals) {
            LlmValidationResult validation = SimulationEngine.getInstance().getServerDecisionQueue().submit(proposal, context);
            SimulationEngine.getInstance().getBrainAuditLog().record(proposal, validation);
            source.sendSuccess(() -> Component.literal("Brain proposal " + proposal.proposalId()
                + " action=" + proposal.actionName()
                + " accepted=" + validation.accepted()
                + " reason=" + validation.reason()), false);
        }
    }

    private static int executeBotConnect(CommandSourceStack source, String botId) {
        SimulationEngine.getInstance().getMindcraftBridge().connectBot(botId);
        source.sendSuccess(() -> Component.literal("Connected bot bridge identity: " + botId), true);
        return 1;
    }

    private static int executeBotBriefing(CommandSourceStack source, String botId, String roleName) {
        BotAffairsRole role;
        try {
            role = BotAffairsRole.valueOf(roleName.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Unknown bot role: " + roleName));
            return 0;
        }
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
        var position = player == null ? null : player.blockPosition();
        BotIdentity identity = new BotIdentity(botId, botId, role, "", "neutral");
        var briefing = SimulationEngine.getInstance().getBotCurrentAffairsService()
            .createBriefing(identity, WorldStateManager.getInstance(), source.getLevel(), position, AffairsScope.LOCAL);
        SimulationEngine.getInstance().getMindcraftBridge().publishBriefing(botId, briefing);
        String explanation = SimulationEngine.getInstance().getBotCurrentAffairsService().explainBriefing(identity, briefing);
        source.sendSuccess(() -> Component.literal(explanation), false);
        return 1;
    }

    private static int executeBotProposals(CommandSourceStack source, String botId) {
        java.util.List<LlmProposal> proposals = SimulationEngine.getInstance().getMindcraftBridge().readPendingProposals(botId, 10);
        if (proposals.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No pending proposals for bot " + botId), false);
            return 1;
        }
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
        IPlayerSimulationData data = player == null ? null : EternalEngineCapabilities.getPlayerData(player);
        LlmValidationContext context = new LlmValidationContext(
            player == null ? "" : player.getUUID().toString(), data,
            SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getCurrentYear() : WorldStateManager.getInstance().getCurrentYear(),
            source.hasPermission(2), true);
        for (LlmProposal proposal : proposals) {
            LlmValidationResult result = SimulationEngine.getInstance().getServerDecisionQueue().submit(proposal, context);
            SimulationEngine.getInstance().getBrainAuditLog().record(proposal, result);
            source.sendSuccess(() -> Component.literal("  proposal " + proposal.proposalId()
                + " action=" + proposal.actionName()
                + " accepted=" + result.accepted()
                + " reason=" + result.reason()), false);
        }
        return 1;
    }

    private static int executeBotProposalTest(CommandSourceStack source, String botId, String action) {
        LlmProposal proposal = new LlmProposal("bot_test_" + System.currentTimeMillis(), botId,
            action, java.util.Map.of("source", "command"), "Bot proposal test", 0.95d);
        SimulationEngine.getInstance().getMindcraftBridge().submitProposal(botId, proposal);
        source.sendSuccess(() -> Component.literal("Submitted test proposal for bot " + botId
            + ". Run /ee bot proposals " + botId + " to validate and queue it."), true);
        return 1;
    }

    private static int executeRepApply(CommandSourceStack source, String action) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player"));
            return 0;
        }
        try {
            IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
            ReputationEffect effect = SimulationEngine.getInstance().getReputationEngine().applyAction(data, action);
            int positive = SimulationEngine.getInstance().getReputationEngine().overallPositive(data);
            int negative = SimulationEngine.getInstance().getReputationEngine().overallNegative(data);
            source.sendSuccess(() -> Component.literal("Applied reputation action " + effect.actionType()
                + ": " + effect.description() + " overall+=" + positive + " overall-=" + negative), true);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] rep apply failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Reputation apply failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeBlockQueueStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(SimulationEngine.getInstance().getBlockUpdateQueue().stats()), false);
        return 1;
    }

    private static int executeBlockQueueClear(CommandSourceStack source) {
        SimulationEngine.getInstance().getBlockUpdateQueue().clear();
        source.sendSuccess(() -> Component.literal("Block update queue cleared."), true);
        return 1;
    }

    private static int executeBuilding(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Look at a building and use /ee building history"), false);
        return 1;
    }
    
    private static int executeBuildingHistory(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Building history requires a player location."));
            return 0;
        }
        WorldDebugSnapshot snapshot = WorldDebugSnapshot.capture(source.getLevel(),
            player.blockPosition().getX(), player.blockPosition().getZ());
        if (!snapshot.valid()) {
            source.sendFailure(Component.literal(snapshot.toMultilineString()));
            return 0;
        }
        var nearby = SimulationEngine.getInstance().getBuildingHistoryDatabase()
            .getBuildingsInRegion(snapshot.latitude() - 0.001d, snapshot.latitude() + 0.001d,
                snapshot.longitude() - 0.001d, snapshot.longitude() + 0.001d);
        if (nearby.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No building records near this position. Terrain context:\n"
                + snapshot.toMultilineString()), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Nearby building histories: " + nearby.size()), false);
        nearby.stream().limit(8).forEach(record ->
            source.sendSuccess(() -> Component.literal("  - " + record.buildingId() + " " + record.name()
                + " changes=" + record.stateHistory().size()), false));
        return 1;
    }

    private static int executeBuildingAddTest(CommandSourceStack source, String id, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Building add-test requires a player location."));
            return 0;
        }
        WorldDebugSnapshot snapshot = WorldDebugSnapshot.capture(source.getLevel(),
            player.blockPosition().getX(), player.blockPosition().getZ());
        if (!snapshot.valid()) {
            source.sendFailure(Component.literal(snapshot.toMultilineString()));
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        BuildingRecord record = new BuildingRecord(id, name, snapshot.latitude(), snapshot.longitude(), year);
        record.setEra(year >= 1945 ? "MODERN" : year >= 1750 ? "INDUSTRIAL" : year >= 500 ? "MEDIEVAL" : "ANCIENT");
        record.setOwnerCivId("neutral");
        record.setPurpose("test");
        SimulationEngine.getInstance().getBuildingHistoryDatabase().addBuilding(record);
        source.sendSuccess(() -> Component.literal("Added test building " + id + " at lat="
            + snapshot.latitude() + " lon=" + snapshot.longitude()), true);
        return 1;
    }

    private static int executeBuildingNearby(CommandSourceStack source, int radiusMeters) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Building nearby requires a player location."));
            return 0;
        }
        WorldDebugSnapshot snapshot = WorldDebugSnapshot.capture(source.getLevel(),
            player.blockPosition().getX(), player.blockPosition().getZ());
        if (!snapshot.valid()) {
            source.sendFailure(Component.literal(snapshot.toMultilineString()));
            return 0;
        }
        var entries = SimulationEngine.getInstance().getBuildingHistoryDatabase().spatialIndex()
            .queryRadius(snapshot.latitude(), snapshot.longitude(), radiusMeters);
        source.sendSuccess(() -> Component.literal("Buildings within " + radiusMeters + "m: " + entries.size()), false);
        entries.stream().limit(12).forEach(entry ->
            source.sendSuccess(() -> Component.literal("  - " + entry.id()
                + " lat=" + entry.lat() + " lon=" + entry.lon()), false));
        return 1;
    }

    private static int executeBuildingInfo(CommandSourceStack source, String id) {
        var record = SimulationEngine.getInstance().getBuildingHistoryDatabase().getBuilding(id);
        if (record.isEmpty()) {
            source.sendFailure(Component.literal("Building not found: " + id));
            return 0;
        }
        BuildingRecord b = record.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Building ").append(b.buildingId()).append(" - ").append(b.name()).append("\n");
        sb.append("  lat/lon: ").append(b.latitude()).append(", ").append(b.longitude()).append("\n");
        sb.append("  year: ").append(b.constructionYear()).append(" era: ").append(b.era()).append("\n");
        sb.append("  state: ").append(b.currentState()).append(" damage: ").append(b.damagePercent()).append("%\n");
        sb.append("  owner: ").append(b.ownerCivId()).append(" purpose: ").append(b.purpose()).append("\n");
        sb.append("  culturalSignificance: ").append(b.culturalSignificance()).append(" changes: ").append(b.stateHistory().size());
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeBuildingInfer(CommandSourceStack source, String id) {
        var record = SimulationEngine.getInstance().getBuildingHistoryDatabase().getBuilding(id);
        if (record.isEmpty()) {
            source.sendFailure(Component.literal("Building not found: " + id));
            return 0;
        }
        BuildingDateInferenceEngine.BuildingDateResult result = SimulationEngine.getInstance()
            .getBuildingDateInferenceEngine().inferConstructionDate(record.get());
        source.sendSuccess(() -> Component.literal("Building date inference " + id
            + " best=" + result.yearBest()
            + " range=" + result.yearMin() + ".." + result.yearMax()
            + " era=" + result.era()
            + " confidence=" + result.confidence()
            + " source=" + result.sourceUsed()
            + " reason=" + result.reasoning()), false);
        return 1;
    }

    private static int executeBuildingDamage(CommandSourceStack source, String id, int percent, String cause) {
        var record = SimulationEngine.getInstance().getBuildingHistoryDatabase().getBuilding(id);
        if (record.isEmpty()) {
            source.sendFailure(Component.literal("Building not found: " + id));
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        BuildingRecord b = record.get();
        BuildingStateChange change = new BuildingStateChange(year, "DAMAGE", cause,
            "Manual building damage command", b.currentState(), percent >= 100 ? "DESTROYED" : "DAMAGED",
            b.era(), b.era(), b.ownerCivId(), b.ownerCivId(), b.purpose(), b.purpose(), percent);
        b.addStateChange(change);
        SimulationEngine.getInstance().getBuildingHistoryDatabase().updateBuilding(b);
        source.sendSuccess(() -> Component.literal("Applied building damage " + percent + "% to " + id), true);
        return 1;
    }

    private static int executeBuildingAnomalies(CommandSourceStack source, int radiusMeters) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Building anomaly scan requires a player location."));
            return 0;
        }
        WorldDebugSnapshot snapshot = WorldDebugSnapshot.capture(source.getLevel(),
            player.blockPosition().getX(), player.blockPosition().getZ());
        if (!snapshot.valid()) {
            source.sendFailure(Component.literal(snapshot.toMultilineString()));
            return 0;
        }
        var entries = SimulationEngine.getInstance().getBuildingHistoryDatabase().spatialIndex()
            .queryRadius(snapshot.latitude(), snapshot.longitude(), radiusMeters);
        java.util.List<BuildingRecord> buildings = new java.util.ArrayList<>();
        for (var entry : entries) {
            var record = SimulationEngine.getInstance().getBuildingHistoryDatabase().getBuilding(entry.id());
            record.ifPresent(buildings::add);
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        AnomalyScanResult result = new BuildingAnomalyDetector().detect(snapshot, buildings, year);
        source.sendSuccess(() -> Component.literal("Building anomaly scan buildings=" + buildings.size()
            + " radius=" + radiusMeters + "m " + result.toSummaryString()), false);
        result.topLines(10).forEach(line ->
            source.sendSuccess(() -> Component.literal("  " + line), false));
        if (result.hasAnomalies()) {
            source.sendSuccess(() -> Component.literal("Top building anomaly detail:\n"
                + result.anomalies().get(0).toDetailedString()), false);
        }
        return 1;
    }

    private static int executeBuildingPlanTest(CommandSourceStack source, String id, String type) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Building plan-test requires a player location."));
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        String era = year >= 1945 ? "MODERN" : year >= 1750 ? "INDUSTRIAL" : year >= 500 ? "MEDIEVAL" : "ANCIENT";
        // Step 33: where the world is a real place, the house is built of what that place is built
        // of in this year -- mud brick in Timbuktu in 1350, a panel block in Moscow in 1980 --
        // rather than of a coarse era. Without Terra++ the era form is used, as it always was.
        var geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint(player.blockPosition());
        BuildingStyle style;
        String what;
        if (geo != null) {
            style = new EraBuildingPlanner().selectStyle(geo.latitude(), geo.longitude(), year,
                    SettlementRank.TOWN, type);
            what = "place=" + style.styleId() + " at " + String.format(java.util.Locale.ROOT, "%.3f,%.3f",
                    geo.latitude(), geo.longitude());
        } else {
            style = new EraBuildingPlanner().selectStyle(era, type);
            what = "era=" + era;
        }
        PlannedBuilding plan = new EraBuildingPlanner().planAtSurface(source.getLevel(), player.blockPosition(), style, id);
        int queued = plan.enqueue(SimulationEngine.getInstance().getBlockUpdateQueue(), 10);
        source.sendSuccess(() -> Component.literal("Planned building " + id
            + " type=" + type
            + " " + what
            + " year=" + year
            + " floors=" + style.floors()
            + " blocks=" + plan.size()
            + " queued=" + queued
            + " origin=" + plan.origin()
            + " explanation=" + plan.explanation()), true);
        return queued > 0 ? 1 : 0;
    }

    private static int executeMorphStatus(CommandSourceStack source) {
        var morphCtrl = SimulationEngine.getInstance().getTemporalMorphController();
        source.sendSuccess(() -> Component.literal("Temporal Morph Controller:"
            + " isMorphing=" + morphCtrl.isMorphing()
            + " freezeTicksRemaining=" + morphCtrl.getFreezeTicksRemaining()
            + " morphCount=" + morphCtrl.getMorphCount()
            + " lastMorphedYear=" + morphCtrl.getLastMorphedYear()), false);
        return 1;
    }

    private static int executeMorphJump(CommandSourceStack source, int targetYear) {
        if (!onlyForward(source, targetYear)) {
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        boolean ok = engine.getTemporalMorphController().morphToYear(source.getServer(), targetYear, engine.getBlockUpdateQueue());
        if (ok) {
            source.sendSuccess(() -> Component.literal("Atomic temporal morph executed: transitioned to year " + targetYear), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Temporal morph to year " + targetYear + " failed or was rejected by bridge."));
            return 0;
        }
    }

    private static int executeMorphLandmark(CommandSourceStack source, String typeStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Landmark generation requires a player."));
            return 0;
        }
        HistoricalLandmarkGenerator.LandmarkType parsedType;
        try {
            parsedType = HistoricalLandmarkGenerator.LandmarkType.valueOf(typeStr.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedType = HistoricalLandmarkGenerator.LandmarkType.PARTHENON;
        }
        final HistoricalLandmarkGenerator.LandmarkType type = parsedType;

        var generator = SimulationEngine.getInstance().getLandmarkGenerator();
        String id = "landmark_" + type.name().toLowerCase(java.util.Locale.ROOT) + "_" + System.currentTimeMillis() % 10000;
        PlannedBuilding plan = generator.generateLandmark(source.getLevel(), player.blockPosition(), type, id);
        int queued = plan.enqueue(SimulationEngine.getInstance().getBlockUpdateQueue(), 10);

        source.sendSuccess(() -> Component.literal("Generated historical landmark " + type
            + " with " + plan.size() + " blocks (queued " + queued + ") at " + player.blockPosition().toShortString()), true);
        return queued > 0 ? 1 : 0;
    }

    private static int executeMorphGrammar(CommandSourceStack source, String cultureStr, String typeStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Grammar generation requires a player."));
            return 0;
        }
        CivilizationGrammarEngine.GrammarCulture parsedCulture;
        try {
            parsedCulture = CivilizationGrammarEngine.GrammarCulture.valueOf(cultureStr.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedCulture = CivilizationGrammarEngine.GrammarCulture.CLASSICAL_GRECO_ROMAN;
        }
        final CivilizationGrammarEngine.GrammarCulture culture = parsedCulture;

        CivilizationGrammarEngine.BuildingGrammarType parsedGrammarType;
        try {
            parsedGrammarType = CivilizationGrammarEngine.BuildingGrammarType.valueOf(typeStr.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedGrammarType = CivilizationGrammarEngine.BuildingGrammarType.TEMPLE;
        }
        final CivilizationGrammarEngine.BuildingGrammarType type = parsedGrammarType;

        var grammar = SimulationEngine.getInstance().getGrammarEngine();
        String id = "grammar_" + culture.name().toLowerCase(java.util.Locale.ROOT) + "_" + System.currentTimeMillis() % 10000;
        PlannedBuilding plan = grammar.generatePlan(source.getLevel(), player.blockPosition(), 14, 18, culture, type, id);
        int queued = plan.enqueue(SimulationEngine.getInstance().getBlockUpdateQueue(), 10);

        source.sendSuccess(() -> Component.literal("Generated grammar architecture (" + culture + " " + type
            + ") with " + plan.size() + " blocks (queued " + queued + ")"), true);
        return queued > 0 ? 1 : 0;
    }
    
    private static int executeHistory(CommandSourceStack source, int year) {
        String yearStr = year < 0 ? Math.abs(year) + " BCE" : year + " CE";
        var events = SimulationEngine.getInstance().getHistoricalEventDatabase().getEventsInYear(year);
        source.sendSuccess(() -> Component.literal("History for " + yearStr + ": " + events.size() + " loaded event(s)."), false);
        events.stream().limit(12).forEach(event ->
            source.sendSuccess(() -> Component.literal("  - " + event.id() + " " + event.name()), false));
        return 1;
    }
    
    private static int executeTimeline(CommandSourceStack source) {
        String branch = SimulationEngine.getInstance().getTimelineManager().activeBranchId();
        source.sendSuccess(() -> Component.literal("Active timeline branch: " + branch), false);
        source.sendSuccess(() -> Component.literal("Use /ee timeline branch, /ee timeline divergences, /ee timeline roll <eventKey>"), false);
        return 1;
    }

    private static int executeTimelineBranch(CommandSourceStack source) {
        TimelineBranch branch = SimulationEngine.getInstance().getTimelineManager().getActiveBranch();
        if (branch == null) {
            source.sendFailure(Component.literal("No active timeline branch available"));
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Timeline Branch: ").append(branch.branchId()).append("\n");
        sb.append("  parent: ").append(branch.parentBranchId()).append("\n");
        sb.append("  divergenceEvent: ").append(branch.divergenceEventId()).append("\n");
        sb.append("  divergenceYear: ").append(branch.divergenceYear()).append("\n");
        sb.append("  cause: ").append(branch.divergenceCause()).append("\n");
        sb.append("  events: ").append(branch.events().size()).append("\n");
        sb.append("  stillPossible: ").append(branch.stillPossible().size());
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
    
    private static int executeTimelineDivergences(CommandSourceStack source) {
        var branches = SimulationEngine.getInstance().getTimelineManager().getDivergencePoints();
        if (branches.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No timeline divergences recorded."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Timeline divergences: " + branches.size()), false);
        for (TimelineBranch branch : branches) {
            source.sendSuccess(() -> Component.literal("  - " + branch.branchId()
                + " year=" + branch.divergenceYear()
                + " event=" + branch.divergenceEventId()), false);
        }
        return 1;
    }

    /**
     * <b>The fixed points of this world, and whether each still stands.</b>
     *
     * <p>Before board 14 this page did not exist: {@code timeline/FixedPoints.java} declared the
     * points, {@link ParadoxEngine} judged them, {@link Repairs} rebuilt them, and no command named
     * the class — so FINAL_TEST G5's refusal could never happen to a player.
     */
    private static int executeTimelineFixed(CommandSourceStack source, String id) {
        FixedPoints pts = FixedPointRegister.points();
        if (id == null) {
            for (String line : FixedPointRegister.lines()) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            source.sendSuccess(() -> Component.literal("  (" + FixedPoints.ruleLine() + ")"), false);
            return 1;
        }
        FixedPoints.Point p = pts.find(id);
        if (p == null) {
            source.sendFailure(Component.literal("No fixed point called " + id + "; /ee timeline fixed lists them"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(p.line()), false);
        if (pts.broken(p.id())) {
            source.sendSuccess(() -> Component.literal("  BROKEN by " + pts.brokenBy(p.id())
                + "; rebuild it with /ee timeline rebuild " + p.id() + " <why>"), false);
        } else {
            source.sendSuccess(() -> Component.literal("  standing: an in-place change is refused, and the "
                + "safe way out is a branch (/ee timeline diverge)"), false);
        }
        return 1;
    }

    /** <b>Force a fixed point.</b> It breaks, the record names the hand and the why, and the paradox is booked. */
    private static int executeTimelineForce(CommandSourceStack source, String id, String why) {
        if (noWorld(source)) {
            return 0;
        }
        int year = currentYear();
        String by = source.getTextName();
        var verdict = ParadoxEngine.forceFixed(new Permissions.Actor(by, Permissions.Rank.OPERATOR),
                FixedPointRegister.points(), FixedPointRegister.stress(), id, why, year);
        if (!verdict.ok()) {
            source.sendFailure(Component.literal(verdict.decision().why()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Forced " + id + ": " + verdict.decision().why()
            + " (" + verdict.booked().size() + " on the counter)"), true);
        return 1;
    }

    /** <b>Rebuild what was forced.</b> The hand is recorded and the point's weight comes off the counter. */
    private static int executeTimelineRebuild(CommandSourceStack source, String id, String why) {
        if (noWorld(source)) {
            return 0;
        }
        int year = currentYear();
        var repaired = Repairs.repair(FixedPointRegister.points(), FixedPointRegister.stress(),
                source.getTextName(), id, year, why);
        source.sendSuccess(() -> Component.literal(repaired.line()), true);
        return repaired.point() == null ? 0 : 1;
    }

    private static int executeTimelineRoll(CommandSourceStack source, String eventKey) {
        if (noWorld(source)) {
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        TimelineBranch branch = SimulationEngine.getInstance().getTimelineManager().getActiveBranch();
        TimelineSeed seed = new TimelineSeed(source.getLevel().getSeed(),
            branch == null ? "main" : branch.branchId(),
            branch == null ? "roll" : branch.divergenceEventId(),
            year,
            source.getTextName() + ":roll");
        double roll = seed.roll(year, eventKey);
        source.sendSuccess(() -> Component.literal("Timeline roll eventKey=" + eventKey
            + " year=" + year + " branchSeed=" + seed.branchId() + " roll=" + roll), false);
        return 1;
    }

    private static int executeTimelineDiverge(CommandSourceStack source, String eventId, String cause) {
        // A fixed point is not diverged in place: it is refused, and the refusal names the way that works
        // (force it and carry the paradox, or rebuild it afterwards). FINAL_TEST G5, board 14.
        FixedPoints.Point fixed = FixedPointRegister.points().find(eventId);
        if (fixed != null) {
            var verdict = ParadoxEngine.attemptFixed(FixedPointRegister.points(),
                    FixedPointRegister.stress(), eventId, "a timeline divergence");
            source.sendFailure(Component.literal(verdict.decision().why()
                + " - /ee timeline force " + fixed.id() + " <why> breaks it deliberately and books the paradox,"
                + " and /ee timeline rebuild " + fixed.id() + " <why> puts it back"));
            return 0;
        }
        if (noWorld(source)) {
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        String playerHash = source.getTextName() + ":" + Integer.toHexString((cause == null ? "" : cause).hashCode());
        TimelineSeed seed = new TimelineSeed(source.getLevel().getSeed(),
            SimulationEngine.getInstance().getTimelineManager().activeBranchId(),
            eventId,
            year,
            playerHash);
        TimelineBranch branch = SimulationEngine.getInstance().getTimelineManager()
            .createDivergence(seed, cause, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Created timeline divergence: " + branch.branchId()
            + " year=" + branch.divergenceYear()
            + " event=" + branch.divergenceEventId()), true);
        return 1;
    }

    private static int executeTimelineCascade(CommandSourceStack source, String eventId, String cause) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Timeline cascade requires a player location."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : 1914;

        var result = engine.getTimelineManager().getCascadeEngine().triggerCascade(
            eventId, cause, year, player.blockPosition(), source.getLevel(),
            engine.getTimelineManager(), WorldStateManager.getInstance());

        if (result == null) {
            source.sendFailure(Component.literal("Failed to execute divergence cascade."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6[BUTTERFLY EFFECT]§r Divergence Cascade Triggered!"), true);
        source.sendSuccess(() -> Component.literal("  Branch: §e" + result.branchId() + "§r | Year: " + result.divergenceYear()), true);
        source.sendSuccess(() -> Component.literal("  Pruned Obsolete Events: §c" + result.obsoletePrunedEvents().size() + "§r"), true);
        source.sendSuccess(() -> Component.literal("  Synthesized Alternate Events: §a" + result.alternateEvents().size() + "§r"), true);
        source.sendSuccess(() -> Component.literal("  Spatial Reality Rift spawned at: " + player.blockPosition().toShortString()), true);
        return 1;
    }

    private static int executeTimelineRift(CommandSourceStack source, String eventId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Spawning a Reality Rift requires a player."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : 0;
        String echo = "At this exact coordinate in the Prime Timeline, " + eventId + " fundamentally altered the trajectory of nations.";

        var rift = engine.getTimelineManager().getRiftController().spawnRift(
            eventId, engine.getTimelineManager().activeBranchId(), year,
            player.blockPosition(), echo, source.getLevel());

        if (rift == null) {
            source.sendFailure(Component.literal("Failed to spawn Reality Rift."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§d[Reality Rift]§r Spacetime tear opened (" + rift.riftId()
            + ") at " + player.blockPosition().toShortString()), true);
        return 1;
    }

    private static int executeTimelineRifts(CommandSourceStack source) {
        var rifts = SimulationEngine.getInstance().getTimelineManager().getRiftController().getActiveRifts();
        if (rifts.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active reality rifts detected in spacetime."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Active Spatial Reality Rifts: " + rifts.size()), false);
        for (var r : rifts) {
            source.sendSuccess(() -> Component.literal("  - " + r.riftId()
                + " pivot=" + r.pivotEventId()
                + " year=" + r.divergenceYear()
                + " at=" + r.centerPos().toShortString()), false);
        }
        return 1;
    }

    private static int executeCodex(CommandSourceStack source, String tabStr) {
        var engine = SimulationEngine.getInstance();
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : 2026;

        String tab = tabStr != null ? tabStr.toLowerCase(java.util.Locale.ROOT) : "tree";
        List<String> pages;

        switch (tab) {
            case "atlas" -> pages = ChronosCodexData.buildAtlasPages(WorldStateManager.getInstance(), year);
            case "dossier", "vips" -> pages = ChronosCodexData.buildDossierPages();
            case "chronicle", "news" -> pages = ChronosCodexData.buildChroniclePages(engine.getTimelineManager(), year);
            case "tree", "timeline" -> pages = ChronosCodexData.buildTimelineTreePages(engine.getTimelineManager());
            default -> pages = ChronosCodexData.buildTimelineTreePages(engine.getTimelineManager());
        }

        source.sendSuccess(() -> Component.literal("§6=== THE CHRONOS CODEX (" + tab.toUpperCase(java.util.Locale.ROOT) + ") ===§r"), false);
        for (String p : pages) {
            source.sendSuccess(() -> Component.literal(p), false);
        }
        return 1;
    }

    private static int executeSovereignCourt(CommandSourceStack source, String civId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Sovereign court requires a player location."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        var court = engine.getRoyalCourtManager().establishThroneRoom(civId, player.blockPosition(), source.getLevel());
        if (court == null) {
            source.sendFailure(Component.literal("Failed to establish royal throne room."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Established Royal Throne Room (" + court.courtId()
            + ") for " + civId.toUpperCase(java.util.Locale.ROOT) + " with Sovereign Ruler, Bodyguards, and Chancellor."), true);
        return 1;
    }

    private static int executeSovereignPetition(CommandSourceStack source, String civId, String petition) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Petitioning the court requires a player."));
            return 0;
        }
        boolean ok = SimulationEngine.getInstance().getRoyalCourtManager().petitionAudience(player, civId, petition);
        return ok ? 1 : 0;
    }

    private static int executeSovereignTreaty(CommandSourceStack source, String civA, String civB, String typeStr) {
        DiplomaticTreatyEngine.TreatyType parsedType;
        try {
            parsedType = DiplomaticTreatyEngine.TreatyType.valueOf(typeStr.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedType = DiplomaticTreatyEngine.TreatyType.NON_AGGRESSION_PACT;
        }
        final DiplomaticTreatyEngine.TreatyType type = parsedType;

        var engine = SimulationEngine.getInstance();
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : 0;

        var treaty = engine.getTreatyEngine().signTreaty(civA, civB, type, year, 10, 500L,
            engine.getFactionRelationshipSystem());

        if (treaty == null) {
            source.sendFailure(Component.literal("Failed to sign treaty."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Signed diplomatic treaty: " + treaty.treatyId()
            + " (" + civA + " ↔ " + civB + ") Type: " + type + " until year " + treaty.expirationYear()), true);
        return 1;
    }

    private static int executeSovereignMegaproject(CommandSourceStack source, String typeStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Commissioning a megaproject requires a player location."));
            return 0;
        }
        ImperialCommandEngine.MegaprojectType parsedType;
        try {
            parsedType = ImperialCommandEngine.MegaprojectType.valueOf(typeStr.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedType = ImperialCommandEngine.MegaprojectType.GRAND_COLOSSEUM;
        }
        final ImperialCommandEngine.MegaprojectType type = parsedType;

        var engine = SimulationEngine.getInstance();
        var commission = engine.getImperialCommandEngine().commissionMegaproject(
            "rome", type, player.blockPosition(), source.getLevel(),
            engine.getBlockUpdateQueue(), engine.getEconomyEngine());

        if (commission == null) {
            source.sendFailure(Component.literal("Failed to commission megaproject."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Commissioned Imperial Megaproject " + type
            + " (" + commission.projectId() + ") at " + player.blockPosition().toShortString()
            + ". Masons and scaffolding deployed!"), true);
        return 1;
    }

    private static int executeSovereignDoctrine(CommandSourceStack source, String civId, String doctrineStr) {
        ImperialCommandEngine.MilitaryDoctrine parsedDoctrine;
        try {
            parsedDoctrine = ImperialCommandEngine.MilitaryDoctrine.valueOf(doctrineStr.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedDoctrine = ImperialCommandEngine.MilitaryDoctrine.ATTRITION_DEFENSE;
        }
        final ImperialCommandEngine.MilitaryDoctrine doctrine = parsedDoctrine;

        SimulationEngine.getInstance().getImperialCommandEngine().setDoctrine(civId, doctrine);
        source.sendSuccess(() -> Component.literal("Set grand military doctrine for " + civId + " to: " + doctrine), true);
        return 1;
    }

    private static int executeSovereignTax(CommandSourceStack source, String civId, int ratePercent) {
        double rate = ratePercent / 100.0D;
        SimulationEngine.getInstance().getImperialCommandEngine().setTaxRate(civId, rate);
        source.sendSuccess(() -> Component.literal("Set imperial tax rate for " + civId + " to " + ratePercent + "%"), true);
        return 1;
    }

    private static int executeSovereignDebase(CommandSourceStack source, String civId) {
        var engine = SimulationEngine.getInstance();
        boolean ok = engine.getImperialCommandEngine().debaseCurrency(civId, 0.25D, engine.getEconomyEngine(), WorldStateManager.getInstance());
        if (ok) {
            source.sendSuccess(() -> Component.literal("Debased imperial currency for " + civId
                + ": +4000 treasury coin injected into circulation; inflation flagged."), true);
            return 1;
        }
        source.sendFailure(Component.literal("Failed to debase currency."));
        return 0;
    }

    private static int executeSovereignCoup(CommandSourceStack source, String civId, String pretender) {
        var engine = SimulationEngine.getInstance();
        boolean success = engine.getSuccessionEngine().attemptUsurpation(civId, pretender, true, WorldStateManager.getInstance());
        if (success) {
            source.sendSuccess(() -> Component.literal("§6[PALACE COUP] §fPretender " + pretender
                + " successfully seized the throne of " + civId + "!"), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Palace coup by " + pretender + " failed! Loyalists defended the crown."));
            return 0;
        }
    }

    private static int executeEcsStatus(CommandSourceStack source) {
        var vEngine = SimulationEngine.getInstance().getVirtualizationEngine();
        source.sendSuccess(() -> Component.literal("§6=== 3-TIER POPULATION ECS METRICS ===§r"), false);
        source.sendSuccess(() -> Component.literal("  Tier 1 Total Virtual Agents: §a" + vEngine.getTotalAgentCount()
            + "§r (Capacity: " + vEngine.ecs().capacity() + ")"), false);
        source.sendSuccess(() -> Component.literal("  Memory Footprint: §e" + String.format("%.2f MB", vEngine.getMemoryUsageBytes() / (1024.0 * 1024.0))
            + "§r (exact 32 bytes/agent)"), false);
        source.sendSuccess(() -> Component.literal("  Tier 3 Active Materialized: §b" + vEngine.getMaterializedCount() + "§r (<64m from players)"), false);
        source.sendSuccess(() -> Component.literal("  Average Tick Duration: §d" + String.format("%.3f ms", vEngine.getAverageTickDurationMs())
            + "§r (Budget: 8.0 ms)"), false);
        return 1;
    }

    private static int executeEcsPopulate(CommandSourceStack source, String civId, int count) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Populating ECS requires a player location."));
            return 0;
        }
        var vEngine = SimulationEngine.getInstance().getVirtualizationEngine();
        int added = vEngine.populateMassive(civId, count, player.getBlockX(), player.getBlockZ());
        source.sendSuccess(() -> Component.literal("Populated " + added + " virtual agents for civ=" + civId
            + " into 32-byte Struct-of-Arrays ECS. Total population: " + vEngine.getTotalAgentCount()), true);
        return 1;
    }

    private static int executeEcsBenchmark(CommandSourceStack source, int count) {
        var vEngine = SimulationEngine.getInstance().getVirtualizationEngine();
        source.sendSuccess(() -> Component.literal("Running 100K-agent zero-heap stress benchmark over " + count + " agents..."), false);
        var report = vEngine.runBenchmark(count);
        source.sendSuccess(() -> Component.literal("§6=== ECS BENCHMARK RESULT ===§r"), false);
        source.sendSuccess(() -> Component.literal("  Agents Processed: " + report.agentCount()), false);
        source.sendSuccess(() -> Component.literal("  Memory Allocated: " + String.format("%.2f MB", report.memoryBytes() / (1024.0 * 1024.0))), false);
        source.sendSuccess(() -> Component.literal("  Average Step Time: §a" + String.format("%.3f ms", report.averageStepDurationMs()) + "§r"), false);
        source.sendSuccess(() -> Component.literal("  Within 8.0ms Server Budget: " + (report.within8MsBudget() ? "§2YES (20.0 TPS GUARANTEED)§r" : "§cNO§r")), false);
        return 1;
    }

    private static int executeEcsFlush(CommandSourceStack source) {
        var vEngine = SimulationEngine.getInstance().getVirtualizationEngine();
        int before = vEngine.getMaterializedCount();
        vEngine.materializationController().flushAll(source.getLevel(), vEngine.ecs());
        source.sendSuccess(() -> Component.literal("Flushed state and dematerialized " + before + " agents back to Tier-1 SoA arrays."), true);
        return 1;
    }

    private static int executeMapSync(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Map sync requires a player."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        engine.getOverlayManager().syncOverlaysToPlayer(
            player, WorldStateManager.getInstance(),
            engine.getWarEngine(), engine.getLogisticsManager(),
            engine.getDiseaseEngine());

        source.sendSuccess(() -> Component.literal("§6[Cartography] §fSynchronized map overlay layers (Trade Routes, War Fronts, Quarantines) to minimap."), true);
        return 1;
    }

    private static int executeAudioAtmosphere(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Audio atmosphere requires a player."));
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : 100;
        boolean inWar = !SimulationEngine.getInstance().getWarEngine().getActiveWars().isEmpty();

        com.eternalengine.carto.HistoricalAudioDirector director =
                SimulationEngine.getInstance().getAudioDirector();
        director.playAtmosphere(player, year, inWar);
        // The player asked, so say it. The old code printed this unbidden on every tick, which
        // is how a guess gets mistaken for a fact.
        source.sendSuccess(() -> Component.literal("§6[Soundscape] §7" + director.describe(player, year)), false);
        String thin = director.coverage();
        source.sendSuccess(() -> Component.literal("§8  (" + thin + ")"), false);
        return 1;
    }

    private static int executeHeraldryList(CommandSourceStack source) {
        var registry = SimulationEngine.getInstance().getHeraldryRegistry();
        source.sendSuccess(() -> Component.literal("§6=== REGISTERED HERALDIC CRESTS ===§r"), false);
        for (var entry : registry.getAllCrests().entrySet()) {
            source.sendSuccess(() -> Component.literal("  • §l" + entry.getKey().toUpperCase(java.util.Locale.ROOT)
                + "§r: " + entry.getValue().chargeName() + " (Base: " + entry.getValue().baseColor() + ", Patterns: "
                + entry.getValue().layers().size() + ")"), false);
        }
        return 1;
    }

    private static int executeHeraldryGive(CommandSourceStack source, String civId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Heraldry give requires a player."));
            return 0;
        }
        var registry = SimulationEngine.getInstance().getHeraldryRegistry();
        ItemStack banner = registry.createBannerItem(civId);
        player.getInventory().add(banner);
        source.sendSuccess(() -> Component.literal("Bestowed authentic heraldic crest banner for "
            + civId.toUpperCase(java.util.Locale.ROOT) + " into inventory."), true);
        return 1;
    }

    private static int executeChronoStatus(CommandSourceStack source) {
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        source.sendSuccess(() -> Component.literal("§6=== CHRONO-NEXUS: THE TEMPORAL PARADOX STATUS ==="), false);
        source.sendSuccess(() -> Component.literal("  • §lMode: §e" + engine.getMode().name() + " §7(" + engine.getMode().getDescription() + ")"), false);
        source.sendSuccess(() -> Component.literal("  • §lCurrent Phase: §b[" + engine.getCurrentPhase().getPhaseNumber() + "/7] "
            + engine.getCurrentPhase().getTitle()), false);
        source.sendSuccess(() -> Component.literal("  • §lObjective: §f" + engine.getCurrentPhase().getObjective()), false);
        if (engine.getParadoxController().isActive()) {
            source.sendSuccess(() -> Component.literal("  • §lParadox Loop: §cACTIVE! ("
                + engine.getParadoxController().getCountdownSeconds() + "s remaining)"), false);
            source.sendSuccess(() -> Component.literal("  • §lTarget Delivery Site: §f"
                + engine.getParadoxController().getTargetDeliveryPos().toShortString()), false);
        } else {
            source.sendSuccess(() -> Component.literal("  • §lParadox Loop: §7Inactive"), false);
        }
        if (engine.getResolvedEnding() != null) {
            source.sendSuccess(() -> Component.literal("  • §lResolved Ending: §d" + engine.getResolvedEnding().getTitle()), false);
        }
        return 1;
    }

    private static int executeChronoStart(CommandSourceStack source) {
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        engine.startCampaign(source.getServer());
        source.sendSuccess(() -> Component.literal("§6[Chrono-Nexus] §aCampaign started! Transferred players to Year -3000 BC."), true);
        return 1;
    }

    private static int executeChronoMode(CommandSourceStack source, String modeStr) {
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        if ("story".equalsIgnoreCase(modeStr) || "survival".equalsIgnoreCase(modeStr)) {
            engine.setMode(ChronoGameMode.STORY);
            source.sendSuccess(() -> Component.literal("§6[Chrono-Nexus] §eMode switched to §lSTORY (Survival Campaign)§r."), true);
        } else {
            engine.setMode(ChronoGameMode.EXPLORATION);
            source.sendSuccess(() -> Component.literal("§6[Chrono-Nexus] §eMode switched to §lEXPLORATION (Creative Sandbox)§r."), true);
        }
        return 1;
    }

    private static int executeChronoPhase(CommandSourceStack source, int phaseNum) {
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        var phase = ChronoNexusCampaignPhase.fromNumber(phaseNum);
        engine.setPhase(phase, source.getServer());
        source.sendSuccess(() -> Component.literal("§6[Chrono-Nexus] §aJumped to Phase " + phaseNum + ": " + phase.getTitle()), true);
        return 1;
    }

    private static int executeChronoAdvance(CommandSourceStack source) {
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        engine.advancePhase(source.getServer());
        source.sendSuccess(() -> Component.literal("§6[Chrono-Nexus] §aAdvanced to next campaign phase."), true);
        return 1;
    }

    private static int executeChronoRivalSpawn(CommandSourceStack source) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        var rival = engine.spawnRival(source.getLevel(), player);
        if (rival != null) {
            source.sendSuccess(() -> Component.literal("§c[Rival Incursion] §4Spawned mirror Rival variant: " + rival.getMirroredPlayerName()), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn rival variant."));
            return 0;
        }
    }

    private static int executeChronoParadoxStart(CommandSourceStack source, int seconds) {
        if (noWorld(source)) {
            return 0;
        }
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        BlockPos target = source.getEntity() != null ? source.getEntity().blockPosition() : new BlockPos(0, 70, 0);
        engine.getParadoxController().startCountdown(seconds, target, source.getServer().getPlayerList().getPlayers());
        source.sendSuccess(() -> Component.literal("§c[Paradox Triggered] §4Countdown initiated: " + seconds + "s at " + target.toShortString()), true);
        return 1;
    }

    private static int executeChronoParadoxDeliver(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Paradox delivery requires a player."));
            return 0;
        }
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        boolean delivered = engine.getParadoxController().deliverMachine(player);
        if (delivered) {
            source.sendSuccess(() -> Component.literal("§a[Loop Closed] Successfully delivered Time Machine to past self!"), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Delivery failed: Not close enough to -3000 BC target coordinates or paradox inactive."));
            return 0;
        }
    }

    private static int executeChronoEnding(CommandSourceStack source, String typeStr) {
        var engine = SimulationEngine.getInstance().getChronoNexusEngine();
        var ending = ChronoEnding.fromString(typeStr);
        engine.triggerEnding(ending, source.getServer());
        source.sendSuccess(() -> Component.literal("§5[Ending Triggered] Resolved campaign with: " + ending.getTitle()), true);
        return 1;
    }
    
    private static int executeEventList(CommandSourceStack source) {
        var db = SimulationEngine.getInstance().getHistoricalEventDatabase();
        var events = db.getAllEvents();
        source.sendSuccess(() -> Component.literal("Historical events loaded: " + events.size()), false);
        int shown = 0;
        for (HistoricalEvent event : events) {
            if (shown++ >= 12) {
                source.sendSuccess(() -> Component.literal("  ... use /ee event info <id> for details"), false);
                break;
            }
            source.sendSuccess(() -> Component.literal("  - " + event.id() + " " + event.formatYear() + " " + event.name()), false);
        }
        return 1;
    }

    private static int executeEventPending(CommandSourceStack source) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        var engine = SimulationEngine.getInstance().getEventChainEngine();
        var pending = engine.database().getPendingEvents(year, WorldStateManager.getInstance(),
            engine.getTriggeredEvents(), engine.getPreventedEvents());
        source.sendSuccess(() -> Component.literal("Pending events up to " + year + ": " + pending.size()), false);
        pending.stream().limit(12).forEach(event ->
            source.sendSuccess(() -> Component.literal("  - " + event.id() + " " + event.formatYear() + " " + event.name()), false));
        return 1;
    }

    private static int executeEventInfo(CommandSourceStack source, String id) {
        var event = SimulationEngine.getInstance().getHistoricalEventDatabase().getEvent(id);
        if (event.isEmpty()) {
            source.sendFailure(Component.literal("Historical event not found: " + id));
            return 0;
        }
        HistoricalEvent e = event.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Event ").append(e.id()).append(" - ").append(e.name()).append("\n");
        sb.append("  date: ").append(e.formatYear()).append("-").append(e.month()).append("-").append(e.day()).append("\n");
        sb.append("  type: ").append(e.type()).append(" severity: ").append(e.severity()).append("\n");
        sb.append("  region: ").append(e.regionId()).append(" lat/lon: ").append(e.latitude()).append(", ").append(e.longitude()).append("\n");
        sb.append("  preconditions: ").append(e.preconditionIds()).append("\n");
        sb.append("  conditions: ").append(e.conditions().size()).append("\n");
        sb.append("  next: ").append(e.nextChainEventIds());
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeEventTrigger(CommandSourceStack source, String id) {
        var event = SimulationEngine.getInstance().getHistoricalEventDatabase().getEvent(id);
        if (event.isEmpty()) {
            source.sendFailure(Component.literal("Historical event not found: " + id));
            return 0;
        }
        SimulationEngine.getInstance().getEventChainEngine().triggerEvent(event.get(), WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Triggered event: " + id), true);
        return 1;
    }

    private static int executeEventPrevent(CommandSourceStack source, String id, String cause) {
        var event = SimulationEngine.getInstance().getHistoricalEventDatabase().getEvent(id);
        if (event.isEmpty()) {
            source.sendFailure(Component.literal("Historical event not found: " + id));
            return 0;
        }
        String player = source.getEntity() instanceof ServerPlayer p ? p.getUUID().toString() : source.getTextName();
        SimulationEngine.getInstance().getEventChainEngine().markPrevented(event.get(), player, cause,
            source.getLevel().getSeed(), WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Prevented event and requested timeline divergence: " + id), true);
        return 1;
    }

    private static int executeEventLoad(CommandSourceStack source) {
        try {
            var dir = EternalDataPaths.events(source.getServer());
            SimulationEngine.getInstance().getHistoricalEventDatabase().loadFromFiles(dir);
            source.sendSuccess(() -> Component.literal("Loaded historical events from " + dir
                + " count=" + SimulationEngine.getInstance().getHistoricalEventDatabase().getSize()), true);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] event load failed: {}", e.getMessage());
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            String why = cause instanceof NullPointerException
                    ? "the world is not ready (no server or level yet)" : String.valueOf(cause.getMessage());
            source.sendFailure(Component.literal("Event load failed: " + why));
            return 0;
        }
    }

    private static int executeDataPaths(CommandSourceStack source) {
        var server = source.getServer();
        if (server == null) {
            // The command surface run (2026-10-23) reached this from a console with no world and got
            // a NullPointerException out of it, not an answer.
            source.sendFailure(Component.literal("This needs a running world: the server is not up yet."));
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Eternal data paths:\n");
        sb.append("  root: ").append(EternalDataPaths.root(server)).append("\n");
        sb.append("  events: ").append(EternalDataPaths.events(server)).append("\n");
        sb.append("  vips: ").append(EternalDataPaths.vips(server)).append("\n");
        sb.append("  buildings: ").append(EternalDataPaths.buildings(server)).append("\n");
        sb.append("  civilizations: ").append(EternalDataPaths.civilizations(server)).append("\n");
        sb.append("  religions: ").append(EternalDataPaths.religions(server)).append("\n");
        sb.append("  technologies: ").append(EternalDataPaths.technologies(server)).append("\n");
        sb.append("  climate: ").append(EternalDataPaths.climate(server));
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeDataInstall(CommandSourceStack source, boolean overwrite) {
        if (noWorld(source)) {
            return 0;
        }
        try {
            FoundationDataInstaller.InstallResult result = FoundationDataInstaller.install(source.getServer(), overwrite);
            source.sendSuccess(() -> Component.literal(result.summary()), true);
            return result.failed() == 0 ? 1 : 0;
        } catch (Exception e) {
            LOGGER.warn("[Commands] data install failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Data install failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDataLoad(CommandSourceStack source) {
        try {
            FoundationDataLoader.LoadResult result = FoundationDataLoader.loadInstalled(source.getServer(), SimulationEngine.getInstance());
            source.sendSuccess(() -> Component.literal(result.summary()), true);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[Commands] data load failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Data load failed: " + e.getMessage()));
            return 0;
        }
    }
    
    /** {@code /ee data status} (step 14): this world's data version and what happened to every file. */
    private static int executeDataStatus(CommandSourceStack source) {
        var server = source.getServer();
        var last = FoundationDataInstaller.lastRefresh(server);
        if (last.isEmpty()) {
            source.sendFailure(Component.literal("No data check ran for this world yet (it runs when the world starts)"));
            return 0;
        }
        var r = last.get();
        StringBuilder sb = new StringBuilder("World data: ").append(r.summary()).append('.');
        for (var f : r.files()) {
            if (f.state() != FoundationDataInstaller.FileState.CURRENT) {
                sb.append("\n  ").append(f.state()).append(": ").append(f.resource())
                    .append(f.detail().isBlank() ? "" : " (" + f.detail() + ")");
            }
        }
        if (r.count(FoundationDataInstaller.FileState.KEPT_UNKNOWN) + r.count(FoundationDataInstaller.FileState.KEPT_EDITED) > 0) {
            sb.append("\n  Kept files stay as they are. /ee data install-overwrite replaces every file with this mod version's copy.");
        }
        sb.append("\n  The world history (rulers, events, people, places) is read from the mod itself and is always this version's.");
        String text = sb.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int executeDiseaseList(CommandSourceStack source) {
        var diseases = SimulationEngine.getInstance().getDiseaseEngine().getActiveDiseases();
        if (diseases.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active disease outbreaks"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Active disease outbreaks: " + diseases.size()), false);
        for (DiseaseOutbreak outbreak : diseases) {
            EpidemicScale scale = SimulationEngine.getInstance().getDiseaseEngine().epidemicTracker()
                .detectEpidemicThreshold(outbreak);
            source.sendSuccess(() -> Component.literal("  - " + outbreak.outbreakId()
                + " disease=" + outbreak.diseaseId()
                + " regions=" + outbreak.affectedRegionIds().size()
                + " deaths=" + outbreak.totalDeaths()
                + " scale=" + scale), false);
        }
        return 1;
    }

    private static int executeDiseaseInfo(CommandSourceStack source, String outbreakId) {
        var outbreak = SimulationEngine.getInstance().getDiseaseEngine().getOutbreak(outbreakId);
        if (outbreak.isEmpty()) {
            source.sendFailure(Component.literal("Outbreak not found: " + outbreakId));
            return 0;
        }
        DiseaseOutbreak o = outbreak.get();
        EpidemicScale scale = SimulationEngine.getInstance().getDiseaseEngine().epidemicTracker()
            .detectEpidemicThreshold(o);
        StringBuilder sb = new StringBuilder();
        sb.append("Outbreak ").append(o.outbreakId()).append("\n");
        sb.append("  disease: ").append(o.diseaseId()).append("\n");
        sb.append("  origin: ").append(o.originRegionId()).append(" startYear: ").append(o.startYear()).append("\n");
        sb.append("  regions: ").append(o.affectedRegionIds().size()).append(" scale: ").append(scale).append("\n");
        sb.append("  deaths: ").append(o.totalDeaths()).append(" ended: ").append(o.isEnded()).append("\n");
        sb.append("  severity: ").append(o.regionSeverity());
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeDiseaseTrigger(CommandSourceStack source, String diseaseId, String regionId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        DiseaseOutbreak outbreak = SimulationEngine.getInstance().getDiseaseEngine()
            .triggerDisease(diseaseId, regionId, year, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Triggered disease outbreak: " + outbreak.outbreakId()
            + " disease=" + diseaseId + " region=" + regionId), true);
        return 1;
    }

    private static int executeDiseaseQuarantine(CommandSourceStack source, String regionId) {
        WorldStateManager.getInstance().setFlag("quarantine." + regionId, "true");
        source.sendSuccess(() -> Component.literal("Quarantine enabled for region: " + regionId), true);
        return 1;
    }

    private static int executeDiseaseCordon(CommandSourceStack source, String regionId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Quarantine cordon requires a player location."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        var cp = engine.getDiseaseEngine().checkpointController()
            .establishCheckpoint(regionId, player.blockPosition(), source.getLevel(), engine.getBlockUpdateQueue());
        if (cp == null) {
            source.sendFailure(Component.literal("Failed to establish quarantine checkpoint."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Established sanitary cordon " + cp.checkpointId()
            + " for region=" + regionId + " with " + cp.inspectorUuids().size() + " inspectors and barricades."), true);
        return 1;
    }

    private static int executeDiseaseDoctorTest(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Doctor test requires a player location."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        var doctor = engine.getDiseaseEngine().hospitalManager()
            .spawnDoctor(player.blockPosition(), source.getLevel(), MedicineTier.MEDIEVAL);
        if (doctor == null) {
            source.sendFailure(Component.literal("Failed to spawn plague doctor."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawned Plague Doctor (" + doctor.getName().getString()
            + ") equipped with inspection cane and medicine."), true);
        return 1;
    }

    private static int executeDiseaseHospitalTest(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Hospital test requires a player location."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        var tent = engine.getDiseaseEngine().hospitalManager()
            .erectHospitalTent(player.blockPosition(), source.getLevel(), engine.getBlockUpdateQueue(), MedicineTier.MEDIEVAL);
        if (tent == null) {
            source.sendFailure(Component.literal("Failed to erect hospital isolation tent."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Erected field hospital isolation tent " + tent.hospitalId()
            + " with " + tent.tentBlocks().size() + " blocks and " + tent.doctorUuids().size() + " doctors."), true);
        return 1;
    }

    private static int executeDiseaseInfectTest(CommandSourceStack source, String diseaseId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Infect test requires a player location."));
            return 0;
        }
        var contagion = SimulationEngine.getInstance().getDiseaseEngine().contagionBrain();
        var npcs = source.getLevel().getEntitiesOfClass(com.eternalengine.entity.HumanNpcEntity.class,
            player.getBoundingBox().inflate(12.0D));

        if (npcs.isEmpty()) {
            source.sendFailure(Component.literal("No nearby HumanNpcEntities to infect. Spawn NPCs first with /ee npc spawn."));
            return 0;
        }

        int infected = 0;
        for (var npc : npcs) {
            contagion.infect(npc.getUUID(), diseaseId, 0.8D);
            infected++;
        }
        final int count = infected;
        source.sendSuccess(() -> Component.literal("Infected " + count + " nearby NPC(s) with " + diseaseId
            + ". Real-time contagion and symptomatic progression active."), true);
        return 1;
    }

    private static int executeDiseaseStatus(CommandSourceStack source) {
        var de = SimulationEngine.getInstance().getDiseaseEngine();
        source.sendSuccess(() -> Component.literal("Disease & Epidemic Status:"), false);
        source.sendSuccess(() -> Component.literal("  Active In-World Infections: " + de.contagionBrain().activeInfectionCount()
            + " | Immune Citizens: " + de.contagionBrain().immuneCount()), false);
        source.sendSuccess(() -> Component.literal("  Active Quarantine Checkpoints: " + de.checkpointController().activeCount()), false);
        source.sendSuccess(() -> Component.literal("  Active Field Hospitals: " + de.hospitalManager().activeHospitalCount()), false);
        source.sendSuccess(() -> Component.literal("  Active Disaster Incidents: " + de.disasterEngine().activeCount()), false);
        return 1;
    }

    private static int executeDisasterTrigger(CommandSourceStack source, String typeStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Disaster trigger requires a player location."));
            return 0;
        }
        HistoricalDisasterEngine.DisasterType parsedType;
        try {
            parsedType = HistoricalDisasterEngine.DisasterType.valueOf(typeStr.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedType = HistoricalDisasterEngine.DisasterType.VOLCANIC_WINTER;
        }
        final HistoricalDisasterEngine.DisasterType type = parsedType;

        var engine = SimulationEngine.getInstance();
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : 536;
        var incident = engine.getDiseaseEngine().disasterEngine()
            .triggerDisaster(type, player.blockPosition(), year, source.getLevel(), engine.getBlockUpdateQueue());

        if (incident == null) {
            source.sendFailure(Component.literal("Failed to trigger disaster."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Triggered historical disaster " + type
            + " (" + incident.disasterId() + ") at " + player.blockPosition().toShortString()), true);
        return 1;
    }
    
    private static int executeWarList(CommandSourceStack source) {
        var wars = SimulationEngine.getInstance().getWarEngine().getActiveWars();
        if (wars.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active wars"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Active wars: " + wars.size()), false);
            for (War war : wars) {
                source.sendSuccess(() -> Component.literal("  - " + war.warId()
                    + " " + war.name()
                    + " status=" + war.status()
                    + " exhaustion=" + String.format("%.1f", war.warExhaustion())), false);
            }
        }
        return 1;
    }

    private static int executeWarInfo(CommandSourceStack source, String warId) {
        var war = SimulationEngine.getInstance().getWarEngine().getWar(warId);
        if (war.isEmpty()) {
            source.sendFailure(Component.literal("War not found: " + warId));
            return 0;
        }
        War w = war.get();
        StringBuilder sb = new StringBuilder();
        sb.append("War ").append(w.warId()).append(" - ").append(w.name()).append("\n");
        sb.append("  status: ").append(w.status()).append("\n");
        sb.append("  startYear: ").append(w.startYear()).append(" endYear: ").append(w.endYear()).append("\n");
        sb.append("  attackers: ").append(w.alliedFactionIds()).append("\n");
        sb.append("  defenders: ").append(w.opposedFactionIds()).append("\n");
        sb.append("  exhaustion: ").append(String.format("%.2f", w.warExhaustion())).append("\n");
        sb.append("  battles: ").append(w.battles().size());
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeWarDeclare(CommandSourceStack source, String attacker, String defender, String cause) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        War war = SimulationEngine.getInstance().getWarEngine()
            .declareWar(attacker, defender, cause, year, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Declared war: " + war.warId() + " " + war.name()), true);
        return 1;
    }

    private static int executeWarEnd(CommandSourceStack source, String warId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        SimulationEngine.getInstance().getWarEngine().endWar(warId, year, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Ended war: " + warId), true);
        return 1;
    }

    private static int executeArmyTest(CommandSourceStack source) {
        var armies = SimulationEngine.getInstance().getWarEngine().armyManager();
        String id = "test_army_" + System.currentTimeMillis();
        Army army = new Army(id, "test_civ", 0.0d, 0.0d, 10000L, 70, 80, "MEDIEVAL", 25.0d);
        army.setCurrentOrder(new Army.MovementOrder("order_" + id, "MOVE", 1.0d, 1.0d, "test_target",
            SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getCurrentYear() : 0));
        armies.addArmy(army);
        source.sendSuccess(() -> Component.literal("Created virtual army " + id + " owner=test_civ size=10000"), true);
        return 1;
    }

    private static int executeWarDeploy(CommandSourceStack source, String armyId, String formationStr, int count) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("War deploy requires a player."));
            return 0;
        }
        var armyMgr = SimulationEngine.getInstance().getWarEngine().armyManager();
        Army army = armyMgr.getArmy(armyId).orElseGet(() -> {
            Army a = new Army(armyId, "militia", 0, 0, 1000L, 75, 80, "ANCIENT", 20.0D);
            armyMgr.addArmy(a);
            return a;
        });

        FormationType parsedFormation;
        try {
            parsedFormation = FormationType.valueOf(formationStr.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedFormation = FormationType.LINE;
        }
        final FormationType formation = parsedFormation;

        RegimentCohort cohort = armyMgr.deployCohort(army, player.blockPosition(), source.getLevel(), formation, count);
        if (cohort == null) {
            source.sendFailure(Component.literal("Failed to deploy physical cohort."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Deployed physical cohort " + cohort.cohortId()
            + " civ=" + army.ownerCivId() + " formation=" + formation + " units=" + cohort.activeStrength()), true);
        return 1;
    }

    private static int executeWarBattleTest(CommandSourceStack source, String civ1, String civ2) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Battle test requires a player."));
            return 0;
        }
        var armyMgr = SimulationEngine.getInstance().getWarEngine().armyManager();
        Army army1 = new Army("army_" + civ1 + "_" + System.currentTimeMillis(), civ1, 0, 0, 5000L, 80, 80, "ANCIENT", 20.0D);
        Army army2 = new Army("army_" + civ2 + "_" + System.currentTimeMillis(), civ2, 0, 0, 5000L, 80, 80, "ANCIENT", 20.0D);
        armyMgr.addArmy(army1);
        armyMgr.addArmy(army2);

        BlockPos pPos = player.blockPosition();
        BlockPos pos1 = pPos.offset(-12, 0, 0);
        BlockPos pos2 = pPos.offset(12, 0, 0);

        RegimentCohort c1 = armyMgr.deployCohort(army1, pos1, source.getLevel(), FormationType.LINE, 10);
        RegimentCohort c2 = armyMgr.deployCohort(army2, pos2, source.getLevel(), FormationType.LINE, 10);

        if (c1 != null && c2 != null) {
            c1.setTargetCohortId(c2.cohortId());
            c2.setTargetCohortId(c1.cohortId());
            source.sendSuccess(() -> Component.literal("Battle clash initialized: " + civ1 + " vs " + civ2 + " (10 vs 10 units in formation)"), true);
            return 1;
        }
        source.sendFailure(Component.literal("Failed to deploy test battle cohorts."));
        return 0;
    }

    private static int executeWarSiegeTest(CommandSourceStack source, String attackerCiv, String defenderCiv) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Siege test requires a player."));
            return 0;
        }
        BlockPos target = player.blockPosition().offset(0, 0, 16);
        String siegeId = "siege_" + System.currentTimeMillis() % 10000;
        SimulationEngine.getInstance().getWarEngine().siegeController()
            .startSiege(siegeId, attackerCiv, defenderCiv, target, source.getLevel().dimension());

        source.sendSuccess(() -> Component.literal("Started siege operation " + siegeId + ": " + attackerCiv
            + " bombarding " + defenderCiv + " fortifications at " + target.toShortString()), true);
        return 1;
    }

    private static int executeWarCohorts(CommandSourceStack source) {
        var cohorts = SimulationEngine.getInstance().getWarEngine().armyManager().getActiveCohorts();
        if (cohorts.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active physical cohorts deployed."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Active physical cohorts: " + cohorts.size()), false);
        for (RegimentCohort c : cohorts) {
            source.sendSuccess(() -> Component.literal("  - " + c.cohortId()
                + " civ=" + c.civilizationId()
                + " formation=" + c.formation()
                + " strength=" + c.activeStrength()
                + " anchor=" + c.anchorPos().toShortString()), false);
        }
        return 1;
    }

    private static int executeTradeList(CommandSourceStack source) {
        var routes = SimulationEngine.getInstance().getTradeRouteEngine().getActiveRoutes();
        if (routes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active trade routes"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Active trade routes: " + routes.size()), false);
        for (TradeRoute route : routes) {
            source.sendSuccess(() -> Component.literal("  - " + route.routeId()
                + " " + route.originCivId() + " -> " + route.destCivId()
                + " transport=" + route.transportType()
                + " value=" + route.economicValuePerYear()
                + " danger=" + String.format("%.1f", route.dangerLevel())), false);
        }
        return 1;
    }

    private static int executeTradeInfo(CommandSourceStack source, String routeId) {
        var route = SimulationEngine.getInstance().getTradeRouteEngine().getRoute(routeId);
        if (route.isEmpty()) {
            source.sendFailure(Component.literal("Trade route not found: " + routeId));
            return 0;
        }
        TradeRoute r = route.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Trade Route ").append(r.routeId()).append(" - ").append(r.name()).append("\n");
        sb.append("  origin: ").append(r.originCivId()).append(" dest: ").append(r.destCivId()).append("\n");
        sb.append("  transport: ").append(r.transportType()).append(" goods: ").append(r.goods()).append("\n");
        sb.append("  value/year: ").append(r.economicValuePerYear()).append(" danger: ").append(String.format("%.1f", r.dangerLevel())).append("\n");
        sb.append("  active: ").append(r.active()).append(" opened: ").append(r.openedYear()).append(" closed: ").append(r.closedYear()).append("\n");
        sb.append("  disruptions: ").append(r.disruptionHistory());
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeTradeOpen(CommandSourceStack source, String origin, String dest, String transport) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        TransportType type;
        try {
            type = TransportType.valueOf(transport.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Unknown transport type: " + transport));
            return 0;
        }
        TradeRoute route = SimulationEngine.getInstance().getTradeRouteEngine()
            .openRoute(origin, dest, java.util.List.of("general_goods"), type, year, 10_000L);
        source.sendSuccess(() -> Component.literal("Opened trade route: " + route.routeId()), true);
        return 1;
    }

    private static int executeTradeClose(CommandSourceStack source, String routeId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        SimulationEngine.getInstance().getTradeRouteEngine().closeRoute(routeId, "command", year, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Closed trade route: " + routeId), true);
        return 1;
    }

    private static int executeTradeDisrupt(CommandSourceStack source, String routeId, String cause) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        SimulationEngine.getInstance().getTradeRouteEngine().disruptRoute(routeId, cause, year);
        source.sendSuccess(() -> Component.literal("Disrupted trade route: " + routeId + " cause=" + cause), true);
        return 1;
    }

    private static int executeTradeCaravan(CommandSourceStack source, String routeId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Trade caravan deployment requires a player."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        var tradeRoutes = engine.getTradeRouteEngine();
        var logistics = engine.getLogisticsManager();

        TradeRoute route = tradeRoutes.getRoute(routeId).orElseGet(() ->
            tradeRoutes.openRoute("rome", "carthage", List.of("SILK", "SPICES", "WINE"),
                TransportType.CARAVAN, 100, 5000L));

        BlockPos pPos = player.blockPosition();
        List<BlockPos> waypoints = List.of(
            pPos.offset(12, 0, 0),
            pPos.offset(24, 0, 8),
            pPos.offset(36, 0, 16)
        );

        CaravanEntityGroup caravan = tradeRoutes.materializeCaravan(route.routeId(), pPos, waypoints, source.getLevel(), logistics);
        if (caravan == null) {
            source.sendFailure(Component.literal("Failed to deploy physical caravan."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Deployed physical trade caravan " + caravan.caravanId()
            + " on route " + route.name() + " carrying value=" + Math.round(caravan.cargoValue()) + " silver"), true);
        return 1;
    }

    private static int executeTradeAmbushTest(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Ambush test requires a player."));
            return 0;
        }
        var logistics = SimulationEngine.getInstance().getLogisticsManager();
        List<CaravanEntityGroup> caravans = logistics.getActiveCaravans();
        CaravanEntityGroup candidate = caravans.isEmpty() ? null : caravans.get(0);

        if (candidate == null) {
            // Deploy a demo caravan first
            BlockPos pPos = player.blockPosition();
            List<BlockPos> wps = List.of(pPos.offset(10, 0, 0), pPos.offset(25, 0, 0));
            candidate = logistics.deployCaravan(null, pPos, wps, source.getLevel(), null);
        }
        final CaravanEntityGroup target = candidate;

        var ambush = logistics.ambushEngine().triggerAmbush(target, source.getLevel());
        if (ambush == null) {
            source.sendFailure(Component.literal("Failed to trigger bandit ambush."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bandit ambush " + ambush.ambushId()
            + " triggered on caravan " + target.caravanId() + " (" + ambush.banditUuids().size() + " outlaws spawned)!"), true);
        return 1;
    }

    private static int executeTradeShipTest(CommandSourceStack source, String routeId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Ship test requires a player."));
            return 0;
        }
        var engine = SimulationEngine.getInstance();
        var tradeRoutes = engine.getTradeRouteEngine();
        var logistics = engine.getLogisticsManager();

        BlockPos pPos = player.blockPosition();
        List<BlockPos> waterWaypoints = List.of(
            pPos.offset(10, 0, 0),
            pPos.offset(25, 0, 5),
            pPos.offset(40, 0, 10)
        );

        MerchantShipConvoy ship = tradeRoutes.materializeShipConvoy(routeId, pPos, waterWaypoints, source.getLevel(), logistics);
        if (ship == null) {
            source.sendFailure(Component.literal("Failed to deploy merchant ship convoy."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Deployed merchant ship convoy " + ship.convoyId()
            + " carrying value=" + Math.round(ship.cargoValue()) + " silver to destination port"), true);
        return 1;
    }

    private static int executeTradeLogistics(CommandSourceStack source) {
        var logistics = SimulationEngine.getInstance().getLogisticsManager();
        var caravans = logistics.getActiveCaravans();
        var ships = logistics.getActiveShips();
        var ambushes = logistics.ambushEngine().getActiveAmbushes();

        source.sendSuccess(() -> Component.literal("Physical Logistics Overview:"), false);
        source.sendSuccess(() -> Component.literal("  Caravans: " + caravans.size()
            + " | Ships: " + ships.size()
            + " | Ongoing Ambushes: " + ambushes.size()), false);

        for (CaravanEntityGroup c : caravans) {
            source.sendSuccess(() -> Component.literal("  - Caravan " + c.caravanId()
                + " [" + c.status() + "] route=" + c.routeId()
                + " animals=" + c.packAnimalUuids().size()
                + " guards=" + c.guardUuids().size()
                + " value=" + Math.round(c.cargoValue())), false);
        }
        for (MerchantShipConvoy s : ships) {
            source.sendSuccess(() -> Component.literal("  - Ship " + s.convoyId()
                + " [" + s.status() + "] from=" + s.originPort() + " to=" + s.destPort()
                + " value=" + Math.round(s.cargoValue())), false);
        }
        return 1;
    }

    private static int executeTradePrices(CommandSourceStack source, String civId) {
        var pricing = SimulationEngine.getInstance().getLogisticsManager().pricingEngine();
        int year = currentYear();
        String mark = projectedMark(year);
        source.sendSuccess(() -> Component.literal("Commodity Market Prices for " + civId + ":" + mark), false);
        if (!mark.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                "  (the price base is the recorded one; nothing here is a price of " + year + ")"), false);
        }
        for (var entry : pricing.getAllCommodities().entrySet()) {
            double price = pricing.calculateUnitPrice(entry.getKey(), civId, "external", 50.0D, 15.0D);
            source.sendSuccess(() -> Component.literal("  - " + entry.getKey() + ": " + price + " silver (category: " + entry.getValue().category() + ")"), false);
        }
        return 1;
    }

    private static int executeEconomyTreasury(CommandSourceStack source, String civId) {
        long treasury = SimulationEngine.getInstance().getEconomyEngine().getTreasury(civId, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Treasury " + civId + " = " + treasury
            + " bankrupt=" + SimulationEngine.getInstance().getEconomyEngine().isBankrupt(civId, WorldStateManager.getInstance())), false);
        return 1;
    }

    private static int executeEconomyAdd(CommandSourceStack source, String civId, int amount) {
        SimulationEngine.getInstance().getEconomyEngine().applyIncome(civId, amount, "command", WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Added " + amount + " to " + civId), true);
        return 1;
    }

    private static int executeEconomySubtract(CommandSourceStack source, String civId, int amount) {
        SimulationEngine.getInstance().getEconomyEngine().applyExpense(civId, amount, "command", WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Subtracted " + amount + " from " + civId), true);
        return 1;
    }

    private static int executeAgricultureFood(CommandSourceStack source, String regionId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        FoodData data = SimulationEngine.getInstance().getAgricultureEngine()
            .updateRegion(regionId, year, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Food " + regionId
            + " crop=" + data.currentCropType()
            + " production=" + data.production()
            + " demand=" + data.demand()
            + " security=" + String.format("%.2f", data.foodSecurity())), false);
        return 1;
    }

    private static int executeAgricultureBoost(CommandSourceStack source, String regionId) {
        SimulationEngine.getInstance().getAgricultureEngine().applyTechBoost(regionId, "irrigation");
        source.sendSuccess(() -> Component.literal("Applied agriculture boost to " + regionId), true);
        return 1;
    }

    private static int executeAgricultureClimate(CommandSourceStack source, String regionId, int modifierPercent) {
        double modifier = modifierPercent / 100.0d;
        SimulationEngine.getInstance().getAgricultureEngine().applyClimateEffect(regionId, modifier);
        source.sendSuccess(() -> Component.literal("Applied climate agriculture modifier " + modifier + " to " + regionId), true);
        return 1;
    }

    private static int executeClimateList(CommandSourceStack source) {
        var events = SimulationEngine.getInstance().getClimateEngine().events();
        source.sendSuccess(() -> Component.literal("Climate events registered by hand: " + events.size()), false);
        for (ClimateEvent event : events) {
            source.sendSuccess(() -> Component.literal("  - " + event.id()
                + " " + event.name()
                + " years=" + event.startYear() + ".." + event.endYear()
                + " type=" + event.type()
                + " temp=" + event.tempChangeDegrees()
                + " yield=" + event.yieldImpactPercent() + "%"), false);
        }
        // Step 35: the era table is the mod's climate now, so the listing has to show it.
        ClimatePeriods table = SimulationEngine.getInstance().getClimateEngine().periods();
        if (!table.loaded()) {
            source.sendSuccess(() -> Component.literal("Era table " + ClimatePeriods.RESOURCE
                + " not loaded: " + table.problem()), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Climate periods of every era: " + table.count()
            + " (the two events above are the fallback, and are not counted twice)"), false);
        for (ClimatePeriods.Period p : table.worst(10)) {
            source.sendSuccess(() -> Component.literal("  - " + p.id()
                + " " + p.name()
                + " " + p.span()
                + " type=" + p.type()
                + " temp=" + p.tempChange()
                + "C rain=" + p.precipChange() + "%"
                + " yield=" + p.yieldImpact() + "%"
                + " reach=" + (p.radiusKm() <= 0.0d ? "everywhere" : (int) p.radiusKm() + "km")
                + " " + (p.estimated() ? "estimated" : "recorded")), false);
        }
        return 1;
    }

    private static int executeClimateActive(CommandSourceStack source) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        var events = SimulationEngine.getInstance().getClimateEngine().getActiveClimateEvents(year);
        source.sendSuccess(() -> Component.literal("Active climate events in " + year + ": " + events.size()
            + " littleIceAge=" + SimulationEngine.getInstance().getClimateEngine().isLittleIceAgeActive(year)), false);
        for (ClimateEvent event : events) {
            source.sendSuccess(() -> Component.literal("  - " + event.id() + " " + event.name()), false);
        }
        // Step 35: the periods of this year. Only the world-wide ones can be named without a
        // place; ask for a region to get the regional ones, which are most of the table.
        ClimatePeriods table = SimulationEngine.getInstance().getClimateEngine().periods();
        if (!table.loaded()) {
            return 1;
        }
        var everywhere = table.activeEverywhere(year);
        source.sendSuccess(() -> Component.literal("Era periods reaching everywhere in " + year + ": "
            + everywhere.size() + " of " + table.count()), false);
        for (ClimatePeriods.Period p : everywhere) {
            source.sendSuccess(() -> Component.literal("  - " + p.id() + " " + p.name()
                + " (" + p.span() + ") temp=" + p.tempChange()
                + "C rain=" + p.precipChange() + "% yield=" + p.yieldImpact() + "%"), false);
        }
        return 1;
    }

    private static int executeClimateRegion(CommandSourceStack source, String regionId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        double temp = SimulationEngine.getInstance().getClimateEngine().getTemperatureModifier(regionId, year);
        double precip = SimulationEngine.getInstance().getClimateEngine().getPrecipModifier(regionId, year);
        String yield = WorldStateManager.getInstance().getFlag("climate.yield." + regionId);
        source.sendSuccess(() -> Component.literal("Climate " + regionId
            + " tempModifier=" + String.format("%.2f", temp)
            + "C precipModifier=" + String.format("%.2f", precip)
            + "% yieldModifier=" + (yield == null ? "not ticked" : yield)), false);
        // Step 35: which periods of the era this region is actually inside. The tick writes them
        // as a flag because only it knows where the region is.
        String ids = WorldStateManager.getInstance().getFlag("climate.periods." + regionId);
        ClimatePeriods table = SimulationEngine.getInstance().getClimateEngine().periods();
        if (ids == null || ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  periods: none recorded here in " + year
                + (table.loaded() ? "" : " (era table not loaded: " + table.problem() + ")")), false);
            return 1;
        }
        for (String id : ids.split(",")) {
            ClimatePeriods.Period p = table.byId(id.trim());
            if (p == null) {
                continue;
            }
            source.sendSuccess(() -> Component.literal("  period: " + p.name()
                + " (" + p.span() + ") " + p.type()
                + " temp=" + p.tempChange() + "C rain=" + p.precipChange()
                + "% yield=" + p.yieldImpact() + "%"
                + " " + (p.estimated() ? "estimated" : "recorded")), false);
        }
        return 1;
    }

    private static int executeClimateTemp(CommandSourceStack source, String regionId, double modifier) {
        SimulationEngine.getInstance().getClimateEngine().setRegionalTempModifier(regionId, modifier);
        WorldStateManager.getInstance().setFlag("climate.temp.manual." + regionId, Double.toString(modifier));
        source.sendSuccess(() -> Component.literal("Set regional temperature modifier for " + regionId + " = " + modifier + "C"), true);
        return 1;
    }

    private static int executeClimatePrecip(CommandSourceStack source, String regionId, double modifier) {
        SimulationEngine.getInstance().getClimateEngine().setRegionalPrecipModifier(regionId, modifier);
        WorldStateManager.getInstance().setFlag("climate.precip.manual." + regionId, Double.toString(modifier));
        source.sendSuccess(() -> Component.literal("Set regional precipitation modifier for " + regionId + " = " + modifier + "%"), true);
        return 1;
    }

    private static int executeFamineList(CommandSourceStack source) {
        var famines = SimulationEngine.getInstance().getAgricultureEngine().famineSystem().activeFamineRegions();
        if (famines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active famines"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Active famines: " + famines), false);
        return 1;
    }

    private static int executeFamineTrigger(CommandSourceStack source, String regionId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        SimulationEngine.getInstance().getAgricultureEngine().famineSystem()
            .triggerFamine(regionId, year, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Triggered famine in " + regionId), true);
        return 1;
    }

    private static int executeFamineEnd(CommandSourceStack source, String regionId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        SimulationEngine.getInstance().getAgricultureEngine().famineSystem()
            .endFamine(regionId, year, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Ended famine in " + regionId), true);
        return 1;
    }

    /**
     * Show the whole resolution chain for the player's current position and year.
     *
     * <p>This command exists because an approximated number is only useful if you can see how it
     * was approximated. It prints which tier answered and why, so a {@code floor} value can never
     * be mistaken for a census.
     */
    private static int executePopulationExplain(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Population explain requires a player location."));
            return 0;
        }
        var geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint(player.blockPosition());
        if (geo == null) {
            source.sendFailure(Component.literal("Could not convert position to a geo point."));
            return 0;
        }
        int year = com.eternalengine.clock.SimulationClockManager.isInitialized()
                ? com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear()
                : 0;
        return explainAt(source, geo.latitude(), geo.longitude(), year);
    }

    private static int executePopulationAt(CommandSourceStack source, double lat, double lon, int year) {
        return explainAt(source, lat, lon, year);
    }

    private static int explainAt(CommandSourceStack source, double lat, double lon, int year) {
        var civs = SimulationEngine.getInstance().getCivilizationManager();
        HistoricalPresence.Result presence = com.eternalengine.core.WorldQuery.presence(civs, com.eternalengine.core.WorldKey.at(lat, lon, year));

        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Population resolution at lat=%.5f lon=%.5f year=%d", lat, lon, year)), false);

        if (!presence.occupied()) {
            source.sendSuccess(() -> Component.literal(
                    "  unoccupied — no sourced site here in this year. No number is derived, "
                    + "because a population needs a place."), false);
            return 0;
        }

        String siteId = presence.site().map(h -> h.site().id()).orElse(null);
        SettlementRank rank = presence.rank();
        PopulationResolver.Result r = PopulationResolver.get().resolve(siteId, lat, lon, year, rank);

        source.sendSuccess(() -> Component.literal("  place:      "
                + (siteId == null ? "(no sourced site)" : siteId)
                + "  rank=" + rank.name().toLowerCase(java.util.Locale.ROOT)), false);
        source.sendSuccess(() -> Component.literal("  population: " + r.population()
                + "  [" + r.confidence().label().toUpperCase(java.util.Locale.ROOT) + "]"), false);
        source.sendSuccess(() -> Component.literal("  because:    " + r.source()), false);

        if (!r.confidence().isRecord()) {
            source.sendSuccess(() -> Component.literal(
                    "  note: derived, not a census. 'floor' means a plausible presence, not a count."), false);
        }

        PopulationResolver.get().polityPopulation(lat, lon, year).ifPresent(pol ->
                source.sendSuccess(() -> Component.literal("  polity:     " + pol.population()
                        + "  (" + pol.source() + ")"), false));

        return 1;
    }

    private static int executePopulationGet(CommandSourceStack source, String regionId) {
        long population = SimulationEngine.getInstance().getPopulationEngine()
            .getPopulation(regionId, WorldStateManager.getInstance());
        PopulationData data = SimulationEngine.getInstance().getPopulationEngine()
            .getOrCreate(regionId, population);
        int year = currentYear();
        String mark = projectedMark(year);
        source.sendSuccess(() -> Component.literal("Population " + regionId
            + " = " + data.current() + mark
            + " birthRate=" + String.format("%.3f", data.birthRate())
            + " deathRate=" + String.format("%.3f", data.deathRate())
            + " capacity=" + data.maxCapacity()), false);
        return 1;
    }

    private static int executePopulationSet(CommandSourceStack source, String regionId, int amount) {
        SimulationEngine.getInstance().getPopulationEngine().getOrCreate(regionId, amount).setCurrent(amount);
        WorldStateManager.getInstance().setPopulation(regionId, amount);
        source.sendSuccess(() -> Component.literal("Set population " + regionId + " = " + amount), true);
        return 1;
    }

    private static int executeMigrationProcess(CommandSourceStack source) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        java.util.List<MigrationFlow> flows = SimulationEngine.getInstance().getMigrationSystem()
            .processMigration(year, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Processed migration flows: " + flows.size()), true);
        flows.stream().limit(8).forEach(flow ->
            source.sendSuccess(() -> Component.literal("  " + flow.fromRegion() + " -> " + flow.toRegion()
                + " people/year=" + flow.peoplePerYear()), false));
        return 1;
    }

    private static int executeMigrationList(CommandSourceStack source) {
        java.util.List<MigrationFlow> flows = SimulationEngine.getInstance().getMigrationSystem().flows();
        if (flows.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No migration flows recorded"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Migration flows: " + flows.size()), false);
        flows.stream().limit(12).forEach(flow ->
            source.sendSuccess(() -> Component.literal("  " + flow.fromRegion() + " -> " + flow.toRegion()
                + " people/year=" + flow.peoplePerYear()
                + " push=" + String.format("%.2f", flow.pushFactor())
                + " pull=" + String.format("%.2f", flow.pullFactor())), false));
        return 1;
    }

    private static int executeClassTension(CommandSourceStack source, String civId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        SimulationEngine.getInstance().getClassSystem().updateDistribution(civId, year, year >= 1750 ? "INDUSTRIAL" : "PRE_INDUSTRIAL");
        double tension = SimulationEngine.getInstance().getClassSystem().getClassTension(civId);
        boolean risk = SimulationEngine.getInstance().getClassSystem().isRevolutionRisk(civId);
        source.sendSuccess(() -> Component.literal("Class tension " + civId + " = "
            + String.format("%.2f", tension) + " revolutionRisk=" + risk), false);
        return 1;
    }

    private static int executeUrbanRatio(CommandSourceStack source, String civId) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        SimulationEngine.getInstance().getUrbanizationSystem().updateUrbanization(civId, year, WorldStateManager.getInstance());
        double ratio = SimulationEngine.getInstance().getUrbanizationSystem().getUrbanRatio(civId);
        source.sendSuccess(() -> Component.literal("Urban ratio " + civId + " = "
            + String.format("%.2f", ratio)
            + " slumForming=" + SimulationEngine.getInstance().getUrbanizationSystem().isSlumForming(civId)), false);
        return 1;
    }

    private static int executeReligionList(CommandSourceStack source) {
        var religions = SimulationEngine.getInstance().getReligionEngine().allReligions();
        source.sendSuccess(() -> Component.literal("Religions: " + religions.size()), false);
        for (Religion religion : religions) {
            source.sendSuccess(() -> Component.literal("  - " + religion.id()
                + " " + religion.name()
                + " founded=" + religion.foundedYear()
                + " followers=" + religion.currentFollowers()
                + " power=" + String.format("%.1f", religion.politicalPower())), false);
        }
        return 1;
    }

    private static int executeReligionConvert(CommandSourceStack source, String civId, String religionId) {
        SimulationEngine.getInstance().getReligionEngine()
            .convertPopulation(civId, religionId, 1.0d, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Converted " + civId + " to religion " + religionId), true);
        return 1;
    }

    private static int executeReligionHolyWar(CommandSourceStack source, String civId, String targetCivId, String religionId) {
        boolean ok = SimulationEngine.getInstance().getReligionEngine()
            .declareHolyWar(civId, targetCivId, religionId, WorldStateManager.getInstance());
        if (!ok) {
            source.sendFailure(Component.literal("Holy war failed: religion missing or does not allow holy war."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Declared holy war flag " + civId + " -> " + targetCivId
            + " religion=" + religionId), true);
        return 1;
    }

    private static int executeReligionExcommunicate(CommandSourceStack source, String civId, String targetCivId) {
        boolean ok = SimulationEngine.getInstance().getReligionEngine()
            .excommunicate(civId, targetCivId, WorldStateManager.getInstance());
        if (!ok) {
            source.sendFailure(Component.literal("Excommunication failed: source civilization has no known religion."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Excommunicated " + targetCivId + " by authority of " + civId), true);
        return 1;
    }

    private static int executeTechList(CommandSourceStack source) {
        var technologies = SimulationEngine.getInstance().getTechnologyEngine().tree().all();
        source.sendSuccess(() -> Component.literal("Technologies: " + technologies.size()), false);
        for (Technology tech : technologies) {
            source.sendSuccess(() -> Component.literal("  - " + tech.id()
                + " " + tech.name()
                + " era=" + tech.era()
                + " level=" + tech.level()
                + " first known " + tech.firstLabel()
                + " prereq=" + tech.prerequisites()), false);
        }
        return 1;
    }

    private static int executeTechStatus(CommandSourceStack source, String civId) {
        var engine = SimulationEngine.getInstance().getTechnologyEngine();
        source.sendSuccess(() -> Component.literal("Technology status " + civId
            + " level=" + engine.getTechLevel(civId)
            + " era=" + engine.getTechEra(civId)
            + " unlocked=" + engine.getUnlockedTechs(civId)
            + " researchSpeed=" + String.format("%.2f", engine.calculateResearchSpeed(civId, WorldStateManager.getInstance()))), false);
        return 1;
    }

    private static int executeTechUnlock(CommandSourceStack source, String civId, String techId) {
        // Step 21: never before the first year it is known anywhere.
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        var engine = SimulationEngine.getInstance().getTechnologyEngine();
        var tech = engine.tree().get(techId).orElse(null);
        if (tech != null && !tech.knownIn(year)) {
            source.sendFailure(Component.literal("Nobody has " + tech.name() + " in " + (year < 0 ? -year + " BCE" : year)
                + ": it is first known in " + tech.firstLabel() + "."));
            return 0;
        }
        boolean ok = engine.unlockTechnology(civId, techId, WorldStateManager.getInstance(), year);
        if (!ok) {
            source.sendFailure(Component.literal("Technology unlock failed. Check tech id and prerequisites."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Unlocked technology " + techId + " for " + civId), true);
        return 1;
    }
    
    private static int executeCivList(CommandSourceStack source) {
        var manager = SimulationEngine.getInstance().getCivilizationManager();
        var ids = manager.ids();
        source.sendSuccess(() -> Component.literal("Civilizations: " + ids.size() + " " + ids), false);
        return 1;
    }

    private static int executeCivActive(CommandSourceStack source) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        var active = SimulationEngine.getInstance().getCivilizationManager().getActiveCivilizations(year);
        source.sendSuccess(() -> Component.literal("Active civilizations in " + year + ": " + active.size()), false);
        active.stream().limit(12).forEach(civ ->
            source.sendSuccess(() -> Component.literal("  - " + civ.id() + " " + civ.getCurrentName(year)), false));
        return 1;
    }
    
    /**
     * FINAL_TEST G2, in one place (marking board 13): a number that comes from the record and is shown
     * for a year past {@link com.eternalengine.clock.Timeline#RECORDED_UNTIL} is a projection, and has
     * to say so wherever a player can read it. Before this, {@code civ info}, {@code trade prices} and
     * {@code population get} answered identically at AD 1900 and AD 2500, with Rome "active" 2,000
     * years after its own {@code fallYear}.
     */
    private static String projectedMark(int year) {
        return com.eternalengine.clock.Timeline.projected(year)
                ? " [projected: the record stops at AD " + com.eternalengine.clock.Timeline.RECORDED_UNTIL
                  + ", this is carried forward]"
                : "";
    }

    /** The year the world stands in, from whichever clock is running. */
    private static int currentYear() {
        return SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
    }

    /** Whether a civilization stood in a given year, in words a player can act on. */
    private static String yearState(Civilization civ, int year) {
        if (year < civ.foundingYear()) {
            return "not yet founded (founded " + civ.foundingYear() + ")";
        }
        if (civ.fallYear() != 0 && year > civ.fallYear()) {
            return "had already ended (fell " + civ.fallYear() + ")";
        }
        return "standing";
    }

    private static int executeCivInfo(CommandSourceStack source, String civId) {
        var civOpt = SimulationEngine.getInstance().getCivilizationManager().getCivilization(civId);
        if (civOpt.isEmpty()) {
            var stateCiv = WorldStateManager.getInstance().getCivilization(civId);
            if (stateCiv == null) {
                source.sendFailure(Component.literal("Civilization not found: " + civId));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("CivilizationState: " + stateCiv.name + " (pop: " + stateCiv.population + ")"), false);
            return 1;
        }
        Civilization civ = civOpt.get();
        int year = currentYear();
        String mark = projectedMark(year);
        StringBuilder sb = new StringBuilder();
        sb.append("Civilization ").append(civ.id()).append(" - ").append(civ.name()).append("\n");
        sb.append("  currentName: ").append(civ.getCurrentName(year)).append("\n");
        sb.append("  founded: ").append(civ.foundingYear()).append(" fallYear: ").append(civ.fallYear())
          .append(" (the register's flag: ").append(civ.active() ? "active" : "inactive").append(")\n");
        // What a player asks is not "what does the register say" but "what is here in this year". The
        // two differ: Rome's register row is active and its fallYear is 476, and reading it out at
        // AD 2500 told a player Rome was standing 2,000 years after it fell (board 13).
        sb.append("  in ").append(year).append(": ").append(yearState(civ, year)).append("\n");
        sb.append("  capital: ").append(civ.capitalLat()).append(", ").append(civ.capitalLon()).append("\n");
        sb.append("  population: ").append(civ.population()).append(mark)
          .append(" treasury: ").append(civ.treasury()).append(mark)
          .append(" tech: ").append(civ.technologyLevel()).append(mark).append("\n");
        if (!mark.isEmpty()) {
            sb.append("  the numbers above are the register's last recorded values, carried forward; "
                    + "they are not a measurement of ").append(year).append("\n");
        }
        sb.append("  territories: ").append(civ.territoryIds());
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeCivCreate(CommandSourceStack source, String id, String name) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        Civilization civ = new Civilization(id, name, year, 0.0d, 0.0d);
        civ.setPopulation(Math.max(1000L, WorldStateManager.getInstance().getPopulation(id)));
        civ.setTreasury(WorldStateManager.getInstance().getTreasury(id));
        SimulationEngine.getInstance().getCivilizationManager().addCivilization(civ, WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Created civilization " + id + " name=" + name), true);
        return 1;
    }

    private static int executeRelationScore(CommandSourceStack source, String civ1, String civ2) {
        var data = SimulationEngine.getInstance().getFactionRelationshipSystem().getRelationship(civ1, civ2);
        source.sendSuccess(() -> Component.literal("Relationship " + civ1 + " / " + civ2
            + " type=" + data.type() + " score=" + data.score()), false);
        return 1;
    }

    private static int executeRelationSet(CommandSourceStack source, String civ1, String civ2, String type) {
        RelationshipType rel;
        try {
            rel = RelationshipType.valueOf(type.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Unknown relationship type: " + type));
            return 0;
        }
        SimulationEngine.getInstance().getFactionRelationshipSystem().setRelationship(civ1, civ2, rel);
        WorldStateManager.getInstance().setRelation(civ1, civ2, rel == RelationshipType.WAR ? -100 : rel == RelationshipType.ALLIED ? 100 : 0);
        source.sendSuccess(() -> Component.literal("Set relationship " + civ1 + " / " + civ2 + " = " + rel), true);
        return 1;
    }

    private static int executeTerritoryOwner(CommandSourceStack source, double lat, double lon) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        var owner = SimulationEngine.getInstance().getCivilizationManager().territoryManager().getOwner(lat, lon, year);
        source.sendSuccess(() -> Component.literal("Territory owner at " + lat + ", " + lon + " = " + owner.orElse("none")), false);
        return 1;
    }

    private static int executeTerritoryCreateTest(CommandSourceStack source, String territoryId, String civId) {
        java.util.List<double[]> square = java.util.List.of(
            new double[] {-1.0d, -1.0d},
            new double[] {-1.0d, 1.0d},
            new double[] {1.0d, 1.0d},
            new double[] {1.0d, -1.0d}
        );
        TerritoryManager.TerritoryPolygon territory = new TerritoryManager.TerritoryPolygon(territoryId, civId, square, 0.0d, 0.0d);
        SimulationEngine.getInstance().getCivilizationManager().territoryManager().addTerritory(territory);
        SimulationEngine.getInstance().getCivilizationManager().getCivilization(civId).ifPresent(civ -> civ.addTerritory(territoryId));
        source.sendSuccess(() -> Component.literal("Created test territory " + territoryId + " owner=" + civId + " bounds=[-1,1]"), true);
        return 1;
    }
    
    private static int executeVipList(CommandSourceStack source) {
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        var available = SimulationEngine.getInstance().getVipSystem()
            .getAvailableVips(year, WorldStateManager.getInstance(), source.getServer());
        source.sendSuccess(() -> Component.literal("Available VIPs in " + year + ": " + available.size()), false);
        available.stream().limit(12).forEach(vip ->
            source.sendSuccess(() -> Component.literal("  - " + vip.vipId() + " " + vip.name()
                + " role=" + vip.roleType() + " authority=" + vip.authorityLevel()), false));
        return 1;
    }
    
    private static int executeVipInfo(CommandSourceStack source, String vipId) {
        var vip = SimulationEngine.getInstance().getVipSystem().getVip(vipId);
        if (vip.isEmpty()) {
            source.sendFailure(Component.literal("VIP not found: " + vipId));
            return 0;
        }
        VipSlot slot = vip.get();
        StringBuilder sb = new StringBuilder();
        sb.append("VIP ").append(slot.vipId()).append(" - ").append(slot.name()).append("\n");
        sb.append("  civ: ").append(slot.civilizationId()).append(" role: ").append(slot.roleType()).append(" authority: ").append(slot.authorityLevel()).append("\n");
        sb.append("  years: ").append(slot.birthYear()).append(" to ").append(slot.historicalDeathYear()).append(" age: ").append(slot.age()).append("\n");
        sb.append("  spawn: ").append(slot.spawnLat()).append(", ").append(slot.spawnLon()).append("\n");
        sb.append("  claimed: ").append(slot.isClaimedByPlayer()).append(" dead: ").append(slot.dead()).append("\n");
        sb.append("  commands: ").append(slot.commandsAvailable());
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
    
    private static int executeVipClaim(CommandSourceStack source, String vipId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player"));
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        boolean claimed = SimulationEngine.getInstance().getVipSystem().claimVip(player, vipId, year);
        if (!claimed) {
            source.sendFailure(Component.literal("VIP claim failed: unavailable, already claimed, or player already controls a VIP."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Claimed VIP: " + vipId), true);
        return 1;
    }
    
    private static int executeVipRelease(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player"));
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        if (!data.isVip()) {
            source.sendFailure(Component.literal("You are not a VIP"));
            return 0;
        }
        String vipId = data.getVipId();
        SimulationEngine.getInstance().getVipSystem().releaseVip(player, year);
        source.sendSuccess(() -> Component.literal("Released VIP: " + vipId), true);
        return 1;
    }

    private static int executeVipTakeoverTest(CommandSourceStack source, String vipId, String methodName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player"));
            return 0;
        }
        var slot = SimulationEngine.getInstance().getVipSystem().getVip(vipId);
        if (slot.isEmpty()) {
            source.sendFailure(Component.literal("VIP not found: " + vipId));
            return 0;
        }
        VipTakeoverMethod method;
        try {
            method = VipTakeoverMethod.valueOf(methodName.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Unknown takeover method. Use DUEL, BATTLE, ASSASSINATION, MURDER, or UNKNOWN."));
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        VipSlot vip = slot.get();
        int tier = vip.authorityLevel() >= 8 ? 1 : 2;
        boolean religious = vip.roleType().name().contains("RELIGIOUS");
        boolean beloved = vip.personality().getOrDefault("mercy", 0) >= 70;
        VipBotRecord record = new VipBotRecord(vip.vipId(), vip.name(), tier, true, true, beloved, religious);
        VipTakeoverContext context = new VipTakeoverContext(player.getUUID().toString(), vip.vipId(), method, source.hasPermission(2), year);
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        VipTakeoverResult result = SimulationEngine.getInstance().getVipTakeoverSystem()
            .attemptTakeover(data, player.getUUID().toString(), record, context);
        if (!result.success()) {
            source.sendFailure(Component.literal("VIP takeover failed: " + result.reason()));
            return 0;
        }
        SimulationEngine.getInstance().getVipSystem().assignAfterTakeover(player, vip.vipId(), year, result.appliedReputationAction());
        source.sendSuccess(() -> Component.literal("VIP takeover succeeded: " + result.reason()
            + " reputationAction=" + result.appliedReputationAction()), true);
        return 1;
    }

    private static int executeVipKill(CommandSourceStack source, String vipId, String cause) {
        var slot = SimulationEngine.getInstance().getVipSystem().getVip(vipId);
        if (slot.isEmpty()) {
            source.sendFailure(Component.literal("VIP not found: " + vipId));
            return 0;
        }
        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : WorldStateManager.getInstance().getCurrentYear();
        SimulationEngine.getInstance().getVipSystem().onVipDeath(slot.get(), cause, year);
        source.sendSuccess(() -> Component.literal("Killed VIP " + vipId + " cause=" + cause), true);
        return 1;
    }

    private static int executeVipSuccessors(CommandSourceStack source, String vipId) {
        var slot = SimulationEngine.getInstance().getVipSystem().getVip(vipId);
        if (slot.isEmpty()) {
            source.sendFailure(Component.literal("VIP not found: " + vipId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Successors for " + vipId + ": " + slot.get().successorVipIds()), false);
        return 1;
    }

    private static int executeVipTagSelf(CommandSourceStack source, String vipId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player entity to tag."));
            return 0;
        }
        var vip = SimulationEngine.getInstance().getVipSystem().getVip(vipId);
        boolean tagged = vip.isPresent()
            ? VipEntityHelper.tagVipEntity(player, vip.get())
            : VipEntityHelper.tagVipEntity(player, vipId, vipId);
        source.sendSuccess(() -> Component.literal("VIP tag self result=" + tagged
            + " tag=" + VipEntityHelper.getVipId(player)), true);
        return tagged ? 1 : 0;
    }

    private static int executeVipClearSelfTag(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player entity."));
            return 0;
        }
        boolean removed = VipEntityHelper.clearVipTag(player);
        source.sendSuccess(() -> Component.literal("VIP tag removed=" + removed), true);
        return 1;
    }

    private static int executeVillagerNearby(CommandSourceStack source, int radius) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Villager nearby requires a player."));
            return 0;
        }
        var villagers = SimulationEngine.getInstance().getVillagerManager()
            .getVillagersNearby(player.blockPosition(), radius, source.getLevel());
        source.sendSuccess(() -> Component.literal("Villagers nearby radius=" + radius + ": " + villagers.size()), false);
        // Step R2-d2 (2026-10-24): the list used to say who a person was and nothing about where they
        // belonged, so a town could only be judged by its nameplates. Each line now names the
        // civilization, and says which kind of answer it is: a pack record (with its money, goods and
        // engines), a polity the ruler register can name and date but has no record for, or an id
        // nobody can resolve at all. That is the difference between "looks right" and "can be counted".
        var civs = SimulationEngine.getInstance().getCivilizationManager();
        int recorded = 0;
        int namedOnly = 0;
        int unresolved = 0;
        for (var entity : villagers) {
            String civId = null;
            if (entity instanceof com.eternalengine.entity.HumanNpcEntity npc) {
                civId = npc.getCivilizationId();
            } else {
                var data = com.eternalengine.capability.EternalEngineCapabilities.getEntityData(entity);
                if (data != null) {
                    civId = data.getCivilizationId();
                }
            }
            String civ = com.eternalengine.civilization.PolityFacts.shortName(civs, civId);
            if (com.eternalengine.civilization.PolityFacts.hasRecord(civs, civId)) {
                recorded++;
            } else if (com.eternalengine.civilization.PolityFacts.name(civId) != null) {
                namedOnly++;
            } else if (civId != null && !civId.isBlank() && !"neutral".equals(civId)) {
                unresolved++;
            }
        }
        int recordedFinal = recorded;
        int namedOnlyFinal = namedOnly;
        int unresolvedFinal = unresolved;
        source.sendSuccess(() -> Component.literal("  civilizations: " + recordedFinal + " with a record, "
            + namedOnlyFinal + " named from the ruler register (*), " + unresolvedFinal + " nobody can resolve (?)"), false);
        villagers.stream().limit(12).forEach(entity -> {
            String civId = null;
            if (entity instanceof com.eternalengine.entity.HumanNpcEntity npc) {
                civId = npc.getCivilizationId();
            } else {
                var data = com.eternalengine.capability.EternalEngineCapabilities.getEntityData(entity);
                if (data != null) {
                    civId = data.getCivilizationId();
                }
            }
            String civ = com.eternalengine.civilization.PolityFacts.shortName(civs, civId);
            source.sendSuccess(() -> Component.literal("  - " + entity.getUUID()
                + " name=" + entity.getName().getString()
                + " civ=" + civ
                + " vip=" + VipEntityHelper.getVipId(entity)), false);
        });
        return 1;
    }

    private static int executeVillagerVirtualCount(CommandSourceStack source) {
        var virtuals = SimulationEngine.getInstance().getVillagerManager().virtualizer().virtualStore();
        source.sendSuccess(() -> Component.literal("Virtual villagers: " + virtuals.size()), false);
        virtuals.values().stream().limit(12).forEach(v ->
            source.sendSuccess(() -> Component.literal("  - " + v.entityUuid()
                + " " + v.displayName()
                + " civ=" + v.civId()
                + " role=" + v.roleType()
                + " health=" + v.health()), false));
        return 1;
    }

    private static int executeVillagerExplain(CommandSourceStack source, String roleName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Villager explain requires a player location."));
            return 0;
        }
        BotAffairsRole role;
        try {
            role = BotAffairsRole.valueOf(roleName.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            role = BotAffairsRole.VILLAGER;
        }
        String text = SimulationEngine.getInstance().getVillagerBehaviorSystem()
            .explainCurrentAffairs("command_villager", "Villager", "neutral", role, source.getLevel(), player.blockPosition());
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int executeVillagerSpawnTest(CommandSourceStack source, String civId, String role) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Villager spawn-test requires a player."));
            return 0;
        }
        java.util.UUID uuid = SimulationEngine.getInstance().getVillagerManager()
            .spawnVillager(civId, role, player.blockPosition(), source.getLevel());
        source.sendSuccess(() -> Component.literal("Spawned test villager uuid=" + uuid
            + " civ=" + civId + " role=" + role
            + " (debug: ignores year/location — use /ee villager here)"), true);
        return uuid == null ? 0 : 1;
    }

    private static HistoricalPresence.Result presenceAtPlayer(CommandSourceStack source, ServerPlayer player) {
        int year = SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
        var geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint(player.blockPosition());
        double lat = geo.latitude();
        double lon = geo.longitude();
        return com.eternalengine.core.WorldQuery.presence(SimulationEngine.getInstance().getCivilizationManager(),
                new com.eternalengine.core.WorldKey(lat, lon, year, 1, 1, com.eternalengine.core.WorldKey.activeTimeline(), com.eternalengine.core.WorldKey.universeOf(player.level())));
    }

    private static int executeVillagerWho(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Villager who requires a player location."));
            return 0;
        }
        HistoricalPresence.Result result = presenceAtPlayer(source, player);
        for (String line : HistoricalPresence.describe(result).split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        // step 26 (A1): the key this answer was asked with
        String keyLine = "  key: " + new com.eternalengine.core.WorldKey(result.lat(), result.lon(), result.year(), 1, 1,
                com.eternalengine.core.WorldKey.activeTimeline(),
                com.eternalengine.core.WorldKey.universeOf(player.level())).display();
        source.sendSuccess(() -> Component.literal(keyLine), false);
        if (result.occupied()) {
            String siteId = result.site().map(h -> h.site().id()).orElse(null);
            PopulationResolver.Result pop = PopulationResolver.get().resolve(
                    siteId, result.lat(), result.lon(), result.year(), result.rank());
            source.sendSuccess(() -> Component.literal("  population: " + pop.population()
                    + " [" + pop.confidence().label() + "] — " + pop.source()), false);
        }
        if (SimulationEngine.getInstance().getCivilizationManager().ids().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "  (civ table empty — /ee data load after /ee data install)"), false);
        }
        return result.occupied() ? 1 : 0;
    }

    private static int executeVillagerHere(CommandSourceStack source, String role) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Villager here requires a player."));
            return 0;
        }
        HistoricalPresence.Result result = presenceAtPlayer(source, player);
        for (String line : HistoricalPresence.describe(result).split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (!result.occupied()) {
            source.sendFailure(Component.literal(
                    "No NPC spawned: no sourced village/city here in year "
                            + result.year() + ". spawn-test can still force one."));
            return 0;
        }
        String civId = result.spawnCivId();
        SettlementRank rank = result.rank();
        String[] roles = result.spawnRoles();
        if (role != null && !role.isBlank() && !"farmer".equalsIgnoreCase(role)) {
            roles = new String[] {role};
        }
        var manager = SimulationEngine.getInstance().getVillagerManager();
        int spawned = 0;
        for (int i = 0; i < roles.length; i++) {
            // Same rule the ambient spawner follows, i.e. vanilla's: not within 24 blocks of the
            // player, within the spawn distance, on ground that can hold somebody. `here` means
            // this place, which is not the same as this square metre.
            net.minecraft.core.BlockPos pos = com.eternalengine.villager.PresenceLocalSpawner
                    .spawnPlacement(source.getLevel(), player);
            if (pos == null) {
                continue;
            }
            java.util.UUID uuid = manager.spawnVillager(civId, roles[i], pos, source.getLevel());
            if (uuid != null) {
                spawned++;
            }
        }
        int spawnedFinal = spawned;
        String rankName = rank.name().toLowerCase(java.util.Locale.ROOT);
        // `roles` is reassigned above when a single role was asked for, so it cannot be captured by
        // the lambda below -- a lambda may only read a local that is never reassigned. Copy it to a
        // variable that is. (`spawnedFinal` above exists for exactly the same reason.)
        String[] rolesFinal = roles;
        source.sendSuccess(() -> Component.literal("Spawned " + spawnedFinal + " NPC(s) civ=" + civId
                + " rank=" + rankName + " roles=" + String.join(",", rolesFinal)), true);
        return spawnedFinal;
    }

    private static int executeVillagerVipSpawnTest(CommandSourceStack source, String vipId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("VIP villager spawn-test requires a player."));
            return 0;
        }
        var vip = SimulationEngine.getInstance().getVipSystem().getVip(vipId);
        if (vip.isEmpty()) {
            source.sendFailure(Component.literal("VIP not found: " + vipId));
            return 0;
        }
        java.util.UUID uuid = SimulationEngine.getInstance().getVillagerManager()
            .spawnVipVillager(vip.get(), player.blockPosition(), source.getLevel());
        source.sendSuccess(() -> Component.literal("Spawned VIP-tagged villager uuid=" + uuid
            + " vip=" + vipId), true);
        return uuid == null ? 0 : 1;
    }
    
    private static int executeAchievements(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Achievements require a player."));
            return 0;
        }
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        source.sendSuccess(() -> Component.literal("Achievement foundation status: tutorial step="
            + data.getTutorialStep() + " complete=" + data.isTutorialComplete()
            + " recentActions=" + data.getRecentActions(5).size()), false);
        return 1;
    }

    private static int executeActionsRecent(CommandSourceStack source) {
        java.util.List<PlayerAction> actions = PlayerActionDetector.divergenceDetector().recentActions(12);
        if (actions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No recent player actions recorded."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Recent player actions: " + actions.size()), false);
        for (PlayerAction action : actions) {
            source.sendSuccess(() -> Component.literal("  - " + action.year() + " "
                + action.playerName() + " " + action.type()
                + " @ " + action.blockX() + "," + action.blockY() + "," + action.blockZ()
                + " " + action.details()), false);
        }
        return 1;
    }

    private static int executeDivergenceChecks(CommandSourceStack source) {
        java.util.List<DivergenceDetector.DivergenceCheck> checks = PlayerActionDetector.divergenceDetector().checks();
        if (checks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No divergence checks recorded."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Divergence checks: " + checks.size()), false);
        for (DivergenceDetector.DivergenceCheck check : checks) {
            source.sendSuccess(() -> Component.literal("  - " + check.eventId()
                + " confidence=" + String.format("%.2f", check.confidence())
                + " actions=" + check.playerActions()), false);
        }
        return 1;
    }

    private static int executeDivergenceConfidence(CommandSourceStack source, String eventId) {
        var event = SimulationEngine.getInstance().getHistoricalEventDatabase().getEvent(eventId);
        if (event.isEmpty()) {
            source.sendFailure(Component.literal("Historical event not found: " + eventId));
            return 0;
        }
        double confidence = PlayerActionDetector.divergenceDetector()
            .calculatePreventionConfidence(event.get(), WorldStateManager.getInstance());
        source.sendSuccess(() -> Component.literal("Prevention confidence for " + eventId
            + " = " + String.format("%.2f", confidence)), false);
        return 1;
    }

    private static int executeDivergenceMark(CommandSourceStack source, String eventId, int confidencePercent) {
        double confidence = Math.max(0.0d, Math.min(1.0d, confidencePercent / 100.0d));
        PlayerActionDetector.divergenceDetector().markEventCandidate(eventId, confidence,
            java.util.List.of("manual command by " + source.getTextName()));
        WorldStateManager.getInstance().setFlag("event.prevented_candidate." + eventId, Double.toString(confidence));
        source.sendSuccess(() -> Component.literal("Marked divergence candidate " + eventId
            + " confidence=" + String.format("%.2f", confidence)), true);
        return 1;
    }
    
    // ========== VIP COMMANDS ==========
    
    private static int executeObserver(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            // It used to return 0 and nothing else: the command surface run of 2026-10-23 found two
            // commands a player could type on the console and get no answer at all.
            source.sendFailure(Component.literal("This command requires a player."));
            return 0;
        }
        
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        boolean newState = !data.isObserver();
        data.setObserver(newState);
        
        source.sendSuccess(() -> Component.literal("Observer mode: " + (newState ? "ON" : "OFF")), true);
        return 1;
    }
    
    private static int executeSpeedStatus(CommandSourceStack source) {
        SimulationClockManager clock = SimulationClockManager.getInstance();
        source.sendSuccess(() -> Component.literal(String.format(
            "Speed: %dx (base: %dx), Slowdown: %s, VIP Lock: %s, Override: %s",
            clock.getEffectiveSpeed(), clock.getBaseSpeed(),
            clock.isSlowdownActive(), clock.isVipLockActive(), clock.isOverrideActive()
        )), false);
        // Step 14: which clock runs and how fast, why history stands still, which event slows it.
        source.sendSuccess(() -> Component.literal(clock.describeMode()), false);
        String why = SimulationEngine.isInitialized() ? SimulationEngine.getInstance().pauseReason() : null;
        source.sendSuccess(() -> Component.literal(why == null ? "History is running." : "History is " + why + "."), false);
        List<String> slow = clock.describeSlowdowns();
        if (!slow.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Slowed for: " + String.join(", ", slow)), false);
        }
        if (clock.isTerraTime()) {
            source.sendSuccess(() -> Component.literal("In Terra++ time the event slowdowns do not apply: the year is the"
                + " day count times the Terra++ scale. /ee speed set and VIP votes change that scale and keep the year."), false);
        }
        return 1;
    }
    
    private static int executeSpeedVote(CommandSourceStack source, int speed) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player."));
            return 0;
        }
        
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        if (!data.isVip()) {
            source.sendFailure(Component.literal("Only VIP players can vote on speed"));
            return 0;
        }
        
        SimulationClockManager.VoteOutcome outcome = SimulationClockManager.getInstance().processVote(player, speed);
        if (!outcome.accepted()) {
            source.sendFailure(Component.literal("Vote not counted: " + outcome.problem()));
            return 0;
        }
        if (outcome.winner() != null) {
            source.sendSuccess(() -> Component.literal("Vote passed: history now runs at " + outcome.winner() + "x"), true);
            return 1;
        }
        StringBuilder tally = new StringBuilder();
        outcome.tally().forEach((v, n) -> tally.append(tally.length() == 0 ? "" : ", ").append(v).append("x: ").append(n));
        source.sendSuccess(() -> Component.literal("Voted for " + speed + "x. Votes: " + tally + ". A speed needs "
            + outcome.needed() + " of the " + outcome.eligible() + " VIP player(s) online."), true);
        return 1;
    }
    
    private static int executeOverride(CommandSourceStack source) {
        int baseSpeed = SimulationClockManager.getInstance().getBaseSpeed();
        SimulationClockManager.getInstance().activateOverride(baseSpeed, 10L * 60L * 1000L);
        source.sendSuccess(() -> Component.literal("Activated speed override at base speed " + baseSpeed + "x for 10 minutes if no unoverridable lock is active."), true);
        return 1;
    }
    
    private static int executeSlowdown(CommandSourceStack source) {
        SimulationClockManager clock = SimulationClockManager.getInstance();
        source.sendSuccess(() -> Component.literal("Slowdown active=" + clock.isSlowdownActive()
            + " effectiveSpeed=" + clock.getEffectiveSpeed()
            + " baseSpeed=" + clock.getBaseSpeed()
            + ". Use /ee override to temporarily restore base speed where allowed."), false);
        return 1;
    }
    
    // ========== ADMIN COMMANDS ==========
    
    private static int executeSpeedSet(CommandSourceStack source, int speed) {
        SimulationClockManager clock = SimulationClockManager.getInstance();
        if (!clock.setBaseSpeed(speed)) {
            source.sendFailure(Component.literal("Speed must be one of "
                + java.util.Arrays.toString(SimulationClockManager.VALID_SPEEDS) + "; it stays " + clock.getBaseSpeed() + "x"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Base speed set to " + clock.getBaseSpeed() + "x. " + clock.describeMode()), true);
        return 1;
    }
    
    private static int executeYearSet(CommandSourceStack source, int year) {
        if (!onlyForward(source, year)) {
            return 0;
        }
        SimulationClockManager.getInstance().setCurrentYear(year);
        String yearStr = year < 0 ? Math.abs(year) + " BCE" : year + " CE";
        source.sendSuccess(() -> Component.literal("Year set to " + yearStr), true);
        // Board 17: moving the clock past a civilization's fall must not leave its people standing in
        // the world until the next minute's pass. Everyone loaded is asked again, now.
        if (source.getLevel() != null) {
            com.eternalengine.civilization.WhoBelongsHere.reconcileAll(source.getLevel(), year);
        }
        // Step 62: the world answers at once where you stand (a player has a where; the console has only a year).
        if (source.getEntity() instanceof ServerPlayer player) {
            try {
                double[] geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeo(player.blockPosition());
                if (geo != null) {
                    final String hereLine =
                        com.eternalengine.here.HereAndNow.read(geo[1], geo[0], year).shortLine();
                    source.sendSuccess(() -> Component.literal(hereLine), true);
                }
            } catch (Exception e) {
                LOGGER.warn("[Commands] here line after year set failed: {}", e.getMessage());
            }
        }
        return 1;
    }
    
    /**
     * Step 18d: the date only goes back with /ee date (the user's rule). /ee year set and /ee morph jump go forward:
     * a later year. False (and says so) for this year or an earlier one.
     */
    private static boolean onlyForward(CommandSourceStack source, int year) {
        if (!SimulationClockManager.isInitialized()) {
            return true;
        }
        int now = SimulationClockManager.getInstance().getCurrentYear();
        if (year > now) {
            return true;
        }
        String today = com.eternalengine.knowledge.TalkService.dateLabel(com.eternalengine.knowledge.KnowledgeDate.today());
        String yearText = year < 0 ? Math.abs(year) + " BCE" : String.valueOf(year);
        source.sendFailure(Component.literal(year == now
            ? "It is already " + yearText + " (today is " + today + "). To go to a day in it: /ee date <year> <month> <day>."
            : "Only /ee date moves the date back: /ee date " + year + " 1 1 goes to 1 January " + yearText
                + ". Today is " + today + "."));
        return false;
    }

    private static int executeDebug(CommandSourceStack source, String system) {
        String key = system == null ? "status" : system.toLowerCase(java.util.Locale.ROOT);
        String message = switch (key) {
            case "status", "engine" -> SimulationEngine.getInstance().getStatusSummary();
            case "async" -> AsyncTaskManager.getInstance().getStatistics();
            case "timeline" -> "active=" + SimulationEngine.getInstance().getTimelineManager().activeBranchId()
                + " divergences=" + SimulationEngine.getInstance().getTimelineManager().getDivergencePoints().size();
            case "events" -> "loaded=" + SimulationEngine.getInstance().getHistoricalEventDatabase().getSize()
                + " triggered=" + SimulationEngine.getInstance().getEventChainEngine().getTriggeredEvents().size()
                + " prevented=" + SimulationEngine.getInstance().getEventChainEngine().getPreventedEvents().size();
            case "war" -> "wars=" + SimulationEngine.getInstance().getWarEngine().getActiveWars().size()
                + " armies=" + SimulationEngine.getInstance().getWarEngine().armyManager().size();
            case "disease" -> "outbreaks=" + SimulationEngine.getInstance().getDiseaseEngine().getActiveDiseases().size();
            case "trade" -> "routes=" + SimulationEngine.getInstance().getTradeRouteEngine().getActiveRoutes().size();
            case "population" -> "regions=" + SimulationEngine.getInstance().getPopulationEngine().regionPopulations().size()
                + " migrations=" + SimulationEngine.getInstance().getMigrationSystem().flows().size();
            case "civ" -> "civilizations=" + SimulationEngine.getInstance().getCivilizationManager().ids().size()
                + " territories=" + SimulationEngine.getInstance().getCivilizationManager().territoryManager().size();
            case "brain" -> "available=" + SimulationEngine.getInstance().getBrainBridge().isAvailable()
                + " queue=" + SimulationEngine.getInstance().getServerDecisionQueue().size();
            default -> "Unknown debug system '" + system + "'. Try status, async, timeline, events, war, disease, trade, population, civ, brain.";
        };
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
    
    private static int executePerformance(CommandSourceStack source) {
        String report = PerformanceMonitor.getInstance().getPerformanceReport();
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }
    
    /**
     * {@code /ee reload} (step 14; it used to print "Configuration reloaded" and do nothing): the server
     * config's intervals, the world's data files (brought up to date with this mod version, edits kept), the
     * civilizations, events, VIPs, buildings, names, population packs and stories.
     */
    private static int executeReload(CommandSourceStack source) {
        var server = source.getServer();
        if (!SimulationEngine.isInitialized() || server == null) {
            source.sendFailure(Component.literal("The simulation is not running"));
            return 0;
        }
        SimulationEngine engine = SimulationEngine.getInstance();
        StringBuilder out = new StringBuilder("Reloaded:");
        boolean ok = true;
        try {
            // The config files from disk first (Forge's watcher misses an editor that saves by replacing the file).
            java.util.Map<String, Integer> before = new java.util.LinkedHashMap<>();
            var scheduler = com.eternalengine.core.SimulationTickScheduler.getInstance();
            for (String id : scheduler.getScheduledSystems()) {
                before.put(id, scheduler.getBaseInterval(id));
            }
            List<String> files = com.eternalengine.core.ConfigReloadListener.reloadFromDisk();
            List<String> changed = new java.util.ArrayList<>();
            for (var e : before.entrySet()) {
                int now = scheduler.getBaseInterval(e.getKey());
                if (now != e.getValue()) {
                    changed.add(e.getKey() + " " + e.getValue() + "->" + now);
                }
            }
            out.append("\n  config: ").append(String.join("; ", files))
                .append(changed.isEmpty() ? "; intervals unchanged" : "; intervals (ticks) " + String.join(", ", changed));
        } catch (Throwable t) {
            ok = false;
            out.append("\n  config: FAILED ").append(t);
        }
        try {
            var data = FoundationDataInstaller.refreshAtStart(server);
            out.append("\n  world data files: ").append(data == null ? "no world" : data.summary());
            FoundationDataLoader.LoadResult loaded = FoundationDataLoader.loadInstalled(server, engine, false);
            out.append("\n  ").append(loaded.summary())
                .append(" (VIPs are read when the world starts: reading them now would drop the current claims)");
            PopulationResolver.get().loadFromDirectory(EternalDataPaths.population(server));
            out.append("\n  population packs reloaded");
            com.eternalengine.timeline.ScenarioRegistry scenarios = new com.eternalengine.timeline.ScenarioRegistry();
            int stories = scenarios.loadFromDirectory(EternalDataPaths.scenarios(server));
            engine.getTimelineManager().setScenarios(scenarios);
            out.append("\n  stories: ").append(stories).append(" loaded")
                .append(scenarios.loadErrors().isEmpty() ? "" : ", problems: " + scenarios.loadErrors());
        } catch (Throwable t) {
            ok = false;
            out.append("\n  data: FAILED ").append(t);
        }
        try {
            // Step 19b: the money and goods tables (from the mod jar) are read again too.
            var money = com.eternalengine.money.MoneyTable.reload();
            var goods = com.eternalengine.money.GoodsTable.reload();
            // Step 19c: the product cards; step 19d: the crier forgets his lines.
            var products = com.eternalengine.money.ShopService.reloadProducts();
            com.eternalengine.news.NewsService.forgetCalls();
            // Product atlas, part B: the world product list.
            var atlas = com.eternalengine.money.ProductAtlas.reload();
            var ms = money.summary();
            var gs = goods.summary();
            List<String> problems = new java.util.ArrayList<>(money.problems());
            problems.addAll(goods.problems());
            problems.addAll(com.eternalengine.money.ShopService.productProblems());
            problems.addAll(atlas.problems());
            out.append("\n  money table: ").append(ms.monies()).append(" kinds of money, ").append(ms.rows())
                .append(" rows, ").append(ms.rates()).append(" rates; goods: ").append(gs.goods()).append(" (")
                .append(gs.withItems()).append(" with items); product cards: ").append(products.size())
                .append("; product atlas: ").append(atlas.summary())
                .append(problems.isEmpty() ? "" : "; problems: " + String.join("; ", problems));
            ok &= problems.isEmpty();
        } catch (Throwable t) {
            ok = false;
            out.append("\n  money: FAILED ").append(t);
        }
        String text = out.toString();
        if (ok) {
            source.sendSuccess(() -> Component.literal(text), true);
        } else {
            source.sendFailure(Component.literal(text));
        }
        return ok ? 1 : 0;
    }

    /** {@code /ee pause} and {@code /ee resume} (step 14). */
    private static int executePause(CommandSourceStack source, boolean pause) {
        if (!SimulationEngine.isInitialized() || !SimulationEngine.getInstance().setOperatorPause(pause)) {
            source.sendFailure(Component.literal("The simulation is not running"));
            return 0;
        }
        SimulationEngine engine = SimulationEngine.getInstance();
        String why = engine.pauseReason();
        String terra = SimulationClockManager.getInstance().isTerraTime() && pause
            ? " In Terra++ time the year is the Minecraft day count, so the day/night cycle is paused too." : "";
        String text = pause
            ? "History is paused: the year, wars, disease, trade and the other world systems wait. People still appear"
                + " and building work goes on. The pause is kept over a restart; /ee resume goes on." + terra
            : (why == null ? "History is running again." : "Your pause is lifted, but history is still " + why + ".");
        source.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }

    /** {@code /ee date} (step 18): today's game date, as villagers know it, and how long a day lasts. */
    private static int executeDateShow(CommandSourceStack source) {
        int today = com.eternalengine.knowledge.KnowledgeDate.today();
        SimulationClockManager clock = SimulationClockManager.getInstance();
        String extra = " " + terraDayNote() + (clock.isPaused() ? " History is paused now, and the day/night cycle with it." : "")
            + " Sleeping, /time add and /time set move the date forward; only /ee date moves it back.";
        String text = "Today in the game: " + com.eternalengine.knowledge.TalkService.dateLabel(today) + "." + extra;
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** How the day moves, in plain words (step 18b for Terra++ time; step 18d: both clocks follow the Minecraft days). */
    private static String terraDayNote() {
        int scale = com.eternalengine.clock.WorldCalendar.daysPerMinecraftDay();
        String clock = SimulationClockManager.isInitialized() && SimulationClockManager.getInstance().isTerraTime()
            ? "With Terra++ time" : "With the Eternal Engine clock";
        return scale == 1
            ? clock + " one Minecraft day is one day of history (20 real minutes)."
            : clock + " at " + scale + "x one Minecraft day is " + scale + " days of history.";
    }

    /**
     * {@code /ee date <year> <month> <day>} (step 18): go to a day, earlier or later: the only way the date goes back
     * (step 18d). With Terra++ time (step 18b) the Minecraft day count is set; with the Eternal Engine clock its
     * calendar's starting point (step 18d; it used to pause history, because a day lasted less than a second). The
     * sun stays where it is, and time runs on: one Minecraft day per day at 1x.
     */
    private static int executeDate(CommandSourceStack source, int year, int month, int day) {
        int days = month == 2 ? (com.eternalengine.knowledge.KnowledgeDate.daysIn(year) == 366 ? 29 : 28)
            : (month == 4 || month == 6 || month == 9 || month == 11) ? 30 : 31;
        if (day > days) {
            source.sendFailure(Component.literal("That month has " + days + " days."));
            return 0;
        }
        if (!SimulationClockManager.isInitialized() || !SimulationEngine.isInitialized()) {
            source.sendFailure(Component.literal("The simulation is not running"));
            return 0;
        }
        SimulationClockManager clock = SimulationClockManager.getInstance();
        // Step 48 (F9): the rule about who may move the clock is checked AT THE DOOR, not only in a test.
        // The command already requires permission 2, so this is a second lock on the same door and it is
        // the one that knows what a jump DOES: whether it forks the record, and whether it crosses the
        // seam into years that have never been written. The clock is read before the year changes.
        com.eternalengine.clock.TimeTravel.Verdict jump = com.eternalengine.clock.TimeTravel.judge(
            clock.getCurrentYear(), year, com.eternalengine.clock.TimeTravel.Who.OPERATOR);
        if (!jump.allowed()) {
            source.sendFailure(Component.literal("The clock stays: " + jump.because()));
            return 0;
        }
        int key = com.eternalengine.knowledge.KnowledgeDate.key(year, month, day);
        if (clock.isTerraTime()) {
            // Step 18b: Terra++ time is made of the Minecraft day count, so the day is set there. A new year is a
            // Terra++ time jump first (buildings change with the year); a day inside the year changes nothing else.
            // History is not paused: at 1x a day already lasts 20 real minutes.
            if (clock.getCurrentYear() != year) {
                clock.setCurrentYear(year);
            }
            int scale = com.eternalengine.terrabridge.TerraTimeBridge.getTimeScale(1);
            long worldDay = com.eternalengine.knowledge.KnowledgeDate.terraDayFor(key, scale,
                com.eternalengine.terrabridge.TerraTimeBridge.baseYear(), com.eternalengine.terrabridge.TerraTimeBridge.daysPerYear());
            if (worldDay < 0 || !com.eternalengine.terrabridge.TerraTimeBridge.setDayCount(source.getServer(), worldDay)) {
                source.sendFailure(Component.literal("Terra++ could not go to that day."));
                return 0;
            }
            com.eternalengine.clock.WorldCalendar.accept();
            int nowKey = com.eternalengine.knowledge.KnowledgeDate.today();
            String note = nowKey == key ? "" : " (the nearest day this Terra++ year has)";
            String done = "It is now " + com.eternalengine.knowledge.TalkService.dateLabel(nowKey) + note + ". " + terraDayNote()
                + (jump.effect() == com.eternalengine.clock.TimeTravel.Effect.STAYS ? "" : " " + jump.because());
            source.sendSuccess(() -> Component.literal(done), true);
            return 1;
        }
        if (clock.getCurrentYear() != year) {
            clock.setCurrentYear(year);
        }
        com.eternalengine.clock.WorldCalendar.setDate(key);
        int now = com.eternalengine.knowledge.KnowledgeDate.today();
        String text = "It is now " + com.eternalengine.knowledge.TalkService.dateLabel(now) + ". " + terraDayNote()
            + (jump.effect() == com.eternalengine.clock.TimeTravel.Effect.STAYS ? "" : " " + jump.because());
        source.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }

    /**
     * {@code /ee purse} (step 19a): your purse, and the money people use where you stand this year. The first time,
     * the purse gets a day's pay in that money. Admins also have {@code /ee purse [at <lat> <lon>] [add <amount>
     * [unit] | reset]}: "at" stands in for where you are (for testing), "add" puts money of the place in (a minus
     * sign takes it out; the unit is any of its coins or "days"), and "reset" empties the purse so that the start
     * money comes again.
     */
    private static int executePurse(CommandSourceStack source, String args) {
        ServerPlayer player = source.getPlayer();
        if (player == null || source.getServer() == null) {
            source.sendFailure(Component.literal("Only a player has a purse."));
            return 0;
        }
        String usage = "Usage: /ee purse [at <lat> <lon>] [add <amount> [unit] | reset]";
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        int i = 0;
        double[] place = null;
        try {
            if (w.length >= 3 && w[0].equalsIgnoreCase("at")) {
                place = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
                i = 3;
                if (Math.abs(place[0]) > 90 || Math.abs(place[1]) > 180) {
                    source.sendFailure(Component.literal("That is not a place on Earth."));
                    return 0;
                }
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal(usage));
            return 0;
        }
        if (place == null) {
            place = com.eternalengine.money.PurseService.placeOf(player);
        }
        int date = com.eternalengine.knowledge.KnowledgeDate.today();
        int year = com.eternalengine.knowledge.KnowledgeDate.year(date);
        com.eternalengine.money.MoneyTable table = com.eternalengine.money.MoneyTable.get();
        com.eternalengine.money.MoneyTable.Here here = com.eternalengine.money.PurseService.here(place, year);
        com.eternalengine.money.PlayerPurses purses = com.eternalengine.money.PlayerPurses.get(source.getServer());
        String action = i < w.length ? w[i].toLowerCase(java.util.Locale.ROOT) : "";
        List<String> out = new java.util.ArrayList<>();
        if (action.equals("reset")) {
            purses.reset(player.getUUID());
            int pieces = com.eternalengine.money.CoinPurse.of(player).clear(); // step 23b: the pieces go too
            String done = "Your purse is empty now" + (pieces > 0 ? " (" + pieces + " piece(s) of money taken away)" : "")
                + ". The start money comes again the next time you open it.";
            source.sendSuccess(() -> Component.literal(done), false);
            LOGGER.info("[Purse] {} reset their purse", player.getGameProfile().getName());
            return 1;
        }
        if (!action.isEmpty() && !action.equals("add")) {
            source.sendFailure(Component.literal(usage));
            return 0;
        }
        String start = com.eternalengine.money.PurseService.startIfNew(purses, player.getUUID(), here, date);
        if (start != null) {
            out.add(start);
            LOGGER.info("[Purse] {} got the start money: {} {} ({})", player.getGameProfile().getName(),
                here.dayPay(), here.money().id(), com.eternalengine.knowledge.KnowledgeDate.label(date));
        }
        com.eternalengine.money.CoinPurse coins = com.eternalengine.money.CoinPurse.of(player); // step 23b: pieces
        if (action.equals("add")) {
            if (i + 1 >= w.length) {
                source.sendFailure(Component.literal(usage));
                return 0;
            }
            String unit = i + 2 < w.length ? String.join(" ", java.util.Arrays.copyOfRange(w, i + 2, w.length)) : "";
            long amount;
            try {
                amount = com.eternalengine.money.MoneyFormat.parse(here.money(), w[i + 1], unit, year);
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal(e.getMessage()));
                return 0;
            }
            long moved = amount;
            if (amount >= 0) {
                moved = coins.receive(here.money().id(), amount, year);
                if (moved < 0) {
                    source.sendFailure(Component.literal("That is more than you can carry in pieces of money."));
                    return 0;
                }
            } else if (!coins.pay(here.money().id(), -amount, year)) {
                long have = coins.amount(here.money().id());
                source.sendFailure(Component.literal(have <= 0
                    ? "You have no " + here.money().name() + "."
                    : "You have only " + com.eternalengine.money.PurseService.named(here.money(), have, year) + "."));
                return 0;
            }
            long now = coins.amount(here.money().id());
            out.add((amount >= 0 ? "Added " : "Took out ")
                + com.eternalengine.money.PurseService.named(here.money(), Math.abs(moved), year) + ".");
            LOGGER.info("[Purse] {} {} {} {}: now {}", player.getGameProfile().getName(), amount >= 0 ? "added" : "took out",
                Math.abs(amount), here.money().id(), now);
        }
        String carried = coins.piecesLine();
        if (!carried.isEmpty()) {
            out.add("Pieces you carry: " + carried + ".");
        }
        out.addAll(com.eternalengine.money.PurseService.describe(table, coins.view(), here));
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee money [at <lat> <lon> [year]]} (step 19a, admin): which money a place used in a year (here and now
     * without "at"), which row of the money table said so, its coins, a day's pay, and what a paper (a sixteenth
     * of a day) and a map (half a day) would cost there.
     */
    private static int executeMoney(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double[] place = null;
        int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
        try {
            if (w.length >= 3 && w[0].equalsIgnoreCase("at")) {
                place = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
                if (w.length >= 4) {
                    year = Integer.parseInt(w[3]);
                }
            } else if (w.length > 0) {
                source.sendFailure(Component.literal("Usage: /ee money [at <lat> <lon> [year]]"));
                return 0;
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Usage: /ee money [at <lat> <lon> [year]]"));
            return 0;
        }
        if (place == null && source.getPlayer() != null) {
            place = com.eternalengine.money.PurseService.placeOf(source.getPlayer());
        }
        com.eternalengine.money.MoneyTable table = com.eternalengine.money.MoneyTable.get();
        com.eternalengine.money.MoneyTable.Here h = com.eternalengine.money.PurseService.here(place, year);
        com.eternalengine.money.MoneyTable.Money m = h.money();
        String where = place == null ? "an unknown place" : String.format(java.util.Locale.ROOT, "%.4f, %.4f", place[0], place[1]);
        String when = year < 0 ? (-year) + " BCE" : String.valueOf(year);
        long day = h.dayPay();
        String units = com.eternalengine.money.MoneyFormat.units(m, year);
        List<String> out = new java.util.ArrayList<>();
        out.add("[EE money] " + where + " in " + when + ": " + m.name() + " (" + m.id() + "), from the row " + h.row().id()
            + " (priority " + h.row().priority() + ").");
        out.add("Coins: " + (m.inDays() ? "none named; amounts are told in days of work" : units.isEmpty()
            ? m.unitsIn(year).get(0).plural() + " only" : units) + ". A day's pay: "
            + com.eternalengine.money.MoneyFormat.amount(m, day, year) + " (" + day + " of the smallest unit). A paper (a sixteenth of a day): "
            + com.eternalengine.money.MoneyFormat.amount(m, Math.max(1L, day / 16), year) + ". A map (half a day): "
            + com.eternalengine.money.MoneyFormat.amount(m, day / 2, year) + ".");
        if (!h.accepts().isEmpty()) {
            List<String> also = new java.util.ArrayList<>();
            for (com.eternalengine.money.MoneyTable.Money a : h.accepts()) {
                String rate = com.eternalengine.money.PurseService.rateText(table, m, a, year);
                also.add(a.name() + (rate.isEmpty() ? "" : " (" + rate + ")"));
            }
            out.add("Also taken: " + String.join("; ", also) + ".");
        }
        var sum = table.summary();
        out.add("Money table: " + sum.monies() + " kinds of money, " + sum.rows() + " rows"
            + (table.problems().isEmpty() ? "." : "; problems: " + String.join("; ", table.problems())));
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee oath} (step 42, F2, admin): <b>the oaths you have sworn, and who you could swear
     * to here</b>.
     *
     * <pre>
     *   /ee oath                          your oaths as they stand, and the parties in force here
     *   /ee oath to &lt;party&gt; [year]       swear to a party (or strengthen an oath already sworn)
     *   /ee oath break &lt;party&gt; [year]     break it - which is worse than never having sworn it
     *   /ee oath here [at &lt;lat&gt; &lt;lon&gt; [year]]   who is in force at a place, and can be sworn to
     * </pre>
     *
     * <p>Loyalty is the other half of F2 from fame, and it works on purpose by different rules:
     * an opinion halves every {@code Fame.HALF_LIFE_YEARS} years and an oath every
     * {@code Fame.OATH_HALF_LIFE_YEARS}, twice as slow, <b>because a tale needs a witness and a
     * promise does not</b>. Loyalty is owed to a <b>party</b> - a house, a city, a faith, a state
     * or a hired company - and not to the ground, which is the opposite of fame: reputation
     * belongs to a place, and an oath belongs to somebody.
     *
     * <p>Two things the command says rather than hides. An oath can outlive the body it was sworn
     * to: {@code swornLive} asks whether the party is still in force this year, so a man bound to
     * a state that no longer exists is shown as still bound and with nobody left to call on it.
     * And a party that {@code cannotTreat} - a band, a rising, an unpaid company - will take your
     * oath and cannot be held to it, which is printed at the moment of swearing and not discovered
     * later.
     */
    private static int executeOath(CommandSourceStack source, String args) {
        String usage = "Usage: /ee oath [to <party> [year] | break <party> [year] | "
                + "here [at <lat> <lon> [year]]]";
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        List<String> out = new java.util.ArrayList<>();
        int year = com.eternalengine.knowledge.KnowledgeDate.year(
                com.eternalengine.knowledge.KnowledgeDate.today());

        // ---------------------------------------------------------------- /ee oath here
        if (w.length >= 1 && w[0].equalsIgnoreCase("here")) {
            double[] place = parsePlace(source, w.length > 1 ? String.join(" ",
                    java.util.Arrays.copyOfRange(w, 1, w.length)) : "", usage);
            if (place == null) {
                return 0;
            }
            int y = parseYear(source, w.length > 1 ? String.join(" ",
                    java.util.Arrays.copyOfRange(w, 1, w.length)) : "", usage);
            out.add("[EE oath] who is in force at " + formatPlace(place) + " in " + formatYear(y) + ":");
            int shown = 0;
            for (com.eternalengine.war.Sides.Party p
                    : com.eternalengine.war.Sides.get().parties()) {
                if (!p.inForce(y) || !p.here(place[0], place[1])) {
                    continue;
                }
                out.add("  " + p.id() + " - " + p.name() + " (" + p.kind() + ")"
                        + (p.cannotTreat() ? "  [takes an oath and cannot be held to it]" : ""));
                shown++;
            }
            if (shown == 0) {
                out.add("  Nobody the register knows is in force there in " + formatYear(y) + ".");
            }
            for (String line : out) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        }

        // ---------------------------------------------------------------- /ee oath to / break
        boolean acting = w.length >= 2
                && (w[0].equalsIgnoreCase("to") || w[0].equalsIgnoreCase("break"));
        if (acting) {
            if (source.getPlayer() == null) {
                source.sendFailure(Component.literal(usage + " - swearing needs a player"));
                return 0;
            }
            boolean breaking = w[0].equalsIgnoreCase("break");
            String id = w[1];
            if (w.length >= 3) {
                try {
                    year = Integer.parseInt(w[2]);
                } catch (NumberFormatException e) {
                    source.sendFailure(Component.literal(usage));
                    return 0;
                }
            }
            com.eternalengine.war.Sides.Party party =
                    com.eternalengine.war.Sides.get().party(id);
            if (party == null) {
                source.sendFailure(Component.literal("No party called '" + id
                        + "'. Say /ee oath here to see who is in force where you stand."));
                return 0;
            }
            // A promise is worth more than a tale, and breaking one costs more than the promise.
            int delta = breaking ? -60 : 40;
            com.eternalengine.reputation.LandReputation store =
                    com.eternalengine.reputation.LandReputation.get(source.getPlayer().serverLevel());
            com.eternalengine.reputation.LandReputation.Oath oath =
                    store.swear(source.getPlayer().getUUID(), id, delta, year);
            int now = store.loyalty(source.getPlayer().getUUID(), id, year);
            out.add("[EE oath] " + (breaking ? "broken with " : "sworn to ") + party.name()
                    + " (" + party.kind() + ") in " + formatYear(year) + ":");
            out.add("  " + com.eternalengine.reputation.Fame.phrase(now) + " (" + now + "),"
                    + " the oath first sworn in " + formatYear(oath.year()) + ".");
            if (!store.swornLive(id, year)) {
                out.add("  The party is not in force in " + formatYear(year)
                        + " - the oath stands, and there is nobody left to call on it.");
            }
            if (party.cannotTreat()) {
                out.add("  And they take an oath without being bound by one: "
                        + party.name() + " cannot be held to a signature.");
            }
            for (String line : out) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        }

        // ---------------------------------------------------------------- /ee oath
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(usage + " - or stand somewhere as a player"));
            return 0;
        }
        com.eternalengine.reputation.LandReputation store =
                com.eternalengine.reputation.LandReputation.get(source.getPlayer().serverLevel());
        java.util.UUID you = source.getPlayer().getUUID();
        out.add("[EE oath] " + formatYear(year) + ": an oath halves every "
                + com.eternalengine.reputation.Fame.OATH_HALF_LIFE_YEARS
                + " years, a name every " + com.eternalengine.reputation.Fame.HALF_LIFE_YEARS + ".");
        java.util.List<com.eternalengine.reputation.LandReputation.Oath> oaths =
                store.oaths(you, year);
        if (oaths.isEmpty()) {
            out.add("You have sworn to nobody.");
        } else {
            for (com.eternalengine.reputation.LandReputation.Oath o : oaths) {
                com.eternalengine.war.Sides.Party p =
                        com.eternalengine.war.Sides.get().party(o.party());
                out.add("  " + (p == null ? o.party() : p.name()) + " [" + o.party() + "]: "
                        + store.loyalty(you, o.party(), year) + ", sworn in " + formatYear(o.year())
                        + (store.swornLive(o.party(), year) ? ""
                        : "  [not in force now - the oath stands, and nobody is left to call on it]"));
            }
        }
        // Who you could swear to, where you are standing.
        double lat = source.getPlayer().getX();
        double lon = source.getPlayer().getZ();
        int offered = 0;
        for (com.eternalengine.war.Sides.Party p : com.eternalengine.war.Sides.get().parties()) {
            if (!p.inForce(year) || !p.here(lat, lon)) {
                continue;
            }
            if (offered == 0) {
                out.add("In force where you stand, and so able to take an oath:");
            }
            out.add("  " + p.id() + " - " + p.name() + " (" + p.kind() + ")"
                    + (p.cannotTreat() ? "  [cannot be held to it]" : ""));
            if (++offered >= 8) {
                out.add("  ... and others; /ee oath here lists them all");
                break;
            }
        }
        if (offered == 0) {
            out.add("Nobody the register knows is in force where you stand.");
        }
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee standing [at <lat> <lon> [year]]} (step 42, F3, admin): <b>who keeps the peace
     * here, who judges here, and what can lawfully be done to a man</b>.
     *
     * <p>Everything printed is a reading and says so - <b>except whether they can still hang
     * you</b>, which is the C18 law pack's own dated abolition and is printed with the year.
     */
    private static int executeJustice(CommandSourceStack source, String args) {
        String usage = "Usage: /ee justice [at <lat> <lon> [year]]";
        double[] place = parsePlace(source, args, usage);
        if (place == null) {
            return 0;
        }
        int year = parseYear(source, args, usage);
        double lat = place[0];
        double lon = place[1];

        com.eternalengine.civilization.Justice.Here here =
                com.eternalengine.civilization.Justice.get().hereAt(lat, lon, year);

        List<String> out = new java.util.ArrayList<>();
        out.add("[EE justice] " + formatPlace(place) + " in " + formatYear(year) + ":");
        out.add("  The peace is kept by " + here.guards().head() + ".");
        out.add("    " + here.guards().line());
        out.add("  You are judged by " + here.courts().head() + ".");
        out.add("    " + here.courts().line());
        out.add("  What they can do to you: "
                + String.join("; ", here.punishments().stream()
                .map(com.eternalengine.civilization.Justice.Row::name).toList()) + ".");
        if (here.abolishedIn() != null) {
            out.add("  This country gave up hanging in " + formatYear(here.abolishedIn()) + ".");
        } else {
            out.add("  Nothing in the record says this country has given up hanging.");
        }
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee oath} (step 42, F2, admin): <b>the oaths you have sworn, and swearing a new one</b>.
     *
     * <pre>
     *   /ee oath                          your oaths, and the bodies in force where you stand
     *   /ee oath to &lt;partyId&gt; [year]       swear to a party, or strengthen an oath already sworn
     *   /ee oath break &lt;partyId&gt; [year]    break it - a broken oath is worse than none
     *   /ee oath parties [at &lt;lat&gt; &lt;lon&gt; [year]]   who can be sworn to there
     * </pre>
     *
     * <p>Loyalty is the other half of F2 from fame, and it runs by different rules on purpose:
     * a name halves every {@code Fame.HALF_LIFE_YEARS} and an oath every
     * {@code Fame.OATH_HALF_LIFE_YEARS}, twice as slow, <b>because a tale needs a witness and a
     * promise does not</b>. Loyalty is owed to a {@code Sides.Party} - a house, a city, a faith, a
     * state, a hired company - and not to the ground, which is the opposite of reputation.
     *
     * <p>An oath outlives the body it was sworn to, and says so: {@code swornLive} asks whether
     * the party is still in force this year. A man still bound to the Kingdom of France in 1900 is
     * not a man with a strong oath, he is a man with a memory, and the command prints the
     * difference.
     */
    /**
     * {@code /ee border} (step 42, F8, admin): <b>what the crossing demands of you</b>, and where
     * the line you are about to cross actually runs.
     *
     * <p>Four forms:
     * <pre>
     *   /ee border                                  what the crossing is like where you stand
     *   /ee border at &lt;lat&gt; &lt;lon&gt; [year]             what it is like there
     *   /ee border to &lt;lat&gt; &lt;lon&gt; [year]             the crossing from where you stand to there
     *   /ee border at &lt;lat&gt; &lt;lon&gt; to &lt;lat&gt; &lt;lon&gt; [year]
     *   /ee border list                            all twelve kinds of crossing
     * </pre>
     *
     * <p>Where the line runs is <b>not</b> printed from a table. It is derived, per question, from
     * the ruler register - if the far side is ruled by somebody else, there is a line between, and
     * bisection finds it. The pack only says what stands on that line.
     *
     * <p><b>By land and by sea are printed both, because they are genuinely different answers.</b>
     * A man riding north through England in 1800 met turnpike gates; a ship putting in at Liverpool
     * met a customs house; a ship putting in at Venice met forty days in a walled yard. Same year.
     */
    private static int executeBorder(CommandSourceStack source, String args) {
        String usage = "Usage: /ee border [at <lat> <lon> [year]] [to <lat> <lon>] | /ee border list";
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        List<String> out = new java.util.ArrayList<>();

        if (w.length >= 1 && w[0].equalsIgnoreCase("list")) {
            out.add("[EE border] " + com.eternalengine.civilization.Borders.get().rowCount()
                    + " kinds of crossing:");
            for (com.eternalengine.civilization.Borders.Row r
                    : com.eternalengine.civilization.Borders.get().rows()) {
                String marks = (r.closed() ? " turns you back" : "")
                        + (r.papers() ? " wants papers" : "")
                        + (r.quarantine() ? " holds you" : "")
                        + (r.sea() ? " [by sea]" : "")
                        + (r.internal() ? " [inside one state]" : "");
                out.add(String.format("  %-18s %s", r.id(), r.head() + (marks.isEmpty() ? "" : " -" + marks)));
            }
            for (String line : out) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        }

        int year = com.eternalengine.knowledge.KnowledgeDate.year(
                com.eternalengine.knowledge.KnowledgeDate.today());
        double[] from = null;
        double[] to = null;
        try {
            int i = 0;
            if (i < w.length && w[i].equalsIgnoreCase("at") && i + 2 < w.length) {
                from = new double[] {Double.parseDouble(w[i + 1]), Double.parseDouble(w[i + 2])};
                i += 3;
            }
            if (i < w.length && w[i].equalsIgnoreCase("to") && i + 2 < w.length) {
                to = new double[] {Double.parseDouble(w[i + 1]), Double.parseDouble(w[i + 2])};
                i += 3;
            }
            if (i < w.length) {
                year = Integer.parseInt(w[i]);
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal(usage));
            return 0;
        }
        if (from == null && source.getPlayer() != null) {
            from = new double[] {source.getPlayer().getX(), source.getPlayer().getZ()};
        }
        if (from == null) {
            source.sendFailure(Component.literal(usage + " - or stand somewhere and say it with no arguments"));
            return 0;
        }

        if (to != null) {
            // The whole point of F8: one step, and what it costs you.
            for (boolean sea : new boolean[] {false, true}) {
                com.eternalengine.civilization.Borders.Cross c =
                        com.eternalengine.civilization.Borders.get()
                                .crossing(from[0], from[1], to[0], to[1], year, sea);
                out.add("[EE border] " + formatPlace(from) + " -> " + formatPlace(to) + " in "
                        + formatYear(year) + (sea ? ", arriving by sea" : ", overland") + ":");
                out.add("  " + c.line());
            }
            for (String line : out) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        }

        out.add("[EE border] " + formatPlace(from) + " in " + formatYear(year) + ":");
        for (boolean sea : new boolean[] {false, true}) {
            com.eternalengine.civilization.Borders.Row r =
                    com.eternalengine.civilization.Borders.get().postAt(from[0], from[1], year, sea);
            out.add("  " + (sea ? "Arriving by sea " : "Overland           ") + ": " + r.head());
            out.add("      " + r.line());
        }
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee justice [at <lat> <lon> [year]]} (step 42, F3, admin): <b>who keeps the peace
     * here, who judges here, and what can lawfully be done to a man</b>.
     *
     * <p>Everything printed is a reading and says so - <b>except whether they can still hang
     * you</b>, which is the C18 law pack's own dated abolition and is printed with the year.
     */
    /**
     * {@code /ee fame [at <lat> <lon> [year]]} (step 42, F2, admin): <b>what a land thinks of
     * you</b>, and how far the tale has run.
     *
     * <p>Three things are printed, because they are three different questions and mixing them is
     * how a reputation system lies: the opinion <b>here</b>, the opinion in each <b>land</b> that
     * has heard of you, and the <b>deeds</b> it is all worked out from.
     */
    private static int executeFame(CommandSourceStack source, String args) {
        String usage = "Usage: /ee fame [at <lat> <lon> [year]]";
        double[] place = parsePlace(source, args, usage);
        if (place == null) {
            return 0;
        }
        int year = parseYear(source, args, usage);
        double lat = place[0];
        double lon = place[1];

        List<String> out = new java.util.ArrayList<>();
        out.add("[EE fame] " + formatPlace(place) + " in " + formatYear(year)
                + ": " + com.eternalengine.reputation.Fame.HALF_LIFE_YEARS + " years to half a name, "
                + com.eternalengine.reputation.Fame.OATH_HALF_LIFE_YEARS + " to half an oath.");

        if (source.getPlayer() == null) {
            out.add("Fame is kept per player, so this needs a player to ask about.");
        } else {
            com.eternalengine.reputation.LandReputation store =
                    com.eternalengine.reputation.LandReputation.get(source.getPlayer().serverLevel());
            java.util.UUID you = source.getPlayer().getUUID();
            int here = store.opinion(you, lat, lon, year);
            out.add("Here: " + here + " - " + com.eternalengine.reputation.Fame.phrase(here) + ".");

            java.util.List<String> lands = store.lands(you, year);
            if (lands.isEmpty()) {
                out.add("Nobody anywhere has heard of you.");
            } else {
                for (String land : lands) {
                    int v = store.landOpinion(you, land, year);
                    out.add("  " + land + ": " + v + " - "
                            + com.eternalengine.reputation.Fame.phrase(v) + ".");
                }
            }
            java.util.List<com.eternalengine.reputation.Fame.Deed> deeds = store.deeds(you);
            if (!deeds.isEmpty()) {
                out.add("Deeds (" + deeds.size() + "):");
                for (com.eternalengine.reputation.Fame.Deed d : deeds) {
                    out.add("  " + d.line()
                            + (com.eternalengine.reputation.Fame.heard(d, lat, lon, year)
                            ? "" : "   [not yet heard here]"));
                }
            }
            java.util.List<com.eternalengine.reputation.LandReputation.Oath> oaths =
                    store.oaths(you, year);
            if (!oaths.isEmpty()) {
                out.add("Oaths:");
                for (com.eternalengine.reputation.LandReputation.Oath o : oaths) {
                    out.add("  " + o.party() + ": " + store.loyalty(you, o.party(), year)
                            + ", sworn in " + formatYear(o.year())
                            + (store.swornLive(o.party(), year) ? ""
                            : "   [the body is no longer in force]"));
                }
            }
        }
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee law [at <lat> <lon> [year]]} (step 42, C18 and D13, admin): the law at a place and
     * year and how the people there speak of it.
     *
     * <p>The law is read through the ruler register, because C18's pack is keyed by the state that
     * rules here and not by the ground: the same square answers differently in 1887 and in 1889.
     */
    private static int executeLaw(CommandSourceStack source, String args) {
        String usage = "Usage: /ee law [at <lat> <lon> [year]]";
        double[] place = parsePlace(source, args, usage);
        if (place == null) {
            return 0;
        }
        int year = parseYear(source, args, usage);
        double lat = place[0];
        double lon = place[1];
        String where = formatPlace(place);
        String when = formatYear(year);

        com.eternalengine.civilization.LawRegister reg = com.eternalengine.civilization.LawRegister.get();
        String qid = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year)
                .map(com.eternalengine.civilization.RulerRegister.Hit::wikidata).orElse(null);

        List<String> out = new java.util.ArrayList<>();
        out.add("[EE law] " + where + " in " + when + ": " + com.eternalengine.civilization.Reactions.toLaw(lat, lon, year));
        if (qid != null) {
            out.add("Ruled by " + reg.polityName(qid) + " (" + qid + ").");
            for (com.eternalengine.civilization.LawRegister.Change c : reg.inForce(qid, year)) {
                out.add("  in force: " + c.line() + (c.estimated() ? " (estimated)" : ""));
            }
            com.eternalengine.civilization.LawRegister.Change coming = reg.next(qid, year);
            out.add(coming == null ? "Nothing further is in the record."
                    : "Still to come: " + coming.line());
        } else {
            out.add("The register names no state ruling here in " + when + ", so it names no law either.");
        }
        out.add("Law pack: " + reg.rowCount() + " changes over " + reg.polityCount() + " polities ("
                + String.join(", ", reg.kinds()) + ").");
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee standing [at <lat> <lon> [year]]} (step 42, D13 and D18, admin): how the people of
     * a place react to you, how they greet you, and who may speak to whom there.
     *
     * <p>The greeting is D7's first use: a man of the third estate does not open a conversation
     * with a man of the first, whatever his opinion of you, so the same command prints a different
     * sentence for each order standing in the square.
     */
    private static int executeStanding(CommandSourceStack source, String args) {
        String usage = "Usage: /ee standing [at <lat> <lon> [year]]";
        double[] place = parsePlace(source, args, usage);
        if (place == null) {
            return 0;
        }
        int year = parseYear(source, args, usage);
        double lat = place[0];
        double lon = place[1];
        String where = formatPlace(place);
        String when = formatYear(year);

        com.eternalengine.civilization.SocialOrder.Order order =
                com.eternalengine.civilization.SocialOrder.get().hereAt(lat, lon, year);

        List<String> out = new java.util.ArrayList<>();
        out.add("[EE standing] " + where + " in " + when + ": " + order.line());

        java.util.UUID you = source.getPlayer() == null ? null : source.getPlayer().getUUID();
        if (source.getPlayer() != null) {
            com.eternalengine.villager.PlayerStanding store =
                    com.eternalengine.villager.PlayerStanding.get(source.getPlayer().serverLevel());
            String key = com.eternalengine.villager.PlayerStanding.placeKey(
                    source.getPlayer().getBlockX(), source.getPlayer().getBlockZ());
            com.eternalengine.villager.PlayerStanding.Opinion o = store.opinion(key, you);
            out.add("They think of you: " + o.standing() + " after " + o.visits() + " visit(s)"
                    + (o.lastYear() == 0 ? "" : ", last seen in " + formatYear(o.lastYear())) + ".");
            out.add("A stranger arriving: " + com.eternalengine.civilization.Reactions.greetStranger(lat, lon, year));
            out.add("To you: " + store.greeting(key, you, lat, lon, year));
            out.add("Reaction: " + store.reaction(key, you, lat, lon, year));
        }

        // Who may open a conversation with whom, and what each order says when it cannot.
        for (com.eternalengine.civilization.SocialOrder.Stratum from : order.strata()) {
            for (com.eternalengine.civilization.SocialOrder.Stratum to : order.strata()) {
                if (from == to) {
                    continue;
                }
                if (!order.mayAddress(from, to)) {
                    out.add("  " + order.addressLine(from, to));
                }
            }
        }
        out.add("Social order pack: " + com.eternalengine.civilization.SocialOrder.get().source());
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /** {@code [at <lat> <lon> [year]]} -> the place, or the player's own; null when the words are wrong. */
    private static double[] parsePlace(CommandSourceStack source, String args, String usage) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double[] place = null;
        try {
            if (w.length >= 3 && w[0].equalsIgnoreCase("at")) {
                place = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
            } else if (w.length > 0) {
                source.sendFailure(Component.literal(usage));
                return null;
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal(usage));
            return null;
        }
        if (place == null && source.getPlayer() != null) {
            place = geoOf(source);
        }
        if (place == null) {
            source.sendFailure(Component.literal(usage + " - or stand somewhere and say it with no arguments"));
        }
        return place;
    }

    /**
     * The latitude and longitude of whoever asked, or null. Step 43 fixed this: it used to hand
     * over {@code getX()} and {@code getZ()} as though block coordinates were degrees, so every
     * command that fell back to "where you are standing" - law, justice, oath, border, and all of
     * step 43 - was asking about a patch of ocean near 0,0.
     */
    private static double[] geoOf(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return null;
        }
        double[] geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeo(
                net.minecraft.core.BlockPos.containing(source.getPosition()));
        // toGeo returns [longitude, latitude].
        return geo == null ? null : new double[] {geo[1], geo[0]};
    }

    // ---------------------------------------------------------------- step 44 (D16): makers

    /**
     * {@code /ee workshop [goods] [at <lat> <lon>] [year] [people]} — what is made here, and
     * what is stopping more of it being made.
     *
     * <p>With a good named, that workshop. With none, the workshops here that are held back,
     * worst first, which is the list a player actually wants: not what a place makes, but what
     * is stopping it.
     */
    private static int executeWorkshop(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double[] place = null;
        int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
        int people = 0;
        String good = null;
        try {
            int n = 0;
            if (w.length >= 3 && w[0].equalsIgnoreCase("at")) {
                place = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
                n = 3;
                while (w.length > n) {
                    try {
                        int v = Integer.parseInt(w[n]);
                        if (Math.abs(v) > 4000 && year == com.eternalengine.knowledge.KnowledgeDate
                                .year(com.eternalengine.knowledge.KnowledgeDate.today())) {
                            year = v;                       // a four-digit number is a year
                        } else {
                            people = v;                     // anything smaller is a head count
                        }
                        n++;
                    } catch (NumberFormatException ignored) {
                        break;                              // not a number, so it is the good
                    }
                }
                if (w.length > n) {
                    good = String.join(" ", java.util.Arrays.copyOfRange(w, n, w.length));
                }
            } else {
                while (w.length > n) {
                    try {
                        people = Integer.parseInt(w[n]);
                        n++;
                    } catch (NumberFormatException ignored) {
                        break;
                    }
                }
                if (w.length > n) {
                    good = String.join(" ", java.util.Arrays.copyOfRange(w, n, w.length));
                }
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Usage: /ee workshop [goods] [at <lat> <lon>] [year] [people]"));
            return 0;
        }
        if (place == null) {
            place = geoOf(source);
        }
        if (place == null) {
            source.sendFailure(Component.literal("No place: stand somewhere, or say /ee workshop at <lat> <lon>."));
            return 0;
        }
        if (people <= 0) {
            // How many people are here, from the population register. Without a head count the
            // jobs table has nothing to take a share of, and a workshop with no hands is not a
            // finding about the place, it is a missing argument.
            people = (int) Math.max(0L, com.eternalengine.population.PopulationResolver.get()
                    .resolve(null, place[0], place[1], year, null).population());
            if (people <= 0) {
                source.sendFailure(Component.literal(
                        "No population here: say how many people, as /ee workshop bread 300."));
                return 0;
            }
        }

        com.eternalengine.money.ProductAtlas atlas = com.eternalengine.money.ProductAtlas.get();
        if (!atlas.loaded()) {
            source.sendFailure(Component.literal("The product atlas did not load: " + atlas.problem()));
            return 0;
        }

        java.util.List<String> out = new java.util.ArrayList<>();
        if (good != null) {
            com.eternalengine.money.ProductAtlas.Product p = atlas.byName(good);
            if (p == null) {
                final String goodMsg = good;
                source.sendSuccess(() -> Component.literal("No product called '" + goodMsg + "'."), false);
                return 1;
            }
            com.eternalengine.money.Workshop.Output o =
                    com.eternalengine.money.Workshop.at(p, place[0], place[1], year, people);
            if (o == null) {
                source.sendSuccess(() -> Component.literal("Nothing to say about " + p.name() + "."), false);
                return 1;
            }
            out.add("[EE workshop] " + o.sentence() + ".");
            if (o.rated() && !o.idle()) {
                out.add("  at most " + whole(o.ceiling()) + " a day if all " + o.hands()
                        + " were set to it — and " + o.rivals() + " other goods here want the same hands.");
            }
            for (java.util.List<String> group : p.inputs()) {
                out.add("  made from " + String.join(" or ", group).replace('_', ' '));
            }
        } else {
            String where = String.format(java.util.Locale.ROOT, "%.2f, %.2f", place[0], place[1]);
            String when = year < 0 ? (Math.abs(year) + " BCE") : String.valueOf(year);
            out.add("[EE workshop] " + where + " in " + when + ", " + people + " people.");
            java.util.List<com.eternalengine.money.Workshop.Output> shortfalls =
                    com.eternalengine.money.Workshop.shortfalls(place[0], place[1], year, people);
            if (shortfalls.isEmpty()) {
                out.add("Nothing here is held back: every workshop that has hands has material.");
            } else {
                out.add(shortfalls.size() + " workshops held back, worst first:");
                for (com.eternalengine.money.Workshop.Output o
                        : shortfalls.subList(0, Math.min(12, shortfalls.size()))) {
                    out.add("  " + o.product().name() + " — " + o.scarce() + ": " + o.why());
                }
            }
        }

        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // ---------------------------------------------------------------- step 44 (E19): what it costs to borrow

    /**
     * {@code /ee credit [at <lat> <lon>] [year] [all]} — what borrowing costs here, who lends,
     * and whether charging interest is lawful.
     *
     * <p>With {@code all}, every row that reaches this place and year, not just the one quoted.
     * The rows that say there is no price for credit are worth the same trouble as the ones that
     * give a number: Cusco in 1500 is a real answer, not a gap.
     */
    private static int executeCredit(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double lat = 0.0;
        double lon = 0.0;
        boolean havePlace = false;
        int year = com.eternalengine.knowledge.KnowledgeDate.year(
                com.eternalengine.knowledge.KnowledgeDate.today());
        boolean all = false;
        for (int i = 0; i < w.length; i++) {
            if (w[i].equalsIgnoreCase("at") && i + 2 < w.length) {
                try {
                    lat = Double.parseDouble(w[i + 1]);
                    lon = Double.parseDouble(w[i + 2]);
                    havePlace = true;
                    i += 2;
                } catch (NumberFormatException ignored) {
                    // left as it was found
                }
            } else if (w[i].equalsIgnoreCase("all")) {
                all = true;
            } else {
                try {
                    year = Integer.parseInt(w[i]);
                } catch (NumberFormatException ignored) {
                    // not a year, not a place: ignored
                }
            }
        }
        if (!havePlace) {
            try {
                net.minecraft.core.BlockPos pos = source.getPlayerOrException().blockPosition();
                // TerraCoordinateHelper.toGeo returns [longitude, latitude].
                double[] ll = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeo(
                        pos.getX(), pos.getZ());
                lat = ll[1];
                lon = ll[0];
                havePlace = true;
            } catch (Exception ignored) {
                source.sendFailure(Component.literal(
                        "No place: stand somewhere, or say /ee credit at <lat> <lon>."));
                return 0;
            }
        }

        com.eternalengine.money.Credit credit = com.eternalengine.money.Credit.get();
        if (!credit.loaded()) {
            source.sendFailure(Component.literal("No credit register: " + credit.problem()));
            return 0;
        }
        com.eternalengine.money.Credit.Terms terms = credit.at(lat, lon, year);
        java.util.List<String> out = new java.util.ArrayList<>();
        String when = year < 0 ? (-year) + " BC" : year + " AD";
        out.add("[EE credit] " + String.format(java.util.Locale.ROOT, "%.2f, %.2f", lat, lon)
                + " in " + when + ".");
        out.add(com.eternalengine.money.Credit.sentence(terms));
        if (all && !terms.silent()) {
            out.add("every row that reaches here:");
            for (com.eternalengine.money.Credit.Loan l : terms.loans()) {
                out.add("  " + credit.lenderWord(l.lender()) + ", " + l.mark() + ", "
                        + (l.noRate() ? "no rate" : l.rate() + "% on " + credit.kindWord(l.kind())));
            }
        }
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /** A count for a sentence: 3, 3.5, about 200. */
    private static String whole(double v) {
        if (v >= 100) {
            return "about " + Math.round(v / 10) * 10;
        }
        return v >= 10 ? String.valueOf(Math.round(v))
                : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    // ---------------------------------------------------------------- step 44 (E6): what a place eats

    /**
     * {@code /ee diet [at <lat> <lon>] [year]} — the meal at a place, seat by seat.
     *
     * <p>The atlas says when each food reaches a place; this says which of them is the grain,
     * which the pulse, and what it is all cooked in. Named goods are looked up in the same way
     * as {@code /ee shortage}: {@code /ee diet wheat} asks where wheat sits at this table.
     */
    private static int executeDiet(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double[] place = null;
        int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
        String good = null;
        try {
            if (w.length >= 3 && w[0].equalsIgnoreCase("at")) {
                place = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
                int n = 3;
                if (w.length > n) {
                    try {
                        year = Integer.parseInt(w[n]);
                        n++;
                    } catch (NumberFormatException ignored) {
                        // not a year, so it must be the food
                    }
                }
                if (w.length > n) {
                    good = String.join(" ", java.util.Arrays.copyOfRange(w, n, w.length));
                }
            } else if (w.length > 0) {
                good = String.join(" ", w);
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Usage: /ee diet [at <lat> <lon>] [year]"));
            return 0;
        }
        if (place == null) {
            place = geoOf(source);
        }
        if (place == null) {
            source.sendFailure(Component.literal("No place: stand somewhere, or say /ee diet at <lat> <lon>."));
            return 0;
        }

        com.eternalengine.money.ProductAtlas atlas = com.eternalengine.money.ProductAtlas.get();
        com.eternalengine.money.Diet diet = com.eternalengine.money.Diet.get();
        if (!atlas.loaded()) {
            source.sendFailure(Component.literal("The product atlas did not load: " + atlas.problem()));
            return 0;
        }
        if (!diet.loaded()) {
            source.sendFailure(Component.literal("The diet pack did not load: " + diet.problem()));
            return 0;
        }

        com.eternalengine.money.Diet.Meal meal = diet.at(place[0], place[1], year);
        java.util.List<String> out = new java.util.ArrayList<>();

        if (good != null) {
            com.eternalengine.money.ProductAtlas.Product p = atlas.byName(good);
            if (p == null) {
                final String goodMsg = good;
                source.sendSuccess(() -> Component.literal("No product called '" + goodMsg + "'."), false);
                return 1;
            }
            boolean sits = false;
            for (String seat : diet.seats()) {
                for (com.eternalengine.money.Diet.Serving v : meal.at(seat)) {
                    if (v.id().equals(p.id())) {
                        out.add("[EE diet] " + p.name() + " is " + diet.seatWord(seat) + " here"
                            + (v.madeHere() ? " (grown or made here)." : " (brought from far)."));
                        sits = true;
                    }
                }
            }
            if (!sits) {
                out.add("[EE diet] " + p.name() + " is not part of what is eaten here in " + year + ".");
            }
        } else {
            String where = String.format(java.util.Locale.ROOT, "%.2f, %.2f", place[0], place[1]);
            String when = year < 0 ? (Math.abs(year) + " BCE") : String.valueOf(year);
            String region = meal.place().region();
            out.add("[EE diet] " + where + " in " + when + ", " + meal.place().area()
                + (region == null || region.isBlank() ? "." : " (" + region + ")."));
            if (meal.seats().isEmpty()) {
                out.add("Nothing grows or is brought here that any register records.");
            } else {
                if (!meal.mapped()) {
                    out.add("The ways-of-life survey has no region here — open water, or an "
                        + "island too small for its 146 regions — so this is what is eaten in "
                        + meal.place().area() + " as a whole, not in this spot.");
                }
                if (meal.thin()) {
                    out.add("A thin table: no grain is grown or brought here, so there is no "
                        + "meal to speak of, only what can be gathered.");
                }
                out.add("What is eaten here: " + com.eternalengine.money.Diet.sentence(meal) + ".");
                for (String seat : meal.seats().keySet()) {
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (com.eternalengine.money.Diet.Serving v : meal.at(seat)) {
                        names.add(v.name() + (v.madeHere() ? " (here)" : " (brought)"));
                    }
                    out.add("  " + diet.seatWord(seat) + ": " + String.join(", ", names));
                }
            }
        }

        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // ---------------------------------------------------------------- step 44 (E4): prices and shortages

    /**
     * Step 44 (E4): what a thing costs here and now, and whether this is a dear year.
     *
     * <p>Usage: {@code /ee prices} (here, now), {@code /ee prices at <lat> <lon> [year]},
     * {@code /ee prices wheat}, {@code /ee prices at <lat> <lon> [year] wheat},
     * or {@code /ee prices shortages [at <lat> <lon>]}.
     *
     * <p>The index is the honest answer and the silver is not always: where the source's own
     * conversion to silver is wrong, the row says so instead of quoting a wrong figure.
     */
    /**
     * Step 44 part E (E5): what has gone wrong here, what is short because of it, and what people
     * would use instead. Reads the 37,201 disasters, the 84 climate periods and the 14 trade
     * routes that already ship — it invents nothing of its own.
     */
    private static int executeShortage(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double[] place = null;
        int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
        String good = null;
        try {
            if (w.length >= 3 && w[0].equalsIgnoreCase("at")) {
                place = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
                int n = 3;
                if (w.length > n) {
                    try {
                        year = Integer.parseInt(w[n]);
                        n++;
                    } catch (NumberFormatException ignored) {
                        // not a year, so it must be the good
                    }
                }
                if (w.length > n) {
                    good = String.join(" ", java.util.Arrays.copyOfRange(w, n, w.length));
                }
            } else if (w.length > 0) {
                good = String.join(" ", w);
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Usage: /ee shortage [good] [at <lat> <lon>] [year]"));
            return 0;
        }
        if (place == null) {
            place = geoOf(source);
        }
        if (place == null) {
            source.sendFailure(Component.literal("No place: stand somewhere, or say /ee shortage at <lat> <lon>."));
            return 0;
        }

        com.eternalengine.money.ProductAtlas atlas = com.eternalengine.money.ProductAtlas.get();
        com.eternalengine.money.Shortages short1 = com.eternalengine.money.Shortages.get();
        if (!atlas.loaded()) {
            source.sendFailure(Component.literal("The product atlas did not load: " + atlas.problem()));
            return 0;
        }
        if (!short1.loaded()) {
            source.sendFailure(Component.literal("The substitutes pack did not load: " + short1.problem()));
            return 0;
        }
        String when = year < 0 ? (-year) + " BCE" : String.valueOf(year);
        String where = String.format(java.util.Locale.ROOT, "%.4f, %.4f", place[0], place[1]);
        com.eternalengine.money.ProductAtlas.Place at = atlas.place(place[0], place[1], year);
        com.eternalengine.money.Shortages.Shock shock = short1.shockAt(place[0], place[1], year);

        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("[EE shortage] " + where + " in " + when + ", " + at.area() + ".");

        if (shock.quiet()) {
            out.add("Nothing is wrong here that any register records: no disaster reaching this "
                + "place in this year, no climate period on it, and no route that ran here and stopped.");
            if (!short1.routeRecordedNear(place[0], place[1])) {
                double km = short1.nearestRouteKm(place[0], place[1]);
                out.add("The trade register does not reach here — the nearest town on any of the "
                    + "fourteen routes is " + (km < 0 ? "nowhere in the register"
                    : String.format(java.util.Locale.ROOT, "%.0f", km) + " km away")
                    + " — so it says nothing about this place's trade rather than calling it cut off.");
            }
        } else {
            out.add("What has happened: " + shock.why() + ".");
            out.add(String.format(java.util.Locale.ROOT,
                "  the land %.0f%%, the hands %.0f%%, the roofs %.0f%%, the road %.0f%%.",
                shock.field() * 100, shock.hand() * 100, shock.roof() * 100, shock.trade() * 100));
        }

        if (good != null) {
            com.eternalengine.money.ProductAtlas.Product p = atlas.byName(good);
            if (p == null) {
                p = atlas.product(good.replace(' ', '_').toLowerCase(java.util.Locale.ROOT));
            }
            if (p == null) {
                final String goodMsg = good;
                source.sendSuccess(() -> Component.literal("No product called '" + goodMsg + "'."), false);
                return 1;
            }
            out.add(short1.sentence(p, at, place[0], place[1]) + ".");
        } else {
            // everything that is actually short here, worst first
            java.util.List<com.eternalengine.money.ProductAtlas.Product> what = new java.util.ArrayList<>();
            java.util.List<com.eternalengine.money.Shortages.Supply> howMuch = new java.util.ArrayList<>();
            for (com.eternalengine.money.ProductAtlas.Product p : atlas.products()) {
                com.eternalengine.money.ProductAtlas.Status st = atlas.status(p, at).status();
                if (st != com.eternalengine.money.ProductAtlas.Status.MADE_HERE
                    && st != com.eternalengine.money.ProductAtlas.Status.BROUGHT) {
                    continue;
                }
                com.eternalengine.money.Shortages.Supply sup = short1.supplyOf(p, shock, at);
                if (sup.short1()) {
                    what.add(p);
                    howMuch.add(sup);
                }
            }
            // sort the products by how short they are, carrying the supply along
            java.util.List<Integer> order = new java.util.ArrayList<>();
            for (int i = 0; i < what.size(); i++) {
                order.add(i);
            }
            order.sort((x, y) -> Double.compare(howMuch.get(x).supply(), howMuch.get(y).supply()));
            if (order.isEmpty()) {
                out.add("Nothing here is short.");
            } else {
                out.add("Short here (" + order.size() + " of "
                    + atlas.products().size() + " products), worst first:");
                for (int i = 0; i < Math.min(10, order.size()); i++) {
                    int j = order.get(i);
                    out.add("  " + howMuch.get(j).sentence(what.get(j)) + ".");
                }
            }
        }
        java.util.List<String> lines = out;
        source.sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
        return 1;
    }

    private static int executePrices(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double[] place = null;
        int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
        int at = -1;
        for (int i = 0; i < w.length; i++) {
            if (w[i].equalsIgnoreCase("at")) {
                at = i;
                break;
            }
        }
        boolean shortages = false;
        String good = null;
        try {
            if (at == 0) {
                if (w.length < 3) {
                    source.sendFailure(Component.literal("Usage: /ee prices at <lat> <lon> [year] [good]"));
                    return 0;
                }
                place = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
                int n = 3;
                if (w.length > n) {
                    try {
                        year = Integer.parseInt(w[n]);
                        n++;
                    } catch (NumberFormatException ignored) {
                        // not a year, so it must be the good
                    }
                }
                if (w.length > n) {
                    good = String.join(" ", java.util.Arrays.copyOfRange(w, n, w.length));
                }
            } else {
                java.util.List<String> words = new java.util.ArrayList<>();
                for (int i = 0; i < w.length; i++) {
                    if (w[i].equalsIgnoreCase("shortages")) {
                        shortages = true;
                    } else {
                        words.add(w[i]);
                    }
                }
                if (!words.isEmpty()) {
                    good = String.join(" ", words);
                }
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Usage: /ee prices [shortages] [at <lat> <lon>] [year] [good]"));
            return 0;
        }
        if (place == null) {
            place = geoOf(source);
        }
        if (place == null) {
            source.sendFailure(Component.literal("No place: stand somewhere, or say /ee prices at <lat> <lon>."));
            return 0;
        }

        final double[] placeFinal = place;
        com.eternalengine.money.Prices prices = com.eternalengine.money.Prices.get();
        java.util.Optional<com.eternalengine.money.Prices.Market> market = prices.marketAt(place[0], place[1]);
        String where = String.format(java.util.Locale.ROOT, "%.4f, %.4f", place[0], place[1]);
        String when = year < 0 ? (-year) + " BCE" : String.valueOf(year);

        if (market.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[EE prices] " + where + " in " + when
                + ": no market within 500 km was ever surveyed. The nearest is "
                + prices.nearestMarket(placeFinal[0], placeFinal[1], 40000.0)
                    .map(m -> m.place() + ", " + String.format(java.util.Locale.ROOT, "%.0f", m.distanceKm(placeFinal[0], placeFinal[1])) + " km away")
                    .orElse("nowhere at all") + "."), false);
            return 1;
        }
        com.eternalengine.money.Prices.Market m = market.get();

        if (shortages || good == null) {
            java.util.List<String> out = new java.util.ArrayList<>();
            out.add("[EE prices] " + m.place() + " (" + where + ") in " + when + ", from "
                + prices.marketList().size() + " markets and " + prices.goodList().size() + " goods, "
                + com.eternalengine.money.Prices.FIRST_YEAR + "-" + com.eternalengine.money.Prices.LAST_YEAR + ".");
            if (year < com.eternalengine.money.Prices.FIRST_YEAR || year > com.eternalengine.money.Prices.LAST_YEAR) {
                out.add("The year " + when + " is outside the record: it covers "
                    + com.eternalengine.money.Prices.FIRST_YEAR + " to "
                    + com.eternalengine.money.Prices.LAST_YEAR + " and nothing after 1914.");
            }
            if (shortages) {
                java.util.List<com.eternalengine.money.Prices.Shortage> near = prices.shortagesAround(year, 2);
                if (near.isEmpty()) {
                    out.add("No shortage recorded within two years of " + when + ", anywhere in the record.");
                } else {
                    out.add("Shortages within two years of " + when + " (" + near.size() + "):");
                    for (int i = 0; i < Math.min(8, near.size()); i++) {
                        out.add("  " + near.get(i).sentence() + ".");
                    }
                }
            } else {
                java.util.List<com.eternalengine.money.Prices.Good> gs = prices.goodsAt(m.id());
                if (gs.isEmpty()) {
                    out.add("This market priced nothing that survives.");
                } else {
                    StringBuilder sb = new StringBuilder("Priced here: ");
                    for (int i = 0; i < gs.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(gs.get(i).name());
                    }
                    out.add(sb.append(".").toString());
                    out.add("Say /ee prices <good> for a particular one, or /ee prices shortages for dear years.");
                }
            }
            for (String line : out) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        }

        java.util.Optional<com.eternalengine.money.Prices.Reading> r = prices.price(m.id(), good, year);
        if (r.isEmpty()) {
            final String goodMsg = good;
            source.sendSuccess(() -> Component.literal("[EE prices] " + m.place() + " in " + when
                + ": no price for '" + goodMsg + "' there in that year. The record has holes; "
                + "the goods priced here are listed by /ee prices."), false);
            return 1;
        }
        com.eternalengine.money.Prices.Reading read = r.get();
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("[EE prices] " + m.place() + " in " + when + ": " + read.sentence() + ".");
        if (read.suspect()) {
            out.add("The record's own conversion of this market's money into silver disagrees with "
                + "the same good everywhere else, so the silver figure below is not to be trusted; "
                + "the comparison with its usual price still holds, because a wrong conversion "
                + "cancels out of a ratio.");
        } else {
            out.add("About " + String.format(java.util.Locale.ROOT, "%.3f", read.grams())
                + " grams of silver per " + read.measure().toLowerCase() + ", against a usual "
                + String.format(java.util.Locale.ROOT, "%.3f", read.usual()) + " here.");
        }
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /** The year named after the place, or the year the world is in now. */
    private static int parseYear(CommandSourceStack source, String args, String usage) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
        if (w.length >= 4 && w[0].equalsIgnoreCase("at")) {
            try {
                year = Integer.parseInt(w[3]);
            } catch (NumberFormatException e) {
                source.sendFailure(Component.literal(usage));
            }
        }
        return year;
    }

    /** "22.9000, -43.2000" for a line of output. */
    private static String formatPlace(double[] place) {
        return String.format(java.util.Locale.ROOT, "%.4f, %.4f", place[0], place[1]);
    }

    /** "1888", or "44 BCE" for a year before nought. */
    private static String formatYear(int year) {
        return year < 0 ? (-year) + " BCE" : String.valueOf(year);
    }

    /**
     * {@code /ee signs [at <lat> <lon> [year]]} (step 43, J3 and E13, admin): <b>what is written
     * here, and in what script</b> — the signs put up where people pass, and the inscription cut
     * into stone.
     *
     * <p>Everything here is a reading except the year writing reached the place, which comes from
     * the technology atlas and is printed. Where the mod cannot name a script it says so rather
     * than guessing one.
     */
    private static int executeSigns(CommandSourceStack source, String args) {
        String usage = "Usage: /ee signs [at <lat> <lon> [year]]";
        double[] place = parsePlace(source, args, usage);
        if (place == null) {
            return 0;
        }
        int year = parseYear(source, args, usage);
        double lat = place[0];
        double lon = place[1];
        com.eternalengine.civilization.SettlementRank rank =
                com.eternalengine.civilization.SettlementRank.TOWN;

        com.eternalengine.civilization.Signs.Here here =
                com.eternalengine.civilization.Signs.get().hereAt(lat, lon, year, rank);
        com.eternalengine.news.Writings.Inscription cut =
                com.eternalengine.news.Writings.inscription(lat, lon, year, rank);

        List<String> out = new java.util.ArrayList<>();
        out.add("[EE signs] " + formatPlace(place) + " in " + formatYear(year) + ":");
        if (!here.writing()) {
            out.add("  " + here.line());
        } else {
            out.add("  " + here.line());
            out.add("  Cut into stone: " + cut.line());
        }
        out.add("  " + here.why()
                + (here.fromTable() ? " (this is the area-and-era reading, not a measured one)" : ""));
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee air [at <lat> <lon> [year]]} (step 43, J4, admin): <b>what the sky looks like
     * here</b> — smoke, dust and haze, and what is putting them there.
     *
     * <p>Derived, not tabled: the smoke from the technology atlas and how many people are living
     * here, the dust and the haze from the climate register's own dated periods, gated on severity
     * so that a geological stage is not read as weather.
     */
    private static int executeAir(CommandSourceStack source, String args) {
        String usage = "Usage: /ee air [at <lat> <lon> [year]]";
        double[] place = parsePlace(source, args, usage);
        if (place == null) {
            return 0;
        }
        int year = parseYear(source, args, usage);
        double lat = place[0];
        double lon = place[1];

        com.eternalengine.civilization.Air.Here here = com.eternalengine.civilization.Air.hereAt(
                lat, lon, year, com.eternalengine.civilization.SettlementRank.TOWN, 0L);

        List<String> out = new java.util.ArrayList<>();
        out.add("[EE air] " + formatPlace(place) + " in " + formatYear(year) + ":");
        out.add("  " + here.line());
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee letter [at <lat> <lon> [year]] to <lat> <lon>} (step 43, E13, admin): <b>what a
     * letter from here to there is, in this year</b> — what it is written on, in and with, how it
     * is closed, how it goes, how long it takes, and whether anyone at the far end can read it.
     *
     * <p>Say {@code to} and a place for a letter going there; without it you get a letter written
     * here and carried a day's journey away. Add {@code sea} for a sea route.
     */
    private static int executeLetter(CommandSourceStack source, String args) {
        String usage = "Usage: /ee letter [at <lat> <lon> [year]] [to <lat> <lon>] [sea]";
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        int year = com.eternalengine.knowledge.KnowledgeDate.year(
                com.eternalengine.knowledge.KnowledgeDate.today());
        double[] from = null;
        double[] to = null;
        boolean sea = false;
        try {
            int i = 0;
            if (i < w.length && w[i].equalsIgnoreCase("at") && i + 2 < w.length) {
                from = new double[] {Double.parseDouble(w[i + 1]), Double.parseDouble(w[i + 2])};
                i += 3;
            }
            if (i < w.length && w[i].equalsIgnoreCase("to") && i + 2 < w.length) {
                to = new double[] {Double.parseDouble(w[i + 1]), Double.parseDouble(w[i + 2])};
                i += 3;
            }
            for (; i < w.length; i++) {
                if (w[i].equalsIgnoreCase("sea")) {
                    sea = true;
                } else {
                    year = Integer.parseInt(w[i]);
                }
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal(usage));
            return 0;
        }
        if (from == null) {
            from = geoOf(source);
        }
        if (from == null) {
            source.sendFailure(Component.literal(usage + " - or stand somewhere and say it with no arguments"));
            return 0;
        }
        if (to == null) {
            // Without a destination, a letter to the next town: a day's journey away.
            to = new double[] {from[0] + 2.0d, from[1] + 2.0d};
        }

        com.eternalengine.news.Writings.Letter l = com.eternalengine.news.Writings.letter(
                from[0], from[1], to[0], to[1], year,
                com.eternalengine.civilization.SettlementRank.TOWN, sea);

        List<String> out = new java.util.ArrayList<>();
        out.add("[EE letter] " + formatPlace(from) + " -> " + formatPlace(to) + " in "
                + formatYear(year) + (sea ? ", by sea" : "") + ":");
        out.add("  " + l.line());
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * {@code /ee market [at <lat> <lon> [year]]} (step 19b, admin): what a merchant there pays for each good (here and
     * now without "at"), which goods nobody buys there that year, what a merchant can pay out in a day and the
     * money-changer's cut.
     */
    private static int executeMarket(CommandSourceStack source, String args) {
        String usage = "Usage: /ee market [at <lat> <lon> [year]]";
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double[] place = null;
        int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
        try {
            if (w.length >= 3 && w[0].equalsIgnoreCase("at")) {
                place = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
                if (w.length >= 4) {
                    year = Integer.parseInt(w[3]);
                }
            } else if (w.length > 0) {
                source.sendFailure(Component.literal(usage));
                return 0;
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal(usage));
            return 0;
        }
        if (place == null && source.getPlayer() != null) {
            place = com.eternalengine.money.PurseService.placeOf(source.getPlayer());
        }
        double lat = place == null ? Double.NaN : place[0];
        double lon = place == null ? Double.NaN : place[1];
        com.eternalengine.money.MoneyTable.Here h = com.eternalengine.money.PurseService.here(place, year);
        com.eternalengine.money.MoneyTable.Money m = h.money();
        com.eternalengine.money.MarketService.Terms t = com.eternalengine.money.MarketService.Terms.current();
        com.eternalengine.money.GoodsTable goods = com.eternalengine.money.GoodsTable.get();
        List<String> bought = new java.util.ArrayList<>();
        List<String> notBought = new java.util.ArrayList<>();
        List<String> noItem = new java.util.ArrayList<>();
        for (com.eternalengine.money.GoodsTable.Good g : goods.all()) {
            if (!g.sellable()) {
                noItem.add(g.name());
            } else if (!g.boughtAt(lat, lon, year)) {
                notBought.add(g.name());
            } else {
                double each = com.eternalengine.money.MarketService.pays(g, h, "market", t);
                bought.add(com.eternalengine.money.MarketService.priceEntry(g, each, m, year) + " (full price for "
                    + g.want() + " a day)");
            }
        }
        String where = place == null ? "an unknown place" : String.format(java.util.Locale.ROOT, "%.4f, %.4f", place[0], place[1]);
        String when = year < 0 ? (-year) + " BCE" : String.valueOf(year);
        List<String> out = new java.util.ArrayList<>();
        out.add("[EE market] " + where + " in " + when + ": " + m.name() + "; a day's pay "
            + com.eternalengine.money.MoneyFormat.amount(m, h.dayPay(), year) + ".");
        out.add("A merchant pays (" + t.buyPercent() + " in 100 of the worth): "
            + (bought.isEmpty() ? "nothing, nobody buys goods here" : String.join("; ", bought)) + ".");
        if (!notBought.isEmpty()) {
            out.add("Not bought here in " + when + ": " + String.join(", ", notBought) + ".");
        }
        if (!noItem.isEmpty()) {
            out.add("No item yet (they come with the product atlas): " + String.join(", ", noItem) + ".");
        }
        out.add("A merchant pays out at most " + com.eternalengine.money.MoneyFormat.amount(m,
            com.eternalengine.money.MarketService.budget(h, t), year) + " a Minecraft day (" + t.budgetDays()
            + " days' pay). The money-changer keeps " + t.feePercent() + " in 100 where the rate is fixed, "
            + t.strangeFeePercent() + " in 100 for money he values by what it buys.");
        // Step 19g: who buys here, on what terms, and the atlas products buyers take.
        out.add(whoBuys(lat, lon, year, h, t));
        List<String> atlasPrices = new java.util.ArrayList<>();
        for (com.eternalengine.money.GoodsTable.Good g : com.eternalengine.money.MarketService.atlasGoods(lat, lon, year)) {
            atlasPrices.add(com.eternalengine.money.MarketService.priceEntry(g,
                com.eternalengine.money.MarketService.pays(g, h, "market", t), m, year));
        }
        out.add("Atlas products a merchant takes here (" + atlasPrices.size() + "): "
            + (atlasPrices.isEmpty() ? "none" : String.join("; ", atlasPrices)) + ".");
        var gs = goods.summary();
        out.add("Goods table: " + gs.goods() + " goods, " + gs.withItems() + " with items"
            + (goods.problems().isEmpty() ? "." : "; problems: " + String.join("; ", goods.problems())));
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * Step 19g: "Who buys here: a trader (60 in 100, 4 days' pay a day; he changes money keeping 10 in 100)." From the
     * people of the place: a merchant, a trader, a pedlar, anybody swapping a little, or nobody (nobody lives here).
     */
    static String whoBuys(double lat, double lon, int year, com.eternalengine.money.MoneyTable.Here h,
                          com.eternalengine.money.MarketService.Terms t) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return "Who buys here: unknown (no place).";
        }
        HistoricalPresence.Result r;
        try {
            r = com.eternalengine.core.WorldQuery.presence(null, com.eternalengine.core.WorldKey.at(lat, lon, year));
        } catch (Throwable e) {
            return "Who buys here: unknown (" + e.getClass().getSimpleName() + ").";
        }
        if (!r.occupied()) {
            return "Who buys here: nobody, nobody lives here in this year (walk to where people live).";
        }
        String[] roles = r.spawnRoles();
        List<String> buyers = new java.util.ArrayList<>();
        for (String role : roles) {
            if (com.eternalengine.money.MarketService.buyer(role)) {
                com.eternalengine.money.MarketService.Terms rt = t.as(role);
                buyers.add("a " + role + " (" + rt.buyPercent() + " in 100, " + rt.budgetDays() + " days' pay a day"
                    + (com.eternalengine.money.MarketService.changes(role) ? "; he changes money keeping "
                    + rt.strangeFeePercent() + " in 100" : "") + ")");
            }
        }
        if (buyers.isEmpty() && com.eternalengine.money.MarketService.swapsALittle(roles, h)) {
            com.eternalengine.money.MarketService.Terms lt = t.as(com.eternalengine.money.MarketService.LOCAL);
            buyers.add("anybody swaps a little (" + lt.buyPercent() + " in 100, " + lt.budgetDays() + " day's pay a day)");
        }
        return "Who buys here (" + r.rank().name().toLowerCase(java.util.Locale.ROOT) + "): "
            + (buyers.isEmpty() ? "nobody" : String.join(", ", buyers)) + ".";
    }

    /**
     * {@code /ee talk [at <lat> <lon>] [level 1|2] [role <role>] [ask <card> [question] | card <card> | topic <topic> |
     * topics | free <words> | sell <good> <count:amount> | swap <money> <to:amount>]} (step 18): what the nearest
     * villager, or a villager at that spot, would say on the talk screen, as text. For testing; nothing is saved to
     * anyone's notes. Step 19b: sell and swap really trade, for the player who runs it (with the nearest villager).
     */
    private static int executeTalk(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        int i = 0;
        double lat = Double.NaN, lon = Double.NaN;
        int level = -1;
        String roleOverride = null;
        try {
            if (w.length >= 3 && w[0].equalsIgnoreCase("at")) {
                lat = Double.parseDouble(w[1]);
                lon = Double.parseDouble(w[2]);
                i = 3;
            }
            while (i + 1 < w.length && (w[i].equalsIgnoreCase("level") || w[i].equalsIgnoreCase("role"))) {
                if (w[i].equalsIgnoreCase("level")) {
                    level = Integer.parseInt(w[i + 1]);
                } else {
                    roleOverride = w[i + 1].toLowerCase(java.util.Locale.ROOT);
                }
                i += 2;
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Usage: /ee talk [at <lat> <lon>] [level 1|2] [role <role>] [ask <card> [question] | card <card> | topic <topic> | topics | free <words> | sell <good> <count:amount> | swap <money> <to:amount> | trade | buy <offer> <price>]"));
            return 0;
        }
        String action = i < w.length ? w[i].toLowerCase(java.util.Locale.ROOT) : "greet";
        String a = i + 1 < w.length ? w[i + 1] : "";
        String b = i + 2 < w.length ? w[i + 2] : "";
        if (action.equals("free")) {
            a = String.join(" ", java.util.Arrays.copyOfRange(w, Math.min(w.length, i + 1), w.length));
            b = "";
        }
        int date = com.eternalengine.knowledge.KnowledgeDate.today();
        com.eternalengine.knowledge.TalkService.Ctx ctx;
        String who;
        if (Double.isNaN(lat)) {
            net.minecraft.world.phys.Vec3 p = source.getPosition();
            com.eternalengine.entity.HumanNpcEntity npc = null;
            double best = Double.MAX_VALUE;
            for (com.eternalengine.entity.HumanNpcEntity e : source.getLevel().getEntitiesOfClass(
                    com.eternalengine.entity.HumanNpcEntity.class,
                    new net.minecraft.world.phys.AABB(p.x - 32, p.y - 16, p.z - 32, p.x + 32, p.y + 16, p.z + 32))) {
                double d = e.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    npc = e;
                }
            }
            if (npc == null) {
                source.sendFailure(Component.literal("No villager within 32 blocks. Stand near one, or use /ee talk at <lat> <lon>."));
                return 0;
            }
            var geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint(npc.blockPosition());
            double nLat = geo == null ? Double.NaN : geo.latitude();
            double nLon = geo == null ? Double.NaN : geo.longitude();
            String name = com.eternalengine.knowledge.TalkService.nameOf(npc, nLat, nLon, date);
            ctx = com.eternalengine.knowledge.TalkService.context(null, source.getServer(), npc.getId(), name,
                roleOverride != null ? roleOverride : npc.getRole(), nLat, nLon, date, level).with(source.getPlayer(), npc);
            who = name + " at " + npc.blockPosition().toShortString();
        } else {
            ctx = com.eternalengine.knowledge.TalkService.context(null, source.getServer(), -1, "Test villager",
                roleOverride != null ? roleOverride : level >= 2 ? "scribe" : "citizen", lat, lon, date, level)
                .with(source.getPlayer(), null);
            who = "a test villager";
        }
        if (action.equals("trade") || action.equals("buy")) {
            // Step 19c: the trade screen as text; "buy <offer> <price>" buys for the player who runs it.
            com.eternalengine.money.TradeView tv = com.eternalengine.knowledge.TalkService.trade(ctx, action, a, b);
            if (tv == null) {
                source.sendFailure(Component.literal("[EE talk] " + who + " sells nothing."));
                return 0;
            }
            StringBuilder out = new StringBuilder("[EE talk] " + who + ": " + tv.title + " (" + tv.job + "). Says: "
                + tv.says + (tv.purse.isEmpty() ? "" : "\n  " + tv.purse));
            for (var o : tv.offers) {
                out.append("\n  ").append(o.id()).append(" = ").append(o.name()).append(": ").append(o.priceText())
                    .append(" [price ").append(o.price()).append(']').append(o.left() >= 0 ? ", " + o.left() + " left today" : "")
                    .append(o.owned() ? ", you have it" : "");
            }
            String text = out.toString();
            source.sendSuccess(() -> Component.literal(text), false);
            return 1;
        }
        com.eternalengine.knowledge.TalkView v = action.equals("greet")
            ? com.eternalengine.knowledge.TalkService.open(ctx)
            : com.eternalengine.knowledge.TalkService.act(ctx, action, a, b);
        if (v == null) {
            source.sendSuccess(() -> Component.literal("[EE talk] Goodbye."), false);
            return 1;
        }
        StringBuilder out = new StringBuilder();
        java.util.List<String> packs = new java.util.ArrayList<>();
        for (var pack : ctx.packs()) {
            packs.add(pack.id());
        }
        out.append("[EE talk] ").append(who).append(": ").append(v.npcName).append(", ").append(v.job)
            .append(v.level >= 2 ? " (knows court news)" : " (knows street news)").append(". ").append(v.dateLabel)
            .append(Double.isNaN(ctx.lat()) ? ". Place unknown (no Terra++ position)"
                : String.format(java.util.Locale.ROOT, ". Here: %.4f, %.4f", ctx.lat(), ctx.lon()))
            .append(". Knowledge packs here: ").append(packs.isEmpty() ? "none" : String.join(", ", packs))
            .append(". Basic cards here: ").append(ctx.local().cards().size()).append('.');
        if (!v.cardTitle.isEmpty()) {
            out.append("\n  About: ").append(v.cardTitle).append(" (").append(v.cardKind).append(')');
        }
        out.append("\n  Says: ").append(v.text.replaceAll("\\[\\[(\\w+)\\|([^\\]]+)\\]\\]", "<$2>"));
        if (!v.hint.isEmpty()) {
            out.append("\n  Where: ").append(v.hint);
        }
        int n = 1;
        for (var o : v.options) {
            out.append("\n  ").append(n++).append(". ").append(o.label()).append("   [").append(o.action()).append(' ').append(o.a())
                .append(o.b().isEmpty() ? "" : " " + o.b()).append(']');
        }
        if (!v.names.isEmpty()) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (var nm : v.names) {
                names.add(nm.label() + " (" + nm.cardId() + ")");
            }
            out.append("\n  Ask about: ").append(String.join(", ", names));
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int executeBackup(CommandSourceStack source) {
        SimulationEngine.getInstance().forceSave();
        // A save makes a backup only every backupIntervalMinutes (step 14), so this command makes one itself.
        java.nio.file.Path state = WorldStateManager.getInstance().getStateFile();
        boolean ok = state != null && com.eternalengine.core.PersistenceManager.getInstance().createBackup(state);
        if (!ok) {
            source.sendFailure(Component.literal("Saved, but the backup could not be written (see the server log)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Saved, and backed up to " + state.getParent().resolve("backups")), true);
        return 1;
    }

    /** Who ran {@code /ee reset} last and when: {@code /ee reset confirm} must follow within a minute. */
    private static volatile String resetArmedBy;
    private static volatile long resetArmedAt;

    private static int executeResetPreview(CommandSourceStack source) {
        resetArmedBy = source.getTextName();
        resetArmedAt = System.currentTimeMillis();
        source.sendSuccess(() -> Component.literal("/ee reset confirm starts this world's simulation over: the year and"
            + " speed from the config, prime history only (every timeline branch removed), no wars, outbreaks, trade"
            + " routes or world flags, the civilizations as the data describes them. Blocks, buildings, items,"
            + " players and their VIP claims are not touched. Purses are emptied (everyone gets new start money). A backup of the current state is made first. Type /ee reset confirm within"
            + " one minute to do it."), false);
        return 1;
    }

    private static int executeReset(CommandSourceStack source) {
        boolean armed = source.getTextName().equals(resetArmedBy)
            && System.currentTimeMillis() - resetArmedAt <= 60_000L;
        if (!armed) {
            source.sendFailure(Component.literal("Type /ee reset first to see what a reset does, then /ee reset confirm"
                + " within one minute."));
            return 0;
        }
        resetArmedBy = null;
        if (!SimulationEngine.isInitialized()) {
            source.sendFailure(Component.literal("The simulation is not running"));
            return 0;
        }
        SimulationEngine engine = SimulationEngine.getInstance();
        engine.forceSave();
        java.nio.file.Path state = WorldStateManager.getInstance().getStateFile();
        if (state != null) {
            com.eternalengine.core.PersistenceManager.getInstance().createBackup(state);
        }
        String summary = engine.resetSimulation();
        if (summary == null) {
            source.sendFailure(Component.literal("The simulation is not running"));
            return 0;
        }
        LOGGER.warn("[Commands] Simulation reset by {}", source.getTextName());
        source.sendSuccess(() -> Component.literal("The simulation of this world starts over: " + summary
            + ". The state before the reset is in " + (state == null ? "the backups folder" : state.getParent().resolve("backups"))
            + ". Restart the server to also clear armies, caravans, courts, treaties and reputation."), true);
        return 1;
    }
}
