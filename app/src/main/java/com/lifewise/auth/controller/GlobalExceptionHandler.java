package com.lifewise.auth.controller;

import com.lifewise.auth.domain.exception.AuthDomainException;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorEnvelope;
import com.lifewise.shared.integration.web.SafeMessageSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * auth 模块全局异常处理（plan-shared-integration §2.3）。
 *
 * <p>将 {@link AuthDomainException} 映射为 {@code ApiResponse.error} + 对应 HTTP 状态。
 * 客户端错误码 {@link com.lifewise.shared.integration.dto.ErrorCode} 由异常携带。
 */
@RestControllerAdvice(basePackages = "com.lifewise.auth")
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthDomainException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthDomain(
            AuthDomainException ex, HttpServletRequest req) {
        String traceId = traceId();
        LOG.warn("[auth] domain error code={} traceId={} path={} msg={}",
                ex.errorCode(), traceId, req.getRequestURI(), ex.getMessage());
        ErrorEnvelope err = new ErrorEnvelope(
                ex.errorCode().name(),
                SafeMessageSanitizer.sanitize(ex.getMessage()),
                traceId,
                null);
        return ResponseEntity.status(httpStatusFor(ex.errorCode().name()))
                .body(ApiResponse.error(err));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String traceId = traceId();
        LOG.warn("[auth] validation error traceId={} path={} errors={}",
                traceId, req.getRequestURI(), ex.getBindingResult().getErrorCount());
        Map<String, Object> details = Map.of(
                "errors", ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> Map.of("field", fe.getField(), "message",
                                fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                        .toList());
        ErrorEnvelope err = new ErrorEnvelope(
                "INVALID_INPUT",
                "request validation failed",
                traceId,
                details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(err));
    }

    private static HttpStatus httpStatusFor(String code) {
        return switch (code) {
            case "EMAIL_EXISTS", "WEAK_PASSWORD", "INVALID_INPUT" -> HttpStatus.BAD_REQUEST;
            case "INVALID_CREDENTIALS" -> HttpStatus.UNAUTHORIZED;
            case "USER_LOCKED" -> HttpStatus.LOCKED;
            case "TOKEN_INVALID", "TOKEN_REUSED", "TOKEN_EXPIRED" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static String traceId() {
        return UUID.randomUUID().toString();
    }
}