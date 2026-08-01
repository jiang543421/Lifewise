package com.lifewise.shared.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Password encoder configuration")
class PasswordEncoderConfigTest {

    @Test
    @DisplayName("BCrypt cost 12 hashes and verifies passwords")
    void security_should_bcrypt_password() {
        var encoder = new PasswordEncoderConfig().passwordEncoder();
        String rawPassword = "Str0ng!Password";

        String encoded = encoder.encode(rawPassword);

        assertThat(encoded).startsWith("$2");
        assertThat(encoded).contains("$12$");
        assertThat(encoder.matches(rawPassword, encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }
}