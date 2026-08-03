package com.lifewise.diet.service;

import com.lifewise.diet.domain.Food;
import com.lifewise.diet.domain.MealItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * 营养聚合（plan-04-diet §5.3）。
 *
 * <p>amount_g 换算公式：{@code kcal = amount_g * food.kcal_per_100g / 100}。
 * food=null 时（食物被软删，引用方容错读取快照）直接返回快照值。
 */
@Component
public class NutritionCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public BigDecimal kcal(MealItem item) {
        validateAmount(item);
        if (item.getKcalSnapshot() == null) {
            return BigDecimal.ZERO;
        }
        // scale=2 与 meal_items.kcal_snapshot NUMERIC(10,2) 一致：
        // 与 SQL 的 SUM/AVG 聚合结果在序列化层同尺度，cents 口径统一。
        return item.getKcalSnapshot().setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal proteinG(MealItem item) {
        validateAmount(item);
        return item.getProteinSnapshot() == null ? BigDecimal.ZERO
                : item.getProteinSnapshot().setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal fatG(MealItem item) {
        validateAmount(item);
        return item.getFatSnapshot() == null ? BigDecimal.ZERO
                : item.getFatSnapshot().setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal carbG(MealItem item) {
        validateAmount(item);
        return item.getCarbSnapshot() == null ? BigDecimal.ZERO
                : item.getCarbSnapshot().setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 由 service 在写 meal_items 前调用：按 amount_g 与 food 营养快照计算快照值。
     */
    public BigDecimal computeKcalSnapshot(BigDecimal amountG, Food food) {
        if (amountG == null || amountG.signum() <= 0) {
            throw new InvalidAmountException("amountG must be > 0");
        }
        if (food == null) {
            throw new InvalidAmountException("food must not be null");
        }
        return amountG.multiply(food.getKcalPer100g())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeProteinSnapshot(BigDecimal amountG, Food food) {
        if (amountG == null || amountG.signum() <= 0) {
            throw new InvalidAmountException("amountG must be > 0");
        }
        if (food == null) {
            return BigDecimal.ZERO;
        }
        return amountG.multiply(food.getProteinGPer100g())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeFatSnapshot(BigDecimal amountG, Food food) {
        if (amountG == null || amountG.signum() <= 0) {
            throw new InvalidAmountException("amountG must be > 0");
        }
        if (food == null) {
            return BigDecimal.ZERO;
        }
        return amountG.multiply(food.getFatGPer100g())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeCarbSnapshot(BigDecimal amountG, Food food) {
        if (amountG == null || amountG.signum() <= 0) {
            throw new InvalidAmountException("amountG must be > 0");
        }
        if (food == null) {
            return BigDecimal.ZERO;
        }
        return amountG.multiply(food.getCarbGPer100g())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private void validateAmount(MealItem item) {
        if (item == null) {
            throw new InvalidAmountException("item must not be null");
        }
        if (item.getAmountG() == null || item.getAmountG().signum() <= 0) {
            throw new InvalidAmountException("amountG must be > 0");
        }
    }
}