package com.lifewise.expense.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/**
 * 分类不存在或不属于当前用户（plan-03-expense §2.2 + ErrorCode.CATEGORY_NOT_FOUND）。
 */
public class CategoryNotFoundException extends ResourceNotFoundException {
    public CategoryNotFoundException(Long id) {
        super("expense_category", id);
    }
}