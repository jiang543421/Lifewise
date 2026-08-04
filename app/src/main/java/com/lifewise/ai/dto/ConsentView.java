package com.lifewise.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** AI 同意状态视图。 */
public record ConsentView(
        @JsonProperty("ai_consent") boolean aiConsent,
        @JsonProperty("consented_at") OffsetDateTime consentedAt) {
}