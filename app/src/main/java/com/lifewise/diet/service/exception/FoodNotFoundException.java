package com.lifewise.diet.service.exception;

/** 食物不存在 / 跨用户访问（统一 404）。 */
public class FoodNotFoundException extends RuntimeException {
    public FoodNotFoundException(Long foodId) {
        super("food not found: id=" + foodId);
    }
}