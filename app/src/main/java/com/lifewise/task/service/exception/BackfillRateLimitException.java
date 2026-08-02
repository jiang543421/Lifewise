package com.lifewise.task.service.exception;

/**
 * 习惯补卡被限流：同习惯当日补卡 > 5 次（plan-01-task §5.2）。
 */
public class BackfillRateLimitException extends RuntimeException {
    public BackfillRateLimitException(long habitId) {
        super("backfill rate limit exceeded for habit " + habitId);
    }
}