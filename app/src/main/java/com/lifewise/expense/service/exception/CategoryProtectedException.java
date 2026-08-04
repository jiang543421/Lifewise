package com.lifewise.expense.service.exception;

/**
 * 默认分类不可改/删（plan-03-expense §2.2 + ErrorCode.CATEGORY_PROTECTED + BR-24）。
 */
public class CategoryProtectedException extends RuntimeException {
    public CategoryProtectedException(Long id) {
        super("default category is protected: id=" + id);
    }
}