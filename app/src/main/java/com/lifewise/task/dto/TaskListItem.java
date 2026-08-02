package com.lifewise.task.dto;

import com.lifewise.task.domain.Task;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record TaskListItem(
        Long id,
        String title,
        TaskStatus status,
        TaskPriority priority,
        OffsetDateTime dueAt,
        OffsetDateTime completedAt,
        List<Long> tagIds) {
    public static TaskListItem of(Task task, List<Long> tagIds) {
        return new TaskListItem(task.getId(), task.getTitle(), task.getStatus(), task.getPriority(),
                task.getDueAt(), task.getCompletedAt(), tagIds);
    }
}
