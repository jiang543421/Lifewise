package com.lifewise.shared.integration.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EventEnvelope 单测（plan-shared-integration §5.4）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code event_should_validate_payload_schema} — 必填字段齐</li>
 *   <li>{@code event_should_increment_version_on_breaking_change} — version 字段保留</li>
 *   <li>{@code event_should_serialize_to_jsonb} — envelope → JSONB 友好 Map + snake_case JSON</li>
 * </ul>
 */
@DisplayName("EventEnvelope 事件信封")
class EventEnvelopeTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("envelope 必填 10 字段齐：eventId/type/version/occurredAt/userId/aggregateId/correlationId/causationId/payload/traceId")
    void envelope_exposes_all_required_fields() {
        UUID corr = UUID.randomUUID();
        UUID caus = UUID.randomUUID();
        Map<String, Object> payload = Map.of("taskId", 99L);

        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(),
                EventType.TASK_COMPLETED.eventType(),
                1,
                OffsetDateTime.of(2026, 7, 31, 10, 0, 0, 0, ZoneOffset.UTC),
                1042L,
                "task",
                99L,
                corr,
                caus,
                "trace-abc",
                payload);

        assertThat(env.eventId()).isNotNull();
        assertThat(env.eventType()).isEqualTo("task.completed");
        assertThat(env.eventVersion()).isEqualTo(1);
        assertThat(env.occurredAt()).isNotNull();
        assertThat(env.userId()).isEqualTo(1042L);
        assertThat(env.aggregateType()).isEqualTo("task");
        assertThat(env.aggregateId()).isEqualTo(99L);
        assertThat(env.correlationId()).isEqualTo(corr);
        assertThat(env.causationId()).isEqualTo(caus);
        assertThat(env.traceId()).isEqualTo("trace-abc");
        assertThat(env.payload()).containsEntry("taskId", 99L);
    }

    @Test
    @DisplayName("causationId 允许为 null（业务根事件无父事件）")
    void causation_id_nullable_for_root_events() {
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(),
                EventType.TASK_CREATED.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L,
                "task",
                1L,
                UUID.randomUUID(),
                null,
                "trace",
                Map.of());

        assertThat(env.causationId()).isNull();
    }

    @Test
    @DisplayName("eventVersion=2 显式版本（破坏性变更后升级，与 v1 兼容期共存）")
    void envelope_carries_event_version() {
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(),
                EventType.TASK_COMPLETED.eventType(),
                2,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L,
                UUID.randomUUID(), null, "trace", Map.of());
        assertThat(env.eventVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("JSON 序列化为 snake_case 全字段名（PG JSONB / consumer 友好）")
    void serialized_json_uses_snake_case_fields() throws Exception {
        EventEnvelope env = new EventEnvelope(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                EventType.TASK_COMPLETED.eventType(),
                1,
                OffsetDateTime.parse("2026-07-31T10:00:00Z"),
                1042L,
                "task",
                99L,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                null,
                "trace-xyz",
                Map.of("taskId", 99L, "completedAt", "2026-07-31T10:00:00Z"));

        String json = mapper.writeValueAsString(env);
        JsonNode node = mapper.readTree(json);

        assertThat(node.get("event_id").asText()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(node.get("event_type").asText()).isEqualTo("task.completed");
        assertThat(node.get("event_version").asInt()).isEqualTo(1);
        assertThat(node.get("occurred_at").asText()).contains("2026-07-31T10:00:00");
        assertThat(node.get("user_id").asLong()).isEqualTo(1042L);
        assertThat(node.get("aggregate_type").asText()).isEqualTo("task");
        assertThat(node.get("aggregate_id").asLong()).isEqualTo(99L);
        assertThat(node.get("correlation_id").asText()).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(node.has("causation_id"))
                .as("causationId=null 时 JSON 不输出 causation_id")
                .isFalse();
        assertThat(node.get("trace_id").asText()).isEqualTo("trace-xyz");
        assertThat(node.get("payload").get("taskId").asLong()).isEqualTo(99L);
    }

    @Test
    @DisplayName("payload 是 Map<String,Object>（PG JSONB 接收类型）")
    void payload_is_map_for_jsonb() {
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(),
                "task.completed", 1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L,
                UUID.randomUUID(), null, "trace",
                Map.of("k", "v", "n", 42));

        assertThat(env.payload()).isInstanceOf(Map.class);
        assertThat(env.payload()).containsEntry("k", "v").containsEntry("n", 42);
    }
}
