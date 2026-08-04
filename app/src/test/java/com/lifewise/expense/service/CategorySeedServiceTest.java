package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.repository.CategoryRepository;
import com.lifewise.shared.integration.event.UserRegisteredEvent;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * CategorySeedService 单元测试（plan-03-expense BR-24 + review M8）。
 *
 * <p>覆盖 4 路径：
 * <ol>
 *   <li>已存在默认分类时不重复创建</li>
 *   <li>不存在时创建「其他」</li>
 *   <li>并发插入触发 DataIntegrityViolationException，重查存在后不抛异常</li>
 *   <li>重查仍不存在时重新抛出（不能静默吞掉真问题）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CategorySeedServiceTest {

    @Mock CategoryRepository categoryRepository;

    private CategorySeedService service;

    @BeforeEach
    void setUp() {
        service = new CategorySeedService(categoryRepository);
    }

    @Test
    @DisplayName("已存在默认分类 → 不重复创建")
    void no_op_when_default_exists() {
        ExpenseCategory existing = ExpenseCategory.createUserDefault(7L, "其他", "📦", "#9CA3AF", 9999);
        existing.setIdInternal(99L);
        when(categoryRepository.findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(7L))
                .thenReturn(Optional.of(existing));

        service.ensureUserDefault(7L);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("不存在 → 创建「其他」默认分类")
    void creates_default_other() {
        when(categoryRepository.findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(7L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());  // 第二次（failure 后）查询也返回空 → 抛出
        when(categoryRepository.save(any(ExpenseCategory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.ensureUserDefault(7L);

        ArgumentCaptor<ExpenseCategory> captor = ArgumentCaptor.forClass(ExpenseCategory.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        ExpenseCategory saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getName()).isEqualTo("其他");
        assertThat(saved.isUserDefault()).isTrue();
    }

    @Test
    @DisplayName("并发：unique violation 后重查存在 → 不抛异常")
    void concurrent_insert_recovered_via_recheck() {
        ExpenseCategory winner = ExpenseCategory.createUserDefault(7L, "其他", "📦", "#9CA3AF", 9999);
        winner.setIdInternal(99L);
        // 第一次空，save 抛 unique violation，第二次重查存在
        when(categoryRepository.findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(7L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(categoryRepository.save(any(ExpenseCategory.class)))
                .thenThrow(new DataIntegrityViolationException("uq_expense_categories_user_default"));

        service.ensureUserDefault(7L);  // 不应抛

        verify(categoryRepository, times(2))
                .findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(7L);
    }

    @Test
    @DisplayName("并发：unique violation 后重查仍不存在 → 重新抛出")
    void rethrows_when_recheck_also_empty() {
        when(categoryRepository.findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(7L))
                .thenReturn(Optional.empty());  // 两次都空
        when(categoryRepository.save(any(ExpenseCategory.class)))
                .thenThrow(new DataIntegrityViolationException("real DB error"));

        assertThatThrownBy(() -> service.ensureUserDefault(7L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("UserRegisteredEvent 监听器透传到 ensureUserDefault")
    void event_listener_dispatches_to_ensureUserDefault() {
        ExpenseCategory existing = ExpenseCategory.createUserDefault(7L, "其他", "📦", "#9CA3AF", 9999);
        when(categoryRepository.findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(7L))
                .thenReturn(Optional.of(existing));

        service.onUserRegistered(new UserRegisteredEvent(7L));

        verify(categoryRepository, never()).save(any());
    }
}
