package com.lifewise.daily.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.lifewise.daily.domain.Mood;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code daily_report.created} 事件负载（plan-02-daily §4）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DailyReportCreatedPayload(
        Long reportId,
        Long userId,
        LocalDate reportDate,
        Mood mood,
        OffsetDateTime createdAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reportId", reportId);
        map.put("userId", userId);
        map.put("reportDate", reportDate == null ? null : reportDate.toString());
        map.put("mood", mood == null ? null : mood.name());
        map.put("createdAt", createdAt == null ? null : createdAt.toString());
        return map;
    }
}
