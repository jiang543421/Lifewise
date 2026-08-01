package com.lifewise.shared.infra.security.exception;

/**
 * Token signature mismatch, malformed JWT, or wrong token type for the call site.
 *
 * <p>Maps to HTTP 401 with {@code TOKEN_INVALID} per plan-shared-infra §2.1.
 */
public class JwtInvalidException extends RuntimeException {

    public JwtInvalidException(String message) {
        super(message);
    }

    public JwtInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}