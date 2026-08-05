package com.lifewise.ai.service.scope;

import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.service.exception.ColumnNotAllowedException;
import com.lifewise.ai.service.exception.ScopeNotDefinedException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * AI 数据访问白名单注册表（plan-06-ai §7.3；BR-19/22）。
 *
 * <p>由 ai-data-scopes.yml 启动时绑定为不可变结构；运行时只暴露 read API。
 *
 * <p>不可变性：{@link Map#copyOf} 保证 {@code scopesByReportType} / 内部 list /
 * 内部 set 全部不可变；尝试运行时拼表名 / 列名一律抛 {@link ScopeNotDefinedException} 或
 * {@link ColumnNotAllowedException}。
 */
@Component
public class ScopedDataDefinitions {

    private final Map<AiJobType, List<AiDataScope>> scopesByReportType;

    public ScopedDataDefinitions(Map<AiJobType, List<AiDataScope>> scopesByReportType) {
        if (scopesByReportType == null || scopesByReportType.isEmpty()) {
            throw new IllegalStateException(
                    "ai-data-scopes.yml must define at least one report type");
        }
        // 深不可变
        Map<AiJobType, List<AiDataScope>> copy = new HashMap<>();
        scopesByReportType.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.scopesByReportType = Collections.unmodifiableMap(copy);
    }

    /**
     * 取指定报告类型允许访问的所有表。
     *
     * @throws ScopeNotDefinedException 报告类型未注册
     */
    public List<AiDataScope> getScopes(AiJobType reportType) {
        if (reportType == null) {
            throw new ScopeNotDefinedException("reportType=null");
        }
        List<AiDataScope> scopes = scopesByReportType.get(reportType);
        if (scopes == null) {
            throw new ScopeNotDefinedException("reportType=" + reportType.name());
        }
        return scopes;
    }

    /**
     * 按表名查找 scope。
     *
     * @throws ScopeNotDefinedException 表未注册到该 reportType
     */
    public AiDataScope findScope(AiJobType reportType, String tableName) {
        return getScopes(reportType).stream()
                .filter(s -> s.tableName().equals(tableName))
                .findFirst()
                .orElseThrow(() -> new ScopeNotDefinedException(
                        "table=" + tableName + " in reportType=" + reportType.name()));
    }

    /**
     * 校验列是否在白名单内。
     *
     * @throws ColumnNotAllowedException 不允许读取的列
     */
    public void assertColumnAllowed(AiDataScope scope, String column) {
        if (column == null || column.isBlank()) {
            throw new ColumnNotAllowedException(scope.tableName(), String.valueOf(column));
        }
        if (!scope.allowedColumns().contains(column)) {
            throw new ColumnNotAllowedException(scope.tableName(), column);
        }
    }

    /** 暴露所有已注册的 reportType（测试用，生产环境受限）。 */
    public java.util.Set<AiJobType> registeredReportTypes() {
        return scopesByReportType.keySet();
    }
}