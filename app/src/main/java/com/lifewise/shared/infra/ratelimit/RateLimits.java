package com.lifewise.shared.infra.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeating {@link RateLimit} declarations on one method
 * (e.g. layered AI limits: 10/min/user + 60/hour/user + 100/min/global).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RateLimits {

    RateLimit[] value();
}