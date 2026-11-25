package com.eternalengine.timeline;

import com.eternalengine.core.WorldStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic Butterfly Effect & Divergence Cascade Engine.
 * When an anchor historical event is prevented by player intervention,
 * this engine prunes obsolete future timeline branches, synthesizes authentic
 * alternate history consequences, shifts geopolitical stability, and spawns
 * a spatial Reality Rift at the pivot point.
 */
public final class DivergenceCascadeEngine {

    public record CascadeResult(
        String branchId,
        String pivotEventId,
        int divergenceYear,
        List<String> obsoletePrunedEvents,
        List<AlternativeEvent> alternateEvents,
        RealityRiftController.RealityRift spawnedRift,
        double stabilityDelta
    ) {}

    private final RealityRiftController riftController;

    public DivergenceCascadeEngine(RealityRiftController riftController) {
        this.riftController = riftController != null ? riftController : new RealityRiftController();
    }

    public CascadeResult triggerCascade(String pivotEventId, String cause, int year,
                                        BlockPos pivotPos, ServerLevel level,
                                        TimelineManager timelineManager, WorldStateManager state) {
        if (pivotEventId == null || timelineManager == null) {
            return null;
        }

        String eventNorm = pivotEventId.toLowerCase(Locale.ROOT);
        String causeDesc = cause != null && !cause.isBlank() ? cause : "Player historical intervention";
        long seedHash = (long) pivotEventId.hashCode() ^ ((long) year << 16) ^ (causeDesc.hashCode());
        String parentBranch = timelineManager.getActiveBranch() != null
            ? timelineManager.getActiveBranch().branchId()
            : "main";

        TimelineSeed seed = new TimelineSeed(seedHash, parentBranch, pivotEventId, year, "action_" + Math.abs(seedHash % 10000));

        // 1. Create diverging branch
        TimelineBranch branch = timelineManager.createDivergence(seed, causeDesc, state);

        // 2. Determine obsolete dependent events and alternate events
        List<String> prunedEvents = new ArrayList<>();
        List<AlternativeEvent> alternateEvents = new ArrayList<>();
        String primeEcho;
        double stabilityDelta;

        if (eventNorm.contains("franz_ferdinand") || eventNorm.contains("sarajevo") || (year >= 1914 && year <= 1918 && eventNorm.contains("assassination"))) {
            prunedEvents.add("outbreak_ww1_1914");
            prunedEvents.add("battle_somme_1916");
            prunedEvents.add("treaty_versailles_1919");

            alternateEvents.add(new AlternativeEvent(
                year + 1,
                "Trialist Imperial Constitutional Reform",
                "Archduke Franz Ferdinand survives and implements tripartite governance granting autonomy to Slavic lands.",
                AlternativeEvent.EventForm.DIVERGED,
                Map.of("stability", "increased", "tensions", "decreased")
            ));
            alternateEvents.add(new AlternativeEvent(
                year + 4,
                "Danubian Economic League Accord",
                "Central Europe avoids continental conflict, establishing an early European common market in Vienna.",
                AlternativeEvent.EventForm.DIVERGED,
                Map.of("trade", "expanded", "empires", "preserved")
            ));

            primeEcho = "Archduke Franz Ferdinand and Sophie are struck by assassin bullets on the street corner of Sarajevo. Four empires collapse in the ensuing World War. In this reality, they survived.";
            stabilityDelta = 25.0D;

        } else if (eventNorm.contains("caesar") || (year == -44 && eventNorm.contains("ides"))) {
            prunedEvents.add("liberators_civil_war_43bc");
            prunedEvents.add("battle_actium_31bc");
            prunedEvents.add("fall_roman_republic_27bc");

            alternateEvents.add(new AlternativeEvent(
                year + 1,
                "Senate Reconciliation Decree",
                "Julius Caesar pardons the conspirators, reforming senatorial representation with provincial magistrates.",
                AlternativeEvent.EventForm.DIVERGED,
                Map.of("republic", "reformed", "civil_war", "prevented")
            ));
            alternateEvents.add(new AlternativeEvent(
                year + 4,
                "Parthian Frontier Settlement",
                "Caesar leads the legions to a decisive negotiated settlement along the Euphrates river.",
                AlternativeEvent.EventForm.DIVERGED,
                Map.of("borders", "secured", "military", "ascendant")
            ));

            primeEcho = "Julius Caesar falls at the foot of Pompey's statue, pierced by twenty-three blades. Rome is plunged into thirteen years of civil war. In this reality, he reformed the Senate.";
            stabilityDelta = 30.0D;

        } else {
            // General event cascade
            prunedEvents.add(pivotEventId + "_consequence");
            alternateEvents.add(new AlternativeEvent(
                year + 1,
                "Post-Intervention Stabilization",
                "The prevention of " + pivotEventId + " averted regional crisis, altering the course of civilization.",
                AlternativeEvent.EventForm.DIVERGED,
                Map.of("stability", "stabilized")
            ));

            primeEcho = "In the Prime Timeline, " + pivotEventId + " concluded with devastating historical consequences. Your intervention has rewritten fate.";
            stabilityDelta = 15.0D;
        }

        // Apply pruned events and alternate events to branch
        for (String pe : prunedEvents) {
            branch.addPermanentlyPrevented(pe);
        }
        for (AlternativeEvent ae : alternateEvents) {
            branch.addEvent(ae);
        }

        // 3. Shift global state
        if (state != null) {
            state.setFlag("timeline.diverged." + branch.branchId(), "true");
            state.setFlag("timeline.stability_delta", Double.toString(stabilityDelta));
        }

        // 4. Spawn spatial Reality Rift
        RealityRiftController.RealityRift rift = null;
        if (pivotPos != null && level != null) {
            rift = riftController.spawnRift(pivotEventId, branch.branchId(), year, pivotPos, primeEcho, level);
        }

        return new CascadeResult(branch.branchId(), pivotEventId, year, prunedEvents, alternateEvents, rift, stabilityDelta);
    }

    public RealityRiftController getRiftController() {
        return riftController;
    }
}
