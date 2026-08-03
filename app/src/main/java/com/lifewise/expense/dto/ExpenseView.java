package com.lifewise.expense.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.lifewise.expense.domain.Expense;
import com.lifewise.expense.domain.enums.PayMethod;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 消费视图（plan-03-expense §3.1）。
 *
 * <p>snake_case JSON；包含分区键 {@code local_date} 便于前端展示。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExpenseView(
        Long id,
        Long userId,
        Long categoryId,
        Long amountCents,
        String currency,
        PayMethod payMethod,
        LocalDate localDate,
        OffsetDateTime occurredAt,
        String note) {

    public static ExpenseView from(Expense e) {
        return new ExpenseView(
                e.getId(),
                e.getUserId(),
                e.getCategoryId(),
                e.getAmountCents(),
                e.getCurrency(),
                e.getPayMethod(),
                e.getLocalDate(),
                e.getOccurredAt(),
                e.getNote());
    }
}