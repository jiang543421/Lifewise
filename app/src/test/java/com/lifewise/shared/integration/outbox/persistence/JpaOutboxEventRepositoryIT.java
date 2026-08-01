package com.lifewise.shared.integration.outbox.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.shared.integration.outbox.OutboxEventRecord;
import com.lifewise.shared.integration.outbox.OutboxEventRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * JpaOutboxEventRepository 集成测试（plan-shared-integration §9 #5 + §5.1）。
 *
 * <p>使用 zonky/embedded-postgres 启动真实 PG 15 子进程（与 FlywayMigrationIT 同款），
 * 验证 path B 的 4 个核心 SQL 路径：
 * <ol>
 *   <li>INSERT + JSONB {@code CAST(:p AS jsonb)} + GeneratedKeyHolder 回填 id</li>
 *   <li>{@code SELECT ... WHERE id = ?} 命中/未命中</li>
 *   <li>{@code SELECT ... WHERE published_at IS NULL ORDER BY occurred_at} 批次</li>
 *   <li>{@code UPDATE ... SET published_at = now()} 标记已派发</li>
 * </ol>
 *
 * <p>本地无 Docker daemon；embedded-postgres 子进程 + 测试范围独立 @BeforeAll/@AfterAll，
 * 不与 FlywayMigrationIT 共享 static 状态（JUnit 5 跨 class 隔离更稳）。
 */
@DisplayName("JpaOutboxEventRepository 真实 PG SQL 行为")
@SpringBootTest
class JpaOutboxEventRepositoryIT {

    private static EmbeddedPostgres PG;

    @Autowired private OutboxEventRepository repository;
    @Autowired private JdbcTemplate jdbc;

    private long userId;

    @BeforeAll
    static void startEmbeddedPg() throws IOException, SQLException {
        PG = EmbeddedPostgres.builder().start();
        try (Connection c =
                        DriverManager.getConnection(
                                PG.getJdbcUrl("postgres", "postgres"),
                                "postgres",
                                "postgres");
                Statement s = c.createStatement()) {
            try {
                s.execute("CREATE DATABASE lifewise");
            } catch (SQLException e) {
                if (!e.getMessage().contains("already exists")) throw e;
            }
            try {
                s.execute("CREATE USER lifewise WITH PASSWORD 'lifewise'");
            } catch (SQLException e) {
                if (!e.getMessage().contains("already exists")) throw e;
            }
            s.execute("GRANT ALL PRIVILEGES ON DATABASE lifewise TO lifewise");
        }
    }

