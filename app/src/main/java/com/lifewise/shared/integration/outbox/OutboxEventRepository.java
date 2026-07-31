package com.lifewise.shared.integration.outbox;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 仓库接口（plan-shared-integration §3.3 path B 修订）。
 *
 * <p>v1.0 修订：移除 {@code moveToDeadLetter} / {@code markFailed} / {@code retryCount} 相关方法。
 * DB 实际列只有 {@code published_at TIMESTAMPTZ NULL}（V2/V30/V33）；重试计数由
 * {@link OutboxWorker} 在内存维护（{@code Map<Long,Integer> attempts}）。
 *
 * <p>ID 类型由 {@link java.util.UUID} 改为 {@code Long}：V30 分区表实际主键是 BIGINT，
 * 写操作由 {@code JpaOutboxEventRepository#save} 通过 {@code GeneratedKeyHolder} 回填。
 */
public interface OutboxEventRepository {

    /**
     * 插入或更新行；若 {@code record.id() == null} 则 INSERT 并回填 id，返回带 id 的新 record。
     */
    OutboxEventRecord save(OutboxEventRecord record);

    Optional<OutboxEventRecord> findById(Long id);

    /** 拉取 {@code published_at IS NULL} 批次，按 occurred_at 升序。 */
    List<OutboxEventRecord> findPendingBatch(int limit);

    /** 把 {@code published_at} 设为 NOW()；被消费后由 Worker 调用。 */
    void markDispatched(Long id);
}