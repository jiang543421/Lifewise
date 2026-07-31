package com.lifewise.shared.integration.port.snapshot;

import java.time.OffsetDateTime;

/**
 * AI 报告只读快照（plan-shared-integration §2.2）。
 *
 * <p>对齐 PG {@code ai_report} 表（plan-data-flyway V20）。
 */
public record AiSnapshot(
        Long id,
        Long userId,
        String reportType,
        OffsetDateTime generatedAt,
        String status) {
}
