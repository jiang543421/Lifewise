package com.lifewise.diet.service.exception;

/** 餐次字段非法（type / items / 时间窗等）。 */
public class InvalidMealException extends RuntimeException {
    public InvalidMealException(String message) {
        super(message);
    }
}