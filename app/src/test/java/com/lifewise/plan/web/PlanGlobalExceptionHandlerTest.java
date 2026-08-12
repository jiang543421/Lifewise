package com.lifewise.plan.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link PlanGlobalExceptionHandler#handleUnexpected} 兜底覆盖（v1.0.3 review G1 closure）。
 *
 * <p>commit {@code 98bbdca} 引入 Exception.class fallback handler 但 0 测试覆盖。
 * 关键安全断言（CLAUDE.md §7.5）：响应 body 不暴露 stack trace / SQL / path。
 *
 * <p>走纯单元路径直接调用 handleUnexpected，避免 @WebMvcTest 装配整个 controller advice
 * 带来的脆弱性。
 */
class PlanGlobalExceptionHandlerTest {

    private final PlanGlobalExceptionHandler handler = new PlanGlobalExceptionHandler();

    @Test
    void handleUnexpected_returns_500_envelope_without_stack_trace() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleUnexpected(
                new RuntimeException("could not execute statement [SELECT * FROM plans] "
                        + "at com.lifewise.plan.PlanService.list(PlanService.java:42)"),
                new MockHttpServletRequest("GET", "/api/plans/123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.data()).isNull();
        assertThat(body.error()).isNotNull();
        assertThat(body.error().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
        assertThat(body.error().message()).isEqualTo("internal error, please retry");
        // 关键安全断言：硬编码 message，不透传原始 exception message
        assertThat(body.error().message())
                .doesNotContain("SELECT", "plans", "PlanService");
        assertThat(body.error().traceId()).isNotBlank();
        assertThat(body.error().details()).isNull();
    }
}