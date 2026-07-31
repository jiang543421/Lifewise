package com.lifewise.shared.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.shared.integration.event.EventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * DeadLetterService 单测（plan-shared-integration §5.1）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code outbox_should_move_to_dead_letter_after_max_retries} — retry>=MAX 时搬移</li>
 *   <li>{@code outbox_should_not_move_below_max} — retry<MAX 时不动</li>
 * </ul>
 */
@DisplayName("DeadLetterService 死信转移")
@ExtendWith(MockitoExtension.class)
class DeadLetterServiceTest {

    @Mock OutboxEventRepository repository;

    @InjectMocks DeadLetterService service;

    @Test
    @DisplayName("retry_count >= 3 时搬到死信表")
    void should_move_to_dead_letter_after_max_retries() {
        OutboxEventRecord r = recordWithRetries(EventType.TASK_COMPLETED, 3);
        when(repository.findById(r.eventId())).thenReturn(Optional.of(r));

        service.moveToDeadLetter(r.eventId());

        verify(repository).moveToDeadLetter(r.eventId());
    }

    @Test
    @DisplayName("retry_count = 4 也照样搬移（>=3 触发）")
    void should_move_when_retries_exceed_max() {
        OutboxEventRecord r = recordWithRetries(EventType.AI_SUMMARY_GENERATED, 4);
        when(repository.findById(r.eventId())).thenReturn(Optional.of(r));

        service.moveToDeadLetter(r.eventId());

        verify(repository).moveToDeadLetter(r.eventId());
    }

    @Test
    @DisplayName("retry_count < 3 时不搬移（still recoverable）")
    void should_not_move_below_max() {
        OutboxEventRecord r = recordWithRetries(EventType.TASK_COMPLETED, 2);
        when(repository.findById(r.eventId())).thenReturn(Optional.of(r));

        service.moveToDeadLetter(r.eventId());

        verify(repository, never()).moveToDeadLetter(r.eventId());
    }

    @Test
    @DisplayName("MAX_RETRIES 默认值 = 3（plan §3.3 业务约束）")
    void default_max_retries_is_three() {
        assertThat(DeadLetterService.MAX_RETRIES).isEqualTo(3);
    }

    @Test
    @DisplayName("shouldDeadLetter 判定：retry_count >= MAX_RETRIES 返回 true")
    void should_dead_letter_predicate() {
        OutboxEventRecord below = recordWithRetries(EventType.PLAN_CREATED, 2);
        OutboxEventRecord at = recordWithRetries(EventType.PLAN_CREATED, 3);
        OutboxEventRecord above = recordWithRetries(EventType.PLAN_CREATED, 5);

        assertThat(service.shouldDeadLetter(below)).isFalse();
        assertThat(service.shouldDeadLetter(at)).isTrue();
        assertThat(service.shouldDeadLetter(above)).isTrue();
    }

    private static OutboxEventRecord recordWithRetries(EventType type, int retries) {
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
                OutboxStatus.FAILED,
                retries,
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
