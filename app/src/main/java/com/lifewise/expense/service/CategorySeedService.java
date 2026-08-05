package com.lifewise.expense.service;

import com.lifewise.expense.repository.CategoryRepository;
import com.lifewise.shared.integration.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 首次注册预置「其他」分类（plan-03-expense §1.3 + BR-24）。
 *
 * <p>在注册事务内同步通过 Spring {@link EventListener} 触发：
 * <ul>
 *   <li>AuthService.register 提交 {@code UserRegisteredPayload} 事件</li>
 *   <li>本类监听后为该用户创建 {@code is_user_default = TRUE} 的「其他」分类</li>
 * </ul>
 *
 * <p><b>事务边界（v1.0 B-2 follow-up）</b>：本方法体走
 * {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW} 独立事务，<b>不</b>与
 * {@code AuthService.register} 共享。理由：v1.0 旧共享事务设计在 10 线程并发下被
 * IT 逮到 Hibernate session pollution —— INSERT 失败导致事务 mark rollback-only，
 * 任何后续 SELECT 在同一事务内被 PG 拒绝（{@code SQL state 25P02 IN_FAILED_SQL_TRANSACTION}）。
 * 独立事务消除了这个传染路径：INSERT 失败只回滚自己事务，SELECT 走全新 connection。
 *
 * <p><b>并发安全（plan-03 review M8 + B-2 follow-up）</b>：使用 PostgreSQL 原生
 * {@code INSERT ... ON CONFLICT DO NOTHING}（{@code CategoryRepository.insertUserDefaultIfAbsent}），
 * 把并发 race 收敛到 DB 层。9 线程同时尝试 INSERT 同一行时，DB 层只成功 1 行，其余
 * 静默忽略；后续统一 SELECT 拿回真实 id。
 *
 * <p><b>残余 race 兜底</b>：PG 在多约束场景下，{@code ON CONFLICT} 仅匹配首个抛出的约束。
 * 9 线程并发时可能撞 {@code uq_expense_categories_user_name}（V37 §2.5）而非被
 * ON CONFLICT 配的 {@code uq_expense_categories_user_default}（V37 §2.6），此时 INSERT
 * 抛 {@link DataIntegrityViolationException}。本方法以纯 JDBC SELECT 拿 id —— 绕过
 * Hibernate 持久化上下文（不触发 auto-flush 的 null id 断言），彻底隔离污染。
 *
 * <p>注：当前实现仅依赖 Spring 进程内事件。v1.1 若引入集群部署，需切换为
 * 监听 outbox {@code auth.user.registered} 事件，由共享的 OutboxWorker 串行消费。
 */
@Service
public class CategorySeedService {

    private static final Logger LOG = LoggerFactory.getLogger(CategorySeedService.class);

    /** v1.0 默认分类固定 5-元组（BR-24）。v1.1+ 引入可配置化。 */
    private static final String DEFAULT_NAME = "其他";
    private static final String DEFAULT_ICON = "📦";
    private static final String DEFAULT_COLOR = "#9CA3AF";
    private static final int DEFAULT_SORT_ORDER = 9999;

    private final CategoryRepository categoryRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public CategorySeedService(CategoryRepository categoryRepository,
                               JdbcTemplate jdbcTemplate,
                               PlatformTransactionManager transactionManager) {
        this.categoryRepository = categoryRepository;
        this.jdbcTemplate = jdbcTemplate;
        // REQUIRES_NEW：独立事务，避免 INSERT 失败污染 caller 事务（SQL state 25P02）
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 确保 userId 拥有「其他」分类（{@code is_user_default = TRUE}）。
     * 已有则跳过；无则创建。返回默认分类 id（供调用方 cache / record）。
     *
     * <p>并发安全：PG ON CONFLICT DO NOTHING 兜住主路径；残余 race 撞其它约束时
     * fallback 走 JdbcTemplate SELECT（独立事务，不受 INSERT 失败影响）。
     */
    public Long ensureUserDefault(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }

        try {
            txTemplate.executeWithoutResult(status ->
                    categoryRepository.insertUserDefaultIfAbsent(
                            userId, DEFAULT_NAME, DEFAULT_ICON, DEFAULT_COLOR, DEFAULT_SORT_ORDER));
            LOG.info("[expense] user_default category seeded userId={}", userId);
        } catch (DataIntegrityViolationException ex) {
            // 残余 race: 9 线程撞 ON CONFLICT 没匹配到的约束（如 uq_expense_categories_user_name）。
            // 该 REQUIRES_NEW 事务已 rollback，但下游 SELECT 走全新独立事务不受影响。
            LOG.info("[expense] user_default insert race fallback for userId={} cause={}",
                    userId, ex.getMostSpecificCause().getClass().getSimpleName());
        }

        // 独立事务 SELECT —— 绕开 Hibernate 持久化上下文，零 auto-flush 风险。
        // 即使 INSERT 失败导致前一个事务 rollback-only，这条 SELECT 走全新 connection。
        Long id;
        try {
            id = txTemplate.execute(status -> jdbcTemplate.queryForObject(
                    "SELECT id FROM expense_categories"
                            + " WHERE user_id = ? AND is_user_default = TRUE AND deleted_at IS NULL",
                    Long.class, userId));
        } catch (EmptyResultDataAccessException ex) {
            // 不变量违反：UPSERT 成功或 race fallback 后，user_default 仍找不到
            throw new IllegalStateException(
                    "user_default category missing after upsert userId=" + userId);
        }
        return id;
    }

    /**
     * 监听 {@link UserRegisteredEvent}（plan-03-expense BR-24 触发点）。
     *
     * <p><b>v1.0 修订</b>：故意<b>不</b>复用 caller 事务 —— 改走
     * {@link #ensureUserDefault} 内部 REQUIRES_NEW 事务，以隔离 INSERT 失败对
     * 注册链路的影响。
     */
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        ensureUserDefault(event.userId());
    }
}
