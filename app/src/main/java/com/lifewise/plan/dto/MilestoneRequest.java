package com.lifewise.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Milestone 创建 / 更新请求体（plan-05-plan §3.2 - 2 端点共用）。
 *
 * <p>BR-29：{@code dueAt} + {@code timeZone} 一起传入形成时区快照。
 * {@code sortOrder} 默认为 0。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MilestoneRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        OffsetDateTime dueAt,
        @Size(max = 64) String timeZone,
        Integer sortOrder) {
}