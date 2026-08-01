package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** plan-auth §2.1: 弱密码 → {@code WEAK_PASSWORD} */
public class WeakPasswordException extends AuthDomainException {

    public WeakPasswordException(String reason) {
        super(ErrorCode.WEAK_PASSWORD, "password too weak: " + reason);
    }
}