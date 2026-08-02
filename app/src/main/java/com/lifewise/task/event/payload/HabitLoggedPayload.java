package com.lifewise.task.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code habit.logged} 事件负载（plan-01-task §4）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HabitLoggedPayload(
        Long habitId,
        Long userId,
        LocalDate logDate,
        int count,
        String source) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("habitId", habitId);
        map.put("userId", userId);
        map.put("logDate", logDate == null ? null : logDate.toString());
        map.put("count", count);
        map.put("source", source);
        return map;
    }
}