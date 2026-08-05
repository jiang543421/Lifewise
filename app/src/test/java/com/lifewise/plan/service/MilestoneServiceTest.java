package com.lifewise.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.domain.MilestoneStatus;
import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.domain.PlanStatus;
import com.lifewise.plan.dto.MilestoneRequest;
import com.lifewise.plan.dto.MilestoneView;
import com.lifewise.plan.repository.MilestoneRepository;
import com.lifewise.plan.repository.PlanRepository;
import com.lifewise.plan.service.exception.MilestoneAlreadyDoneException;
import com.lifewise.plan.service.exception.MilestoneDoneReadOnlyException;
import com.lifewise.plan.service.exception.MilestoneNotDoneException;
import com.lifewise.plan.service.exception.MilestoneNotFoundException;
import com.lifewise.plan.service.exception.PlanAlreadyAbandonedException;
import com.lifewise.plan.service.exception.PlanNotFoundException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** MilestoneService 单元测试（plan-05-plan §5.2 + BR-14 状态机）。 */
@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 8, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock PlanRepository planRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock OutboxWriter outboxWriter;
    @Mock MilestoneTaskLinkService linkService;
    MilestoneService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        service = new MilestoneService(planRepository, milestoneRepository,
                outboxWriter, linkService, clock);
    }

    private Plan activePlan() {
        Plan p = Plan.create(7L, "plan", null, "STUDY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        p.setIdInternal(1L);
        return p;
    }

    private static MilestoneRequest sampleRequest(java.time.OffsetDateTime dueAt, String tz) {
        return new MilestoneRequest("完成第一章", "背诵500词", dueAt, tz, 1);
    }

    // ---------- create ----------

    @Test
    void create_persists_pending_milestone_and_emits_event() {
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(activePlan()));
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(inv -> {
            Milestone m = inv.getArgument(0);
            m.setIdInternal(50L);
            return m;
        });

        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        MilestoneView view = service.create(7L, 1L,
                sampleRequest(due, "Asia/Shanghai"));

        assertThat(view.id()).isEqualTo(50L);
        assertThat(view.status()).isEqualTo(MilestoneStatus.PENDING);
        assertThat(view.dueAt()).isEqualTo(due);
        assertThat(view.timeZone()).isEqualTo("Asia/Shanghai");

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("milestone.created");
    }

    @Test
    void create_rejects_when_plan_not_found() {
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.empty());

        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        assertThatThrownBy(() -> service.create(7L, 1L, sampleRequest(due, "Asia/Shanghai")))
            .isInstanceOf(PlanNotFoundException.class);

        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void create_rejects_when_plan_is_cancelled() {
        Plan cancelled = activePlan();
        cancelled.abandon();
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(cancelled));

        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        assertThatThrownBy(() -> service.create(7L, 1L, sampleRequest(due, "Asia/Shanghai")))
            .isInstanceOf(PlanAlreadyAbandonedException.class);
    }

    // ---------- complete / reopen 状态机（BR-14）----------

    @Test
    void complete_transitions_pending_to_done_and_emits_event() {
        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m = Milestone.create(1L, 7L, "title", null,
                due, "Asia/Shanghai", 1);
        m.setIdInternal(50L);
        when(milestoneRepository.findByIdAndUserIdAndPlanIdAndDeletedAtIsNull(50L, 7L, 1L))
            .thenReturn(Optional.of(m));
        when(milestoneRepository.save(any(Milestone.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        MilestoneView view = service.complete(7L, 1L, 50L);

        assertThat(view.status()).isEqualTo(MilestoneStatus.DONE);
        assertThat(view.completedAt()).isNotNull();

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("milestone.completed");
    }

    @Test
    void complete_rejects_when_already_done() {
        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m = Milestone.create(1L, 7L, "title", null,
                due, "Asia/Shanghai", 1);
        m.setIdInternal(50L);
        m.complete(FIXED_NOW);
        when(milestoneRepository.findByIdAndUserIdAndPlanIdAndDeletedAtIsNull(50L, 7L, 1L))
            .thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.complete(7L, 1L, 50L))
            .isInstanceOf(MilestoneAlreadyDoneException.class);

        verify(outboxWriter, never()).append(any());
    }

    @Test
    void reopen_transitions_done_to_pending_and_emits_event() {
        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m = Milestone.create(1L, 7L, "title", null,
                due, "Asia/Shanghai", 1);
        m.setIdInternal(50L);
        m.complete(FIXED_NOW);
        when(milestoneRepository.findByIdAndUserIdAndPlanIdAndDeletedAtIsNull(50L, 7L, 1L))
            .thenReturn(Optional.of(m));
        when(milestoneRepository.save(any(Milestone.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        MilestoneView view = service.reopen(7L, 1L, 50L);

        assertThat(view.status()).isEqualTo(MilestoneStatus.PENDING);
        assertThat(view.completedAt()).isNull();

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("milestone.updated");
    }

    @Test
    void reopen_rejects_when_not_done() {
        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m = Milestone.create(1L, 7L, "title", null,
                due, "Asia/Shanghai", 1);
        m.setIdInternal(50L);
        when(milestoneRepository.findByIdAndUserIdAndPlanIdAndDeletedAtIsNull(50L, 7L, 1L))
            .thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.reopen(7L, 1L, 50L))
            .isInstanceOf(MilestoneNotDoneException.class);
    }

    // ---------- update：BR-14 done-only-readonly ----------

    @Test
    void update_rejects_when_milestone_done() {
        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m = Milestone.create(1L, 7L, "title", null,
                due, "Asia/Shanghai", 1);
        m.setIdInternal(50L);
        m.complete(FIXED_NOW);
        when(milestoneRepository.findByIdAndUserIdAndPlanIdAndDeletedAtIsNull(50L, 7L, 1L))
            .thenReturn(Optional.of(m));

        java.time.OffsetDateTime newDue = LocalDate.of(2026, 3, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        assertThatThrownBy(() -> service.update(7L, 1L, 50L,
                sampleRequest(newDue, "Asia/Shanghai")))
            .isInstanceOf(MilestoneDoneReadOnlyException.class);

        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void update_emits_event_on_pending_milestone() {
        java.time.OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m = Milestone.create(1L, 7L, "title", null,
                due, "Asia/Shanghai", 1);
        m.setIdInternal(50L);
        when(milestoneRepository.findByIdAndUserIdAndPlanIdAndDeletedAtIsNull(50L, 7L, 1L))
            .thenReturn(Optional.of(m));
        when(milestoneRepository.save(any(Milestone.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        java.time.OffsetDateTime newDue = LocalDate.of(2026, 3, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        service.update(7L, 1L, 50L,
                sampleRequest(newDue, "Asia/Shanghai"));

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("milestone.updated");
    }

    // ---------- query ----------

    @Test
    void list_returns_pending_and_done_for_owner() {
        java.time.OffsetDateTime due1 = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        java.time.OffsetDateTime due2 = LocalDate.of(2026, 3, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m1 = Milestone.create(1L, 7L, "a", null,
                due1, "Asia/Shanghai", 1);
        m1.setIdInternal(10L);
        Milestone m2 = Milestone.create(1L, 7L, "b", null,
                due2, "Asia/Shanghai", 2);
        m2.setIdInternal(11L);
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(Plan.create(7L, "p", null, "STUDY",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 1))));
        when(milestoneRepository
                .findAllByPlanIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(1L))
            .thenReturn(List.of(m1, m2));

        List<MilestoneView> views = service.list(7L, 1L);

        assertThat(views).hasSize(2);
        verify(milestoneRepository, times(1))
            .findAllByPlanIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(1L);
    }

    @Test
    void list_throws_when_plan_not_owned() {
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(7L, 1L))
            .isInstanceOf(PlanNotFoundException.class);
    }
}