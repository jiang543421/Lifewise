package com.lifewise.task.domain;

/**
 * 习惯频率枚举（plan-01-task §3 + V3 DDL）。
 *
 * <p>对应 PG {@code habits.frequency} CHECK 约束：
 * {@code 'DAILY','WEEKLY'}。
 */
public enum HabitFrequency {
    DAILY,
    WEEKLY
}