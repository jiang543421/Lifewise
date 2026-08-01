package com.lifewise.auth.service;

import com.lifewise.auth.domain.RefreshToken;
import com.lifewise.auth.domain.exception.TokenExpiredException;
import com.lifewise.auth.domain.exception.TokenInvalidException;
import com.lifewise.auth.domain.exception.TokenMismatchException;
import com.lifewise.auth.domain.exception.TokenReusedException;
import com.lifewise.auth.repository.RefreshTokenRepository;
import com.lifewise.shared.infra.security.JwtRefreshTokenService;
import com.lifewise.shared.infra.security.JwtTokenProvider;
import com.lifewise.shared.infra.security.exception.JwtExpiredException;
import com.lifewise.shared.infra.security.exception.JwtInvalidException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh token rotation + reuse detection 实现（plan-auth §5.2）。
 *
 * <p>持有 SHA-256(refresh) → row 映射；rotation 流程：
 * <ol>
 *   <li>解析 refresh JWT → claims (jti, familyId, userId, exp)</li>
 *   <li>按 tokenHash 查找 row；若不存在 → {@link TokenInvalidException}</li>
 *   <li>若 row.revokedAt 非空 → reuse detected（family 已撤销） → 抛 {@link TokenReusedException}</li>
 *   <li>若 row.usedAt 非空 → reuse detected（曾被 rotation 消费过） →
 *       撤销该 family 全部 token，发布 {@code auth.token.reuse_detected} 事件 → 抛 {@link TokenReusedException}</li>
 *   <li>若 row.expiresAt &lt; now → 抛 {@link TokenExpiredException}</li>
 *   <li>markUsed(now)；生成新 refresh（同一 familyId）；返回新 (access, refresh, exp)</li>
 * </ol>
 */
