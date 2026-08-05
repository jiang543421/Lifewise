package com.lifewise.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.domain.MilestoneStatus;
import com.lifewise.plan.dto.ProgressView;
import com.lifewise.plan.repository.MilestoneRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ProgressEvaluator 单元测试（plan-05-plan §5.4）。 */
@ExtendWith(MockitoExtension.class)
class ProgressEvaluatorTest {

    @Mock MilestoneRepository milestoneRepository;
    @Mock TaskReadPortFacade taskReadPortFacade;

    ProgressEvaluator evaluator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(LocalDate.of(2026, 8, 3)
                .atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        evaluator = new ProgressEvaluator(milestoneRepository, taskReadPortFacade, clock);
    }

    private static Milestone link(long id, MilestoneStatus status) {
        OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m = Milestone.create(1L, 7L, "m" + id, null,
                due, "Asia/Shanghai", 1);
        m.setIdInternal(id);
        switch (status) {
            case DONE -> m.complete(LocalDate.of(2026, 2, 5)
                    .atStartOfDay().atOffset(ZoneOffset.UTC));
            case MISSED -> m.markMissed();
            case CANCELLED -> m.cancel();
            default -> { /* PENDING / IN_PROGRESS */ }
        }
        return m;
    }

    @Test
    void progress_returns_ratio_when_no_links() {
        when(milestoneRepository.findAllByPlanIdAndDeletedAtIsNull(1L))
            .thenReturn(List.of(
                link(10L, MilestoneStatus.DONE),
                link(11L, MilestoneStatus.PENDING),
                link(12L, MilestoneStatus.PENDING)));
        when(taskReadPortFacade.findByPlanId(1L)).thenReturn(List.of());

        ProgressView view = evaluator.compute(7L, 1L);

        assertThat(view.completedMilestones()).isEqualTo(1);
        assertThat(view.totalMilestones()).isEqualTo(3);
        assertThat(view.ratio()).isEqualTo(1.0 / 3);
        assertThat(view.linkedTaskIds()).isEmpty();
        verify(taskReadPortFacade, times(1)).findByPlanId(1L);
    }

    @Test
    void progress_counts_completed_tasks_only() {
        when(milestoneRepository.findAllByPlanIdAndDeletedAtIsNull(1L))
            .thenReturn(List.of(link(10L, MilestoneStatus.PENDING)));
        when(taskReadPortFacade.findByPlanId(1L)).thenReturn(List.of(101L, 102L, 103L));
        when(taskReadPortFacade.countCompletedInPlan(7L, 1L))
            .thenReturn(2L);

        ProgressView view = evaluator.compute(7L, 1L);

        assertThat(view.completedTasks()).isEqualTo(2);
        assertThat(view.totalTasks()).isEqualTo(3);
        verify(taskReadPortFacade, times(1)).countCompletedInPlan(7L, 1L);
    }

    @Test
    void progress_scopes_task_count_to_plan_only() {
        // 回归：旧实现 countCompletedSince 返回全量计数，会让 totalTasks=3 + completedTasks=42
        // 现在必须按 plan 内部 task.status==DONE 计数，且空 list 短路（不再调 countCompletedInPlan）
        when(milestoneRepository.findAllByPlanIdAndDeletedAtIsNull(1L))
            .thenReturn(List.of(link(10L, MilestoneStatus.PENDING)));
        when(taskReadPortFacade.findByPlanId(1L)).thenReturn(List.of());

        ProgressView view = evaluator.compute(7L, 1L);

        assertThat(view.totalTasks()).isZero();
        assertThat(view.completedTasks()).isZero();
        verify(taskReadPortFacade, times(1)).findByPlanId(1L);
        verify(taskReadPortFacade, times(0)).countCompletedInPlan(anyLong(), anyLong());
    }

    @Test
    void progress_cancels_milestones_are_excluded_from_total() {
        when(milestoneRepository.findAllByPlanIdAndDeletedAtIsNull(1L))
            .thenReturn(List.of(
                link(10L, MilestoneStatus.DONE),
                link(11L, MilestoneStatus.CANCELLED),
                link(12L, MilestoneStatus.MISSED)));
        when(taskReadPortFacade.findByPlanId(1L)).thenReturn(List.of());

        ProgressView view = evaluator.compute(7L, 1L);

        assertThat(view.totalMilestones()).isEqualTo(2);
        assertThat(view.completedMilestones()).isEqualTo(1);
    }
}