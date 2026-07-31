package com.lifewise.shared.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.shared.integration.event.EventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OutboxWorker 单测（plan-shared-integration §5.1 path B）。
 *
 * <p>v1.0 path B 覆盖：
 * <ul>
 *   <li>拉取 PENDING 批次并逐条 dispatch，成功 → markDispatched</li>
 *   <li>dispatch 抛异常时不 markDispatched；内存 attempts++</li>
 *   <li>attempts >= maxRetries → log ERROR + 跳过；不搬死信（path B 无 DLQ）</li>
 *   <li>无 pending 时空跑（不调 dispatcher、不写 DB）</li>
 *   <li>批量大小可由 WorkerConfig 控制</li>
 *   <li>单条失败不影响后续事件处理</li>
 * </ul>
 */
@DisplayName("OutboxWorker 轮询 + 派发")
@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock OutboxEventRepository repository;
    @Mock OutboxDispatcher dispatcher;

    private OutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = OutboxWorker.withDefaultConfig(repository, dispatcher);
    }

    @Test
    @DisplayName("拉取 PENDING 批次并逐条 dispatch")
    void should_pick_pending_and_dispatch() {
        OutboxEventRecord r1 = pending(EventType.TASK_COMPLETED, 1L);
        OutboxEventRecord r2 = pending(EventType.TASK_CREATED, 2L);
        when(repository.findPendingBatch(50)).thenReturn(List.of(r1, r2));

        int processed = worker.runOnce();

        assertThat(processed).isEqualTo(2);
        verify(dispatcher).dispatch(r1);
        verify(dispatcher).dispatch(r2);
        verify(repository).markDispatched(1L);
        verify(repository).markDispatched(2L);
    }

    @Test
    @DisplayName("dispatch 抛异常时 → 不 markDispatched，内存 attempts++（不搬死信）")
    void should_retry_in_memory_on_failure() {
        OutboxEventRecord r = pending(EventType.TASK_COMPLETED, 1L);
        when(repository.findPendingBatch(50)).thenReturn(List.of(r));
        doThrow(new RuntimeException("downstream down")).when(dispatcher).dispatch(r);

        worker.runOnce();

        verify(repository, never()).markDispatched(any());
        assertThat(worker.attemptsSnapshot()).containsEntry(1L, 1);
    }

    @Test
    @DisplayName("attempts >= maxRetries 时只 log ERROR + 跳过；行仍 PENDING")
    void should_discard_after_max_attempts() {
        // 先预热：3 次失败让 attempts 涨到 3
        OutboxEventRecord r = pending(EventType.TASK_COMPLETED, 1L);
        when(repository.findPendingBatch(50)).thenReturn(List.of(r));
        doThrow(new RuntimeException("boom")).when(dispatcher).dispatch(r);

        for (int i = 0; i < 3; i++) {
            worker.runOnce();
        }

        assertThat(worker.attemptsSnapshot()).containsEntry(1L, 3);
        // 不搬死信（path B 无 DLQ）
        verify(repository, never()).markDispatched(any());
    }

    @Test
    @DisplayName("无 pending 事件时空跑（不调 dispatcher、不写 DB）")
    void should_be_idempotent_when_no_pending() {
        when(repository.findPendingBatch(50)).thenReturn(List.of());

        int processed = worker.runOnce();

        assertThat(processed).isZero();
        verify(dispatcher, never()).dispatch(any());
        verify(repository, never()).markDispatched(any());
    }

    @Test
    @DisplayName("批量大小可由 WorkerConfig 控制（默认 50，测试中设为 10）")
    void should_respect_poll_batch_size() {
        OutboxWorker customWorker = new OutboxWorker(repository, dispatcher,
                new OutboxWorker.WorkerConfig(10, 3));
        when(repository.findPendingBatch(10)).thenReturn(List.of());

        customWorker.runOnce();

        verify(repository).findPendingBatch(10);
    }

    @Test
    @DisplayName("dispatcher 抛异常后 worker 继续处理后续事件（不因单条失败而中断）")
    void should_continue_after_individual_failure() {
        OutboxEventRecord r1 = pending(EventType.TASK_COMPLETED, 1L);
        OutboxEventRecord r2 = pending(EventType.TASK_CREATED, 2L);
        OutboxEventRecord r3 = pending(EventType.HABIT_LOGGED, 3L);
        when(repository.findPendingBatch(50)).thenReturn(List.of(r1, r2, r3));
        lenient().doThrow(new RuntimeException("boom"))
                .when(dispatcher).dispatch(r2);

        worker.runOnce();

        verify(dispatcher).dispatch(r1);
        verify(dispatcher).dispatch(r2);
        verify(dispatcher).dispatch(r3);
        verify(repository).markDispatched(1L);
        verify(repository, never()).markDispatched(2L);
        verify(repository).markDispatched(3L);
        assertThat(worker.attemptsSnapshot()).containsEntry(2L, 1);
    }

    // ---- helpers ----

    private static OutboxEventRecord pending(EventType type, Long id) {
        return new OutboxEventRecord(
                id,
                type.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L,
                type.eventType().split("\\.")[0],
                99L,
                null,
                "trace",
                "{}",
                null,
                0);
    }
}