package com.bidnow.common.aop;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.annotation.MaskPii;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * AOP aspect that logs method entry, exit, execution time, and exceptions for methods
 * (or all methods in classes) annotated with {@link Loggable}.
 *
 * <p>Log lines are emitted through SLF4J, so {@code traceId} and {@code spanId} from
 * Micrometer Tracing MDC appear automatically in every log entry when a span is active.
 *
 * <p>Fields annotated with {@link MaskPii} on parameter or return value types are
 * redacted before logging.
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    private static final int MAX_VALUE_LENGTH = 200;

    @Around("@annotation(com.bidnow.common.annotation.Loggable) || @within(com.bidnow.common.annotation.Loggable)")
    public Object logMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Loggable loggable = resolveAnnotation(signature);

        String label = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        if (loggable.logParameters()
                && !(loggable.level() == Loggable.Level.DEBUG && !log.isDebugEnabled())) {
            String params = buildParamsString(signature.getParameterNames(), joinPoint.getArgs());
            doLog(loggable.level(), ">>> {} ({})", label, params);
        } else {
            doLog(loggable.level(), ">>> {}", label);
        }

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;

            if (loggable.logResult() && result != null) {
                doLog(loggable.level(), "<<< {} completed in {}ms | result: {}",
                        label, elapsed, truncate(sanitize(result)));
            } else {
                doLog(loggable.level(), "<<< {} completed in {}ms", label, elapsed);
            }

            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("<<< {} threw {} in {}ms: {}", label, ex.getClass().getSimpleName(), elapsed, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Method-level annotation takes precedence over class-level.
     */
    private Loggable resolveAnnotation(MethodSignature signature) {
        Loggable methodLevel = signature.getMethod().getAnnotation(Loggable.class);
        if (methodLevel != null) return methodLevel;
        return (Loggable) signature.getDeclaringType().getAnnotation(Loggable.class);
    }

    private String buildParamsString(String[] names, Object[] args) {
        if (args == null || args.length == 0) return "";
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < args.length; i++) {
            String name = (names != null && i < names.length) ? names[i] : "arg" + i;
            joiner.add(name + "=" + truncate(sanitize(args[i])));
        }
        return joiner.toString();
    }

    /**
     * Converts a value to a loggable string, masking any {@link MaskPii}-annotated
     * fields on complex object types.
     */
    private String sanitize(Object value) {
        if (value == null) return "null";
        if (isSimpleType(value)) return String.valueOf(value);

        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            Class<?> cls = value.getClass();
            while (cls != null && cls != Object.class) {
                for (Field field : cls.getDeclaredFields()) {
                    if (field.isSynthetic()) continue;
                    field.setAccessible(true);
                    fields.put(field.getName(), maskIfNeeded(field, field.get(value)));
                }
                cls = cls.getSuperclass();
            }
            return fields.toString();
        } catch (Exception e) {
            // Fall back to identity string to avoid logging failures crashing the method
            return value.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(value));
        }
    }

    private Object maskIfNeeded(Field field, Object value) {
        if (value == null || !field.isAnnotationPresent(MaskPii.class)) return value;
        MaskPii mask = field.getAnnotation(MaskPii.class);
        if (mask.fully()) return "***";
        String str = String.valueOf(value);
        int keep = mask.keepLast();
        if (str.length() <= keep) return str;
        return "*".repeat(str.length() - keep) + str.substring(str.length() - keep);
    }

    private boolean isSimpleType(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value.getClass().isPrimitive()
                || value.getClass().isEnum();
    }

    private String truncate(String value) {
        if (value == null) return "null";
        return value.length() > MAX_VALUE_LENGTH
                ? value.substring(0, MAX_VALUE_LENGTH) + "..."
                : value;
    }

    private void doLog(Loggable.Level level, String format, Object... args) {
        if (level == Loggable.Level.DEBUG) {
            log.debug(format, args);
        } else {
            log.info(format, args);
        }
    }
}
