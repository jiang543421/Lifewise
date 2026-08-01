package com.lifewise.shared.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.shared.infra.security.annotation.RequireAuth;
import com.lifewise.shared.infra.security.annotation.RequireRole;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Security annotation contracts")
class SecurityAnnotationContractTest {

    @Test
    @DisplayName("RequireAuth is available at runtime on methods and types")
    void security_should_define_require_auth_annotation() {
        assertThat(RequireAuth.class.getAnnotation(Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(RequireAuth.class.getAnnotation(Target.class).value())
                .containsExactlyInAnyOrder(ElementType.METHOD, ElementType.TYPE);
    }

    @Test
    @DisplayName("RequireRole carries the required role")
    void security_should_define_require_role_annotation() throws Exception {
        RequireRole annotation = ProtectedActions.class
                .getDeclaredMethod("adminOnly")
                .getAnnotation(RequireRole.class);

        assertThat(annotation.value()).isEqualTo("ADMIN");
    }

    private static final class ProtectedActions {

        @RequireRole("ADMIN")
        void adminOnly() {
        }
    }
}