package com.lifewise.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.domain.PlanStatus;
import java.time.LocalDate;

/**
 * Plan 视图（plan-05-plan §3.1 GET / GET list / POST / PUT / POST abandon）。
 *
 * <p>snake_case JSON 字段名；nullable 字段（如 description、dates）缺省省略。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanView(
        Long id,
        Long userId,
        String title,
        String description,
        String type,
        PlanStatus status,
        LocalDate startDate,
        LocalDate targetEndDate) {

    public static PlanView from(Plan p) {
        return new PlanView(
            p.getId(),
            p.getUserId(),
            p.getTitle(),
            p.getDescription(),
            p.getType(),
            p.getStatus(),
            p.getStartDate(),
            p.getTargetEndDate());
    }
}