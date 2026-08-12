package com.lifewise.expense.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link ExpenseGlobalExceptionHandler#handleUnexpected} 兜底覆盖（v1.0.3 review G2 closure）。
 *
 * <p>commit {@code 98bbdca} 引入 Exception.class fallback handler 但 0 测试覆盖。
 * 关键安全断言（CLAUDE.md §7.5）：响应 body 不暴露 stack trace / SQL / path。
 *
 * <p>独立文件而非合并进 {@link ExpenseGlobalExceptionHandlerTest} 是因为
 * 那个文件用 @WebMvcTest 装配整个 controller advice；本测试用纯单元模式
 * （直接调 handler 方法），避免 mock 链路噪音。
 */
class ExpenseHandleUnexpectedTest {

    private final ExpenseGlobalExceptionHandler handler = new ExpenseGlobalExceptionHandler();

    @Test
    void handleUnexpected_returns_500_envelope_without_stack_trace() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleUnexpected(
                new RuntimeException("could not execute statement [INSERT INTO expense ...] "
                        + "at com.lifewise.expense.ExpenseService.create(ExpenseService.java:88)"),
                new MockHttpServletRequest("POST", "/api/expenses"));

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
                .doesNotContain("INSERT", "expense", "ExpenseService");
        assertThat(body.error().traceId()).isNotBlank();
        assertThat(body.error().details()).isNull();
    }
}