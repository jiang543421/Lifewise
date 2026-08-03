package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
 * StatsService 单元测试（plan-03-expense §5.5）。
 *
 * <p>核心断言：groupBy=category 走 mv_expense_monthly_category 物化视图；
 * groupBy=day 走按日查询。
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock NamedParameterJdbcTemplate jdbc;
    StatsService service;

    @BeforeEach
    void setUp() {
        service = new StatsService(jdbc);
    }

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
}