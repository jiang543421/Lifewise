package com.lifewise.expense.web;

/** 缺失或非法的 X-User-Id 头；由 expense 异常处理器映射为 401。 */
public class MissingCurrentUserException extends RuntimeException {
    public MissingCurrentUserException(String message) {
        super(message);
    }
}