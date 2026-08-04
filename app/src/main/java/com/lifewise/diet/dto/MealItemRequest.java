package com.lifewise.diet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** MealCreateRequest.items 单项（plan-04-diet §2.1 + §5.1）。 */
public record MealItemRequest(
        @NotNull Long foodId,
        @NotNull @Positive BigDecimal amountG,
        String note) {
}