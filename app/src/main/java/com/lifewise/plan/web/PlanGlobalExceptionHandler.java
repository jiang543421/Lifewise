package com.lifewise.plan.web;

import com.lifewise.plan.service.exception.CrossModuleTaskNotFoundException;
import com.lifewise.plan.service.exception.EndBeforeStartException;
import com.lifewise.plan.service.exception.MilestoneAlreadyDoneException;
import com.lifewise.plan.service.exception.MilestoneDoneReadOnlyException;
import com.lifewise.plan.service.exception.MilestoneNotDoneException;
import com.lifewise.plan.service.exception.PlanAlreadyAbandonedException;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorCode;
import com.lifewise.shared.integration.dto.ErrorEnvelope;
import com.lifewise.shared.integration.port.ResourceNotFoundException;
import com.lifewise.shared.integration.web.SafeMessageSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * plan 模块全局异常处理（plan-05-plan §2.5）。
 *
 * <p>映射规则：
 * <ul>
 *   <li>404 PLAN_NOT_FOUND / MILESTONE_NOT_FOUND — ResourceNotFoundException</li>
 *   <li>400 PLAN_END_BEFORE_START / MILESTONE_DONE_READONLY — domain Validation</li>
 *   <li>409 MILESTONE_ALREADY_DONE / MILESTONE_NOT_DONE / PLAN_ALREADY_ABANDONED — state 冲突</li>
 *   <li>401 TOKEN_INVALID — MissingCurrentUserException</li>
 *   <li>409 DATA_CONFLICT — DataIntegrityViolationException 兜底</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "com.lifewise.plan")
public class PlanGlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PlanGlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        ErrorCode code = switch (ex.resourceType()) {
            case "plan" -> ErrorCode.PLAN_NOT_FOUND;
            case "milestone" -> ErrorCode.MILESTONE_NOT_FOUND;
            case "task" -> ErrorCode.CROSS_MODULE_TASK_NOT_FOUND;
            default -> ErrorCode.NOT_FOUND;
        };
        return envelope(ex, req, HttpStatus.NOT_FOUND, code);
    }

    @ExceptionHandler(EndBeforeStartException.class)
    public ResponseEntity<ApiResponse<Object>> handleEndBeforeStart(
            EndBeforeStartException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.PLAN_END_BEFORE_START);
    }

    @ExceptionHandler(MilestoneDoneReadOnlyException.class)
    public ResponseEntity<ApiResponse<Object>> handleDoneReadOnly(
            MilestoneDoneReadOnlyException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.MILESTONE_DONE_READONLY);
    }

    @ExceptionHandler(MilestoneAlreadyDoneException.class)
    public ResponseEntity<ApiResponse<Object>> handleAlreadyDone(
            MilestoneAlreadyDoneException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.MILESTONE_ALREADY_DONE);
    }

    @ExceptionHandler(MilestoneNotDoneException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotDone(
            MilestoneNotDoneException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.MILESTONE_NOT_DONE);
    }

    @ExceptionHandler(PlanAlreadyAbandonedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAbandoned(
            PlanAlreadyAbandonedException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.PLAN_ALREADY_ABANDONED);
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
        LOG.warn("[plan] validation error traceId={} path={} errors={}",
                traceId, req.getRequestURI(), ex.getBindingResult().getErrorCount());
        Map<String, Object> details = Map.of("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList());
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.INVALID_INPUT.name(),
                "request validation failed", traceId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(err));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[plan] data integrity violation traceId={} path={} cause={}",
                traceId, req.getRequestURI(),
                ex.getMostSpecificCause() == null ? "n/a" : ex.getMostSpecificCause().getMessage());
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.DATA_CONFLICT.name(),
                "data conflict (duplicate or invalid reference)", traceId, null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(err));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        String typeName = ex.getRequiredType() == null
                ? "unknown" : ex.getRequiredType().getSimpleName();
        LOG.warn("[plan] type mismatch traceId={} path={} param={} requiredType={}",
                traceId, req.getRequestURI(), ex.getName(), typeName);
        Map<String, Object> details = Map.of("errors", List.of(Map.of(
                "field", ex.getName(),
                "message", "expected " + typeName)));
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.INVALID_INPUT.name(),
                "request type mismatch", traceId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(err));
    }

    /** 兜底 500（CLAUDE.md §7.5）— 任何未捕获异常返回通用 envelope，不暴露堆栈/SQL/路径。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        LOG.error("[plan] unexpected error traceId={} path={}", traceId, req.getRequestURI(), ex);
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.INTERNAL_ERROR.name(),
                "internal error, please retry", traceId, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(err));
    }

    private static ResponseEntity<ApiResponse<Object>> envelope(
            RuntimeException ex, HttpServletRequest req, HttpStatus status, ErrorCode code) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[plan] domain error code={} traceId={} path={} msg={}",
                code, traceId, req.getRequestURI(), ex.getMessage());
        ErrorEnvelope err = new ErrorEnvelope(code.name(),
                SafeMessageSanitizer.sanitize(ex.getMessage()), traceId, null);
        return ResponseEntity.status(status).body(ApiResponse.error(err));
    }
}