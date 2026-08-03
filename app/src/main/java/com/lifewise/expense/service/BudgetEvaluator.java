package com.lifewise.expense.service;

import com.lifewise.expense.domain.Budget;
import com.lifewise.expense.domain.enums.BudgetScope;
import com.lifewise.expense.event.payload.BudgetThresholdPayload;
import com.lifewise.expense.repository.BudgetRepository;
import com.lifewise.expense.repository.ExpenseRepository;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预算评估器（plan-03-expense §1.3 + §5.4）。
 *
 * <p>职责：消费发生后评估同 user+同月份+覆盖分类的所有预算，计算累计（cents 长整型），
 * 触发 80% / 100% 阈值。in-memory dedupe 按 {@code (budgetId, periodYearMonth, threshold)}
 * 去重，保证同周期内每个预算每个阈值最多发一次 outbox 事件。
 *
 * <p>注意：dedupe 是进程内 Map，集群部署时需要在 DB 增加 {@code budgets.last_threshold_*}
 * 列（v1.1 评估）。当前单机部署足够。
 */
@Component
public class BudgetEvaluator {

    private static final double THRESHOLD_80 = 0.8;
    private static final double THRESHOLD_100 = 1.0;

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    private final Map<String, Boolean> sentThresholds = new ConcurrentHashMap<>();

    public BudgetEvaluator(BudgetRepository budgetRepository,
                            ExpenseRepository expenseRepository,
                            OutboxWriter outboxWriter,
                            Clock clock) {
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /**
     * 评估指定用户在指定月份的预算触发。
     *
     * <p><b>事务契约</b>：必须在调用方事务内运行（{@link Propagation#MANDATORY}）。
     * 当前调用方 {@code ExpenseService.create} 处于 {@code @Transactional}，
     * 与 outbox 写库共享同一事务，确保「业务写库 + outbox + 阈值事件」三件套同步落库或整体回滚。
     * 从非事务上下文调用会抛 {@code IllegalTransactionStateException}。
     *
     * <p><b>dedupe 已知限制</b>：{@code checkAndEmit} 用「check → append → mark」顺序，
     * append 失败时 {@code remove(dedupeKey)} 解除污染。但若 append 成功且
     * 调用方事务在 evaluate 返回后被外部 hook 二次抛异常回滚，in-memory {@code sentThresholds}
     * 不会回滚，导致同 {@code budgetId/period/threshold} 在进程生命周期内不再重发。
     * 当前 {@code ExpenseService.create} 在 evaluate 之后无可能失败的步骤，故实际不触发；
     * 未来若 evaluate 之后增加 hook / 二次校验，需重新评估 dedupe 策略。
     * 临时缓解：依赖运维每日重启进程（dedupe 随进程销毁）。
     *
     * @param userId      触发该评估的用户（通常与 expense.userId 一致）
     * @param categoryId  触发分类（expense.categoryId）
     * @param occurredAt  消费发生时间（用于推导 period）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(Long userId, Long categoryId, OffsetDateTime occurredAt) {
        LocalDate today = LocalDate.now(clock);
        YearMonth period = YearMonth.from(occurredAt.toLocalDate());
        int year = period.getYear();
        int month = period.getMonthValue();

        List<Budget> candidates = budgetRepository.findActiveForEvaluation(
                userId, year, month, categoryId);
        if (candidates.isEmpty()) {
            return;
        }

        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        for (Budget budget : candidates) {
            if (!budget.isNotifyEnabled()) {
                continue;
            }
            if (budget.isMuted(today)) {
                continue;
            }

            long usedCents = (budget.getScope() == BudgetScope.TOTAL)
                    ? expenseRepository.sumInRangeCents(userId, periodStart, periodEnd)
                    : expenseRepository.sumInRangeByCategoryCents(
                            userId, periodStart, periodEnd, budget.getCategoryId());
            long totalCents = budget.getAmountCents();
            double pct = (double) usedCents / (double) totalCents;

            checkAndEmit(budget, year, month, usedCents, totalCents, pct, THRESHOLD_80);
            checkAndEmit(budget, year, month, usedCents, totalCents, pct, THRESHOLD_100);
        }
    }

    private void checkAndEmit(Budget budget, int year, int month,
                               long usedCents, long totalCents, double pct,
                               double threshold) {
        if (pct < threshold) {
            return;
        }
        String dedupeKey = budget.getId() + ":" + year + "-" + month + ":" + threshold;
        // H7: 改为「check → append → mark」顺序；append 失败时 remove(dedupeKey)
        // 解除污染，避免阈值事件随事务回滚而永久丢失。
        // TOCTOU 窗口在单用户串行 + 同事务场景下不触发（ExpenseService.create 单线程入口）。
        // 未来若 EventConsumer 异步消费 expense.created，需重新审视。
        if (sentThresholds.containsKey(dedupeKey)) {
            return;
        }
        BudgetThresholdPayload payload = new BudgetThresholdPayload(
                budget.getUserId(), budget.getId(), threshold, usedCents, totalCents);
        try {
            outboxWriter.append(new EventEnvelope(
                    UUID.randomUUID(),
                    EventType.BUDGET_THRESHOLD.eventType(),
                    1,
                    OffsetDateTime.now(clock),
                    budget.getUserId(),
                    "budget",
                    budget.getId(),
                    null, null, null,
                    payload.toMap()));
            sentThresholds.put(dedupeKey, Boolean.TRUE);
        } catch (RuntimeException ex) {
            sentThresholds.remove(dedupeKey);
            throw ex;
        }
    }
}