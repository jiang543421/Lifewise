package com.lifewise.daily.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code daily_report.updated} 事件负载（plan-02-daily §4）。
 *
 * <p>{@code changeType} 标字符串："publish" / "edit" / "softDelete"。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DailyReportUpdatedPayload(
        Long reportId,
        Long userId,
        String changeType,
        OffsetDateTime updatedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reportId", reportId);
        map.put("userId", userId);
        map.put("changeType", changeType);
        map.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        return map;
    }
}
