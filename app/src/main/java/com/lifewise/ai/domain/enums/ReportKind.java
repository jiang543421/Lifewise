package com.lifewise.ai.domain.enums;

/**
 * 报告种类（V8 ai_reports.report_kind CHECK 约束）。
 *
 * <p>与 {@link AiJobType} 是 1:1 关系（job_type → report_kind），但分两个枚举是为了
 * DDL CHECK 约束独立维护（V8 / V31 不联动）。
 */
public enum ReportKind {
    DAILY,
    WEEKLY,
    PLAN,
    HABIT,
    MEAL,
    EXPENSE,
    CUSTOM
}