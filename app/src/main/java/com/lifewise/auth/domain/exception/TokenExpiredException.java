package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** plan-auth §5.2: refresh token expires_at < now → {@code TOKEN_EXPIRED} */
public class TokenExpiredException extends AuthDomainException {

    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED, "refresh token expired");
    }

    /** B-7 closure (v1.3.3): reset-password token 过期专用 message */
    public TokenExpiredException(String message) {
        super(ErrorCode.TOKEN_EXPIRED, message);
    }
}