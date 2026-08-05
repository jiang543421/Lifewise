package com.lifewise.plan.service.exception;

/** 对已 CANCELLED 的 Plan 调用 abandon / create milestone。映射到 409 PLAN_ALREADY_ABANDONED。 */
public class PlanAlreadyAbandonedException extends RuntimeException {
    private final Long planId;

    public PlanAlreadyAbandonedException(Long planId) {
        super("plan " + planId + " is already abandoned (CANCELLED)");
        this.planId = planId;
    }

    public Long getPlanId() { return planId; }
}