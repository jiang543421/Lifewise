package com.lifewise.expense.domain;

import com.lifewise.common.BaseEntity;
import com.lifewise.expense.domain.enums.BudgetScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 月度预算（plan-03-expense §2.3 + V6/V37 budgets DDL）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-10：月度内 (user, scope, category?, period) 唯一（5-col UNIQUE）</li>
 *   <li>H-5：notify_muted_until 必须落在当前 period 内</li>
 *   <li>scope/category_id 一致性（DB CHECK）：TOTAL 时 category_id NULL</li>
 * </ul>
 *
 * <p>{@code period_year_month} 派生自 {@code period_year}/{@code period_month}。
 */
@Entity
@Table(name = "budgets")
public class Budget extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "scope", nullable = false)
    @Enumerated(EnumType.STRING)
    private BudgetScope scope;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled;

    @Column(name = "notify_muted_until")
    private LocalDate notifyMutedUntil;

    /** 保留 V6 的旧字段，向后兼容；{@link #alertThresholdPct()} 不再被 BudgetEvaluator 使用。 */
    @Column(name = "alert_threshold_pct", nullable = false)
    private int alertThresholdPct;

    protected Budget() {
        // JPA
    }

    private Budget(Long userId, BudgetScope scope, Long categoryId,
                    int periodYear, int periodMonth,
                    Long amountCents, String currency,
                    boolean notifyEnabled, LocalDate notifyMutedUntil,
                    int alertThresholdPct) {
        this.userId = userId;
        this.scope = scope;
        this.categoryId = categoryId;
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.amountCents = amountCents;
        this.currency = currency;
        this.notifyEnabled = notifyEnabled;
        this.notifyMutedUntil = notifyMutedUntil;
        this.alertThresholdPct = alertThresholdPct;
    }

    public static Budget create(Long userId, BudgetScope scope, Long categoryId,
                                 int periodYear, int periodMonth,
                                 Long amountCents, String currency,
                                 boolean notifyEnabled) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope required");
        }
        if (scope == BudgetScope.CATEGORY && categoryId == null) {
            throw new IllegalArgumentException("categoryId required for CATEGORY scope");
        }
        if (scope == BudgetScope.TOTAL && categoryId != null) {
            throw new IllegalArgumentException("categoryId must be null for TOTAL scope");
        }
        validatePeriod(periodYear, periodMonth);
        validateAmount(amountCents);
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be 3-char ISO 4217");
        }
        return new Budget(userId, scope, categoryId, periodYear, periodMonth,
                amountCents, currency, notifyEnabled, null, 80);
    }

    /** 应用层更新（amount_cents / notify_enabled）。scope/period 不可变（BR-10）。 */
    public void applyUpdate(Long amountCents, boolean notifyEnabled) {
        validateAmount(amountCents);
        this.amountCents = amountCents;
        this.notifyEnabled = notifyEnabled;
    }

    /** H-5：静音至月末（mute_until 必须落在当前 period 内，DB CHECK 守护）。 */
    public void mute(LocalDate until) {
        if (until == null) {
            this.notifyMutedUntil = null;
            return;
        }
        LocalDate periodStart = LocalDate.of(periodYear, periodMonth, 1);
        LocalDate periodEnd = periodStart.plusMonths(1);
        if (until.isBefore(periodStart) || !until.isBefore(periodEnd)) {
            throw new IllegalArgumentException("mute_until must be within current period");
        }
        this.notifyMutedUntil = until;
    }

    public void unmute() {
        this.notifyMutedUntil = null;
    }

    public boolean isMuted(LocalDate today) {
        return notifyMutedUntil != null && !today.isAfter(notifyMutedUntil);
    }

    public boolean isNotifyEnabled() {
        return notifyEnabled;
    }

    public boolean isActive() {
        return !isDeleted();
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public static void validatePeriod(int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("period_year out of range");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("period_month out of range");
        }
    }

    private static void validateAmount(Long amountCents) {
        if (amountCents == null || amountCents <= 0) {
            throw new IllegalArgumentException("amount_cents must be positive");
        }
        if (amountCents > 9_999_999_999L) {
            throw new IllegalArgumentException("amount_cents exceeds limit");
        }
    }

    public Long getUserId() { return userId; }
    public BudgetScope getScope() { return scope; }
    public Long getCategoryId() { return categoryId; }
    public int getPeriodYear() { return periodYear; }
    public int getPeriodMonth() { return periodMonth; }
    public Long getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public boolean isNotifyEnabledField() { return notifyEnabled; }
    public LocalDate getNotifyMutedUntil() { return notifyMutedUntil; }
    public int getAlertThresholdPct() { return alertThresholdPct; }
}