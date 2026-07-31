package com.lifewise.shared.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.event.payload.TaskCompletedPayload;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OutboxWriter 单测（plan-shared-integration §5.1）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code outbox_should_write_event_in_business_transaction} — 走 repository.save()</li>
 *   <li>{@code outbox_should_persist_event_with_full_envelope} — 字段不丢失</li>
 * </ul>
 *
 * <p>事务边界验证由集成测试（{@code OutboxWriterIT}，含 {@code @Transactional} 上下文）覆盖；
 * 本单测只验证 envelope → record 映射正确。
 */
@DisplayName("OutboxWriter 事件写入")
@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock OutboxEventRepository repository;

    @InjectMocks OutboxWriter writer;

    @Test
    @DisplayName("append(EventEnvelope) → repository.save() with status=PENDING, retry=0")
    void should_append_with_pending_status() {
        EventEnvelope env = envelope();

        writer.append(env);

        ArgumentCaptor<OutboxEventRecord> cap = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(repository).save(cap.capture());

        OutboxEventRecord saved = cap.getValue();
        assertThat(saved.eventId()).isEqualTo(env.eventId());
        assertThat(saved.eventType()).isEqualTo("task.completed");
        assertThat(saved.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.retryCount()).isZero();
    }

    @Test
    @DisplayName("append 携带完整 envelope 字段（不丢 userId / aggregate / correlation）")
    void should_preserve_full_envelope() {
        UUID corr = UUID.randomUUID();
        UUID caus = UUID.randomUUID();
        EventEnvelope env = new EventEnvelope(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                EventType.MILESTONE_COMPLETED.eventType(),
                2,
                OffsetDateTime.parse("2026-07-31T10:00:00Z"),
                42L,
                "milestone",
                7L,
                corr,
                caus,
                "trace-xyz",
                Map.of("milestoneId", 7L));

        writer.append(env);

        ArgumentCaptor<OutboxEventRecord> cap = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(repository).save(cap.capture());

        OutboxEventRecord saved = cap.getValue();
        assertThat(saved.eventId())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(saved.eventType()).isEqualTo("milestone.completed");
        assertThat(saved.eventVersion()).isEqualTo(2);
        assertThat(saved.userId()).isEqualTo(42L);
        assertThat(saved.aggregateType()).isEqualTo("milestone");
        assertThat(saved.aggregateId()).isEqualTo(7L);
        assertThat(saved.correlationId()).isEqualTo(corr);
        assertThat(saved.causationId()).isEqualTo(caus);
        assertThat(saved.traceId()).isEqualTo("trace-xyz");
    }

    @Test
    @DisplayName("append 多次：每次都走 repository.save()（无幂等抑制）")
    void should_save_each_append_independently() {
        writer.append(envelope());
        writer.append(envelope());
        writer.append(envelope());

        verify(repository, times(3)).save(any(OutboxEventRecord.class));
    }

    private static EventEnvelope envelope() {
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.TASK_COMPLETED.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1042L,
                "task",
                99L,
                UUID.randomUUID(),
                null,
                "trace",
                new TaskCompletedPayload(99L, OffsetDateTime.now(ZoneOffset.UTC)).toMap());
    }
}
