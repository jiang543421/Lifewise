package com.lifewise.expense.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code expense.restored} 事件负载（plan-03-expense §1.4 + Phase B-3 修订）。
 *
 * <p>由 {@code ExpenseService.restore()} 发射；语义为「软删记录被恢复」。
 * 字段与 {@link ExpenseUpdatedPayload} 对齐（categoryId/amountCents/currency/occurredAt），
 * 但独立事件类型以保证下游可区分「字段变更（update）」与「恢复（restore）」——
 * 二者对 ai/Stats 投影的处理路径不同（restore 需重新加入累计，update 仅修正）。
 *
 * <p>{@code restoredAt} 等于 envelope 的 occurredAt（service-clock 业务时间），
 * 不等于 payload 内其他字段的任何时间。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExpenseRestoredPayload(
        Long expenseId,
        Long userId,
        Long categoryId,
        Long amountCents,
        String currency,
        OffsetDateTime occurredAt,
        OffsetDateTime restoredAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("expenseId", expenseId);
        map.put("userId", userId);
        map.put("categoryId", categoryId);
        map.put("amountCents", amountCents);
        map.put("currency", currency);
        map.put("occurredAt", occurredAt == null ? null : occurredAt.toString());
        map.put("restoredAt", restoredAt == null ? null : restoredAt.toString());
        return map;
    }
}
