package com.lifewise.auth.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code auth.user.logged_in} 事件负载（plan-auth §4）。
 *
 * <p>字段：{@code user_id}, {@code ip}, {@code user_agent}, {@code occurred_at}。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserLoggedInPayload(
        Long userId,
        String ip,
        String userAgent,
        OffsetDateTime occurredAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("user_id", userId);
        map.put("ip", ip);
        map.put("user_agent", userAgent);
        map.put("occurred_at", occurredAt == null ? null : occurredAt.toString());
        return map;
    }
}