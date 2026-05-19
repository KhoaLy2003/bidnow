package com.bidnow.common.util;

import com.bidnow.common.annotation.MaskPii;
import jakarta.persistence.Id;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for calculating field-level differences between two objects
 * and applying PII masking.
 */
@Slf4j
@UtilityClass
public class DiffUtils {

    private static final Set<String> EXCLUDED_FIELDS = Set.of(
            "serialVersionUID",
            "userId"
    );

    /**
     * Calculates the differences between two objects of the same type.
     * Identity fields (@Id) and audit fields (createdAt, updatedAt) are excluded.
     *
     * @param oldObj The original object (before change)
     * @param newObj The updated object (after change)
     * @return A map containing changed fields with their old and new values:
     * { "fieldName": { "old": "v1", "new": "v2" } }
     */
    public static Map<String, Map<String, Object>> calculateDiff(Object oldObj, Object newObj) {
        Map<String, Map<String, Object>> diff = new HashMap<>();

        if (oldObj == null && newObj == null) {
            return diff;
        }

        Class<?> clazz = (oldObj != null) ? oldObj.getClass() : newObj.getClass();

        // If one is null, treat all fields as changed
        if (oldObj == null || newObj == null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (shouldSkipField(field)) continue;
                addFieldToDiff(diff, field, oldObj, newObj);
            }
            return diff;
        }

        // Both are non-null, compare fields
        for (Field field : clazz.getDeclaredFields()) {
            if (shouldSkipField(field)) continue;
            addFieldToDiff(diff, field, oldObj, newObj);
        }

        return diff;
    }

    private static boolean shouldSkipField(Field field) {
        if (EXCLUDED_FIELDS.contains(field.getName())) {
            return true;
        }
        if (field.isAnnotationPresent(Id.class)) {
            return true;
        }
        return false;
    }

    private static void addFieldToDiff(Map<String, Map<String, Object>> diff, Field field, Object oldObj, Object newObj) {
        field.setAccessible(true);
        try {
            Object oldValue = (oldObj != null) ? field.get(oldObj) : null;
            Object newValue = (newObj != null) ? field.get(newObj) : null;

            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> values = new HashMap<>();
                values.put("old", applyMasking(field, oldValue));
                values.put("new", applyMasking(field, newValue));
                diff.put(field.getName(), values);
            }
        } catch (IllegalAccessException e) {
            log.error("Failed to access field: {}", field.getName(), e);
        }
    }

    private static Object applyMasking(Field field, Object value) {
        if (value == null || !field.isAnnotationPresent(MaskPii.class)) {
            return value;
        }

        MaskPii annotation = field.getAnnotation(MaskPii.class);
        String stringValue = String.valueOf(value);

        if (annotation.fully()) {
            return "***REDACTED***";
        }

        int keepLast = annotation.keepLast();
        if (stringValue.length() <= keepLast) {
            return stringValue;
        }

        return "*".repeat(stringValue.length() - keepLast) + stringValue.substring(stringValue.length() - keepLast);
    }
}
