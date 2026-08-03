package com.lifewise.daily.domain;

/**
 * 日报亮点类型枚举（plan-02-daily §3 + V5 DDL daily_report_highlights.highlight_type）。
 *
 * <p>对齐 PG CHECK 约束：{@code 'TASK','HABIT','MEAL','EXPENSE','MILESTONE','INSIGHT'}。
 */
public enum HighlightType {
    TASK,
    HABIT,
    MEAL,
    EXPENSE,
    MILESTONE,
    INSIGHT
}
