package com.lifewise.expense.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/**
 * 预算不存在或不属于当前用户（plan-03-expense §2.3 + ErrorCode.BUDGET_NOT_FOUND）。
 */
public class BudgetNotFoundException extends ResourceNotFoundException {
    public BudgetNotFoundException(Long id) {
        super("budget", id);
    }
}