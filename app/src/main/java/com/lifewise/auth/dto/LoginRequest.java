package com.lifewise.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** plan-auth §2.1 POST /api/auth/login 请求体。 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password) {
}