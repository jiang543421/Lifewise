package com.lifewise.shared.integration.port;

import com.lifewise.shared.integration.port.snapshot.TaskSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Task 模块对外只读端口（plan-shared-integration §2.2 + business-architecture §4）。
 *
 * <p>约束：
 * <ul>
 *   <li>所有方法第一参数为 {@code userId}，强制所有权校验</li>
 *   <li>返回 {@link TaskSnapshot} 而非 JPA entity（防跨模块耦合 Hibernate 状态）</li>
 *   <li>仅暴露查询；不允许 save / update / delete（接口契约层校验）</li>
 * </ul>
 *
 * <p>实现由 task 模块在 {@code com.lifewise.task.port.out.TaskReadPortAdapter} 提供，
 * 跨模块消费者通过 Spring DI 注入本接口。
 */
public interface TaskReadPort {

    Optional<TaskSnapshot> findById(Long userId, Long taskId);

    List<TaskSnapshot> findByIds(Long userId, List<Long> taskIds);

    List<TaskSnapshot> findByPlanId(Long userId, Long planId);

    /** 自 {@code since} 以来已完成任务数（用于 plan 进度 / daily report 统计）。 */
    long countCompletedSince(Long userId, Instant since);
}
