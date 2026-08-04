package com.lifewise.diet.service.exception;

/** 餐次不存在 / 跨用户访问（统一 404，避免存在性泄露）。 */
public class MealNotFoundException extends RuntimeException {
    public MealNotFoundException(Long mealId) {
        super("meal not found: id=" + mealId);
    }
}