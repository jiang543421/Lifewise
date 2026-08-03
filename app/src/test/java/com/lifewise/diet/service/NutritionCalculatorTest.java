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
    @DisplayName("computeKcalSnapshot: amount_g * food.kcalPer100g / 100")
    void compute_kcal_snapshot() {
        BigDecimal result = calculator.computeKcalSnapshot(
                new BigDecimal("150.00"), rice());
        // 150 * 130 / 100 = 195
        assertThat(result).isEqualByComparingTo(new BigDecimal("195.000"));
    }

    @Test
    @DisplayName("computeProteinSnapshot: amount_g * food.proteinGPer100g / 100")
    void compute_protein_snapshot() {
        BigDecimal result = calculator.computeProteinSnapshot(
                new BigDecimal("150.00"), rice());
        // 150 * 2.7 / 100 = 4.05
        assertThat(result).isEqualByComparingTo(new BigDecimal("4.050"));
    }

    @Test
    @DisplayName("computeCarbSnapshot: amount_g * food.carbGPer100g / 100")
    void compute_carb_snapshot() {
        BigDecimal result = calculator.computeCarbSnapshot(
                new BigDecimal("150.00"), rice());
        // 150 * 28 / 100 = 42
        assertThat(result).isEqualByComparingTo(new BigDecimal("42.000"));
    }

    @Test
    @DisplayName("computeFatSnapshot: amount_g * food.fatGPer100g / 100")
    void compute_fat_snapshot() {
        BigDecimal result = calculator.computeFatSnapshot(
                new BigDecimal("150.00"), rice());
        // 150 * 0.3 / 100 = 0.45
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.450"));
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
    @DisplayName("kcal(item) returns kcalSnapshot directly when present")
    void kcal_returns_snapshot() {
        BigDecimal result = calculator.kcal(itemWithAmount(new BigDecimal("100.00")));
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.000"));
    }
}