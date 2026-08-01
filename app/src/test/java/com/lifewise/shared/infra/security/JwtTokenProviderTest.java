package com.lifewise.shared.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifewise.shared.infra.security.exception.JwtExpiredException;
import com.lifewise.shared.infra.security.exception.JwtInvalidException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JWT token provider")
class JwtTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final String ISSUER = "lifewise";
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "lifewise-test-secret-must-be-32-bytes-long".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("access token round trip preserves immutable claims")
    void security_should_round_trip_access_token_claims() {
        JwtTokenProvider provider = providerAt(NOW);

        String token = provider.createAccessToken(42L, List.of("USER", "ADMIN"));
        JwtTokenProvider.AccessClaims claims = provider.parseAccessToken(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.roles()).containsExactly("USER", "ADMIN");
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(ACCESS_TTL));
        assertThatThrownBy(() -> claims.roles().add("OTHER"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("expired access token maps to TOKEN_EXPIRED exception")
    void security_should_reject_expired_jwt() {
        String token = providerAt(NOW).createAccessToken(42L, List.of("USER"));

        assertThatThrownBy(() -> providerAt(NOW.plus(ACCESS_TTL).plusSeconds(1))
                .parseAccessToken(token))
                .isInstanceOf(JwtExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("tampered signature maps to TOKEN_INVALID exception")
    void security_should_reject_tampered_jwt() {
        JwtTokenProvider provider = providerAt(NOW);
        String token = provider.createAccessToken(42L, List.of("USER"));
        String tampered = tamperSignature(token);

        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
                .isInstanceOf(JwtInvalidException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    @DisplayName("refresh token carries jti and family id")
    void security_should_round_trip_refresh_claims() {
        JwtTokenProvider provider = providerAt(NOW);

        String token = provider.createRefreshToken(42L, "family-7");
        JwtRefreshTokenService.RefreshClaims claims = provider.parseRefreshToken(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.familyId()).isEqualTo("family-7");
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.exp()).isEqualTo(NOW.plus(REFRESH_TTL));
    }

    @Test
    @DisplayName("refresh token cannot authenticate an API request")
    void security_should_reject_refresh_token_as_access_token() {
        JwtTokenProvider provider = providerAt(NOW);
        String refreshToken = provider.createRefreshToken(42L, "family-7");

        assertThatThrownBy(() -> provider.parseAccessToken(refreshToken))
                .isInstanceOf(JwtInvalidException.class);
    }

    @Test
    @DisplayName("HS256 secret shorter than 256 bits is rejected")
    void security_should_reject_short_secret() {
        String shortSecret = Base64.getEncoder().encodeToString(
                "too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtTokenProvider(
                shortSecret, ISSUER, ACCESS_TTL, REFRESH_TTL, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256");
    }

    @Test
    @DisplayName("token signed by same secret but different issuer is rejected (iss guard)")
    void security_should_reject_iss_mismatch() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtTokenProvider providerA = new JwtTokenProvider(
                SECRET, "lifewise-a", ACCESS_TTL, REFRESH_TTL, fixedClock);
        JwtTokenProvider providerB = new JwtTokenProvider(
                SECRET, "lifewise-b", ACCESS_TTL, REFRESH_TTL, fixedClock);
        String tokenFromA = providerA.createAccessToken(42L, List.of("USER"));

        assertThatThrownBy(() -> providerB.parseAccessToken(tokenFromA))
                .isInstanceOf(JwtInvalidException.class)
                .hasMessageContaining("issuer");
    }

    private static JwtTokenProvider providerAt(Instant instant) {
        return new JwtTokenProvider(
                SECRET,
                ISSUER,
                ACCESS_TTL,
                REFRESH_TTL,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'a' ? 'b' : 'a';
        parts[2] = replacement + parts[2].substring(1);
        return String.join(".", parts);
    }
}