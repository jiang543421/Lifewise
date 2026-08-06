package com.lifewise.auth.controller;

import com.lifewise.auth.dto.ForgotPasswordRequest;
import com.lifewise.auth.dto.LoginRequest;
import com.lifewise.auth.dto.MessageResponse;
import com.lifewise.auth.dto.RefreshRequest;
import com.lifewise.auth.dto.RegisterRequest;
import com.lifewise.auth.dto.ResetPasswordRequest;
import com.lifewise.auth.dto.TokenResponse;
import com.lifewise.auth.service.AuthService;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权闭环 6 端点（plan-auth §2.1 + §5 + B-7 closure v1.3.3）。
 *
 * <p>核心鉴权：register / login / refresh / logout。
 * B-7 closure (v1.3.3)：forgot-password / reset-password 已落地（v1.0 单用户
 * 实际不会触发；v1.1+ 多用户接入时复用）。邮箱验证 / CSRF 仍 defer。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest req) {
        TokenResponse data = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest http) {
        TokenResponse data = authService.login(
                req,
                extractIp(http),
                http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        TokenResponse data = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<MessageResponse>> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(MessageResponse.ok()));
    }

    /**
     * B-7 closure: 触发密码重置邮件（plan-auth §5.4）。
     *
     * <p>始终返回 200 OK 防止 email enumeration。EmailService 投递 token（v1.0
     * stdout，v1.1+ SMTP）。
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<MessageResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.email());
        return ResponseEntity.ok(ApiResponse.ok(MessageResponse.ok()));
    }

    /**
     * B-7 closure: 用 token + 新密码重置密码（plan-auth §5.4）。
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<MessageResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.ok(ApiResponse.ok(MessageResponse.ok()));
    }

    private static String extractIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return http.getRemoteAddr();
    }
}