package com.lifewise.plan.service.exception;

/** BR-14：已 DONE 的里程碑不可修改 title/due_at/sort_order。映射到 400 MILESTONE_DONE_READONLY。 */
public class MilestoneDoneReadOnlyException extends RuntimeException {
    private final Long milestoneId;

    public MilestoneDoneReadOnlyException(Long milestoneId) {
        super("milestone " + milestoneId + " is DONE and read-only (BR-14)");
        this.milestoneId = milestoneId;
    }

    public Long getMilestoneId() { return milestoneId; }
}