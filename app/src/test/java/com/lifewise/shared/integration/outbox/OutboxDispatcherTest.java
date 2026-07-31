package com.lifewise.shared.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.event.payload.TaskCompletedPayload;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OutboxDispatcher 单测（plan-shared-integration §5.1）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code outbox_dispatcher_should_route_by_event_type} — 按 event_type 找 consumer</li>
 *   <li>{@code outbox_dispatcher_should_invoke_consumer} — 调用 consumer.consume(envelope)</li>
 * </ul>
 */
@DisplayName("OutboxDispatcher 事件路由")
class OutboxDispatcherTest {

    @Test
    @DisplayName("按 event_type 找到匹配 consumer 并调用 consume()")
    void should_route_to_matching_consumer() {
        RecordingConsumer dailyConsumer = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        RecordingConsumer planConsumer = new RecordingConsumer(EventType.PLAN_CREATED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(dailyConsumer, planConsumer));

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
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of());
        OutboxEventRecord record = recordFrom(envelope(EventType.AI_SUMMARY_GENERATED));

        assertThatThrownBy(() -> dispatcher.dispatch(record))
                .isInstanceOf(NoConsumerRegisteredException.class)
                .hasMessageContaining("ai.summary.generated");
    }

    @Test
    @DisplayName("未知 event_type（不在 EventType 枚举内）抛 UnknownEventTypeException")
    void should_reject_unknown_event_type() {
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of());
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(), "evil.unknown.event", 1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L, UUID.randomUUID(), null, "trace",
                java.util.Map.of());
        OutboxEventRecord record = recordFrom(env);

        assertThatThrownBy(() -> dispatcher.dispatch(record))
                .isInstanceOf(UnknownEventTypeException.class);
    }

    @Test
    @DisplayName("同 event_type 注册多个 consumer 时，全部调用（fan-out 语义）")
    void should_invoke_all_consumers_for_same_event_type() {
        RecordingConsumer c1 = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        RecordingConsumer c2 = new RecordingConsumer(EventType.TASK_COMPLETED.eventType());
        OutboxDispatcher dispatcher = new OutboxDispatcher(List.of(c1, c2));

        dispatcher.dispatch(recordFrom(envelope(EventType.TASK_COMPLETED)));

        assertThat(c1.received).hasSize(1);
        assertThat(c2.received).hasSize(1);
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
