package com.lifewise.daily.dto;

import com.lifewise.daily.domain.HighlightType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HighlightRequest(
        @NotNull HighlightType highlightType,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 32) String referenceType,
        Long referenceId,
        @Min(0) Integer sortOrder) {
}
