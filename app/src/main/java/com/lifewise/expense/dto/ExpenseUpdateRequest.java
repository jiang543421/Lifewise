package com.lifewise.expense.dto;

import com.lifewise.expense.domain.enums.PayMethod;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * 更新消费请求（plan-03-expense §3.1）。
 *
 * <p>所有字段可选（PATCH 语义）。修改 {@code occurredAt} 会同步刷新 {@code local_date}
 * （由 {@code Expense.applyUpdate} 内部保证）。
 */
public record ExpenseUpdateRequest(
        Long categoryId,
        @Positive
        Long amountCents,
        PayMethod payMethod,
        OffsetDateTime occurredAt,
        @Size(max = 200)
        String note) {
}