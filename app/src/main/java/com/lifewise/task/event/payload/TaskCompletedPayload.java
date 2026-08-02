package com.lifewise.task.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code task.completed} 事件负载（business-architecture §6.1 + plan-01-task §4）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskCompletedPayload(
        Long taskId,
        Long userId,
        Long planId,
        OffsetDateTime completedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", taskId);
        map.put("userId", userId);
        map.put("planId", planId);
        map.put("completedAt", completedAt == null ? null : completedAt.toString());
        return map;
    }
}