package com.lifewise.task.service.exception;

/**
 * 同 user 下标签 name 重复（plan-01-task §5.3 + V3 UNIQUE(user_id, name)）。
 */
public class DuplicateTagNameException extends RuntimeException {
    public DuplicateTagNameException(String name) {
        super("task tag name already exists: " + name);
    }
}