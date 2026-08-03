package com.lifewise.expense.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code expense.deleted} 事件负载（plan-03-expense §1.4 + Phase B-3）。
 *
 * <p>由 {@code ExpenseService.softDelete()} 发射；语义为「软删生效」，
 * 下游消费者据此剔除月度聚合与审计留痕。
 *
 * <p>仅含 3 字段：{@code expenseId}、{@code userId}、{@code deletedAt}。
 * 不复用 {@link ExpenseCreatedPayload}，原因：删除事件无需保留 amountCents / categoryId /
 * occurredAt（这些字段已冻结在 audit 表）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExpenseDeletedPayload(
        Long expenseId,
        Long userId,
        OffsetDateTime deletedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("expenseId", expenseId);
        map.put("userId", userId);
        map.put("deletedAt", deletedAt == null ? null : deletedAt.toString());
        return map;
    }
}
