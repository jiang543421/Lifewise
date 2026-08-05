package com.lifewise.ai.service.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.ai.service.exception.OllamaUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OllamaClient 单元测试（plan-06-ai §7.5）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>endpoint + body 正确拼接</li>
 *   <li>timeout 抛出 OllamaUnavailableException（job FAILED 触发）</li>
 *   <li>模型未就绪（5xx / connection refused）→ 重试 maxRetries 次</li>
 *   <li>latency_ms 从首次尝试开始累计</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class OllamaClientTest {

    @Mock OllamaHttpClient httpClient;
    OllamaClient client;
    OllamaProperties props;

    // Fixed clock：起始时间 2026-08-05T08:00:00Z，每次前进 N ms
    private AtomicLong nowMillis;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        props = new OllamaProperties();
        props.setEndpoint("http://ai:11434");
        props.setModel("deepseek:8b");
        props.setTimeoutMs(30_000L);
        props.setMaxRetries(3);
        props.setBaseBackoffMs(1L); // 缩短 backoff 让测试快

        nowMillis = new AtomicLong(Instant.parse("2026-08-05T08:00:00Z").toEpochMilli());
        fixedClock = new Clock() {
            @Override public Instant instant() { return Instant.ofEpochMilli(nowMillis.get()); }
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            public long millis() { return nowMillis.get(); }
        };

        client = new OllamaClient(props, httpClient, new ObjectMapper(), fixedClock);
    }

    @Test
    @DisplayName("calls the local Ollama endpoint with model + prompt + stream=false")
    void generate_callsLocalEndpoint() {
        when(httpClient.post(anyString(), anyString(), any(Duration.class)))
                .thenReturn("{\"response\":\"hello world\",\"eval_count\":42,\"done\":true}");

        GenerationResult r = client.generate("summarize my day");

        assertThat(r.content()).isEqualTo("hello world");
        assertThat(r.tokensUsed()).isEqualTo(42L);

        // 关键：URL 必须是 plan §2.3 规定的本地 endpoint + /api/generate
        org.mockito.ArgumentCaptor<String> urlCap = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> bodyCap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(urlCap.capture(), bodyCap.capture(), eq(props.timeout()));
        assertThat(urlCap.getValue()).isEqualTo("http://ai:11434/api/generate");
        assertThat(bodyCap.getValue()).contains("\"model\":\"deepseek:8b\"");
        assertThat(bodyCap.getValue()).contains("\"prompt\":\"summarize my day\"");
        assertThat(bodyCap.getValue()).contains("\"stream\":false");
    }

    @Test
    @DisplayName("throws OllamaUnavailableException after timeout on every attempt")
    void generate_timeoutExhausted_throws() {
        // 每次都超时
        when(httpClient.post(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new OllamaHttpClient.OllamaHttpException("read timeout"));

        assertThatThrownBy(() -> client.generate("p"))
                .isInstanceOf(OllamaUnavailableException.class)
                .hasMessageContaining("exhausted");

        // 应尝试 maxRetries + 1 = 4 次
        verify(httpClient, times(4)).post(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("retries up to maxRetries+1 attempts on transient model-unavailable")
    void generate_modelUnavailable_retriesThenSucceeds() {
        // 前 3 次失败（deepseek:8b 还在加载），第 4 次成功
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger(0);
        when(httpClient.post(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    int n = calls.incrementAndGet();
                    if (n <= 3) {
                        throw new OllamaHttpClient.OllamaHttpException("model not ready");
                    }
                    return "{\"response\":\"ok\",\"eval_count\":10}";
                });

        GenerationResult r = client.generate("p");

        assertThat(r.content()).isEqualTo("ok");
        // 4 次（3 fail + 1 success）
        verify(httpClient, times(4)).post(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("captures latency_ms from first attempt to final response")
    void generate_capturesLatency() {
        // 模拟 3 次尝试，每次推进 100ms
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger(0);
        when(httpClient.post(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    int k = n.incrementAndGet();
                    nowMillis.addAndGet(100L);
                    if (k < 3) throw new OllamaHttpClient.OllamaHttpException("transient");
                    return "{\"response\":\"ok\",\"eval_count\":5}";
                });

        GenerationResult r = client.generate("p");

        // 从首次调用开始累计：3 次 * 100ms = 300ms
        assertThat(r.latencyMs()).isEqualTo(300L);
    }
}