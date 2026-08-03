package com.lifewise.diet.controller.exception;

import com.lifewise.diet.service.exception.FoodNotFoundException;
import com.lifewise.diet.service.exception.FoodSystemReadOnlyException;
import com.lifewise.diet.service.exception.InvalidMealException;
import com.lifewise.diet.service.exception.InvalidProfileInputException;
import com.lifewise.diet.service.exception.MealNotFoundException;
import com.lifewise.diet.service.exception.NegativeNutrientException;
import com.lifewise.diet.web.MissingCurrentUserException;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorCode;
import com.lifewise.shared.integration.dto.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** diet 模块全局异常处理（plan-04-diet §6.2）。 */
@RestControllerAdvice(basePackages = "com.lifewise.diet")
public class DietGlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DietGlobalExceptionHandler.class);

    @ExceptionHandler(MissingCurrentUserException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorized(MissingCurrentUserException ex,
                                                                  HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_INVALID);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingHeader(MissingRequestHeaderException ex,
                                                                   HttpServletRequest req) {
        return envelope(new RuntimeException(ex.getMessage()), req, HttpStatus.UNAUTHORIZED,
                ErrorCode.TOKEN_INVALID);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(Exception ex,
                                                                HttpServletRequest req) {
        return envelope(new RuntimeException(ex.getMessage()), req, HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(MealNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleMealNotFound(MealNotFoundException ex,
                                                                  HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.NOT_FOUND, ErrorCode.MEAL_NOT_FOUND);
    }

    @ExceptionHandler(FoodNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleFoodNotFound(FoodNotFoundException ex,
                                                                  HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.NOT_FOUND, ErrorCode.FOOD_NOT_FOUND);
    }

    @ExceptionHandler(FoodSystemReadOnlyException.class)
    public ResponseEntity<ApiResponse<Object>> handleSystemReadOnly(FoodSystemReadOnlyException ex,
                                                                    HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.FORBIDDEN, ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(InvalidMealException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidMeal(InvalidMealException ex,
                                                                 HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.MEAL_INVALID_TIME_WINDOW);
    }

    @ExceptionHandler(NegativeNutrientException.class)
    public ResponseEntity<ApiResponse<Object>> handleNegativeNutrient(NegativeNutrientException ex,
                                                                      HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(InvalidProfileInputException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidProfile(InvalidProfileInputException ex,
                                                                    HttpServletRequest req) {
        return envelope(ex, req, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT);
    }

    /** 兜底 500（CLAUDE.md §7.5）。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        LOG.error("[diet] unexpected error traceId={} path={}", traceId, req.getRequestURI(), ex);
        ErrorEnvelope err = new ErrorEnvelope(ErrorCode.INTERNAL_ERROR.name(),
                "internal error, please retry", traceId, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(err));
    }

    private static ResponseEntity<ApiResponse<Object>> envelope(
            RuntimeException ex, HttpServletRequest req, HttpStatus status, ErrorCode code) {
        String traceId = UUID.randomUUID().toString();
        LOG.warn("[diet] domain error code={} traceId={} path={} msg={}",
                code, traceId, req.getRequestURI(), ex.getMessage());
        ErrorEnvelope err = new ErrorEnvelope(code.name(), ex.getMessage(), traceId, null);
        return ResponseEntity.status(status).body(ApiResponse.error(err));
    }
}