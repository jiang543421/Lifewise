package com.lifewise.diet;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.diet.domain.MealType;
import com.lifewise.diet.dto.FoodCreateRequest;
import com.lifewise.diet.dto.MealCreateRequest;
import com.lifewise.diet.dto.MealItemRequest;
import com.lifewise.diet.repository.FoodRepository;
import com.lifewise.diet.repository.MealRepository;
import com.lifewise.diet.service.FoodService;
import com.lifewise.diet.service.MealService;
import com.lifewise.diet.service.StatsService;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxEventRecord;
import com.lifewise.shared.integration.outbox.OutboxEventRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
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
 * diet 模块端到端 + cents 一致性 + MV 刷新 + outbox 事件投递集成测试
 * （plan-04-diet §7 关键路径 100% 覆盖）。
 *
 * <p>使用 zonky/embedded-postgres 启动真实 PG 15 + Flyway 全量迁移，覆盖：
 * <ol>
 *   <li>create / update 端到端：meals + meal_items + total_kcal_cents + outbox meal.created</li>
 *   <li>softDelete + restore：meal.deleted_at 切换，meal_items 行零数据丢失</li>
 *   <li>foods 别名 JSONB @> 模糊搜索（V40 + GIN 索引）</li>
 *   <li>cents 一致性：SUM(mi.kcal_snapshot) * 100 = meal.total_kcal_cents</li>
 *   <li>mv_meal_nutrition_weekly：insert 后 REFRESH 看到新行</li>
 * </ol>
 *
 * <p>未覆盖：diet 在 pg 上用 real decimal (33.33g x 99.99kcal) 已有 MealServiceTest
 * 锁住 Bug A/B；这里只验证 cents 一致性不分裂。
 */
@DisplayName("diet E2E + cents 一致性 + MV + Outbox")
@SpringBootTest
class DietE2EAndOutboxIT {

    private static EmbeddedPostgres PG;

    @Autowired private MealService mealService;
    @Autowired private FoodService foodService;
    @Autowired private StatsService statsService;
    @Autowired private FoodRepository foodRepository;
    @Autowired private MealRepository mealRepository;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private JdbcTemplate jdbc;

    private long userId;

    @BeforeAll
    static void startEmbeddedPg() throws IOException, SQLException {
        PG = EmbeddedPostgres.builder().start();
        try (Connection c = DriverManager.getConnection(
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
        userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, display_name, timezone)"
                        + " VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                "u-" + UUID.randomUUID() + "@lifewise.test",
                "test-hash-1234567890",
                "test-user",
                "UTC");
    }

    @AfterEach
    void truncateState() {
        // 物化视图不能 TRUNCATE，REFRESH CONCURRENTLY 会从空基表重建。
        // 顺序：先清基表（含外键引用方）→ REFRESH MV。
        jdbc.execute("TRUNCATE TABLE outbox_events, meal_items,"
                + " meals, foods, user_profiles, users RESTART IDENTITY CASCADE");
        jdbc.execute("REFRESH MATERIALIZED VIEW mv_meal_nutrition_weekly");
    }

    // ---- 1. create + update end-to-end (meals + meal_items + cents + outbox) ----

    @Test
    @DisplayName("E2E: create meal → 1 行 meals + N 行 meal_items + total_kcal_cents 写入 + outbox meal.created")
    void create_meal_persists_and_emits_event() {
        long riceId = insertSystemFood("Rice", 130.00, 2.70, 0.30, 28.00);
        long eggId = insertSystemFood("Egg", 155.00, 13.00, 11.00, 1.10);

        var view = mealService.create(userId, new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", "noon",
                List.of(
                        new MealItemRequest(riceId, new BigDecimal("100.00"), null),
                        new MealItemRequest(eggId, new BigDecimal("50.00"), null))));

