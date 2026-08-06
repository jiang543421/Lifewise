package com.lifewise.ai.service.scope;

import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.service.exception.ColumnNotAllowedException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 数据安全读取器（plan-06-ai §7.3；BR-19/22；shared-strings §7 'ai' scope）。
 *
 * <p><b>核心安全约束</b>：
 * <ol>
 *   <li>reportType 必须在白名单内（否则 {@code ScopeNotDefinedException}）</li>
 *   <li>tableName 必须在该 reportType 的 scope 列表内（否则 {@code ScopeNotDefinedException}）</li>
 *   <li>每个请求列必须在 {@code allowedColumns} 内（否则 {@code ColumnNotAllowedException}）</li>
 *   <li>SQL 强制 WHERE user_id = :userId 与 period BETWEEN（user 隔离 + 时间窗口）</li>
 * </ol>
 *
 * <p>所有 SQL 通过 NamedParameterJdbcTemplate 参数化，禁止拼接用户输入。
 */
@Component
public class ScopedDataFetcher {

    private final ScopedDataDefinitions definitions;
    private final NamedParameterJdbcTemplate jdbc;

    public ScopedDataFetcher(ScopedDataDefinitions definitions,
                             NamedParameterJdbcTemplate jdbc) {
        this.definitions = definitions;
        this.jdbc = jdbc;
    }

    /**
     * 取数。
     *
     * @param userId      用户 ID（强制注入到 user_id = :userId）
     * @param reportType  报告类型（决定哪些表 / 列可用）
     * @param tableName   目标表名（必须在白名单内）
     * @param columns     目标列集合（每个必须在 allowedColumns 内）
     * @param from        周期起点（含）
     * @param to          周期终点（含）
     * @return 行数据（每行 column → value）
     */
    public List<Map<String, Object>> fetch(Long userId,
                                           AiJobType reportType,
                                           String tableName,
                                           Set<String> columns,
                                           LocalDate from,
                                           LocalDate to) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("period required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be <= to");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns required");
        }

        AiDataScope scope = definitions.findScope(reportType, tableName);

        // 列白名单校验（保留请求顺序 + 去重）
        Set<String> dedup = new LinkedHashSet<>();
        for (String col : columns) {
            definitions.assertColumnAllowed(scope, col);
            dedup.add(col);
        }
        // user_id 列和 period 列由 SQL 隐式使用，不需 SELECT，但仍需 allow 检查
        // （已经在 AiDataScope 注册时声明）

        String sql = buildSelectSql(scope, dedup);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(scope.userIdColumn(), userId)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        return jdbc.queryForList(sql, params);
    }

    private String buildSelectSql(AiDataScope scope, Set<String> columns) {
        StringBuilder sb = new StringBuilder("SELECT ");
        boolean first = true;
        for (String c : columns) {
            if (!first) sb.append(", ");
            sb.append(c);
            first = false;
        }
        sb.append(" FROM ").append(scope.tableName())
          .append(" WHERE ").append(scope.userIdColumn()).append(" = :")
          .append(scope.userIdColumn())
          .append(" AND ").append(scope.periodColumn())
          .append(" BETWEEN :fromDate AND :toDate")
          .append(" ORDER BY ").append(scope.periodColumn()).append(" ASC");
        return sb.toString();
    }

    /**
     * 列出某报告类型的所有可用表（debug / 文档化用）。
     */
    public List<String> listTables(AiJobType reportType) {
        List<String> out = new ArrayList<>();
        for (AiDataScope s : definitions.getScopes(reportType)) {
            out.add(s.tableName());
        }
        return out;
    }
}