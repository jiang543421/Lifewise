package com.lifewise.shared.integration.outbox;

import java.time.OffsetDateTime;

/**
 * Outbox 事件运行期状态（plan-shared-integration §3.3，path B 修订）。
 *
 * <p>v1.0 修订：DB 实际 schema 用 {@code published_at TIMESTAMPTZ NULL} 表达"未投递/已投递"，
 * 不引入 {@code status} / {@code retry_count} / {@code next_attempt_at} 列。
 * 本枚举仅作 Worker 内存语义，不持久化。
 *
 * <p>三态：
 * <ul>
 *   <li>{@link #PENDING} — {@code published_at IS NULL}；Worker 待拉取</li>
 *   <li>{@link #DISPATCHED} — {@code published_at IS NOT NULL}；至少一个 consumer 已成功消费</li>
 *   <li>{@link #DISCARDED} — Worker 内存标记，重试达上限；行仍 PENDING 由 admin 手动干预</li>
 * </ul>
 *
 * <p>v1.1 计划：在 plan-shared-integration-amendment.md 中追加 outbox_dead_letter 与
 * retry_count 列迁移后，再回填本枚举的持久化语义。
 */
public enum OutboxStatus {
    PENDING,
    DISPATCHED,
    DISCARDED;

    /** 由 DB 行（仅含 published_at）推断状态。 */
    public static OutboxStatus from(OffsetDateTime publishedAt) {
        return publishedAt == null ? PENDING : DISPATCHED;
    }
}