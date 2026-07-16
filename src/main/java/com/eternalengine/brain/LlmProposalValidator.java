package com.eternalengine.brain;

/**
 * Multiplayer-safe validator for external brain proposals.
 *
 * Every proposal from Mindcraft, a remote gateway, or another advisory brain must
 * pass through this class before any Eternal system acts on it.
 */
public final class LlmProposalValidator {

    private static final double MIN_CONFIDENCE = 0.60d;

    public LlmValidationResult validate(LlmProposal proposal, LlmValidationContext context) {
        if (proposal == null) {
            return LlmValidationResult.rejected("proposal is null");
        }
        if (context == null) {
            return LlmValidationResult.rejected("validation context is null");
        }
        if (proposal.confidence() < MIN_CONFIDENCE) {
            return LlmValidationResult.rejected("proposal confidence below safety threshold");
        }
        if (ForbiddenLlmAction.matches(proposal.actionName())) {
            return LlmValidationResult.rejected("proposal attempted forbidden direct world/state mutation");
        }
        if (proposal.action() == LlmAction.UNKNOWN) {
            return LlmValidationResult.rejected("unknown or unsupported action: " + proposal.actionName());
        }
        if (context.simulationYear() < -3000 || context.simulationYear() > 9999) {
            return LlmValidationResult.rejected("simulation year outside supported range");
        }
        if (proposal.action().requiresVipAuthority()) {
            if (context.playerData() == null || !context.playerData().isVip()) {
                return LlmValidationResult.rejected("action requires VIP authority");
            }
        }
        if (proposal.action() == LlmAction.PROPOSE_BUILDING && !context.allowBuildingProposals()) {
            return LlmValidationResult.rejected("building proposals disabled by server policy");
        }
        if (proposal.action() == LlmAction.DECLARE_WAR && context.simulationYear() < -3000) {
            return LlmValidationResult.rejected("war proposal before supported simulation start");
        }
        return LlmValidationResult.accepted(proposal.action(), "proposal accepted for server decision queue");
    }
}
