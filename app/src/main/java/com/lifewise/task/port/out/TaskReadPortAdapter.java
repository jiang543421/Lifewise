package com.lifewise.task.port.out;

import com.lifewise.shared.integration.port.TaskReadPort;
import com.lifewise.shared.integration.port.snapshot.TaskSnapshot;
import com.lifewise.task.domain.Task;
import com.lifewise.task.repository.TaskRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Task 对外只读适配器：只返回不可变快照，并在仓储层完成 user ownership 校验。 */
@Component
public class TaskReadPortAdapter implements TaskReadPort {
    private final TaskRepository repository;

    public TaskReadPortAdapter(TaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TaskSnapshot> findById(Long userId, Long taskId) {
        return repository.findByIdAndDeletedAtIsNull(taskId)
                .filter(task -> userId.equals(task.getUserId()))
                .map(TaskReadPortAdapter::snapshot);
    }

    @Override
    public List<TaskSnapshot> findByIds(Long userId, List<Long> taskIds) {
        return repository.findByUserIdAndIdInAndDeletedAtIsNull(userId, List.copyOf(taskIds))
                .stream().map(TaskReadPortAdapter::snapshot).toList();
    }

    @Override
    public List<TaskSnapshot> findByPlanId(Long userId, Long planId) {
        // 计划关联由 plan.milestone_task_links 持有；task 域不跨模块读取该表。
        return List.of();
    }

    @Override
    public long countCompletedSince(Long userId, Instant since) {
        return repository.countCompletedSince(userId, OffsetDateTime.ofInstant(since, ZoneOffset.UTC));
    }

    private static TaskSnapshot snapshot(Task task) {
        return new TaskSnapshot(task.getId(), task.getUserId(), task.getTitle(),
                task.getStatus().name(), task.getCreatedAt(), task.getCompletedAt());
    }
}