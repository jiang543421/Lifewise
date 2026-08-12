package com.lifewise.ai.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户的 Long userId（plan-06-ai §2）。
 *
 * <p>v1.0 个人版白名单（CLAUDE.md §7.3.1）：仅允许 userId=1。AI 模块 v1.0.3 之前
 * 复用 task 模块的 {@code com.lifewise.task.web.CurrentUser}，违反分层契约——本
 * 注解是 AI 模块自给自足版本，切断跨模块依赖。
 *
 * <p>TODO(jxw): v1.1+ 切换多用户时合并到 shared/infra/web/ + 整体替换为
 * {@code @AuthenticationPrincipal}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}