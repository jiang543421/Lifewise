package com.lifewise.shared.infra.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable audit row aligned with V26 {@code operation_logs} columns (see
 * data-model-v1.2-amendment.md):
 *
 * <pre>
 *   user_id        -> userId
 *   module         -> module
 *   operation      -> operation
 *   aggregate_type -> resourceType
 *   aggregate_id   -> resourceId
 *   source_ip      -> sourceIp
 *   user_agent     -> userAgent
 *   payload        -> payload (JSONB)
 *   occurred_at    -> occurredAt
 * </pre>
 *
 * <p>Columns {@code status_code / latency_ms / trace_id / request_hash} are
 * deferred to a future V36+ migration; carried in this record so the audit
 * writer can emit them once the schema lands.
 *
 * @param payload immutable view; backing map is wrapped in
 *                {@link Collections#unmodifiableMap(Map)}.
 */
public record AuditEntry(
        Long userId,
        String module,
        String operation,
        String resourceType,
        Long resourceId,
        String sourceIp,
        String userAgent,
        Integer statusCode,
        Long latencyMs,
        String traceId,
        String requestHash,
        Instant occurredAt,
        Map<String, Object> payload) {

    public AuditEntry {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(payload);
    }
}