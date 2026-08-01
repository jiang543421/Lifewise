package com.lifewise.shared.infra.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Redis token bucket service")
class TokenBucketServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("allows a request when Lua reports available capacity")
    void ratelimit_should_allow_under_limit() {
        TokenBucketService service = new TokenBucketService((key, limit, window, now) -> 1L, CLOCK);

        RateLimitDecision decision = service.tryAcquire("rl:api:user:42", 60, Duration.ofMinutes(1));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.degraded()).isFalse();
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @Test
    @DisplayName("rejects a request when Lua reports an empty bucket")
    void ratelimit_should_reject_over_limit() {
        TokenBucketService service = new TokenBucketService((key, limit, window, now) -> 0L, CLOCK);

        RateLimitDecision decision = service.tryAcquire("rl:api:user:42", 60, Duration.ofMinutes(1));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.degraded()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("Redis failure is explicit fail-open for nginx fallback")
    void ratelimit_should_fail_open_when_redis_is_unavailable() {
        TokenBucketService service = new TokenBucketService((key, limit, window, now) -> {
            throw new IllegalStateException("redis unavailable");
        }, CLOCK);

        RateLimitDecision decision = service.tryAcquire("rl:api:user:42", 60, Duration.ofMinutes(1));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.degraded()).isTrue();
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @Test
    @DisplayName("invalid bucket inputs fail fast before Redis")
    void ratelimit_should_validate_bucket_inputs() {
        TokenBucketService service = new TokenBucketService((key, limit, window, now) -> 1L, CLOCK);

        assertThatThrownBy(() -> service.tryAcquire("", 60, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.tryAcquire("rl:key", 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.tryAcquire("rl:key", 60, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}