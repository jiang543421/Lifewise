package com.lifewise.auth.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code auth.user.registered} 事件负载（plan-auth §4）。
 *
 * <p>字段：{@code user_id}, {@code email}, {@code timezone}, {@code locale}, {@code occurred_at}。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserRegisteredPayload(
        Long userId,
        String email,
        String timezone,
        String locale,
        OffsetDateTime occurredAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("user_id", userId);
        map.put("email", email);
        map.put("timezone", timezone);
        map.put("locale", locale);
        map.put("occurred_at", occurredAt == null ? null : occurredAt.toString());
        return map;
    }
}