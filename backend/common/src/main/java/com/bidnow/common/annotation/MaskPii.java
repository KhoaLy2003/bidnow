package com.bidnow.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields that should be masked in audit logs (PII).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskPii {
    /**
     * Whether to redact the field completely or partially.
     * Default is true (complete redaction).
     */
    boolean fully() default true;

    /**
     * Number of characters to keep unmasked at the end if fully() is false.
     */
    int keepLast() default 4;
}
