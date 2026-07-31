package com.lifewise.shared.integration.outbox;

import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li>任一 consumer 抛异常 → 整体抛给 Worker 处理（重试 / 死信）</li>
 * </ul>
 */
@Component
public class OutboxDispatcher {

    /** PG outbox_events.event_type CHECK 白名单的 Java 侧镜像。 */
    private static final Set<String> KNOWN_EVENT_TYPES =
            EnumSet.allOf(EventType.class).stream()
                    .map(EventType::eventType)
                    .collect(Collectors.toUnmodifiableSet());

    private final Map<String, List<EventConsumer>> consumerIndex;

    public OutboxDispatcher(List<EventConsumer> consumers) {
        this.consumerIndex = consumers.stream()
                .collect(Collectors.groupingBy(
                        EventConsumer::eventType,
                        Collectors.toUnmodifiableList()));
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

    private static EventEnvelope toEnvelope(OutboxEventRecord r) {
        // payload 字符串本期 toString 还原；实现模块接入 ObjectMapper 反序列化
        Map<String, Object> payload = Map.of("_raw", r.payload());
        return new EventEnvelope(
                r.eventId(),
                r.eventType(),
                r.eventVersion(),
                r.occurredAt(),
                r.userId(),
                r.aggregateType(),
                r.aggregateId(),
                r.correlationId(),
                r.causationId(),
                r.traceId(),
                payload);
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
