package com.lifewise.shared.integration.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifewise.shared.integration.event.payload.TaskCompletedPayload;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TaskCompletedPayload 单测（plan-shared-integration §1 payload/）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code event_should_validate_payload_schema} — payload record 含 taskId + completedAt</li>
 *   <li>{@code event_should_serialize_to_jsonb} — payload 序列化为 JSONB 友好 Map</li>
 * </ul>
 */
@DisplayName("TaskCompletedPayload 任务完成事件负载")
class TaskCompletedPayloadTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("payload 携带 taskId + completedAt（业务 §6.1 流程 1 字段）")
    void payload_has_required_fields() {
        OffsetDateTime when = OffsetDateTime.of(2026, 7, 31, 10, 30, 0, 0, ZoneOffset.UTC);
        TaskCompletedPayload p = new TaskCompletedPayload(99L, when);

        assertThat(p.taskId()).isEqualTo(99L);
        assertThat(p.completedAt()).isEqualTo(when);
    }

    @Test
    @DisplayName("payload.toMap() 输出 JSONB 友好的 Map<String,Object>")
    void payload_to_map_for_jsonb() {
        OffsetDateTime when = OffsetDateTime.parse("2026-07-31T10:30:00Z");
        TaskCompletedPayload p = new TaskCompletedPayload(99L, when);

        Map<String, Object> map = p.toMap();
        assertThat(map).containsKey("taskId");
        assertThat(map).containsKey("completedAt");
        assertThat(map.get("taskId")).isEqualTo(99L);
    }

    @Test
    @DisplayName("payload → JSON 含 task_id / completed_at 字段（snake_case 与项目约定一致）")
    void payload_serializes_to_json() throws Exception {
        TaskCompletedPayload p = new TaskCompletedPayload(
                99L, OffsetDateTime.parse("2026-07-31T10:30:00Z"));
        String json = mapper.writeValueAsString(p);
        assertThat(json).contains("\"task_id\":99").contains("\"completed_at\"");
    }
}
