package com.labmentix.docsign.audit.annotation;

import com.labmentix.docsign.audit.entity.AuditEventType;

import java.lang.annotation.*;

/**
 * Marks a service method as auditable.
 *
 * When applied, the {@link com.labmentix.docsign.audit.aop.AuditAspect} intercepts
 * the method after successful execution and writes an {@link com.labmentix.docsign.audit.entity.AuditLog}
 * row with the specified event type.
 *
 * Usage:
 * <pre>
 *   {@literal @}Auditable(AuditEventType.DOCUMENT_UPLOADED)
 *   public DocumentResponse uploadDocument(...) { ... }
 * </pre>
 *
 * The aspect extracts context (actor, document ID, IP) from method arguments
 * automatically using well-known parameter types.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** The event type to record in the audit log. */
    AuditEventType value();

    /**
     * Optional human-readable description appended to event_detail JSON.
     * Leave blank to use the default description for the event type.
     */
    String description() default "";

    /**
     * Whether to suppress audit logging if the method throws an exception.
     * Default: true (only log successful operations).
     */
    boolean onlyOnSuccess() default true;
}