package com.lifewise.task.dto;

import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskStatus;
import java.time.OffsetDateTime;

public record TaskView(Long id, String title, String description, TaskStatus status,
                       TaskPriority priority, OffsetDateTime dueAt, OffsetDateTime completedAt,
                       Long parentId) {
    public static TaskView from(com.lifewise.task.domain.Task task) {
        return new TaskView(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getPriority(), task.getDueAt(), task.getCompletedAt(), task.getParentId());
    }
}