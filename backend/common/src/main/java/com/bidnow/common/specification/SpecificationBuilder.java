/*
 * BidNow Auction System
 */
package com.bidnow.common.specification;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for constructing dynamic JPA {@link Specification} instances.
 * <p>
 * Provides a clean, reusable API for building WHERE conditions dynamically
 * across any JPA entity, eliminating boilerplate Specification code in service layers.
 * </p>
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Top-level conditions are combined with <strong>AND</strong> logic.</li>
 *   <li>Use {@link #orGroup(Consumer)} to create sub-groups combined with <strong>OR</strong> logic.</li>
 *   <li>{@code withIfPresent} / {@code withLikeIfPresent} variants automatically skip null/blank values.</li>
 *   <li>Supports nested property paths via dot notation (e.g., "category.name").</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * Specification<Auction> spec = SpecificationBuilder.<Auction>forEntity()
 *     .withIfPresent("status", SearchOperator.EQUAL, status)
 *     .withIfPresent("category.name", SearchOperator.EQUAL, categoryName)
 *     .withBetween("startPrice", minPrice, maxPrice)
 *     .orGroup(or -> or
 *         .withLike("title", "%" + search + "%")
 *         .withLike("description", "%" + search + "%")
 *     )
 *     .build();
 *
 * Page<Auction> results = auctionRepository.findAll(spec, pageable);
 * }</pre>
 *
 * @param <T> the entity type
 */
public class SpecificationBuilder<T> {

    private final List<SearchCriteria> andCriteria = new ArrayList<>();
    private final List<Specification<T>> additionalSpecs = new ArrayList<>();

    private SpecificationBuilder() {
    }

    /**
     * Creates a new builder instance for the given entity type.
     *
     * @param <T> the entity type
     * @return a new empty SpecificationBuilder
     */
    public static <T> SpecificationBuilder<T> forEntity() {
        return new SpecificationBuilder<>();
    }

    // ==================== Core Methods ====================

    /**
     * Adds a condition with the specified field, operator, and value.
     *
     * @param field    the entity field name (supports dot notation for nested paths)
     * @param operator the comparison operator
     * @param value    the comparison value
     * @return this builder for chaining
     * @throws IllegalArgumentException if field or operator is null
     */
    public SpecificationBuilder<T> with(String field, SearchOperator operator, Object value) {
        validateField(field);
        validateOperator(operator);
        andCriteria.add(new SearchCriteria(field, operator, value, null));
        return this;
    }

    /**
     * Adds a BETWEEN condition: {@code field BETWEEN from AND to}.
     *
     * @param field the entity field name
     * @param from  the lower bound (inclusive)
     * @param to    the upper bound (inclusive)
     * @return this builder for chaining
     * @throws IllegalArgumentException if field is null, or from/to is null
     */
    public SpecificationBuilder<T> withBetween(String field, Object from, Object to) {
        validateField(field);
        if (from == null || to == null) {
            throw new IllegalArgumentException("BETWEEN requires both 'from' and 'to' values");
        }
        andCriteria.add(new SearchCriteria(field, SearchOperator.BETWEEN, from, to));
        return this;
    }

    /**
     * Adds a case-insensitive LIKE condition.
     * <p>
     * The caller is responsible for including SQL wildcards (%) in the pattern.
     * </p>
     *
     * @param field   the entity field name
     * @param pattern the LIKE pattern (e.g., "%search%")
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> withLike(String field, String pattern) {
        validateField(field);
        andCriteria.add(new SearchCriteria(field, SearchOperator.LIKE, pattern, null));
        return this;
    }

    /**
     * Adds an IN condition: {@code field IN (values)}.
     *
     * @param field  the entity field name
     * @param values the collection of values to match against
     * @return this builder for chaining
     * @throws IllegalArgumentException if values is null or empty
     */
    public SpecificationBuilder<T> withIn(String field, Collection<?> values) {
        validateField(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("IN operator requires a non-empty collection");
        }
        andCriteria.add(new SearchCriteria(field, SearchOperator.IN, values, null));
        return this;
    }

