package com.lifewise.plan.service.exception;

/** BR-14：对已完成里程碑重复调用 complete()。映射到 409 MILESTONE_ALREADY_DONE。 */
public class MilestoneAlreadyDoneException extends RuntimeException {
    private final Long milestoneId;

    public MilestoneAlreadyDoneException(Long milestoneId) {
        super("milestone " + milestoneId + " is already DONE (BR-14)");
        this.milestoneId = milestoneId;
    }

    public Long getMilestoneId() { return milestoneId; }
}