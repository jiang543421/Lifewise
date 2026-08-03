package com.lifewise.expense.dto;

import com.lifewise.expense.domain.enums.PayMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * 创建消费请求（plan-03-expense §3.1）。
 *
 * <p>BR-09：amount_cents > 0 且 &lt;= 9_999_999_999。
 */
public record ExpenseCreateRequest(
        @NotNull
        Long categoryId,
        @NotNull
        @Positive
        Long amountCents,
        @NotNull
        PayMethod payMethod,
        @NotNull
        OffsetDateTime occurredAt,
        @Size(max = 200)
        String note,
        String currency) {
}