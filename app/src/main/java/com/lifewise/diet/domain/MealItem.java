package com.lifewise.diet.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 餐项实体（plan-04-diet §3 + V7 §9.3 meal_items）。
 *
 * <p>冗余 {@code kcal_snapshot / protein_g_snapshot / fat_g_snapshot / carb_g_snapshot}：
 * 食物库修改后不影响历史快照（业务架构 §8.4）。
 */
@Entity
@Table(name = "meal_items")
public class MealItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "amount_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal amountG;

    @Column(name = "kcal_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal kcalSnapshot;

    @Column(name = "protein_g_snapshot", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinSnapshot;

    @Column(name = "fat_g_snapshot", nullable = false, precision = 8, scale = 2)
    private BigDecimal fatSnapshot;

    @Column(name = "carb_g_snapshot", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbSnapshot;

    protected MealItem() {
        // JPA
    }

    private MealItem(Meal meal, Long foodId, BigDecimal amountG,
                     BigDecimal kcalSnapshot, BigDecimal proteinSnapshot,
                     BigDecimal fatSnapshot, BigDecimal carbSnapshot) {
        this.meal = meal;
        this.localDate = meal.getLocalDate();
        this.foodId = foodId;
        this.amountG = amountG;
        this.kcalSnapshot = kcalSnapshot;
        this.proteinSnapshot = proteinSnapshot;
        this.fatSnapshot = fatSnapshot;
        this.carbSnapshot = carbSnapshot;
    }

    /** 工厂：service 层在事务内调用。 */
    public static MealItem of(Meal meal, Long foodId, BigDecimal amountG,
                              BigDecimal kcalSnapshot, BigDecimal proteinSnapshot,
                              BigDecimal fatSnapshot, BigDecimal carbSnapshot) {
        if (amountG == null || amountG.signum() <= 0) {
            throw new com.lifewise.diet.service.InvalidAmountException(
                    "amount_g must be > 0");
        }
        return new MealItem(meal, foodId, amountG,
                kcalSnapshot, proteinSnapshot, fatSnapshot, carbSnapshot);
    }

    /** 由 Meal.addItem 调用；包内可访问。 */
    void bindTo(Meal meal) {
        this.meal = meal;
        this.localDate = meal.getLocalDate();
    }

    public Meal getMeal() { return meal; }
    public LocalDate getLocalDate() { return localDate; }
    public Long getFoodId() { return foodId; }
    public BigDecimal getAmountG() { return amountG; }
    public BigDecimal getKcalSnapshot() { return kcalSnapshot; }
    public BigDecimal getProteinSnapshot() { return proteinSnapshot; }
    public BigDecimal getFatSnapshot() { return fatSnapshot; }
    public BigDecimal getCarbSnapshot() { return carbSnapshot; }
}