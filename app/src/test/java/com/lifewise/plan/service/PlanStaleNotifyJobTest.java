package com.lifewise.plan.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.repository.PlanRepository;
import com.lifewise.plan.service.notification.PlanNotifier;
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

/** PlanStaleNotifyJob 单元测试（plan-05-plan §5.7）。 */
@ExtendWith(MockitoExtension.class)
class PlanStaleNotifyJobTest {

    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 8, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock PlanRepository planRepository;
    @Mock PlanNotifier notifier;

    PlanStaleNotifyJob job;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        job = new PlanStaleNotifyJob(planRepository, notifier, clock);
    }

    private Plan plan(long id, long userId, OffsetDateTime lastActivityAt) {
        Plan p = Plan.create(userId, "t", null, "STUDY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        p.setIdInternal(id);
        p.touchActivity(lastActivityAt);
        return p;
    }

    @Test
    void notify_finds_stale_plans_above_threshold() {
        OffsetDateTime longAgo = FIXED_NOW.minusDays(30);
        when(planRepository.findStaleBefore(any(OffsetDateTime.class))).thenReturn(List.of(
            plan(1L, 7L, longAgo),
            plan(2L, 7L, longAgo)));

        job.run();

        verify(notifier, times(2)).notifyStale(any(Plan.class));
        verify(planRepository, times(1)).findStaleBefore(any(OffsetDateTime.class));
    }
}