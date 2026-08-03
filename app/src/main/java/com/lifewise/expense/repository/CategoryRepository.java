package com.lifewise.expense.repository;

import com.lifewise.expense.domain.ExpenseCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ExpenseCategory 仓库接口（plan-03-expense §1.3）。
 *
 * <p>BR-23：name 唯一性由 partial unique index 守护，应用层补码以提供友好错误。
 */
public interface CategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    Optional<ExpenseCategory> findByIdAndDeletedAtIsNull(Long id);

    /** 系统默认 + 该用户的自定义分类（未归档 + 未软删）。 */
    List<ExpenseCategory> findByUserIdIsNullOrUserIdAndArchivedFalseAndDeletedAtIsNullOrderBySortOrderAsc(
            Long userId1, Long userId2);

    /** 仅系统默认（{@code user_id IS NULL}）。 */
    List<ExpenseCategory> findByUserIdIsNullAndDeletedAtIsNullOrderBySortOrderAsc();

    /** 仅当前用户自定义（{@code user_id = ?}）。 */
    List<ExpenseCategory> findByUserIdAndDeletedAtIsNullOrderBySortOrderAsc(Long userId);

    /** 用户默认分类（{@code is_user_default = TRUE}）。BR-24 每用户最多 1 个。 */
    Optional<ExpenseCategory> findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(Long userId);

    /** 用于 BR-23 应用层补码。 */
    Optional<ExpenseCategory> findByUserIdAndNameAndDeletedAtIsNull(Long userId, String name);

    /** 系统分类名查重（{@code user_id IS NULL}）。 */
    Optional<ExpenseCategory> findByUserIdIsNullAndNameAndDeletedAtIsNull(String name);
}