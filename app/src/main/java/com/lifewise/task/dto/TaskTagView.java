package com.lifewise.task.dto;

import com.lifewise.task.domain.TaskTag;

public record TaskTagView(Long id, String name, String color) {
    public static TaskTagView from(TaskTag tag) {
        return new TaskTagView(tag.getId(), tag.getName(), tag.getColor());
    }
}
