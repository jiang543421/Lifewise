package com.lifewise.auth.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code auth.user.password_reset_requested} 事件负载（plan-auth §4）。
 *
 * <p>字段：{@code user_id}, {@code email}, {@code occurred_at}。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PasswordResetRequestedPayload(
        Long userId,
        String email,
        OffsetDateTime occurredAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("user_id", userId);
        map.put("email", email);
        map.put("occurred_at", occurredAt == null ? null : occurredAt.toString());
        return map;
    }
}