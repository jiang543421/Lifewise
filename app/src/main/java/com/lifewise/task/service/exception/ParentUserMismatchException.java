package com.lifewise.task.service.exception;

/**
 * 子任务 parent 不属于同一用户（BR-27 + plan-01-task §5.1）。
 */
public class ParentUserMismatchException extends RuntimeException {
    public ParentUserMismatchException(long parentId) {
        super("parent task " + parentId + " is not owned by current user");
    }
}