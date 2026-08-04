package com.lifewise.plan.event.payload;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** plan.created 事件 payload（plan-05-plan §4.3）。 */
public record PlanCreatedPayload(
        Long planId,
        Long userId,
        String title,
        String type,
        LocalDate startDate,
        LocalDate targetEndDate) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planId", planId);
        m.put("userId", userId);
        m.put("title", title);
        m.put("type", type);
        m.put("startDate", startDate == null ? null : startDate.toString());
        m.put("targetEndDate", targetEndDate == null ? null : targetEndDate.toString());
        return m;
    }
}