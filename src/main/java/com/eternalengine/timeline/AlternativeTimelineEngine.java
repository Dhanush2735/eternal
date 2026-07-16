package com.eternalengine.timeline;

import com.eternalengine.core.WorldStateManager;

import java.util.Map;

/**
 * Deterministic alternative-timeline generator foundation.
 *
 * LLMs may later narrate these branches, but rule/seed logic owns the branch
 * identity and initial consequences.
 */
public final class AlternativeTimelineEngine {

    public TimelineBranch generateAlternative(TimelineSeed seed, WorldStateManager state, String cause) {
        if (seed == null) {
            seed = new TimelineSeed(0L, "main", "unknown_event", 0, "none");
        }
        TimelineBranch branch = new TimelineBranch(
            seed.branchId(),
            seed.parentBranchId(),
            seed.divergenceEventId(),
            seed.divergenceYear(),
            cause,
            seed.playerActionHash(),
            false
        );

        branch.addPermanentlyPrevented(seed.divergenceEventId());

        double instabilityRoll = seed.roll(seed.divergenceYear() + 1, "instability");
        if (instabilityRoll < 0.65d) {
            branch.addEvent(new AlternativeEvent(
                seed.divergenceYear() + 1,
                "Political instability after divergence",
                "The prevented event created a power vacuum and regional uncertainty.",
                AlternativeEvent.EventForm.DIVERGED,
                Map.of("stability", "decreased")
            ));
        } else {
            branch.addEvent(new AlternativeEvent(
                seed.divergenceYear() + 1,
                "Temporary stabilization after divergence",
                "The immediate crisis was avoided, but long-term consequences remain uncertain.",
                AlternativeEvent.EventForm.DIVERGED,
                Map.of("stability", "increased")
            ));
        }

        int probability = (int) Math.round(seed.roll(seed.divergenceYear() + 2, "future_form") * 100.0d);
        branch.addStillPossible(new PossibleFutureEvent(
            seed.divergenceEventId(),
            probability,
            "altered_form",
            "May reappear if regional pressure, war pressure, or instability rises."
        ));

        branch.setHistoryLogEntry("Timeline diverged at " + seed.divergenceYear()
            + " because " + (cause == null ? "an event was altered" : cause));
        return branch;
    }

    public double deterministicEventRoll(TimelineSeed seed, int year, String eventKey) {
        return seed == null ? 0.0d : seed.roll(year, eventKey);
    }
}
