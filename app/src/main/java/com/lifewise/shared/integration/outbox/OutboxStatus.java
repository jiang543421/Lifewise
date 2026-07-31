package com.lifewise.shared.integration.outbox;

/**
 * Outbox 事件生命周期状态（plan-shared-integration §3.3 + data-model-v1.2 §3.35 V33）。
 *
 * <p>与 PG {@code outbox_events.status} CHECK 约束一一对应：
 * <pre>
 * PENDING     — 已写入，等待 worker 拉取
 * DISPATCHED  — 已成功投递给至少一个 consumer
 * FAILED      — dispatch 抛异常，retry_count < MAX_RETRIES，等待 next_attempt_at
 * DEAD_LETTER — retry_count >= MAX_RETRIES，已搬入 outbox_dead_letter 表
 * </pre>
 */
public enum OutboxStatus {
    PENDING,
    DISPATCHED,
    FAILED,
    DEAD_LETTER
}
