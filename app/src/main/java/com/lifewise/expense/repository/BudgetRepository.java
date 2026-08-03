package com.lifewise.expense.repository;

import com.lifewise.expense.domain.Budget;
import com.lifewise.expense.domain.enums.BudgetScope;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Budget 仓库接口（plan-03-expense §1.3）。
 *
 * <p>BR-10：唯一约束由 DB UNIQUE 守护；应用层补码捕获并转换为业务异常。
 */
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /** 用户某月所有预算（用于 BudgetEvaluator 评估）。 */
    List<Budget> findByUserIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNullOrderByScopeAsc(
            Long userId, int periodYear, int periodMonth);

    /**
     * 评估候选：用户某月预算 + 给定分类作为触发分类。
     *
     * <p>返回该用户的全部预算（TOTAL 也算入 CATEGORY 的累计），调用方按需过滤。
     */
    @Query("""
            select b from Budget b
            where b.userId = :userId
              and b.periodYear = :periodYear
              and b.periodMonth = :periodMonth
              and b.deletedAt is null
              and (
                  b.scope = com.lifewise.expense.domain.enums.BudgetScope.TOTAL
                  or b.categoryId = :categoryId
              )
            order by b.scope asc
            """)
    List<Budget> findActiveForEvaluation(@Param("userId") Long userId,
                                          @Param("periodYear") int periodYear,
                                          @Param("periodMonth") int periodMonth,
                                          @Param("categoryId") Long categoryId);

    /** 用户月度内同 (scope, categoryId, period) 已存在？用于 BR-10 补码。 */
    Optional<Budget> findByUserIdAndScopeAndCategoryIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNull(
            Long userId, BudgetScope scope, Long categoryId,
            int periodYear, int periodMonth);

    /** 该分类下是否存在任何非软删预算（用于 CATEGORY_HAS_BUDGET 检查）。 */
    @Query("""
            select count(b) > 0 from Budget b
            where b.categoryId = :categoryId
              and b.deletedAt is null
            """)
    boolean existsByCategoryIdActive(@Param("categoryId") Long categoryId);
}