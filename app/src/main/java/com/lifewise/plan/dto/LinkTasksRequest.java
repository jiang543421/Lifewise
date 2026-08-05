package com.lifewise.plan.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 关联任务到 milestone（plan-05-plan §3.2 附属 POST /milestones/{id}/tasks）。 */
public record LinkTasksRequest(@NotEmpty List<Long> taskIds) {
}