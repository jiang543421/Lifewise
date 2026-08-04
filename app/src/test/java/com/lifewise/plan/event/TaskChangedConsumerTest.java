package com.lifewise.plan.event;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lifewise.plan.service.LastActivityRefresher;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TaskChangedConsumer 单元测试（plan-05-plan §7 - 订阅 task.created / task.updated）。 */
@ExtendWith(MockitoExtension.class)
class TaskChangedConsumerTest {

    @Mock LastActivityRefresher refresher;
    TaskChangedConsumer consumer;

    @Test
    void consume_refreshes_activity_for_created_event() {
        consumer = new TaskChangedConsumer(refresher);
        EventEnvelope env = new EventEnvelope(
            UUID.randomUUID(),
            EventType.TASK_CREATED.eventType(),
            1,
            java.time.OffsetDateTime.now(),
            1L,
            "task",
            101L,
            null,
            null,
            null,
            Map.of("planId", 1L));

        consumer.consume(env);

        verify(refresher, times(1)).refreshForTask(101L, 1L);
    }

    @Test
    void consume_refreshes_activity_for_updated_event() {
        consumer = new TaskChangedConsumer(refresher);
        EventEnvelope env = new EventEnvelope(
            UUID.randomUUID(),
            EventType.TASK_UPDATED.eventType(),
            1,
            java.time.OffsetDateTime.now(),
            1L,
            "task",
            101L,
            null,
            null,
            null,
            Map.of("planId", 1L));

        consumer.consume(env);

        verify(refresher, times(1)).refreshForTask(101L, 1L);
    }
}