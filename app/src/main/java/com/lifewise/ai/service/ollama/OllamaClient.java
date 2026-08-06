package com.lifewise.ai.service.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.ai.service.exception.OllamaUnavailableException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ollama deepseek:8b 客户端（plan-06-ai §2.3 + §7.5）。
 *
 * <p>职责：
 * <ul>
 *   <li>调 {@code POST {endpoint}/api/generate}（超时 / 重试 / 退避）</li>
 *   <li>累计 {@code latency_ms}（从首次尝试到最终响应）</li>
 *   <li>解析 {@code response} + {@code eval_count}（tokens_used）</li>
 * </ul>
 *
 * <p>不可用处理：
 * <ol>
 *   <li>网络层异常 → 重试 {@code maxRetries} 次（指数退避：base * 2^attempt）</li>
 *   <li>最终失败 → {@link OllamaUnavailableException}（AiJobService 触发 DONE_NO_LLM）</li>
 * </ol>
 *
 * <p>设计取舍：HTTP 层抽象成 {@link OllamaHttpClient}，便于单元测试 mock
 * 网络行为（timeout / connection refused / 5xx）。
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final OllamaProperties props;
    private final OllamaHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OllamaClient(OllamaProperties props,
                        OllamaHttpClient httpClient,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.props = props;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public GenerationResult generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt required");
        }

        long start = clock.millis();
        String body = buildRequestBody(prompt);

        OllamaHttpClient.OllamaHttpException lastError = null;
        for (int attempt = 0; attempt <= props.getMaxRetries(); attempt++) {
            try {
                String response = httpClient.post(
                        props.getEndpoint() + props.generatePath(),
                        body,
                        props.timeout());
                long latency = clock.millis() - start;
                return parseResponse(response, latency);
            } catch (OllamaHttpClient.OllamaHttpException ex) {
                lastError = ex;
                if (attempt < props.getMaxRetries()) {
                    long backoff = props.getBaseBackoffMs() * (1L << attempt);
                    log.warn("Ollama attempt {}/{} failed: {} — backing off {}ms",
                            attempt + 1, props.getMaxRetries() + 1, ex.getMessage(), backoff);
                    sleep(backoff);
                }
            }
        }

        throw new OllamaUnavailableException(
                "exhausted " + (props.getMaxRetries() + 1) + " attempts", lastError);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new OllamaUnavailableException("interrupted during backoff", ie);
        }
    }

    private String buildRequestBody(String prompt) {
        // Ollama /api/generate 最小请求体；不传 stream（同步等待完整响应）
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "model", props.getModel(),
                    "prompt", prompt,
                    "stream", false));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Ollama request", ex);
        }
    }

    private GenerationResult parseResponse(String json, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String content = root.path("response").asText("");
            long tokens = root.path("eval_count").asLong(0L);
            return new GenerationResult(content, latencyMs, tokens);
        } catch (Exception ex) {
            throw new OllamaUnavailableException("invalid Ollama response JSON", ex);
        }
    }
}