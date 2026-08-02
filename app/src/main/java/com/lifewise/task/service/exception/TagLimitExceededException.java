package com.lifewise.task.service.exception;

/**
 * 单任务标签超过 5 个（BR-03 + plan-01-task §5.1）。
 */
public class TagLimitExceededException extends RuntimeException {
    private final long taskId;
    private final int actual;

    public TagLimitExceededException(long taskId, int actual) {
        super("task " + taskId + " has " + actual + " tags (limit 5)");
        this.taskId = taskId;
        this.actual = actual;
    }

    public long getTaskId() { return taskId; }
    public int getActual() { return actual; }
}