package com.lifewise.task.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code task.updated} 事件负载（plan-01-task §4 + BR-30）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskUpdatedPayload(
        Long taskId,
        Long userId,
        String changeType) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", taskId);
        map.put("userId", userId);
        map.put("changeType", changeType);
        return map;
    }
}