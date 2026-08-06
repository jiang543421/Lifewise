package com.lifewise.ai.service.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RestClientOllamaHttpClient} 单元测试（plan-06-ai §2.3）。
 *
 * <p>不依赖 Spring 容器，stub 用 JDK 内置 {@code com.sun.net.httpserver.HttpServer}
 * （无需引入第三方 mock-web-server）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>2xx → 返回 response body</li>
 *   <li>5xx → {@link OllamaHttpException}（错误信息含状态码）</li>
 *   <li>connection refused（端口空闲但无 listener）→ {@link OllamaHttpException}</li>
 * </ol>
 */
@DisplayName("RestClientOllamaHttpClient")
class RestClientOllamaHttpClientTest {

    private HttpServer server;
    private RestClientOllamaHttpClient client;
    private int port;
    private AtomicInteger requestCount;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        requestCount = new AtomicInteger();
        client = new RestClientOllamaHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("returns response body on 2xx")
    void post_returns_body_on_2xx() {
        server.createContext("/api/generate", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = "{\"response\":\"hello\",\"eval_count\":42}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String body = client.post(
                "http://127.0.0.1:" + port + "/api/generate",
                "{\"model\":\"deepseek:8b\"}",
                Duration.ofSeconds(5));

        assertThat(body).isEqualTo("{\"response\":\"hello\",\"eval_count\":42}");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("throws OllamaHttpException with status code on 5xx")
    void post_throws_on_5xx() {
        server.createContext("/api/generate", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = "{\"error\":\"model loading\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() ->
                client.post(
                        "http://127.0.0.1:" + port + "/api/generate",
                        "{}",
                        Duration.ofSeconds(5)))
                .isInstanceOf(OllamaHttpClient.OllamaHttpException.class)
                .hasMessageContaining("503")
                .hasMessageContaining("model loading");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("throws OllamaHttpException on connection refused (no server listening)")
    void post_throws_on_connection_refused() throws IOException {
        // bind socket(0) → close → 端口立即可被新连接 connect 时收到 ECONNREFUSED
        // （127.0.0.1 上 TIME_WAIT 窗口极短，可靠性足够单测场景）
        int deadPort = findFreePort();

        assertThatThrownBy(() ->
                client.post(
                        "http://127.0.0.1:" + deadPort + "/api/generate",
                        "{}",
                        Duration.ofSeconds(5)))
                .isInstanceOf(OllamaHttpClient.OllamaHttpException.class)
                .hasMessageContaining("I/O");
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}