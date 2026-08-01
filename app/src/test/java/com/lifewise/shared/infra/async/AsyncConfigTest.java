package com.lifewise.shared.infra.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("Shared async executor configuration")
class AsyncConfigTest {

    @Test
    @DisplayName("executor uses the plan-defined bounded pool and CallerRuns backpressure")
    void async_should_configure_bounded_executor() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().sharedInfraExecutor();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(8);
            assertThat(executor.getMaxPoolSize()).isEqualTo(16);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(200);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            executor.shutdown();
        }
    }
}