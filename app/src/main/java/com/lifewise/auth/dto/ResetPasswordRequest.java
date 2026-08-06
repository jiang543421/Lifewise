package com.lifewise.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求（plan-auth §5.4 + B-7 closure）。
 *
 * <p>字段：
 * <ul>
 *   <li>{@code token} — 从邮件链接获取的原始 token</li>
 *   <li>{@code newPassword} — 强密码（CLAUDE.md §7.3 + PasswordService.assertStrong）</li>
 * </ul>
 */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 100) String newPassword
) {
}