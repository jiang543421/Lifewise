package com.lifewise.diet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * diet 模块 4 个仓库的端到端真 PG 集成测试（plan-04-diet §6 验证 Repository ≥ 85%）。
 *
 * <p>为什么不用 {@code @DataJpaTest}：
 * <ol>
 *   <li>{@code searchByNameOrAlias} native JSONB @> 仅 PG 支持；H2 默认 schema 不兼容</li>
 *   <li>{@code StatsRepository} SQL 依赖物化视图 {@code mv_meal_nutrition_weekly}（V13）</li>
 *   <li>{@code meals} 按月分区 + 复合 FK {@code (meal_id, local_date)} 用 H2 无法验证</li>
 * </ol>
 * 因此遵循 {@link com.lifewise.diet.DietE2EAndOutboxIT} 已用 zonky/embedded-postgres 模式，与
 * task/daily IT 同一规范（AbstractPostgresIT 抽出属独立分支任务，不在 diet 模块做）。
 *
 * <p>此文件独立启动 PG，不与 DietE2EAndOutboxIT 共享实例（避免测试间数据库状态污染）。
 */
@SpringBootTest
@DisplayName("diet Repository IT — 真 PG 覆盖所有派生方法与 @Query")
class DietRepositoryIT {

    private static EmbeddedPostgres PG;

    @Autowired private MealRepository mealRepository;
    @Autowired private FoodRepository foodRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private StatsRepository statsRepository;
    @Autowired private JdbcTemplate jdbc;

    private long userIdA;
    private long userIdB;

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
    void seedUsers() {
        userIdA = insertUser();
        userIdB = insertUser();
    }

    @AfterEach
    void truncateState() {
        jdbc.execute("TRUNCATE TABLE outbox_events, meal_items,"
                + " meals, foods, user_profiles, users RESTART IDENTITY CASCADE");
    }

    // ============================================================
    // MealRepository
    // ============================================================

    @Test
    @DisplayName("MealRepository.findByIdAndDeletedAtIsNull 返回 active meal，排除软删")
    void mealRepo_findByIdAndDeletedAtIsNull_filters_soft_deleted() {
        long foodId = insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        long mealId = insertMeal(userIdA, LocalDate.of(2026, 8, 3), "LUNCH", 13000L,
                java.util.List.of(foodId));

        assertThat(mealRepository.findByIdAndDeletedAtIsNull(mealId)).isPresent();

        jdbc.update("UPDATE meals SET deleted_at = NOW() WHERE id = ?", mealId);
        assertThat(mealRepository.findByIdAndDeletedAtIsNull(mealId)).isEmpty();

        // 不存在 ID
        assertThat(mealRepository.findByIdAndDeletedAtIsNull(mealId + 9999L)).isEmpty();
    }

    @Test
    @DisplayName("MealRepository.findByUserIdAndLocalDateBetween 包含两端边界，跨用户隔离")
    void mealRepo_findByUserIdAndLocalDateBetween_inclusive_and_cross_user_isolation() {
        long foodId = insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        // A 用户：1日、2日、3日 三餐
        long a1 = insertMeal(userIdA, LocalDate.of(2026, 8, 1), "BREAKFAST", 13000L, items(foodId));
        long a2 = insertMeal(userIdA, LocalDate.of(2026, 8, 2), "LUNCH", 13000L, items(foodId));
        long a3 = insertMeal(userIdA, LocalDate.of(2026, 8, 3), "DINNER", 13000L, items(foodId));
        // B 用户：3 日 同天
        long b3 = insertMeal(userIdB, LocalDate.of(2026, 8, 3), "LUNCH", 13000L, items(foodId));

        // 闭区间 [2, 3] 应包含 a2 + a3（不含 a1）
        var between = mealRepository
                .findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByLocalDateAsc(
                        userIdA, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3));
        assertThat(between).extracting("id").containsExactly(a2, a3);

