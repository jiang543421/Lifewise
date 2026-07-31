package com.lifewise.shared.integration.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 分页元数据（CLAUDE.md §4.5 + plan-shared-integration §2.3）。
 *
 * <p>JSON 形状（snake_case，与前端约定对齐）：
 * <pre>
 * { "total": 101, "page": 1, "limit": 20, "has_next": true }
 * </pre>
 *
 * <p>{@code has_next=false} 时字段仍输出（与 {@code ApiResponse.meta=null} 在 wire 层
 * 显式区分 —— 前端可稳定按 schema 反序列化）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PageMeta(
        long total,
        int page,
        int limit,
        boolean hasNext) {
}
