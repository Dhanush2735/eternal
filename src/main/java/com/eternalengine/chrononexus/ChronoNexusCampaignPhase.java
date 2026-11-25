package com.eternalengine.chrononexus;

/**
 * The 7 Campaign Phases of Chrono-Nexus: The Temporal Paradox.
 */
public enum ChronoNexusCampaignPhase {
    PHASE_1_DAWN_OF_TIME(1, "The Dawn of Time (-3000 BC)", "Survive the prehistoric era and recover the mysterious dormant Time Machine."),
    PHASE_2_PARALLEL_THREAT(2, "The Parallel Threat", "Defend against the first Rival incursion and uncover the multiversal purge."),
    PHASE_3_REWRITING_HISTORY(3, "Rewriting History", "Gather era-specific resources, infiltrate VIP thrones, and forge your civilization war machine."),
    PHASE_4_DIMENSIONAL_BREACH(4, "Breaching the Mirror Dimension", "Defeat elite Rival commandos and claim the Dimension Machine."),
    PHASE_5_TEMPORAL_PARADOX(5, "The Loop Closers (The Paradox)", "Jump back to Year -3000 BC before the countdown expires to secretly deliver the original Time Machine to your past self."),
    PHASE_6_CITADEL_RAID(6, "The Citadel of Zero", "Launch a vertical multiversal assault directly into the heart of the Rival Dimension."),
    PHASE_7_THE_CHOICE(7, "The Multiversal Destiny", "Choose the fate of the multiverse: The Infinite Horizon, The Sovereign Cycle, or The Severed Knot.");

    private final int phaseNumber;
    private final String title;
    private final String objective;

    ChronoNexusCampaignPhase(int phaseNumber, String title, String objective) {
        this.phaseNumber = phaseNumber;
        this.title = title;
        this.objective = objective;
    }

    public int getPhaseNumber() {
        return phaseNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getObjective() {
        return objective;
    }

    public static ChronoNexusCampaignPhase fromNumber(int num) {
        for (ChronoNexusCampaignPhase p : values()) {
            if (p.phaseNumber == num) {
                return p;
            }
        }
        return PHASE_1_DAWN_OF_TIME;
    }
}
