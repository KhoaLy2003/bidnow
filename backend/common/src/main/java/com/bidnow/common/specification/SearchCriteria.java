/*
 * BidNow Auction System
 */
package com.bidnow.common.specification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Value object representing a single search condition.
 * <p>
 * Holds the field name (supports nested paths via dot notation, e.g. "category.name"),
 * the operator to apply, and the comparison value(s).
 * </p>
 *
 * <p>Usage examples:</p>
 * <ul>
 *   <li>Simple: {@code new SearchCriteria("status", EQUAL, "ACTIVE", null)}</li>
 *   <li>Between: {@code new SearchCriteria("price", BETWEEN, 100, 500)}</li>
 *   <li>Null check: {@code new SearchCriteria("deletedAt", IS_NULL, null, null)}</li>
 *   <li>Nested: {@code new SearchCriteria("category.name", EQUAL, "Electronics", null)}</li>
 * </ul>
 */
@Getter
@Builder
@AllArgsConstructor
public class SearchCriteria {

    /**
     * The entity field/property name to filter on.
     * Supports dot notation for nested paths (e.g., "category.name").
     */
    private final String field;

    /**
     * The comparison operator to apply.
     */
    private final SearchOperator operator;

    /**
     * The primary comparison value.
     * Can be null for IS_NULL / IS_NOT_NULL operators.
     * Should be a Collection for IN / NOT_IN operators.
     */
    private final Object value;

    /**
     * The secondary comparison value, used only for BETWEEN operator (upper bound).
     */
    private final Object valueTo;
}
