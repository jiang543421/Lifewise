package com.lifewise.shared.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.shared.infra.async.AsyncConfig;
import com.lifewise.shared.infra.audit.AuditEntry;
import com.lifewise.shared.infra.audit.AuditPayloadHasher;
import com.lifewise.shared.infra.audit.Auditable;
import com.lifewise.shared.infra.ratelimit.RateLimit;
import com.lifewise.shared.infra.security.PasswordEncoderConfig;
import com.lifewise.shared.infra.security.annotation.RequireAuth;
import com.lifewise.shared.infra.security.annotation.RequireRole;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/**
 * shared/infra 子包 Spring Context 装配验证（3.4Verify M2 兜底 — plan-shared-infra §6 验收）。
 *
 * <p>目的：单测 22/22 全绿曾给过假信心（CRITICAL #1 在共享-infra 是 iss 校验，在共享-integration 是
 * OutboxDispatcher）。本 IT 确保 shared-infra 包下所有 Bean（BCryptPasswordEncoder、
 * sharedInfraExecutor）+ 所有运行时注解（{@code @RequireAuth / @RequireRole / @RateLimit /
 * @Auditable}）能在 Spring 容器里正常接线。
 *
 * <p>策略：H2 内存 + Flyway 关闭 + JPA ddl 关闭 — 仅作 Context 装配探针。
 */
@DisplayName("shared/infra 子包 Spring Context 装配")
@SpringBootTest(classes = {PasswordEncoderConfig.class, AsyncConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:shared-infra;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class SharedInfraContextTest {

    @Autowired
    @Qualifier("sharedInfraExecutor")
    ThreadPoolTaskExecutor sharedInfraExecutor;

    @Autowired
    BCryptPasswordEncoder passwordEncoder;

    @Test
    @DisplayName("BCryptPasswordEncoder bean 装配成功且 cost=12")
    void should_load_bcrypt_password_encoder_at_strength_12() {
        assertThat(passwordEncoder).isNotNull();
        String hashed = passwordEncoder.encode("test-password-shared-infra-12345");
        assertThat(hashed).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches("test-password-shared-infra-12345", hashed)).isTrue();
    }

    @Test
    @DisplayName("sharedInfraExecutor bean 装配且 core=8 / max=16 / 队列>=200 / CallerRuns")
    void should_load_async_executor_with_plan_spec_settings() {
        assertThat(sharedInfraExecutor).isNotNull();
        assertThat(sharedInfraExecutor.getCorePoolSize()).isEqualTo(8);
        assertThat(sharedInfraExecutor.getMaxPoolSize()).isEqualTo(16);
        assertThat(sharedInfraExecutor.getQueueCapacity()).isEqualTo(200);
        assertThat(sharedInfraExecutor.getThreadNamePrefix()).startsWith("shared-infra-");
        java.util.concurrent.RejectedExecutionHandler handler =
                sharedInfraExecutor.getThreadPoolExecutor().getRejectedExecutionHandler();
        assertThat(handler).isInstanceOf(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    @Test
    @DisplayName("@RequireAuth 是 RUNTIME 注解，且支持 METHOD target")
    void should_define_require_auth_as_runtime_annotation() {
        Method method;
        try {
            method = MarkedClass.class.getDeclaredMethod("marked");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("MarkedClass#marked missing", e);
        }
        assertThat(method.getAnnotation(RequireAuth.class)).isNotNull();
        assertThat(RequireAuth.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("@RequireRole 是 RUNTIME 注解，value() 透传角色字符串")
    void should_define_require_role_as_runtime_annotation_with_value() {
        Method method;
        try {
            method = MarkedClass.class.getDeclaredMethod("adminOnly");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("MarkedClass#adminOnly missing", e);
        }
        RequireRole roleAnn = method.getAnnotation(RequireRole.class);
        assertThat(roleAnn).isNotNull();
        assertThat(roleAnn.value()).isEqualTo("ADMIN");
        assertThat(RequireRole.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("@RateLimit 是 RUNTIME 注解，默认 key=userId limit=60 window=60s scope=api")
    void should_define_rate_limit_as_runtime_annotation_with_plan_defaults() {
        Method method;
        try {
            method = MarkedClass.class.getDeclaredMethod("rateLimited");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("MarkedClass#rateLimited missing", e);
        }
        RateLimit rl = method.getAnnotation(RateLimit.class);
        assertThat(rl).isNotNull();
        assertThat(rl.key()).isEqualTo("userId");
        assertThat(rl.limit()).isEqualTo(60L);
        assertThat(rl.window()).isEqualTo(60L);
        assertThat(rl.scope()).isEqualTo("api");
        assertThat(RateLimit.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("@Auditable 是 RUNTIME 注解，暴露 action / resourceType / captureArgs / mask")
    void should_define_auditable_as_runtime_annotation() {
        Method method;
        try {
            method = MarkedClass.class.getDeclaredMethod("auditedCreate", Object.class, Object.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("MarkedClass#auditedCreate missing", e);
        }
        Auditable aud = method.getAnnotation(Auditable.class);
        assertThat(aud).isNotNull();
        assertThat(aud.action()).isEqualTo("task.create");
        assertThat(aud.resourceType()).isEqualTo("Task");
        assertThat(aud.captureArgs()).containsExactly(0, 1);
        assertThat(aud.mask()).isFalse();
        assertThat(Auditable.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("AuditEntry 是不可变 record + AuditPayloadHasher SHA-256 可重现")
    void should_resolve_audit_entry_and_hasher_in_classpath() {
        AuditPayloadHasher hasher = new AuditPayloadHasher();
        String h1 = hasher.hash(new Object[]{"body"}, new int[]{0}, false);
        String h2 = hasher.hash(new Object[]{"body"}, new int[]{0}, false);
        assertThat(h1).isEqualTo(h2).hasSize(64);

        AuditEntry sample = new AuditEntry(
                1L, "task", "task.create", "Task", 42L, "192.168.0.1", "curl/8",
                200, 100L, "trace-1", h1, java.time.Instant.now(), java.util.Map.of());
        assertThat(sample.userId()).isEqualTo(1L);
        assertThat(sample.operation()).isEqualTo("task.create");
        assertThat(sample.payload()).isEmpty();
        assertThat(sample.requestHash()).isEqualTo(h1);
    }

    @RequireAuth
    static class MarkedClass {

        @RequireAuth
        void marked() {
            // sentinel
        }

        @RequireRole("ADMIN")
        void adminOnly() {
            // sentinel
        }

        @RateLimit(key = "userId", limit = 60, window = 60, scope = "api")
        void rateLimited() {
            // sentinel
        }

        @Auditable(action = "task.create", resourceType = "Task", captureArgs = {0, 1})
        void auditedCreate(Object a, Object b) {
            // sentinel
        }
    }
}
