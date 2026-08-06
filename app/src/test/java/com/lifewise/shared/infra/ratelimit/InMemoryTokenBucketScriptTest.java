package com.lifewise.shared.infra.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * In-memory token-bucket script unit tests.
 *
 * <p>Pure JUnit5 + AssertJ, no Spring container — the script is a plain
 * class, the Spring wiring is exercised by
 * {@code SharedIntegrationContextTest}.
 */
@DisplayName("In-memory token-bucket script")
class InMemoryTokenBucketScriptTest {

    private static final String KEY = "rl:test:bucket";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final long LIMIT = 10L;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Test
    @DisplayName("consumes the bucket monotonically to zero, then denies further requests")
    void monotonic_consume_to_zero_then_deny() {
        InMemoryTokenBucketScript script = new InMemoryTokenBucketScript();

        for (int i = 0; i < LIMIT; i++) {
            long result = script.execute(KEY, LIMIT, WINDOW, T0);
            assertThat(result).as("acquire #%d (should be allowed)", i + 1).isEqualTo(1L);
        }
        // 第 (limit+1) 次：bucket 空，立即拒绝
        assertThat(script.execute(KEY, LIMIT, WINDOW, T0)).isZero();
    }

    @Test
    @DisplayName("refills tokens after one full window elapses")
    void refill_after_window_passes() {
        InMemoryTokenBucketScript script = new InMemoryTokenBucketScript();

        // 消耗光
        for (int i = 0; i < LIMIT; i++) {
            script.execute(KEY, LIMIT, WINDOW, T0);
        }
        assertThat(script.execute(KEY, LIMIT, WINDOW, T0)).isZero();

        // 推进恰好 1 window：tokens 重新补满到 LIMIT
        Instant t1 = T0.plus(WINDOW);
        long firstAfter = script.execute(KEY, LIMIT, WINDOW, t1);
        assertThat(firstAfter).isEqualTo(1L);
        // 补满后还能再消费 LIMIT-1 次
        for (int i = 0; i < LIMIT - 1; i++) {
            assertThat(script.execute(KEY, LIMIT, WINDOW, t1))
                    .as("post-refill acquire #%d", i + 2)
                    .isEqualTo(1L);
        }
        assertThat(script.execute(KEY, LIMIT, WINDOW, t1)).isZero();
    }

    @Test
    @DisplayName("concurrent acquires on the same key consume exactly `limit` tokens total")
    void concurrent_acquires_consume_exact_count() throws InterruptedException {
        InMemoryTokenBucketScript script = new InMemoryTokenBucketScript();
        int threads = 16;
        int perThread = 50;        // total attempts = 800
        int totalAttempts = threads * perThread;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < perThread; j++) {
                            long r = script.execute(KEY, LIMIT, WINDOW, T0);
                            if (r > 0) {
                                allowed.incrementAndGet();
                            } else {
                                denied.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            // 没有时间推进 → 桶最多只够 LIMIT 个 token
            assertThat(allowed.get()).as("allowed").isEqualTo((int) LIMIT);
            assertThat(denied.get()).as("denied").isEqualTo(totalAttempts - (int) LIMIT);
            assertThat(allowed.get() + denied.get()).isEqualTo(totalAttempts);
        } finally {
            if (!pool.isTerminated()) {
                pool.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("fractional refill accumulates: half-window grants floor(limit/2) usable tokens")
    void fractional_refill_accumulates_correctly() {
        InMemoryTokenBucketScript script = new InMemoryTokenBucketScript();

        // 消耗光
        for (int i = 0; i < LIMIT; i++) {
            script.execute(KEY, LIMIT, WINDOW, T0);
        }

        // 推进半 window：桶应该补到 LIMIT/2 = 5 token
        Instant half = T0.plus(WINDOW.dividedBy(2));
        for (int i = 0; i < LIMIT / 2; i++) {
            long r = script.execute(KEY, LIMIT, WINDOW, half);
            assertThat(r).as("half-window acquire #%d (should be allowed)", i + 1).isEqualTo(1L);
        }
        // 同一半 window 时刻再请求：桶空（剩下的 fractional token 不足 1）
        assertThat(script.execute(KEY, LIMIT, WINDOW, half)).isZero();
    }
}
