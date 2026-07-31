package com.lifewise.shared.integration.event.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code task.completed} 事件负载（business-architecture §6.1 流程 1）。
 *
 * <p>字段：
 * <ul>
 *   <li>{@code taskId} — 任务主键（与 {@code outbox_events.aggregate_id} 一致）</li>
 *   <li>{@code completedAt} — 业务完成时间（UTC；plan 域用作里程碑判定的 "completedAt"）</li>
 * </ul>
 *
 * <p>序列化约定：{@code toMap()} 返回 {@link LinkedHashMap} 保字段顺序；
 * OffsetDateTime 以 ISO-8601 字符串存储（与 PG
 * {@code outbox_events.occurred_at} 同语义）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskCompletedPayload(
        Long taskId,
        OffsetDateTime completedAt) {

    /** 转 JSONB-ready Map（保字段顺序，值多态）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", taskId);
        map.put("completedAt", completedAt == null ? null : completedAt.toString());
        return map;
    }
}
