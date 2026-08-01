package com.lifewise.auth.dto;

import java.time.Instant;

/**
 * 鉴权响应 DTO（plan-auth §2.1）。
 *
 * <p>{@code expiresIn} 为 access token 剩余秒数；refresh 不在此处暴露 expiresAt
 * （前端按本地策略处理）。
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        Instant issuedAt) {

    public static TokenResponse of(String access, String refresh, long expiresIn, Instant issuedAt) {
        return new TokenResponse(access, refresh, expiresIn, issuedAt);
    }
}