package com.lifewise.plan.service;

import com.lifewise.shared.integration.port.TaskReadPort;
import com.lifewise.shared.integration.port.snapshot.TaskSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Plan 模块对 Task 模块的最小查询门面（plan-05-plan §2.4）。
 *
 * <p>对齐模块边界规约（business-architecture §4）：plan 模块不能直接修改 task，
 * 只通过本 facade 查询（findById / findByPlanId / findByIds /
 * countCompletedInPlan）。
 *
 * <p>v1.0 默认 userId 通过 {@code lifewise.v1.user-id} 注入（CLAUDE.md §7.3.1
 * 白名单设计）；v1.1+ 切多用户时改用调用方传入的 userId。
 */
@Component
public class TaskReadPortFacade {

    private final TaskReadPort taskReadPort;
    private final long v1UserId;

    public TaskReadPortFacade(TaskReadPort taskReadPort,
                              @Value("${lifewise.v1.user-id:1}") long v1UserId) {
        this.taskReadPort = taskReadPort;
        this.v1UserId = v1UserId;
    }

    public Optional<TaskSnapshot> findById(long taskId) {
        return taskReadPort.findById(v1UserId, taskId);
    }

    public List<Long> findByPlanId(long planId) {
        return taskReadPort.findByPlanId(v1UserId, planId).stream()
            .map(TaskSnapshot::id)
            .toList();
    }

    public List<TaskSnapshot> findByIds(long userId, List<Long> taskIds) {
        return taskReadPort.findByIds(userId, taskIds);
    }

    /**
     * plan 作用域内的已完成任务数（task.status == DONE）。
     *
     * <p>取代旧 {@code countCompletedSince(userId, planId)} 的全量计数语义：进度评估只
     * 应统计该 plan 关联 task 的完成情况，否则 ratio/total 跨 plan 污染（reviewer
     * 发现的 correctness bug）。
     */
    public long countCompletedInPlan(long userId, long planId) {
        List<Long> ids = taskReadPort.findByPlanId(userId, planId).stream()
                .map(TaskSnapshot::id)
                .toList();
        if (ids.isEmpty()) {
            return 0L;
        }
        return taskReadPort.findByIds(userId, ids).stream()
                .filter(t -> "DONE".equals(t.status()))
                .count();
    }
}