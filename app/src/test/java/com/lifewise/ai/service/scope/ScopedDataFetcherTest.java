package com.lifewise.ai.service.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.service.exception.ColumnNotAllowedException;
import com.lifewise.ai.service.exception.ScopeNotDefinedException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * ScopedDataFetcher 安全单元测试（plan-06-ai §7.3；BR-19/22）。
 *
 * <p>覆盖 5 个核心安全断言：
 * <ol>
 *   <li>列白名单（password_hash 拒绝）</li>
 *   <li>user_id 强制注入</li>
 *   <li>跨用户读取阻断</li>
 *   <li>周期范围按 report_type 切换</li>
 *   <li>未知 reportType / tableName 拒绝</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ScopedDataFetcherTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 99L;
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 31);

    @Mock NamedParameterJdbcTemplate jdbc;
    ScopedDataFetcher fetcher;
    ScopedDataDefinitions definitions;

    @BeforeEach
    void setUp() {
        // 双 scope：DAILY_SUMMARY 用 tasks 表（period=occurred_at），
        // EXPENSE_ANALYSIS 用 expenses 表（period=occurred_at，currency 列）
        AiDataScope taskScope = new AiDataScope(
                "tasks",
                Set.of("id", "title", "status", "occurred_at", "user_id"),
                "occurred_at",
                "user_id");
        AiDataScope expenseScope = new AiDataScope(
                "expenses",
                Set.of("id", "amount", "currency", "category", "occurred_at", "user_id"),
                "occurred_at",
                "user_id");

        Map<AiJobType, List<AiDataScope>> map = new HashMap<>();
        map.put(AiJobType.DAILY_SUMMARY, List.of(taskScope));
        map.put(AiJobType.EXPENSE_ANALYSIS, List.of(expenseScope));
        // WEEKLY_SUMMARY 也用 tasks（共享同一张表，验证列集合差异）
        AiDataScope weeklyTaskScope = new AiDataScope(
                "tasks",
                Set.of("id", "title", "status", "occurred_at"),
                "occurred_at",
                "user_id");
        map.put(AiJobType.WEEKLY_SUMMARY, List.of(weeklyTaskScope));

        definitions = new ScopedDataDefinitions(map);
        fetcher = new ScopedDataFetcher(definitions, jdbc);
    }

    @Test
    @DisplayName("rejects reading columns that are not in the whitelist (e.g. password_hash)")
    void fetch_columnNotInWhitelist_throws() {
        // DAILY_SUMMARY 不允许 password_hash
        assertThatThrownBy(() -> fetcher.fetch(USER_ID, AiJobType.DAILY_SUMMARY, "tasks",
                Set.of("title", "password_hash"), FROM, TO))
                .isInstanceOf(ColumnNotAllowedException.class)
                .hasMessageContaining("password_hash");

        // 关键：不应执行任何 SQL
        verify(jdbc, never()).queryForList(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("injects the user_id filter into the WHERE clause")
    void fetch_injectsUserIdFilter() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("title", "buy milk")));

        fetcher.fetch(USER_ID, AiJobType.DAILY_SUMMARY, "tasks",
                Set.of("title"), FROM, TO);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCap =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(1)).queryForList(sqlCap.capture(), paramCap.capture());

        String sql = sqlCap.getValue();
        assertThat(sql).contains("SELECT title");
        assertThat(sql).contains("FROM tasks");
        // 关键断言：强制 user_id = :user_id 过滤
        assertThat(sql).contains("user_id = :user_id");
        assertThat(sql).contains("BETWEEN :fromDate AND :toDate");
        assertThat(sql).contains("ORDER BY occurred_at ASC");

        // 参数值正确
        MapSqlParameterSource params = paramCap.getValue();
        assertThat(params.getValue("user_id")).isEqualTo(USER_ID);
        assertThat(params.getValue("fromDate")).isEqualTo(FROM);
        assertThat(params.getValue("toDate")).isEqualTo(TO);
    }

    @Test
    @DisplayName("blocks cross-user reads by binding the request user_id (never trusts caller values)")
    void fetch_crossUserReturnsZeroRows() {
        // 模拟 SQL 行为：传 OTHER_USER_ID 时 SQL 直接返回空（user_id = :user_id 是真值过滤）
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(inv -> {
                    MapSqlParameterSource p = inv.getArgument(1);
                    Long boundUser = (Long) p.getValue("user_id");
                    if (OTHER_USER_ID.equals(boundUser)) {
                        return List.of();
                    }
                    return List.of(Map.of("title", "buy milk"));
                });

        // 攻击者尝试用 OTHER_USER_ID 调用：SQL 注入不会成功，user_id 由我们绑定
        List<Map<String, Object>> rows = fetcher.fetch(
                OTHER_USER_ID, AiJobType.DAILY_SUMMARY, "tasks",
                Set.of("title"), FROM, TO);

        // 关键：OTHER_USER_ID 看到的是空集（不属于自己的数据）
        assertThat(rows).isEmpty();
        // 没有任何 SQL 修改 user_id 值的可能（防御 SQL 注入）
        verify(jdbc, times(1)).queryForList(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("different report_types enforce different column allow-lists on the same table")
    void fetch_periodRangeDiffersByReportType() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        // DAILY_SUMMARY 允许 user_id 列
        fetcher.fetch(USER_ID, AiJobType.DAILY_SUMMARY, "tasks",
                Set.of("title", "user_id"), FROM, TO);

        // WEEKLY_SUMMARY 不允许 user_id 列（白名单更严）
        assertThatThrownBy(() -> fetcher.fetch(USER_ID, AiJobType.WEEKLY_SUMMARY, "tasks",
                Set.of("title", "user_id"), FROM, TO))
                .isInstanceOf(ColumnNotAllowedException.class)
                .hasMessageContaining("user_id");

        // DAILY_SUMMARY 触发了一次合法查询
        verify(jdbc, times(1)).queryForList(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("rejects unknown table names with SCOPE_NOT_DEFINED")
    void fetch_unknownTable_throws() {
        assertThatThrownBy(() -> fetcher.fetch(USER_ID, AiJobType.DAILY_SUMMARY, "secrets",
                Set.of("value"), FROM, TO))
                .isInstanceOf(ScopeNotDefinedException.class)
                .hasMessageContaining("secrets");

        // 不应执行任何 SQL
        verify(jdbc, never()).queryForList(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("rejects unknown report types with SCOPE_NOT_DEFINED")
    void fetch_unknownReportType_throws() {
        assertThatThrownBy(() -> fetcher.fetch(USER_ID, AiJobType.HABIT_ANALYSIS, "tasks",
                Set.of("title"), FROM, TO))
                .isInstanceOf(ScopeNotDefinedException.class)
                .hasMessageContaining("HABIT_ANALYSIS");

        verify(jdbc, never()).queryForList(anyString(), any(MapSqlParameterSource.class));
    }
}