package com.lifewise.ai.service.ollama;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * JDK HttpClient 实现 {@link OllamaHttpClient}（plan-06-ai §2.3；technical-arch §3.8）。
 *
 * <p>v1.0 不引入第三方 HTTP starter（OkHttp / Reactor Netty / Apache HttpClient），
 * 复用 JDK 11+ 的 {@link HttpClient}。{@code HttpClient} 实例是线程安全的，
 * 多个 {@link OllamaClient} 并发触发请求时共享同一个底层连接池。
 *
 * <p>行为契约（与 {@code OllamaClient.generate} 重试逻辑对齐）：
 * <ul>
 *   <li>HTTP 2xx → 返回 response body</li>
 *   <li>HTTP 4xx / 5xx → {@link OllamaHttpException}（body 截断到 200 字符）</li>
 *   <li>{@link IOException}（connection refused / read error）→ {@link OllamaHttpException}</li>
 *   <li>{@link InterruptedException} → 中断当前线程 + {@link OllamaHttpException}</li>
 *   <li>request timeout 由调用方传入的 {@code timeout} 参数控制；
 *       connect timeout 硬编码 5s（Ollama 启动期 connect 不应超过 5s）</li>
 * </ul>
 *
 * <p><b>Future plan</b>：v1.1+ 若引入 metrics / tracing，可在此处 wrap 一个
 * {@code MeterRegistry} 装饰器；当前 v1.0 单机部署不需要。
 */
@Component
public class RestClientOllamaHttpClient implements OllamaHttpClient {

    /** Ollama 连接超时 — 启动期或网络抖动不应超过 5s。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 5xx 错误 body 截断长度 — 日志可读性。 */
    private static final int ERROR_BODY_PREVIEW = 200;

    private final HttpClient http;

    public RestClientOllamaHttpClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public String post(String url, String body, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new OllamaHttpException(
                        "Ollama HTTP " + status + ": "
                                + truncate(response.body(), ERROR_BODY_PREVIEW));
            }
            return response.body();
        } catch (IOException ex) {
            // connection refused / read timeout / DNS 失败 → 重试源
            throw new OllamaHttpException(
                    "Ollama I/O failure: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            // 罕见：job 取消时；恢复中断标志 + 抛异常让 OllamaClient 走 DONE_NO_LLM 分支
            Thread.currentThread().interrupt();
            throw new OllamaHttpException("Ollama call interrupted", ex);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}