package com.lifewise.ai.service.scope;

import java.util.Set;

/**
 * AI 数据访问白名单（plan-06-ai §7.3；BR-19/22）。
 *
 * <p>由 ai-data-scopes.yml 反序列化得到，{@code ScopedDataDefinitions} 在编译期
 * 加载，运行时只读——禁止运行时拼表名 / 拼列名。
 *
 * @param tableName      数据库表名（snake_case）
 * @param allowedColumns 允许读取的列集合（任何不在集合内的列 → ColumnNotAllowedException）
 * @param periodColumn   日期范围过滤列（如 {@code occurred_at} / {@code log_date}）
 * @param userIdColumn   用户隔离列（v1.0 固定为 {@code user_id}）
 */
public record AiDataScope(
        String tableName,
        Set<String> allowedColumns,
        String periodColumn,
        String userIdColumn) {

    public AiDataScope {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName required");
        }
        if (allowedColumns == null || allowedColumns.isEmpty()) {
            throw new IllegalArgumentException("allowedColumns required");
        }
        if (periodColumn == null || periodColumn.isBlank()) {
            throw new IllegalArgumentException("periodColumn required");
        }
        if (userIdColumn == null || userIdColumn.isBlank()) {
            throw new IllegalArgumentException("userIdColumn required");
        }
        // 防御性复制 + 不可变
        allowedColumns = Set.copyOf(allowedColumns);
    }
}