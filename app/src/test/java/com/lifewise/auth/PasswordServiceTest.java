package com.lifewise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifewise.auth.domain.exception.WeakPasswordException;
import com.lifewise.auth.service.PasswordService;
import com.lifewise.shared.infra.security.PasswordEncoderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PasswordService 强度校验 + BCrypt")
class PasswordServiceTest {

    private final PasswordService service = new PasswordService(
            new PasswordEncoderConfig().passwordEncoder());

    @Test
    @DisplayName("满足 5 条规则的密码通过校验")
    void should_accept_strong_password() {
        assertThatCode(() -> service.assertStrong("Str0ng!Password"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("短密码（< 12）抛 WeakPasswordException")
    void should_reject_short_password() {
        assertThatThrownBy(() -> service.assertStrong("Ab1!aaaa"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("12 characters");
    }

    @Test
    @DisplayName("缺大写字母抛 WeakPasswordException")
    void should_reject_no_uppercase() {
        assertThatThrownBy(() -> service.assertStrong("str0ng!password"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    @DisplayName("缺数字抛 WeakPasswordException")
    void should_reject_no_digit() {
        assertThatThrownBy(() -> service.assertStrong("Strong!Password"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("digit");
    }

    @Test
    @DisplayName("缺符号抛 WeakPasswordException")
    void should_reject_no_symbol() {
        assertThatThrownBy(() -> service.assertStrong("Strong1Password"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("symbol");
    }

    @Test
    @DisplayName("BCrypt cost=12 哈希 + matches 验证")
    void should_hash_and_verify_with_bcrypt() {
        String raw = "Str0ng!Password";
        String hash = service.hash(raw);
        assertThat(hash).startsWith("$2");
        assertThat(hash).contains("$12$");
        assertThat(service.matches(raw, hash)).isTrue();
        assertThat(service.matches("wrong", hash)).isFalse();
    }
}