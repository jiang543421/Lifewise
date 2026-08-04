package com.lifewise.plan.service;

import com.lifewise.shared.integration.port.TaskReadPort;
import com.lifewise.shared.integration.port.snapshot.TaskSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Plan 模块对 Task 模块的最小查询门面（plan-05-plan §2.4）。
 *
 * <p>对齐模块边界规约（business-architecture §4）：plan 模块不能直接修改 task，
 * 只通过本 facade 查询（findById / findByPlanId / countCompletedSince）。
 * facade 内部把 {@link TaskSnapshot} 投影为更小字段的 record，避免外部依赖。
 */
@Component
public class TaskReadPortFacade {

    private final TaskReadPort taskReadPort;

    public TaskReadPortFacade(TaskReadPort taskReadPort) {
        this.taskReadPort = taskReadPort;
    }

    public Optional<TaskSnapshot> findById(long taskId) {
        return taskReadPort.findById(1L, taskId);
    }

    public List<Long> findByPlanId(long planId) {
        return taskReadPort.findByPlanId(1L, planId).stream()
            .map(TaskSnapshot::id)
            .toList();
    }

    public long countCompletedSince(long userId, long planId) {
        Instant since = Instant.EPOCH;  // 全量计数（v1.0 简化）
        return taskReadPort.countCompletedSince(userId, since);
    }
}