package com.lifewise.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 模块专用异步执行器（plan-06-ai §1 + technical-architecture §3.7）。
 *
 * <p>H4 强制约束：{@code core=2 / max=4 / queue=50 / rejection=CallerRuns}，
 * 与 shared-infra 通用池（core=8/max=16）完全隔离，避免 deepseek:8b OOM。
 *
 * <p>{@link EnableAsync} 必须在模块配置类上声明，否则 {@code @Async("aiJobExecutor")}
 * 静默 fallback 到第一个 {@code TaskExecutor} bean（可能是 shared-infra 的 core=8/max=16
 * 池），破坏 H4 隔离。code-review Finding #5 修复。
 *
 * <p>CLAUDE.md §2.2 单用户串行约束：单机部署，下一个 AI 报告排队等待；
 * CallerRuns 拒绝策略保证任务不丢（同步线程兜底跑）。
 */
@Configuration
@EnableAsync
public class AiAsyncConfig {

    public static final String AI_JOB_EXECUTOR_BEAN = "aiJobExecutor";

    @Bean(name = AI_JOB_EXECUTOR_BEAN)
    public TaskExecutor aiJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-job-");
        // 单用户串行 + CallerRuns → 极端情况下同步线程兜底，调用方线程被占用即阻塞，
        // 触发前端 200 OK 但响应延迟，由前端超时机制兜底（plan §6 步骤 6 SSE）。
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // 等待任务完成（用于优雅停机）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}