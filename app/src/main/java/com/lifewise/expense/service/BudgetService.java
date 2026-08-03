package com.lifewise.expense.service;

import com.lifewise.expense.domain.Budget;
import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.domain.enums.BudgetScope;
import com.lifewise.expense.dto.BudgetMuteRequest;
import com.lifewise.expense.dto.BudgetRequest;
import com.lifewise.expense.dto.BudgetView;
import com.lifewise.expense.repository.BudgetRepository;
import com.lifewise.expense.repository.CategoryRepository;
import com.lifewise.expense.service.exception.BudgetAlreadyExistsException;
import com.lifewise.expense.service.exception.BudgetNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预算服务（plan-03-expense §1.3 + §5.3）。
 *
 * <p>BR-10：唯一性由 DB UNIQUE + 应用层补码双保险。
 * H-5：mute 日期必须落在当前 period 内，由 {@link Budget#mute(LocalDate)} 守护。
 */
@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    public BudgetService(BudgetRepository budgetRepository,
                          CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public BudgetView create(Long userId, BudgetRequest req) {
        validateScopeCategory(req.scope(), req.categoryId());
        if (req.scope() == BudgetScope.CATEGORY) {
            ExpenseCategory cat = categoryRepository.findByIdAndDeletedAtIsNull(req.categoryId())
                    .orElseThrow(() -> new com.lifewise.expense.service.exception.CategoryNotFoundException(
                            req.categoryId()));
            if (!cat.isSystem() && !cat.isOwnedBy(userId)) {
                throw new com.lifewise.expense.service.exception.CategoryNotFoundException(
                        req.categoryId());
            }
        }
        checkUniqueness(userId, req);
        Budget budget = Budget.create(
                userId,
                req.scope(),
                req.scope() == BudgetScope.TOTAL ? null : req.categoryId(),
                req.periodYear(),
                req.periodMonth(),
                req.amountCents(),
                req.currency() == null ? "CNY" : req.currency(),
                req.notifyEnabled() == null || req.notifyEnabled());
        return BudgetView.from(budgetRepository.save(budget));
    }

    @Transactional
    public BudgetView update(Long userId, Long budgetId, BudgetRequest req) {
        Budget budget = loadOwned(userId, budgetId);
        validateScopeCategory(req.scope(), req.categoryId());
        budget.applyUpdate(req.amountCents(),
                req.notifyEnabled() == null || req.notifyEnabled());
        return BudgetView.from(budgetRepository.save(budget));
    }

    @Transactional
    public void softDelete(Long userId, Long budgetId) {
        Budget budget = loadOwned(userId, budgetId);
        budget.softDelete();
        budgetRepository.save(budget);
    }

    @Transactional
    public BudgetView mute(Long userId, Long budgetId, BudgetMuteRequest req) {
        Budget budget = loadOwned(userId, budgetId);
        budget.mute(req.until());
        return BudgetView.from(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetView> list(Long userId, int year, int month) {
        return budgetRepository
                .findByUserIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNullOrderByScopeAsc(userId, year, month)
                .stream()
                .map(BudgetView::from)
                .toList();
    }

    // ---------- internals ----------

    private Budget loadOwned(Long userId, Long budgetId) {
        return budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(budgetId, userId)
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));
    }

    private void checkUniqueness(Long userId, BudgetRequest req) {
        Long categoryId = req.scope() == BudgetScope.TOTAL ? null : req.categoryId();
        if (budgetRepository
                .findByUserIdAndScopeAndCategoryIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNull(
                        userId, req.scope(), categoryId, req.periodYear(), req.periodMonth())
                .isPresent()) {
            throw new BudgetAlreadyExistsException(userId, req.scope(), categoryId,
                    req.periodYear(), req.periodMonth());
        }
    }

    private static void validateScopeCategory(BudgetScope scope, Long categoryId) {
        if (scope == BudgetScope.TOTAL && categoryId != null) {
            throw new IllegalArgumentException("categoryId must be null for TOTAL scope");
        }
        if (scope == BudgetScope.CATEGORY && categoryId == null) {
            throw new IllegalArgumentException("categoryId required for CATEGORY scope");
        }
    }
}