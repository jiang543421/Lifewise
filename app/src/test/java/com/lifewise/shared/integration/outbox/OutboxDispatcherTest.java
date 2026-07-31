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
 * OutboxDispatcher 单测（plan-shared-integration §5.1 path B）。
 *
 * <p>v1.0 path B 覆盖：
 * <ul>
 *   <li>按 event_type 找 consumer 并 fan-out</li>
 *   <li>无 consumer 抛 NoConsumerRegisteredException</li>
 *   <li>未知 event_type 抛 UnknownEventTypeException</li>
 *   <li>payload JSON 字符串 → Map 反序列化</li>
 *   <li>反序列化失败包装为 RuntimeException</li>
 *   <li>envelope.eventId 在派发时新生成（DB id Long 与 envelope UUID 隔离）</li>
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

        OutboxEventRecord record = record(EventType.TASK_COMPLETED);

        dispatcher.dispatch(record);

        assertThat(dailyConsumer.received).hasSize(1);
        assertThat(dailyConsumer.received.get(0).eventType()).isEqualTo("task.completed");
        assertThat(planConsumer.received).isEmpty();
    }

    @Test
    @DisplayName("无匹配 consumer 时抛 NoConsumerRegisteredException（不静默丢消息）")
    void should_throw_when_no_consumer_registered() {
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(), objectMapper);

        assertThatThrownBy(() -> dispatcher.dispatch(record(EventType.AI_SUMMARY_GENERATED)))
                .isInstanceOf(NoConsumerRegisteredException.class)
                .hasMessageContaining("ai.summary.generated");
    }

    @Test
    @DisplayName("未知 event_type（不在 EventType 枚举内）抛 UnknownEventTypeException")
    void should_reject_unknown_event_type() {
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(), objectMapper);
        OutboxEventRecord record = new OutboxEventRecord(
                1L, "evil.unknown.event", 1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L,
                null, null, "{}",
                null, 0);

        assertThatThrownBy(() -> dispatcher.dispatch(record))
                .isInstanceOf(UnknownEventTypeException.class);
    }

    @Test
    @DisplayName("同 event_type 注册多个 consumer 时，全部调用（fan-out 语义）")
    void should_invoke_all_consumers_for_same_event_type() {
        RecordingConsumer c1 = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        RecordingConsumer c2 = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(c1, c2), objectMapper);

        dispatcher.dispatch(record(EventType.TASK_COMPLETED));

        assertThat(c1.received).hasSize(1);
        assertThat(c2.received).hasSize(1);
    }

    @Test
    @DisplayName("record.payload (JSON 字符串) 反序列化为 Map<String,Object>")
    void should_deserialize_payload_from_jsonb() throws JsonProcessingException {
        RecordingConsumer consumer = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(consumer), objectMapper);

        Map<String, Object> originalPayload = Map.of("taskId", 99L, "completedAt", "2026-07-31T10:30:00Z");
        String json = objectMapper.writeValueAsString(originalPayload);

        OutboxEventRecord record = new OutboxEventRecord(
                42L, EventType.TASK_COMPLETED.eventType(), 1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 99L, null, "trace",
                json, null, 0);

        dispatcher.dispatch(record);

        Map<String, Object> received = consumer.received.get(0).payload();
        assertThat(received)
                .containsKey("taskId")
                .containsKey("completedAt")
                .doesNotContainKey("_raw")
                .as("payload 必须是真实字段 Map，不是 Map.of('_raw', rawString)");
        assertThat(((Number) received.get("taskId")).longValue()).isEqualTo(99L);
        assertThat(received.get("completedAt")).isEqualTo("2026-07-31T10:30:00Z");
    }

    @Test
    @DisplayName("payload 反序列化失败 → 包装为 RuntimeException（dispatch 抛错由 Worker 重试）")
    void should_propagate_deserialization_failure_as_runtime() {
        RecordingConsumer consumer = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(consumer), objectMapper);

        OutboxEventRecord record = new OutboxEventRecord(
                1L, EventType.TASK_COMPLETED.eventType(), 1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L, null, "trace",
                "{not valid json", null, 0);

        assertThatThrownBy(() -> dispatcher.dispatch(record))
                .isInstanceOf(RuntimeException.class);
        assertThat(consumer.received).isEmpty();
    }

    @Test
    @DisplayName("envelope.eventId 在派发时新生成（不与 DB Long id 共享）")
    void should_generate_eventId_on_dispatch() {
        RecordingConsumer consumer = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(consumer), objectMapper);

        OutboxEventRecord record = new OutboxEventRecord(
                999L, EventType.TASK_COMPLETED.eventType(), 1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L, null, "trace",
                "{}", null, 0);

        dispatcher.dispatch(record);

        EventEnvelope env = consumer.received.get(0);
        assertThat(env.eventId()).isNotNull();
        assertThat(env.eventId()).isNotEqualTo(999L);
    }

    // ---- helpers ----

    private static OutboxEventRecord record(EventType type) {
        return new OutboxEventRecord(
                1L,
                type.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1042L,
                type.eventType().split("\\.")[0],
                99L,
                null,
                "trace-test",
                "{}",
                null,
                0);
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