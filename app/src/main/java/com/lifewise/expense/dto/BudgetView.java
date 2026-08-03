package com.lifewise.expense.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.lifewise.expense.domain.Budget;
import com.lifewise.expense.domain.enums.BudgetScope;
import java.time.LocalDate;

/**
 * 预算视图（plan-03-expense §3.3）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BudgetView(
        Long id,
        Long userId,
        BudgetScope scope,
        Long categoryId,
        int periodYear,
        int periodMonth,
        Long amountCents,
        String currency,
        boolean notifyEnabled,
        LocalDate notifyMutedUntil) {

    public static BudgetView from(Budget b) {
        return new BudgetView(
                b.getId(),
                b.getUserId(),
                b.getScope(),
                b.getCategoryId(),
                b.getPeriodYear(),
                b.getPeriodMonth(),
                b.getAmountCents(),
                b.getCurrency(),
                b.isNotifyEnabledField(),
                b.getNotifyMutedUntil());
    }
}