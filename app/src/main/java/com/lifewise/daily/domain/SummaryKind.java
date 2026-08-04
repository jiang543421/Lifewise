package com.lifewise.daily.domain;

/**
 * AI 摘要类型枚举（V5 DDL ai_summaries.summary_kind）。
 *
 * <p>对齐 PG CHECK 约束：{@code 'DAILY','WEEKLY','PLAN','CUSTOM'}。
 */
public enum SummaryKind {
    DAILY,
    WEEKLY,
    PLAN,
    CUSTOM
}
