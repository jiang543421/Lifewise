package com.lifewise.shared.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;

/**
 * 错误信封（CLAUDE.md §4.5 + plan-shared-integration §2.3）。
 *
 * <p>JSON 形状（snake_case，对齐前端约定）：
 * <pre>
 * { "code": "TASK_NOT_FOUND",
 *   "message": "task 99 not found",
 *   "trace_id": "trace-abc",
 *   "details": { ... }   // 可选，null 时不输出
 * }
 * </pre>
 *
 * <p>{@code code} 必须是 {@link ErrorCode} 枚举值或其历史别名；新增必须前向兼容。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ErrorEnvelope(
        String code,
        String message,
        String traceId,
        Map<String, Object> details) {
}
