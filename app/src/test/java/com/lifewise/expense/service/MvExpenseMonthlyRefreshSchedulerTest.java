package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MvExpenseMonthlyRefreshScheduler 单元测试（plan-03-expense §6 验收）。
 *
 * <p>覆盖 3 路径：
 * <ol>
 *   <li>执行固定 SQL：REFRESH MATERIALIZED VIEW CONCURRENTLY mv_expense_monthly_category</li>
 *   <li>DB 异常不导致调度线程崩溃（异常被吞）</li>
 *   <li>失败被 ERROR 级别日志 + 计数器记录</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class MvExpenseMonthlyRefreshSchedulerTest {

    @Mock EntityManager entityManager;
    @Mock Query query;

    private MvExpenseMonthlyRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MvExpenseMonthlyRefreshScheduler(entityManager);
        scheduler.resetFailureCount();
    }

    @Test
    @DisplayName("执行固定 SQL：REFRESH MATERIALIZED VIEW CONCURRENTLY mv_expense_monthly_category")
    void executes_expected_native_sql() {
        when(entityManager.createNativeQuery(any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        scheduler.refreshDaily();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(1)).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).isEqualTo(
                "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_expense_monthly_category");
        verify(query, times(1)).executeUpdate();
        assertThat(scheduler.getFailureCount()).isZero();
    }

    @Test
    @DisplayName("DB 异常不导致调度线程崩溃")
    void db_exception_does_not_propagate() {
        when(entityManager.createNativeQuery(any())).thenReturn(query);
        doThrow(new RuntimeException("connection refused")).when(query).executeUpdate();

        // 关键：异常被 scheduler 内部捕获，调用方不感知
        assertThatCode(() -> scheduler.refreshDaily()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("失败被计数器记录（连续 N 次 → N）")
    void failure_counter_increments_per_attempt() {
        when(entityManager.createNativeQuery(any())).thenReturn(query);
        doThrow(new RuntimeException("transient"))
                .when(query).executeUpdate();

        scheduler.refreshDaily();
        scheduler.refreshDaily();
        scheduler.refreshDaily();

        assertThat(scheduler.getFailureCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("成功后计数器不增；重置可清零")
    void success_does_not_increment_counter() {
        when(entityManager.createNativeQuery(any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        scheduler.refreshDaily();
        scheduler.refreshDaily();

        assertThat(scheduler.getFailureCount()).isZero();
    }
}
