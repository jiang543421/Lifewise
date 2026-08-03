package com.lifewise.diet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * GET /api/meals/stats 与 /api/meals/stats/weekly 响应（plan-04-diet §2.3）。
 *
 * <p>{@code byDay} 用日聚合 Map；{@code weekly} 用物化视图 {@code mv_meal_nutrition_weekly}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsView(
        Map<LocalDate, BigDecimal> byDay,
        Map<LocalDate, BigDecimal> byWeek,
        Integer targetKcal) {

    /** 单条周聚合（来自 mv_meal_nutrition_weekly）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WeeklyBucket(
            LocalDate weekStart,
            String mealType,
            Long mealCount,
            BigDecimal totalKcal,
            BigDecimal totalProteinG,
            BigDecimal totalCarbG,
            BigDecimal totalFatG) {
    }
}