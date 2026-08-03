package com.lifewise.expense.controller;

import com.lifewise.expense.service.exception.BudgetAlreadyExistsException;
import com.lifewise.expense.service.exception.BudgetNotFoundException;
import com.lifewise.expense.service.exception.CategoryHasBudgetException;
import com.lifewise.expense.service.exception.CategoryNameExistsException;
import com.lifewise.expense.service.exception.CategoryNotFoundException;
import com.lifewise.expense.service.exception.CategoryProtectedException;
import com.lifewise.expense.service.exception.ExpenseNotFoundException;
import com.lifewise.expense.web.MissingCurrentUserException;
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

/** expense 模块全局异常处理（plan-03-expense §2.2）。 */
@RestControllerAdvice(basePackages = "com.lifewise.expense")
public class ExpenseGlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExpenseGlobalExceptionHandler.class);

    @ExceptionHandler({ExpenseNotFoundException.class, BudgetNotFoundException.class, CategoryNotFoundException.class})
    public ResponseEntity<ApiResponse<Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        ErrorCode code = codeFromType(ex);
        return envelope(ex, req, HttpStatus.NOT_FOUND, code);
    }

    @ExceptionHandler(CategoryNameExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleCategoryName(
            CategoryNameExistsException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.CATEGORY_NAME_EXISTS);
    }

    @ExceptionHandler(CategoryProtectedException.class)
    public ResponseEntity<ApiResponse<Object>> handleCategoryProtected(
            CategoryProtectedException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.CATEGORY_PROTECTED);
    }

    @ExceptionHandler(CategoryHasBudgetException.class)
    public ResponseEntity<ApiResponse<Object>> handleCategoryHasBudget(
            CategoryHasBudgetException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.CATEGORY_HAS_BUDGET);
    }

    @ExceptionHandler(BudgetAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBudgetExists(
            BudgetAlreadyExistsException ex, HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.CONFLICT, ErrorCode.BUDGET_ALREADY_EXISTS);
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
        LOG.warn("[expense] validation error traceId={} path={} errors={}",
                traceId, req.getRequestURI(), ex.getBindingResult().getErrorCount());
        Map<String, Object> details = Map.of("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList());
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.INVALID_INPUT.name(),
                "request validation failed", traceId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(err));
    }

    private static ErrorCode codeFromType(ResourceNotFoundException ex) {
        return switch (ex.resourceType()) {
            case "expense" -> ErrorCode.EXPENSE_NOT_FOUND;
            case "budget" -> ErrorCode.BUDGET_NOT_FOUND;
            case "expense_category" -> ErrorCode.CATEGORY_NOT_FOUND;
            default -> ErrorCode.NOT_FOUND;
        };
    }

    private static ResponseEntity<ApiResponse<Object>> envelope(
            RuntimeException ex, HttpServletRequest req, HttpStatus status, ErrorCode code) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[expense] domain error code={} traceId={} path={} msg={}",
                code, traceId, req.getRequestURI(), ex.getMessage());
        ErrorEnvelope err = new ErrorEnvelope(code.name(), ex.getMessage(), traceId, null);
        return ResponseEntity.status(status).body(ApiResponse.error(err));
    }
}