package com.bidnow.common.annotation;

import com.bidnow.common.enums.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    AuditAction action();

    String entityType();

    String reason() default "";

    boolean captureDelta() default true;
}
