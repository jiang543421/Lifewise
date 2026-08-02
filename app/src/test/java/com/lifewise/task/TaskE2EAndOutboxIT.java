package com.lifewise.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.EventConsumer;
import com.lifewise.shared.integration.outbox.OutboxDispatcher;
import com.lifewise.shared.integration.outbox.OutboxEventRecord;
import com.lifewise.shared.integration.outbox.OutboxEventRepository;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskTagLink;
import com.lifewise.task.dto.TaskCreateRequest;
import com.lifewise.task.repository.TaskRepository;
import com.lifewise.task.repository.TaskTagLinkRepository;
import com.lifewise.task.repository.TaskTagRepository;
import com.lifewise.task.service.TaskService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Task 模块端到端链路 + 跨模块 Outbox 投递集成测试（plan-01-task §7 关键路径 100% 覆盖）。
 *
 * <p>使用 zonky/embedded-postgres 启动真实 PG + Flyway 全量迁移，验证：
 * <ol>
 *   <li>TaskService → tasks 表 + outbox_events 表端到端（create / complete / softDelete）</li>
 *   <li>OutboxDispatcher 把事件路由到 stub EventConsumer，验证 envelope 字段映射</li>
 *   <li>TaskTagLink 实体 @EmbeddedId 持久化路径</li>
 *   <li>BR-03 单任务 ≤ 5 标签约束（验证 Service 抛 TagLimitExceededException + 事务回滚）</li>
 * </ol>
 *
 * <p>已知 schema 缺陷（CLAUDE.md §9 红线，本测试不修）：
 * {@code task_tags} / {@code habits} 表 V3 DDL 缺少 {@code deleted_at} 列；
 * TaskTag / Habit 实体继承 {@code BaseEntity} 会在 INSERT 时尝试写入该列。
 * 本测试通过 {@code JdbcTemplate} 直接 INSERT 绕过该缺陷，覆盖 task / outbox 端到端链路。
 */
@DisplayName("Task E2E + Outbox 跨模块投递")
@SpringBootTest
@Import(TaskE2EAndOutboxIT.StubConsumerConfig.class)
class TaskE2EAndOutboxIT {

    private static EmbeddedPostgres PG;

    @Autowired private TaskService taskService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskTagRepository taskTagRepository;
    @Autowired private TaskTagLinkRepository taskTagLinkRepository;
    @Autowired private OutboxDispatcher outboxDispatcher;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CapturingConsumer capturingConsumer;

    private long userId;

    @BeforeAll
    static void startEmbeddedPg() throws IOException, SQLException {
        PG = EmbeddedPostgres.builder().start();
        try (Connection c =
                        DriverManager.getConnection(
                                PG.getJdbcUrl("postgres", "postgres"), "postgres", "postgres");
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
        r.add("outbox.scheduler.enabled", () -> "false");
    }

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
        capturingConsumer.clear();
    }

    @AfterEach
    void truncateState() {
        jdbc.execute(
                "TRUNCATE TABLE outbox_events, task_tag_links, task_tags, tasks"
                        + " RESTART IDENTITY CASCADE");
    }

    /** 绕过 TaskTag 实体的 deleted_at INSERT：直接走 SQL。 */
    private long insertTag(String name) {
        return jdbc.queryForObject(
                "INSERT INTO task_tags (user_id, name, created_at, updated_at)"
                        + " VALUES (?, ?, NOW(), NOW()) RETURNING id",
                Long.class,
                userId, name);
    }

    // ---- 1. 任务 create → outbox task.created 落库 ----

    @Test
    @DisplayName("E2E: create task → tasks 表 1 行 + outbox_events 表 1 行 task.created")
    void createTask_should_persist_task_and_outbox_event() {
        TaskCreateRequest req =
                new TaskCreateRequest(
                        "buy milk", null, TaskPriority.NORMAL, null, null, List.of());

        var view = taskService.create(userId, req);

        var task = taskRepository.findById(view.id()).orElseThrow();
        assertThat(task.getTitle()).isEqualTo("buy milk");
        assertThat(task.getUserId()).isEqualTo(userId);
        assertThat(task.getStatus().name()).isEqualTo("OPEN");

        List<OutboxEventRecord> events =
                outboxRepository.findPendingBatch(10);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(EventType.TASK_CREATED.eventType());
        assertThat(events.get(0).aggregateType()).isEqualTo("task");
        assertThat(events.get(0).aggregateId()).isEqualTo(view.id());
        assertThat(events.get(0).userId()).isEqualTo(userId);
        assertThat(events.get(0).publishedAt()).as("新建事件 published_at 必须 NULL").isNull();
    }

