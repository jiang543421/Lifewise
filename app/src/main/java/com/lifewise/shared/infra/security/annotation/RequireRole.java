package com.lifewise.shared.infra.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the JWT {@code roles} claim to contain the given role string.
 *
 * <p>Implies {@link RequireAuth}. Returns HTTP 403 with {@code ROLE_REQUIRED}
 * when the role is missing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {

    /**
     * Required role name (e.g. {@code "ADMIN"}).
     */
    String value();
}