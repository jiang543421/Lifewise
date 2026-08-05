package com.lifewise.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.domain.MilestoneStatus;
import java.time.OffsetDateTime;

/**
 * Milestone 视图（plan-05-plan §3.2 全部 7 端点）。
 *
 * <p>completedAt 非 DONE 时省略。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MilestoneView(
        Long id,
        Long planId,
        Long userId,
        String title,
        String description,
        MilestoneStatus status,
        OffsetDateTime dueAt,
        String timeZone,
        Integer sortOrder,
        OffsetDateTime completedAt) {

    public static MilestoneView from(Milestone m) {
        return new MilestoneView(
            m.getId(),
            m.getPlanId(),
            m.getUserId(),
            m.getTitle(),
            m.getDescription(),
            m.getStatus(),
            m.getDueAt(),
            m.getTimeZone(),
            m.getSortOrder(),
            m.getCompletedAt());
    }
}