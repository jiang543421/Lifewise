package com.lifewise.diet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifewise.diet.domain.Food;
import com.lifewise.diet.domain.Meal;
import com.lifewise.diet.domain.MealItem;
import com.lifewise.diet.domain.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/meals/{id} 完整视图（含 items + 聚合营养）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealView(
        Long id,
        MealType type,
        LocalDate localDate,
        String timezone,
        String note,
        BigDecimal totalKcal,
        List<MealItemView> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static MealView from(Meal meal, java.util.Map<Long, Food> foodIndex) {
        List<MealItemView> items = meal.getItems().stream()
                .map(it -> MealItemView.from(it, foodIndex.get(it.getFoodId())))
                .toList();
        BigDecimal totalKcal = items.stream()
                .map(MealItemView::kcal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MealView(meal.getId(), meal.getMealType(), meal.getLocalDate(),
                meal.getTimezone(), meal.getNote(), totalKcal, items,
                meal.getCreatedAt(), meal.getUpdatedAt());
    }

    /** 测试 / 空视图便捷构造。 */
    public static MealView empty(Long id, MealType type, LocalDate date) {
        return new MealView(id, type, date, "UTC", null, BigDecimal.ZERO,
                List.of(), null, null);
    }
}