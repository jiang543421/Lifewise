package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** auth 模块领域异常基类。携带 {@link ErrorCode} 用于 GlobalExceptionHandler 映射。 */
public abstract class AuthDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected AuthDomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}