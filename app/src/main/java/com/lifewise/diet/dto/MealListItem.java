package com.lifewise.diet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifewise.diet.domain.Meal;
import com.lifewise.diet.domain.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** GET /api/meals 列表项（轻量视图，无 items）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealListItem(
        Long id,
        MealType type,
        LocalDate localDate,
        BigDecimal totalKcal,
        String note) {

    public static MealListItem from(Meal meal) {
        BigDecimal total = meal.totalKcalAsDecimal();
        return new MealListItem(meal.getId(), meal.getMealType(), meal.getLocalDate(),
                total, meal.getNote());
    }
}