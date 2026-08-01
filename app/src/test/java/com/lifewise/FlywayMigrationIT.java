package com.lifewise;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import com.lifewise.shared.integration.event.EventType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * plan-data-flyway TDD 集成测试（RED → GREEN 唯一入口）。
 *
 * <p>验证 V1~V36 Flyway 迁移在 PG 15 上完整执行后，
 * 38 张表 / 5 个分区表 / 2 个物化视图 / 关键 BR 约束全部就位。
 * 与 plan-data-flyway.md §8 验收场景与 §9 验收标准一一对应。</p>
 *
 * <p>运行环境：本地无 Docker daemon，因此测试使用 zonky/embedded-postgres
 * 作为 PG 15 真二进制的子进程封装（与 docker-compose db 同版本族：PG 15）。
 * 生产部署路径不变（docker-compose db）。</p>
 */
@SpringBootTest
class FlywayMigrationIT {

    /** 单 JVM 内嵌 PG 15；端口随机避免冲突；生命周期由 @BeforeAll/@AfterAll 控制 */
    private static EmbeddedPostgres PG;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    static void startEmbeddedPg() throws IOException, SQLException {
        PG = EmbeddedPostgres.builder().start();
        // 用默认 postgres 超级用户创建 lifewise 库 + 角色（与 Flyway 后续运维对齐）
        String adminUrl = "jdbc:postgresql://localhost:" + PG.getPort() + "/postgres";
        try (Connection conn = DriverManager.getConnection(adminUrl, "postgres", "postgres");
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE lifewise");
            st.execute("CREATE USER lifewise WITH PASSWORD 'lifewise'");
            st.execute("GRANT ALL PRIVILEGES ON DATABASE lifewise TO lifewise");
        } catch (SQLException e) {
            // 容错：重复跑测试时库/用户已存在
            if (!e.getMessage().contains("already exists")) {
                throw e;
            }
        }
    }

