package com.lifewise.task.service.exception;

/**
 * 任务状态机冲突（plan-01-task §5.1）：
 * <ul>
 *   <li>{@link #ALREADY_COMPLETED} — 重复调用 complete</li>
 *   <li>{@link #ALREADY_OPEN} — 重复调用 reopen</li>
 * </ul>
 */
public class TaskStateConflictException extends RuntimeException {

    public enum Kind { ALREADY_COMPLETED, ALREADY_OPEN }

    private final Kind kind;

    public TaskStateConflictException(Kind kind, long taskId) {
        super("task " + taskId + " state conflict: " + kind);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}