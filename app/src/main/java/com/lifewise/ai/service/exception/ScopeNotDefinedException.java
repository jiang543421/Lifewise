package com.lifewise.ai.service.exception;

/**
 * 报告类型 / 表名未在 ai-data-scopes.yml 白名单内（plan-06-ai §7.3；BR-19/22）。
 *
 * <p>由 {@code ScopedDataDefinitions} 在 fetch 之前阻断——任何不在白名单的
 * reportType 或 tableName 一律不允许读。
 */
public class ScopeNotDefinedException extends RuntimeException {
    public ScopeNotDefinedException(String resource) {
        super("Scope not defined: " + resource);
    }
}