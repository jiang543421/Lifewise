package com.lifewise.task.controller;

import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorCode;
import com.lifewise.shared.integration.dto.ErrorEnvelope;
import com.lifewise.shared.integration.port.ResourceNotFoundException;
import com.lifewise.shared.integration.web.SafeMessageSanitizer;
import com.lifewise.task.service.exception.BackfillOutOfRangeException;
import com.lifewise.task.service.exception.BackfillRateLimitException;
import com.lifewise.task.service.exception.DuplicateTagNameException;
import com.lifewise.task.service.exception.ParentUserMismatchException;
import com.lifewise.task.service.exception.TagLimitExceededException;
import com.lifewise.task.service.exception.TaskStateConflictException;
import com.lifewise.task.web.MissingCurrentUserException;
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

/** task 模块全局异常处理（plan-01-task §5.7 异常映射）。 */
@RestControllerAdvice(basePackages = "com.lifewise.task")
public class TaskGlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TaskGlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(TaskStateConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleStateConflict(
            TaskStateConflictException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.TASK_INVALID_STATUS_TRANSITION);
    }

    @ExceptionHandler({TagLimitExceededException.class, DuplicateTagNameException.class})
    public ResponseEntity<ApiResponse<Object>> handleTagConflict(
            RuntimeException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT);
    }

    @ExceptionHandler(BackfillOutOfRangeException.class)
    public ResponseEntity<ApiResponse<Object>> handleBackfillRange(
            BackfillOutOfRangeException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.HABIT_OUT_OF_BACKFILL_WINDOW);
    }

    @ExceptionHandler(BackfillRateLimitException.class)
    public ResponseEntity<ApiResponse<Object>> handleBackfillRate(
            BackfillRateLimitException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED);
    }

    @ExceptionHandler(ParentUserMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleParentMismatch(
            ParentUserMismatchException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.FORBIDDEN, ErrorCode.CROSS_USER_ACCESS);
    }

    @ExceptionHandler(MissingCurrentUserException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingUser(
            MissingCurrentUserException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_INVALID);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[task] validation error traceId={} path={} errors={}",
                traceId, req.getRequestURI(), ex.getBindingResult().getErrorCount());
        Map<String, Object> details = Map.of("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList());
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.INVALID_INPUT.name(),
                "request validation failed", traceId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(err));
    }

    /** 兜底 500（CLAUDE.md §7.5）— 任何未捕获异常返回通用 envelope，不暴露堆栈/SQL/路径。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        LOG.error("[task] unexpected error traceId={} path={}", traceId, req.getRequestURI(), ex);
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.INTERNAL_ERROR.name(),
                "internal error, please retry", traceId, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(err));
    }

    private static ResponseEntity<ApiResponse<Object>> envelope(
            RuntimeException ex, HttpServletRequest req, HttpStatus status, ErrorCode code) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[task] domain error code={} traceId={} path={} msg={}",
                code, traceId, req.getRequestURI(), ex.getMessage());
        ErrorEnvelope err = new ErrorEnvelope(code.name(),
                SafeMessageSanitizer.sanitize(ex.getMessage()), traceId, null);
        return ResponseEntity.status(status).body(ApiResponse.error(err));
    }
}
