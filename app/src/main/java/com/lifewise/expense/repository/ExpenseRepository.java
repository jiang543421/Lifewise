package com.lifewise.expense.repository;

import com.lifewise.expense.domain.Expense;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Expense 仓库接口（plan-03-expense §1.3）。
 *
 * <p>所有方法默认过滤 {@code deleted_at IS NULL}，并按 {@code user_id} 隔离。
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /**
     * 按 {@code (id, userId)} 查找，<b>不区分软删状态</b>。仅用于 {@code restore} 找回已软删记录。
     * 正常查询请用 {@link #findByIdAndUserIdAndDeletedAtIsNull}。
     */
    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    List<Expense> findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByOccurredAtDesc(
            Long userId, LocalDate from, LocalDate to);

    List<Expense> findByUserIdAndCategoryIdAndDeletedAtIsNullOrderByOccurredAtDesc(
            Long userId, Long categoryId, Pageable pageable);

    @Query("""
            select coalesce(sum(e.amountCents), 0) from Expense e
            where e.userId = :userId
              and e.localDate >= :from
              and e.localDate <= :to
              and e.deletedAt is null
            """)
    long sumInRangeCents(@Param("userId") Long userId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(e.amountCents), 0) from Expense e
            where e.userId = :userId
              and e.localDate >= :from
              and e.localDate <= :to
              and e.categoryId = :categoryId
              and e.deletedAt is null
            """)
    long sumInRangeByCategoryCents(@Param("userId") Long userId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("categoryId") Long categoryId);

    /**
     * 软删除账单迁移到新分类（BR-20 辅助；用于分类删除事务）。
     *
     * <p><b>仅在 {@code @Transactional} 内调用</b>；单独调用会抛 {@code TransactionRequiredException}。
     * 调用方：{@link CategoryService#softDelete(Long, Long)}。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Expense e set e.categoryId = :newCategoryId
            where e.userId = :userId
              and e.categoryId = :oldCategoryId
              and e.deletedAt is null
            """)
    int reassignCategory(@Param("userId") Long userId,
                          @Param("oldCategoryId") Long oldCategoryId,
                          @Param("newCategoryId") Long newCategoryId);
}