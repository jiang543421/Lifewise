package com.lifewise.ai.dto;

import jakarta.validation.constraints.NotNull;

/** 用户 AI 同意请求（plan §2.5）。 */
public record ConsentRequest(@NotNull Boolean aiConsent) {
}