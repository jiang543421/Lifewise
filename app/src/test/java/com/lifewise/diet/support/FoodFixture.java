package com.lifewise.diet.support;

import com.lifewise.diet.domain.Food;

/** 食物测试夹具。 */
public final class FoodFixture {

    private FoodFixture() {}

    public static Food systemFood(Long id, String name) {
        // system(name, category, kcal, protein, carb, fat) — doubles
        Food f = Food.system(name, "test", 100d, 10d, 20d, 5d);
        f.setIdInternal(id);
        return f;
    }

    public static Food userFood(Long id, Long userId, String name) {
        // user(userId, name, category, kcal, protein, carb, fat) — doubles
        Food f = Food.user(userId, name, "test", 100d, 10d, 20d, 5d);
        f.setIdInternal(id);
        return f;
    }
}