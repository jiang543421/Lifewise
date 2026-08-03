package com.lifewise.daily.domain;

/**
 * 日报心情枚举（plan-02-daily §3 + V5 DDL daily_reports.mood）。
 *
 * <p>5 档情感标识，对应 PG CHECK 约束：
 * {@code 'GREAT','GOOD','NEUTRAL','BAD','TERRIBLE'}。
 *
 * <p>NOTE：plan-02-daily 原稿期望 {@code NUMERIC(2,1)} 半星级 1.0~5.0；当前 schema
 * 已落地为 TEXT 枚举（V5）。{@code energy_score} 字段（INT 1~5）用于数值聚合。
 */
public enum Mood {
    GREAT,
    GOOD,
    NEUTRAL,
    BAD,
    TERRIBLE;

    /** 应用层数值化，用于 {@code averageMoodInRange} 聚合。 */
    public int score() {
        return switch (this) {
            case GREAT -> 5;
            case GOOD -> 4;
            case NEUTRAL -> 3;
            case BAD -> 2;
            case TERRIBLE -> 1;
        };
    }
}
