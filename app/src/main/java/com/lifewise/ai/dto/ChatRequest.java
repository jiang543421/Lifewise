package com.lifewise.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 问答请求（plan §2.3）。 */
public record ChatRequest(
        @NotBlank @Size(max = 1000) String question,
        String scope) {
}