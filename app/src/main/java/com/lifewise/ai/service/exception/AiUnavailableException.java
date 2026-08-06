package com.lifewise.ai.service.exception;

/** Ollama 不可用 / LLM 通道失败（plan-06-ai §2.3 步骤 3）。 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }
}