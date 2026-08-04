package com.lifewise.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 创建 / 更新 plan 请求体（plan-05-plan §3.1 - 2 端点共用）。
 *
 * <p>更新请求允许字段为 null 表示保持原值（PATCH 语义）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 64) String type,
        LocalDate startDate,
        LocalDate targetEndDate) {
}