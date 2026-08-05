package com.lifewise.plan.service.exception;

/** BR-14：对未完成里程碑调用 reopen()。映射到 409 MILESTONE_NOT_DONE。 */
public class MilestoneNotDoneException extends RuntimeException {
    private final Long milestoneId;

    public MilestoneNotDoneException(Long milestoneId) {
        super("milestone " + milestoneId + " is not DONE; cannot reopen (BR-14)");
        this.milestoneId = milestoneId;
    }

    public Long getMilestoneId() { return milestoneId; }
}