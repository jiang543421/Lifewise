package com.lifewise.plan.event.payload;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** milestone.missed 事件 payload（plan-05-plan §4.3 - 定时任务 sweep）。 */
public record MilestoneMissedPayload(
        Long milestoneId,
        Long planId,
        Long userId,
        OffsetDateTime dueAt,
        String timeZone) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("milestoneId", milestoneId);
        m.put("planId", planId);
        m.put("userId", userId);
        m.put("dueAt", dueAt == null ? null : dueAt.toString());
        m.put("timeZone", timeZone);
        return m;
    }
}