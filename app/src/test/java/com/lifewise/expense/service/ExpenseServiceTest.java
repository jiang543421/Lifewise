package com.lifewise.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.expense.domain.Expense;
import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.domain.enums.PayMethod;
import com.lifewise.expense.dto.ExpenseCreateRequest;
import com.lifewise.expense.dto.ExpenseUpdateRequest;
import com.lifewise.expense.dto.ExpenseView;
import com.lifewise.expense.event.payload.ExpenseCreatedPayload;
import com.lifewise.expense.event.payload.ExpenseDeletedPayload;
import com.lifewise.expense.event.payload.ExpenseRestoredPayload;
import com.lifewise.expense.event.payload.ExpenseUpdatedPayload;
import com.lifewise.expense.repository.ExpenseRepository;
import com.lifewise.expense.service.exception.CategoryNotFoundException;
import com.lifewise.expense.service.exception.CategoryProtectedException;
import com.lifewise.expense.service.exception.ExpenseNotFoundException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ExpenseService 单元测试（plan-03-expense §5.1）。
 *
 * <p>BR 覆盖：BR-09 金额正数、跨用户访问、Outbox 同事务、occurred_at → local_date 同步。
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 8, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock ExpenseRepository expenseRepository;
    @Mock OutboxWriter outboxWriter;
    @Mock CategoryService categoryService;
    @Mock BudgetEvaluator budgetEvaluator;
    ExpenseService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        service = new ExpenseService(expenseRepository, outboxWriter,
                categoryService, budgetEvaluator, clock);
    }

    // ---------- create ----------

    @Test
    void create_persists_expense_and_emits_outbox_event() {
        Long userId = 7L;
        ExpenseCategory category = ExpenseCategory.createUserCategory(userId, "咖啡", null, null, null, 0);
        category.setIdInternal(11L);
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC);

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setIdInternal(100L);
            return e;
        });

        ExpenseView view = service.create(userId, new ExpenseCreateRequest(
                11L, 3500L, PayMethod.ALIPAY, occurredAt, "早咖啡", "CNY"), category);

        assertThat(view.id()).isEqualTo(100L);
        assertThat(view.amountCents()).isEqualTo(3500L);
        assertThat(view.localDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(view.payMethod()).isEqualTo(PayMethod.ALIPAY);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        EventEnvelope captured = env.getValue();
        assertThat(captured.eventType()).isEqualTo(EventType.EXPENSE_CREATED.eventType());
        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.aggregateType()).isEqualTo("expense");
        assertThat(captured.aggregateId()).isEqualTo(100L);
        assertThat(captured.payload()).containsEntry("expenseId", 100L);
        assertThat(captured.payload()).containsEntry("amountCents", 3500L);
    }

    @Test
    void create_rejects_archived_category() {
        ExpenseCategory archived = ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 0);
        archived.setIdInternal(11L);
        archived.archive();

        assertThatThrownBy(() ->
                service.create(7L, new ExpenseCreateRequest(11L, 100L, PayMethod.CASH,
                        FIXED_NOW, null, "CNY"), archived))
            .isInstanceOf(CategoryProtectedException.class);

        verify(expenseRepository, never()).save(any());
        verify(outboxWriter, never()).append(any());
    }

    @Test
    void create_throws_when_category_belongs_to_other_user() {
        ExpenseCategory other = ExpenseCategory.createUserCategory(99L, "x", null, null, null, 0);
        other.setIdInternal(11L);

        assertThatThrownBy(() ->
                service.create(7L, new ExpenseCreateRequest(11L, 100L, PayMethod.CASH,
                        FIXED_NOW, null, "CNY"), other))
            .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void create_rejects_non_positive_amount() {
        ExpenseCategory category = ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 0);
        category.setIdInternal(11L);

        // plan-03 review M2：金额非法 → ExpenseInvalidAmountException（而非 IllegalArgumentException）。
        assertThatThrownBy(() ->
                service.create(7L, new ExpenseCreateRequest(11L, 0L, PayMethod.CASH,
                        FIXED_NOW, null, "CNY"), category))
            .isInstanceOf(com.lifewise.expense.service.exception.ExpenseInvalidAmountException.class);
    }

    // ---------- C1: BudgetEvaluator 接入 ----------
    // 测试覆盖：create() 必须在 outbox append 之后调用 BudgetEvaluator.evaluate，
    // 传 userId / categoryId / occurredAt；同一事务内（不验证事务，由 Spring AOP 接管）。

    @Test
    void create_invokes_budget_evaluator_with_expense_context() {
        ExpenseCategory category = ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 0);
        category.setIdInternal(11L);
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC);

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setIdInternal(100L);
            return e;
        });

        service.create(7L, new ExpenseCreateRequest(11L, 3500L, PayMethod.ALIPAY,
                occurredAt, "早咖啡", "CNY"), category);

        verify(budgetEvaluator, times(1)).evaluate(7L, 11L, occurredAt);
    }

    // ---------- update ----------

    @Test
    void update_applies_partial_changes_and_preserves_ownership() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1),
                "UTC", 1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseView view = service.update(7L, 1L, new ExpenseUpdateRequest(
                null, 2000L, null, null, "更正"));

        assertThat(view.amountCents()).isEqualTo(2000L);
        assertThat(view.note()).isEqualTo("更正");
    }

    @Test
    void update_throws_for_cross_user_access() {
        Expense other = Expense.create(99L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        other.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(7L, 1L, new ExpenseUpdateRequest(null, 2000L, null, null, null)))
            .isInstanceOf(ExpenseNotFoundException.class);
    }

    @Test
    void update_syncs_local_date_when_occurred_at_changes() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1),
                "UTC", 1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime newTime = OffsetDateTime.of(2026, 8, 5, 10, 0, 0, 0, ZoneOffset.UTC);
        ExpenseView view = service.update(7L, 1L, new ExpenseUpdateRequest(null, null, null, newTime, null));

        assertThat(view.localDate()).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    // ---------- softDelete / restore ----------

    @Test
    void softDelete_marks_deleted_and_saves() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(7L, 1L);

        assertThat(existing.isDeleted()).isTrue();
        verify(expenseRepository, times(1)).save(existing);
    }

    @Test
    void restore_throws_for_unknown_expense() {
        when(expenseRepository.findByIdAndUserId(1L, 7L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(7L, 1L))
            .isInstanceOf(ExpenseNotFoundException.class);
    }

    @Test
    void restore_clears_deleted_at() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        existing.softDelete();
        // H2: restore 必须用 findByIdAndUserId（不带 deletedAt 过滤）才能找回软删记录
        when(expenseRepository.findByIdAndUserId(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.restore(7L, 1L);

        assertThat(existing.isDeleted()).isFalse();
        verify(expenseRepository, times(1)).save(existing);
    }

    // ---------- update categoryId 归属校验（C2）----------

    @Test
    void update_rejects_other_user_category() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1),
                "UTC", 1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        // 别人的分类
        ExpenseCategory othersCategory = ExpenseCategory.createUserCategory(99L, "x", null, null, null, 0);
        othersCategory.setIdInternal(99L);
        when(categoryService.loadOwnedCategory(7L, 99L)).thenReturn(othersCategory);

        assertThatThrownBy(() ->
                service.update(7L, 1L, new ExpenseUpdateRequest(99L, null, null, null, null)))
            .isInstanceOf(CategoryNotFoundException.class);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void update_rejects_archived_category() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1),
                "UTC", 1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        // 已归档分类（虽是本人，但归档后不可指派）
        ExpenseCategory archived = ExpenseCategory.createUserCategory(7L, "已归档", null, null, null, 0);
        archived.setIdInternal(11L);
        archived.archive();
        when(categoryService.loadOwnedCategory(7L, 11L)).thenReturn(archived);

        assertThatThrownBy(() ->
                service.update(7L, 1L, new ExpenseUpdateRequest(11L, null, null, null, null)))
            .isInstanceOf(CategoryProtectedException.class);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void restore_finds_soft_deleted_expense() {
        // H2 真实场景：模拟用户软删 → restore 真实路径
        Expense softDeleted = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1),
                "UTC", 1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        softDeleted.setIdInternal(1L);
        softDeleted.softDelete();
        // 关键：用不带 deletedAt 过滤的 finder
        when(expenseRepository.findByIdAndUserId(1L, 7L))
            .thenReturn(Optional.of(softDeleted));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.restore(7L, 1L);

        assertThat(softDeleted.isDeleted()).isFalse();
        assertThat(softDeleted.getDeletedAt()).isNull();
        verify(expenseRepository, times(1)).save(softDeleted);
        // 不应再调用 deletedAt 过滤的旧 finder
        verify(expenseRepository, never()).findByIdAndUserIdAndDeletedAtIsNull(any(), any());
    }

    // ---------- query ----------

    @Test
    void findById_returns_view() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));

        ExpenseView view = service.findById(7L, 1L);
        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.amountCents()).isEqualTo(1000L);
    }

    @Test
    void listInRange_returns_sorted_views() {
        Expense e1 = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        e1.setIdInternal(1L);
        Expense e2 = Expense.create(7L, 11L, LocalDate.of(2026, 8, 2), "UTC",
                2000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 2, 10, 0, 0, 0, ZoneOffset.UTC), null);
        e2.setIdInternal(2L);
        when(expenseRepository.findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByOccurredAtDesc(
                eq(7L), eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
            .thenReturn(List.of(e2, e1));

        List<ExpenseView> views = service.listInRange(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(views).hasSize(2);
        assertThat(views.get(0).id()).isEqualTo(2L);
    }

    @Test
    void create_payload_serializes_occurred_at_as_iso_string() {
        ExpenseCategory category = ExpenseCategory.createUserCategory(7L, "x", null, null, null, 0);
        category.setIdInternal(11L);
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC);

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setIdInternal(100L);
            return e;
        });

        service.create(7L, new ExpenseCreateRequest(11L, 3500L, PayMethod.ALIPAY,
                occurredAt, "note", "CNY"), category);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        ExpenseCreatedPayload payload = new ExpenseCreatedPayload(
                100L, 7L, 11L, 3500L, "CNY", occurredAt);
        assertThat(env.getValue().payload()).isEqualTo(payload.toMap());
    }

    // ---------- B-3: EXPENSE_UPDATED / EXPENSE_DELETED 事件发射（plan-03 Phase B）----------

    @Test
    void update_emits_expense_updated_outbox_event() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1),
                "UTC", 1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(7L, 1L, new ExpenseUpdateRequest(null, 2000L, null, null, "更正"));

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        EventEnvelope captured = env.getValue();
        assertThat(captured.eventType()).isEqualTo(EventType.EXPENSE_UPDATED.eventType());
        assertThat(captured.userId()).isEqualTo(7L);
        assertThat(captured.aggregateType()).isEqualTo("expense");
        assertThat(captured.aggregateId()).isEqualTo(1L);
        ExpenseUpdatedPayload expected = new ExpenseUpdatedPayload(
                1L, 7L, 11L, 2000L, "CNY",
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(captured.payload()).isEqualTo(expected.toMap());
    }

    @Test
    void softDelete_emits_expense_deleted_outbox_event() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(7L, 1L);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        EventEnvelope captured = env.getValue();
        assertThat(captured.eventType()).isEqualTo(EventType.EXPENSE_DELETED.eventType());
        assertThat(captured.userId()).isEqualTo(7L);
        assertThat(captured.aggregateId()).isEqualTo(1L);
        ExpenseDeletedPayload payload = new ExpenseDeletedPayload(
                1L, 7L, existing.getDeletedAt());
        assertThat(captured.payload()).isEqualTo(payload.toMap());
    }

    @Test
    void restore_emits_expense_restored_outbox_event() {
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        existing.softDelete();
        when(expenseRepository.findByIdAndUserId(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.restore(7L, 1L);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        EventEnvelope captured = env.getValue();
        // P0-1 修订：restore 走独立 EXPENSE_RESTORED 事件（不与 EXPENSE_UPDATED 复用），
        // 否则下游无法区分「修改字段」与「恢复软删记录」。
        assertThat(captured.eventType()).isEqualTo(EventType.EXPENSE_RESTORED.eventType());
        assertThat(captured.userId()).isEqualTo(7L);
        assertThat(captured.aggregateType()).isEqualTo("expense");
        assertThat(captured.aggregateId()).isEqualTo(1L);
        ExpenseRestoredPayload expected = new ExpenseRestoredPayload(
                1L, 7L, 11L, 1000L, "CNY",
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                FIXED_NOW);
        assertThat(captured.payload()).isEqualTo(expected.toMap());
    }

    // ---------- B-3 修订 2: BudgetEvaluator 接入（P0-2 修复）----------

    @Test
    void update_invokes_budget_evaluator() {
        // update 改 amountCents 直接影响分类周期累计，必须 evaluate
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1),
                "UTC", 1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(7L, 1L, new ExpenseUpdateRequest(null, 2000L, null, null, null));

        verify(budgetEvaluator, times(1)).evaluate(7L, 11L,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void restore_invokes_budget_evaluator() {
        // restore 让软删记录重新进入累计，必须 evaluate
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1),
                "UTC", 1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        existing.softDelete();
        when(expenseRepository.findByIdAndUserId(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.restore(7L, 1L);

        verify(budgetEvaluator, times(1)).evaluate(7L, 11L,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void softDelete_does_not_invoke_budget_evaluator() {
        // 软删只让累计下降；evaluator 仅在 pct ≥ threshold 时 emit，下降不触发新事件
        // 「回落复位」通知属 v1.1 范围
        Expense existing = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        existing.setIdInternal(1L);
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(1L, 7L))
            .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(7L, 1L);

        verify(budgetEvaluator, never()).evaluate(any(), any(), any());
    }
}
