package com.lifewise.expense.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/**
 * 消费不存在或不属于当前用户（plan-03-expense §2.2 + ErrorCode.EXPENSE_NOT_FOUND）。
 */
public class ExpenseNotFoundException extends ResourceNotFoundException {
    public ExpenseNotFoundException(Long id) {
        super("expense", id);
    }
}