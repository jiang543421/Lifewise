package com.lifewise.diet.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 餐次实体（plan-04-diet §3 + V7 §9.2 meals）。
 *
 * <p>按月 RANGE 分区（V11），分区键 {@code local_date}；Hibernate 仍按 IDENTITY 单列查询。
 *
 * <p>聚合：{@code totalKcal} 由 {@link com.lifewise.diet.service.NutritionCalculator} 计算后写入。
 */
@Entity
@Table(name = "meals")
public class Meal extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false)
    private MealType mealType;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "note", length = 2000)
    private String note;

    /** 餐次总 kcal（cents BIGINT）—— 通过触发器或 service 计算后写入。 */
    @Column(name = "total_kcal_cents")
    private Long totalKcalCents;

    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<MealItem> items = new ArrayList<>();

    protected Meal() {
        // JPA
    }

    private Meal(Long userId, LocalDate localDate, String timezone,
                 MealType mealType, Long expenseId, String note) {
        this.userId = userId;
        this.localDate = localDate;
        this.timezone = timezone;
        this.mealType = mealType;
        this.expenseId = expenseId;
        this.note = note;
    }

    /** 工厂：创建新餐次。 */
    public static Meal create(Long userId, LocalDate localDate, String timezone,
                              MealType mealType, String note) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (localDate == null) {
            throw new IllegalArgumentException("localDate must not be null");
        }
        if (mealType == null) {
            throw new IllegalArgumentException("mealType must not be null");
        }
        String safeTimezone = (timezone == null || timezone.isBlank()) ? "UTC" : timezone;
        if (safeTimezone.length() > 64) {
            throw new IllegalArgumentException("timezone length must be <= 64");
        }
        return new Meal(userId, localDate, safeTimezone, mealType, null, note);
    }

    /** 添加餐项；由 service 层在事务内调用。 */
    public void addItem(MealItem item) {
        item.bindTo(this);
        this.items.add(item);
    }

    /** 清空所有 items（用于 update 时整替换）。 */
    public void clearItems() {
        this.items.clear();
    }

    /** 写入聚合后的总 kcal（cents BIGINT）。 */
    public void setTotalKcalCents(long cents) {
        this.totalKcalCents = cents;
    }

    /**
     * 更新备注（H1 fix）。storefront 也可以传空串清空。
     * 长度校验放在 DTO @Size（max 2000），service 层不再重复。
     */
    public void setNote(String note) {
        this.note = note;
    }

    /** 当前聚合 kcal 总和（cents，由 service 循环 items 后写入）。 */
    public BigDecimal totalKcalAsDecimal() {
        if (totalKcalCents == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalKcalCents).divide(new BigDecimal(100));
    }

    public Long getUserId() { return userId; }
    public LocalDate getLocalDate() { return localDate; }
    public String getTimezone() { return timezone; }
    public MealType getMealType() { return mealType; }
    public Long getExpenseId() { return expenseId; }
    public String getNote() { return note; }
    public Long getTotalKcalCents() { return totalKcalCents; }

    /** 只读视图，避免外部 mutate 内部 list。 */
    public List<MealItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}