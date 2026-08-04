package com.lifewise.ai.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** AI 模块 CurrentUser 注解（与 expense 同名但本地包隔离 — ArchUnit 不跨模块扫描）。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}