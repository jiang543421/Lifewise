package com.lifewise.daily.controller;

import com.lifewise.daily.service.exception.AiSummaryNotFoundException;
import com.lifewise.daily.service.exception.ContentTooLongException;
import com.lifewise.daily.service.exception.DuplicateDailyReportException;
import com.lifewise.daily.service.exception.HighlightLimitExceededException;
import com.lifewise.daily.service.exception.HighlightNotFoundException;
import com.lifewise.daily.service.exception.InvalidHighlightPositionException;
import com.lifewise.daily.web.MissingCurrentUserException;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorCode;
import com.lifewise.shared.integration.dto.ErrorEnvelope;
import com.lifewise.shared.integration.port.ResourceNotFoundException;
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

/** daily 模块全局异常处理（plan-02-daily §5 异常映射）。 */
@RestControllerAdvice(basePackages = "com.lifewise.daily")
public class DailyGlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DailyGlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(com.lifewise.daily.service.exception.DailyReportNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleReportNotFound(
            com.lifewise.daily.service.exception.DailyReportNotFoundException ex,
            HttpServletRequest req) {
        ErrorCode code = ex.getMessage() != null && ex.getMessage().contains("CROSS_USER_ACCESS")
                ? ErrorCode.CROSS_USER_ACCESS : ErrorCode.DAILY_REPORT_NOT_FOUND;
        return envelope(ex, req, HttpStatus.NOT_FOUND, code);
    }

    @ExceptionHandler(AiSummaryNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleSummaryNotFound(
            AiSummaryNotFoundException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateDailyReportException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicate(
            DuplicateDailyReportException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT);
    }

    @ExceptionHandler(ContentTooLongException.class)
    public ResponseEntity<ApiResponse<Object>> handleContentTooLong(
            ContentTooLongException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(HighlightLimitExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleHighlightLimit(
            HighlightLimitExceededException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT);
    }

    @ExceptionHandler(InvalidHighlightPositionException.class)
    public ResponseEntity<ApiResponse<Object>> handleHighlightPosition(
            InvalidHighlightPositionException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(HighlightNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleHighlightNotFound(
            HighlightNotFoundException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
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
        LOG.warn("[daily] validation error traceId={} path={} errors={}",
                traceId, req.getRequestURI(), ex.getBindingResult().getErrorCount());
        Map<String, Object> details = Map.of("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList());
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.INVALID_INPUT.name(),
                "request validation failed", traceId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(err));
    }

    private static ResponseEntity<ApiResponse<Object>> envelope(
            RuntimeException ex, HttpServletRequest req, HttpStatus status, ErrorCode code) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[daily] domain error code={} traceId={} path={} msg={}",
                code, traceId, req.getRequestURI(), ex.getMessage());
        ErrorEnvelope err = new ErrorEnvelope(code.name(), ex.getMessage(), traceId, null);
        return ResponseEntity.status(status).body(ApiResponse.error(err));
    }
}
