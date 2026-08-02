package com.lifewise.task.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code task.created} 事件负载（plan-01-task §4 + BR-30）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskCreatedPayload(
        Long taskId,
        Long userId,
        Long planId,
        OffsetDateTime createdAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", taskId);
        map.put("userId", userId);
        map.put("planId", planId);
        map.put("createdAt", createdAt == null ? null : createdAt.toString());
        return map;
    }
}