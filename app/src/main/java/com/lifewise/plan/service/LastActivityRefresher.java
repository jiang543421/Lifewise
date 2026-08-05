package com.lifewise.plan.service;

import com.lifewise.plan.repository.PlanRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-30：plan.last_activity_at 由 task.* 事件触发刷新（plan-05-plan §7）。
 *
 * <p>供 {@code TaskCompletedConsumer / TaskReopenedConsumer / TaskChangedConsumer} 调用。
 * planId 为 null 或 plan 不属于当前 user 时静默 noop。
 *
 * <p>v1.0 默认 userId 通过 {@code lifewise.v1.user-id} 注入（CLAUDE.md §7.3.1 白名单设计）；
 * v1.1+ 切多用户时改用调用方传入的 userId。
 */
@Service
public class LastActivityRefresher {

    private final PlanRepository planRepository;
    private final Clock clock;
    private final long v1UserId;

    public LastActivityRefresher(PlanRepository planRepository,
                                 Clock clock,
                                 @Value("${lifewise.v1.user-id:1}") long v1UserId) {
        this.planRepository = planRepository;
        this.clock = clock;
        this.v1UserId = v1UserId;
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
        planRepository.findByIdAndUserIdAndDeletedAtIsNull(planId, v1UserId)
                .ifPresent(plan -> {
                    plan.touchActivity(OffsetDateTime.now(clock));
                    planRepository.save(plan);
                });
    }
}