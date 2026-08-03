package com.lifewise.diet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifewise.diet.domain.Food;
import com.lifewise.diet.domain.Meal;
import com.lifewise.diet.domain.MealItem;
import com.lifewise.diet.domain.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NutritionCalculator pure unit tests (plan-04-diet section 5.3).
 */
class NutritionCalculatorTest {

    private final NutritionCalculator calculator = new NutritionCalculator();

    private Food rice() {
        // system(name, category, kcal, protein, carb, fat) -- doubles
        return Food.system("White rice", "STAPLE", 130d, 2.7d, 28.0d, 0.3d);
    }

    private MealItem itemWithAmount(BigDecimal amountG) {
        Meal meal = Meal.create(1L, LocalDate.of(2026, 8, 3), "UTC", MealType.LUNCH, null);
        return MealItem.of(meal, 1L, amountG,
                new BigDecimal("100.00"), new BigDecimal("10.00"),
                new BigDecimal("20.00"), new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("computeKcalSnapshot: amount_g * food.kcalPer100g / 100 (scale=2)")
    void compute_kcal_snapshot() {
        BigDecimal result = calculator.computeKcalSnapshot(
                new BigDecimal("150.00"), rice());
        // 150 * 130 / 100 = 195.00
        assertThat(result).isEqualByComparingTo(new BigDecimal("195.00"));
    }

    @Test
    @DisplayName("computeProteinSnapshot: amount_g * food.proteinGPer100g / 100 (scale=2)")
    void compute_protein_snapshot() {
        BigDecimal result = calculator.computeProteinSnapshot(
                new BigDecimal("150.00"), rice());
        // 150 * 2.7 / 100 = 4.05
        assertThat(result).isEqualByComparingTo(new BigDecimal("4.05"));
    }

    @Test
    @DisplayName("computeCarbSnapshot: amount_g * food.carbGPer100g / 100 (scale=2)")
    void compute_carb_snapshot() {
        BigDecimal result = calculator.computeCarbSnapshot(
                new BigDecimal("150.00"), rice());
        // 150 * 28 / 100 = 42.00
        assertThat(result).isEqualByComparingTo(new BigDecimal("42.00"));
    }

    @Test
    @DisplayName("computeFatSnapshot: amount_g * food.fatGPer100g / 100 (scale=2)")
    void compute_fat_snapshot() {
        BigDecimal result = calculator.computeFatSnapshot(
                new BigDecimal("150.00"), rice());
        // 150 * 0.3 / 100 = 0.45
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.45"));
    }

    @Test
    @DisplayName("computeKcalSnapshot with realistic decimals rounds to scale=2 (Bug B lock)")
    void compute_kcal_snapshot_realistic_decimals() {
        // food with kcal=99.99 (per 100g), amount=33.33g → 33.33 * 99.99 / 100 = 33.329667
        // scale=2 HALF_UP → 33.33；scale=3 → 33.330（口径差异即 Bug B）。
        Food decimalFood = Food.system("Custom", "STAPLE", 99.99d, 0d, 0d, 0d);
        BigDecimal result = calculator.computeKcalSnapshot(
                new BigDecimal("33.33"), decimalFood);
        assertThat(result).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("amount_g <= 0 throws InvalidAmountException")
    void reject_non_positive_amount_g() {
        assertThatThrownBy(() ->
                calculator.computeKcalSnapshot(BigDecimal.ZERO, rice()))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("amount_g null throws InvalidAmountException")
    void reject_null_amount_g() {
        assertThatThrownBy(() ->
                calculator.computeKcalSnapshot(null, rice()))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("computeKcalSnapshot with null food throws InvalidAmountException")
    void reject_null_food() {
        assertThatThrownBy(() ->
                calculator.computeKcalSnapshot(new BigDecimal("100.00"), null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("kcal(item) returns kcalSnapshot directly when present (scale=2)")
    void kcal_returns_snapshot() {
        BigDecimal result = calculator.kcal(itemWithAmount(new BigDecimal("100.00")));
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}