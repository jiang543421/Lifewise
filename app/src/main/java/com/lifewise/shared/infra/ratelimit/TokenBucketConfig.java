package com.lifewise.shared.infra.ratelimit;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the {@link TokenBucketService} (plan-shared-infra §2.2).
 *
 * <p>Default {@link TokenBucketScript} impl is the package-private
 * {@code InMemoryTokenBucketScript}: v1.0 ships no
 * {@code spring-boot-starter-data-redis}, so cross-process rate limiting is
 * a future-plan concern. The in-memory bucket is sufficient for the
 * single-user / single-process v1.0 deployment topology.
 *
 * <p><b>Clock injection note</b>: we deliberately do NOT publish a
 * {@link Clock} bean. {@code auth} module already registers
 * {@code authClock} and the AI module's {@code ConsentVerifier} (and any
 * other consumer) injects {@code Clock} by type — adding a second
 * {@code Clock} bean would trigger {@code NoUniqueBeanDefinitionException}
 * at Spring context start. The {@code TokenBucketService} therefore
 * constructs its own {@code Clock.systemUTC()} inline; unit tests
 * instantiate {@code TokenBucketService} directly with a
 * {@link Clock#fixed} instance (matches the existing
 * {@code TokenBucketServiceTest} pattern).
 *
 * <p><b>Future plan</b>: when the Redis starter is introduced, add a
 * {@code RedisTokenBucketScript} backed by an atomic Lua script, registered
 * with {@code @Bean} + {@code @ConditionalOnMissingBean(TokenBucketScript.class)}
 * to override this default. The {@link TokenBucketScript} functional
 * interface is the seam — see {@code AsyncConfig} for an analogous
 * "future Redis wiring" Javadoc pattern.
 */
@Configuration
public class TokenBucketConfig {

    /**
     * Default in-memory {@link TokenBucketScript}. Replaced by a
     * Redis-backed impl once the Redis starter is added (see class Javadoc).
     */
    @Bean
    public TokenBucketScript tokenBucketScript() {
        return new InMemoryTokenBucketScript();
    }

    /**
     * Composes the script + a process-local UTC clock into a
     * {@link TokenBucketService} that Spring will inject into consumers
     * (e.g. {@code AiRateLimiter}). The {@link Clock} is constructed
     * inline — see class Javadoc for why this is NOT a {@code @Bean}.
     */
    @Bean
    public TokenBucketService tokenBucketService(TokenBucketScript script) {
        return new TokenBucketService(script, Clock.systemUTC());
    }
}
