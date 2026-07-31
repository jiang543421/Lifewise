package com.lifewise.shared.integration.port.snapshot;

import java.time.LocalDate;

/**
 * 计划只读快照（plan-shared-integration §2.2）。
 *
 * <p>对齐 PG {@code plan} 表（plan-data-flyway V17）。
 */
public record PlanSnapshot(
        Long id,
        Long userId,
        String title,
        String status,
        LocalDate startDate,
        LocalDate endDate) {
}
