package com.lifewise.auth.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code auth.token.reuse_detected} 事件负载（plan-auth §4）。
 *
 * <p>字段：{@code user_id}, {@code family_id}, {@code ip}, {@code occurred_at}。
 * 触发后 JwtRefreshServiceImpl 已撤销该 family 全部 token。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TokenReuseDetectedPayload(
        Long userId,
        String familyId,
        String ip,
        OffsetDateTime occurredAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("user_id", userId);
        map.put("family_id", familyId);
        map.put("ip", ip);
        map.put("occurred_at", occurredAt == null ? null : occurredAt.toString());
        return map;
    }
}