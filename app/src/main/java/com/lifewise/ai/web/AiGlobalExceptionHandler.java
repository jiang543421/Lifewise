package com.lifewise.ai.web;

import com.lifewise.ai.service.exception.OllamaUnavailableException;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorCode;
import com.lifewise.shared.integration.dto.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * AI 模块全局异常处理（v1.0.3 review F1 closure — CLAUDE.md §4.5 + §7.5）。
 *
 * <p>v1.0.3 落地：commit {@code 2109de5} 引入
 * {@link com.lifewise.ai.web.MissingCurrentUserException}（自建 resolver），
 * 但当时未配 advice，导致 userId != 1 调用走 Spring whitelabel 500 + 裸 HTML 错误页，
 * 破坏 {@link com.lifewise.shared.integration.dto.ApiResponse} envelope 契约。
 * 本类把 AI 模块的异常统一收敛到 envelope 形状，对齐 task / daily / expense /
 * plan / diet 5 个其他模块。
 *
 * <p>映射规则：
 * <ul>
 *   <li>401 TOKEN_INVALID — MissingCurrentUserException（鉴权失败）</li>
 *   <li>503 AI_UNAVAILABLE — OllamaUnavailableException（LLM 不可用）</li>
 *   <li>500 INTERNAL_ERROR — Exception.class 兜底（不暴露堆栈 / SQL / 路径）</li>
 * </ul>
 *
 * <p>其他 AI 域异常（ConsentRequiredException / RateLimitedException /
 * UnsafeSqlException 等）走兜底 500 通道；后续若需细分错误码，按需加 specific
 * handler。
 */
@RestControllerAdvice(basePackages = "com.lifewise.ai")
public class AiGlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AiGlobalExceptionHandler.class);

    @ExceptionHandler(MissingCurrentUserException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingUser(
            MissingCurrentUserException ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[ai] missing whitelisted userId traceId={} path={} msg={}",
                traceId, req.getRequestURI(), ex.getMessage());
        ErrorEnvelope err = new ErrorEnvelope(
                ErrorCode.TOKEN_INVALID.name(),
                "missing or invalid user identification",
                traceId, null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(err));
    }

    @ExceptionHandler(OllamaUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handleOllamaUnavailable(
            OllamaUnavailableException ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[ai] ollama unavailable traceId={} path={} cause={}",
                traceId, req.getRequestURI(), ex.getMessage());
        ErrorEnvelope err = new ErrorEnvelope(
                ErrorCode.AI_UNAVAILABLE.name(),
                "AI service temporarily unavailable, please retry",
                traceId, null);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(err));
    }

    /** 兜底 500（CLAUDE.md §7.5）— 任何未捕获异常返回通用 envelope，不暴露堆栈/SQL/路径。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        LOG.error("[ai] unexpected error traceId={} path={}", traceId, req.getRequestURI(), ex);
        ErrorEnvelope err = new ErrorEnvelope(
                ErrorCode.INTERNAL_ERROR.name(),
                "internal error, please retry",
                traceId, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(err));
    }
}