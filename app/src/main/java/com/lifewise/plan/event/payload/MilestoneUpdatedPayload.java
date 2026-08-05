package com.lifewise.plan.event.payload;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** milestone.updated 事件 payload（plan-05-plan §4.3 - 复用 BR-14 reopen 场景）。 */
public record MilestoneUpdatedPayload(
        Long milestoneId,
        Long planId,
        Long userId,
        String previousStatus,
        String newStatus,
        OffsetDateTime dueAt,
        OffsetDateTime completedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("milestoneId", milestoneId);
        m.put("planId", planId);
        m.put("userId", userId);
        m.put("previousStatus", previousStatus);
        m.put("newStatus", newStatus);
        m.put("dueAt", dueAt == null ? null : dueAt.toString());
        m.put("completedAt", completedAt == null ? null : completedAt.toString());
        return m;
    }
}