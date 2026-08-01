package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/** plan-auth §2.1: 注册时邮箱已存在 → {@code EMAIL_EXISTS} */
public class EmailExistsException extends AuthDomainException {

    public EmailExistsException(String email) {
        super(ErrorCode.EMAIL_EXISTS, "email already registered: " + redact(email));
    }

    private static String redact(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}