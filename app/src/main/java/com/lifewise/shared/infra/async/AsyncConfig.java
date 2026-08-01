package com.lifewise.shared.infra.async;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared bounded executor for {@code @Async} tasks fired from audit / rate-limit
 * aspects. {@code CallerRunsPolicy} provides graceful backpressure rather than
 * dropping the audit row.
 *
 * <p>Pool sizing (plan-shared-infra §1 async):
 * <ul>
 *   <li>core 8 — keeps warm threads for typical audit/rate-limit spikes</li>
 *   <li>max 16 — absorbs Ollama call bursts without saturating Tomcat's 200 threads</li>
 *   <li>queue 200 — short buffer; saturation falls through to caller-runs</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final int CORE_POOL_SIZE = 8;
    private static final int MAX_POOL_SIZE = 16;
    private static final int QUEUE_CAPACITY = 200;

    /**
     * No-arg constructor — kept for explicit Spring instantiation. Future Redis
     * audit-sink wiring will introduce a real typed dependency.
     */
    public AsyncConfig() {
    }

    @Bean(name = "sharedInfraExecutor")
    public ThreadPoolTaskExecutor sharedInfraExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("shared-infra-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}