package com.lifewise.expense.dto;

import java.time.LocalDate;

/**
 * 静音至月底请求（plan-03-expense §3.3 + H-5）。
 *
 * <p>Service 层校验 {@code until} 落在当前预算月份内；DB CHECK 兜底。
 */
public record BudgetMuteRequest(LocalDate until) {
}