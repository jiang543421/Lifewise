package com.lifewise.task.domain;

/**
 * 习惯打卡来源枚举（V3 DDL）。
 *
 * <p>对应 PG {@code habit_logs.source} CHECK 约束：
 * {@code 'NORMAL','BACKFILL'}。
 */
public enum HabitLogSource {
    NORMAL,
    BACKFILL
}