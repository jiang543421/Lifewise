package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** plan-auth §5.2: refresh token expires_at < now → {@code TOKEN_EXPIRED} */
public class TokenExpiredException extends AuthDomainException {

    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED, "refresh token expired");
    }
}