package com.lifewise.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.domain.PlanStatus;
import com.lifewise.plan.dto.PlanCreateRequest;
import com.lifewise.plan.dto.PlanView;
import com.lifewise.plan.event.payload.PlanCreatedPayload;
import com.lifewise.plan.repository.MilestoneRepository;
import com.lifewise.plan.repository.PlanRepository;
import com.lifewise.plan.service.exception.EndBeforeStartException;
import com.lifewise.plan.service.exception.PlanNotFoundException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PlanService 单元测试（plan-05-plan §5.1 + BR-15 / BR-30）。 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 8, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock PlanRepository planRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock OutboxWriter outboxWriter;
    PlanService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        service = new PlanService(planRepository, milestoneRepository, outboxWriter, clock);
    }

    private static PlanCreateRequest sampleRequest(LocalDate start, LocalDate end) {
        return new PlanCreateRequest("学完英语", "通过6月考试",
                "STUDY", start, end);
    }

    // ---------- create ----------

    @Test
    void create_persists_plan_with_default_status_active_and_emits_event() {
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setIdInternal(100L);
            return p;
        });

        PlanView view = service.create(7L,
                sampleRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)));

        assertThat(view.id()).isEqualTo(100L);
        assertThat(view.status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(view.title()).isEqualTo("学完英语");

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("plan.created");
        assertThat(env.getValue().aggregateId()).isEqualTo(100L);
        assertThat(env.getValue().userId()).isEqualTo(7L);
    }

    @Test
    void create_rejects_end_before_start() {
        assertThatThrownBy(() -> service.create(7L,
                sampleRequest(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1))))
            .isInstanceOf(EndBeforeStartException.class);

        verify(planRepository, never()).save(any());
        verify(outboxWriter, never()).append(any());
    }

    // ---------- update ----------

    @Test
    void update_preserves_status_active() {
        Plan existing = Plan.create(7L, "原标题", "原描述", "STUDY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        existing.setIdInternal(1L);
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanView view = service.update(7L, 1L,
                new PlanCreateRequest("新标题", "新描述", "WORK",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 7, 1)));

        assertThat(view.title()).isEqualTo("新标题");
        assertThat(view.status()).isEqualTo(PlanStatus.ACTIVE);
    }

    @Test
    void update_throws_for_cross_user_access() {
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(7L, 1L,
                new PlanCreateRequest("x", null, "STUDY",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1))))
            .isInstanceOf(PlanNotFoundException.class);
    }

    // ---------- softDelete (级联 milestones) ----------

    @Test
    void softDelete_cascades_to_milestones_and_emits_nothing() {
        Plan existing = Plan.create(7L, "t", null, "STUDY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        existing.setIdInternal(1L);
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(7L, 1L);

        verify(milestoneRepository, times(1)).softDeleteByPlanId(eq(1L));
        // soft delete 不发 outbox 事件（plan 业务事件仅 created/milestone-*）
        verify(outboxWriter, never()).append(any());
    }

    // ---------- abandon ----------

    @Test
    void abandon_sets_status_cancelled() {
        Plan existing = Plan.create(7L, "t", null, "STUDY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        existing.setIdInternal(1L);
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanView view = service.abandon(7L, 1L);

        assertThat(view.status()).isEqualTo(PlanStatus.CANCELLED);
    }

    // ---------- query ----------

    @Test
    void list_excludes_cancelled_by_default() {
        Plan p1 = Plan.create(7L, "A", null, "STUDY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        p1.setIdInternal(1L);
        when(planRepository.findActiveByUser(7L)).thenReturn(List.of(p1));

        List<PlanView> views = service.list(7L, null, null, false);

        assertThat(views).hasSize(1);
        verify(planRepository, times(1)).findActiveByUser(7L);
    }

    @Test
    void list_includes_cancelled_when_includeCancelled_true() {
        when(planRepository.findAllActiveOrCancelledByUser(7L))
            .thenReturn(List.of());

        service.list(7L, null, null, true);

        verify(planRepository, times(1)).findAllActiveOrCancelledByUser(7L);
    }
}
