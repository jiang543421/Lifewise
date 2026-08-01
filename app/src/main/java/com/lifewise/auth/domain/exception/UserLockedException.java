package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** plan-auth §5.1.1: 5 次失败后 IP 锁定 15min → {@code USER_LOCKED} */
public class UserLockedException extends AuthDomainException {

    public UserLockedException(String reason) {
        super(ErrorCode.USER_LOCKED, reason);
    }
}