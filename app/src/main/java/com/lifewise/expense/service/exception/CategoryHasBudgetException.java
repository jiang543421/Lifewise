package com.lifewise.expense.service.exception;

/**
 * 分类下仍有活跃预算，先删预算（plan-03-expense §2.2 + ErrorCode.CATEGORY_HAS_BUDGET）。
 */
public class CategoryHasBudgetException extends RuntimeException {
    public CategoryHasBudgetException(Long categoryId) {
        super("category has active budgets: id=" + categoryId);
    }
}