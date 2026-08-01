package com.lifewise.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO（plan-auth §2.1 POST /api/auth/register）。
 *
 * <p>字段：
 * <ul>
 *   <li>{@code email} — 邮箱（唯一）；{@code @Email} + 长度 254</li>
 *   <li>{@code password} — 弱密码校验在 {@link com.lifewise.auth.service.PasswordService} 强制（CLAUDE.md §7.3）</li>
 *   <li>{@code timezone} — IANA ID；非空</li>
 *   <li>{@code locale} — BCP-47；非空</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Size(max = 16) String locale) {
}