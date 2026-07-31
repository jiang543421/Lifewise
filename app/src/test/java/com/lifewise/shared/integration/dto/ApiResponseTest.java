package com.lifewise.shared.integration.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ApiResponse 单测（来自 plan-shared-integration §5.3）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code dto_should_wrap_success_response} — 成功分支 data/error/meta 正确性</li>
 *   <li>{@code dto_should_wrap_error_response} — 失败分支 data=null、error 含正确字段</li>
 *   <li>{@code dto_should_paginate_with_meta} — 分页响应 meta 含 total/page/limit/has_next</li>
 *   <li>{@code dto_should_omit_meta_when_not_paginated} — 非分页接口 meta=null</li>
 * </ul>
 */
@DisplayName("ApiResponse 信封")
class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("ok(data) 包装为 success=true 且 data/error/meta 正确")
    void ok_wraps_success_response() {
        ApiResponse<List<String>> resp = ApiResponse.ok(List.of("a", "b"));

        assertThat(resp.success()).isTrue();
        assertThat(resp.data()).containsExactly("a", "b");
        assertThat(resp.error()).isNull();
        assertThat(resp.meta()).isNull();
    }

    @Test
    @DisplayName("ok(null) 仍 success=true 但 data=null（与 error 区分）")
    void ok_with_null_data_still_success() {
        ApiResponse<String> resp = ApiResponse.ok(null);

        assertThat(resp.success()).isTrue();
        assertThat(resp.data()).isNull();
        assertThat(resp.error()).isNull();
    }

    @Test
    @DisplayName("error(envelope) 包装为 success=false 且 data=null")
    void error_wraps_failure_response() {
        ErrorEnvelope env = new ErrorEnvelope("TASK_NOT_FOUND", "task 99 not found", "trace-xyz", null);

        ApiResponse<Object> resp = ApiResponse.error(env);

        assertThat(resp.success()).isFalse();
        assertThat(resp.data()).isNull();
        assertThat(resp.error()).isSameAs(env);
        assertThat(resp.meta()).isNull();
    }

    @Test
    @DisplayName("error(envelope) 不允许 null 包裹（强制显式传 ErrorEnvelope）")
    void error_rejects_null_envelope() {
        assertThatThrownBy(() -> ApiResponse.<Object>error(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("error");
    }

    @Test
    @DisplayName("paged(data, pageMeta) 同时承载 data 与 meta")
    void paged_wraps_data_and_meta() {
        PageMeta meta = new PageMeta(100L, 1, 20, true);
        ApiResponse<List<Integer>> resp = ApiResponse.paged(List.of(1, 2, 3), meta);

        assertThat(resp.success()).isTrue();
        assertThat(resp.data()).hasSize(3);
        assertThat(resp.meta()).isSameAs(meta);
        assertThat(resp.error()).isNull();
    }

    @Test
    @DisplayName("JSON 序列化字段名为 success/data/error/meta")
    void serialized_json_uses_expected_field_names() throws Exception {
        ApiResponse<List<String>> resp = ApiResponse.ok(List.of("hello"));
        String json = mapper.writeValueAsString(resp);

        JsonNode node = mapper.readTree(json);
        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("data").isArray()).isTrue();
        assertThat(node.get("error").isNull()).isTrue();
        assertThat(node.get("meta").isNull()).isTrue();
    }

    @Test
    @DisplayName("失败响应 JSON 形如 {success:false, data:null, error:{...}, meta:null}")
    void failure_response_json_shape() throws Exception {
        ApiResponse<Object> resp = ApiResponse.error(
                new ErrorEnvelope("INVALID_INPUT", "bad payload", "trace-1", null));
        String json = mapper.writeValueAsString(resp);

        JsonNode node = mapper.readTree(json);
        assertThat(node.get("success").asBoolean()).isFalse();
        assertThat(node.get("data").isNull()).isTrue();
        assertThat(node.get("error").get("code").asText()).isEqualTo("INVALID_INPUT");
        assertThat(node.get("error").get("message").asText()).isEqualTo("bad payload");
        assertThat(node.get("error").get("trace_id").asText()).isEqualTo("trace-1");
        assertThat(node.get("meta").isNull()).isTrue();
    }
}
