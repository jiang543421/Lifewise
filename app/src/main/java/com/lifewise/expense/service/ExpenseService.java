package com.lifewise.expense.service;

import com.lifewise.expense.domain.Expense;
import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.dto.ExpenseCreateRequest;
import com.lifewise.expense.dto.ExpenseUpdateRequest;
import com.lifewise.expense.dto.ExpenseView;
import com.lifewise.expense.event.payload.ExpenseCreatedPayload;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消费写服务（plan-03-expense §1.3 + §5.1）。
 *
 * <p>职责：CRUD + outbox 同事务 + 校验分类所有权/未归档。读操作也在此类（同事务传播）
 * 以减少 controller 注入的服务数量；{@link com.lifewise.expense.repository.ExpenseRepository}
 * 自身已过滤软删。
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
        expense = expenseRepository.save(expense);

        ExpenseCreatedPayload payload = new ExpenseCreatedPayload(
                expense.getId(),
                userId,
                expense.getCategoryId(),
                expense.getAmountCents(),
                expense.getCurrency(),
                expense.getOccurredAt());
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.EXPENSE_CREATED.eventType(),
                1,
                OffsetDateTime.now(clock),
                userId,
                "expense",
                expense.getId(),
                null,
                null,
                null,
                payload.toMap()));

        // C1: 同事务内评估预算阈值。dedupe 与 outbox 写库共享本方法 @Transactional，
        // 任一侧失败均整体回滚（不变量：业务写库 + outbox + 阈值事件三件套同步落库）。
        // BudgetEvaluator.evaluate 自身声明 Propagation.MANDATORY，强制加入调用方事务。
        budgetEvaluator.evaluate(userId, expense.getCategoryId(), expense.getOccurredAt());

        return ExpenseView.from(expense);
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
        return ExpenseView.from(expenseRepository.save(expense));
    }

    @Transactional
    public void softDelete(Long userId, Long expenseId) {
        Expense expense = loadOwnedExpense(userId, expenseId);
        expense.softDelete();
        expenseRepository.save(expense);
    }

    @Transactional
    public void restore(Long userId, Long expenseId) {
        // H2: 软删记录的 deleted_at 非空，原 loadOwnedExpense 用的
        // findByIdAndUserIdAndDeletedAtIsNull 会过滤掉，导致 restore 永远 404。
        // 必须用不带 deletedAt 过滤的 findByIdAndUserId 才能找回软删记录。
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));
        expense.restore();
        expenseRepository.save(expense);
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
}