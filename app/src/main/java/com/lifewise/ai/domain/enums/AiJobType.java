package com.lifewise.ai.domain.enums;

/**
 * AI 任务类型（V8 ai_jobs.job_type CHECK 约束）。
 *
 * <p>映射 ai-data-scopes.yml 的 {@code report_types} 字段，决定 ScopedDataFetcher 加载哪些表。
 *
 * <p>命名约定：UPPER_SNAKE_CASE（与 V8 CHECK 约束字符串对齐）。
 */
public enum AiJobType {
    DAILY_SUMMARY,
    WEEKLY_SUMMARY,
    PLAN_REVIEW,
    HABIT_ANALYSIS,
    MEAL_ANALYSIS,
    EXPENSE_ANALYSIS,
    CUSTOM_PROMPT
}