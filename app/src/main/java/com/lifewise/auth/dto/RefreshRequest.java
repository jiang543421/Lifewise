package com.lifewise.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** plan-auth §2.1 POST /api/auth/refresh 请求体。 */
public record RefreshRequest(@NotBlank String refreshToken) {
}