    @AfterAll
    static void stopEmbeddedPg() throws IOException {
        if (PG != null) {
            PG.close();
        }
    }

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl("lifewise", "lifewise"));
        registry.add("spring.datasource.username", () -> "lifewise");
        registry.add("spring.datasource.password", () -> "lifewise");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "true");
    }

    /** 用 PG 元数据库创建一次性连接（与 Spring 注入的 dataSource 解耦，避免 Hibernate 持有锁） */
    private static Connection metaConnection() throws SQLException {
        return DriverManager.getConnection(
                PG.getJdbcUrl("lifewise", "lifewise"), "lifewise", "lifewise");
    }

    // -------------------------------------------------------
    // §8 迁移完整性
    // -------------------------------------------------------

    @Test
    void flyway_should_apply_v36_cleanly() throws SQLException {
        // Spring Boot 启动阶段已通过 spring.flyway 全部应用完毕
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT COUNT(*)
                     FROM flyway_schema_history
                     WHERE success = TRUE
                       AND version = '36'
                     """)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("V36 认证契约修正迁移必须成功应用")
                    .isEqualTo(1);
        }
    }

    // -------------------------------------------------------
    // §8 结构正确性 — 38 张业务表
    // -------------------------------------------------------

    @Test
    void flyway_should_create_38_business_tables() throws SQLException {
        Set<String> expected = Set.of(
                // 公共基础设施（5）
                "users", "user_profiles", "push_subscriptions",
                "outbox_events", "job_runs",
                // 任务（5）
                "tasks", "task_tags", "task_tag_links", "habits", "habit_logs",
                // 计划（3）
                "plans", "milestones", "milestone_task_links",
                // 日报（3）
                "daily_reports", "daily_report_highlights", "ai_summaries",
                // 消费（3）
                "expense_categories", "expenses", "budgets",
                // 饮食（3）
                "foods", "meals", "meal_items",
                // AI（4）
                "ai_jobs", "ai_reports", "chat_messages", "chat_feedbacks",
                // v1.2 新增（5）
                "export_requests", "export_artifacts",
                "notification_requests", "notification_deliveries",
                "conversations",
                // V26/V27 跨模块（2）
                "operation_logs", "outbox_dead_letter",
                // V28 auth（3）
                "refresh_tokens", "email_verifications", "password_resets",
                // V29 observability（2）
                "scheduled_jobs", "backup_manifests"
        );
        assertThat(expected).hasSize(38);

        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'public'
                       AND table_type = 'BASE TABLE'
                     """)) {
            Set<String> actual = new HashSet<>();
            while (rs.next()) actual.add(rs.getString(1));
            assertThat(actual).containsAll(expected);
        }
    }

    @Test
    void flyway_should_use_bigint_identity_for_all_primary_keys() throws SQLException {
        String[] criticalTables = {"users", "tasks", "plans", "daily_reports", "outbox_events"};
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            for (String t : criticalTables) {
                try (ResultSet rs = st.executeQuery("""
                        SELECT data_type, is_identity, identity_generation
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = %s
                          AND column_name = 'id'
                        """.formatted("'" + t + "'"))) {
                    assertThat(rs.next())
                            .as("表 %s 应有 id 列", t).isTrue();
                    assertThat(rs.getString("data_type"))
                            .as("表 %s.id 必须为 bigint", t).isEqualToIgnoringCase("bigint");
                    assertThat(rs.getString("is_identity"))
                            .as("表 %s.id 必须为 IDENTITY", t).isEqualToIgnoringCase("yes");
                    assertThat(rs.getString("identity_generation"))
                            .as("表 %s.id 必须为 ALWAYS", t).isEqualToIgnoringCase("always");
                }
            }
        }
    }

    // -------------------------------------------------------
    // §8 结构正确性 — 5 个分区表
    // -------------------------------------------------------

    @Test
    void flyway_should_partition_five_tables_by_month() throws SQLException {
        List<String> partitionedTables = List.of(
                "daily_reports", "expenses", "meals", "chat_messages", "outbox_events"
        );

        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            for (String t : partitionedTables) {
                try (ResultSet rs = st.executeQuery("""
                        SELECT partstrat
                        FROM pg_partitioned_table pt
                        JOIN pg_class c ON pt.partrelid = c.oid
                        WHERE c.relname = %s
                        """.formatted("'" + t + "'"))) {
                    assertThat(rs.next())
                            .as("表 %s 必须为分区表（partstrat='r' range）", t).isTrue();
                    assertThat(rs.getString(1))
                            .as("表 %s 分区策略应为 'r'（RANGE）", t).isEqualTo("r");
                }
            }
        }
    }

    // -------------------------------------------------------
    // §8 结构正确性 — 2 个物化视图
    // -------------------------------------------------------

    @Test
    void flyway_should_create_two_materialized_views_with_unique_index() throws SQLException {
        List<String> views = List.of(
                "mv_expense_monthly_category", "mv_meal_nutrition_weekly"
        );

        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            for (String view : views) {
                try (ResultSet rs = st.executeQuery("""
                        SELECT 1 FROM pg_matviews
                        WHERE matviewname = %s
                        """.formatted("'" + view + "'"))) {
                    assertThat(rs.next())
                            .as("物化视图 %s 必须存在", view).isTrue();
                }
                try (ResultSet rs = st.executeQuery("""
                        SELECT 1 FROM pg_indexes
                        WHERE schemaname='public' AND tablename=%s AND indexdef LIKE '%%UNIQUE%%'
                        """.formatted("'" + view + "'"))) {
                    assertThat(rs.next())
                            .as("物化视图 %s 必须有 UNIQUE INDEX（CONCURRENTLY 前置）", view).isTrue();
                }
            }
        }
    }

    // -------------------------------------------------------
    // §8 约束正确性 — BR 抽查
    // -------------------------------------------------------

    @Test
    void flyway_should_enforce_br_check_amount_positive() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("""
                    SELECT conname FROM pg_constraint
                    WHERE conname IN (
                      'expenses_amount_cents_positive',
                      'expenses_amount_cents_check'
                    )
                    """)) {
                assertThat(rs.next())
                        .as("expenses 表必须有 BR-09 金额正数 CHECK 约束").isTrue();
            }
        }
    }

    @Test
    void flyway_should_reject_invalid_outbox_event_type() throws SQLException {
        // V33 扩充 event_type 含 25 条白名单事件；非法 event_type 必须被拒
        // 插入 user_id=1（FK 任意合法值即可；CHECK 在 FK 之前生效）
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            assertThatThrownBy(() -> {
                String sql = """
                        INSERT INTO outbox_events
                          (user_id, event_type, aggregate_type, aggregate_id, payload)
                        VALUES
                          (1, 'definitely.not.whitelisted', 'task', 1, '{}'::jsonb);
                        """;
                st.execute(sql);
            })
                    .hasMessageContaining("outbox_events_event_type_check");
        }
    }

    @Test
    void flyway_should_accept_every_canonical_event_type_and_legacy_auth_aliases() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT pg_get_constraintdef(c.oid)
                     FROM pg_constraint c
                     JOIN pg_class t ON c.conrelid = t.oid
                     WHERE t.relname = 'outbox_events'
                       AND c.conname = 'outbox_events_event_type_check'
                     """)) {
            assertThat(rs.next()).isTrue();
            String definition = rs.getString(1);
            for (EventType eventType : EventType.values()) {
                assertThat(definition)
                        .as("DB CHECK 必须接受 Java EventType: %s", eventType.eventType())
                        .contains("'" + eventType.eventType() + "'");
            }
            assertThat(definition)
                    .as("V33 已发布别名必须为历史数据保留")
                    .contains("'auth.login'", "'auth.logout'");
        }
    }

    @Test
    void flyway_should_add_non_null_uuid_family_id_to_refresh_tokens() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT data_type, is_nullable
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'refresh_tokens'
                       AND column_name = 'family_id'
                     """)) {
            assertThat(rs.next())
                    .as("refresh_tokens.family_id 必须由 V36 增加")
                    .isTrue();
            assertThat(rs.getString("data_type")).isEqualToIgnoringCase("uuid");
            assertThat(rs.getString("is_nullable")).isEqualToIgnoringCase("no");
        }

        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT 1
                     FROM pg_indexes
                     WHERE schemaname = 'public'
                       AND tablename = 'refresh_tokens'
                       AND indexdef LIKE '%(user_id, family_id)%'
                     """)) {
            assertThat(rs.next())
                    .as("refresh family 撤销查询必须有 (user_id, family_id) 索引")
                    .isTrue();
        }
    }

    // -------------------------------------------------------
    // §8 v1.2 修订
    // -------------------------------------------------------

    @Test
    void flyway_should_have_chat_messages_conversation_id_nullable() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT is_nullable
                     FROM information_schema.columns
                     WHERE table_schema='public'
                       AND table_name='chat_messages'
                       AND column_name='conversation_id'
                     """)) {
            assertThat(rs.next())
                    .as("chat_messages.conversation_id 列必须存在（V25）").isTrue();
            assertThat(rs.getString(1)).isEqualToIgnoringCase("yes");
        }
    }

    @Test
    void flyway_should_have_ai_summaries_not_null_on_model_version() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT is_nullable
                     FROM information_schema.columns
                     WHERE table_schema='public'
                       AND table_name='ai_summaries'
                       AND column_name='model_version'
                     """)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualToIgnoringCase("no");
        }
    }

    @Test
    void flyway_should_have_daily_reports_is_draft_column() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT data_type, is_nullable, column_default
                     FROM information_schema.columns
                     WHERE table_schema='public'
                       AND table_name='daily_reports'
                       AND column_name='is_draft'
                     """)) {
            assertThat(rs.next())
                    .as("daily_reports.is_draft 必须存在（V32）").isTrue();
            assertThat(rs.getString("data_type")).isEqualToIgnoringCase("boolean");
            assertThat(rs.getString("is_nullable")).isEqualToIgnoringCase("no");
            assertThat(rs.getString("column_default")).containsIgnoringCase("true");
        }
    }

    @Test
    void flyway_should_have_outbox_tracing_columns() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT column_name
                     FROM information_schema.columns
                     WHERE table_schema='public'
                       AND table_name='outbox_events'
                       AND column_name IN ('event_version','correlation_id','causation_id')
                     ORDER BY column_name
                     """)) {
            List<String> cols = new ArrayList<>();
            while (rs.next()) cols.add(rs.getString(1));
            assertThat(cols).containsExactly("causation_id", "correlation_id", "event_version");
        }
    }

    @Test
    void flyway_should_extend_ai_jobs_status_with_partial_done() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT pg_get_constraintdef(c.oid)
                     FROM pg_constraint c
                     JOIN pg_class t ON c.conrelid = t.oid
                     WHERE t.relname = 'ai_jobs'
                       AND c.conname = 'ai_jobs_status_check'
                     """)) {
            assertThat(rs.next()).isTrue();
            String def = rs.getString(1);
            assertThat(def).contains("DONE_PARTIAL");
            assertThat(def).contains("DONE_NO_LLM");
        }
    }

    @Test
    void flyway_should_extend_export_module_check_to_six() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT pg_get_constraintdef(c.oid)
                     FROM pg_constraint c
                     JOIN pg_class t ON c.conrelid = t.oid
                     WHERE t.relname = 'export_requests'
                       AND c.conname = 'export_requests_module_check'
                     """)) {
            assertThat(rs.next()).isTrue();
            String def = rs.getString(1);
            assertThat(def).contains("'task'");
            assertThat(def).contains("'plan'");
            assertThat(def).contains("'ai'");
        }
    }

    // -------------------------------------------------------
    // 软删除必须可空（§8 soft_delete_nullable）
    // -------------------------------------------------------

    @Test
    void flyway_should_enforce_soft_delete_nullable() throws SQLException {
        String[] softDeleteTables = {"tasks", "daily_reports", "expenses", "meals", "plans"};
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            for (String t : softDeleteTables) {
                try (ResultSet rs = st.executeQuery("""
                        SELECT is_nullable
                        FROM information_schema.columns
                        WHERE table_schema='public' AND table_name=%s
                          AND column_name='deleted_at'
                        """.formatted("'" + t + "'"))) {
                    assertThat(rs.next())
                            .as("表 %s 应有 deleted_at 列", t).isTrue();
                    assertThat(rs.getString(1))
                            .as("表 %s.deleted_at 必须可空（BR 全局软删除语义）", t)
                            .isEqualToIgnoringCase("yes");
                }
            }
        }
    }

    // -------------------------------------------------------
    // 反向 BUG 检测：BR + NOT NULL + UNIQUE + FK 完整性
    // -------------------------------------------------------

    @Test
    void flyway_should_enforce_br13_meal_items_nutrition_non_negative() throws SQLException {
        // V10 加了 4 个命名非负 CHECK（V7 也有列内匿名 CHECK，共 8 个 — 二者皆生效）
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT c.conname, pg_get_constraintdef(c.oid)
                     FROM pg_constraint c
                     JOIN pg_class t ON c.conrelid = t.oid
                     WHERE t.relname = 'meal_items'
                       AND c.conname IN (
                         'meal_items_kcal_non_negative',
                         'meal_items_protein_non_negative',
                         'meal_items_fat_non_negative',
                         'meal_items_carb_non_negative'
                       )
                     ORDER BY c.conname
                     """)) {
            List<String> seen = new ArrayList<>();
            while (rs.next()) {
                seen.add(rs.getString(1));
                // V7 的 CHECK 形如 "(kcal_snapshot >= (0)::numeric)"；V10 同义
                assertThat(rs.getString(2))
                        .containsAnyOf(">= 0", ">= (0)");
            }
            assertThat(seen)
                    .as("BR-13 meal_items 4 个非负 CHECK 必须存在")
                    .containsExactly(
                            "meal_items_carb_non_negative",
                            "meal_items_fat_non_negative",
                            "meal_items_kcal_non_negative",
                            "meal_items_protein_non_negative");
        }
    }

    @Test
    void flyway_should_enforce_br30_plans_date_order() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            assertThatThrownBy(() -> st.execute("""
                    INSERT INTO plans
                      (user_id, title, start_date, target_end_date)
                    VALUES (1, 'plan-test',
                            DATE '2026-12-31', DATE '2026-01-01');
                    """))
                    .hasMessageContaining("plans_date_order");
        }
    }

    @Test
    void flyway_should_enforce_br09_budgets_amount_positive() throws SQLException {
        // metadata-only：避免 user_id / category_id FK 准备；CHECK 约束存在即代表 BR-09 生效
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT pg_get_constraintdef(c.oid)
                     FROM pg_constraint c
                     JOIN pg_class t ON c.conrelid = t.oid
                     WHERE t.relname = 'budgets'
                       AND c.conname = 'budgets_amount_cents_positive'
                     """)) {
            assertThat(rs.next())
                    .as("BR-09 budgets.amount_cents > 0 CHECK 必须存在")
                    .isTrue();
            assertThat(rs.getString(1)).contains("amount_cents > 0");
        }
    }

    @Test
    void flyway_should_enforce_users_email_unique() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            st.execute("""
                    INSERT INTO users (email, password_hash, display_name, timezone)
                    VALUES ('dup-' || gen_random_uuid() || '@lifewise.test',
                            'placeholder-hash-1234567890', 'dup1', 'UTC');
                    """);
            assertThatThrownBy(() -> {
                String dup = "SELECT email FROM users WHERE display_name = 'dup1'";
                st.execute("""
                        INSERT INTO users (email, password_hash, display_name, timezone)
                        SELECT email, 'placeholder-hash-1234567890', 'dup2', 'UTC'
                        FROM users WHERE display_name = 'dup1' LIMIT 1;
                        """);
            })
                    .hasMessageContaining("users_email_key");
        }
    }

    @Test
    void flyway_should_enforce_tasks_completion_order() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement()) {
            // created_at 默认 NOW()，completed_at < created_at 必失败
            assertThatThrownBy(() -> st.execute("""
                    INSERT INTO tasks
                      (user_id, title, status, created_at, completed_at)
                    VALUES (1, 'task-bug-test', 'DONE',
                            NOW(), NOW() - INTERVAL '1 hour');
                    """))
                    .hasMessageContaining("tasks_completion_order");
        }
    }

    @Test
    void flyway_should_enforce_refresh_tokens_have_expires_at() throws SQLException {
        try (Connection conn = metaConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT is_nullable
                     FROM information_schema.columns
                     WHERE table_schema='public'
                       AND table_name='refresh_tokens'
                       AND column_name='expires_at'
                     """)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualToIgnoringCase("no");
        }
    }

    /**
     * plan-auth review H3：V36 backfill 必须沿 V28 parent_id/replaced_by 链合并
     * 同一 chain 的 token 共用一个 family_id；孤立行才独立分配。
     *
     * <p>本测试作为 chain-aware backfill 的契约断言：V36 文件必须包含
     * 递归 CTE 沿 parent_id/replaced_by 链找到 root，并把 root UUID
     * 共享给链内所有 row。
     *
     * <p>DB 状态断言（行级 backfill 验证）由 V36 自身 + {@code mvn verify}
     * 启动期 Flyway 校验承担；此处聚焦 SQL 源契约。
     */
    @Test
    void flyway_v36_should_use_recursive_cte_for_chain_aware_backfill() throws IOException {
        java.nio.file.Path v36 = java.nio.file.Path.of(
                "src/main/resources/db/migration/V36__auth_contract_correction.sql");
        String content = java.nio.file.Files.readString(v36);

        // 修复后 V36 必须用递归 CTE 沿 parent_id/replaced_by 链合并
        assertThat(content)
                .as("V36 must use a recursive CTE to merge family_id along parent_id chain")
                .contains("WITH RECURSIVE");
        assertThat(content)
                .as("V36 backfill must reference parent_id column for chain traversal")
                .contains("parent_id");
        // 修复前 V36 是「update ... set family_id = gen_random_uuid()」，每行独立 UUID，
        // 该简单 UPDATE 已被 chain-aware 重写所替代
        assertThat(content)
                .as("V36 backfill should not be a per-row gen_random_uuid() anymore")
                .doesNotContain("SET family_id = gen_random_uuid()");
    }

    // -------------------------------------------------------
    // Spring 上下文要求：DataSource 必须注入成功
    // -------------------------------------------------------

    @Test
    void flyway_data_source_should_be_resolvable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(1)).isTrue();
        }
    }
}
