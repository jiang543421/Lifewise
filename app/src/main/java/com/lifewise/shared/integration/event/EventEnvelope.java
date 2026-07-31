package com.lifewise.shared.integration.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox 事件信封（business-architecture §5.3 + plan-shared-integration §1）。
 *
 * <p>10 字段含义：
 * <pre>
 * eventId          本事件 UUID（消费者幂等去重）
 * eventType        事件名（{@link EventType}，wire 时为小写点分字符串）
 * eventVersion     schema 版本（破坏性变更 +1；旧消费者可同时消费 v1 / v2）
 * occurredAt       业务发生时间（UTC ISO 8601，对齐 DB occurred_at TIMESTAMPTZ）
 * userId           事件发起用户（所有权 + Worker 分片键；plan-data-flyway §0）
 * aggregateType    聚合根类型（task / milestone / expense 等）
 * aggregateId      聚合根 ID（与 aggregateType 一起定位业务事实）
 * correlationId    链路追踪：同根跨服务请求同一 ID（V30 加列，plan-data-flyway §3.32）
 * causationId      父事件 UUID（事件 A 触发的 B；可为 null）
 * traceId          服务端日志 trace ID（Micrometer；非业务字段，便于排障）
 * payload          事件负载（JSONB；Map&lt;String,Object&gt; 由 PG Hibernate 直接存储）
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventEnvelope(
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
        Map<String, Object> payload) {
}
