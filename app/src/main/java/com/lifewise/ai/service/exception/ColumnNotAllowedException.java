package com.lifewise.ai.service.exception;

/**
 * 尝试读取不在白名单的列（plan-06-ai §7.3；BR-19/22）。
 *
 * <p>由 {@code ScopedDataFetcher} 在构建查询前阻断——敏感列（如 password_hash /
 * password / secret 等）一律不可注入 Prompt。
 */
public class ColumnNotAllowedException extends RuntimeException {
    public ColumnNotAllowedException(String tableName, String column) {
        super("Column not allowed: " + tableName + "." + column);
    }
}