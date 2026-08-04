package com.lifewise.expense.domain;

import com.lifewise.common.BaseEntity;
import com.lifewise.expense.domain.enums.PayMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 消费记录（plan-03-expense §2.1 + V6/V37 expenses DDL）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-09：amount_cents > 0 且 ≤ 9999999999（cents → 99999999.99 元）</li>
 *   <li>BR-22：note ≤ 200（V37 收紧）</li>
 *   <li>local_date 必须 = occurred_at.toLocalDate()（分区裁剪一致性）</li>
 * </ul>
 *
 * <p>分区键 = {@code local_date}（V11）；occurred_at 仅用于排序和 outbox 时间戳。
 */
@Entity
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", nullable = false)
    private PayMethod payMethod;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "note")
    private String note;

    protected Expense() {
        // JPA
    }

    private Expense(Long userId, Long categoryId, LocalDate localDate, String timezone,
                    Long amountCents, String currency, PayMethod payMethod,
                    OffsetDateTime occurredAt, String note) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.localDate = localDate;
        this.timezone = timezone;
        this.amountCents = amountCents;
        this.currency = currency;
        this.payMethod = payMethod;
        this.occurredAt = occurredAt;
        this.note = note;
    }

    /**
     * 工厂：创建消费记录。
     *
     * @param localDate 必填；应等于 {@code occurredAt.toLocalDate()} 以保证分区一致
     */
    public static Expense create(Long userId, Long categoryId, LocalDate localDate,
                                  String timezone, Long amountCents, String currency,
                                  PayMethod payMethod, OffsetDateTime occurredAt,
                                  String note) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId required");
        }
        validateAmount(amountCents);
        if (localDate == null) {
            throw new IllegalArgumentException("localDate required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt required");
        }
        validateNote(note);
        if (timezone == null || timezone.isBlank()) {
            timezone = "UTC";
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be 3-char ISO 4217");
        }
        if (payMethod == null) {
            throw new IllegalArgumentException("payMethod required");
        }
        return new Expense(userId, categoryId, localDate, timezone,
                amountCents, currency, payMethod, occurredAt, note);
    }

    /** 应用层更新（金额/分类/支付方式/时间/备注）。 */
    public void applyUpdate(Long categoryId, Long amountCents, PayMethod payMethod,
                             OffsetDateTime occurredAt, String note) {
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (amountCents != null) {
            validateAmount(amountCents);
            this.amountCents = amountCents;
        }
        if (payMethod != null) {
            this.payMethod = payMethod;
        }
        if (occurredAt != null) {
            this.occurredAt = occurredAt;
            this.localDate = occurredAt.toLocalDate();
        }
        if (note != null) {
            validateNote(note);
            this.note = note;
        }
    }

    private static void validateAmount(Long amountCents) {
        if (amountCents == null || amountCents <= 0) {
            throw new com.lifewise.expense.service.exception.ExpenseInvalidAmountException(
                    "amount_cents must be positive", amountCents);
        }
        if (amountCents > 9_999_999_999L) {
            throw new com.lifewise.expense.service.exception.ExpenseInvalidAmountException(
                    "amount_cents exceeds limit (max 9999999999)", amountCents);
        }
    }

    private static void validateNote(String note) {
        if (note != null && note.length() > 200) {
            throw new IllegalArgumentException("note must be <= 200 chars");
        }
    }

    public Long getUserId() { return userId; }
    public Long getCategoryId() { return categoryId; }
    public LocalDate getLocalDate() { return localDate; }
    public String getTimezone() { return timezone; }
    public Long getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public PayMethod getPayMethod() { return payMethod; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getNote() { return note; }
    public boolean isOwnedBy(Long userId) { return this.userId.equals(userId); }
}