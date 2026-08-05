package com.lifewise.plan.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/** Milestone 不存在或不属于当前用户。映射到 404 MILESTONE_NOT_FOUND。 */
public class MilestoneNotFoundException extends ResourceNotFoundException {
    public MilestoneNotFoundException(Long milestoneId) {
        super("milestone", milestoneId);
    }
}