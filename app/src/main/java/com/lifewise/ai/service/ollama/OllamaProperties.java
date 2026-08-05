package com.lifewise.ai.service.ollama;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama 配置（plan-06-ai §2.3；technical-arch §3.8）。
 *
 * <p>从 {@code application.yml} 的 {@code lifewise.ai.ollama.*} 读取：
 * <pre>
 * lifewise:
 *   ai:
 *     ollama:
 *       endpoint: http://ai:11434
 *       model: deepseek:8b
 *       timeout-ms: 30000
 *       max-retries: 3
 *       base-backoff-ms: 500
 * </pre>
 */
@ConfigurationProperties(prefix = "lifewise.ai.ollama")
public class OllamaProperties {

    private String endpoint = "http://ai:11434";
    private String model = "deepseek:8b";
    private long timeoutMs = 30_000L;
    private int maxRetries = 3;
    private long baseBackoffMs = 500L;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getBaseBackoffMs() { return baseBackoffMs; }
    public void setBaseBackoffMs(long baseBackoffMs) { this.baseBackoffMs = baseBackoffMs; }

    public Duration timeout() { return Duration.ofMillis(timeoutMs); }

    public String generatePath() { return "/api/generate"; }
}