package com.lifewise.shared.integration.port;

import com.lifewise.shared.integration.port.snapshot.ExpenseSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Expense 模块对外只读端口（plan-shared-integration §2.2 + plan-03-expense §2.2）。
 *
 * <p>实现由 expense 模块在 {@code com.lifewise.expense.port.out.ExpenseReadPortAdapter} 提供。
 *
 * <p>所有 sum* 方法返回 {@code long}（cents），跨进程累加无浮点误差（BR-09）。
 */
public interface ExpenseReadPort {

    /** 单笔消费（软删过滤）。 */
    Optional<ExpenseSnapshot> findById(Long userId, Long expenseId);

    /** 时间区间内消费列表（按 occurredAt 降序）。 */
    List<ExpenseSnapshot> findInRange(Long userId, LocalDate from, LocalDate to);

    /** 指定分类下最新 N 笔消费（默认列表/详情页用）。 */
    List<ExpenseSnapshot> findByCategory(Long userId, Long categoryId, int limit);

    /** 时间区间内消费总额（cents），用于 BudgetEvaluator 等。 */
    long sumInRange(Long userId, LocalDate from, LocalDate to);

    /** 时间区间内按分类汇总（categoryId -> cents），用于 StatsService。 */
    Map<Long, Long> sumByCategoryInRange(Long userId, LocalDate from, LocalDate to);
}
