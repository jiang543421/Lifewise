package com.lifewise.expense.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code expense.updated} 事件负载（plan-03-expense §1.4 + Phase B-3）。
 *
 * <p>仅由 {@code ExpenseService.update()} 发射；语义为「已有字段被修改」。
 * 字段语义与 {@link ExpenseCreatedPayload} 一致但用途不同：create 是「新增」，
 * update 是「已存在记录的字段变更」。
 *
 * <p><b>与 restore 的区分</b>：restore 走独立 {@code expense.restored} 事件
 * （见 {@link ExpenseRestoredPayload}），不复用本 payload——
 * 下游对修改与恢复的处理路径不同（restore 需重新加入累计，update 仅修正）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExpenseUpdatedPayload(
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
