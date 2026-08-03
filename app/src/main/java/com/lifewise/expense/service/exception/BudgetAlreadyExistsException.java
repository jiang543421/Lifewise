package com.lifewise.expense.service.exception;

import com.lifewise.expense.domain.enums.BudgetScope;

/**
 * 同 user+scope+category+period 已有预算（plan-03-expense §2.3 + BR-10 + ErrorCode.BUDGET_ALREADY_EXISTS）。
 */
public class BudgetAlreadyExistsException extends RuntimeException {
    public BudgetAlreadyExistsException(Long userId, BudgetScope scope, Long categoryId,
                                         int year, int month) {
        super("budget already exists: user=" + userId + " scope=" + scope
                + " category=" + categoryId + " period=" + year + "-" + month);
    }
}