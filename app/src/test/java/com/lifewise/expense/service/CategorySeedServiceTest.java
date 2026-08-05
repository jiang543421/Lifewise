package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.expense.repository.CategoryRepository;
import com.lifewise.shared.integration.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * CategorySeedService 单元测试（plan-03-expense BR-24 + review M8 + B-2 follow-up）。
 *
 * <p>v1.0 修订（ledger M8 fully Closed）：旧版 catch + re-query 实现已被 PostgreSQL UPSERT
 * + JdbcTemplate fallback 替代。本测试覆盖 4 路径：
 * <ol>
 *   <li>UPSERT 成功 1 行 → JDBC SELECT 拿 id → 返回 id</li>
 *   <li>UPSERT 抛 DataIntegrityViolationException（残余 race）→ JDBC SELECT 兜底 → 返回 id</li>
 *   <li>userId = null → 抛 IllegalArgumentException</li>
 *   <li>JDBC SELECT 拿不到 id（不变量违反）→ 抛 IllegalStateException</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CategorySeedServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock PlatformTransactionManager transactionManager;

    private CategorySeedService service;

    @BeforeEach
    void setUp() {
        // 让 TransactionTemplate 内部 getTransaction(...) 拿到 stub status，commit/rollback 走 mock
        // lenient: null_user_id_throws 在 null check 前不调 transactionManager
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new CategorySeedService(categoryRepository, jdbcTemplate, transactionManager);
    }

    @Test
    @DisplayName("UPSERT 成功 → JDBC SELECT 拿 id")
    void upsert_success_returns_id_via_jdbc_select() {
        when(categoryRepository.insertUserDefaultIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(42L);

        Long id = service.ensureUserDefault(7L);

        assertThat(id).isEqualTo(42L);
        verify(categoryRepository, times(1))
                .insertUserDefaultIfAbsent(7L, "其他", "📦", "#9CA3AF", 9999);
    }

    @Test
    @DisplayName("UPSERT 抛 DataIntegrityViolation → JDBC fallback 拿 id")
    void upsert_race_fallback_to_jdbc_select() {
        when(categoryRepository.insertUserDefaultIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new DataIntegrityViolationException("uq_expense_categories_user_default"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(99L);

        Long id = service.ensureUserDefault(7L);

        assertThat(id).isEqualTo(99L);
        // 不应再有人为重试 INSERT，由 JDBC SELECT 兜底
        verify(categoryRepository, times(1))
                .insertUserDefaultIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("userId = null → 抛 IllegalArgumentException")
    void null_user_id_throws() {
        assertThatThrownBy(() -> service.ensureUserDefault(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        verify(categoryRepository, never()).insertUserDefaultIfAbsent(anyLong(), anyString(),
                anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("JDBC SELECT 拿不到 id → 抛 IllegalStateException")
    void jdbc_select_empty_throws() {
        when(categoryRepository.insertUserDefaultIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenThrow(new EmptyResultDataAccessException("not found", 1));

        assertThatThrownBy(() -> service.ensureUserDefault(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user_default category missing");
    }

    @Test
    @DisplayName("UserRegisteredEvent 监听器透传到 ensureUserDefault")
    void event_listener_dispatches_to_ensureUserDefault() {
        when(categoryRepository.insertUserDefaultIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(42L);

        service.onUserRegistered(new UserRegisteredEvent(7L));

        verify(categoryRepository, times(1))
                .insertUserDefaultIfAbsent(7L, "其他", "📦", "#9CA3AF", 9999);
    }
}
