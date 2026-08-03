package com.lifewise.daily.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code ai.summary.generated} 事件负载（plan-02-daily §4 + BR-21）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiSummaryGeneratedPayload(
        Long reportId,
        Long summaryId,
        Long userId,
        String modelName,
        String modelVersion,
        OffsetDateTime generatedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reportId", reportId);
        map.put("summaryId", summaryId);
        map.put("userId", userId);
        map.put("modelName", modelName);
        map.put("modelVersion", modelVersion);
        map.put("generatedAt", generatedAt == null ? null : generatedAt.toString());
        return map;
    }
}
