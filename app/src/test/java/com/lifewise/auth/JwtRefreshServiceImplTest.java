package com.lifewise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.auth.domain.RefreshToken;
import com.lifewise.auth.domain.exception.TokenExpiredException;
import com.lifewise.auth.domain.exception.TokenInvalidException;
import com.lifewise.auth.domain.exception.TokenMismatchException;
import com.lifewise.auth.domain.exception.TokenReusedException;
import com.lifewise.auth.repository.RefreshTokenRepository;
import com.lifewise.auth.service.JwtRefreshServiceImpl;
import com.lifewise.auth.service.ReuseRevoker;
import com.lifewise.shared.infra.security.JwtRefreshTokenService;
import com.lifewise.shared.infra.security.JwtTokenProvider;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
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

@DisplayName("JwtRefreshServiceImpl rotation + reuse detection")
@ExtendWith(MockitoExtension.class)
class JwtRefreshServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Mock private RefreshTokenRepository repository;
    @Mock private OutboxWriter outboxWriter;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private ReuseRevoker reuseRevoker;

    private JwtRefreshServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        // plan-auth review H1：reuse 路径委托给 ReuseRevoker（REQUIRES_NEW 独立事务）。
        // 此处只 mock ReuseRevoker，不直接走 outbox/revoke —— 验证职责分离。
        service = new JwtRefreshServiceImpl(tokenProvider, repository, reuseRevoker, fixed);
    }

    @Test
    @DisplayName("正常 rotate：返回新 access+refresh；旧 row.usedAt 写入；新 row INSERT")
    void should_rotate_and_issue_new_pair() {
        UUID familyId = UUID.randomUUID();
        Long userId = 42L;
        String oldRefresh = "old-refresh-token";
        String oldHash = JwtRefreshServiceImpl.sha256Hex(oldRefresh);

        RefreshToken oldRow = RefreshToken.issue(
                userId, familyId, oldHash,
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).plusDays(30),
                "ua", "ip");
        when(repository.findByTokenHash(oldHash)).thenReturn(Optional.of(oldRow));

        when(tokenProvider.parseRefreshToken(oldRefresh)).thenReturn(
                new JwtRefreshTokenService.RefreshClaims(
                        "old-jti", familyId.toString(), userId,
                        NOW.plus(Duration.ofDays(30))));
        when(tokenProvider.createRefreshToken(userId, familyId.toString()))
                .thenReturn("new-refresh-token");
        when(tokenProvider.createAccessToken(eq(userId), any()))
                .thenReturn("new-access-token");
        when(tokenProvider.parseAccessToken("new-access-token"))
                .thenReturn(new JwtTokenProvider.AccessClaims(
                        userId, List.of("USER"), "new-jti",
                        NOW.plus(Duration.ofMinutes(15))));

        JwtRefreshTokenService.RefreshResult result = service.rotate(oldRefresh).orElseThrow();

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(oldRow.usedAt()).isNotNull();
        ArgumentCaptor<RefreshToken> savedCap = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(2)).save(savedCap.capture());
        RefreshToken child = savedCap.getAllValues().get(1);
        assertThat(child.userId()).isEqualTo(userId);
        assertThat(child.familyId()).isEqualTo(familyId);
        assertThat(child.tokenHash()).isEqualTo(JwtRefreshServiceImpl.sha256Hex("new-refresh-token"));
    }

    @Test
    @DisplayName("reuse 检测：旧 row.usedAt 已非空 → 委托 ReuseRevoker.revokeAndAudit（REQUIRES_NEW）+ 抛 TokenReusedException")
    void should_detect_reuse_and_revoke_family() {
        UUID familyId = UUID.randomUUID();
        Long userId = 42L;
        String oldRefresh = "reused-token";
        String hash = JwtRefreshServiceImpl.sha256Hex(oldRefresh);

        RefreshToken reusedRow = RefreshToken.issue(
                userId, familyId, hash,
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).plusDays(30),
                null, "1.2.3.4");
        reusedRow.markUsed(OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).minusSeconds(60));

        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(reusedRow));
        when(tokenProvider.parseRefreshToken(oldRefresh)).thenReturn(
                new JwtRefreshTokenService.RefreshClaims(
                        "old-jti", familyId.toString(), userId,
                        NOW.plus(Duration.ofDays(30))));

        assertThatThrownBy(() -> service.rotate(oldRefresh))
                .isInstanceOf(TokenReusedException.class);

        // plan-auth review H1：reuse 路径委托 ReuseRevoker（独立事务内 revoke family + append outbox）。
        // JwtRefreshServiceImpl 不再直接调 outboxWriter 或 revokeFamily —— 避免外层 rollback 吃掉 audit。
        ArgumentCaptor<RefreshToken> rowCap = ArgumentCaptor.forClass(RefreshToken.class);
        ArgumentCaptor<UUID> fidCap = ArgumentCaptor.forClass(UUID.class);
        verify(reuseRevoker, times(1)).revokeAndAudit(rowCap.capture(), fidCap.capture(), any());
        assertThat(rowCap.getValue()).isSameAs(reusedRow);
        assertThat(fidCap.getValue()).isEqualTo(familyId);

        // 关键隔离：JwtRefreshServiceImpl 不应直接 append outbox（应走 ReuseRevoker）
        verify(outboxWriter, never()).append(any());
        // 不应直接撤销 family（应走 ReuseRevoker 的独立事务）
        verify(repository, never()).findAllByUserIdAndFamilyId(any(), any());
    }

    @Test
    @DisplayName("expired refresh：parseRefreshToken 抛 JwtExpiredException → 映射 TokenExpiredException")
    void should_map_jwt_expired_to_domain_expired() {
        when(tokenProvider.parseRefreshToken(anyString()))
                .thenThrow(new com.lifewise.shared.infra.security.exception.JwtExpiredException("e"));

        assertThatThrownBy(() -> service.rotate("anything"))
                .isInstanceOf(TokenExpiredException.class);
        verify(repository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("malformed refresh：parseRefreshToken 抛 JwtInvalidException → 映射 TokenInvalidException")
    void should_map_jwt_invalid_to_domain_invalid() {
        when(tokenProvider.parseRefreshToken(anyString()))
                .thenThrow(new com.lifewise.shared.infra.security.exception.JwtInvalidException("bad"));

        assertThatThrownBy(() -> service.rotate("garbage"))
                .isInstanceOf(TokenInvalidException.class);
    }

    @Test
    @DisplayName("未知 refresh：findByTokenHash empty → rotate 返回 Optional.empty")
    void should_return_empty_for_unknown_refresh() {
        when(tokenProvider.parseRefreshToken(anyString())).thenReturn(
                new JwtRefreshTokenService.RefreshClaims(
                        "jti", UUID.randomUUID().toString(), 1L,
                        NOW.plus(Duration.ofDays(30))));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.rotate("unknown-token")).isEmpty();
    }

    @Test
    @DisplayName("claims.userId 与 row.userId 不一致 → 抛 TokenMismatchException，不撤销、不发事件")
    void should_throw_token_mismatch_when_claims_userId_differs_from_row() {
        UUID familyId = UUID.randomUUID();
        Long rowUserId = 42L;
        Long claimsUserId = 99L; // 攻击者用别人的合法签名
        String refresh = "mismatched-user";
        String hash = JwtRefreshServiceImpl.sha256Hex(refresh);

        RefreshToken row = RefreshToken.issue(
                rowUserId, familyId, hash,
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).plusDays(30),
                null, null);
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(row));
        when(tokenProvider.parseRefreshToken(refresh)).thenReturn(
                new JwtRefreshTokenService.RefreshClaims(
                        "jti", familyId.toString(), claimsUserId,
                        NOW.plus(Duration.ofDays(30))));

        assertThatThrownBy(() -> service.rotate(refresh))
                .isInstanceOf(TokenMismatchException.class);

        // 关键：claims 与 row 不一致时，不能撤销任何 token、不能发事件、不能进入 reuse 路径
        // （confused deputy 防御）
        verify(repository, never()).save(any());
        verify(outboxWriter, never()).append(any());
        verify(reuseRevoker, never()).revokeAndAudit(any(), any(), any());
    }

    @Test
    @DisplayName("claims.familyId 与 row.familyId 不一致 → 抛 TokenMismatchException，无副作用")
    void should_throw_token_mismatch_when_claims_familyId_differs_from_row() {
        UUID rowFamilyId = UUID.randomUUID();
        UUID claimsFamilyId = UUID.randomUUID(); // 攻击者家族混淆
        Long userId = 42L;
        String refresh = "mismatched-family";
        String hash = JwtRefreshServiceImpl.sha256Hex(refresh);

        RefreshToken row = RefreshToken.issue(
                userId, rowFamilyId, hash,
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)).plusDays(30),
                null, null);
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(row));
        when(tokenProvider.parseRefreshToken(refresh)).thenReturn(
                new JwtRefreshTokenService.RefreshClaims(
                        "jti", claimsFamilyId.toString(), userId,
                        NOW.plus(Duration.ofDays(30))));

        assertThatThrownBy(() -> service.rotate(refresh))
                .isInstanceOf(TokenMismatchException.class);

        verify(repository, never()).save(any());
        verify(outboxWriter, never()).append(any());
        verify(reuseRevoker, never()).revokeAndAudit(any(), any(), any());
    }
}