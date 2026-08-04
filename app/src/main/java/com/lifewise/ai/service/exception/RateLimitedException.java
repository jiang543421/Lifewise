package com.lifewise.ai.service.exception;

/** AI 速率限制触发（plan-06-ai §7.2）。 */
public class RateLimitedException extends RuntimeException {
    private final int retryAfterSeconds;

    public RateLimitedException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}