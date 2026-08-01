package com.lifewise.shared.integration.outbox.persistence;

import com.lifewise.shared.integration.outbox.OutboxEventRecord;
import com.lifewise.shared.integration.outbox.OutboxEventRepository;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Outbox 仓库 JPA 实现（plan-shared-integration §3.3 path B 修订 + data-model-v1.2 §3.32 V30 + §3.35 V33）。
 *
 * <p>使用 {@link NamedParameterJdbcTemplate} 直接走 JDBC，避开 JPA composite-PK
 * 映射复杂度（{@code outbox_events} 主键是 {@code (id, occurred_at)} 联合主键，V2 分区设计）。
 *
 * <p>JSONB 列写入使用 SQL 内 {@code CAST(? AS jsonb)} 显式类型转换，
 * 由 PG JDBC 驱动完成 JSON 字符串 → JSONB 二进制转换。
 *
 * <p>ID 由 {@link GeneratedKeyHolder} 回填；INSERT 后返回的 record 携带 {@code id}，
 * 用于 {@link com.lifewise.shared.integration.outbox.OutboxWorker} 内存重试计数 key。
 */
@Repository
public class JpaOutboxEventRepository implements OutboxEventRepository {

    private static final String COL_ID = "id";
    private static final String COL_EVENT_TYPE = "event_type";
    private static final String COL_EVENT_VERSION = "event_version";
    private static final String COL_OCCURRED_AT = "occurred_at";
    private static final String COL_USER_ID = "user_id";
    private static final String COL_AGGREGATE_TYPE = "aggregate_type";
    private static final String COL_AGGREGATE_ID = "aggregate_id";
    private static final String COL_CORRELATION_ID = "correlation_id";
    private static final String COL_TRACE_ID = "trace_id";
    private static final String COL_PAYLOAD = "payload";
    private static final String COL_PUBLISHED_AT = "published_at";

    private static final String SELECT_COLUMNS =
            "id, event_type, event_version, occurred_at, user_id, aggregate_type, aggregate_id,"
                    + " correlation_id, trace_id, payload, published_at";

    private static final String INSERT_SQL = """
            INSERT INTO outbox_events (
                occurred_at, user_id, aggregate_type, aggregate_id,
                correlation_id, trace_id, payload, event_type, event_version, published_at
            ) VALUES (
                :occurred_at, :user_id, :aggregate_type, :aggregate_id,
                :correlation_id, :trace_id, CAST(:payload AS jsonb), :event_type, :event_version, :published_at
            )
            """;

    private static final String SELECT_BY_ID_SQL =
            "SELECT " + SELECT_COLUMNS + " FROM outbox_events WHERE id = :id";

    private static final String SELECT_PENDING_SQL =
            "SELECT " + SELECT_COLUMNS
                    + " FROM outbox_events WHERE published_at IS NULL"
                    + " ORDER BY occurred_at ASC, id ASC LIMIT :limit";

    private static final String MARK_DISPATCHED_SQL =
            "UPDATE outbox_events SET published_at = :now WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbc;

    public JpaOutboxEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OutboxEventRecord save(OutboxEventRecord record) {
        if (record.id() != null) {
            throw new UnsupportedOperationException(
                    "Outbox v1.0 path B: update by id not supported; INSERT-only path. id="
                            + record.id());
        }
        KeyHolder kh = new GeneratedKeyHolder();
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue(COL_OCCURRED_AT, toTimestamp(record.occurredAt()))
                .addValue(COL_USER_ID, record.userId())
                .addValue(COL_AGGREGATE_TYPE, record.aggregateType())
                .addValue(COL_AGGREGATE_ID, record.aggregateId())
                .addValue(COL_CORRELATION_ID, record.correlationId())
                .addValue(COL_TRACE_ID, record.traceId())
                .addValue(COL_PAYLOAD, record.payload() == null ? "{}" : record.payload(), Types.OTHER)
                .addValue(COL_EVENT_TYPE, record.eventType())
                .addValue(COL_EVENT_VERSION, record.eventVersion())
                .addValue(COL_PUBLISHED_AT, toTimestamp(record.publishedAt()));
        jdbc.update(INSERT_SQL, params, kh, new String[] { COL_ID });
        Object rawId = kh.getKeys().get(COL_ID);
        Long id = ((Number) rawId).longValue();
        return withId(record, id);
    }

    @Override
    public Optional<OutboxEventRecord> findById(Long id) {
        List<OutboxEventRecord> rows = jdbc.query(
                SELECT_BY_ID_SQL, Map.of(COL_ID, id), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<OutboxEventRecord> findPendingBatch(int limit) {
        return jdbc.query(
                SELECT_PENDING_SQL, Map.of("limit", limit), ROW_MAPPER);
    }

    @Override
    public void markDispatched(Long id) {
        jdbc.update(MARK_DISPATCHED_SQL, Map.of(
                COL_ID, id,
                "now", OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private static OutboxEventRecord withId(OutboxEventRecord r, Long id) {
        return new OutboxEventRecord(
                id,
                r.eventType(),
                r.eventVersion(),
                r.occurredAt(),
                r.userId(),
                r.aggregateType(),
                r.aggregateId(),
                r.correlationId(),
                r.traceId(),
                r.payload(),
                r.publishedAt(),
                r.attemptCount());
    }

    private static Timestamp toTimestamp(OffsetDateTime t) {
        return t == null ? null : Timestamp.from(t.toInstant());
    }

    private static final RowMapper<OutboxEventRecord> ROW_MAPPER =
            (rs, rowNum) -> new OutboxEventRecord(
                    rs.getLong(COL_ID),
                    rs.getString(COL_EVENT_TYPE),
                    rs.getInt(COL_EVENT_VERSION),
                    toOdt(rs.getTimestamp(COL_OCCURRED_AT)),
                    rs.getLong(COL_USER_ID),
                    rs.getString(COL_AGGREGATE_TYPE),
                    (Long) rs.getObject(COL_AGGREGATE_ID),
                    rs.getString(COL_CORRELATION_ID),
                    rs.getString(COL_TRACE_ID),
                    rs.getString(COL_PAYLOAD),
                    toOdt(rs.getTimestamp(COL_PUBLISHED_AT)),
                    0);

    private static OffsetDateTime toOdt(Timestamp t) {
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }
}