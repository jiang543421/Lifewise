package com.lifewise.shared.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.shared.integration.event.EventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OutboxWorker 单测（plan-shared-integration §5.1）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code outbox_worker_should_pick_pending_events} — 拉取 PENDING 批次</li>
 *   <li>{@code outbox_should_retry_on_failure} — 失败时增加 retry_count 并排 next_attempt_at</li>
 *   <li>{@code outbox_worker_should_be_idempotent} — 无 pending 时空跑</li>
 * </ul>
 */
@DisplayName("OutboxWorker 轮询 + 派发")
@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock OutboxEventRepository repository;
    @Mock OutboxDispatcher dispatcher;
    @Mock DeadLetterService deadLetterService;

    private OutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxWorker(repository, dispatcher, deadLetterService,
                new OutboxWorker.WorkerConfig(50, 3));
    }

    @Test
    @DisplayName("拉取 PENDING 批次并逐条 dispatch")
    void should_pick_pending_and_dispatch() {
        OutboxEventRecord r1 = pending(EventType.TASK_COMPLETED);
        OutboxEventRecord r2 = pending(EventType.TASK_CREATED);
        when(repository.findPendingBatch(50)).thenReturn(List.of(r1, r2));

        int processed = worker.runOnce();

        assertThat(processed).isEqualTo(2);
        verify(dispatcher).dispatch(r1);
        verify(dispatcher).dispatch(r2);
        verify(repository).markDispatched(r1.eventId());
        verify(repository).markDispatched(r2.eventId());
    }

    @Test
    @DisplayName("dispatch 抛异常时 → 增加 retry_count + 重排 next_attempt_at，不标记 dispatched")
    void should_retry_on_failure() {
        OutboxEventRecord r = pending(EventType.TASK_COMPLETED);
        when(repository.findPendingBatch(50)).thenReturn(List.of(r));
        doThrow(new RuntimeException("downstream down")).when(dispatcher).dispatch(r);

        worker.runOnce();

        verify(repository, never()).markDispatched(any());
        verify(repository).markFailed(eq(r.eventId()), eq(1), any(OffsetDateTime.class));
        verify(deadLetterService, never()).moveToDeadLetter(any());
    }

    @Test
    @DisplayName("无 pending 事件时空跑（不调 dispatcher、不写 DB）")
    void should_be_idempotent_when_no_pending() {
        when(repository.findPendingBatch(50)).thenReturn(List.of());

        int processed = worker.runOnce();

        assertThat(processed).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(repository, never()).markDispatched(any());
        verify(repository, never()).markFailed(any(), anyInt(), any());
    }

    @Test
    @DisplayName("批量大小可由 WorkerConfig 控制（默认 50，测试中设为 10）")
    void should_respect_poll_batch_size() {
        OutboxWorker customWorker = new OutboxWorker(repository, dispatcher, deadLetterService,
                new OutboxWorker.WorkerConfig(10, 3));
        when(repository.findPendingBatch(10)).thenReturn(List.of());

        customWorker.runOnce();

        verify(repository).findPendingBatch(10);
    }

    @Test
    @DisplayName("dispatcher 抛异常后 worker 继续处理后续事件（不因单条失败而中断）")
    void should_continue_after_individual_failure() {
        OutboxEventRecord r1 = pending(EventType.TASK_COMPLETED);
        OutboxEventRecord r2 = pending(EventType.TASK_CREATED);
        OutboxEventRecord r3 = pending(EventType.HABIT_LOGGED);
        when(repository.findPendingBatch(50)).thenReturn(List.of(r1, r2, r3));
        lenient().doThrow(new RuntimeException("boom"))
                .when(dispatcher).dispatch(r2);

        worker.runOnce();

        verify(dispatcher).dispatch(r1);
        verify(dispatcher).dispatch(r2);
        verify(dispatcher).dispatch(r3);
        verify(repository).markDispatched(r1.eventId());
        verify(repository).markFailed(eq(r2.eventId()), eq(1), any(OffsetDateTime.class));
        verify(repository).markDispatched(r3.eventId());
    }

    // ---- helpers ----

    private static OutboxEventRecord pending(EventType type) {
        return new OutboxEventRecord(
                UUID.randomUUID(),
                type.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L,
                type.eventType().split("\\.")[0],
                99L,
                UUID.randomUUID(),
                null,
                "trace",
                "{}",
                OutboxStatus.PENDING,
                0,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** Mockito eq shorthand. */
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
