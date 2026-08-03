package com.lifewise.expense.domain.enums;

/**
 * 支付方式（plan-03-expense §2.1 + V37 expenses.pay_method CHECK 白名单）。
 *
 * <p>wire 字符串与 DB CHECK 对齐（UPPER_SNAKE_CASE）；新增需先迁移。
 */
public enum PayMethod {
    CASH,
    ALIPAY,
    WECHAT,
    BANK
}