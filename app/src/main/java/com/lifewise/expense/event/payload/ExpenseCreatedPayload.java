package com.lifewise.expense.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code expense.created} 事件负载（plan-03-expense §2.5）。
 *
 * <p>驱动 BudgetEvaluator 评估 + ai 模块月度聚合。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExpenseCreatedPayload(
        Long expenseId,
        Long userId,
        Long categoryId,
        Long amountCents,
        String currency,
        OffsetDateTime occurredAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("expenseId", expenseId);
        map.put("userId", userId);
        map.put("categoryId", categoryId);
        map.put("amountCents", amountCents);
        map.put("currency", currency);
        map.put("occurredAt", occurredAt == null ? null : occurredAt.toString());
        return map;
    }
}