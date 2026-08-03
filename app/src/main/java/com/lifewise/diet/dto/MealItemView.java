package com.lifewise.diet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifewise.diet.domain.Food;
import com.lifewise.diet.domain.MealItem;
import java.math.BigDecimal;

/** MealView.items 单项（携带聚合 kcal 与食物名）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealItemView(
        Long id,
        Long foodId,
        String foodName,
        BigDecimal amountG,
        BigDecimal kcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal carbG) {

    public static MealItemView from(MealItem item, Food food) {
        return new MealItemView(
                item.getId(),
                item.getFoodId(),
                food == null ? null : food.getName(),
                item.getAmountG(),
                item.getKcalSnapshot(),
                item.getProteinSnapshot(),
                item.getFatSnapshot(),
                item.getCarbSnapshot());
    }
}