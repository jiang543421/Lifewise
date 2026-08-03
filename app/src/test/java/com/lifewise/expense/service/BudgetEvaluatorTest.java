package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.expense.domain.Budget;
import com.lifewise.expense.domain.enums.BudgetScope;
import com.lifewise.expense.event.payload.BudgetThresholdPayload;
import com.lifewise.expense.repository.BudgetRepository;
import com.lifewise.expense.repository.ExpenseRepository;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BudgetEvaluator 单元测试（plan-03-expense §5.4）。
 *
 * <p>核心场景：80% / 100% 触发、dedupe、静音、disabled、跨月、两个 scope。
 */
@ExtendWith(MockitoExtension.class)
class BudgetEvaluatorTest {

    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 8, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock BudgetRepository budgetRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock OutboxWriter outboxWriter;
    BudgetEvaluator evaluator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        evaluator = new BudgetEvaluator(budgetRepository, expenseRepository, outboxWriter, clock);
    }

    private static Budget categoryBudget(Long id, Long categoryId, Long amountCents,
                                          boolean notifyEnabled, LocalDate mutedUntil) {
        Budget b = Budget.create(7L, BudgetScope.CATEGORY, categoryId, 2026, 8,
                amountCents, "CNY", notifyEnabled);
        b.setIdInternal(id);
        if (mutedUntil != null) {
            b.mute(mutedUntil);
        }
        return b;
    }

    private static Budget totalBudget(Long id, Long amountCents) {
        Budget b = Budget.create(7L, BudgetScope.TOTAL, null, 2026, 8,
                amountCents, "CNY", true);
        b.setIdInternal(id);
        return b;
    }

    @Test
    void triggers_80_percent_threshold() {
        Budget b = categoryBudget(1L, 11L, 10000L, true, null);
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of(b));
        when(expenseRepository.sumInRangeByCategoryCents(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 11L))
            .thenReturn(8000L);

        evaluator.evaluate(7L, 11L, FIXED_NOW);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        EventEnvelope e = env.getValue();
        assertThat(e.eventType()).isEqualTo("budget.threshold");
        assertThat(e.payload().get("threshold")).isEqualTo(0.8);
        assertThat(e.payload().get("usedCents")).isEqualTo(8000L);
        assertThat(e.payload().get("totalCents")).isEqualTo(10000L);
    }

    @Test
    void triggers_100_percent_threshold() {
        Budget b = categoryBudget(1L, 11L, 10000L, true, null);
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of(b));
        when(expenseRepository.sumInRangeByCategoryCents(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 11L))
            .thenReturn(10000L);

        evaluator.evaluate(7L, 11L, FIXED_NOW);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(2)).append(env.capture());
        // 80% first (pct >= 0.8), then 100% (pct >= 1.0)
        List<Double> thresholds = env.getAllValues().stream()
                .map(e -> (Double) e.payload().get("threshold"))
                .sorted()
                .toList();
        assertThat(thresholds).containsExactly(0.8, 1.0);
    }

    @Test
    void does_not_trigger_below_80_percent() {
        Budget b = categoryBudget(1L, 11L, 10000L, true, null);
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of(b));
        when(expenseRepository.sumInRangeByCategoryCents(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 11L))
            .thenReturn(7500L);

        evaluator.evaluate(7L, 11L, FIXED_NOW);

        verify(outboxWriter, never()).append(any());
    }

    @Test
    void does_not_duplicate_80_percent_in_same_period() {
        Budget b = categoryBudget(1L, 11L, 10000L, true, null);
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of(b));
        when(expenseRepository.sumInRangeByCategoryCents(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 11L))
            .thenReturn(8500L);

        evaluator.evaluate(7L, 11L, FIXED_NOW);
        evaluator.evaluate(7L, 11L, FIXED_NOW);

        verify(outboxWriter, times(1)).append(any());
    }

    @Test
    void skips_muted_budget() {
        Budget b = categoryBudget(1L, 11L, 10000L, true, LocalDate.of(2026, 8, 20));
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of(b));

        evaluator.evaluate(7L, 11L, FIXED_NOW);

        verify(outboxWriter, never()).append(any());
        verify(expenseRepository, never()).sumInRangeByCategoryCents(anyLong(), any(), any(), anyLong());
    }

    @Test
    void skips_disabled_notify_budget() {
        Budget b = categoryBudget(1L, 11L, 10000L, false, null);
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of(b));

        evaluator.evaluate(7L, 11L, FIXED_NOW);

        verify(outboxWriter, never()).append(any());
    }

    @Test
    void handles_total_scope_using_sumInRange() {
        Budget b = totalBudget(1L, 50000L);
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of(b));
        when(expenseRepository.sumInRangeCents(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
            .thenReturn(40000L);

        evaluator.evaluate(7L, 11L, FIXED_NOW);

        verify(expenseRepository, times(1)).sumInRangeCents(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        verify(expenseRepository, never()).sumInRangeByCategoryCents(anyLong(), any(), any(), anyLong());
        verify(outboxWriter, times(1)).append(any());
    }

    @Test
    void no_budgets_for_period_emits_nothing() {
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of());

        evaluator.evaluate(7L, 11L, FIXED_NOW);

        verify(outboxWriter, never()).append(any());
    }

    @Test
    void payload_matches_BudgetThresholdPayload_toMap() {
        Budget b = categoryBudget(1L, 11L, 10000L, true, null);
        when(budgetRepository.findActiveForEvaluation(7L, 2026, 8, 11L))
            .thenReturn(List.of(b));
        when(expenseRepository.sumInRangeByCategoryCents(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 11L))
            .thenReturn(8000L);

        evaluator.evaluate(7L, 11L, FIXED_NOW);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        BudgetThresholdPayload payload = new BudgetThresholdPayload(
                7L, 1L, 0.8, 8000L, 10000L);
        assertThat(env.getValue().payload()).isEqualTo(payload.toMap());
    }
}