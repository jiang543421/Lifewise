package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BudgetService 单元测试（plan-03-expense §5.3）。
 *
 * <p>BR 覆盖：BR-10 唯一性、H-5 静音范围、scope/category_id 一致性。
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock BudgetRepository budgetRepository;
    @Mock CategoryRepository categoryRepository;
    BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetRepository, categoryRepository);
    }

    // ---------- create ----------

    @Test
    void create_persists_category_budget() {
        ExpenseCategory cat = ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 0);
        cat.setIdInternal(11L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(cat));
        when(budgetRepository.findByUserIdAndScopeAndCategoryIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNull(
                7L, BudgetScope.CATEGORY, 11L, 2026, 8)).thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            b.setIdInternal(50L);
            return b;
        });

        BudgetView view = service.create(7L, new BudgetRequest(
                BudgetScope.CATEGORY, 11L, 2026, 8, 10000L, "CNY", true));

        assertThat(view.id()).isEqualTo(50L);
        assertThat(view.amountCents()).isEqualTo(10000L);
        assertThat(view.categoryId()).isEqualTo(11L);
    }

    @Test
    void create_persists_total_budget() {
        when(budgetRepository.findByUserIdAndScopeAndCategoryIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNull(
                7L, BudgetScope.TOTAL, null, 2026, 8)).thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            b.setIdInternal(60L);
            return b;
        });

        BudgetView view = service.create(7L, new BudgetRequest(
                BudgetScope.TOTAL, null, 2026, 8, 50000L, "CNY", true));

        assertThat(view.scope()).isEqualTo(BudgetScope.TOTAL);
        assertThat(view.categoryId()).isNull();
    }

    @Test
    void create_rejects_duplicate_budget() {
        ExpenseCategory cat = ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 0);
        cat.setIdInternal(11L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(cat));
        when(budgetRepository.findByUserIdAndScopeAndCategoryIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNull(
                7L, BudgetScope.CATEGORY, 11L, 2026, 8))
            .thenReturn(Optional.of(Budget.create(7L, BudgetScope.CATEGORY, 11L, 2026, 8,
                    10000L, "CNY", true)));

        assertThatThrownBy(() -> service.create(7L, new BudgetRequest(
                BudgetScope.CATEGORY, 11L, 2026, 8, 20000L, "CNY", true)))
            .isInstanceOf(BudgetAlreadyExistsException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void create_rejects_total_with_categoryId() {
        assertThatThrownBy(() -> service.create(7L, new BudgetRequest(
                BudgetScope.TOTAL, 11L, 2026, 8, 10000L, "CNY", true)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejects_category_without_categoryId() {
        assertThatThrownBy(() -> service.create(7L, new BudgetRequest(
                BudgetScope.CATEGORY, null, 2026, 8, 10000L, "CNY", true)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- update ----------

    @Test
    void update_throws_for_unknown_budget() {
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(7L, 1L, new BudgetRequest(
                BudgetScope.CATEGORY, 11L, 2026, 8, 20000L, "CNY", true)))
            .isInstanceOf(BudgetNotFoundException.class);
    }

    @Test
    void update_applies_amount_and_notify() {
        Budget existing = Budget.create(7L, BudgetScope.CATEGORY, 11L, 2026, 8,
                10000L, "CNY", true);
        existing.setIdInternal(1L);
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetView view = service.update(7L, 1L, new BudgetRequest(
                BudgetScope.CATEGORY, 11L, 2026, 8, 20000L, "CNY", false));

        assertThat(view.amountCents()).isEqualTo(20000L);
        assertThat(view.notifyEnabled()).isFalse();
    }

    // ---------- softDelete ----------

    @Test
    void softDelete_throws_for_unknown_budget() {
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(99L, 7L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(7L, 99L))
            .isInstanceOf(BudgetNotFoundException.class);
    }

    @Test
    void softDelete_marks_deleted() {
        Budget existing = Budget.create(7L, BudgetScope.CATEGORY, 11L, 2026, 8,
                10000L, "CNY", true);
        existing.setIdInternal(1L);
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));

        service.softDelete(7L, 1L);

        assertThat(existing.isDeleted()).isTrue();
        verify(budgetRepository, times(1)).save(existing);
    }

    // ---------- mute ----------

    @Test
    void mute_throws_for_unknown_budget() {
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(99L, 7L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mute(7L, 99L, new BudgetMuteRequest(LocalDate.of(2026, 8, 15))))
            .isInstanceOf(BudgetNotFoundException.class);
    }

    @Test
    void mute_sets_until_within_period() {
        Budget existing = Budget.create(7L, BudgetScope.CATEGORY, 11L, 2026, 8,
                10000L, "CNY", true);
        existing.setIdInternal(1L);
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetView view = service.mute(7L, 1L, new BudgetMuteRequest(LocalDate.of(2026, 8, 20)));

        assertThat(view.notifyMutedUntil()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void mute_rejects_until_outside_period() {
        Budget existing = Budget.create(7L, BudgetScope.CATEGORY, 11L, 2026, 8,
                10000L, "CNY", true);
        existing.setIdInternal(1L);
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                service.mute(7L, 1L, new BudgetMuteRequest(LocalDate.of(2026, 9, 1))))
            .isInstanceOf(IllegalArgumentException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void mute_unmute_with_null_until() {
        Budget existing = Budget.create(7L, BudgetScope.CATEGORY, 11L, 2026, 8,
                10000L, "CNY", true);
        existing.setIdInternal(1L);
        existing.mute(LocalDate.of(2026, 8, 25));
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetView view = service.mute(7L, 1L, new BudgetMuteRequest(null));

        assertThat(view.notifyMutedUntil()).isNull();
    }

    // ---------- list ----------

    @Test
    void list_returns_user_budgets_for_month() {
        Budget b = Budget.create(7L, BudgetScope.CATEGORY, 11L, 2026, 8,
                10000L, "CNY", true);
        b.setIdInternal(1L);
        when(budgetRepository.findByUserIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNullOrderByScopeAsc(
                7L, 2026, 8)).thenReturn(List.of(b));

        List<BudgetView> views = service.list(7L, 2026, 8);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(1L);
    }
}