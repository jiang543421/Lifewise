package com.lifewise.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.domain.MilestoneStatus;
import com.lifewise.plan.repository.MilestoneRepository;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** MissedMilestoneJob 单元测试（plan-05-plan §5.6）。 */
@ExtendWith(MockitoExtension.class)
class MissedMilestoneJobTest {

    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 8, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock MilestoneRepository milestoneRepository;
    @Mock OutboxWriter outboxWriter;
    MissedMilestoneJob job;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        job = new MissedMilestoneJob(milestoneRepository, outboxWriter, clock);
    }

    private Milestone pending(long id, OffsetDateTime due) {
        Milestone m = Milestone.create(1L, 7L, "m", null, due, "UTC", 1);
        m.setIdInternal(id);
        return m;
    }

    @Test
    void sweep_marks_overdue_pending_milestones_missed_and_emits_event() {
        OffsetDateTime due = FIXED_NOW.minusDays(30);
        Milestone m1 = pending(10L, due);
        when(milestoneRepository.findOverduePending(any(OffsetDateTime.class)))
            .thenReturn(List.of(m1));
        when(milestoneRepository.save(any(Milestone.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        int swept = job.sweep();

        assertThat(swept).isEqualTo(1);
        assertThat(m1.getStatus()).isEqualTo(MilestoneStatus.MISSED);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("milestone.missed");
        assertThat(env.getValue().aggregateId()).isEqualTo(10L);
    }

    @Test
    void sweep_emits_nothing_when_no_overdue() {
        when(milestoneRepository.findOverduePending(any(OffsetDateTime.class)))
            .thenReturn(List.of());

        int swept = job.sweep();

        assertThat(swept).isZero();
        verify(outboxWriter, times(0)).append(any());
    }
}