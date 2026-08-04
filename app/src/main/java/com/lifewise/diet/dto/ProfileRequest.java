package com.lifewise.diet.dto;

import com.lifewise.diet.domain.ActivityLevel;
import com.lifewise.diet.domain.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** PUT /api/meals/profile body（plan-04-diet §2.4 + §5.5）。 */
public record ProfileRequest(
        @NotNull @Positive BigDecimal heightCm,
        @NotNull @Positive BigDecimal weightKg,
        @NotNull @Min(10) @Max(120) Integer age,
        @NotNull Gender gender,
        @NotNull ActivityLevel activityLevel,
        Integer dailyKcalTarget) {
}