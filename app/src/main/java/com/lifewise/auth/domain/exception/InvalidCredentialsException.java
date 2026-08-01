package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** plan-auth §2.1: 登录邮箱或密码错误 → {@code INVALID_CREDENTIALS} */
public class InvalidCredentialsException extends AuthDomainException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "invalid email or password");
    }
}