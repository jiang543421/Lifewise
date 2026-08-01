package com.lifewise.shared.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Auditable annotation contract")
class AuditableContractTest {

    @Test
    @DisplayName("annotation exposes action, resource type, selected args and masking")
    void audit_should_define_annotation_contract() throws Exception {
        assertThat(Auditable.class.getMethod("action").getDefaultValue()).isNull();
        assertThat(Auditable.class.getMethod("resourceType").getDefaultValue()).isNull();
        assertThat((int[]) Auditable.class.getMethod("captureArgs").getDefaultValue()).isEmpty();
        assertThat(Auditable.class.getMethod("mask").getDefaultValue()).isEqualTo(false);
    }
}