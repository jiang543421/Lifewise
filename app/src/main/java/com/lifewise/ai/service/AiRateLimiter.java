package com.lifewise.ai.service;

import com.lifewise.ai.service.exception.RateLimitedException;
import com.lifewise.shared.infra.ratelimit.RateLimitDecision;
import com.lifewise.shared.infra.ratelimit.TokenBucketService;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * AI 模块三重速率限制器（plan-06-ai §7.2；shared-strings §7 'ai' scope）。
 *
 * <p>桶策略（与 plan-06-ai §2.1 + business-architecture §5.2 AI-043 一致）：
 * <ol>
 *   <li>{@code rl:ai:user:{userId}:m} — 10 req/min/user</li>
 *   <li>{@code rl:ai:user:{userId}:h} — 60 req/h/user</li>
 *   <li>{@code rl:ai:global:m} — 100 req/min/global</li>
 * </ol>
 *
 * <p>任意一桶拒绝即抛 {@link RateLimitedException}，先按"per-user-minute / per-user-hour /
 * global-minute"顺序校验（local 失败概率最高 → 优先告知客户端 retry-after）。
 *
 * <p>Redis 不可用时由 {@link TokenBucketService} 返回
 * {@link RateLimitDecision#failOpen()}，本类直接放行（fail-open 由 nginx 层兜底）。
 */
@Component
public class AiRateLimiter {

    private static final long PER_USER_MINUTE_LIMIT = 10L;
    private static final long PER_USER_HOUR_LIMIT = 60L;
    private static final long GLOBAL_MINUTE_LIMIT = 100L;

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final TokenBucketService tokenBucketService;

    public AiRateLimiter(TokenBucketService tokenBucketService) {
        this.tokenBucketService = tokenBucketService;
    }

    public void acquireOrThrow(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }

        // 1. per-user minute
        Decision first = check("rl:ai:user:" + userId + ":m", PER_USER_MINUTE_LIMIT, ONE_MINUTE);
        if (first.denied()) {
            throw new RateLimitedException(
                    "AI rate limit exceeded (per-user minute)", first.retryAfterSeconds());
        }

        // 2. per-user hour
        Decision second = check("rl:ai:user:" + userId + ":h", PER_USER_HOUR_LIMIT, ONE_HOUR);
        if (second.denied()) {
            throw new RateLimitedException(
                    "AI rate limit exceeded (per-user hour)", second.retryAfterSeconds());
        }

        // 3. global minute
        Decision third = check("rl:ai:global:m", GLOBAL_MINUTE_LIMIT, ONE_MINUTE);
        if (third.denied()) {
            throw new RateLimitedException(
                    "AI rate limit exceeded (global minute)", third.retryAfterSeconds());
        }
    }

    private Decision check(String key, long limit, Duration window) {
        RateLimitDecision d = tokenBucketService.tryAcquire(key, limit, window);
        if (d.allowed()) {
            return Decision.allow();
        }
        return Decision.deny((int) Math.min(d.retryAfterSeconds(), Integer.MAX_VALUE));
    }

    private record Decision(boolean allowed, int retryAfterSeconds) {
        static Decision allow() { return new Decision(true, 0); }
        static Decision deny(int retryAfter) { return new Decision(false, retryAfter); }
        boolean denied() { return !allowed; }
    }
}
