package com.lifewise.auth.controller;

import com.lifewise.auth.dto.LoginRequest;
import com.lifewise.auth.dto.MessageResponse;
import com.lifewise.auth.dto.RefreshRequest;
import com.lifewise.auth.dto.RegisterRequest;
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
 * 鉴权闭环 4 端点（plan-auth §2.1 + §5）。
 *
 * <p>本控制器仅承载核心鉴权闭环：register / login / refresh / logout。
 * 邮箱验证 / 找回密码 / 重置密码 / CSRF 等 plan-auth §2.1 范围超出本期 v1.0
 * 实施范围，留待后续 plan 接入。
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

    private static String extractIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return http.getRemoteAddr();
    }
}