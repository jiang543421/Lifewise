package com.lifewise.diet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** POST /api/foods body（plan-04-diet §2.2）。 */
public record FoodCreateRequest(
        @NotBlank @Size(max = 100) String name,
        List<String> aliases,
        @NotBlank String category,
        @NotNull BigDecimal kcalPer100g,
        @NotNull BigDecimal proteinGPer100g,
        @NotNull BigDecimal fatGPer100g,
        @NotNull BigDecimal carbGPer100g) {
}