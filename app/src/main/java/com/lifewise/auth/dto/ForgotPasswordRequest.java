package com.lifewise.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 忘记密码请求（plan-auth §5.4 + B-7 closure）。
 *
 * <p>只需 email 字段。响应永远 200 OK（不暴露 email 是否存在防 enumeration）。
 */
public record ForgotPasswordRequest(
        @NotBlank @Email String email
) {
}