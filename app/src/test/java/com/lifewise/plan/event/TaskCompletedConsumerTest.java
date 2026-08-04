package com.lifewise.plan.event;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.plan.service.LastActivityRefresher;
import com.lifewise.plan.service.ProgressEvaluator;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TaskCompletedConsumer 单元测试（plan-05-plan §7 - 订阅 task.completed）。 */
@ExtendWith(MockitoExtension.class)
class TaskCompletedConsumerTest {

    @Mock ProgressEvaluator progressEvaluator;
    @Mock LastActivityRefresher refresher;

    TaskCompletedConsumer consumer;

    @Test
    void consume_triggers_progress_evaluation_and_activity_refresh() {
        consumer = new TaskCompletedConsumer(progressEvaluator, refresher);
        EventEnvelope env = envelope(101L, 1L);

        consumer.consume(env);

        verify(progressEvaluator, times(1)).compute(1L, 1L);
        verify(refresher, times(1)).refreshForTask(101L, 1L);
    }

    private EventEnvelope envelope(long taskId, long planId) {
        return new EventEnvelope(
            UUID.randomUUID(),
            EventType.TASK_COMPLETED.eventType(),
            1,
            java.time.OffsetDateTime.now(),
            1L,
            "task",
            taskId,
            null,
            null,
            null,
            Map.of("planId", planId));
    }
}