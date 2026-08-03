package com.lifewise.diet.repository;

import com.lifewise.diet.domain.Meal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 统计 / 物化视图仓库（plan-04-diet §2.3 + §5.4）。
 *
 * <p>物化视图 {@code mv_meal_nutrition_weekly} 由 {@code RefreshMaterializedViewJob}
 * 每日 02:30 REFRESH CONCURRENTLY。
 *
 * <p>此接口只承载声明式原生查询，无派生方法。领域类型取 {@link Meal} 仅为
 * 满足 Spring Data JPA 的 {@code Repository<T, ID>} 协议 —— 实体元数据解析
 * 不会触发任何 JPQL 生成，仅校验 T 是 {@code @Entity}。
 */
public interface StatsRepository extends Repository<Meal, Long> {

    /**
     * 按日聚合 kcal（cents BIGINT）。
     */
    @Query(value = """
            SELECT m.local_date AS day,
                   (COALESCE(SUM(mi.kcal_snapshot), 0) * 100)::BIGINT AS kcal_cents
            FROM meals m
            JOIN meal_items mi ON mi.meal_id = m.id AND mi.local_date = m.local_date
            WHERE m.user_id = :userId
              AND m.deleted_at IS NULL
              AND m.local_date >= :from
              AND m.local_date <= :to
            GROUP BY m.local_date
            ORDER BY m.local_date ASC
            """, nativeQuery = true)
    List<DayKcalRow> sumKcalByDayRaw(@Param("userId") Long userId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** 行 → Map 转换（service 层）。 */
    default Map<LocalDate, Long> sumKcalByDayInRange(Long userId, LocalDate from, LocalDate to) {
        return sumKcalByDayRaw(userId, from, to).stream()
                .collect(java.util.stream.Collectors.toMap(
                        DayKcalRow::getDay, DayKcalRow::getKcalCents));
    }

    /** 区间内总 kcal（cents）。 */
    @Query(value = """
            SELECT (COALESCE(SUM(mi.kcal_snapshot), 0) * 100)::BIGINT AS total
            FROM meals m
            JOIN meal_items mi ON mi.meal_id = m.id AND mi.local_date = m.local_date
            WHERE m.user_id = :userId
              AND m.deleted_at IS NULL
              AND m.local_date >= :from
              AND m.local_date <= :to
            """, nativeQuery = true)
    Long sumKcalCentsInRangeRaw(@Param("userId") Long userId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    default Map<LocalDate, Long> sumKcalCentsByDayInRange(Long userId, LocalDate from, LocalDate to) {
        return sumKcalByDayInRange(userId, from, to);
    }

    /**
     * 从物化视图 {@code mv_meal_nutrition_weekly} 读周聚合。
     */
    @Query(value = """
            SELECT week_start, meal_type, meal_count,
                   total_kcal, total_protein_g, total_carb_g, total_fat_g
            FROM mv_meal_nutrition_weekly
            WHERE user_id = :userId
              AND week_start >= :weekStart
            ORDER BY week_start DESC, meal_type ASC
            """, nativeQuery = true)
    List<WeeklyBucketRaw> weeklyBucketsRaw(@Param("userId") Long userId,
                                           @Param("weekStart") LocalDate weekStart);

    default List<WeeklyBucket> weeklyBuckets(Long userId, LocalDate weekStart) {
        return weeklyBucketsRaw(userId, weekStart).stream()
                .map(r -> new WeeklyBucket(r.getWeekStart(), r.getMealType(), r.getMealCount(),
                        r.getTotalKcal(), r.getTotalProteinG(), r.getTotalCarbG(),
                        r.getTotalFatG()))
                .toList();
    }

    /**
     * 原生 SQL 行：日聚合。
     * <p>Spring Data JPA native query + interface projection 必须用 JavaBean
     * getter 风格（{@code getDay()}），record 风格（{@code day()}）不被支持。
     */
    interface DayKcalRow {
        LocalDate getDay();
        Long getKcalCents();
    }

    /**
     * 原生 SQL 行：物化视图周聚合（同上，必须 JavaBean 风格）。
     */
    interface WeeklyBucketRaw {
        LocalDate getWeekStart();
        String getMealType();
        Long getMealCount();
        java.math.BigDecimal getTotalKcal();
        java.math.BigDecimal getTotalProteinG();
        java.math.BigDecimal getTotalCarbG();
        java.math.BigDecimal getTotalFatG();
    }

    /** 域内 record（service 层返回）。 */
    record WeeklyBucket(LocalDate weekStart, String mealType, Long mealCount,
                        java.math.BigDecimal totalKcal, java.math.BigDecimal totalProteinG,
                        java.math.BigDecimal totalCarbG, java.math.BigDecimal totalFatG) {
    }
}