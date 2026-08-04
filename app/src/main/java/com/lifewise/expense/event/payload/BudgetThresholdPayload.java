package com.lifewise.expense.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code budget.threshold} 事件负载（plan-03-expense §2.5）。
 *
 * <p>{@code thresholdPct}：整数百分比（80 = 80% / 100 = 100%）。驱动 notify 模块
 * Web Push 投递。plan-03 review M4：从 {@code Double threshold} 改为 {@code int
 * thresholdPct}，避免「金额边界避免浮点」原则被阈值边界计算破坏。
 *
 * <p>范围约束：1..100（应用层校验）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BudgetThresholdPayload(
        Long userId,
        Long budgetId,
        Integer thresholdPct,
        Long usedCents,
        Long totalCents) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", userId);
        map.put("budgetId", budgetId);
        map.put("thresholdPct", thresholdPct);
        map.put("usedCents", usedCents);
        map.put("totalCents", totalCents);
        return map;
    }
}
