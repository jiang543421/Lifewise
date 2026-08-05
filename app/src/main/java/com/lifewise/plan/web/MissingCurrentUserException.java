package com.lifewise.plan.web;

/** 缺失或非法的 X-User-Id 头；映射到 401 TOKEN_INVALID。 */
public class MissingCurrentUserException extends RuntimeException {
    public MissingCurrentUserException(String message) {
        super(message);
    }
}