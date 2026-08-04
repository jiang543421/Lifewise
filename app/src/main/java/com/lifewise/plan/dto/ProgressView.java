package com.lifewise.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Plan 进度视图（plan-05-plan §3.3 - GET /api/plans/{id}/progress）。
 *
 * <p>含里程碑完成度 + 任务完成度 + 关联任务 ID 列表（用于前端交叉展示）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProgressView(
        Long planId,
        long completedMilestones,
        long totalMilestones,
        long completedTasks,
        long totalTasks,
        double ratio,
        List<Long> linkedTaskIds) {
}