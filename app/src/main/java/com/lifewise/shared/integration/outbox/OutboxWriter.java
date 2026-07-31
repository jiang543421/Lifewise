package com.lifewise.shared.integration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.shared.integration.event.EventEnvelope;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 事件写入器（plan-shared-integration §3.3 + business-architecture §5.3）。
 *
 * <p>Transactional Outbox 模式：调用方在 {@code @Transactional} 上下文中调用
 * {@link #append(EventEnvelope)}，使 outbox 行与业务表同事务提交。
 * 事务回滚 → outbox 也回滚，保证 at-least-once + 业务一致性。
 *
 * <p>约束：
 * <ul>
 *   <li>envelope.eventId 由调用方生成（UUID v4）</li>
 *   <li>status 强制 {@link OutboxStatus#PENDING}</li>
 *   <li>retryCount=0；nextAttemptAt=now(UTC)</li>
 *   <li>payload 经 Jackson {@link ObjectMapper} 序列化为合法 JSON（PG JSONB 列要求）</li>
 * </ul>
 *
 * <p>{@code Propagation.MANDATORY} 强制要求调用方已开启事务；无事务时直接抛
 * {@code IllegalTransactionStateException}，避免误用导致 outbox 与业务表不一致。
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 把 envelope 持久化到 outbox 表。
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         调用方未开启事务时（{@code Propagation.MANDATORY}）
     * @throws RuntimeException payload 序列化失败时
     */
    @Transactional(propagation = Propagation.MANDATORY)
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

    /**
     * payload 经 Jackson 序列化为 JSON 字符串。
     * payload=null 时输出 {@code "{}"} 以满足 PG JSONB NOT NULL 默认约束。
     */
    private String serializePayload(EventEnvelope env) {
        try {
            return objectMapper.writeValueAsString(env.payload());
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(
                    "Failed to serialize outbox payload for eventType=" + env.eventType(), ex);
        }
    }
}