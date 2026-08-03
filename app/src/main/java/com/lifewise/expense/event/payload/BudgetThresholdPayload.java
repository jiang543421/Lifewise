package com.lifewise.expense.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code budget.threshold} 事件负载（plan-03-expense §2.5）。
 *
 * <p>{@code threshold}：0.8 / 1.0（80% / 100%）。驱动 notify 模块 Web Push 投递。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BudgetThresholdPayload(
        Long userId,
        Long budgetId,
        Double threshold,
        Long usedCents,
        Long totalCents) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", userId);
        map.put("budgetId", budgetId);
        map.put("threshold", threshold);
        map.put("usedCents", usedCents);
        map.put("totalCents", totalCents);
        return map;
    }
}