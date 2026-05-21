package com.bidnow.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class for detailed entry/exit logging via {@link com.bidnow.common.aop.LoggingAspect}.
 *
 * <p>Place on a method for per-method control, or on a class to log all methods in that class.
 * Method-level annotation takes precedence over class-level.
 *
 * <p>Fields annotated with {@link MaskPii} on parameter/return types are automatically redacted.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {

    /** Log level used for entry and exit messages. Exceptions always log at ERROR. */
    Level level() default Level.INFO;

    /** Whether to include parameter names and values in the entry log. */
    boolean logParameters() default true;

    /** Whether to include the return value in the exit log. */
    boolean logResult() default true;

    enum Level {
        INFO, DEBUG
    }
}
