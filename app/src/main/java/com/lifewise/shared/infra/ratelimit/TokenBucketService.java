package com.lifewise.shared.infra.ratelimit;

import java.time.Clock;
import java.time.Duration;

/**
 * Wraps the atomic Redis Lua script with input validation and fail-open semantics.
 *
 * <p>Redis failures degrade to {@link RateLimitDecision#degraded()} (allowed=true)
 * — nginx handles the ultimate backstop. {@code plan-shared-infra §2.2} requires
 * this fallback so that rate limiting never causes a hard outage.
 */
public class TokenBucketService {

    private final TokenBucketScript script;
    private final Clock clock;

    public TokenBucketService(TokenBucketScript script, Clock clock) {
        this.script = script;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public RateLimitDecision tryAcquire(String key, long limit, Duration window) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("rate-limit key must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("rate-limit limit must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("rate-limit window must be positive");
        }
        long remaining;
        try {
            remaining = script.execute(key, limit, window, clock.instant());
        } catch (RuntimeException e) {
            return RateLimitDecision.failOpen();
        }
        if (remaining > 0) {
            return RateLimitDecision.allow();
        }
        return RateLimitDecision.deny(window.toSeconds());
    }
}