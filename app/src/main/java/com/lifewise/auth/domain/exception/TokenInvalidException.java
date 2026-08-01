package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** plan-auth §2.1: refresh token 签名/格式无效 → {@code TOKEN_INVALID} */
public class TokenInvalidException extends AuthDomainException {

    public TokenInvalidException(String reason) {
        super(ErrorCode.TOKEN_INVALID, "refresh token invalid: " + reason);
    }
}