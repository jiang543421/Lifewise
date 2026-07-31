package com.lifewise.shared.integration.port.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 消费只读快照（plan-shared-integration §2.2）。
 *
 * <p>对齐 PG {@code expense} 表（plan-data-flyway V25）。
 */
public record ExpenseSnapshot(
        Long id,
        Long userId,
        BigDecimal amount,
        String currency,
        String category,
        LocalDate occurredOn) {
}