    /**
     * Adds an IS_NULL condition: {@code field IS NULL}.
     *
     * @param field the entity field name
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> withIsNull(String field) {
        validateField(field);
        andCriteria.add(new SearchCriteria(field, SearchOperator.IS_NULL, null, null));
        return this;
    }

    /**
     * Adds an IS_NOT_NULL condition: {@code field IS NOT NULL}.
     *
     * @param field the entity field name
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> withIsNotNull(String field) {
        validateField(field);
        andCriteria.add(new SearchCriteria(field, SearchOperator.IS_NOT_NULL, null, null));
        return this;
    }

    /**
     * Adds an IS_NOT_NULL condition only if {@code condition} is true.
     *
     * @param field     the entity field name
     * @param condition when false the condition is skipped entirely
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> withIsNotNullIf(String field, boolean condition) {
        if (condition) {
            withIsNotNull(field);
        }
        return this;
    }

    // ==================== Conditional Methods (skip if null/blank) ====================

    /**
     * Adds a condition only if the value is non-null.
     * <p>
     * For String values, also checks that the value is not blank.
     * This eliminates the need for manual {@code if (value != null)} checks at the call site.
     * </p>
     *
     * @param field    the entity field name
     * @param operator the comparison operator
     * @param value    the comparison value (skipped if null or blank string)
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> withIfPresent(String field, SearchOperator operator, Object value) {
        if (isValuePresent(value)) {
            with(field, operator, value);
        }
        return this;
    }

    /**
     * Adds a case-insensitive LIKE condition only if the pattern is non-null and non-blank.
     * <p>
     * Automatically wraps the value with SQL wildcards: {@code %value%}.
     * </p>
     *
     * @param field the entity field name
     * @param value the search value (without wildcards; skipped if null or blank)
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> withLikeIfPresent(String field, String value) {
        if (StringUtils.hasText(value)) {
            withLike(field, "%" + value + "%");
        }
        return this;
    }

    /**
     * Adds a BETWEEN condition only if both bounds are non-null.
     *
     * @param field the entity field name
     * @param from  the lower bound (skipped if null)
     * @param to    the upper bound (skipped if null)
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> withBetweenIfPresent(String field, Object from, Object to) {
        if (from != null && to != null) {
            withBetween(field, from, to);
        }
        return this;
    }

    /**
     * Adds an IN condition only if the collection is non-null and non-empty.
     *
     * @param field  the entity field name
     * @param values the collection of values (skipped if null or empty)
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> withInIfPresent(String field, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            withIn(field, values);
        }
        return this;
    }

    // ==================== OR Grouping ====================

    /**
     * Creates an OR group where all conditions within the group are combined with OR logic.
     * <p>
     * The resulting OR group is then ANDed with the other top-level conditions.
     * If the inner builder produces no conditions, this group is skipped entirely.
     * </p>
     *
     * <pre>{@code
     * builder.orGroup(or -> or
     *     .withLike("name", "%search%")
     *     .withLike("description", "%search%")
     * );
     * // Generates: ... AND (LOWER(name) LIKE '%search%' OR LOWER(description) LIKE '%search%')
     * }</pre>
     *
     * @param orBuilder consumer that receives a fresh builder to define OR conditions
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> orGroup(Consumer<SpecificationBuilder<T>> orBuilder) {
        SpecificationBuilder<T> innerBuilder = new SpecificationBuilder<>();
        orBuilder.accept(innerBuilder);

        if (!innerBuilder.andCriteria.isEmpty() || !innerBuilder.additionalSpecs.isEmpty()) {
            Specification<T> orSpec = buildOrSpecification(innerBuilder);
            additionalSpecs.add(orSpec);
        }
        return this;
    }

    // ==================== Build ====================

    /**
     * Builds the final {@link Specification} by combining all conditions.
     * <p>
     * All top-level criteria and OR groups are combined with AND logic.
     * Returns {@link Specification#where(Specification)} with no filter if no conditions were added.
     * </p>
     *
     * @return the composed Specification, never null
     */
    public Specification<T> build() {
        Specification<T> result = Specification.where(null);

        // AND all individual criteria
        for (SearchCriteria criteria : andCriteria) {
            result = result.and(new GenericSpecification<>(criteria));
        }

        // AND all additional specs (OR groups, etc.)
        for (Specification<T> spec : additionalSpecs) {
            result = result.and(spec);
        }

        return result;
    }

    // ==================== Private Helpers ====================

    private Specification<T> buildOrSpecification(SpecificationBuilder<T> innerBuilder) {
        Specification<T> orResult = Specification.where(null);
        boolean first = true;

        for (SearchCriteria criteria : innerBuilder.andCriteria) {
            GenericSpecification<T> spec = new GenericSpecification<>(criteria);
            if (first) {
                orResult = Specification.where(spec);
                first = false;
            } else {
                orResult = orResult.or(spec);
            }
        }

        for (Specification<T> spec : innerBuilder.additionalSpecs) {
            if (first) {
                orResult = Specification.where(spec);
                first = false;
            } else {
                orResult = orResult.or(spec);
            }
        }

        return orResult;
    }

    private boolean isValuePresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String str) {
            return StringUtils.hasText(str);
        }
        return true;
    }

    private void validateField(String field) {
        if (!StringUtils.hasText(field)) {
            throw new IllegalArgumentException("Field name must not be null or blank");
        }
    }

    private void validateOperator(SearchOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Operator must not be null");
        }
    }
}
