package com.lifewise.shared.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Refresh token service contract")
class JwtRefreshTokenServiceContractTest {

    @Test
    @DisplayName("service exposes rotation and reuse-detection boundary")
    void security_should_expose_refresh_rotation_contract() throws Exception {
        Method rotate = JwtRefreshTokenService.class.getMethod("rotate", String.class);
        Method detectReuse = JwtRefreshTokenService.class.getMethod("detectReuse", String.class);
        Method revokeFamily = JwtRefreshTokenService.class.getMethod("revokeFamily", String.class);
        Method parseClaims = JwtRefreshTokenService.class.getMethod("parseClaims", String.class);

        assertThat(rotate.getReturnType()).isEqualTo(Optional.class);
        assertThat(optionalArgument(rotate)).isEqualTo(JwtRefreshTokenService.RefreshResult.class);
        assertThat(detectReuse.getReturnType()).isEqualTo(void.class);
        assertThat(revokeFamily.getReturnType()).isEqualTo(void.class);
        assertThat(parseClaims.getReturnType()).isEqualTo(Optional.class);
        assertThat(optionalArgument(parseClaims)).isEqualTo(JwtRefreshTokenService.RefreshClaims.class);
    }

    @Test
    @DisplayName("contract records preserve the plan-defined fields")
    void security_should_define_refresh_contract_records() {
        var result = new JwtRefreshTokenService.RefreshResult(
                "access", "refresh", Instant.parse("2026-08-30T10:00:00Z"));
        var claims = new JwtRefreshTokenService.RefreshClaims(
                "jti-1", "family-1", 42L, Instant.parse("2026-08-30T10:00:00Z"));

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.expiresAt()).isAfter(Instant.parse("2026-07-31T10:00:00Z"));
        assertThat(claims.jti()).isEqualTo("jti-1");
        assertThat(claims.familyId()).isEqualTo("family-1");
        assertThat(claims.userId()).isEqualTo(42L);
    }

    private static Object optionalArgument(Method method) {
        return ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
    }
}