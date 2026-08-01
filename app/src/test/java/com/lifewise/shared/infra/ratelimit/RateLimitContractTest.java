package com.lifewise.shared.infra.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Repeatable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimit annotation contract")
class RateLimitContractTest {

    @Test
    @DisplayName("annotation defaults match the API scope contract")
    void ratelimit_should_define_plan_defaults() throws Exception {
        assertThat(RateLimit.class.getMethod("key").getDefaultValue()).isEqualTo("userId");
        assertThat(RateLimit.class.getMethod("limit").getDefaultValue()).isEqualTo(60);
        assertThat(RateLimit.class.getMethod("window").getDefaultValue()).isEqualTo(60L);
        assertThat(RateLimit.class.getMethod("scope").getDefaultValue()).isEqualTo("api");
    }

    @Test
    @DisplayName("annotation is repeatable for layered AI limits")
    void ratelimit_should_support_layered_limits() {
        assertThat(RateLimit.class.getAnnotation(Repeatable.class).value())
                .isEqualTo(RateLimits.class);
    }
}