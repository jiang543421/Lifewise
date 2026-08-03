package com.lifewise.diet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifewise.diet.domain.Food;
import java.math.BigDecimal;
import java.util.List;

/** GET /api/foods/{id} 视图。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FoodView(
        Long id,
        String name,
        List<String> aliases,
        String category,
        BigDecimal kcalPer100g,
        BigDecimal proteinGPer100g,
        BigDecimal fatGPer100g,
        BigDecimal carbGPer100g,
        String source) {

    public static FoodView from(Food food) {
        return new FoodView(
                food.getId(),
                food.getName(),
                food.getAliases() == null || food.getAliases().isEmpty()
                        ? null : food.getAliases(),
                food.getSource() == null ? null : food.getSource().name(),
                food.getKcalPer100g(),
                food.getProteinGPer100g(),
                food.getFatGPer100g(),
                food.getCarbGPer100g(),
                food.getSource() == null ? null : food.getSource().name());
    }

    /** 测试 / 预置食物种子用便捷构造器。 */
    public FoodView(Long id, String name, List<String> aliases, String category,
                    BigDecimal kcalPer100g, BigDecimal proteinGPer100g,
                    BigDecimal fatGPer100g, BigDecimal carbGPer100g) {
        this(id, name, aliases, category, kcalPer100g, proteinGPer100g,
                fatGPer100g, carbGPer100g, null);
    }
}