package com.lifewise.plan.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.domain.MilestoneStatus;
import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.port.out.PlanReadPortAdapter;
import com.lifewise.plan.repository.MilestoneRepository;
import com.lifewise.plan.repository.PlanRepository;
import com.lifewise.shared.integration.port.snapshot.PlanSnapshot;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PlanReadPortAdapter 单元测试（plan-05-plan §6 - 跨模块读端口）。 */
@ExtendWith(MockitoExtension.class)
class PlanReadPortAdapterTest {

    @Mock PlanRepository planRepository;
    @Mock MilestoneRepository milestoneRepository;

    @InjectMocks PlanReadPortAdapter adapter;

    private static Plan plan() {
        Plan p = Plan.create(7L, "t", null, "STUDY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        p.setIdInternal(1L);
        return p;
    }

    @Test
    void findById_returns_snapshot_when_owned() {
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(plan()));

        Optional<PlanSnapshot> snap = adapter.findById(7L, 1L);

        assertThat(snap).isPresent();
        assertThat(snap.get().id()).isEqualTo(1L);
        assertThat(snap.get().userId()).isEqualTo(7L);
    }

    @Test
    void findActiveByUser_filters_by_user() {
        when(planRepository.findActiveByUser(7L)).thenReturn(List.of(plan()));

        List<PlanSnapshot> snaps = adapter.findActiveByUser(7L);

        assertThat(snaps).hasSize(1);
        assertThat(snaps.get(0).title()).isEqualTo("t");
    }

    @Test
    void findMilestonesByTaskId_returns_milestone_ids() {
        OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone m1 = Milestone.create(1L, 7L, "a", null,
                due, "Asia/Shanghai", 1);
        m1.setIdInternal(10L);
        Milestone m2 = Milestone.create(1L, 7L, "b", null,
                due, "Asia/Shanghai", 2);
        m2.setIdInternal(11L);
        when(milestoneRepository.findByTaskId(99L)).thenReturn(List.of(m1, m2));

        List<Long> ids = adapter.findMilestonesByTaskId(99L);

        assertThat(ids).containsExactly(10L, 11L);
    }

    @Test
    void computeProgress_aggregates_done_over_total() {
        OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        Milestone done = Milestone.create(1L, 7L, "a", null,
                due, "Asia/Shanghai", 1);
        done.setIdInternal(10L);
        done.complete(LocalDate.of(2026, 2, 5).atStartOfDay().atOffset(ZoneOffset.UTC));
        Milestone pending = Milestone.create(1L, 7L, "b", null,
                due, "Asia/Shanghai", 2);
        pending.setIdInternal(11L);
        Milestone cancelled = Milestone.create(1L, 7L, "c", null,
                due, "Asia/Shanghai", 3);
        cancelled.setIdInternal(12L);
        cancelled.cancel();
        when(milestoneRepository.findAllByPlanIdAndDeletedAtIsNull(1L))
            .thenReturn(List.of(done, pending, cancelled));

        double ratio = adapter.computeProgress(1L);

        assertThat(ratio).isEqualTo(1.0 / 2); // 1 done / (1 done + 1 pending)
    }

    @SuppressWarnings("unused")
    private static MilestoneStatus ignored() { return MilestoneStatus.PENDING; }
}