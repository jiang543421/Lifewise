package com.lifewise.shared.integration.outbox;

import com.lifewise.shared.integration.event.EventEnvelope;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Outbox 事件写入器（plan-shared-integration §3.3 + business-architecture §5.3）。
 *
 * <p>Transactional Outbox 模式：调用方在 {@code @Transactional} 上下文中调用
 * {@link #append(EventEnvelope)}，使 outbox 行与业务表同事务提交。
 * 事务回滚 → outbox 也回滚，保证 at-least-once + 业务一致性。
 *
 * <p>约束：envelope.eventId 由调用方生成（UUID v4）；status 强制 PENDING；retryCount=0；
 * nextAttemptAt=now(UTC)；payload 由 mapper 序列化为 JSON 字符串。
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;

    public OutboxWriter(OutboxEventRepository repository) {
        this.repository = repository;
    }

    public void append(EventEnvelope env) {
        OutboxEventRecord record = new OutboxEventRecord(
                env.eventId(),
                env.eventType(),
                env.eventVersion(),
                env.occurredAt(),
                env.userId(),
                env.aggregateType(),
                env.aggregateId(),
                env.correlationId(),
                env.causationId(),
                env.traceId(),
                serializePayload(env),
                OutboxStatus.PENDING,
                0,
                OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(record);
    }

    /** payload 序列化为 JSON 字符串；本期用 toString 占位，由 JPA 适配器接 ObjectMapper。 */
    private static String serializePayload(EventEnvelope env) {
        return env.payload() == null ? "{}" : env.payload().toString();
    }
}