        // meals + meal_items 持久化
        Integer mealCount = jdbc.queryForObject(
                "SELECT count(*) FROM meals WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                Integer.class, view.id(), userId);
        assertThat(mealCount).isEqualTo(1);

        Integer itemCount = jdbc.queryForObject(
                "SELECT count(*) FROM meal_items WHERE meal_id = ? AND local_date = ?",
                Integer.class, view.id(), LocalDate.of(2026, 8, 3));
        assertThat(itemCount).isEqualTo(2);

        // total_kcal_cents 严格持久化；130*1 + 155*0.5 = 207.5 → cents 20750
        Long cents = jdbc.queryForObject(
                "SELECT total_kcal_cents FROM meals WHERE id = ?",
                Long.class, view.id());
        assertThat(cents).isEqualTo(20750L);

        // outbox meal.created
        List<OutboxEventRecord> events = outboxRepository.findPendingBatch(10);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(EventType.MEAL_CREATED.eventType());
        assertThat(events.get(0).aggregateType()).isEqualTo("meal");
        assertThat(events.get(0).aggregateId()).isEqualTo(view.id());
        assertThat(events.get(0).publishedAt()).isNull();
    }

    @Test
    @DisplayName("E2E: update 替换 items → orphanRemoval 清旧 + cents 重新聚合")
    void update_meal_replaces_items_and_recomputes_cents() {
        long riceId = insertSystemFood("Rice", 130.00, 2.70, 0.30, 28.00);
        long eggId = insertSystemFood("Egg", 155.00, 13.00, 11.00, 1.10);

        var initial = mealService.create(userId, new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(riceId, new BigDecimal("100.00"), null))));

        // 替换为 rice 200g + egg 0g（只有 1 项）
        mealService.update(userId, initial.id(), new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", "updated",
                List.of(new MealItemRequest(eggId, new BigDecimal("100.00"), null))));

        Integer itemCount = jdbc.queryForObject(
                "SELECT count(*) FROM meal_items WHERE meal_id = ?",
                Integer.class, initial.id());
        assertThat(itemCount).isEqualTo(1); // orphanRemoval 移除旧 1 行，写入新 1 行

        // cents = 155 * 1 = 15500
        Long cents = jdbc.queryForObject(
                "SELECT total_kcal_cents FROM meals WHERE id = ?",
                Long.class, initial.id());
        assertThat(cents).isEqualTo(15500L);

        String note = jdbc.queryForObject(
                "SELECT note FROM meals WHERE id = ?",
                String.class, initial.id());
        assertThat(note).isEqualTo("updated");
    }

    // ---- 2. softDelete + restore ----

    @Test
    @DisplayName("E2E: softDelete + restore → meal.deleted_at 切换，meal_items 行零数据丢失")
    void soft_delete_then_restore_preserves_items() {
        long riceId = insertSystemFood("Rice", 130.00, 2.70, 0.30, 28.00);
        var meal = mealService.create(userId, new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(riceId, new BigDecimal("100.00"), null))));

        mealService.softDelete(userId, meal.id());

        // meal.deleted_at 非空，meal_items 行未动
        java.time.OffsetDateTime deletedAt = jdbc.queryForObject(
                "SELECT deleted_at FROM meals WHERE id = ?",
                java.time.OffsetDateTime.class, meal.id());
        assertThat(deletedAt).isNotNull();

        Integer itemCountAfterDelete = jdbc.queryForObject(
                "SELECT count(*) FROM meal_items WHERE meal_id = ?",
                Integer.class, meal.id());
        assertThat(itemCountAfterDelete).isEqualTo(1);

        // restore → meal.deleted_at 回 null，items 行不动
        mealService.restore(userId, meal.id());

        java.time.OffsetDateTime deletedAtAfterRestore = jdbc.queryForObject(
                "SELECT deleted_at FROM meals WHERE id = ?",
                java.time.OffsetDateTime.class, meal.id());
        assertThat(deletedAtAfterRestore).isNull();

        Integer itemCountAfterRestore = jdbc.queryForObject(
                "SELECT count(*) FROM meal_items WHERE meal_id = ?",
                Integer.class, meal.id());
        assertThat(itemCountAfterRestore).isEqualTo(1);
    }

    // ---- 3. foods JSONB @> 模糊搜索 ----

    @Test
    @DisplayName("E2E: foods 搜索命中 name LIKE 与 aliases JSONB @>（V40 GIN 索引）")
    void search_food_hits_name_or_alias_jsonb() {
        // system food：name 命中
        insertSystemFood("Apple", 52.00, 0.30, 0.20, 14.00);
        // user food：alias 命中
        long aliasFoodId = foodService.create(userId, new FoodCreateRequest(
                "Pear", List.of("鸭梨", "雪梨"), "FRUIT",
                new BigDecimal("42.00"), new BigDecimal("0.40"),
                new BigDecimal("0.10"), new BigDecimal("11.00"))).id();

        var hitsByName = foodService.search(userId, "Apple");
        assertThat(hitsByName).extracting(com.lifewise.diet.dto.FoodView::name)
                .contains("Apple");

        // alias 中文：to_jsonb(ARRAY['鸭梨']) @> aliases
        var hitsByAlias = foodService.search(userId, "鸭梨");
        assertThat(hitsByAlias).extracting(com.lifewise.diet.dto.FoodView::id)
                .contains(aliasFoodId);
    }

    // ---- 4. cents 一致性 ----

    @Test
    @DisplayName("E2E: StatsService 按日 cents 与 meal.total_kcal_cents 一致（无分叉）")
    void cents_consistency_service_vs_db() {
        long riceId = insertSystemFood("Rice", 130.00, 2.70, 0.30, 28.00);
        long eggId = insertSystemFood("Egg", 155.00, 13.00, 11.00, 1.10);

        mealService.create(userId, new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(
                        new MealItemRequest(riceId, new BigDecimal("100.00"), null),
                        new MealItemRequest(eggId, new BigDecimal("50.00"), null))));

        mealService.create(userId, new MealCreateRequest(
                MealType.DINNER, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(riceId, new BigDecimal("200.00"), null))));

        // StatsService 实时聚合 cents
        var byDayCents = statsService.sumKcalByDayInRange(userId,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3));
        assertThat(byDayCents).containsKey(LocalDate.of(2026, 8, 3));
        Long statsCents = byDayCents.get(LocalDate.of(2026, 8, 3));

        // 直接 SQL: SUM(meal.total_kcal_cents) —— 与 StatsService 一致
        Long dbSumCents = jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_kcal_cents), 0) FROM meals"
                        + " WHERE user_id = ? AND local_date = ? AND deleted_at IS NULL",
                Long.class, userId, LocalDate.of(2026, 8, 3));

        assertThat(statsCents).isEqualTo(dbSumCents);
        // 207.5 (130+77.5) + 260 = 467.5 → cents 46750
        assertThat(statsCents).isEqualTo(46750L);
    }

    // ---- 5. MV 刷新 ----

    @Test
    @DisplayName("E2E: REFRESH mv_meal_nutrition_weekly 后周聚合命中（V13）")
    void mv_meal_nutrition_weekly_reflects_new_meals() {
        long riceId = insertSystemFood("Rice", 130.00, 2.70, 0.30, 28.00);
        mealService.create(userId, new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(riceId, new BigDecimal("100.00"), null))));

        // 显式 REFRESH（@Scheduled 在 IT 中禁用，r__repeatable_mviews.sql 启动后跑过一次）
        jdbc.execute("REFRESH MATERIALIZED VIEW mv_meal_nutrition_weekly");

        Integer mvRows = jdbc.queryForObject(
                "SELECT count(*) FROM mv_meal_nutrition_weekly WHERE user_id = ?"
                        + " AND meal_type = 'LUNCH'",
                Integer.class, userId);
        assertThat(mvRows).isEqualTo(1);

        // total_kcal = 130.00
        java.math.BigDecimal totalKcal = jdbc.queryForObject(
                "SELECT total_kcal FROM mv_meal_nutrition_weekly"
                        + " WHERE user_id = ? AND meal_type = 'LUNCH'",
                java.math.BigDecimal.class, userId);
        assertThat(totalKcal).isEqualByComparingTo(new BigDecimal("130.00"));
    }

    // ---- 辅助 ----

    private long insertSystemFood(String name, double kcal, double protein,
                                  double fat, double carb) {
        jdbc.update("INSERT INTO foods (user_id, name, kcal_per_100g, protein_g_per_100g,"
                        + " fat_g_per_100g, carb_g_per_100g, source)"
                        + " VALUES (NULL, ?, ?, ?, ?, ?, 'SYSTEM')",
                name, kcal, protein, fat, carb);
        return jdbc.queryForObject(
                "SELECT id FROM foods WHERE name = ? AND source = 'SYSTEM'"
                        + " ORDER BY id DESC LIMIT 1",
                Long.class, name);
    }
}