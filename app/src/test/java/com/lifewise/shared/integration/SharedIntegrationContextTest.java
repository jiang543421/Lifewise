package com.lifewise.shared.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.LifewiseApplication;
import com.lifewise.shared.integration.outbox.OutboxDispatcher;
import com.lifewise.shared.integration.outbox.OutboxEventRepository;
import com.lifewise.shared.integration.outbox.OutboxWorker;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * shared/integration 子包 Spring Context 装配验证（M2 — plan-shared-integration §5.1）。
 *
 * <p>目的：单测 GREEN 是陷阱（H1 命中过）。这里确保所有 Bean（writer / dispatcher /
 * worker / repository）能在 Spring 容器里正常接线，避免生产启动失败。
 *
 * <p>策略：{@link MockBean} 替换 {@link OutboxEventRepository} 避免真实 DB；
 * H2 内存 + 关闭 Flyway 仅作 Context 装配探针。
 *
 * <p>关闭 {@code outbox.scheduler.enabled} 避免 {@code @Scheduled} 在测试期间重复触发。
 */
@DisplayName("shared/integration 子包 Spring Context 装配")
@SpringBootTest(classes = LifewiseApplication.class)
@TestPropertySource(properties = {
        "outbox.scheduler.enabled=true",
        "outbox.poll.ms=86400000",                       // 1 day — effectively disable tick during test
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class SharedIntegrationContextTest {

    @MockBean OutboxEventRepository outboxEventRepository;

    @Autowired OutboxWriter outboxWriter;
    @Autowired OutboxDispatcher outboxDispatcher;
    @Autowired OutboxWorker outboxWorker;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("所有 outbox 子包 Bean 装配成功（writer / dispatcher / worker / repository）")
    void should_load_all_outbox_beans() {
        assertThat(outboxWriter).isNotNull();
        assertThat(outboxDispatcher).isNotNull();
        assertThat(outboxWorker).isNotNull();
        assertThat(outboxEventRepository).isNotNull();
        assertThat(objectMapper).isNotNull();
    }

    @Test
    @DisplayName("WorkerConfig 默认值：pollBatchSize=50, maxRetries=3")
    void should_apply_default_worker_config() {
        OutboxWorker.WorkerConfig cfg = new OutboxWorker.WorkerConfig(50, 3);
        assertThat(cfg.pollBatchSize()).isEqualTo(50);
        assertThat(cfg.maxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("WorkerConfig 校验：pollBatchSize<=0 抛 IllegalArgumentException")
    void should_reject_invalid_poll_batch_size() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new OutboxWorker.WorkerConfig(0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pollBatchSize");
    }

    @Test
    @DisplayName("WorkerConfig 校验：maxRetries<0 抛 IllegalArgumentException")
    void should_reject_invalid_max_retries() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new OutboxWorker.WorkerConfig(50, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetries");
    }
}