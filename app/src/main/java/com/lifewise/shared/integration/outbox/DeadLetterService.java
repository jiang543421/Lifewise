package com.lifewise.shared.integration.outbox;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Outbox 死信转移服务（plan-shared-integration §3.3 + data-model-v1.2 §3.36 V34）。
 *
 * <p>v1.0 边界：死信表仅留痕 + 告警，不自动重投；运维通过 SQL / 控制台手动处理。
 */
@Service
public class DeadLetterService {

    /** 业务约束：3 次失败后进入死信（plan §3.3）。 */
    public static final int MAX_RETRIES = 3;

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final OutboxEventRepository repository;

    public DeadLetterService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    /** 判定事件是否应转入死信。 */
    public boolean shouldDeadLetter(OutboxEventRecord record) {
        return record.retryCount() >= MAX_RETRIES;
    }

    /**
     * 把事件搬入死信表。先按 ID 查最新 retry_count，再判定；防止并发把 retry_count 推过上限时误判。
     */
    public void moveToDeadLetter(UUID eventId) {
        OutboxEventRecord record = repository.findById(eventId).orElse(null);
        if (record == null) {
            log.warn("DeadLetterService.moveToDeadLetter: eventId={} not found, skipping", eventId);
            return;
        }
        if (!shouldDeadLetter(record)) {
            log.debug("DeadLetterService.moveToDeadLetter: eventId={} retry={} below MAX_RETRIES={}, skipping",
                    eventId, record.retryCount(), MAX_RETRIES);
            return;
        }
        repository.moveToDeadLetter(eventId);
        log.warn("Outbox event {} moved to dead letter (retry={}, type={})",
                eventId, record.retryCount(), record.eventType());
    }
}
