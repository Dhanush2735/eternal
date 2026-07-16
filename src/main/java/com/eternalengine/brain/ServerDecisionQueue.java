package com.eternalengine.brain;

import com.eternalengine.EternalEngineMod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accepted proposal queue.
 *
 * This queue stores validated decisions for later system-specific handlers. It
 * does not directly place blocks, edit saves, or mutate timelines.
 */
public final class ServerDecisionQueue {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private final ConcurrentLinkedQueue<QueuedDecision> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger acceptedCount = new AtomicInteger();
    private final AtomicInteger rejectedCount = new AtomicInteger();
    private final LlmProposalValidator validator;

    public ServerDecisionQueue() {
        this(new LlmProposalValidator());
    }

    public ServerDecisionQueue(LlmProposalValidator validator) {
        this.validator = validator == null ? new LlmProposalValidator() : validator;
    }

    public LlmValidationResult submit(LlmProposal proposal, LlmValidationContext context) {
        LlmValidationResult result = validator.validate(proposal, context);
        if (result.accepted()) {
            queue.offer(new QueuedDecision(proposal, result, System.currentTimeMillis()));
            acceptedCount.incrementAndGet();
            LOGGER.debug("[Brain] Accepted proposal {} action={}", proposal.proposalId(), result.normalizedAction());
        } else {
            rejectedCount.incrementAndGet();
            LOGGER.debug("[Brain] Rejected proposal: {}", result.reason());
        }
        return result;
    }

    public List<QueuedDecision> drain(int maxCount) {
        int limit = Math.max(0, maxCount);
        List<QueuedDecision> drained = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            QueuedDecision decision = queue.poll();
            if (decision == null) {
                break;
            }
            drained.add(decision);
        }
        return drained;
    }

    public int size() {
        return queue.size();
    }

    public int acceptedCount() { return acceptedCount.get(); }
    public int rejectedCount() { return rejectedCount.get(); }

    public record QueuedDecision(LlmProposal proposal, LlmValidationResult validation, long queuedAtMs) {
    }
}
