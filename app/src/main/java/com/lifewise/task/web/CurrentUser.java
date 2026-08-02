package com.lifewise.task.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户的 Long userId（plan-01-task §2.1 临时方案）。
 *
 * <p>在 JWT 切面落地前，由 {@link CurrentUserArgumentResolver} 从
 * {@code X-User-Id} 请求头解析；TODO 阶段：替换为 {@code @AuthenticationPrincipal}
 * 或共享模块的 {@code AuthPrincipal}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}
