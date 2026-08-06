package com.lifewise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.auth.domain.RefreshToken;
import com.lifewise.auth.domain.User;
import com.lifewise.auth.domain.exception.EmailExistsException;
import com.lifewise.auth.domain.exception.InvalidCredentialsException;
import com.lifewise.auth.domain.exception.TokenInvalidException;
import com.lifewise.auth.domain.exception.WeakPasswordException;
import com.lifewise.auth.dto.LoginRequest;
import com.lifewise.auth.dto.RegisterRequest;
import com.lifewise.auth.dto.TokenResponse;
import com.lifewise.auth.repository.PasswordResetTokenRepository;
import com.lifewise.auth.repository.RefreshTokenRepository;
import com.lifewise.auth.repository.UserRepository;
import com.lifewise.auth.service.AuthService;
import com.lifewise.auth.service.EmailService;
import com.lifewise.auth.service.JwtRefreshServiceImpl;
import com.lifewise.auth.service.PasswordService;
import com.lifewise.shared.infra.security.JwtRefreshTokenService;
import com.lifewise.shared.infra.security.JwtTokenProvider;
import com.lifewise.shared.infra.security.PasswordEncoderConfig;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("AuthService 注册 / 登录 / 刷新 / 登出")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private JwtRefreshTokenService refreshService;
    @Mock private OutboxWriter outboxWriter;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private EmailService emailService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        PasswordService pwd = new PasswordService(new PasswordEncoderConfig().passwordEncoder());
        service = new AuthService(
                userRepository, refreshTokenRepository, passwordResetTokenRepository,
                pwd, emailService,
                tokenProvider, refreshService, outboxWriter, eventPublisher, fixed);
        lenient().when(tokenProvider.createAccessToken(any(), any()))
                .thenReturn("access.token.value");
        lenient().when(tokenProvider.parseAccessToken(anyString()))
                .thenReturn(new JwtTokenProvider.AccessClaims(
                        1L, List.of("USER"), "jti", NOW.plusSeconds(900)));
    }

    @Test
    @DisplayName("register：合法请求 → 返回 TokenResponse + 发布 auth.user.registered 事件")
    void should_register_and_publish_event() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            // 模拟 JPA 回填 id=1（@GeneratedValue IDENTITY 在测试中由 mock 注入）
            com.lifewise.auth.domain.UserWithId.setId(u, 1L);
            return u;
        });

        TokenResponse resp = service.register(new RegisterRequest(
                "alice@example.com", "Str0ng!Password", "Asia/Shanghai", "zh-CN"));

        assertThat(resp.accessToken()).isEqualTo("access.token.value");
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.expiresIn()).isEqualTo(900);

        ArgumentCaptor<com.lifewise.shared.integration.event.EventEnvelope> envCap =
                ArgumentCaptor.forClass(com.lifewise.shared.integration.event.EventEnvelope.class);
        verify(outboxWriter).append(envCap.capture());
        assertThat(envCap.getValue().eventType()).isEqualTo("auth.user.registered");
    }

    @Test
    @DisplayName("register：邮箱已存在 → 抛 EmailExistsException")
    void should_reject_duplicate_email() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "dup@example.com", "Str0ng!Password", "UTC", "en")))
                .isInstanceOf(EmailExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register：弱密码 → 抛 WeakPasswordException")
    void should_reject_weak_password() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "bob@example.com", "weak", "UTC", "en")))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    @DisplayName("login：正确凭据 → 返回 TokenResponse + 发布 auth.user.logged_in 事件")
    void should_login_with_correct_credentials() {
        // 用真实 BCrypt 哈希让 PasswordService.matches 通过
        String realHash = new PasswordService(new PasswordEncoderConfig().passwordEncoder())
                .hash("Str0ng!Password");
        User existing = User.create("alice@example.com", realHash, "alice", "UTC", "en");
        com.lifewise.auth.domain.UserWithId.setId(existing, 1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse resp = service.login(
                new LoginRequest("alice@example.com", "Str0ng!Password"),
                "1.2.3.4", "ua");

        assertThat(resp.accessToken()).isNotBlank();
        ArgumentCaptor<com.lifewise.shared.integration.event.EventEnvelope> cap =
                ArgumentCaptor.forClass(com.lifewise.shared.integration.event.EventEnvelope.class);
        verify(outboxWriter).append(cap.capture());
        assertThat(cap.getValue().eventType()).isEqualTo("auth.user.logged_in");
    }

    @Test
    @DisplayName("login：邮箱不存在 → 抛 InvalidCredentialsException")
    void should_reject_login_with_unknown_email() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(
                new LoginRequest("ghost@example.com", "Str0ng!Password"), null, null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("refresh：rotate 返回有效结果 → TokenResponse 含新 access/refresh")
    void should_refresh_with_valid_token() {
        when(refreshService.rotate("old.rt")).thenReturn(Optional.of(
                new JwtRefreshTokenService.RefreshResult(
                        "new-access", "new-refresh", NOW.plusSeconds(900))));

        TokenResponse resp = service.refresh("old.rt");

        assertThat(resp.accessToken()).isEqualTo("new-access");
        assertThat(resp.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("refresh：rotate 返回 empty → 抛 TokenInvalidException")
    void should_reject_unknown_refresh() {
        when(refreshService.rotate(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh("unknown"))
                .isInstanceOf(TokenInvalidException.class);
    }

    @Test
    @DisplayName("logout：解析成功 → 同 family 全部 revoke")
    void should_revoke_family_on_logout() {
        UUID familyId = UUID.randomUUID();
        when(refreshService.parseClaims("rt")).thenReturn(Optional.of(
                new JwtRefreshTokenService.RefreshClaims(
                        "jti", familyId.toString(), 42L, NOW.plusSeconds(900))));
        RefreshToken row = RefreshToken.issue(
                42L, familyId, JwtRefreshServiceImpl.sha256Hex("rt"),
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).plusDays(30),
                null, null);
        when(refreshTokenRepository.findByTokenHash(JwtRefreshServiceImpl.sha256Hex("rt")))
                .thenReturn(Optional.of(row));
        when(refreshTokenRepository.findAllByUserIdAndFamilyId(42L, familyId))
                .thenReturn(List.of(row));

        service.logout("rt");

        assertThat(row.revokedAt()).isNotNull();
    }

    @Test
    @DisplayName("logout：refresh 解析失败 → 抛 TokenInvalidException")
    void should_reject_logout_with_malformed_refresh() {
        when(refreshService.parseClaims(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.logout("garbage"))
                .isInstanceOf(TokenInvalidException.class);
    }
}