package com.eternalengine.affairs;

import com.eternalengine.anomaly.AnomalyDetectionEngine;
import com.eternalengine.anomaly.AnomalyScanResult;
import com.eternalengine.civilization.EventNews;
import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.incident.IncidentRecord;
import com.eternalengine.people.Scenes;
import com.eternalengine.population.PopulationResolver;
import com.eternalengine.telemetry.WorldDebugSnapshot;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
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

        if (level != null && position != null) {
            try {
                var geo = TerraCoordinateHelper.toGeoPoint(position);
                var civs = SimulationEngine.getInstance().getCivilizationManager();
                HistoricalPresence.Result presence = com.eternalengine.core.WorldQuery.presence(civs, com.eternalengine.core.WorldKey.at(level, position, year));
                facts.add(new CurrentAffairsFact("place", EventNews.placeLine(presence), 96));
                // Talk of the land: ruler, languages and faiths, famous people (Wikipedia registers),
                // and the estimated way of life. Each is skipped when the data has nothing to say.
                String[][] land = {
                        {"ruler", EventNews.rulerLine(presence), "94"},
                        {"culture", EventNews.cultureLine(presence), "90"},
                        {"people", EventNews.famousLine(presence, year), "84"},
                        {"life", EventNews.wayOfLifeLine(presence), "80"},
                        {"trade", EventNews.tradeLine(presence), "82"},
                        {"road", EventNews.roadLine(presence), "78"}};
                for (String[] f : land) {
                    if (!f[1].isBlank()) {
                        facts.add(new CurrentAffairsFact(f[0], f[1], Integer.parseInt(f[2])));
                    }
                }
                presence.event().ifPresent(eh -> facts.add(new CurrentAffairsFact(
                        "event",
                        EventNews.rumor(eh.event(), year),
                        99)));
                // Step 39 (D9): what an incident has brought onto the street here. Three of the
                // twenty scenes hang off the incident engine -- an election, a protest and a
                // trial -- because an incident is generated rather than recorded, so no register
                // that already exists can say when one is on. The caller hands the name over and
                // the register casts the scene from the people this place actually has; a good
                // harvest sets nothing off, and that is deliberate.
                try {
                    var pop = PopulationResolver.get().resolve(
                            presence.site().map(h -> h.site().id()).orElse(null),
                            presence.lat(), presence.lon(), year, presence.rank());
                    long crowd = Math.max(1L, pop == null ? 200L : pop.population());
                    if (recentIncidents != null) {
                        for (IncidentRecord inc : recentIncidents) {
                            if (inc == null || inc.type() == null) {
                                continue;
                            }
                            String id = Scenes.incidentScene(inc.type().name());
                            if (id.isEmpty()) {
                                continue;
                            }
                            Scenes.Scene scene = Scenes.find(id);
                            if (scene == null) {
                                continue;
                            }
                            Scenes.Occasion occ = Scenes.make(scene,
                                    Scenes.Where.of(presence.lat(), presence.lon(), year)
                                            .town(crowd > 1000)
                                            .folk((int) Math.min(Integer.MAX_VALUE, crowd))
                                            .after(inc.type().name()));
                            if (occ != null) {
                                facts.add(new CurrentAffairsFact("scene", occ.line(), 80));
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // The scene register is optional at briefing time, as the gazetteer is.
                }
            } catch (Exception ignored) {
                // Gazetteer is optional at briefing time.
            }
        }

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