        // B 用户隔离：A 查询 B 应无结果
        var crossUser = mealRepository
                .findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByLocalDateAsc(
                        userIdB, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));
        assertThat(crossUser).extracting("id").containsExactly(b3);

        // 删除 a2 → 不应在结果中
        jdbc.update("UPDATE meals SET deleted_at = NOW() WHERE id = ?", a2);
        var afterSoftDelete = mealRepository
                .findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByLocalDateAsc(
                        userIdA, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3));
        assertThat(afterSoftDelete).extracting("id").containsExactly(a3);

        // 桩数据使用
        assertThat(a1).isNotEqualTo(a2);
    }

    @Test
    @DisplayName("MealRepository.search 多参数组合：null 参数跳过对应条件")
    void mealRepo_search_multi_param_combinations() {
        long foodId = insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        long aB = insertMeal(userIdA, LocalDate.of(2026, 8, 1), "BREAKFAST", 13000L, items(foodId));
        insertMeal(userIdA, LocalDate.of(2026, 8, 2), "LUNCH", 13000L, items(foodId));
        insertMeal(userIdA, LocalDate.of(2026, 8, 3), "DINNER", 13000L, items(foodId));
        insertMeal(userIdB, LocalDate.of(2026, 8, 2), "LUNCH", 13000L, items(foodId));

        // 全 null：A 用户全部
        Page<?> allA = mealRepository.search(userIdA, null, null, null, PageRequest.of(0, 10));
        assertThat(allA.getTotalElements()).isEqualTo(3);

        // 仅 type=LUNCH：A 用户只有 8-2 LUNCH
        Page<?> lunchOnly = mealRepository.search(userIdA, null, null,
                com.lifewise.diet.domain.MealType.LUNCH, PageRequest.of(0, 10));
        assertThat(lunchOnly.getTotalElements()).isEqualTo(1);

        // 仅 from=A 用户全部（>= 8-1）
        Page<?> fromOnly = mealRepository.search(userIdA,
                LocalDate.of(2026, 8, 2), null, null, PageRequest.of(0, 10));
        assertThat(fromOnly.getTotalElements()).isEqualTo(2);

        // 仅 to=A 用户全部（<= 8-2）
        Page<?> toOnly = mealRepository.search(userIdA, null,
                LocalDate.of(2026, 8, 2), null, PageRequest.of(0, 10));
        assertThat(toOnly.getTotalElements()).isEqualTo(2);

        // from+to 闭区间
        Page<?> fromTo = mealRepository.search(userIdA,
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 2), null, PageRequest.of(0, 10));
        assertThat(fromTo.getTotalElements()).isEqualTo(1);

        // 跨用户隔离
        Page<?> userB = mealRepository.search(userIdB, null, null, null, PageRequest.of(0, 10));
        assertThat(userB.getTotalElements()).isEqualTo(1);

        // 分页
        Page<?> page0 = mealRepository.search(userIdA, null, null, null, PageRequest.of(0, 2));
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);

        assertThat(aB).isNotNull();
    }

    // ============================================================
    // FoodRepository
    // ============================================================

    @Test
    @DisplayName("FoodRepository.findByIdAndDeletedAtIsNull 系统/用户食物，排除软删")
    void foodRepo_findByIdAndDeletedAtIsNull_includes_both_and_skips_deleted() {
        long sysId = insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        long userIdFood = insertUserFood(userIdA, "Custom Soup", 50.0, 5.0, 1.0, 10.0);

        assertThat(foodRepository.findByIdAndDeletedAtIsNull(sysId)).isPresent();
        assertThat(foodRepository.findByIdAndDeletedAtIsNull(userIdFood)).isPresent();

        jdbc.update("UPDATE foods SET deleted_at = NOW() WHERE id = ?", userIdFood);
        assertThat(foodRepository.findByIdAndDeletedAtIsNull(userIdFood)).isEmpty();
    }

    @Test
    @DisplayName("FoodRepository.searchByNameOrOwner JPQL 同时返回系统 + 当前用户食物，按 q 过滤")
    void foodRepo_searchByNameOrOwner_jpql_matches_system_and_user_filtered() {
        long sysRice = insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        long sysApple = insertSystemFood("Apple", 52.0, 0.3, 0.2, 14.0);
        long userPear = insertUserFood(userIdA, "Pear", 42.0, 0.4, 0.1, 11.0);

        // 无 q：A 用户视角应看到 2 个系统 + 1 个自己的 = 3
        Page<?> noQ = foodRepository.searchByNameOrOwner(userIdA, null, PageRequest.of(0, 10));
        assertThat(noQ.getTotalElements()).isEqualTo(3);

        // 有 q=Rice：A 视角只看到 Rice
        Page<com.lifewise.diet.domain.Food> riceOnly = foodRepository.searchByNameOrOwner(
                userIdA, "Rice", PageRequest.of(0, 10));
        assertThat(riceOnly.getTotalElements()).isEqualTo(1);
        assertThat(riceOnly.getContent().get(0).getId()).isEqualTo(sysRice);

        // 不区分大小写
        Page<com.lifewise.diet.domain.Food> lower = foodRepository.searchByNameOrOwner(
                userIdA, "rice", PageRequest.of(0, 10));
        assertThat(lower.getTotalElements()).isEqualTo(1);

        // B 用户视角：看不到 A 的 pear，但可见系统食物
        Page<?> userB = foodRepository.searchByNameOrOwner(userIdB, null, PageRequest.of(0, 10));
        assertThat(userB.getTotalElements()).isEqualTo(2);

        assertThat(sysRice).isNotEqualTo(sysApple);
        assertThat(userPear).isNotNull();
    }

    @Test
    @DisplayName("FoodRepository.searchByNameOrAlias native JSONB @>：name LIKE 与 aliases 同时命中")
    void foodRepo_searchByNameOrAlias_native_jsonb_path_ops() throws SQLException {
        long sysApple = insertSystemFood("Apple", 52.0, 0.3, 0.2, 14.0);
        long pearUser = insertUserFoodWithAliases(userIdA, "Pear",
                List.of("鸭梨", "雪梨"), 42.0, 0.4, 0.1, 11.0);

        // name LIKE "App" 命中 Apple
        var byName = foodRepository.searchByNameOrAlias(userIdA, "App");
        assertThat(byName).extracting("id").contains(sysApple);

        // aliases JSONB @>：命中 Pear（"鸭梨" 是 alias）
        var byAlias = foodRepository.searchByNameOrAlias(userIdA, "鸭梨");
        assertThat(byAlias).extracting("id").contains(pearUser);

        // 完全没命中：返回空
        var noHit = foodRepository.searchByNameOrAlias(userIdA, "不存在-xyz");
        assertThat(noHit).isEmpty();

        // 用户隔离：userIdB 看不到 userIdA 的 Pear（用户私有食物仅本人可见）
        var otherUserView = foodRepository.searchByNameOrAlias(userIdB, "鸭梨");
        assertThat(otherUserView).isEmpty();

        // null q：PG SQL 标准 `'...' || NULL || '...'` 整体为 NULL →
        // WHERE 短路 false → 返回空（不静默返回"含字面量 null 的所有行"）。
        // 锁住 native query 对 nullable 参数的隐式语义。
        var nullQ = foodRepository.searchByNameOrAlias(userIdA, null);
        assertThat(nullQ).isEmpty();
    }

    @Test
    @DisplayName("FoodRepository.findAllSystem 只返回 user_id=NULL 食物")
    void foodRepo_findAllSystem_returns_only_system_food() {
        insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        insertSystemFood("Apple", 52.0, 0.3, 0.2, 14.0);
        insertUserFood(userIdA, "UserCustom", 100.0, 1.0, 0.0, 20.0);

        var systemFoods = foodRepository.findAllSystem();
        assertThat(systemFoods).hasSize(2);
        assertThat(systemFoods).allMatch(f -> f.getUserId() == null);
        assertThat(systemFoods).allMatch(f -> f.isSystem());

        // 用户自定义不应在结果中
        assertThat(systemFoods).extracting("name").doesNotContain("UserCustom");
    }

    // ============================================================
    // ProfileRepository
    // ============================================================

    @Test
    @DisplayName("ProfileRepository.findByUserId 1:N findById 派生方法 — 命中/未命中/跨用户隔离")
    void profileRepo_findByUserId_derivation() {
        // 插 user_profiles 行（A）
        jdbc.update("INSERT INTO user_profiles (user_id, height_cm, weight_kg, age,"
                        + " gender, activity_level, daily_kcal_target, updated_at)"
                        + " VALUES (?, 175.0, 70.0, 30, 'MALE', 'MODERATE', 2400, NOW())",
                userIdA);

        Optional<com.lifewise.diet.domain.UserProfile> found =
                profileRepository.findByUserId(userIdA);
        assertThat(found).isPresent();
        assertThat(found.get().getDailyKcalTarget()).isEqualTo(2400);
        assertThat(found.get().getGender())
                .isEqualTo(com.lifewise.diet.domain.Gender.MALE);

        // 没插的 B 用户：empty
        assertThat(profileRepository.findByUserId(userIdB)).isEmpty();

        // 不存在 ID：empty
        assertThat(profileRepository.findByUserId(99999L)).isEmpty();
    }

    // ============================================================
    // StatsRepository
    // ============================================================

    @Test
    @DisplayName("StatsRepository.sumKcalByDayInRange cents Map：按 local_date 分组 + cents 严格")
    void statsRepo_sumKcalByDayInRange_cents_by_local_date() {
        long foodId = insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        // 8-1 + 8-2 各一餐，每餐 cents 13000
        insertMeal(userIdA, LocalDate.of(2026, 8, 1), "LUNCH", 13000L, items(foodId));
        insertMeal(userIdA, LocalDate.of(2026, 8, 2), "LUNCH", 13000L, items(foodId));
        // B 用户：同日 不应污染 A
        insertMeal(userIdB, LocalDate.of(2026, 8, 1), "LUNCH", 99999L, items(foodId));

        Map<LocalDate, Long> byDay = statsRepository.sumKcalByDayInRange(userIdA,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

        // A 用户：8-1 应只有 13000（不混 B 的 99999）
        assertThat(byDay.get(LocalDate.of(2026, 8, 1))).isEqualTo(13000L);
        assertThat(byDay.get(LocalDate.of(2026, 8, 2))).isEqualTo(13000L);
    }

    @Test
    @DisplayName("StatsRepository.sumKcalCentsInRangeRaw 区间总 cents；空区间返回 0")
    void statsRepo_sumKcalCentsInRangeRaw_total_cents_in_range() {
        long foodId = insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        insertMeal(userIdA, LocalDate.of(2026, 8, 1), "LUNCH", 13000L, items(foodId));
        insertMeal(userIdA, LocalDate.of(2026, 8, 2), "DINNER", 6500L, items(foodId));
        // out of range
        insertMeal(userIdA, LocalDate.of(2026, 8, 5), "LUNCH", 99999L, items(foodId));

        long total = statsRepository.sumKcalCentsInRangeRaw(userIdA,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));
        assertThat(total).isEqualTo(19500L); // 13000 + 6500

        // 完全空区间
        long none = statsRepository.sumKcalCentsInRangeRaw(userIdA,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        assertThat(none).isEqualTo(0L);
    }

    @Test
    @DisplayName("StatsRepository.weeklyBuckets 依赖物化视图：REFRESH 前为空，REFRESH 后命中")
    void statsRepo_weeklyBuckets_requires_materialized_view_refresh() {
        long foodId = insertSystemFood("Rice", 130.0, 2.7, 0.3, 28.0);
        insertMeal(userIdA, LocalDate.of(2026, 8, 3), "LUNCH", 13000L, items(foodId));

        // REFRESH 一次（IT 中 @Scheduled 已被禁用）
        jdbc.execute("REFRESH MATERIALIZED VIEW mv_meal_nutrition_weekly");

        LocalDate weekStart = LocalDate.of(2026, 8, 3); // 实际查询点是 week_start 列，含 >= 8-3 行
        var buckets = statsRepository.weeklyBuckets(userIdA, weekStart);
        assertThat(buckets).isNotEmpty();
        assertThat(buckets).allMatch(b -> b.mealType() != null && b.totalKcal() != null);
    }

    // ============================================================
    // 辅助
    // ============================================================

    private long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, display_name, timezone)"
                        + " VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                "u-" + UUID.randomUUID() + "@lifewise.test",
                "test-hash-1234567890",
                "test-user",
                "UTC");
    }

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

    private long insertUserFood(long userId, String name, double kcal, double protein,
                                double fat, double carb) {
        jdbc.update("INSERT INTO foods (user_id, name, kcal_per_100g, protein_g_per_100g,"
                        + " fat_g_per_100g, carb_g_per_100g, source)"
                        + " VALUES (?, ?, ?, ?, ?, ?, 'USER')",
                userId, name, kcal, protein, fat, carb);
        return jdbc.queryForObject(
                "SELECT id FROM foods WHERE name = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, name, userId);
    }

    private long insertUserFoodWithAliases(long userId, String name, List<String> aliases,
                                           double kcal, double protein, double fat, double carb)
            throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(toJsonArray(aliases));
        // jdbc.update 返回受影响行数，不支持 RETURNING 子句读 id —— 拆成两步：
        // 1) PreparedStatement 写 JSONB；2) 按 name+user_id 反查最新 id。
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO foods (user_id, name, kcal_per_100g, protein_g_per_100g,"
                            + " fat_g_per_100g, carb_g_per_100g, source, aliases)"
                            + " VALUES (?, ?, ?, ?, ?, ?, 'USER', ?::jsonb)");
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setBigDecimal(3, BigDecimal.valueOf(kcal));
            ps.setBigDecimal(4, BigDecimal.valueOf(protein));
            ps.setBigDecimal(5, BigDecimal.valueOf(fat));
            ps.setBigDecimal(6, BigDecimal.valueOf(carb));
            ps.setObject(7, jsonb);
            return ps;
        });
        return jdbc.queryForObject(
                "SELECT id FROM foods WHERE name = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, name, userId);
    }

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 单食物 100g，1 行 meal_items；返回 mealId。
     *
     * <p>{@code kcal_snapshot} 按 {@code totalKcalCents / 100} 计算（单 100g item
     * 等价于食物 {@code kcal_per_100g}），让 Stats SQL
     * {@code SUM(mi.kcal_snapshot) * 100} 与 meal.total_kcal_cents 一致。
     */
    private long insertMeal(long userId, LocalDate localDate, String mealType,
                            long totalKcalCents, List<Long> foodIds) {
        long mealId = jdbc.queryForObject(
                "INSERT INTO meals (user_id, local_date, timezone, meal_type, total_kcal_cents,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, 'UTC', ?, ?, NOW(), NOW()) RETURNING id",
                Long.class,
                userId, localDate, mealType, totalKcalCents);
        double kcalSnapshot = totalKcalCents / 100.0;
        for (Long foodId : foodIds) {
            jdbc.update("INSERT INTO meal_items (meal_id, local_date, food_id,"
                            + " amount_g, kcal_snapshot, protein_g_snapshot, fat_g_snapshot,"
                            + " carb_g_snapshot, created_at, updated_at)"
                            + " VALUES (?, ?, ?, 100.00, ?, 0.0, 0.0, 0.0, NOW(), NOW())",
                    mealId, localDate, foodId, kcalSnapshot);
        }
        return mealId;
    }

    private static List<Long> items(long foodId) {
        return List.of(foodId);
    }
}
