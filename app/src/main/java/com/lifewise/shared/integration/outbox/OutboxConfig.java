package com.lifewise.shared.integration.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Outbox 子包 Spring 装配配置（plan-shared-integration §3.3）。
 *
 * <p>把 {@link OutboxWorker.WorkerConfig}（不可变 record）显式注册为 Spring Bean，
 * 避免 {@code OutboxWorker} 构造器因 record 无默认构造而装配失败。
 *
 * <p>默认 {@code pollBatchSize=50, maxRetries=3}；可通过 {@code outbox.worker.poll-batch-size}
 * 与 {@code outbox.worker.max-retries} 覆盖。
 */
@Configuration
public class OutboxConfig {

    @Bean
    public OutboxWorker.WorkerConfig outboxWorkerConfig(
            @Value("${outbox.worker.poll-batch-size:50}") int pollBatchSize,
            @Value("${outbox.worker.max-retries:3}") int maxRetries) {
        return new OutboxWorker.WorkerConfig(pollBatchSize, maxRetries);
    }
}