package com.lifewise.diet.service.exception;

/** 用户身体参数非法（身高/体重/年龄/性别/活动量）。 */
public class InvalidProfileInputException extends RuntimeException {
    public InvalidProfileInputException(String message) {
        super(message);
    }
}