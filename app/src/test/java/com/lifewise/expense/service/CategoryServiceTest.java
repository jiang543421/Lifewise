package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CategoryService 单元测试（plan-03-expense §5.2）。
 *
 * <p>BR 覆盖：BR-23 名称唯一、BR-24 默认分类保护、BR-20 删除迁移、BR-09 名称长度。
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock BudgetRepository budgetRepository;
    CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, expenseRepository, budgetRepository);
    }

    // ---------- list ----------

    @Test
    void list_returns_system_and_user_categories() {
        ExpenseCategory sys = ExpenseCategory.createSystem("餐饮", null, null, null, 1);
        ExpenseCategory user = ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 10);
        when(categoryRepository.findByUserIdIsNullOrUserIdAndArchivedFalseAndDeletedAtIsNullOrderBySortOrderAsc(
                eq(7L)))
            .thenReturn(List.of(sys, user));

        List<CategoryView> result = service.list(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("餐饮");
        assertThat(result.get(1).name()).isEqualTo("咖啡");
    }

    @Test
    void listSystem_returns_only_system_categories() {
        ExpenseCategory sys = ExpenseCategory.createSystem("餐饮", null, null, null, 1);
        when(categoryRepository.findByUserIdIsNullAndDeletedAtIsNullOrderBySortOrderAsc())
            .thenReturn(List.of(sys));

        assertThat(service.listSystem()).hasSize(1);
    }

    // ---------- create ----------

    @Test
    void create_rejects_duplicate_user_name() {
        when(categoryRepository.findByUserIdAndNameAndDeletedAtIsNull(7L, "咖啡"))
            .thenReturn(Optional.of(ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 0)));

        assertThatThrownBy(() ->
                service.create(7L, new CategoryCreateRequest("咖啡", null, null, null, 0)))
            .isInstanceOf(CategoryNameExistsException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_persists_user_category() {
        when(categoryRepository.findByUserIdAndNameAndDeletedAtIsNull(7L, "咖啡"))
            .thenReturn(Optional.empty());
        when(categoryRepository.save(any(ExpenseCategory.class))).thenAnswer(inv -> {
            ExpenseCategory c = inv.getArgument(0);
            c.setIdInternal(11L);
            return c;
        });

        CategoryView view = service.create(7L,
                new CategoryCreateRequest("咖啡", "☕", "#A0522D", null, 10));

        assertThat(view.id()).isEqualTo(11L);
        assertThat(view.name()).isEqualTo("咖啡");
        assertThat(view.icon()).isEqualTo("☕");
        assertThat(view.color()).isEqualTo("#A0522D");
        assertThat(view.system()).isFalse();
    }

    // ---------- update ----------

    @Test
    void update_throws_when_category_not_found() {
        when(categoryRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(7L, 99L, new CategoryUpdateRequest("x", null, null, 0, null)))
            .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void update_rejects_default_category_change() {
        ExpenseCategory def = ExpenseCategory.createUserDefault(7L, "其他", null, null, 100);
        def.setIdInternal(5L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(def));

        assertThatThrownBy(() ->
                service.update(7L, 5L, new CategoryUpdateRequest("新名", null, null, 0, null)))
            .isInstanceOf(CategoryProtectedException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_throws_for_cross_user_access() {
        ExpenseCategory other = ExpenseCategory.createUserCategory(99L, "x", null, null, null, 0);
        other.setIdInternal(1L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() ->
                service.update(7L, 1L, new CategoryUpdateRequest("y", null, null, 0, null)))
            .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void update_rejects_duplicate_name_when_renaming() {
        ExpenseCategory mine = ExpenseCategory.createUserCategory(7L, "old", null, null, null, 0);
        mine.setIdInternal(1L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(mine));
        when(categoryRepository.findByUserIdAndNameAndDeletedAtIsNull(7L, "new"))
            .thenReturn(Optional.of(ExpenseCategory.createUserCategory(7L, "new", null, null, null, 0)));

        assertThatThrownBy(() ->
                service.update(7L, 1L, new CategoryUpdateRequest("new", null, null, 0, null)))
            .isInstanceOf(CategoryNameExistsException.class);
    }

    @Test
    void update_applies_changes() {
        ExpenseCategory mine = ExpenseCategory.createUserCategory(7L, "old", null, null, null, 0);
        mine.setIdInternal(1L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(mine));
        when(categoryRepository.findByUserIdAndNameAndDeletedAtIsNull(7L, "new"))
            .thenReturn(Optional.empty());
        when(categoryRepository.save(any(ExpenseCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryView view = service.update(7L, 1L,
                new CategoryUpdateRequest("new", "🔥", "#FF0000", 5, false));

        assertThat(view.name()).isEqualTo("new");
        assertThat(view.icon()).isEqualTo("🔥");
        assertThat(view.color()).isEqualTo("#FF0000");
    }

    // ---------- softDelete ----------

    @Test
    void softDelete_throws_when_category_not_found() {
        when(categoryRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(7L, 99L))
            .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void softDelete_throws_when_category_has_active_budgets() {
        ExpenseCategory mine = ExpenseCategory.createUserCategory(7L, "x", null, null, null, 0);
        mine.setIdInternal(1L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(mine));
        when(budgetRepository.existsByCategoryIdActive(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(7L, 1L))
            .isInstanceOf(CategoryHasBudgetException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void softDelete_rejects_default_category() {
        ExpenseCategory def = ExpenseCategory.createUserDefault(7L, "其他", null, null, 100);
        def.setIdInternal(5L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.softDelete(7L, 5L))
            .isInstanceOf(CategoryProtectedException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void softDelete_migrates_expenses_to_user_default_then_deletes() {
        ExpenseCategory mine = ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 0);
        mine.setIdInternal(1L);
        ExpenseCategory def = ExpenseCategory.createUserDefault(7L, "其他", null, null, 100);
        def.setIdInternal(2L);
        when(categoryRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(mine));
        when(budgetRepository.existsByCategoryIdActive(1L)).thenReturn(false);
        when(categoryRepository.findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(7L))
            .thenReturn(Optional.of(def));
        when(expenseRepository.reassignCategory(7L, 1L, 2L)).thenReturn(3);
        when(categoryRepository.save(any(ExpenseCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(7L, 1L);

        verify(expenseRepository, times(1)).reassignCategory(7L, 1L, 2L);
        verify(categoryRepository, times(1)).save(any(ExpenseCategory.class));
        assertThat(mine.isDeleted()).isTrue();
    }
}