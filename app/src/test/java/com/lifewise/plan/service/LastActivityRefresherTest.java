package com.lifewise.plan.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.repository.PlanRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** LastActivityRefresher 单元测试（plan-05-plan §5.5 + BR-30）。 */
@ExtendWith(MockitoExtension.class)
class LastActivityRefresherTest {

    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 8, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock PlanRepository planRepository;

    LastActivityRefresher refresher;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        // v1.0 单用户白名单（CLAUDE.md §7.3.1）：默认值 1L
        refresher = new LastActivityRefresher(planRepository, clock, 1L);
    }

    private Plan activePlan() {
        Plan p = Plan.create(1L, "t", null, "STUDY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        p.setIdInternal(1L);
        return p;
    }

    @Test
    void refresh_updates_last_activity_at_when_plan_owned() {
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 1L))
            .thenReturn(Optional.of(activePlan()));

        refresher.refreshForTask(101L, 1L);

        verify(planRepository, times(1)).save(any(Plan.class));
    }

    @Test
    void refresh_is_noop_when_plan_id_null() {
        refresher.refreshForTask(101L, null);

        verify(planRepository, never()).findByIdAndUserIdAndDeletedAtIsNull(any(), any());
        verify(planRepository, never()).save(any());
    }

    @Test
    void refresh_is_noop_when_plan_not_owned() {
        when(planRepository.findByIdAndUserIdAndDeletedAtIsNull(anyLong(), anyLong()))
            .thenReturn(Optional.empty());

        refresher.refreshForTask(101L, 1L);

        verify(planRepository, never()).save(any());
    }
}