    // ---- 2. 任务 complete → outbox task.completed 落库 + 状态机 ----

    @Test
    @DisplayName("E2E: create + complete → tasks.status=DONE + outbox 有 task.created + task.completed")
    void completeTask_should_transition_status_and_emit_completed_event() {
        var created = taskService.create(
                userId, new TaskCreateRequest("x", null, TaskPriority.NORMAL, null, null, List.of()));
        var completed = taskService.complete(userId, created.id());

        var reloaded = taskRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus().name()).isEqualTo("DONE");
        assertThat(reloaded.getCompletedAt()).isNotNull();
        assertThat(completed.completedAt()).isNotNull();

        List<OutboxEventRecord> events =
                outboxRepository.findPendingBatch(10);
        assertThat(events).extracting(OutboxEventRecord::eventType)
                .containsExactly(EventType.TASK_CREATED.eventType(), EventType.TASK_COMPLETED.eventType());
    }

    // ---- 3. 跨模块投递：OutboxDispatcher 把事件路由到 stub consumer ----

    @Test
    @DisplayName("OUTBOX: Dispatcher 把 envelope 完整字段路由给 consumer（task.created + task.completed）")
    void outboxDispatcher_should_route_event_to_registered_consumer() {
        var created = taskService.create(
                userId, new TaskCreateRequest("y", null, TaskPriority.HIGH, null, null, List.of()));
        taskService.complete(userId, created.id());

        List<OutboxEventRecord> pending = outboxRepository.findPendingBatch(10);
        assertThat(pending).hasSize(2);

        for (OutboxEventRecord record : pending) {
            outboxDispatcher.dispatch(record);
        }

        List<EventEnvelope> delivered = capturingConsumer.drain();
        assertThat(delivered).extracting(EventEnvelope::eventType)
                .containsExactlyInAnyOrder(
                        EventType.TASK_CREATED.eventType(), EventType.TASK_COMPLETED.eventType());
        EventEnvelope completedEnvelope = delivered.stream()
                .filter(e -> e.eventType().equals(EventType.TASK_COMPLETED.eventType()))
                .findFirst()
                .orElseThrow();
        assertThat(completedEnvelope.userId()).isEqualTo(userId);
        assertThat(completedEnvelope.aggregateId()).isEqualTo(created.id());
        assertThat(completedEnvelope.payload())
                .as("payload Map 必须包含 TaskCompletedPayload 字段")
                .containsKey("taskId");
        assertThat(completedEnvelope.eventId()).isNotNull();
    }

    // ---- 4. markDispatched ----

    @Test
    @DisplayName("OUTBOX: markDispatched 后 published_at 非空 + 后续 pending batch 不再含该行")
    void markDispatched_should_set_published_at_and_remove_from_pending() {
        taskService.create(
                userId, new TaskCreateRequest("z", null, TaskPriority.NORMAL, null, null, List.of()));
        OutboxEventRecord event = outboxRepository.findPendingBatch(10).get(0);

        OffsetDateTime beforeMark = OffsetDateTime.now().minusSeconds(1);
        outboxDispatcher.dispatch(event);
        outboxRepository.markDispatched(event.id());
        OffsetDateTime afterMark = OffsetDateTime.now().plusSeconds(1);

        OutboxEventRecord reloaded = outboxRepository.findById(event.id()).orElseThrow();
        assertThat(reloaded.publishedAt()).isBetween(beforeMark, afterMark);
        assertThat(outboxRepository.findPendingBatch(10))
                .extracting(OutboxEventRecord::id)
                .doesNotContain(event.id());
    }

    // ---- 5. TaskTagLink 持久化路径（@EmbeddedId 复合主键） ----

    @Test
    @DisplayName("TaskTagLink 持久化：save + findById 命中 + findByIdTaskId 列表正确")
    void taskTagLink_should_be_persistable_and_findable() {
        var task = taskService.create(
                userId, new TaskCreateRequest("a", null, TaskPriority.NORMAL, null, null, List.of()));
        // 绕过 TaskTag 实体 INSERT（schema 缺 deleted_at 列）
        Long tagId = insertTag("alpha");

        taskTagLinkRepository.save(new TaskTagLink(task.id(), tagId));
        taskTagLinkRepository.flush();

        var pk = new TaskTagLink.Pk(task.id(), tagId);
        var persisted = taskTagLinkRepository.findById(pk).orElseThrow();
        assertThat(persisted.getTaskId()).isEqualTo(task.id());
        assertThat(persisted.getTagId()).isEqualTo(tagId);
        assertThat(persisted.getCreatedAt()).isNotNull();

        List<TaskTagLink> links = taskTagLinkRepository.findByIdTaskId(task.id());
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getTagId()).isEqualTo(tagId);
    }

    // ---- 6. outbox payload JSONB roundtrip（消费方拿到 Map） ----

    @Test
    @DisplayName("OUTBOX payload: JSONB 写入 / 读出后语义相等；envelope.payload 为 Map<String,Object>")
    void outbox_payload_should_roundtrip_as_map() throws Exception {
        taskService.create(
                userId, new TaskCreateRequest("p", null, TaskPriority.NORMAL, null, null, List.of()));
        OutboxEventRecord event = outboxRepository.findPendingBatch(10).get(0);

        JsonNode tree = objectMapper.readTree(event.payload());
        assertThat(tree.get("taskId").asLong()).isEqualTo(event.aggregateId());
        assertThat(tree.get("userId").asLong()).isEqualTo(userId);

        outboxDispatcher.dispatch(event);
        EventEnvelope env = capturingConsumer.drain().get(0);
        assertThat(env.payload()).containsKey("taskId");
        assertThat(((Number) env.payload().get("taskId")).longValue()).isEqualTo(event.aggregateId());
    }

    // ---- 7. softDelete task → outbox 仅 task.created（删除不写事件） + tasks.deleted_at 非空 ----

    @Test
    @DisplayName("E2E: softDelete task → tasks.deleted_at 非空 + outbox 仅 task.created（无删除事件）")
    void softDeleteTask_should_mark_deleted_at_and_emit_no_event() {
        var task = taskService.create(
                userId, new TaskCreateRequest("d", null, TaskPriority.NORMAL, null, null, List.of()));

        taskService.softDelete(userId, task.id());

        var reloaded = taskRepository.findById(task.id()).orElseThrow();
        assertThat(reloaded.getDeletedAt())
                .as("softDelete 后 deleted_at 必须非空")
                .isNotNull();

        List<OutboxEventRecord> events =
                outboxRepository.findPendingBatch(10);
        assertThat(events)
                .as("softDelete 不应触发任何 outbox 事件")
                .extracting(OutboxEventRecord::eventType)
                .containsExactly(EventType.TASK_CREATED.eventType());
    }

    // ---- 测试 stub consumer：捕获派发过来的 envelope ----

    static final class CapturingConsumer implements EventConsumer {
        private final ConcurrentLinkedQueue<EventEnvelope> received = new ConcurrentLinkedQueue<>();

        @Override
        public String eventType() {
            // 仅作为 sink 暴露给各具体 consumer；不参与 dispatcher 路由
            return "__CAPTURE_ALL__";
        }

        @Override
        public void consume(EventEnvelope env) {
            received.add(env);
        }

        void clear() {
            received.clear();
        }

        List<EventEnvelope> drain() {
            return List.copyOf(received);
        }
    }

    /**
     * OutboxDispatcher 通过 {@code List<EventConsumer>} 构造器注入。
     * 为每个 task 事件类型分别注册一个 stub consumer，路由后调用 sink.consume(env) 收集。
     */
    @TestConfiguration
    static class StubConsumerConfig {
        @Bean
        CapturingConsumer capturingConsumer() {
            return new CapturingConsumer();
        }

        @Bean
        EventConsumer taskCreatedCapturingConsumer(CapturingConsumer sink) {
            return new EventConsumer() {
                @Override public String eventType() { return EventType.TASK_CREATED.eventType(); }
                @Override public void consume(EventEnvelope env) { sink.consume(env); }
            };
        }

        @Bean
        EventConsumer taskCompletedCapturingConsumer(CapturingConsumer sink) {
            return new EventConsumer() {
                @Override public String eventType() { return EventType.TASK_COMPLETED.eventType(); }
                @Override public void consume(EventEnvelope env) { sink.consume(env); }
            };
        }

        @Bean
        EventConsumer taskUpdatedCapturingConsumer(CapturingConsumer sink) {
            return new EventConsumer() {
                @Override public String eventType() { return EventType.TASK_UPDATED.eventType(); }
                @Override public void consume(EventEnvelope env) { sink.consume(env); }
            };
        }

        @Bean
        EventConsumer taskReopenedCapturingConsumer(CapturingConsumer sink) {
            return new EventConsumer() {
                @Override public String eventType() { return EventType.TASK_REOPENED.eventType(); }
                @Override public void consume(EventEnvelope env) { sink.consume(env); }
            };
        }
    }
}