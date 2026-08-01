package com.lifewise.shared.infra.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * Functional contract for atomic token-bucket Lua scripts (or a fake in tests).
 *
 * <p>Return value semantics:
 * <ul>
 *   <li>{@code > 0} — request allowed; the value is the remaining capacity</li>
 *   <li>{@code 0} or {@code -1} — request denied</li>
 * </ul>
 * Negative or other errors must surface as {@link RuntimeException}.
 */
@FunctionalInterface
public interface TokenBucketScript {

    long execute(String key, long limit, Duration window, Instant now);
}