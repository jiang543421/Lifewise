package com.lifewise.expense.repository;

import com.lifewise.expense.domain.ExpenseCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ExpenseCategory 仓库接口（plan-03-expense §1.3）。
 *
 * <p>BR-23：name 唯一性由 partial unique index 守护，应用层补码以提供友好错误。
 */
public interface CategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    Optional<ExpenseCategory> findByIdAndDeletedAtIsNull(Long id);

    /** 系统默认 + 该用户的自定义分类（未归档 + 未软删）。 */
    List<ExpenseCategory> findByUserIdIsNullOrUserIdAndArchivedFalseAndDeletedAtIsNullOrderBySortOrderAsc(
            Long userId);

    /** 仅系统默认（{@code user_id IS NULL}）。 */
    List<ExpenseCategory> findByUserIdIsNullAndDeletedAtIsNullOrderBySortOrderAsc();

    /** 仅系统默认 + 未归档（plan-03 review MEDIUM：listSystem 与 list() 行为对齐）。 */
    List<ExpenseCategory> findByUserIdIsNullAndDeletedAtIsNullAndArchivedFalseOrderBySortOrderAsc();

    /** 仅当前用户自定义（{@code user_id = ?}）。 */
    List<ExpenseCategory> findByUserIdAndDeletedAtIsNullOrderBySortOrderAsc(Long userId);

    /** 用户默认分类（{@code is_user_default = TRUE}）。BR-24 每用户最多 1 个。 */
    Optional<ExpenseCategory> findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(Long userId);

    /** 用于 BR-23 应用层补码。 */
    Optional<ExpenseCategory> findByUserIdAndNameAndDeletedAtIsNull(Long userId, String name);

    /** 系统分类名查重（{@code user_id IS NULL}）。 */
    Optional<ExpenseCategory> findByUserIdIsNullAndNameAndDeletedAtIsNull(String name);

    /**
     * M8 / B-2 follow-up: 用户默认分类 UPSERT（plan-03 review §M8）。
     *
     * <p>用 PostgreSQL 原生 {@code ON CONFLICT ... DO NOTHING} 处理并发注册 race —— 9 线程同时
     * 尝试 INSERT 同一行时，DB 层只成功 1 行，其余 9 行静默忽略。彻底规避 Hibernate 内部
     * AssertionFailure（catch 块重查触发 auto-flush 时的 null id 断言）。
     *
     * <p>ON CONFLICT 目标：{@code uq_expense_categories_user_default}（V37 §2.6，partial
     * {@code ON (user_id) WHERE is_user_default = TRUE}）。WHERE 谓词必须与 unique index
     * 定义完全一致，否则 PG 抛
     * {@code "no unique or exclusion constraint matching the ON CONFLICT specification"}。
     * {@code created_at / updated_at} 绕开 Hibernate AuditListener 直接 NOW()，因为
     * native SQL 不触发 {@code @PrePersist} 回调。
     *
     * <p>DO NOTHING 不触发 {@code trg_expense_categories_set_updated_at}（仅 UPDATE 触发），
     * 后续 SELECT 拿到的行就是这次 INSERT 的快照，无需额外 UPDATE 同步。
     *
     * <p><b>残余 race 兜底</b>：10 线程并发时 PG 可能撞 {@code uq_expense_categories_user_name}
     * （V37 §2.5）而非 user_default 约束——多约束场景下 ON CONFLICT 仅匹配首个抛出的约束。
     * 此时 INSERT 抛 {@code DataIntegrityViolationException}，由
     * {@link CategorySeedService#ensureUserDefault} 兜住 + 走 fallback SELECT 取回 id。
     *
     * @return 1 = 本次 INSERT 成功；0 = 已被另一事务写入（ON CONFLICT 静默）
     */
    @Modifying
    @Query(value = """
            INSERT INTO expense_categories (
                user_id, name, icon, color, parent_id, sort_order,
                is_archived, is_user_default, created_at, updated_at
            ) VALUES (
                :userId, :name, :icon, :color, NULL, :sortOrder,
                FALSE, TRUE, NOW(), NOW()
            )
            ON CONFLICT (user_id)
                WHERE is_user_default = TRUE
                DO NOTHING
            """, nativeQuery = true)
    int insertUserDefaultIfAbsent(@Param("userId") Long userId,
                                  @Param("name") String name,
                                  @Param("icon") String icon,
                                  @Param("color") String color,
                                  @Param("sortOrder") int sortOrder);
}