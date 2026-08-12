package com.lifewise.ai.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.ai.service.exception.OllamaUnavailableException;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorCode;
import com.lifewise.shared.integration.dto.ErrorEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link AiGlobalExceptionHandler} 单元测试（v1.0.3 review F1 closure）。
 *
 * <p>直接调用 handler 方法验证 envelope 形状 + HTTP 状态码 + 不暴露内部细节。
 * 不走 {@code @WebMvcTest}（AI 模块当前 0 controller test，避免 mock
 * 整个 controller advice 装配带来的脆弱性）。
 *
 * <p>覆盖 3 个 handler：
 * <ul>
 *   <li>MissingCurrentUserException → 401 TOKEN_INVALID</li>
 *   <li>OllamaUnavailableException → 503 AI_UNAVAILABLE</li>
 *   <li>RuntimeException → 500 INTERNAL_ERROR（兜底 + 不含 stack）</li>
 * </ul>
 */
class AiGlobalExceptionHandlerTest {

    private final AiGlobalExceptionHandler handler = new AiGlobalExceptionHandler();

    @Test
    void missing_user_returns_401_TOKEN_INVALID_envelope() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleMissingUser(
                new MissingCurrentUserException("v1.0 single-user mode: only userId=1 is allowed"),
                new MockHttpServletRequest("GET", "/api/ai/consent"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.data()).isNull();
        assertThat(body.error()).isNotNull();
        assertThat(body.error().code()).isEqualTo(ErrorCode.TOKEN_INVALID.name());
        assertThat(body.error().message()).isEqualTo("missing or invalid user identification");
        assertThat(body.error().traceId()).isNotBlank();
        assertThat(body.error().details()).isNull();
    }

    @Test
    void ollama_unavailable_returns_503_AI_UNAVAILABLE_envelope() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleOllamaUnavailable(
                new OllamaUnavailableException("deepseek:8b timeout"),
                new MockHttpServletRequest("POST", "/api/ai/jobs"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.error().code()).isEqualTo(ErrorCode.AI_UNAVAILABLE.name());
        assertThat(body.error().message()).isEqualTo("AI service temporarily unavailable, please retry");
        assertThat(body.error().traceId()).isNotBlank();
    }

    @Test
    void unexpected_returns_500_envelope_without_stack_trace() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleUnexpected(
                new RuntimeException("could not execute statement [SELECT * FROM users] "
                        + "at com.lifewise.ai.AiJobProcessor.process(AiJobProcessor.java:42)"),
                new MockHttpServletRequest("POST", "/api/ai/jobs"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        ErrorEnvelope err = body.error();
        assertThat(err.code()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
        assertThat(err.message()).isEqualTo("internal error, please retry");
        // 关键安全断言：硬编码 message，不透传原始异常 message
        assertThat(err.message()).doesNotContain("SELECT", "users", "AiJobProcessor");
        assertThat(err.traceId()).isNotBlank();
        assertThat(err.details()).isNull();
    }

    @Test
    void unexpected_trace_id_is_unique_per_call() {
        // traceId 必须每次不同，便于服务端 log 关联
        ResponseEntity<ApiResponse<Object>> r1 = handler.handleUnexpected(
                new RuntimeException("a"), new MockHttpServletRequest());
        ResponseEntity<ApiResponse<Object>> r2 = handler.handleUnexpected(
                new RuntimeException("b"), new MockHttpServletRequest());
        assertThat(r1.getBody().error().traceId()).isNotEqualTo(r2.getBody().error().traceId());
    }
}