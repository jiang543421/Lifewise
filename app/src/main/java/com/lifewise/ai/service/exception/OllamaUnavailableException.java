package com.lifewise.ai.service.exception;

/**
 * Ollama / deepseek:8b 不可用（plan-06-ai §2.3 + §7.5）。
 *
 * <p>由 {@code OllamaClient} 在 timeout / connection refused / 模型未就绪
 * 达到最大重试次数后抛出；上层 AiJobService 据此触发 {@code DONE_NO_LLM} 降级
 * 或 {@code FAILED} 终态。
 */
public class OllamaUnavailableException extends RuntimeException {
    public OllamaUnavailableException(String reason) {
        super("Ollama unavailable: " + reason);
    }

    public OllamaUnavailableException(String reason, Throwable cause) {
        super("Ollama unavailable: " + reason, cause);
    }
}