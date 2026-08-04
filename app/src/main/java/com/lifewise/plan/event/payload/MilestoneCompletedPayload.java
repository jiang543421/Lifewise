package com.lifewise.plan.event.payload;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** milestone.completed 事件 payload（plan-05-plan §4.3）。 */
public record MilestoneCompletedPayload(
        Long milestoneId,
        Long planId,
        Long userId,
        OffsetDateTime completedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("milestoneId", milestoneId);
        m.put("planId", planId);
        m.put("userId", userId);
        m.put("completedAt", completedAt == null ? null : completedAt.toString());
        return m;
    }
}