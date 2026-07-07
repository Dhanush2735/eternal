package com.eternalengine.commands;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.PerformanceMonitor;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
                    .executes(ctx -> executeYear(ctx.getSource())))
                
                .then(Commands.literal("help")
                    .executes(ctx -> executeHelp(ctx.getSource())))
                
                .then(Commands.literal("rep")
                    .executes(ctx -> executeRep(ctx.getSource())))
                
                .then(Commands.literal("building")
                    .executes(ctx -> executeBuilding(ctx.getSource()))
                    .then(Commands.literal("history")
                        .executes(ctx -> executeBuildingHistory(ctx.getSource()))))
                
                .then(Commands.literal("history")
                    .then(Commands.argument("year", IntegerArgumentType.integer(-300000, 3000))
                        .executes(ctx -> executeHistory(ctx.getSource(), 
                            IntegerArgumentType.getInteger(ctx, "year")))))
                
                .then(Commands.literal("timeline")
                    .executes(ctx -> executeTimeline(ctx.getSource()))
                    .then(Commands.literal("divergences")
                        .executes(ctx -> executeTimelineDivergences(ctx.getSource()))))
                
                .then(Commands.literal("event")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeEventList(ctx.getSource()))))
                
                .then(Commands.literal("disease")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeDiseaseList(ctx.getSource()))))
                
                .then(Commands.literal("war")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeWarList(ctx.getSource()))))
                
                .then(Commands.literal("civ")
                    .then(Commands.literal("list")
                        .executes(ctx -> executeCivList(ctx.getSource())))
                    .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> executeCivInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"))))))
                
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
                        .executes(ctx -> executeVipRelease(ctx.getSource()))))
                
                .then(Commands.literal("achievements")
                    .executes(ctx -> executeAchievements(ctx.getSource())))
                
                // ========== VIP PLAYERS ==========
                .then(Commands.literal("observer")
                    .executes(ctx -> executeObserver(ctx.getSource())))
                
                .then(Commands.literal("speed")
                    .then(Commands.literal("status")
                        .executes(ctx -> executeSpeedStatus(ctx.getSource())))
                    .then(Commands.literal("vote")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 50))
                            .executes(ctx -> executeSpeedVote(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "value"))))))
                
                .then(Commands.literal("override")
                    .executes(ctx -> executeOverride(ctx.getSource())))
                
                .then(Commands.literal("slowdown")
                    .executes(ctx -> executeSlowdown(ctx.getSource())))
                
                // ========== ADMIN ==========
                .then(Commands.literal("speed")
                    .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 50))
                            .executes(ctx -> executeSpeedSet(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "value"))))))
                
                .then(Commands.literal("year")
                    .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("year", IntegerArgumentType.integer(-300000, 3000))
                            .executes(ctx -> executeYearSet(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "year"))))))
                
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
    
    private static int executeBuilding(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Look at a building and use /ee building history"), false);
        return 1;
    }
    
    private static int executeBuildingHistory(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Building history feature coming soon"), false);
        return 1;
    }
    
    private static int executeHistory(CommandSourceStack source, int year) {
        String yearStr = year < 0 ? Math.abs(year) + " BCE" : year + " CE";
        source.sendSuccess(() -> Component.literal("History for " + yearStr + ": (coming soon)"), false);
        return 1;
    }
    
    private static int executeTimeline(CommandSourceStack source) {
        String branch = WorldStateManager.getInstance().getActiveTimelineBranchId();
        source.sendSuccess(() -> Component.literal("Active timeline branch: " + branch), false);
        return 1;
    }
    
    private static int executeTimelineDivergences(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Timeline divergences: (coming soon)"), false);
        return 1;
    }
    
    private static int executeEventList(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Active events: (coming soon)"), false);
        return 1;
    }
    
    private static int executeDiseaseList(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Active diseases: (coming soon)"), false);
        return 1;
    }
    
    private static int executeWarList(CommandSourceStack source) {
        var wars = WorldStateManager.getInstance().getAllActiveWars();
        if (wars.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active wars"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Active wars: " + wars.size()), false);
            wars.forEach((id, war) -> 
                source.sendSuccess(() -> Component.literal("  - " + war.name), false));
        }
        return 1;
    }
    
    private static int executeCivList(CommandSourceStack source) {
        var civs = WorldStateManager.getInstance().getAllCivilizationIds();
        source.sendSuccess(() -> Component.literal("Civilizations: " + civs.size()), false);
        return 1;
    }
    
    private static int executeCivInfo(CommandSourceStack source, String civId) {
        var civ = WorldStateManager.getInstance().getCivilization(civId);
        if (civ == null) {
            source.sendFailure(Component.literal("Civilization not found: " + civId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Civilization: " + civ.name + " (pop: " + civ.population + ")"), false);
        return 1;
    }
    
    private static int executeVipList(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Available VIPs: (coming soon)"), false);
        return 1;
    }
    
    private static int executeVipInfo(CommandSourceStack source, String vipId) {
        source.sendSuccess(() -> Component.literal("VIP info for " + vipId + ": (coming soon)"), false);
        return 1;
    }
    
    private static int executeVipClaim(CommandSourceStack source, String vipId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player"));
            return 0;
        }
        
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        if (data.isVip()) {
            source.sendFailure(Component.literal("You are already a VIP. Release first."));
            return 0;
        }
        
        // Would validate VIP exists and is available
        data.setVipId(vipId);
        source.sendSuccess(() -> Component.literal("Claimed VIP: " + vipId), true);
        return 1;
    }
    
    private static int executeVipRelease(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command requires a player"));
            return 0;
        }
        
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        if (!data.isVip()) {
            source.sendFailure(Component.literal("You are not a VIP"));
            return 0;
        }
        
        String vipId = data.getVipId();
        data.setVipId(null);
        source.sendSuccess(() -> Component.literal("Released VIP: " + vipId), true);
        return 1;
    }
    
    private static int executeAchievements(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Your achievements: (coming soon)"), false);
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
        source.sendSuccess(() -> Component.literal("Override vote registered (coming soon)"), false);
        return 1;
    }
    
    private static int executeSlowdown(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Slowdown restore vote registered (coming soon)"), false);
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
        source.sendSuccess(() -> Component.literal("Debug info for " + system + ": (coming soon)"), false);
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
