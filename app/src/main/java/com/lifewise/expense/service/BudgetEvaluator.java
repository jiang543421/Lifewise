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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p><b>commit #7（plan-03 review MEDIUM）</b>：dedupe map 从 {@code ConcurrentHashMap}
 * 切换为 access-order {@code LinkedHashMap} + {@code Collections.synchronizedMap}，
 * 通过 {@code removeEldestEntry} 实现 LRU 淘汰（默认 maxSize=1024）。这避免
 * long-running 进程下 dedupe map 内存无界增长（1 user × 数十 budget × 12 月 × 2
 * threshold ≈ 1200 keys，1024 足够）。淘汰已发 key 不影响正确性：dedupe key
 * 含 {@code year-month}，过期 period key 淘汰等于自动解除 dedupe。
 *
 * <p>注意：dedupe 是进程内 Map，集群部署时需要在 DB 增加 {@code budgets.last_threshold_*}
 * 列（v1.1 评估）。当前单机部署足够。
 */
@Component
public class BudgetEvaluator {

    private static final int THRESHOLD_80_PCT = 80;
    private static final int THRESHOLD_100_PCT = 100;

    /** 生产默认值。1 user × 数十 budget × 12 月 × 2 threshold ≈ 1200 keys，1024 足够。
     *  设为 instance field（非 static final）以便 test 注入小值。 */
    static final int DEFAULT_SENT_THRESHOLDS_MAX_SIZE = 1024;

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    /** 必须先于 {@link #sentThresholds} 声明（实例字段按声明顺序初始化）。 */
    private final int sentThresholdsMaxSize;

    private final Map<String, Boolean> sentThresholds =
            Collections.synchronizedMap(
                    new LinkedHashMap<String, Boolean>(128, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                            return size() > sentThresholdsMaxSize;
                        }
                    });

    /** Spring 自动装配用（容器注入 Clock）。多构造器场景必须显式 @Autowired。 */
    @Autowired
    public BudgetEvaluator(BudgetRepository budgetRepository,
                            ExpenseRepository expenseRepository,
                            OutboxWriter outboxWriter,
                            Clock clock) {
        this(budgetRepository, expenseRepository, outboxWriter, clock,
                DEFAULT_SENT_THRESHOLDS_MAX_SIZE);
    }

    /** Test 注入用（package-private）。允许覆盖 maxSize 以验证 LRU 淘汰行为。 */
    BudgetEvaluator(BudgetRepository budgetRepository,
                    ExpenseRepository expenseRepository,
                    OutboxWriter outboxWriter,
                    Clock clock,
                    int sentThresholdsMaxSize) {
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
        this.sentThresholdsMaxSize = sentThresholdsMaxSize;
    }

    /** 仅供 test 观察 LRU 内部状态（package-private）。 */
    int sentThresholdsSize() {
        return sentThresholds.size();
    }

    /**
     * 评估指定用户在指定月份的预算触发。
     *
     * <p><b>事务契约</b>：必须在调用方事务内运行（{@link Propagation#MANDATORY}）。
     * 当前调用方为 {@code ExpenseService.create / update / restore}（均处于
     * {@code @Transactional}），与 outbox 写库共享同一事务，确保「业务写库 + outbox
     * + 阈值事件」三件套同步落库或整体回滚。从非事务上下文调用会抛
     * {@code IllegalTransactionStateException}。
     *
     * <p><b>dedupe 已知限制（H3，方案 B 现状）</b>：当前实现走「方案 B：
     * 进程内 LRU Map」（同 transaction 内 in-memory dedupe；详
     * BudgetEvaluator commit #7 message 与 commit #9 LRU 改造），未采用
     * review notes (plan-03-expense-review-notes §H3) 推荐的「方案 A：
     * budget_notifications 表」。两个真实约束：
     * <ul>
     *   <li><b>事务回滚不对应 Map 回滚</b>：{@code checkAndEmit} 用「mark → append → unmark-on-fail」
     *       顺序，append 失败时 {@code remove(dedupeKey)} 解除污染；但若 append 成功且
     *       调用方事务在 evaluate 返回后被外部 hook 二次抛异常回滚，in-memory sentThresholds
     *       不会回滚，导致同 {@code budgetId/period/threshold} 在进程生命周期内不再重发。
     *       当前 ExpenseService 三个调用方在 evaluate 之后均无可能失败的步骤，故实际不触发；
     *       未来若 evaluate 之后增加 hook / 二次校验，需重新评估。</li>
     *   <li><b>进程重启 = dedupe 清空</b>：sentThresholds 是 in-memory 状态，重启即丢。
     *       这是「每天保证已达阈值的预算会在下次消费时再发一次通知」的机制，不是缓解措施。
     *       v1.0 单机部署接受这一行为；集群或要求严格一次通知的场景必须切方案 A
     *       （DB UNIQUE (budget_id, threshold_pct, period_year_month)）。</li>
     * </ul>
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
            // plan-03 review M4：payload 改 int thresholdPct，evaluator 内部也按整数百分比比较。
            // 公式：usedCents * 100 / totalCents（长整型 × 100 不溢出：9_999_999_999 × 100
            // < 2^63）。ceil 防止「恰好 79.999%」误判为 80% 触发。
            int pctX100 = (int) ((usedCents * 100L + totalCents - 1) / totalCents);

            checkAndEmit(budget, year, month, usedCents, totalCents, pctX100, THRESHOLD_80_PCT);
            checkAndEmit(budget, year, month, usedCents, totalCents, pctX100, THRESHOLD_100_PCT);
        }
    }

    private void checkAndEmit(Budget budget, int year, int month,
                               long usedCents, long totalCents, int pctX100,
                               int thresholdPct) {
        if (pctX100 < thresholdPct) {
            return;
        }
        String dedupeKey = budget.getId() + ":" + year + "-" + month + ":" + thresholdPct;
        // H7: 改为「mark → append → unmark-on-fail」顺序；append 失败时 remove(dedupeKey)
        // 解除污染，避免阈值事件随事务回滚而永久丢失。
        // commit #7：用 putIfAbsent 替代 containsKey+put，三合一：
        //   1. atomic — 消除 containsKey+put 之间的 TOCTOU 窗口（commit #7 测试发现）
        //   2. 真 LRU — putIfAbsent 对已存在 key 触发 access reordering
        //      （containsKey 不会！accessOrder=true 下必须是 get/put/putIfAbsent）
        //   3. 同步语义 — Collections.synchronizedMap.putIfAbsent 一次原子调用
        // TOCTOU 窗口在单用户串行 + 同事务场景下不触发（ExpenseService.create 单线程入口）。
        // 未来若 EventConsumer 异步消费 expense.created，需重新审视。
        Boolean prev = sentThresholds.putIfAbsent(dedupeKey, Boolean.TRUE);
        if (prev != null) {
            return;  // 已发过，dedup
        }
        BudgetThresholdPayload payload = new BudgetThresholdPayload(
                budget.getUserId(), budget.getId(), thresholdPct, usedCents, totalCents);
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
        } catch (RuntimeException ex) {
            sentThresholds.remove(dedupeKey);  // unmark：允许重试
            throw ex;
        }
    }
}