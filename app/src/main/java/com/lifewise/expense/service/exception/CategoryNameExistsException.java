package com.lifewise.expense.service.exception;

/**
 * 分类名称重复（plan-03-expense §2.2 + ErrorCode.CATEGORY_NAME_EXISTS + BR-23）。
 */
public class CategoryNameExistsException extends RuntimeException {
    public CategoryNameExistsException(String name) {
        super("expense category name already exists: " + name);
    }
}