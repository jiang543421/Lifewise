package com.lifewise.diet.support;

import com.lifewise.diet.domain.Meal;
import com.lifewise.diet.domain.MealItem;
import java.math.BigDecimal;

/** 餐食项目测试夹具。 */
public final class MealItemFixture {

    private MealItemFixture() {}

    public static MealItem item(Meal meal, Long foodId, BigDecimal amountG,
                                BigDecimal kcal, BigDecimal protein,
                                BigDecimal fat, BigDecimal carb) {
        return MealItem.of(meal, foodId, amountG, kcal, protein, fat, carb);
    }
}