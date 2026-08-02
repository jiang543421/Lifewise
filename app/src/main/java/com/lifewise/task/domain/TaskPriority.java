package com.lifewise.task.domain;

/**
 * 任务优先级枚举（plan-01-task §3 + V3 DDL）。
 *
 * <p>对应 PG {@code tasks.priority} CHECK 约束：
 * {@code 'LOW','NORMAL','HIGH','URGENT'}。
 */
public enum TaskPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}