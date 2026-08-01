package com.lifewise.shared.infra.security;

import java.time.Instant;
import java.util.Optional;

/**
 * Refresh-token rotation primitive. The concrete implementation lives in the
 * auth module (plan-auth) and depends on this interface, NOT vice-versa.
 *
 * <p>Contract (plan-shared-infra §1, H6):
 * <ul>
 *   <li>{@link #rotate(String)} — issue a new access + refresh pair; empty if the
 *       presented token is unknown / already revoked.</li>
 *   <li>{@link #detectReuse(String)} — invoked when the caller presents a refresh
 *       token whose jti was already rotated; throws {@code ReuseDetectedException}
 *       and triggers family-wide revocation.</li>
 *   <li>{@link #revokeFamily(String)} — purge every refresh token in a family.</li>
 *   <li>{@link #parseClaims(String)} — extract {@link RefreshClaims}; empty if the
 *       token is malformed or revoked.</li>
 * </ul>
 */
public interface JwtRefreshTokenService {

    Optional<RefreshResult> rotate(String refreshToken);

    void detectReuse(String refreshToken);

    void revokeFamily(String familyId);

    Optional<RefreshClaims> parseClaims(String token);

    /**
     * Pair returned by {@link #rotate(String)}. {@code expiresAt} is the access-token expiry.
     */
    record RefreshResult(String accessToken, String refreshToken, Instant expiresAt) {
    }

    /**
     * Decoded claims of a refresh token. {@code familyId} groups rotated children; reuse of any
     * child jti revokes the entire family.
     */
    record RefreshClaims(String jti, String familyId, Long userId, Instant exp) {
    }
}