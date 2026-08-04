package com.lifewise.plan.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 注入当前登录用户的 Long userId；由 {@link CurrentUserArgumentResolver} 解析 X-User-Id 头。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}