package com.lifewise.shared.infra.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket implementation of {@link TokenBucketScript}
 * (plan-shared-infra §2.2).
 *
 * <p>v1.0 default for the AI rate limiter. State lives in a
 * {@link ConcurrentHashMap}; each bucket is mutated under a per-bucket
 * monitor so concurrent acquires on different keys never contend, while
 * acquires on the same key are serialised.
 *
 * <p>Sliding-window refill: tokens regenerate linearly at rate
 * {@code limit / window.toNanos()}. A request consumes one whole token;
 * partial tokens accumulate but cannot be spent until a full token is
 * available. The bucket capacity and refill rate are read from the
 * {@code limit} / {@code window} parameters on every call so callers can
 * change policy without restarting.
 *
 * <p><b>Future plan</b>: once {@code spring-boot-starter-data-redis} is
 * added to {@code app/pom.xml}, a {@code RedisTokenBucketScript} backed by
 * an atomic Lua script replaces this implementation. The
 * {@link TokenBucketScript} functional interface is the seam — see
 * {@code TokenBucketConfig} for the @ConditionalOnMissingBean wiring plan.
 */
final class InMemoryTokenBucketScript implements TokenBucketScript {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public long execute(String key, long limit, Duration window, Instant now) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(limit, now));
        synchronized (bucket) {
            refill(bucket, limit, window, now);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return 1L;
            }
            return 0L;
        }
    }

    private static void refill(Bucket bucket, long limit, Duration window, Instant now) {
        long nowNanos = toEpochNanos(now);
        long elapsedNanos = nowNanos - bucket.lastRefillNanos;
        if (elapsedNanos <= 0) {
            return;
        }
        double refillRate = (double) limit / (double) window.toNanos();
        double refilled = bucket.tokens + (double) elapsedNanos * refillRate;
        bucket.tokens = Math.min((double) limit, refilled);
        bucket.lastRefillNanos = nowNanos;
    }

    private static long toEpochNanos(Instant now) {
        return Math.addExact(
                Math.multiplyExact(now.getEpochSecond(), 1_000_000_000L),
                (long) now.getNano());
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(long initialTokens, Instant now) {
            this.tokens = initialTokens;
            this.lastRefillNanos = toEpochNanos(now);
        }
    }
}
