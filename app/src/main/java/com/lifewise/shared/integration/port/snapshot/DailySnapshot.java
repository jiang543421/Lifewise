package com.lifewise.shared.integration.port.snapshot;

import java.time.LocalDate;

/**
 * 日报只读快照（plan-shared-integration §2.2）。
 *
 * <p>对齐 PG {@code daily_report} 表（plan-data-flyway V23）。
 */
public record DailySnapshot(
        Long id,
        Long userId,
        LocalDate reportDate,
        String mood,
        String summary) {
}
