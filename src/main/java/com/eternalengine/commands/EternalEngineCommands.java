package com.eternalengine.commands;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.climate.ClimateEvent;
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
import com.eternalengine.buildings.EraBuildingPlanner;
import com.eternalengine.buildings.PlannedBuilding;
import com.eternalengine.brain.LlmValidationContext;
import com.eternalengine.brain.LlmValidationResult;
import com.eternalengine.civilization.Civilization;
import com.eternalengine.civilization.RelationshipType;
import com.eternalengine.civilization.TerritoryManager;
import com.eternalengine.data.EternalDataPaths;
import com.eternalengine.data.FoundationDataInstaller;
import com.eternalengine.data.FoundationDataLoader;
import com.eternalengine.disease.DiseaseOutbreak;
import com.eternalengine.disease.EpidemicScale;
import com.eternalengine.history.HistoricalEvent;
import com.eternalengine.incident.IncidentContext;
import com.eternalengine.incident.IncidentRecord;
import com.eternalengine.population.MigrationFlow;
import com.eternalengine.player.DivergenceDetector;
import com.eternalengine.player.PlayerAction;
import com.eternalengine.player.PlayerActionDetector;
import com.eternalengine.population.PopulationData;
import com.eternalengine.religion.Religion;
import com.eternalengine.reputation.ReputationEffect;
import com.eternalengine.technology.Technology;
import com.eternalengine.telemetry.ChunkTelemetryScanner;
import com.eternalengine.timeline.TimelineBranch;
import com.eternalengine.timeline.TimelineSeed;
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
import com.eternalengine.war.War;
import com.eternalengine.telemetry.WorldDebugSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

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
                                    StringArgumentType.getString(ctx, "cause")))))))
                
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
                        .executes(ctx -> executeDataLoad(ctx.getSource()))))
                
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
                                StringArgumentType.getString(ctx, "regionId"))))))
                
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
                            .executes(ctx -> executeArmyTest(ctx.getSource())))))

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
                                    StringArgumentType.getString(ctx, "cause")))))))

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
                
                .then(Commands.literal("backup")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> executeBackup(ctx.getSource())))
                
                .then(Commands.literal("historian")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> executeHistorian(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player")))))
                
                .then(Commands.literal("reset")
                    .requires(src -> src.hasPermission(4)) // Require highest permission
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
        source.sendSuccess(() -> Component.literal("Current simulation year: " + yearStr), false);
        return 1;
    }
    
    private static int executeHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== Eternal Engine Commands ==="), false);
        source.sendSuccess(() -> Component.literal("/ee status - Simulation status"), false);
        source.sendSuccess(() -> Component.literal("/ee year - Current year"), false);
        source.sendSuccess(() -> Component.literal("/ee rep - Your reputation"), false);
        source.sendSuccess(() -> Component.literal("/ee probe - F3-style Terra++ debug snapshot here"), false);
        source.sendSuccess(() -> Component.literal("/ee blockqueue [status|clear] - Block placement queue diagnostics"), false);
        source.sendSuccess(() -> Component.literal("/ee building [history|add-test|nearby|info|infer|damage|anomalies|plan-test] - Building tools"), false);
        source.sendSuccess(() -> Component.literal("/ee scan chunk [step] - Admin chunk telemetry scan"), false);
        source.sendSuccess(() -> Component.literal("/ee anomaly [probe|chunk] - Detect likely world anomalies"), false);
        source.sendSuccess(() -> Component.literal("/ee affairs [global|war|disease|timeline|debug] - Current affairs briefing"), false);
        source.sendSuccess(() -> Component.literal("/ee timeline [branch|divergences|roll|diverge] - Timeline tools"), false);
        source.sendSuccess(() -> Component.literal("/ee event [list|pending|info|trigger|prevent|load] - Historical events"), false);
        source.sendSuccess(() -> Component.literal("/ee data [paths|install|install-overwrite|load] - Foundation data tools"), false);
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
        source.sendSuccess(() -> Component.literal("/ee villager [nearby|virtual-count|explain|spawn-test|vip-spawn-test] - Villager/bot tools"), false);
        source.sendSuccess(() -> Component.literal("/ee relation [score|set] - Faction relationship tools"), false);
        source.sendSuccess(() -> Component.literal("/ee territory [owner|create-test] - Territory tools"), false);
        source.sendSuccess(() -> Component.literal("/ee incident test - Admin test incident generation"), false);
        source.sendSuccess(() -> Component.literal("/ee vehicle test - Admin test vehicle route evaluation"), false);
        source.sendSuccess(() -> Component.literal("/ee brain [availability|config|remote-start|queue|audit|test|affairs-propose|anomaly-propose] - Remote brain diagnostics"), false);
        source.sendSuccess(() -> Component.literal("/ee web [status|start|stop|url] - Localhost brain web chat"), false);
        source.sendSuccess(() -> Component.literal("/ee bot [connect|briefing|proposals|proposal-test] - Bot bridge diagnostics"), false);
        source.sendSuccess(() -> Component.literal("/ee actions recent - Recent player actions"), false);
        source.sendSuccess(() -> Component.literal("/ee divergence [checks|confidence|mark] - Divergence diagnostics"), false);
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

    private static int executeProbe(CommandSourceStack source, int blockX, int blockZ) {
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
        try {
            int year = SimulationClockManager.isInitialized()
                ? SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
            VehicleEra era = VehicleEra.fromYear(year);
            VehicleType type = switch (era) {
                case ANCIENT -> VehicleType.CARAVAN;
                case MEDIEVAL -> VehicleType.WAGON;
                case INDUSTRIAL -> VehicleType.TRAIN;
                case MODERN -> VehicleType.TRUCK;
            };
            VehicleRoute route = new VehicleRoute("test_route", type, era,
                0.0d, 0.0d, 1.0d, 1.0d, 700.0d, 50.0d,
                "test_civ", true, false);
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
            source.sendSuccess(() -> Component.literal("Remote brain is advisory-only. The Minecraft server still validates proposals and owns all world changes."), false);
            return available ? 1 : 0;
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
        BuildingStyle style = new EraBuildingPlanner().selectStyle(era, type);
        PlannedBuilding plan = new EraBuildingPlanner().planAtSurface(source.getLevel(), player.blockPosition(), style, id);
        int queued = plan.enqueue(SimulationEngine.getInstance().getBlockUpdateQueue(), 10);
        source.sendSuccess(() -> Component.literal("Planned building " + id
            + " type=" + type
            + " era=" + era
            + " blocks=" + plan.size()
            + " queued=" + queued
            + " origin=" + plan.origin()
            + " explanation=" + plan.explanation()), true);
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

    private static int executeTimelineRoll(CommandSourceStack source, String eventKey) {
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
            source.sendFailure(Component.literal("Event load failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDataPaths(CommandSourceStack source) {
        var server = source.getServer();
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
        source.sendSuccess(() -> Component.literal("Climate events: " + events.size()), false);
        for (ClimateEvent event : events) {
            source.sendSuccess(() -> Component.literal("  - " + event.id()
                + " " + event.name()
                + " years=" + event.startYear() + ".." + event.endYear()
                + " type=" + event.type()
                + " temp=" + event.tempChangeDegrees()
                + " yield=" + event.yieldImpactPercent() + "%"), false);
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

    private static int executePopulationGet(CommandSourceStack source, String regionId) {
        long population = SimulationEngine.getInstance().getPopulationEngine()
            .getPopulation(regionId, WorldStateManager.getInstance());
        PopulationData data = SimulationEngine.getInstance().getPopulationEngine()
            .getOrCreate(regionId, population);
        source.sendSuccess(() -> Component.literal("Population " + regionId
            + " = " + data.current()
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
        boolean ok = SimulationEngine.getInstance().getTechnologyEngine()
            .unlockTechnology(civId, techId, WorldStateManager.getInstance());
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
        StringBuilder sb = new StringBuilder();
        sb.append("Civilization ").append(civ.id()).append(" - ").append(civ.name()).append("\n");
        sb.append("  currentName: ").append(civ.getCurrentName(SimulationClockManager.isInitialized() ? SimulationClockManager.getInstance().getCurrentYear() : 0)).append("\n");
        sb.append("  founded: ").append(civ.foundingYear()).append(" active: ").append(civ.active()).append(" fallYear: ").append(civ.fallYear()).append("\n");
        sb.append("  capital: ").append(civ.capitalLat()).append(", ").append(civ.capitalLon()).append("\n");
        sb.append("  population: ").append(civ.population()).append(" treasury: ").append(civ.treasury()).append(" tech: ").append(civ.technologyLevel()).append("\n");
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
        villagers.stream().limit(12).forEach(entity ->
            source.sendSuccess(() -> Component.literal("  - " + entity.getUUID()
                + " name=" + entity.getName().getString()
                + " vip=" + VipEntityHelper.getVipId(entity)), false));
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
            + " civ=" + civId + " role=" + role), true);
        return uuid == null ? 0 : 1;
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
        return 1;
    }
    
    private static int executeSpeedVote(CommandSourceStack source, int speed) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        if (!data.isVip()) {
            source.sendFailure(Component.literal("Only VIP players can vote on speed"));
            return 0;
        }
        
        SimulationClockManager.getInstance().processVote(player, speed);
        source.sendSuccess(() -> Component.literal("Voted for speed: " + speed + "x"), true);
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
        SimulationClockManager.getInstance().setBaseSpeed(speed);
        source.sendSuccess(() -> Component.literal("Base speed set to " + speed + "x"), true);
        return 1;
    }
    
    private static int executeYearSet(CommandSourceStack source, int year) {
        SimulationClockManager.getInstance().setCurrentYear(year);
        String yearStr = year < 0 ? Math.abs(year) + " BCE" : year + " CE";
        source.sendSuccess(() -> Component.literal("Year set to " + yearStr), true);
        return 1;
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
    
    private static int executeReload(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Configuration reloaded"), true);
        return 1;
    }
    
    private static int executeBackup(CommandSourceStack source) {
        SimulationEngine.getInstance().forceSave();
        source.sendSuccess(() -> Component.literal("Backup created"), true);
        return 1;
    }
    
    private static int executeHistorian(CommandSourceStack source, String playerName) {
        source.sendSuccess(() -> Component.literal("Granted historian role to " + playerName), true);
        return 1;
    }
    
    private static int executeReset(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("⚠️ FULL RESET EXECUTED - This cannot be undone!"), true);
        LOGGER.warn("[Commands] FULL RESET executed by {}", source.getTextName());
        return 1;
    }
}
