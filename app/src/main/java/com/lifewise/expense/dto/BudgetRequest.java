package com.lifewise.expense.dto;

import com.lifewise.expense.domain.enums.BudgetScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 创建/更新预算请求（plan-03-expense §3.3）。
 *
 * <p>BR-10：scope + (categoryId?) + period 唯一。
 *
 * <p>{@code categoryId} 与 scope 一致性由 service 校验：
 * <ul>
 *   <li>{@code TOTAL} → 必须为 null</li>
 *   <li>{@code CATEGORY} → 必须非空</li>
 * </ul>
 */
public record BudgetRequest(
        @NotNull BudgetScope scope,
        Long categoryId,
        @Min(2000) @Max(2100) int periodYear,
        @Min(1) @Max(12) int periodMonth,
        @NotNull @Positive Long amountCents,
        String currency,
        Boolean notifyEnabled) {
}