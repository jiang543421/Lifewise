package com.lifewise.task.domain;

/**
 * 任务状态枚举（plan-01-task §3 + V3 DDL）。
 *
 * <p>对应 PG {@code tasks.status} CHECK 约束：
 * {@code 'OPEN','IN_PROGRESS','DONE','CANCELLED'}。
 */
public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELLED
}