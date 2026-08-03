package com.lifewise.shared.integration.port.snapshot;

import java.time.LocalDate;

/**
 * 消费只读快照（plan-shared-integration §2.2 + plan-03-expense §2.3）。
 *
 * <p>对齐 PG {@code expenses} 表（V6/V37）：cents 长整型 + 分类 ID。
 * 历史上用 BigDecimal amount，但 v1.1.1 改 cents 后此处同步更新（无引用方，安全）。
 *
 * @param id          消费记录 ID
 * @param userId      用户 ID（防止跨用户访问）
 * @param amountCents 金额（分），与 schema BIGINT amount_cents 对齐（BR-09）
 * @param currency    ISO 4217 三字代码（CNY/USD）
 * @param categoryId  分类 ID（NOT NULL；plan-03 §2.1：消费必属某分类）
 * @param occurredOn  消费发生日期（{@code local_date}，分区键）
 */
public record ExpenseSnapshot(
        Long id,
        Long userId,
        Long amountCents,
        String currency,
        Long categoryId,
        LocalDate occurredOn) {
}
