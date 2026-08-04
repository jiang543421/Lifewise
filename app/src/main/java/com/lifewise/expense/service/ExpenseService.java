package com.lifewise.expense.service;

import com.lifewise.expense.domain.Expense;
import com.lifewise.expense.domain.ExpenseCategory;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消费写服务（plan-03-expense §1.3 + §5.1）。
 *
 * <p>职责：CRUD + outbox 同事务 + 校验分类所有权/未归档 + 同事务触发 BudgetEvaluator。
 * 读操作也在此类（同事务传播）以减少 controller 注入的服务数量；
 * {@link com.lifewise.expense.repository.ExpenseRepository} 自身已过滤软删。
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final OutboxWriter outboxWriter;
    private final CategoryService categoryService;
    private final BudgetEvaluator budgetEvaluator;
    private final Clock clock;

    public ExpenseService(ExpenseRepository expenseRepository,
                           OutboxWriter outboxWriter,
                           CategoryService categoryService,
                           BudgetEvaluator budgetEvaluator,
                           Clock clock) {
        this.expenseRepository = expenseRepository;
        this.outboxWriter = outboxWriter;
        this.categoryService = categoryService;
        this.budgetEvaluator = budgetEvaluator;
        this.clock = clock;
    }

    /**
     * 创建消费。{@code category} 由 controller 在调用本方法前通过
     * {@link CategoryService#loadOwnedCategory(Long, Long)} 取得，保证同事务 + 所有权校验。
     */
    @Transactional
    public ExpenseView create(Long userId, ExpenseCreateRequest req, ExpenseCategory category) {
        validateCategory(category, userId);

        LocalDate localDate = req.occurredAt().toLocalDate();
        Expense expense = Expense.create(
                userId,
                category.getId(),
                localDate,
                "UTC",
                req.amountCents(),
                req.currency() == null ? "CNY" : req.currency(),
                req.payMethod(),
                req.occurredAt(),
                req.note());
        Expense saved = expenseRepository.save(expense);

        OffsetDateTime eventAt = OffsetDateTime.now(clock);
        appendExpenseEvent(EventType.EXPENSE_CREATED, userId, saved.getId(),
                new ExpenseCreatedPayload(
                        saved.getId(),
                        userId,
                        saved.getCategoryId(),
                        saved.getAmountCents(),
                        saved.getCurrency(),
                        saved.getOccurredAt()).toMap(),
                eventAt);

        // C1: 同事务内评估预算阈值。BudgetEvaluator.evaluate 自身声明 Propagation.MANDATORY，
        // 强制加入调用方事务；任一侧失败均整体回滚（不变量：业务写库 + outbox + 阈值事件三件套同步落库）。
        budgetEvaluator.evaluate(userId, saved.getCategoryId(), saved.getOccurredAt());

        return ExpenseView.from(saved);
    }

    @Transactional
    public ExpenseView update(Long userId, Long expenseId, ExpenseUpdateRequest req) {
        Expense expense = loadOwnedExpense(userId, expenseId);
        // C2: 当请求里改了 categoryId，必须先校验归属 + 未归档，
        // 否则攻击者可 PUT 别人的分类 ID → stats 视图 JOIN 泄露他人分类名。
        if (req.categoryId() != null) {
            ExpenseCategory category = categoryService.loadOwnedCategory(userId, req.categoryId());
            validateCategory(category, userId);
        }
        expense.applyUpdate(req.categoryId(), req.amountCents(),
                req.payMethod(), req.occurredAt(), req.note());
        Expense saved = expenseRepository.save(expense);

        OffsetDateTime eventAt = OffsetDateTime.now(clock);
        appendExpenseEvent(EventType.EXPENSE_UPDATED, userId, saved.getId(),
                new ExpenseUpdatedPayload(
                        saved.getId(),
                        userId,
                        saved.getCategoryId(),
                        saved.getAmountCents(),
                        saved.getCurrency(),
                        saved.getOccurredAt()).toMap(),
                eventAt);

        // P0-2 修订：update 必须调 BudgetEvaluator。amountCents/categoryId/occurredAt
        // 任一变更都直接影响分类周期累计（如 100 元改 10000 元、跨分类迁移、跨日期迁移），
        // 不评估会漏报阈值事件。dedupe 由 evaluator 内部按 (budgetId, period, threshold) 保证。
        budgetEvaluator.evaluate(userId, saved.getCategoryId(), saved.getOccurredAt());

        return ExpenseView.from(saved);
    }

    @Transactional
    public void softDelete(Long userId, Long expenseId) {
        Expense expense = loadOwnedExpense(userId, expenseId);
        expense.softDelete();
        Expense saved = expenseRepository.save(expense);

        OffsetDateTime eventAt = OffsetDateTime.now(clock);
        // B-3: payload.deletedAt 来自域（BaseEntity.softDelete 用 OffsetDateTime.now() 写入）；
        // envelope.occurredAt 用 service clock 标识业务发生时间。两者可能差几毫秒，是设计上有意分离。
        appendExpenseEvent(EventType.EXPENSE_DELETED, userId, saved.getId(),
                new ExpenseDeletedPayload(
                        saved.getId(),
                        userId,
                        saved.getDeletedAt()).toMap(),
                eventAt);

        // P0-2 修订：softDelete 不调 BudgetEvaluator。软删让分类周期累计下降，
        // evaluator 仅在 pct ≥ threshold 时 emit，下降不会触发新事件；
        // 「回落复位」通知属 v1.1 范围（evaluator 当前无 reset 状态）。
    }

    @Transactional
    public void restore(Long userId, Long expenseId) {
        // H2: 软删记录的 deleted_at 非空，原 loadOwnedExpense 用的
        // findByIdAndUserIdAndDeletedAtIsNull 会过滤掉，导致 restore 永远 404。
        // 必须用不带 deletedAt 过滤的 findByIdAndUserId 才能找回软删记录。
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));
        expense.restore();
        Expense saved = expenseRepository.save(expense);

        OffsetDateTime eventAt = OffsetDateTime.now(clock);
        // P0-1 修订：restore 使用独立 EXPENSE_RESTORED 事件（不与 EXPENSE_UPDATED 复用）——
        // 二者对下游 ai/Stats 投影语义不同（restore 需重加入累计，update 仅修正）。
        appendExpenseEvent(EventType.EXPENSE_RESTORED, userId, saved.getId(),
                new ExpenseRestoredPayload(
                        saved.getId(),
                        userId,
                        saved.getCategoryId(),
                        saved.getAmountCents(),
                        saved.getCurrency(),
                        saved.getOccurredAt(),
                        eventAt).toMap(),
                eventAt);

        // P0-2 修订：restore 必须调 BudgetEvaluator。恢复后分类周期累计上升（之前软删被排除），
        // 可能重新触阈；同 update 一样依赖 evaluator dedupe 防重复。
        budgetEvaluator.evaluate(userId, saved.getCategoryId(), saved.getOccurredAt());
    }

    @Transactional(readOnly = true)
    public ExpenseView findById(Long userId, Long expenseId) {
        return ExpenseView.from(loadOwnedExpense(userId, expenseId));
    }

    @Transactional(readOnly = true)
    public List<ExpenseView> listInRange(Long userId, LocalDate from, LocalDate to) {
        return expenseRepository
                .findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByOccurredAtDesc(userId, from, to)
                .stream()
                .map(ExpenseView::from)
                .toList();
    }

    // ---------- internals ----------

    private void validateCategory(ExpenseCategory category, Long userId) {
        if (category.isArchived()) {
            throw new CategoryProtectedException(category.getId());
        }
        if (!category.isSystem() && !category.isOwnedBy(userId)) {
            throw new CategoryNotFoundException(category.getId());
        }
    }

    private Expense loadOwnedExpense(Long userId, Long expenseId) {
        return expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(expenseId, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));
    }

    /**
     * DRY helper：把消费类 outbox 事件的样板收口（4 处调用统一为 2 行）。
     * envelope aggregateType 固定为 "expense"，aggregateId 为 expenseId；
     * null 三个尾字段（correlationId/tenantId/sourceIp）当前 v1.0 不使用，留口 v1.1 接入 Outbox trace。
     */
    private void appendExpenseEvent(EventType type, Long userId, Long expenseId,
                                    Map<String, Object> payload, OffsetDateTime eventAt) {
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                type.eventType(),
                1,
                eventAt,
                userId,
                "expense",
                expenseId,
                null,
                null,
                null,
                payload));
    }
}
