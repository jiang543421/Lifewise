package com.lifewise.expense.service.exception;

/**
 * 金额非法（plan-03-expense review M2）。
 *
 * <p>应用层对 amount_cents 的业务校验（BR-09：必须 > 0 且 ≤ 9_999_999_999）。
 * 区别于 {@link IllegalArgumentException}：本异常特指金额字段非法，前端可
 * 通过 {@code EXPENSE_INVALID_AMOUNT} 错误码做差异提示（避免与「分类 ID 缺失」
 * 等通用 INVALID_INPUT 混淆）。
 *
 * <p>独立类（不继承 {@link IllegalArgumentException}）以确保
 * {@link com.lifewise.expense.controller.ExpenseGlobalExceptionHandler}
 * 的具体 handler 优先匹配，不会被通用 {@code IllegalArgumentException handler}
 * 截走。
 */
public class ExpenseInvalidAmountException extends RuntimeException {

    private final Long amountCents;

    public ExpenseInvalidAmountException(String message, Long amountCents) {
        super(message);
        this.amountCents = amountCents;
    }

    public Long getAmountCents() {
        return amountCents;
    }
}
