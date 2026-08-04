package com.lifewise.plan.service;

import com.lifewise.plan.repository.PlanRepository;
import com.lifewise.plan.service.notification.PlanNotifier;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * 长期未活动 Plan 通知 Job（plan-05-plan §5.7）。
 *
 * <p>每日 04:00 运行；扫描 last_activity_at 超过 14 天的 ACTIVE plan，
 * 委托 {@link PlanNotifier} 推送提醒（v1.0 noop，v1.1 push）。
 */
@Component
public class PlanStaleNotifyJob {

    private static final long STALE_DAYS = 14L;

    private final PlanRepository planRepository;
    private final PlanNotifier notifier;
    private final Clock clock;

    public PlanStaleNotifyJob(PlanRepository planRepository,
                              PlanNotifier notifier,
                              Clock clock) {
        this.planRepository = planRepository;
        this.notifier = notifier;
        this.clock = clock;
    }

    public void run() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock)
                .minus(STALE_DAYS, ChronoUnit.DAYS);
        planRepository.findStaleBefore(cutoff)
                .forEach(notifier::notifyStale);
    }
}