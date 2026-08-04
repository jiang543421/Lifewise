package com.lifewise.plan.service;

import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.repository.PlanRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-30：plan.last_activity_at 由 task.* 事件触发刷新（plan-05-plan §7）。
 *
 * <p>供 {@code TaskCompletedConsumer / TaskReopenedConsumer / TaskChangedConsumer} 调用。
 * planId 为 null 或 plan 不属于当前 user 时静默 noop。
 */
@Service
public class LastActivityRefresher {

    private final PlanRepository planRepository;
    private final Clock clock;

    public LastActivityRefresher(PlanRepository planRepository, Clock clock) {
        this.planRepository = planRepository;
        this.clock = clock;
    }

    /**
     * 双参版本：直接根据 payload 携带的 planId 找到 plan 并刷新 last_activity_at。
     * 无 plan 关联（planId=null）或 plan 不属于该 user 时静默 noop。
     */
    @Transactional
    public void refreshForTask(Long taskId, Long planId) {
        if (planId == null || taskId == null) {
            return;
        }
        planRepository.findByIdAndUserIdAndDeletedAtIsNull(planId, 1L)  // v1.0 固定 userId=1
                .ifPresent(plan -> {
                    plan.touchActivity(OffsetDateTime.now(clock));
                    planRepository.save(plan);
                });
    }

    /** 抑制 Plan 引用未用警告。 */
    @SuppressWarnings("unused")
    private static Class<?> anchor() {
        return Plan.class;
    }
}