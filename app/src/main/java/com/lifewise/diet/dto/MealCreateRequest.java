package com.lifewise.diet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifewise.diet.domain.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** POST /api/meals body（plan-04-diet §2.1）。 */
public record MealCreateRequest(
        @NotNull MealType type,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate localDate,
        @Size(max = 64) String timezone,
        @Size(max = 2000) String note,
        @NotEmpty @Valid List<MealItemRequest> items) {
}