package com.lifewise.shared.infra.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller / service method whose invocation must be recorded in
 * {@code operation_logs} (V26 schema, see data-model-v1.2-amendment.md) by
 * {@code AuditAspect}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

    /** Dot-prefixed action name, e.g. {@code "task.create"}. */
    String action();

    /** Aggregate (resource) type, e.g. {@code "Task"} — maps to {@code aggregate_type}. */
    String resourceType();

    /**
     * Zero-based method-argument indices to capture into the audit payload.
     * Each captured value is SHA-256 hashed before storage unless {@link #mask()} is true.
     */
    int[] captureArgs() default {};

    /**
     * When {@code true}, captured arguments are stored as {@code ***} instead of hashed.
     * Use for password / token arguments.
     */
    boolean mask() default false;
}