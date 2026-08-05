package com.lifewise.expense.service;

import com.lifewise.expense.dto.StatsView;
import com.lifewise.expense.dto.StatsView.CategoryBucket;
import com.lifewise.expense.dto.StatsView.DayBucket;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消费统计服务（plan-03-expense §1.3 + §5.5）。
 *
 * <p>查询走物化视图 {@code mv_expense_monthly_category}（V12/V37），小时级 REFRESH。
 * 不命中物化视图：按月明细直接 SELECT expenses；按日明细 SELECT expenses；
 * 跨表聚合在 service 层完成。
 *
 * <p>设计取舍：v1.0 优先使用物化视图（小时新鲜度足够 monthly 统计）；UI 实时性
 * 需求通过 {@link com.lifewise.shared.integration.port.ExpenseReadPort#sumInRange}
 * 等即时查询补齐。
 *
 * <p><b>H6（plan-03 review）</b>：非整月范围（如 8/15-8/20）会回退到直接
 * SELECT expenses 的精确路径，避免物化视图返回整月聚合。
 *
 * <p><b>LOW-currency（plan-03 review）</b>：所有 SQL 加 {@code AND currency = :currency}
 * 过滤，避免多币种混合聚合（v1.0 默认 CNY）。
 */
@Service("expenseStatsService")
public class StatsService {

    /** v1.0 单币种默认值；v1.1 多币种时由调用方传入。 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final NamedParameterJdbcTemplate jdbc;

    public StatsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StatsView stats(Long userId, LocalDate from, LocalDate to, String groupBy) {
        return stats(userId, from, to, groupBy, DEFAULT_CURRENCY);
    }

    @Transactional(readOnly = true)
    public StatsView stats(Long userId, LocalDate from, LocalDate to,
                           String groupBy, String currency) {
        if (isFullMonth(from, to)) {
            return statsByMaterializedView(userId, from, to, groupBy, currency);
        }
        return statsByDirectQuery(userId, from, to, groupBy, currency);
    }

    /**
     * 是否整月范围：[月初, 月末]。用于决定走物化视图（快）还是精确查询（精）。
     */
    private static boolean isFullMonth(LocalDate from, LocalDate to) {
        return from.getDayOfMonth() == 1
                && to.equals(from.with(TemporalAdjusters.lastDayOfMonth()));
    }

    private StatsView statsByMaterializedView(Long userId, LocalDate from, LocalDate to,
                                              String groupBy, String currency) {
        String periodStart = String.format("%04d-%02d", from.getYear(), from.getMonthValue());
        String periodEnd = String.format("%04d-%02d", to.getYear(), to.getMonthValue());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("periodStart", periodStart)
                .addValue("periodEnd", periodEnd)
                .addValue("currency", currency);

        List<Map<String, Object>> rows = jdbc.queryForList(SQL_BY_CATEGORY, params);
        long total = 0L;
        Map<Long, CategoryBucket> byCategory = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long cents = ((Number) row.get("total_amount_cents")).longValue();
            long count = ((Number) row.get("expense_count")).longValue();
            total += cents;
            Long categoryId = ((Number) row.get("category_id")).longValue();
            String categoryName = (String) row.get("category_name");
            byCategory.put(categoryId,
                    new CategoryBucket(categoryId, categoryName, cents, count));
        }

        List<DayBucket> byDay = "day".equalsIgnoreCase(groupBy)
                ? loadDayBreakdown(userId, from, to, currency)
                : new ArrayList<>();

        return new StatsView(total, currency, new ArrayList<>(byCategory.values()), byDay);
    }

    private StatsView statsByDirectQuery(Long userId, LocalDate from, LocalDate to,
                                         String groupBy, String currency) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("from", from)
                .addValue("to", to)
                .addValue("currency", currency);

        List<Map<String, Object>> rows = jdbc.queryForList(SQL_BY_CATEGORY_DIRECT, params);
        long total = 0L;
        Map<Long, CategoryBucket> byCategory = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long cents = ((Number) row.get("total_amount_cents")).longValue();
            long count = ((Number) row.get("expense_count")).longValue();
            total += cents;
            Long categoryId = ((Number) row.get("category_id")).longValue();
            String categoryName = (String) row.get("category_name");
            byCategory.put(categoryId,
                    new CategoryBucket(categoryId, categoryName, cents, count));
        }

        List<DayBucket> byDay = "day".equalsIgnoreCase(groupBy)
                ? loadDayBreakdown(userId, from, to, currency)
                : new ArrayList<>();

        return new StatsView(total, currency, new ArrayList<>(byCategory.values()), byDay);
    }

    private List<DayBucket> loadDayBreakdown(Long userId, LocalDate from, LocalDate to,
                                              String currency) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("from", from)
                .addValue("to", to)
                .addValue("currency", currency);
        List<Map<String, Object>> rows = jdbc.queryForList(SQL_BY_DAY, params);
        List<DayBucket> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object day = row.get("day");
            long cents = ((Number) row.get("amount_cents")).longValue();
            long count = ((Number) row.get("expense_count")).longValue();
            result.add(new DayBucket(day.toString(), cents, count));
        }
        return result;
    }

    private static final String SQL_BY_CATEGORY = """
            SELECT category_id,
                   category_name,
                   SUM(expense_count)   AS expense_count,
                   SUM(total_amount_cents) AS total_amount_cents
            FROM mv_expense_monthly_category
            WHERE user_id = :userId
              AND period_year_month >= :periodStart
              AND period_year_month <= :periodEnd
              AND currency = :currency
            GROUP BY category_id, category_name
            ORDER BY total_amount_cents DESC
            """;

    private static final String SQL_BY_DAY = """
            SELECT local_date::text AS day,
                   COUNT(*)         AS expense_count,
                   SUM(amount_cents) AS amount_cents
            FROM expenses
            WHERE user_id = :userId
              AND deleted_at IS NULL
              AND local_date >= :from
              AND local_date <= :to
              AND currency = :currency
            GROUP BY local_date
            ORDER BY local_date
            """;

    /** 非整月精确查询（直接 SELECT expenses + JOIN expense_categories）。 */
    private static final String SQL_BY_CATEGORY_DIRECT = """
            SELECT e.category_id, c.name AS category_name,
                   COUNT(*)            AS expense_count,
                   SUM(e.amount_cents) AS total_amount_cents
            FROM expenses e
            JOIN expense_categories c ON c.id = e.category_id
            WHERE e.user_id = :userId
              AND e.deleted_at IS NULL
              AND e.local_date >= :from
              AND e.local_date <= :to
              AND e.currency = :currency
            GROUP BY e.category_id, c.name
            ORDER BY total_amount_cents DESC
            """;
}
