package com.lifewise.shared.integration.outbox;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Outbox 轮询 + 派发 Worker（plan-shared-integration §3.3）。
 *
 * <p>单轮行为：
 * <ol>
 *   <li>从仓库拉取 PENDING 批次（默认 50 条）</li>
 *   <li>逐条调用 {@link OutboxDispatcher#dispatch(OutboxEventRecord)}</li>
 *   <li>成功 → markDispatched</li>
 *   <li>失败 → markFailed(retry+1, now + backoff)；如新 retry &gt;= MAX → moveToDeadLetter</li>
 *   <li>单条失败不影响后续事件处理</li>
 * </ol>
 *
 * <p>调度：{@code @Scheduled(fixedDelay = ...)} 由 Spring 触发，本类只暴露
 * {@link #runOnce()}。单元测试直接调用 runOnce 验证行为；集成测试在 Spring Context 中跑定时任务。
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxEventRepository repository;
    private final OutboxDispatcher dispatcher;
    private final DeadLetterService deadLetterService;
    private final WorkerConfig config;

    public OutboxWorker(OutboxEventRepository repository,
                        OutboxDispatcher dispatcher,
                        DeadLetterService deadLetterService,
                        WorkerConfig config) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.deadLetterService = deadLetterService;
        this.config = config;
    }

    /** 便利构造：使用默认配置（批次 50，最大重试 3）。 */
    public OutboxWorker(OutboxEventRepository repository,
                        OutboxDispatcher dispatcher,
                        DeadLetterService deadLetterService) {
        this(repository, dispatcher, deadLetterService,
                new WorkerConfig(50, DeadLetterService.MAX_RETRIES));
    }

    /**
     * @return 实际处理的事件数（含成功 + 失败）
     */
    public int runOnce() {
        List<OutboxEventRecord> batch = repository.findPendingBatch(config.pollBatchSize());
        if (batch.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (OutboxEventRecord record : batch) {
            try {
                dispatcher.dispatch(record);
                repository.markDispatched(record.eventId());
                processed++;
            } catch (RuntimeException ex) {
                int newRetry = record.retryCount() + 1;
                OffsetDateTime nextAttempt = OffsetDateTime.now(ZoneOffset.UTC)
                        .plus(backoffFor(newRetry));
                log.warn("Outbox dispatch failed eventId={} retry={} cause={}",
                        record.eventId(), newRetry, ex.toString());
                repository.markFailed(record.eventId(), newRetry, nextAttempt);
                if (newRetry >= config.maxRetries()) {
                    deadLetterService.moveToDeadLetter(record.eventId());
                }
                processed++;
            }
        }
        return processed;
    }

    /**
     * 指数退避：30s / 2min / 10min（重试 1/2/3+）。
     */
    static Duration backoffFor(int retry) {
        return switch (retry) {
            case 1 -> Duration.ofSeconds(30);
            case 2 -> Duration.ofMinutes(2);
            default -> Duration.ofMinutes(10);
        };
    }

    /** Worker 配置（不可变 record）。 */
    public record WorkerConfig(int pollBatchSize, int maxRetries) {

        public WorkerConfig {
            if (pollBatchSize <= 0) {
                throw new IllegalArgumentException("pollBatchSize must be > 0");
            }
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
        }
    }
}
