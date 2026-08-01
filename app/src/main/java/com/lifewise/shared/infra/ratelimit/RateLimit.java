package com.lifewise.shared.infra.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-method rate-limit rule, enforced by {@code RateLimitAspect}.
 *
 * <p>Defaults (60 requests / 60 seconds keyed by {@code userId}) match the
 * generic {@code api} scope from plan-shared-infra §2.2.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /** Token source — {@code userId} (default), {@code ip}, or {@code global}. */
    String key() default "userId";

    /** Maximum requests permitted within {@link #window()}. */
    int limit() default 60;

    /** Sliding window length in seconds. */
    long window() default 60L;

    /** Scope name — drives Redis key prefix and error code. */
    String scope() default "api";
}