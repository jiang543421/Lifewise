package com.lifewise.auth.config;

import com.lifewise.shared.infra.security.JwtTokenProvider;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * auth 模块 Spring 装配（plan-auth §3 + plan-shared-infra §1）。
 *
 * <p>把 {@link JwtTokenProvider} 装配为 Spring Bean；TTL 通过
 * {@code auth.jwt.access-ttl-minutes} / {@code auth.jwt.refresh-ttl-days}
 * 配置覆盖（默认 15min / 30d，对齐 plan-auth §2.2 + §2.3）。
 *
 * <p>{@code auth.jwt.secret-base64} 无默认值，必须由部署侧 env / secret
 * manager 显式注入（{@code AUTH_JWT_SECRET_BASE64} 等）；缺失时
 * {@link JwtTokenProvider} 构造抛 {@link IllegalArgumentException}，Spring
 * 启动失败 —— 这是 plan-auth review C1 的修复（CLAUDE.md §7.1 严禁硬编码密钥）。
 */
@Configuration
public class AuthConfig {

    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${auth.jwt.secret-base64}") String secretBase64,
            @Value("${auth.jwt.issuer:lifewise}") String issuer,
            @Value("${auth.jwt.access-ttl-minutes:15}") long accessTtlMinutes,
            @Value("${auth.jwt.refresh-ttl-days:30}") long refreshTtlDays,
            Clock authClock) {
        return new JwtTokenProvider(
                secretBase64,
                issuer,
                Duration.ofMinutes(accessTtlMinutes),
                Duration.ofDays(refreshTtlDays),
                authClock);
    }
}