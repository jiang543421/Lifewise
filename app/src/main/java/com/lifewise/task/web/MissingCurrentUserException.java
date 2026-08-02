package com.lifewise.task.web;

/** 缺失或非法的 X-User-Id 头；由 task 异常处理器映射为 401/400。 */
public class MissingCurrentUserException extends RuntimeException {
    public MissingCurrentUserException(String message) {
        super(message);
    }
}
