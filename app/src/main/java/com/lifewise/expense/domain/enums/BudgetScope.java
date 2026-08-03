package com.lifewise.expense.domain.enums;

/**
 * 预算范围（plan-03-expense §2.3 + V37 budgets.scope CHECK 白名单）。
 *
 * <ul>
 *   <li>{@link #TOTAL} — 月度总预算（category_id 必须为 NULL）</li>
 *   <li>{@link #CATEGORY} — 分类预算（category_id 必须非空）</li>
 * </ul>
 */
public enum BudgetScope {
    TOTAL,
    CATEGORY
}