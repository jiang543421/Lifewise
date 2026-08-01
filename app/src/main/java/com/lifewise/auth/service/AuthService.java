package com.lifewise.auth.service;

import com.lifewise.auth.domain.RefreshToken;
import com.lifewise.auth.domain.User;
import com.lifewise.auth.domain.exception.EmailExistsException;
import com.lifewise.auth.domain.exception.InvalidCredentialsException;
import com.lifewise.auth.domain.exception.TokenInvalidException;
import com.lifewise.auth.domain.exception.UserLockedException;
import com.lifewise.auth.dto.LoginRequest;
import com.lifewise.auth.dto.RegisterRequest;
import com.lifewise.auth.dto.TokenResponse;
import com.lifewise.auth.event.payload.UserLoggedInPayload;
import com.lifewise.auth.event.payload.UserRegisteredPayload;
import com.lifewise.auth.repository.RefreshTokenRepository;
import com.lifewise.auth.repository.UserRepository;
import com.lifewise.shared.infra.security.JwtRefreshTokenService;
import com.lifewise.shared.infra.security.JwtTokenProvider;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * auth 业务编排（plan-auth §5.1 + §5.2）。
 *
 * <p>注册 / 登录 / 刷新 / 登出 4 个核心用例；每个事务内 INSERT/UPDATE + outbox append。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordService passwordService;
    private final JwtTokenProvider tokenProvider;
    private final JwtRefreshTokenService refreshService;
    private final OutboxWriter outboxWriter;
    private final Clock clock;
    private final Duration refreshTtl;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordService passwordService,
            JwtTokenProvider tokenProvider,
            JwtRefreshTokenService refreshService,
            OutboxWriter outboxWriter,
            Clock authClock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordService = passwordService;
        this.tokenProvider = tokenProvider;
        this.refreshService = refreshService;
        this.outboxWriter = outboxWriter;
        this.clock = authClock;
        this.refreshTtl = Duration.ofDays(30);
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailExistsException(request.email());
        }
        passwordService.assertStrong(request.password());

        String hash = passwordService.hash(request.password());
        String email = request.email().toLowerCase();
        // display_name 由 email local-part 派生（plan-auth §2.1 不要求 client 提交）；
        // 后续 v1.1 提供独立 PATCH /users/me 端点由用户重命名
        String displayName = email.substring(0, Math.min(email.indexOf('@'), 100));
        User user = User.create(email, hash, displayName, request.timezone(), request.locale());
        user = userRepository.save(user);

        TokenPair pair = issueInitialTokens(user);

        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.AUTH_USER_REGISTERED.eventType(),
                1,
                OffsetDateTime.now(clock),
                user.getId(),
                "user",
                user.getId(),
                null,
                null,
                null,
                new UserRegisteredPayload(
                        user.getId(),
                        user.email(),
                        user.timezone(),
                        user.locale(),
                        OffsetDateTime.now(clock)).toMap()));

        return TokenResponse.of(pair.access, pair.refresh, pair.expiresIn, pair.issuedAt.toInstant());
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String ip, String userAgent) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);
        if (user.isLocked()) {
            throw new UserLockedException("account is locked");
        }
        if (!passwordService.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        user.recordLogin(now);
        userRepository.save(user);

        TokenPair pair = issueInitialTokens(user);

        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.AUTH_USER_LOGGED_IN.eventType(),
                1,
                now,
                user.getId(),
                "user",
                user.getId(),
                null,
                null,
                null,
                new UserLoggedInPayload(user.getId(), ip, userAgent, now).toMap()));

        return TokenResponse.of(pair.access, pair.refresh, pair.expiresIn, pair.issuedAt.toInstant());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        JwtRefreshTokenService.RefreshResult result = refreshService.rotate(refreshToken)
                .orElseThrow(() -> new TokenInvalidException("refresh token unknown"));
        OffsetDateTime nowOdt = OffsetDateTime.now(clock);
        long expiresIn = Duration.between(nowOdt.toInstant(),
                result.expiresAt()).getSeconds();
        return TokenResponse.of(result.accessToken(), result.refreshToken(),
                expiresIn, nowOdt.toInstant());
    }

    @Transactional
    public void logout(String refreshToken) {
        // 解析 refresh → 找 row → revoke（不影响同 user 其他设备）
        JwtRefreshTokenService.RefreshClaims claims = refreshService.parseClaims(refreshToken)
                .orElseThrow(() -> new TokenInvalidException("refresh token unknown"));
        String hash = JwtRefreshServiceImpl.sha256Hex(refreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            if (rt.revokedAt() == null) {
                rt.revoke(OffsetDateTime.now(clock));
                refreshTokenRepository.save(rt);
            }
        });
        // 同 family 内其他 active token 也撤销（plan-auth §5.2 logout 语义 = 全 family 失效）
        UUID familyId;
        try {
            familyId = UUID.fromString(claims.familyId());
        } catch (IllegalArgumentException e) {
            return;
        }
        refreshTokenRepository.findAllByUserIdAndFamilyId(
                claims.userId(), familyId).forEach(rt -> {
            if (rt.revokedAt() == null) {
                rt.revoke(OffsetDateTime.now(clock));
                refreshTokenRepository.save(rt);
            }
        });
    }

    private TokenPair issueInitialTokens(User user) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String familyId = UUID.randomUUID().toString();
        String refreshToken = JwtRefreshServiceImpl.randomRefreshToken();
        String accessToken = tokenProvider.createAccessToken(user.getId(), java.util.List.of("USER"));

        OffsetDateTime refreshExp = now.plus(refreshTtl);
        RefreshToken row = RefreshToken.issue(
                user.getId(),
                UUID.fromString(familyId),
                JwtRefreshServiceImpl.sha256Hex(refreshToken),
                refreshExp,
                null,
                null);
        refreshTokenRepository.save(row);

        long expiresIn = Duration.between(now.toInstant(),
                tokenProvider.parseAccessToken(accessToken).expiresAt()).getSeconds();
        return new TokenPair(accessToken, refreshToken, expiresIn, now);
    }

    private record TokenPair(String access, String refresh, long expiresIn, OffsetDateTime issuedAt) {
    }
}