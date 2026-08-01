package com.lifewise.shared.infra.ratelimit;

/**
 * Outcome of a token-bucket acquire. Immutable.
 *
 * @param allowed             whether the caller may proceed
 * @param degraded            {@code true} when Redis was unavailable and we
 *                            fail-open (caller should log + alert)
 * @param retryAfterSeconds   seconds until the bucket refills; 0 when allowed
 */
public record RateLimitDecision(boolean allowed, boolean degraded, long retryAfterSeconds) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, false, 0L);
    }

    public static RateLimitDecision deny(long retryAfterSeconds) {
        return new RateLimitDecision(false, false, retryAfterSeconds);
    }

    public static RateLimitDecision failOpen() {
        return new RateLimitDecision(true, true, 0L);
    }
}