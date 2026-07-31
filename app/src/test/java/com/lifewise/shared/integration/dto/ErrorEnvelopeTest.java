package com.lifewise.shared.integration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ErrorEnvelope 单测（plan-shared-integration §5.3 dto_should_wrap_error_response）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>字段命名驼峰 {@code traceId} Jackson 序列化为蛇形 {@code trace_id}</li>
 *   <li>{@code details} 透传 map / null</li>
 *   <li>全 null details 不被序列化（明确契约）</li>
 * </ul>
 */
@DisplayName("ErrorEnvelope 错误信封")
class ErrorEnvelopeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("record 字段为 code/message/traceId/details（驼峰）")
    void record_fields_use_camel_case() {
        ErrorEnvelope env = new ErrorEnvelope(
                "RATE_LIMITED", "too many requests", "trace-1", Map.of("retry_after", "60"));

        assertThat(env.code()).isEqualTo("RATE_LIMITED");
        assertThat(env.message()).isEqualTo("too many requests");
        assertThat(env.traceId()).isEqualTo("trace-1");
        assertThat(env.details()).containsEntry("retry_after", "60");
    }

    @Test
    @DisplayName("JSON 序列化 traceId → trace_id（业务约定 snake_case）")
    void serialized_json_uses_trace_id_snake_case() throws Exception {
        ErrorEnvelope env = new ErrorEnvelope(
                "INTERNAL_ERROR", "boom", "trace-abc", null);
        String json = mapper.writeValueAsString(env);

        JsonNode node = mapper.readTree(json);
        assertThat(node.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(node.get("message").asText()).isEqualTo("boom");
        assertThat(node.get("trace_id").asText()).isEqualTo("trace-abc");
        assertThat(node.has("traceId"))
                .as("Java record 字段名 traceId 不应在 JSON 出现")
                .isFalse();
    }

    @Test
    @DisplayName("details=null 时 JSON 不输出 details 字段")
    void details_null_omitted_from_json() throws Exception {
        ErrorEnvelope env = new ErrorEnvelope("X", "y", "z", null);
        String json = mapper.writeValueAsString(env);

        JsonNode node = mapper.readTree(json);
        assertThat(node.has("details")).isFalse();
    }

    @Test
    @DisplayName("details=Map 时透传 JSON 对象")
    void details_map_passthrough() throws Exception {
        ErrorEnvelope env = new ErrorEnvelope(
                "INVALID_INPUT", "bad",
                "trace-7",
                Map.of("field", "amount", "reason", "negative"));
        String json = mapper.writeValueAsString(env);

        JsonNode node = mapper.readTree(json);
        assertThat(node.get("details").get("field").asText()).isEqualTo("amount");
        assertThat(node.get("details").get("reason").asText()).isEqualTo("negative");
    }
}
