package com.eternalengine.affairs;

import com.eternalengine.anomaly.AnomalyDetectionEngine;
import com.eternalengine.anomaly.AnomalyScanResult;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.incident.IncidentRecord;
import com.eternalengine.telemetry.WorldDebugSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-side current affairs engine.
 *
 * Bots should explain these facts; they must not invent unsupported current
 * affairs. LLMs may rephrase the briefing but must not add new facts.
 */
public final class CurrentAffairsEngine {

    private final CurrentAffairsMemory memory;
    private final AnomalyDetectionEngine anomalyDetectionEngine;

    public CurrentAffairsEngine() {
        this(new CurrentAffairsMemory(), new AnomalyDetectionEngine());
    }

    public CurrentAffairsEngine(CurrentAffairsMemory memory, AnomalyDetectionEngine anomalyDetectionEngine) {
        this.memory = memory == null ? new CurrentAffairsMemory() : memory;
        this.anomalyDetectionEngine = anomalyDetectionEngine == null ? new AnomalyDetectionEngine() : anomalyDetectionEngine;
    }

    public CurrentAffairsBriefing createBriefing(WorldStateManager state,
                                                 @Nullable ServerLevel level,
                                                 @Nullable BlockPos position,
                                                 BotAffairsRole role,
                                                 AffairsScope scope,
                                                 List<IncidentRecord> recentIncidents) {
        if (state == null) {
            return new CurrentAffairsBriefing(0, "global", scope, role,
                List.of(new CurrentAffairsFact("system", "World state is unavailable.", 100)),
                BotAffairsPersonality.focus(role), "WorldStateManager missing");
        }

        int year = SimulationClockManager.isInitialized()
            ? SimulationClockManager.getInstance().getCurrentYear()
            : state.getCurrentYear();
        String regionId = regionId(level, position);
        List<CurrentAffairsFact> facts = new ArrayList<>();

        facts.add(new CurrentAffairsFact("time", "The current simulation year is " + formatYear(year) + ".", 100));
        facts.add(new CurrentAffairsFact("timeline", "The active timeline branch is " + state.getActiveTimelineBranchId() + ".", 92));

        Map<String, WorldStateManager.WarState> wars = state.getAllActiveWars();
        if (wars.isEmpty()) {
            if (scope == AffairsScope.WAR || role == BotAffairsRole.SOLDIER || role == BotAffairsRole.ADVISOR) {
                facts.add(new CurrentAffairsFact("war", "No active wars are recorded in server state.", 70));
            }
        } else {
            facts.add(new CurrentAffairsFact("war", "Active wars recorded: " + wars.size() + ".", 95));
            wars.values().stream().limit(5).forEach(war ->
                facts.add(new CurrentAffairsFact("war", "War: " + safe(war.name, war.warId) + ".", 88)));
        }

        if (recentIncidents != null && !recentIncidents.isEmpty()) {
            recentIncidents.stream().limit(5).forEach(incident ->
                facts.add(new CurrentAffairsFact("incident", incident.toSummaryString(), 82)));
        }

        if (position != null && level != null) {
            WorldDebugSnapshot snapshot = WorldDebugSnapshot.capture(level, position.getX(), position.getZ());
            if (snapshot.valid()) {
                facts.add(new CurrentAffairsFact("local", "Here: " + snapshot.toShortLine(), 78));
                if (scope == AffairsScope.DEBUG || role == BotAffairsRole.ADVISOR || role == BotAffairsRole.HISTORIAN) {
                    AnomalyScanResult anomalyResult = anomalyDetectionEngine.detectSnapshot(snapshot, year);
                    if (anomalyResult.hasAnomalies()) {
                        facts.add(new CurrentAffairsFact("anomaly", anomalyResult.toSummaryString(), 90));
                        anomalyResult.topLines(3).forEach(line ->
                            facts.add(new CurrentAffairsFact("anomaly", line, 86)));
                    } else {
                        facts.add(new CurrentAffairsFact("anomaly", "No anomaly detected at this exact position.", 65));
                    }
                }
            } else {
                facts.add(new CurrentAffairsFact("local", "Local Terra++ debug facts are unavailable here.", 70));
            }
        }

        if (facts.size() <= 2) {
            facts.add(new CurrentAffairsFact("general", "No major active affairs are known from server facts.", 50));
        }

        CurrentAffairsBriefing briefing = new CurrentAffairsBriefing(
            year, regionId, scope, role, facts, BotAffairsPersonality.focus(role), null);
        memory.remember(briefing);
        return briefing;
    }

    public String explainForBot(CurrentAffairsBriefing briefing) {
        if (briefing == null) {
            return "I do not have reliable current-affairs facts right now.";
        }
        return BotAffairsPersonality.prefix(briefing.role()) + " for " + briefing.regionId() + ": "
            + briefing.toChatSummary();
    }

    public CurrentAffairsMemory memory() {
        return memory;
    }

    private String regionId(@Nullable ServerLevel level, @Nullable BlockPos position) {
        if (level == null || position == null) {
            return "global";
        }
        return level.dimension().location() + ":chunk:"
            + Math.floorDiv(position.getX(), 16) + ":" + Math.floorDiv(position.getZ(), 16);
    }

    private String formatYear(int year) {
        return year < 0 ? Math.abs(year) + " BCE" : year + " CE";
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
