package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** plan-auth §2.1: refresh token reuse → 全 family 失效 → {@code TOKEN_REUSED} */
public class TokenReusedException extends AuthDomainException {

    private final String familyId;

    public TokenReusedException(String familyId) {
        super(ErrorCode.TOKEN_REUSED, "refresh token reuse detected: family revoked");
        this.familyId = familyId;
    }

    public String familyId() {
        return familyId;
    }
}