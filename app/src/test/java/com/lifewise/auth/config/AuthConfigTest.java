package com.lifewise.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.shared.infra.security.JwtTokenProvider;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AuthConfig 启动校验（plan-auth review C1 修复）。
 *
 * <p>{@code auth.jwt.secret-base64} 必须由部署侧 env 显式注入；缺失时
 * Spring 启动失败，{@link JwtTokenProvider} 构造抛
 * {@link IllegalArgumentException}（secret 长度 &lt; 32 字节）。
 */
@DisplayName("AuthConfig 启动校验：JWT secret 必填且 >= 32 字节")
class AuthConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(AuthConfig.class, TestConfig.class);

    @Test
    @DisplayName("缺失 auth.jwt.secret-base64 → 启动失败")
    void should_fail_to_load_when_secret_property_missing() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .isInstanceOf(BeanCreationException.class)
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("auth.jwt.secret-base64 长度不足 32 字节 → 启动失败")
    void should_fail_to_load_when_secret_too_short() {
        runner.withPropertyValues(
                        "auth.jwt.secret-base64=YWJjZGVm") // 6 bytes after decode
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    @DisplayName("正确长度的 secret → 启动成功")
    void should_load_when_secret_is_32_bytes_or_more() {
        // 32 bytes plaintext "01234567890123456789012345678901" Base64-encoded
        runner.withPropertyValues(
                        "auth.jwt.secret-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(JwtTokenProvider.class);
                });
    }

    @Configuration
    static class TestConfig {
        @Bean
        public Clock testClock() {
            return Clock.systemUTC();
        }
    }
}
