package com.lifewise.shared.infra.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method (or whole controller) that requires a valid JWT.
 *
 * <p>Enforced by the {@code AuthAspect} (post-RED). Returns HTTP 401 with
 * {@code AUTH_REQUIRED} when no/invalid bearer token is present.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireAuth {
}