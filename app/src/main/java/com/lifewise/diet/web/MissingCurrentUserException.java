package com.lifewise.diet.web;

/**
 * 缺少 X-User-Id 头或解析失败。
 *
 * <p>对应 HTTP 401（见 {@code DietGlobalExceptionHandler}）。
 */
public class MissingCurrentUserException extends RuntimeException {
    public MissingCurrentUserException(String message) {
        super(message);
    }
}