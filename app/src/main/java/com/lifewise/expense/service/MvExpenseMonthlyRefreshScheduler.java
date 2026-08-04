package com.lifewise.expense.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物化视图 {@code mv_expense_monthly_category} 每日刷新（plan-03-expense §6 验收）。
 *
 * <p>由 {@link com.lifewise.LifewiseApplication} 全局 {@code @EnableScheduling} 启用。
 * 默认 cron 每日 02:00（时区 Asia/Shanghai），可通过环境变量覆盖：
 * <pre>
 *   expense.mv-refresh.cron=0 0 2 * * *
 *   expense.mv-refresh.zone=Asia/Shanghai
 * </pre>
 *
 * <p>使用 {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} 必须存在 UNIQUE INDEX，
 * V12 / V37 §5 已建 {@code uq_mv_expense_monthly_category}。CONCURRENTLY 允许
 * 读不阻塞，但 WRITE 锁全程持有；凌晨 02:00 是低活跃时段。
 *
 * <p>失败策略：捕获异常 → ERROR 日志 + {@link #failureCount} 自增计数器，<b>不抛</b>
 * 异常（避免污染其他定时任务）。运维通过日志 + 计数器告警介入。首次填充若 CONCURRENTLY
 * 失败（视图从未填充），需手动 {@code REFRESH}（无 CONCURRENTLY）一次再做后续计划。
 *
 * <p>时区：显式 {@code Asia/Shanghai}。容器 / JVM 默认时区漂移后，"02:00" 仍是
 * 北京时间 02:00，不被无意改写。
 */
@Component
public class MvExpenseMonthlyRefreshScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MvExpenseMonthlyRefreshScheduler.class);

    /** 失败累计计数器（进程内；JVM 重启归零，与 OutboxWorker 单机权衡一致）。 */
    private final AtomicLong failureCount = new AtomicLong();

    private final EntityManager entityManager;

    public MvExpenseMonthlyRefreshScheduler(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Scheduled(
            cron = "${expense.mv-refresh.cron:0 0 2 * * *}",
            zone = "${expense.mv-refresh.zone:Asia/Shanghai}")
    @Transactional
    public void refreshDaily() {
        long start = System.currentTimeMillis();
        try {
            entityManager.createNativeQuery(
                    "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_expense_monthly_category")
                    .executeUpdate();
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("[expense] mv_expense_monthly_category refreshed elapsedMs={}", elapsed);
        } catch (RuntimeException ex) {
            long total = failureCount.incrementAndGet();
            // ERROR 级别：连续失败可由日志告警系统拦截；计数器供监控面板导出
            // （v1.0 不引入 actuator，后期接 Prometheus 时可直接替换为 MeterRegistry）。
            LOG.error("[expense] mv_expense_monthly_category refresh failed totalFailures={} msg={}",
                    total, ex.getMessage());
        }
    }

    /** 测试观察用（package-private）。 */
    long getFailureCount() {
        return failureCount.get();
    }

    /** 测试重置用（package-private）。 */
    void resetFailureCount() {
        failureCount.set(0);
    }
}
