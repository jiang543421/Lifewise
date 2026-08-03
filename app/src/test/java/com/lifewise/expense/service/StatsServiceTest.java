package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.expense.dto.StatsView;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * StatsService 单元测试（plan-03-expense §5.5 + review H6/LOW-currency）。
 *
 * <p>核心断言：
 * <ul>
 *   <li>groupBy=category 整月走 mv_expense_monthly_category 物化视图（H6 快路径）</li>
 *   <li>非整月回退到 FROM expenses e + JOIN expense_categories（H6 精确路径）</li>
 *   <li>2 个 SQL 都含 AND currency = :currency（LOW-currency）</li>
 *   <li>StatsView.currency 跟查询参数（非硬编码）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock NamedParameterJdbcTemplate jdbc;
    StatsService service;

    @BeforeEach
    void setUp() {
        service = new StatsService(jdbc);
    }

    // ---------- 现有测试（保留，date range 都是 8/1-8/31 整月） ----------

    @Test
    void stats_by_category_uses_materialized_view_and_aggregates_total() {
        Map<String, Object> row1 = new HashMap<>();
        row1.put("category_id", 11L);
        row1.put("category_name", "餐饮");
        row1.put("expense_count", 3);
        row1.put("total_amount_cents", 8000L);
        Map<String, Object> row2 = new HashMap<>();
        row2.put("category_id", 12L);
        row2.put("category_name", "交通");
        row2.put("expense_count", 2);
        row2.put("total_amount_cents", 2000L);
        when(jdbc.queryForList(contains("mv_expense_monthly_category"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(row1, row2));

        StatsView view = service.stats(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "category");

        assertThat(view.totalCents()).isEqualTo(10000L);
        assertThat(view.byCategory()).hasSize(2);
        assertThat(view.byCategory().get(0).categoryName()).isEqualTo("餐饮");
    }

    @Test
    void stats_by_day_runs_day_query() {
        when(jdbc.queryForList(contains("mv_expense_monthly_category"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());
        Map<String, Object> dayRow = new HashMap<>();
        dayRow.put("day", "2026-08-01");
        dayRow.put("expense_count", 2);
        dayRow.put("amount_cents", 1000L);
        when(jdbc.queryForList(contains("FROM expenses"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(dayRow));

        StatsView view = service.stats(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "day");

        assertThat(view.byDay()).hasSize(1);
        assertThat(view.byDay().get(0).day()).isEqualTo("2026-08-01");
    }

    @Test
    void stats_default_returns_empty_byDay() {
        when(jdbc.queryForList(contains("mv_expense_monthly_category"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        StatsView view = service.stats(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null);

        assertThat(view.byDay()).isEmpty();
    }

    @Test
    void stats_passes_period_year_month_in_iso_format() {
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        service.stats(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "category");

        ArgumentCaptor<MapSqlParameterSource> captor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(1)).queryForList(any(String.class), captor.capture());
        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("userId")).isEqualTo(7L);
        assertThat(params.getValue("periodStart")).isEqualTo("2026-08");
        assertThat(params.getValue("periodEnd")).isEqualTo("2026-08");
    }

    // ---------- H6：整月 → 物化视图（非整月 → 直接查询） ----------

    @Test
    void stats_full_month_uses_materialized_view() {
        when(jdbc.queryForList(argThat((String sql) -> sql.contains("mv_expense_monthly_category")),
                any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        service.stats(7L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "category");

        // 整月 → 走物化视图，byCategory SQL 命中 mv_expense_monthly_category
        verify(jdbc).queryForList(
                argThat((String sql) -> sql.contains("mv_expense_monthly_category")),
                any(MapSqlParameterSource.class));
        // 整月 → 不走 FROM expenses e 精确路径
        verify(jdbc, times(0)).queryForList(
                argThat((String sql) -> sql.contains("FROM expenses e")),
                any(MapSqlParameterSource.class));
    }

    @Test
    void stats_partial_month_falls_back_to_direct_query() {
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        service.stats(7L, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20), "category");

        // 非整月 → 走 FROM expenses e + JOIN expense_categories 精确路径
        verify(jdbc).queryForList(
                argThat((String sql) -> sql.contains("FROM expenses e")
                        && sql.contains("JOIN expense_categories")),
                any(MapSqlParameterSource.class));
        // 非整月 → 不走物化视图
        verify(jdbc, times(0)).queryForList(
                argThat((String sql) -> sql.contains("mv_expense_monthly_category")),
                any(MapSqlParameterSource.class));
    }

    @Test
    void stats_partial_month_by_category_aggregates_exact_range() {
        // 8/15-8/20 范围 6 天：2 行
        Map<String, Object> row1 = new HashMap<>();
        row1.put("category_id", 11L);
        row1.put("category_name", "餐饮");
        row1.put("expense_count", 2);
        row1.put("total_amount_cents", 3000L);
        Map<String, Object> row2 = new HashMap<>();
        row2.put("category_id", 12L);
        row2.put("category_name", "交通");
        row2.put("expense_count", 1);
        row2.put("total_amount_cents", 1500L);
        when(jdbc.queryForList(argThat((String sql) -> sql.contains("FROM expenses e")),
                any(MapSqlParameterSource.class)))
            .thenReturn(List.of(row1, row2));

        StatsView view = service.stats(7L,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20), "category");

        // 验证 byCategory 是 6 天精确聚合（不返回整月）
        assertThat(view.totalCents()).isEqualTo(4500L);
        assertThat(view.byCategory()).hasSize(2);
    }

    // ---------- LOW-currency：2 SQL 都含 currency 过滤 ----------

    @Test
    void stats_mv_sql_includes_currency_filter() {
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        service.stats(7L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "category");

        // 物化视图 SQL 含 AND currency = :currency
        verify(jdbc).queryForList(
                argThat((String sql) -> sql.contains("mv_expense_monthly_category")
                        && sql.contains("AND currency = :currency")),
                any(MapSqlParameterSource.class));
    }

    @Test
    void stats_day_sql_includes_currency_filter() {
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        service.stats(7L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "day");

        // SQL_BY_DAY（含 FROM expenses 无别名）含 AND currency = :currency
        verify(jdbc).queryForList(
                argThat((String sql) -> sql.contains("FROM expenses")
                        && !sql.contains("FROM expenses e")
                        && sql.contains("AND currency = :currency")),
                any(MapSqlParameterSource.class));
    }

    // ---------- StatsView.currency + DEFAULT_CURRENCY（双断言） ----------

    @Test
    void stats_default_currency_is_CNY() {
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        StatsView view = service.stats(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "category");

        // 断言 1：StatsView.currency 跟查询 currency 参数（不是硬编码 "CNY"）
        assertThat(view.currency()).isEqualTo("CNY");
        // 断言 2：SQL 参数 currency = "CNY"
        ArgumentCaptor<MapSqlParameterSource> captor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(1)).queryForList(any(String.class), captor.capture());
        assertThat(captor.getValue().getValue("currency")).isEqualTo("CNY");
    }

    // ---------- 边界：跨月 / 单日 / 月末单日 ----------

    @Test
    void stats_cross_month_falls_back_to_direct_query() {
        // 8/15-9/15 跨月：from.getDayOfMonth() == 15 != 1 → direct
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        service.stats(7L, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15), "category");

        verify(jdbc).queryForList(
                argThat((String sql) -> sql.contains("FROM expenses e")),
                any(MapSqlParameterSource.class));
        verify(jdbc, times(0)).queryForList(
                argThat((String sql) -> sql.contains("mv_expense_monthly_category")),
                any(MapSqlParameterSource.class));
    }

    @Test
    void stats_single_day_not_full_month() {
        // from=to=8/15 单日：from.dayOfMonth==15 != 1 → direct
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        service.stats(7L, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15), "category");

        verify(jdbc).queryForList(
                argThat((String sql) -> sql.contains("FROM expenses e")),
                any(MapSqlParameterSource.class));
    }

    @Test
    void stats_last_day_of_month_not_full_month() {
        // from=to=8/31 月末 1 天：from.dayOfMonth==31 != 1 → direct
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
            .thenReturn(List.of());

        service.stats(7L, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 31), "category");

        verify(jdbc).queryForList(
                argThat((String sql) -> sql.contains("FROM expenses e")),
                any(MapSqlParameterSource.class));
    }
}
