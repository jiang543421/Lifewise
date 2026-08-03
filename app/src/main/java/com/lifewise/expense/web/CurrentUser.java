package com.lifewise.expense.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户的 Long userId（plan-03-expense §2.1）。
 *
 * <p>由 {@link CurrentUserArgumentResolver} 从 {@code X-User-Id} 请求头解析。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}