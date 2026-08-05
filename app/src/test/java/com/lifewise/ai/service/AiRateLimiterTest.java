package com.lifewise.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifewise.ai.service.exception.RateLimitedException;
import com.lifewise.shared.infra.ratelimit.RateLimitDecision;
import com.lifewise.shared.infra.ratelimit.TokenBucketService;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AiRateLimiter 单元测试（plan-06-ai §7.2）。
 *
 * <p>三重限流（与 shared-strings §7 'ai' scope 对齐）：
 * <ol>
 *   <li>10 req/min/user — 每用户每分钟令牌桶</li>
 *   <li>60 req/h/user — 每用户每小时令牌桶</li>
 *   <li>100 req/min/global — 全局每分钟令牌桶（防 OOM 跨用户叠加）</li>
 * </ol>
 *
 * <p>策略：任意一桶拒绝 → {@link RateLimitedException}。降级（Redis 故障 →
 * fail-open）由 {@link TokenBucketService} 自身处理，本类只做决策。
 */
@ExtendWith(MockitoExtension.class)
class AiRateLimiterTest {

    private static final long USER_ID = 7L;

    @Mock TokenBucketService tokenBucketService;
    AiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AiRateLimiter(tokenBucketService);
    }

    @Test
    @DisplayName("allows the request when all three buckets have capacity")
    void acquireOrThrow_allBucketsAllow_passes() {
        // 三次调用都返回 allow
        org.mockito.Mockito.when(tokenBucketService.tryAcquire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(RateLimitDecision.allow());

        limiter.acquireOrThrow(USER_ID);
    }

    @Test
    @DisplayName("rejects with RATE_LIMITED when the per-user minute bucket is empty")
    void acquireOrThrow_userMinuteBucketEmpty_throws() {
        AtomicInteger callIdx = new AtomicInteger(0);
        org.mockito.Mockito.when(tokenBucketService.tryAcquire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenAnswer(inv -> {
                    int idx = callIdx.getAndIncrement();
                    // 第一次：user-per-minute（10/min）denied
                    if (idx == 0) {
                        return RateLimitDecision.deny(60L);
                    }
                    return RateLimitDecision.allow();
                });

        assertThatThrownBy(() -> limiter.acquireOrThrow(USER_ID))
                .isInstanceOf(RateLimitedException.class)
                .satisfies(ex -> assertThat(((RateLimitedException) ex).getRetryAfterSeconds()).isEqualTo(60));
    }

    @Test
    @DisplayName("rejects with RATE_LIMITED when the per-user hour bucket is empty")
    void acquireOrThrow_userHourBucketEmpty_throws() {
        AtomicInteger callIdx = new AtomicInteger(0);
        org.mockito.Mockito.when(tokenBucketService.tryAcquire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenAnswer(inv -> {
                    int idx = callIdx.getAndIncrement();
                    // 0=per-user minute (allow), 1=per-user hour (deny)
                    if (idx == 1) {
                        return RateLimitDecision.deny(3600L);
                    }
                    return RateLimitDecision.allow();
                });

        assertThatThrownBy(() -> limiter.acquireOrThrow(USER_ID))
                .isInstanceOf(RateLimitedException.class)
                .satisfies(ex -> assertThat(((RateLimitedException) ex).getRetryAfterSeconds()).isEqualTo(3600));
    }

    @Test
    @DisplayName("rejects with RATE_LIMITED when the global minute bucket is empty")
    void acquireOrThrow_globalMinuteBucketEmpty_throws() {
        AtomicInteger callIdx = new AtomicInteger(0);
        org.mockito.Mockito.when(tokenBucketService.tryAcquire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenAnswer(inv -> {
                    int idx = callIdx.getAndIncrement();
                    // 0=per-user minute (allow), 1=per-user hour (allow), 2=global (deny)
                    if (idx == 2) {
                        return RateLimitDecision.deny(60L);
                    }
                    return RateLimitDecision.allow();
                });

        assertThatThrownBy(() -> limiter.acquireOrThrow(USER_ID))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("treats degraded Redis (fail-open) as allowed without throwing")
    void acquireOrThrow_degradedRedis_passes() {
        org.mockito.Mockito.when(tokenBucketService.tryAcquire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(RateLimitDecision.failOpen());

        // 应当不抛异常（fail-open 行为由 TokenBucketService 决定，本类透传）
        limiter.acquireOrThrow(USER_ID);
    }

    @Test
    @DisplayName("builds the expected Redis keys for the three buckets")
    void acquireOrThrow_usesCorrectKeysAndLimits() {
        org.mockito.Mockito.when(tokenBucketService.tryAcquire(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(RateLimitDecision.allow());

        limiter.acquireOrThrow(USER_ID);

        // 验证 3 次调用：key 模式 + limit + window
        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Long> limits = org.mockito.ArgumentCaptor.forClass(Long.class);
        org.mockito.ArgumentCaptor<Duration> windows = org.mockito.ArgumentCaptor.forClass(Duration.class);
        org.mockito.Mockito.verify(tokenBucketService, org.mockito.Mockito.times(3))
                .tryAcquire(keys.capture(), limits.capture(), windows.capture());

        // 顺序：per-user-minute, per-user-hour, global-minute
        assertThat(keys.getAllValues().get(0)).isEqualTo("rl:ai:user:7:m");
        assertThat(keys.getAllValues().get(1)).isEqualTo("rl:ai:user:7:h");
        assertThat(keys.getAllValues().get(2)).isEqualTo("rl:ai:global:m");

        assertThat(limits.getAllValues().get(0)).isEqualTo(10L);
        assertThat(limits.getAllValues().get(1)).isEqualTo(60L);
        assertThat(limits.getAllValues().get(2)).isEqualTo(100L);

        assertThat(windows.getAllValues().get(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(windows.getAllValues().get(1)).isEqualTo(Duration.ofHours(1));
        assertThat(windows.getAllValues().get(2)).isEqualTo(Duration.ofMinutes(1));
    }
}
