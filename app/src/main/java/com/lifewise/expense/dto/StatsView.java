package com.lifewise.expense.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * 消费统计视图（plan-03-expense §3.4 + business-architecture §6.1）。
 *
 * <p>snake_case JSON。{@code from / to} 区间内：{@code totalCents} 是总额；
 * {@code byCategory} / {@code byDay} 是分组明细。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StatsView(
        long totalCents,
        String currency,
        List<CategoryBucket> byCategory,
        List<DayBucket> byDay) {

    public record CategoryBucket(
            Long categoryId,
            String categoryName,
            long amountCents,
            long expenseCount) {
    }

    public record DayBucket(
            String day,           // ISO date "YYYY-MM-DD"
            long amountCents,
            long expenseCount) {
    }
}