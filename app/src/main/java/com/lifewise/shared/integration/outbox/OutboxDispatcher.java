package com.lifewise.shared.integration.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Outbox 事件路由器（plan-shared-integration §3.3）。
 *
 * <p>职责：
 * <ul>
 *   <li>校验 eventType 在 {@link EventType} 白名单内（防御性）</li>
 *   <li>查找所有订阅该 eventType 的 {@link EventConsumer}</li>
 *   <li>fan-out 调用所有匹配 consumer</li>
 *   <li>任一 consumer 抛异常 → 整体抛给 Worker 处理（重试）</li>
 * </ul>
 *
 * <p>v1.0 path B：
 * <ul>
 *   <li>{@link OutboxEventRecord#id()} (Long) → envelope.eventId (UUID) 在派发时新生成。
 *       envelope 上的 UUID 仅作 in-flight 标识，业务唯一追溯走 {@code aggregateType/aggregateId} +
 *       {@code correlationId}。</li>
 *   <li>payload (JSON 字符串) 经 Jackson 反序列化为 {@code Map<String,Object>} 后注入 envelope</li>
 * </ul>
 */
@Component
public class OutboxDispatcher {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String EMPTY_JSON = "{}";

    /** PG outbox_events.event_type CHECK 白名单的 Java 侧镜像。 */
    private static final Set<String> KNOWN_EVENT_TYPES =
            EnumSet.allOf(EventType.class).stream()
                    .map(EventType::eventType)
                    .collect(Collectors.toUnmodifiableSet());

    private final Map<String, List<EventConsumer>> consumerIndex;
    private final ObjectMapper objectMapper;

    public OutboxDispatcher(List<EventConsumer> consumers, ObjectMapper objectMapper) {
        this.consumerIndex = consumers.stream()
                .collect(Collectors.groupingBy(
                        EventConsumer::eventType,
                        Collectors.toUnmodifiableList()));
        this.objectMapper = objectMapper;
    }

    /**
     * @throws UnknownEventTypeException eventType 不在白名单
     * @throws NoConsumerRegisteredException eventType 合法但无 consumer 订阅
     * @throws RuntimeException 任意 consumer 失败时透传（Worker 触发 retry）
     */
    public void dispatch(OutboxEventRecord record) {
        String type = record.eventType();
        if (!KNOWN_EVENT_TYPES.contains(type)) {
            throw new UnknownEventTypeException(type);
        }
        List<EventConsumer> targets = consumerIndex.get(type);
        if (targets == null || targets.isEmpty()) {
            throw new NoConsumerRegisteredException(type);
        }
        EventEnvelope envelope = toEnvelope(record);
        List<RuntimeException> failures = new ArrayList<>();
        for (EventConsumer c : targets) {
            try {
                c.consume(envelope);
            } catch (RuntimeException ex) {
                failures.add(ex);
            }
        }
        if (!failures.isEmpty()) {
            RuntimeException first = failures.get(0);
            for (int i = 1; i < failures.size(); i++) {
                first.addSuppressed(failures.get(i));
            }
            throw first;
        }
    }

    /**
     * OutboxEventRecord → EventEnvelope；payload (JSON 字符串) 经 Jackson 反序列化为 Map。
     * envelope.eventId 在派发时新生成（DB BIGINT id 与 envelope UUID 是不同语义层）。
     * envelope.correlationId/causationId 在 record → envelope 边界还原为 UUID（若可解析）。
     */
    private EventEnvelope toEnvelope(OutboxEventRecord r) {
        Map<String, Object> payload = deserializePayload(r);
        UUID correlationId = parseUuidOrNull(r.correlationId());
        return new EventEnvelope(
                UUID.randomUUID(),
                r.eventType(),
                r.eventVersion(),
                r.occurredAt(),
                r.userId(),
                r.aggregateType(),
                r.aggregateId(),
                correlationId,
                null,                                  // causationId 不入库
                r.traceId(),
                payload);
    }

    private Map<String, Object> deserializePayload(OutboxEventRecord r) {
        String raw = r.payload();
        if (raw == null || raw.isBlank() || EMPTY_JSON.equals(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Failed to deserialize outbox payload id=" + r.id()
                            + " eventType=" + r.eventType(), ex);
        }
    }

    private static UUID parseUuidOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 测试用：暴露 consumer 索引。 */
    Map<String, List<EventConsumer>> consumerIndex() {
        return consumerIndex;
    }

    /** 仅供 OutboxWorker 单元测试断言类型。 */
    static Set<String> knownEventTypes() {
        return KNOWN_EVENT_TYPES;
    }
}