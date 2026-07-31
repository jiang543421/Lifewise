package com.lifewise.shared.integration.outbox;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 轮询 + 派发 Worker（plan-shared-integration §3.3 path B 修订）。
 *
 * <p>v1.0 path B：
 * <ul>
 *   <li>不再调用 {@code DeadLetterService}（DB 无 outbox_dead_letter 表）</li>
 *   <li>失败重试次数由内存 {@code Map<Long,Integer> attempts} 维护（进程重启归零，
 *       行保持 PENDING，由 admin 通过 SQL 手动 {@code UPDATE outbox_events SET
 *       published_at = now()} 介入）</li>
 *   <li>达到 {@code maxRetries} 上限后只记 ERROR 日志 + 跳过该事件；不再搬运</li>
 * </ul>
 *
 * <p>单轮行为：
 * <ol>
 *   <li>从仓库拉取 PENDING 批次（默认 50 条）</li>
 *   <li>逐条调用 {@link OutboxDispatcher#dispatch(OutboxEventRecord)}</li>
 *   <li>成功 → markDispatched</li>
 *   <li>失败 → attempts++; 若新 attempts &gt;= maxRetries → log ERROR + 跳过；否则继续 PENDING</li>
 *   <li>单条失败不影响后续事件处理</li>
 * </ol>
 *
 * <p>调度：{@code @Scheduled(fixedDelayString = "${outbox.poll.ms:1000}")}；
 * 测试可通过 {@code outbox.scheduler.enabled=false} 关闭（由 {@link ConditionalOnProperty} 控制）。
 */
@Component
@ConditionalOnProperty(
        name = "outbox.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxEventRepository repository;
    private final OutboxDispatcher dispatcher;
    private final WorkerConfig config;
    /** 重试计数（内存态；DB 不持久化）。 */
    private final Map<Long, Integer> attempts = new ConcurrentHashMap<>();

    public OutboxWorker(OutboxEventRepository repository,
                        OutboxDispatcher dispatcher,
                        WorkerConfig config) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.config = config;
    }

    /** 便利构造：使用默认配置（批次 50，最大重试 3）。仅供 OutboxWorkerTest 单测使用，避免 Spring 装配歧义。 */
    public static OutboxWorker withDefaultConfig(OutboxEventRepository repository,
                                                OutboxDispatcher dispatcher) {
        return new OutboxWorker(repository, dispatcher, new WorkerConfig(50, 3));
    }

    /**
     * 定时调度入口（{@code @Scheduled}）。
     * 默认 1s 间隔；可通过 {@code outbox.poll.ms} 覆盖。
     */
    @Scheduled(fixedDelayString = "${outbox.poll.ms:1000}")
    public void scheduledRun() {
        try {
            int n = runOnce();
            if (n > 0) {
                log.debug("OutboxWorker tick processed {} events", n);
            }
        } catch (RuntimeException ex) {
            log.error("OutboxWorker tick failed", ex);
        }
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
            Long id = record.id();
            try {
                dispatcher.dispatch(record);
                repository.markDispatched(id);
                if (id != null) {
                    attempts.remove(id);
                }
                processed++;
            } catch (RuntimeException ex) {
                int newAttempts = (id == null ? 0 : attempts.getOrDefault(id, 0)) + 1;
                if (id != null) {
                    attempts.put(id, newAttempts);
                }
                if (newAttempts >= config.maxRetries()) {
                    log.error(
                            "[outbox] GIVING UP after {} attempts: id={} type={} cause={}",
                            newAttempts, id, record.eventType(), ex.toString());
                } else {
                    log.warn(
                            "[outbox] dispatch failed id={} attempts={} cause={}",
                            id, newAttempts, ex.toString());
                }
                processed++;
            }
        }
        return processed;
    }

    /** 仅测试用：暴露当前内存 attempts 快照。 */
    Map<Long, Integer> attemptsSnapshot() {
        return Map.copyOf(attempts);
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