    @AfterAll
    static void stopEmbeddedPg() throws IOException {
        if (PG != null) PG.close();
    }

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("lifewise", "lifewise"));
        r.add("spring.datasource.username", () -> "lifewise");
        r.add("spring.datasource.password", () -> "lifewise");
        r.add("spring.flyway.enabled", () -> "true");
        // 关闭 Outbox 调度器，避免与 IT 用例并发争抢
        r.add("outbox.scheduler.enabled", () -> "false");
    }

    /**
     * 每个测试独立 userId（避免 FK 冲突 + 测试间隔离）。random uuid email 同时规避
     * shared-infra 测试清理阶段偶发的 users_email_key 重复键问题。
     */
    @BeforeEach
    void seedUser() {
        userId =
                jdbc.queryForObject(
                        "INSERT INTO users (email, password_hash, display_name, timezone)"
                                + " VALUES (?, ?, ?, ?) RETURNING id",
                        Long.class,
                        "u-" + UUID.randomUUID() + "@lifewise.test",
                        "test-hash-1234567890",
                        "test-user",
                        "UTC");
    }

    /**
     * 清理 outbox_events 残留行——PG V33 含 UNIQUE(aggregate_type, aggregate_id, event_type, occurred_at)，
     * JUnit 5 默认不隔离 @SpringBootTest 单测上下文，行累积导致后续测试撞键。
     */
    @AfterEach
    void truncateOutbox() {
        jdbc.execute("TRUNCATE TABLE outbox_events");
    }

    // -----------------------------------------------------------------
    // 1. INSERT + JSONB CAST + GeneratedKeyHolder
    // -----------------------------------------------------------------

    @Test
    @DisplayName("save → 回填 id + roundtrip 所有字段（包括 JSONB payload）")
    void save_should_populate_id_and_roundtrip_all_columns() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        OutboxEventRecord in =
                new OutboxEventRecord(
                        null,
                        "task.completed",
                        1,
                        now,
                        userId,
                        "task",
                        42L,
                        "corr-abc",
                        "trace-xyz",
                        "{\"taskId\":42,\"title\":\"hello\"}",
                        null,
                        0);

        OutboxEventRecord out = repository.save(in);

        assertThat(out.id()).as("save 必须回填 DB 生成的 id").isNotNull().isPositive();

        // roundtrip
        OutboxEventRecord loaded = repository.findById(out.id()).orElseThrow();
        assertThat(loaded.eventType()).isEqualTo("task.completed");
        assertThat(loaded.eventVersion()).isEqualTo(1);
        assertThat(loaded.occurredAt()).isEqualTo(now);
        assertThat(loaded.userId()).isEqualTo(userId);
        assertThat(loaded.aggregateType()).isEqualTo("task");
        assertThat(loaded.aggregateId()).isEqualTo(42L);
        assertThat(loaded.correlationId()).isEqualTo("corr-abc");
        assertThat(loaded.traceId()).isEqualTo("trace-xyz");
        assertJsonSemanticallyEqual(loaded.payload(), "{\"taskId\":42,\"title\":\"hello\"}");
        assertThat(loaded.publishedAt()).as("新建行 published_at 必须 NULL").isNull();
        // attemptCount 是内存态，DB roundtrip 必为 0
        assertThat(loaded.attemptCount()).isZero();
    }

    @Test
    @DisplayName("save → payload 为 NULL 时写 '{}'（JSONB NOT NULL 兜底）")
    void save_should_default_null_payload_to_empty_json_object() {
        OutboxEventRecord in =
                new OutboxEventRecord(
                        null,
                        "task.completed",
                        1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        userId,
                        "task",
                        1L,
                        null,
                        null,
                        null,
                        null,
                        0);

        OutboxEventRecord out = repository.save(in);

        OutboxEventRecord loaded = repository.findById(out.id()).orElseThrow();
        assertThat(loaded.payload())
                .as("payload=NULL 写入后应回填 '{}'（JSONB NOT NULL 兜底）")
                .isEqualTo("{}");
    }

    @Test
    @DisplayName("save(id != null) → 拒绝 update-by-id（v1.0 path B INSERT-only）")
    void save_should_reject_update_by_id() {
        OutboxEventRecord withId =
                new OutboxEventRecord(
                        9999L,
                        "task.completed",
                        1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        userId,
                        "task",
                        1L,
                        null,
                        null,
                        "{}",
                        null,
                        0);

        assertThatThrownBy(() -> repository.save(withId))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("INSERT-only");
    }

    // -----------------------------------------------------------------
    // 2. findById 命中/未命中
    // -----------------------------------------------------------------

    @Test
    @DisplayName("findById 命中：返回完整 record")
    void findById_should_return_record_when_exists() {
        OutboxEventRecord saved = repository.save(pending("expense.created", 7L));

        OutboxEventRecord found = repository.findById(saved.id()).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.eventType()).isEqualTo("expense.created");
        assertThat(found.aggregateId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("findById 未命中：返回 Optional.empty()")
    void findById_should_return_empty_when_missing() {
        assertThat(repository.findById(99_999L)).isEmpty();
    }

    // -----------------------------------------------------------------
    // 3. findPendingBatch — published_at IS NULL + ORDER BY occurred_at
    // -----------------------------------------------------------------

    @Test
    @DisplayName("findPendingBatch 只返回 published_at IS NULL 行，按 occurred_at 升序")
    void findPendingBatch_should_return_only_unpublished_sorted_by_occurred_at()
            throws InterruptedException {
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

        // 3 行 PENDING，时间错开确保排序生效
        OutboxEventRecord a = repository.save(pendingAt("task.completed", 1L, t0));
        Thread.sleep(10);
        OutboxEventRecord b = repository.save(pendingAt("task.created", 2L, t0.plusSeconds(1)));
        Thread.sleep(10);
        OutboxEventRecord c = repository.save(pendingAt("habit.logged", 3L, t0.plusSeconds(2)));

        // 把 b 标记为 DISPATCHED，应不再出现在 pending batch
        repository.markDispatched(b.id());

        List<OutboxEventRecord> batch = repository.findPendingBatch(50);

        assertThat(batch).extracting(OutboxEventRecord::id).containsExactly(a.id(), c.id());
    }

    @Test
    @DisplayName("findPendingBatch(limit=2) 在 3 个 pending 行时只返回前 2")
    void findPendingBatch_should_respect_limit() throws InterruptedException {
        OffsetDateTime t0 = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        OutboxEventRecord a = repository.save(pendingAt("task.completed", 1L, t0));
        Thread.sleep(10);
        repository.save(pendingAt("task.created", 2L, t0.plusSeconds(1)));
        Thread.sleep(10);
        repository.save(pendingAt("habit.logged", 3L, t0.plusSeconds(2)));

        List<OutboxEventRecord> batch = repository.findPendingBatch(2);

        assertThat(batch).hasSize(2);
        assertThat(batch.get(0).id()).isEqualTo(a.id());
    }

    // -----------------------------------------------------------------
    // 4. markDispatched + JSONB 不变性
    // -----------------------------------------------------------------

    @Test
    @DisplayName("markDispatched 把 published_at 写为 now；后续 pending batch 不再含该行")
    void markDispatched_should_set_published_at_and_remove_from_pending() {
        OutboxEventRecord saved = repository.save(pending("meal.created", 11L));

        assertThat(repository.findPendingBatch(50))
                .as("初始应在 pending batch")
                .extracting(OutboxEventRecord::id)
                .contains(saved.id());

        OffsetDateTime beforeMark = OffsetDateTime.now(ZoneOffset.UTC);
        repository.markDispatched(saved.id());

        OutboxEventRecord reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.publishedAt())
                .as("markDispatched 后 published_at 必须非空")
                .isNotNull();
        assertThat(reloaded.publishedAt())
                .as("published_at 应 ≥ 调用时刻前后的 now")
                .isAfterOrEqualTo(beforeMark.minusSeconds(1));

        assertThat(repository.findPendingBatch(50))
                .as("DISPATCHED 行不应出现在 pending batch")
                .extracting(OutboxEventRecord::id)
                .doesNotContain(saved.id());
    }

    @Test
    @DisplayName("JSONB payload 在写入 / 读出后内容不变（H2 / PG 兼容性对照基线）")
    void payload_jsonb_should_roundtrip_string_content() {
        String complexPayload =
                "{\"a\":1,\"b\":\"hello\",\"c\":[1,2,3],\"d\":{\"nested\":true}}";
        OutboxEventRecord saved =
                repository.save(
                        new OutboxEventRecord(
                                null,
                                "plan.created",
                                1,
                                OffsetDateTime.now(ZoneOffset.UTC),
                                userId,
                                "plan",
                                99L,
                                null,
                                null,
                                complexPayload,
                                null,
                                0));

        OutboxEventRecord loaded = repository.findById(saved.id()).orElseThrow();
        assertJsonSemanticallyEqual(loaded.payload(), complexPayload);
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private OutboxEventRecord pending(String eventType, Long aggregateId) {
        return pendingAt(
                eventType, aggregateId, OffsetDateTime.now(ZoneOffset.UTC).withNano(0));
    }

    private OutboxEventRecord pendingAt(String eventType, Long aggregateId, OffsetDateTime when) {
        return new OutboxEventRecord(
                null,
                eventType,
                1,
                when,
                userId,
                eventType.split("\\.")[0],
                aggregateId,
                null,
                null,
                "{\"k\":\"v\"}",
                null,
                0);
    }

    /**
     * PG {@code CAST(:p AS jsonb)} 二进制存储会重新解析+序列化 JSON 字面量（key 顺序可能重排、
     * 加空格）。H2 直接字符串存储无此行为。生产代码消费者都是 Jackson 反序列化为
     * {@code Map<String,Object>}，因此断言应以「语义相等」而非「字面相等」。
     */
    private static void assertJsonSemanticallyEqual(String actual, String expected) {
        try {
            ObjectMapper m = new ObjectMapper();
            JsonNode a = m.readTree(actual);
            JsonNode e = m.readTree(expected);
            assertThat(a)
                    .as("payload JSON 语义相等（PG JSONB 重排格式不影响）")
                    .isEqualTo(e);
        } catch (Exception ex) {
            throw new AssertionError("payload 不是合法 JSON: " + ex.getMessage(), ex);
        }
    }
}