package com.lifewise.plan.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/** Plan 不存在或不属于当前用户。映射到 404 PLAN_NOT_FOUND。 */
public class PlanNotFoundException extends ResourceNotFoundException {
    public PlanNotFoundException(Long planId) {
        super("plan", planId);
    }
}