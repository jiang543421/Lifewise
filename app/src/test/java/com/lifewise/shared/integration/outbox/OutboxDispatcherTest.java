package com.lifewise.shared.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.event.payload.TaskCompletedPayload;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OutboxDispatcher 单测（plan-shared-integration §5.1）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code outbox_dispatcher_should_route_by_event_type} — 按 event_type 找 consumer</li>
 *   <li>{@code outbox_dispatcher_should_invoke_consumer} — 调用 consumer.consume(envelope)</li>
 *   <li>{@code outbox_should_deserialize_payload_from_jsonb} — payload 反序列化为 Map</li>
 * </ul>
 */
@DisplayName("OutboxDispatcher 事件路由")
class OutboxDispatcherTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("按 event_type 找到匹配 consumer 并调用 consume()")
    void should_route_to_matching_consumer() {
        RecordingConsumer dailyConsumer = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        RecordingConsumer planConsumer = new RecordingConsumer(EventType.PLAN_CREATED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(dailyConsumer, planConsumer), objectMapper);

        EventEnvelope env = envelope(EventType.TASK_COMPLETED);
        OutboxEventRecord record = recordFrom(env);

        dispatcher.dispatch(record);

        assertThat(dailyConsumer.received).hasSize(1);
        assertThat(dailyConsumer.received.get(0).eventType()).isEqualTo("task.completed");
        assertThat(planConsumer.received).isEmpty();
    }

    @Test
    @DisplayName("无匹配 consumer 时抛 NoConsumerRegisteredException（不静默丢消息）")
    void should_throw_when_no_consumer_registered() {
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(), objectMapper);
        OutboxEventRecord record = recordFrom(envelope(EventType.AI_SUMMARY_GENERATED));

        assertThatThrownBy(() -> dispatcher.dispatch(record))
                .isInstanceOf(NoConsumerRegisteredException.class)
                .hasMessageContaining("ai.summary.generated");
    }

    @Test
    @DisplayName("未知 event_type（不在 EventType 枚举内）抛 UnknownEventTypeException")
    void should_reject_unknown_event_type() {
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(), objectMapper);
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(), "evil.unknown.event", 1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L, UUID.randomUUID(), null, "trace",
                Map.of());
        OutboxEventRecord record = recordFrom(env);

        assertThatThrownBy(() -> dispatcher.dispatch(record))
                .isInstanceOf(UnknownEventTypeException.class);
    }

    @Test
    @DisplayName("同 event_type 注册多个 consumer 时，全部调用（fan-out 语义）")
    void should_invoke_all_consumers_for_same_event_type() {
        RecordingConsumer c1 = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        RecordingConsumer c2 = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(c1, c2), objectMapper);

        dispatcher.dispatch(recordFrom(envelope(EventType.TASK_COMPLETED)));

        assertThat(c1.received).hasSize(1);
        assertThat(c2.received).hasSize(1);
    }

    @Test
    @DisplayName("record.payload (JSON 字符串) 反序列化为 Map<String,Object>（consumer 拿到真实字段）")
    void should_deserialize_payload_from_jsonb() throws JsonProcessingException {
        RecordingConsumer consumer = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(consumer), objectMapper);

        // 手工构造一个带 JSON 字符串 payload 的 record（模拟 DB 读出）
        Map<String, Object> originalPayload = Map.of("taskId", 99L, "completedAt", "2026-07-31T10:30:00Z");
        String json = objectMapper.writeValueAsString(originalPayload);

        OutboxEventRecord record = new OutboxEventRecord(
                UUID.randomUUID(),
                EventType.TASK_COMPLETED.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 99L,
                UUID.randomUUID(), null, "trace",
                json, OutboxStatus.PENDING, 0,
                OffsetDateTime.now(ZoneOffset.UTC));

        dispatcher.dispatch(record);

        Map<String, Object> received = consumer.received.get(0).payload();
        assertThat(received)
                .containsKey("taskId")
                .containsKey("completedAt")
                .doesNotContainKey("_raw")
                .as("payload 必须是真实字段 Map，不是 Map.of('_raw', rawString)");
        // Jackson 把 JSON 数字 99 解析为 Integer（除非显式声明 Long）
        assertThat(((Number) received.get("taskId")).longValue()).isEqualTo(99L);
        assertThat(received.get("completedAt")).isEqualTo("2026-07-31T10:30:00Z");
    }

    @Test
    @DisplayName("payload 反序列化失败 → 包装为 RuntimeException（dispatch 抛错由 Worker 重试）")
    void should_propagate_deserialization_failure_as_runtime() {
        RecordingConsumer consumer = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(consumer), objectMapper);

        OutboxEventRecord record = new OutboxEventRecord(
                UUID.randomUUID(),
                EventType.TASK_COMPLETED.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L,
                UUID.randomUUID(), null, "trace",
                "{not valid json",
                OutboxStatus.PENDING, 0,
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> dispatcher.dispatch(record))
                .isInstanceOf(RuntimeException.class);
        assertThat(consumer.received).isEmpty();
    }

    // ---- helpers ----

    private static EventEnvelope envelope(EventType type) {
        return new EventEnvelope(
                UUID.randomUUID(),
                type.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1042L,
                type.eventType().split("\\.")[0],
                99L,
                UUID.randomUUID(),
                null,
                "trace-test",
                new TaskCompletedPayload(99L, OffsetDateTime.now(ZoneOffset.UTC)).toMap());
    }

    private static OutboxEventRecord recordFrom(EventEnvelope env) {
        return new OutboxEventRecord(
                env.eventId(), env.eventType(), env.eventVersion(),
                env.occurredAt(), env.userId(),
                env.aggregateType(), env.aggregateId(),
                env.correlationId(), env.causationId(),
                env.traceId(),
                "{}",
                OutboxStatus.PENDING, 0, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** 录音 consumer：记录所有收到的 envelope。 */
    private static final class RecordingConsumer implements EventConsumer {
        final List<EventEnvelope> received = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final String subscribedType;

        RecordingConsumer(String subscribedType) {
            this.subscribedType = subscribedType;
        }

        @Override
        public String eventType() {
            return subscribedType;
        }

        @Override
        public void consume(EventEnvelope env) {
            received.add(env);
        }
    }
}