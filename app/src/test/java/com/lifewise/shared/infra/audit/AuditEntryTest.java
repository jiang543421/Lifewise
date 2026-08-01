package com.lifewise.shared.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Audit entry and payload hashing")
class AuditEntryTest {

    @Test
    @DisplayName("audit entry keeps immutable metadata for V27 operation_logs")
    void audit_should_create_immutable_entry() {
        Instant occurredAt = Instant.parse("2026-07-31T10:00:00Z");
        AuditEntry entry = new AuditEntry(
                42L,
                "task",
                "task.create",
                "Task",
                99L,
                "127.0.0.1",
                "JUnit",
                201,
                12L,
                "trace-1",
                "abc123",
                occurredAt,
                Map.of("result", "success"));

        assertThat(entry.module()).isEqualTo("task");
        assertThat(entry.operation()).isEqualTo("task.create");
        assertThat(entry.payload()).containsEntry("result", "success");
        assertThatThrownBy(() -> entry.payload().put("secret", "leak"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("selected arguments are SHA-256 hashed and never stored as plaintext")
    void audit_should_hash_selected_args() {
        AuditPayloadHasher hasher = new AuditPayloadHasher();

        String first = hasher.hash(new Object[]{"safe", "secret-token"}, new int[]{1}, false);
        String second = hasher.hash(new Object[]{"safe", "secret-token"}, new int[]{1}, false);

        assertThat(first).hasSize(64).isEqualTo(second).doesNotContain("secret-token");
    }

    @Test
    @DisplayName("masked arguments are replaced without hashing their plaintext")
    void audit_should_mask_sensitive_args() {
        AuditPayloadHasher hasher = new AuditPayloadHasher();

        assertThat(hasher.hash(new Object[]{"password"}, new int[]{0}, true)).isEqualTo("***");
    }
}