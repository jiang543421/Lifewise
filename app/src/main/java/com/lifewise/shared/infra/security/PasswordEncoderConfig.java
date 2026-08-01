package com.lifewise.shared.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt password encoder with cost 12 (CLAUDE.md §7.3 + plan-shared-infra §1).
 *
 * <p>Cost 12 raises the per-hash CPU budget to ~250ms which is acceptable for an
 * interactive login endpoint and mitigates offline brute-force on a leaked dump.
 */
@Configuration
public class PasswordEncoderConfig {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}