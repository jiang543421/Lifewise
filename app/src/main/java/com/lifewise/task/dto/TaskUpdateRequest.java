package com.lifewise.task.dto;

import com.lifewise.task.domain.TaskPriority;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record TaskUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String description,
        TaskPriority priority,
        OffsetDateTime dueAt,
        Long parentId,
        List<Long> tagIds) {
    public List<Long> tagIdsOrEmpty() { return tagIds == null ? List.of() : List.copyOf(tagIds); }
}
