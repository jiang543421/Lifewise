package com.lifewise.shared.infra.security.exception;

/**
 * Token signature/format valid but {@code exp} claim is in the past.
 *
 * <p>Maps to HTTP 401 with {@code TOKEN_EXPIRED} per plan-shared-infra §2.1.
 */
public class JwtExpiredException extends RuntimeException {

    public JwtExpiredException(String message) {
        super(message);
    }
}