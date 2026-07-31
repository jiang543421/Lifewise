package com.lifewise.shared.integration.outbox;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox 仓库接口（plan-shared-integration §3.3）。
 *
 * <p>解耦业务层与 JPA 实现；具体持久化由
 * {@code com.lifewise.shared.integration.outbox.persistence.JpaOutboxEventRepository} 提供。
 */
public interface OutboxEventRepository {

    void save(OutboxEventRecord record);

    Optional<OutboxEventRecord> findById(UUID eventId);

    List<OutboxEventRecord> findPendingBatch(int limit);

    void markDispatched(UUID eventId);

    void markFailed(UUID eventId, int newRetryCount, OffsetDateTime nextAttemptAt);

    void moveToDeadLetter(UUID eventId);
}
