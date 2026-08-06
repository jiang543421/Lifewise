package com.lifewise.ai.service.ollama;

import java.time.Duration;

/**
 * Ollama HTTP 客户端抽象（plan-06-ai §2.3；technical-arch §3.8）。
 *
 * <p>封装 REST 调用细节，便于在单元测试中 mock。生产实现可基于
 * {@code java.net.http.HttpClient} 或 Spring {@code RestClient}。
 */
public interface OllamaHttpClient {

    /**
     * 发送 POST 请求到 {@code url}（含 timeout 兜底）。
     *
     * @return HTTP 响应 body（JSON 字符串）
     * @throws OllamaHttpException HTTP / 网络层失败（timeout / connection refused / 5xx）
     */
    String post(String url, String body, Duration timeout);

    /** HTTP 层异常。 */
    class OllamaHttpException extends RuntimeException {
        public OllamaHttpException(String message, Throwable cause) {
            super(message, cause);
        }
        public OllamaHttpException(String message) {
            super(message);
        }
    }
}