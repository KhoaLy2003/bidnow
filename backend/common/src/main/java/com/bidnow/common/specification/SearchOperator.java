/*
 * BidNow Auction System
 */
package com.bidnow.common.specification;

/**
 * Enum defining all supported query operators for dynamic specification building.
 * <p>
 * Each operator maps to a corresponding JPA Criteria API predicate method.
 * </p>
 */
public enum SearchOperator {

    /** Equal: field = value */
    EQUAL,

    /** Not equal: field != value */
    NOT_EQUAL,

    /** Greater than: field > value (requires Comparable) */
    GREATER_THAN,

    /** Greater than or equal: field >= value (requires Comparable) */
    GREATER_THAN_OR_EQUAL,

    /** Less than: field < value (requires Comparable) */
    LESS_THAN,

    /** Less than or equal: field <= value (requires Comparable) */
    LESS_THAN_OR_EQUAL,

    /** Is null: field IS NULL (no value required) */
    IS_NULL,

    /** Is not null: field IS NOT NULL (no value required) */
    IS_NOT_NULL,

    /** In collection: field IN (value1, value2, ...) */
    IN,

    /** Not in collection: field NOT IN (value1, value2, ...) */
    NOT_IN,

    /** Like pattern (case-insensitive): LOWER(field) LIKE LOWER(value) */
    LIKE,

    /** Not like pattern (case-insensitive): LOWER(field) NOT LIKE LOWER(value) */
    NOT_LIKE,

    /** Between range: field BETWEEN value AND valueTo (requires Comparable) */
    BETWEEN
}
