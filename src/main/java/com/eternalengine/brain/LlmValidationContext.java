package com.eternalengine.brain;

import com.eternalengine.capability.IPlayerSimulationData;

/**
 * Server facts used to validate a proposal.
 */
public final class LlmValidationContext {

    private final String actorPlayerUuid;
    private final IPlayerSimulationData playerData;
    private final int simulationYear;
    private final boolean adminApproved;
    private final boolean allowBuildingProposals;

    public LlmValidationContext(String actorPlayerUuid, IPlayerSimulationData playerData,
                                int simulationYear, boolean adminApproved, boolean allowBuildingProposals) {
        this.actorPlayerUuid = actorPlayerUuid == null ? "" : actorPlayerUuid;
        this.playerData = playerData;
        this.simulationYear = simulationYear;
        this.adminApproved = adminApproved;
        this.allowBuildingProposals = allowBuildingProposals;
    }

    public String actorPlayerUuid() { return actorPlayerUuid; }
    public IPlayerSimulationData playerData() { return playerData; }
    public int simulationYear() { return simulationYear; }
    public boolean adminApproved() { return adminApproved; }
    public boolean allowBuildingProposals() { return allowBuildingProposals; }
}
