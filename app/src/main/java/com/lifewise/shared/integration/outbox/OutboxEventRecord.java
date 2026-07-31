package com.lifewise.shared.integration.outbox;

import java.time.OffsetDateTime;

/**
 * Outbox 行级 record（plan-shared-integration §3.3 path B 修订 + data-model-v1.2 §3.32 V30 + §3.35 V33）。
 *
 * <p>v1.0 字段（对齐 PG {@code outbox_events} 实际列）：
 * <pre>
 * id                Long（DB 生成；本 record 不持有）
 * event_type        text（白名单 25 条，见 EventType）
 * event_version     int（V30）
 * occurred_at       TIMESTAMPTZ（V2 分区键）
 * user_id           BIGINT NOT NULL（BR-22）
 * aggregate_type    text
 * aggregate_id      BIGINT
 * correlation_id    text NULL（V30；envelope UUID 转 String）
 * trace_id          text NULL（V30）
 * payload           JSONB（Hibernate {@code @JdbcTypeCode(SqlTypes.JSON)} Map 互转）
 * published_at      TIMESTAMPTZ NULL（PENDING 判定；path B 不再单独 status 列）
 * attempt_count     int（内存态；Worker 维护；DB 不持久化）
 * </pre>
 *
 * <p>v1.0 边界：
 * <ul>
 *   <li>{@code causationId} 不持久化（envelope 链路上有 UUID → DB BIGINT id 的语义错位，v1.1 评估）</li>
 *   <li>{@code attemptCount} 是 Worker 内存 Map&lt;Long,Integer&gt;，进程重启归零；行保持 PENDING</li>
 *   <li>无 outbox_dead_letter 表（path B 不引入）</li>
 * </ul>
 */
public record OutboxEventRecord(
        Long id,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        Long userId,
        String aggregateType,
        Long aggregateId,
        String correlationId,
        String traceId,
        String payload,
        OffsetDateTime publishedAt,
        int attemptCount) {
}