@Service
public class JwtRefreshServiceImpl implements JwtRefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int REFRESH_BYTES = 64;

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRepository repository;
    private final ReuseRevoker reuseRevoker;
    private final Clock clock;
    private final Duration refreshTtl;

    public JwtRefreshServiceImpl(
            JwtTokenProvider tokenProvider,
            RefreshTokenRepository repository,
            ReuseRevoker reuseRevoker,
            Clock authClock) {
        this.tokenProvider = tokenProvider;
        this.repository = repository;
        this.reuseRevoker = reuseRevoker;
        this.clock = authClock;
        // refreshTtl 与 JwtTokenProvider 共享；从 provider 不可直接拿到，反向约定：
        // JwtTokenProvider.refreshTtl 是 plan 锁定的 30 天，与本服务一致；
        // 通过 Clock + repo.expiresAt 判过期，不依赖本地 TTL 字段。
        this.refreshTtl = Duration.ofDays(30);
    }

    /**
     * 旋转：使用旧 refresh → 发放新 access + 新 refresh + mark old used。
     * 同时处理 reuse detection 与 expiry。
     */
    @Override
    @Transactional
    public Optional<RefreshResult> rotate(String refreshToken) {
        JwtRefreshTokenService.RefreshClaims claims;
        try {
            claims = tokenProvider.parseRefreshToken(refreshToken);
        } catch (JwtExpiredException e) {
            throw new TokenExpiredException();
        } catch (JwtInvalidException e) {
            throw new TokenInvalidException(e.getMessage());
        }

        String hash = sha256Hex(refreshToken);
        Optional<RefreshToken> maybe = repository.findByTokenHash(hash);
        if (maybe.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken row = maybe.get();
        OffsetDateTime now = OffsetDateTime.now(clock);

        // plan-auth review H2 修复：claims 与 row 交叉验证（confused deputy 防御）。
        // JWT signature 通过不代表 claims 与 DB row 对齐：攻击者拿到合法签名但
        // 替换 payload 会被 signature 拦截，但如果 token_hash 在 DB 命中但
        // claims.userId/familyId 与 row 不一致，说明 token 已被替换或 row
        // 被改动——任何 family 操作都可能撤销无辜 token，必须 fail-fast。
        verifyClaimAlignment(row, claims);

        // reuse detection：family 内任何成员若 usedAt 非空或 revokedAt 非空
        if (row.revokedAt() != null) {
            throw new TokenReusedException(claims.familyId());
        }
        if (row.usedAt() != null) {
            // plan-auth review H1 修复：family 撤销 + outbox audit 走 REQUIRES_NEW 独立事务，
            // 外层 rotate() 即将抛 TokenReusedException 触发 rollback；audit 必须落库
            reuseRevoker.revokeAndAudit(row, UUID.fromString(claims.familyId()), now);
            throw new TokenReusedException(claims.familyId());
        }
        if (!row.isUsable(now)) {
            throw new TokenExpiredException();
        }

        // markUsed + 发放新 token（同 familyId）
        row.markUsed(now);
        repository.save(row);

        String newRefresh = tokenProvider.createRefreshToken(row.userId(), claims.familyId());
        String newHash = sha256Hex(newRefresh);
        OffsetDateTime newExp = OffsetDateTime.now(clock).plus(refreshTtl);
        RefreshToken child = RefreshToken.issue(
                row.userId(),
                UUID.fromString(claims.familyId()),
                newHash,
                newExp,
                null,
                null);
        repository.save(child);

        String access = tokenProvider.createAccessToken(row.userId(), ListOfRole.user());
        return Optional.of(new RefreshResult(access, newRefresh, tokenProvider
                .parseAccessToken(access).expiresAt()));
    }

    @Override
    public void detectReuse(String refreshToken) {
        // rotate() 已覆盖；此接口作为外部 hook 入口
        Optional<RefreshResult> result = rotate(refreshToken);
        // result 不为 empty 且无异常 = 正常 rotation；本方法语义为「外部触发 reuse 检测」，
        // 若 refresh 合法则 no-op；若被复用则 rotate() 会抛 TokenReusedException。
        if (result.isEmpty()) {
            throw new TokenInvalidException("refresh token unknown");
        }
    }

    @Override
    @Transactional
    public void revokeFamily(String familyId) {
        // 不带 userId 时按 familyId 全表查
        UUID fid;
        try {
            fid = UUID.fromString(familyId);
        } catch (IllegalArgumentException e) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        repository.findAll().stream()
                .filter(rt -> fid.equals(rt.familyId()))
                .filter(rt -> rt.revokedAt() == null)
                .forEach(rt -> {
                    rt.revoke(now);
                    repository.save(rt);
                });
    }

    @Override
    public Optional<RefreshClaims> parseClaims(String token) {
        try {
            return Optional.of(tokenProvider.parseRefreshToken(token));
        } catch (JwtInvalidException | JwtExpiredException e) {
            return Optional.empty();
        }
    }

    /**
     * plan-auth review H2：交叉验证 JWT claims 与 DB row 的 userId / familyId
     * 必须一致。不一致抛 {@link TokenMismatchException}（ErrorCode.TOKEN_INVALID），
     * 不调用 {@code repository.save()}、不调用 {@code outboxWriter.append()}、
     * 不撤销 family —— confused deputy 防御：若允许不一致 token 触发
     * revokeFamily，可能误伤无辜用户的活跃 session。
     *
     * <p>调用方负责在 reuse detection 之前调用（本类 {@link #rotate(String)}）。
     */
    private void verifyClaimAlignment(RefreshToken row, RefreshClaims claims) {
        if (!java.util.Objects.equals(row.userId(), claims.userId())) {
            throw new TokenMismatchException(
                    "claims.userId=" + claims.userId() + " != row.userId=" + row.userId());
        }
        UUID rowFamily = row.familyId();
        UUID claimsFamily;
        try {
            claimsFamily = UUID.fromString(claims.familyId());
        } catch (IllegalArgumentException e) {
            throw new TokenMismatchException("claims.familyId is not a valid UUID: " + claims.familyId());
        }
        if (!rowFamily.equals(claimsFamily)) {
            throw new TokenMismatchException(
                    "claims.familyId=" + claims.familyId() + " != row.familyId=" + rowFamily);
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String randomRefreshToken() {
        byte[] bytes = new byte[REFRESH_BYTES];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 单角色占位（auth 模块 v1.0 不引入 RBAC；plan-auth §2.2 role 字段保留扩展位） */
    private static final class ListOfRole {
        static java.util.List<String> user() {
            return java.util.List.of("USER");
        }
    }
}