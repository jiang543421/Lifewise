package com.lifewise.shared.integration.port.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 饮食只读快照（plan-shared-integration §2.2）。
 *
 * <p>对齐 PG {@code meal} 表（plan-data-flyway V11）。
 */
public record MealSnapshot(
        Long id,
        Long userId,
        String mealType,
        LocalDate consumedOn,
        BigDecimal calories) {
}
