package com.lifewise.shared.integration.outbox;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Outbox 行级 record（plan-shared-integration §3.3 + data-model-v1.2 §3.35 V33）。
 *
 * <p>字段对齐 PG {@code outbox_events}：event_id / event_type / event_version /
 * occurred_at / user_id / aggregate_type / aggregate_id / correlation_id /
 * causation_id / trace_id / payload(JSONB) / status / retry_count / next_attempt_at。
 *
 * <p>本期只暴露 record（与 ORM 实体解耦）；JPA 实体由实现模块提供。
 */
public record OutboxEventRecord(
        UUID eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        Long userId,
        String aggregateType,
        Long aggregateId,
        UUID correlationId,
        UUID causationId,
        String traceId,
        String payload,
        OutboxStatus status,
        int retryCount,
        OffsetDateTime nextAttemptAt) {
}
