package com.lifewise.expense.service;

import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.dto.CategoryCreateRequest;
import com.lifewise.expense.dto.CategoryUpdateRequest;
import com.lifewise.expense.dto.CategoryView;
import com.lifewise.expense.repository.BudgetRepository;
import com.lifewise.expense.repository.CategoryRepository;
import com.lifewise.expense.repository.ExpenseRepository;
import com.lifewise.expense.service.exception.CategoryHasBudgetException;
import com.lifewise.expense.service.exception.CategoryNameExistsException;
import com.lifewise.expense.service.exception.CategoryNotFoundException;
import com.lifewise.expense.service.exception.CategoryProtectedException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分类写服务（plan-03-expense §1.3 + §5.2）。
 *
 * <p>BR 覆盖：BR-20 删除迁移、BR-23 名称唯一、BR-24 默认分类保护。
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;

    public CategoryService(CategoryRepository categoryRepository,
                            ExpenseRepository expenseRepository,
                            BudgetRepository budgetRepository) {
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list(Long userId) {
        return categoryRepository
                .findByUserIdIsNullOrUserIdAndArchivedFalseAndDeletedAtIsNullOrderBySortOrderAsc(userId)
                .stream()
                .map(CategoryView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listSystem() {
        return categoryRepository
                .findByUserIdIsNullAndDeletedAtIsNullAndArchivedFalseOrderBySortOrderAsc()
                .stream()
                .map(CategoryView::from)
                .toList();
    }

    @Transactional
    public CategoryView create(Long userId, CategoryCreateRequest req) {
        if (categoryRepository.findByUserIdAndNameAndDeletedAtIsNull(userId, req.name()).isPresent()) {
            throw new CategoryNameExistsException(req.name());
        }
        validateParentId(userId, req.parentId());
        ExpenseCategory created = ExpenseCategory.createUserCategory(
                userId,
                req.name(),
                req.icon(),
                req.color(),
                req.parentId(),
                req.sortOrder() == null ? 0 : req.sortOrder());
        ExpenseCategory saved = categoryRepository.save(created);
        return CategoryView.from(saved);
    }

    /**
     * 校验 parentId（plan-03 review MEDIUM：create 路径 parentId 完全缺失）。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code null} → 顶级分类，跳过校验</li>
     *   <li>不存在 / 已软删 → 404 {@code CategoryNotFoundException}（不区分，防探测）</li>
     *   <li>不属于该 user 且非系统分类 → 404（不暴露存在性，防跨 user 探测）</li>
     *   <li>已归档 → 400 {@code IllegalArgumentException}（走 commit #4 新增 handler → INVALID_INPUT）</li>
     *   <li>系统分类 / 自己的活跃分类 → 允许</li>
     * </ul>
     *
     * <p>注：{@code CategoryUpdateRequest} 无 {@code parentId} 字段，update 路径不涉及
     * parent 修改，本 commit 不覆盖 update。如未来加 parentId 字段，需复用本方法。
     */
    private void validateParentId(Long userId, Long parentId) {
        if (parentId == null) {
            return;
        }
        ExpenseCategory parent = categoryRepository.findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new CategoryNotFoundException(parentId));
        if (parent.isArchived()) {
            throw new IllegalArgumentException(
                    "parent category is archived: id=" + parentId);
        }
        if (!parent.isSystem() && !parent.isOwnedBy(userId)) {
            throw new CategoryNotFoundException(parentId);
        }
    }

    @Transactional
    public CategoryView update(Long userId, Long categoryId, CategoryUpdateRequest req) {
        ExpenseCategory cat = loadOwnedCategory(userId, categoryId);
        if (cat.isUserDefault()) {
            throw new CategoryProtectedException(categoryId);
        }
        if (req.name() != null && !req.name().equals(cat.getName())) {
            if (categoryRepository.findByUserIdAndNameAndDeletedAtIsNull(userId, req.name()).isPresent()) {
                throw new CategoryNameExistsException(req.name());
            }
        }
        cat.applyUpdate(req.name(), req.icon(), req.color(),
                req.sortOrder() == null ? cat.getSortOrder() : req.sortOrder());
        if (Boolean.TRUE.equals(req.archived())) {
            cat.archive();
        } else if (Boolean.FALSE.equals(req.archived())) {
            cat.unarchive();
        }
        return CategoryView.from(categoryRepository.save(cat));
    }

    /**
     * 软删除 + BR-20 账目迁移到用户的「其他」分类。
     *
     * <p>默认分类不可删（BR-24）。分类下若仍有活跃预算则拒（CATEGORY_HAS_BUDGET）。
     */
    @Transactional
    public void softDelete(Long userId, Long categoryId) {
        ExpenseCategory cat = loadOwnedCategory(userId, categoryId);
        if (cat.isUserDefault()) {
            throw new CategoryProtectedException(categoryId);
        }
        if (budgetRepository.existsByCategoryIdActive(categoryId)) {
            throw new CategoryHasBudgetException(categoryId);
        }
        ExpenseCategory userDefault = categoryRepository
                .findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "user_default category missing for userId=" + userId));
        expenseRepository.reassignCategory(userId, categoryId, userDefault.getId());
        cat.softDelete();
        categoryRepository.save(cat);
    }

    @Transactional(readOnly = true)
    public ExpenseCategory loadOwnedCategory(Long userId, Long categoryId) {
        ExpenseCategory cat = categoryRepository.findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (!cat.isSystem() && !cat.isOwnedBy(userId)) {
            throw new CategoryNotFoundException(categoryId);
        }
        return cat;
    }
}