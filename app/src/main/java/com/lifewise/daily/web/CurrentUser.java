package com.lifewise.daily.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户的 Long userId（plan-02-daily §2）。
 *
 * <p>TODO(jxw): 合并到 shared/infra/web/ 后整体替换为 {@code @AuthenticationPrincipal}；
 * 当前为 daily 模块独立副本，遵循 task 模块同款过渡方案。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}