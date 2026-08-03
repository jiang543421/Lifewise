package com.lifewise.diet.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 解析当前用户 ID（plan-04-diet §6.1）。
 *
 * <p>v1.0 单一用户白名单解析：详见 {@link CurrentUserArgumentResolver}。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}