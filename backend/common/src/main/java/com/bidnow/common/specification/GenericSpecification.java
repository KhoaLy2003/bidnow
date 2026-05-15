/*
 * BidNow Auction System
 */
package com.bidnow.common.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Set;

/**
 * Generic JPA Specification implementation that converts a {@link SearchCriteria}
 * into a JPA {@link Predicate}.
 * <p>
 * Supports nested property paths via dot notation (e.g., "category.name") with
 * automatic join reuse to prevent duplicate JOINs in the generated SQL.
 * </p>
 *
 * @param <T> the entity type
 */
@RequiredArgsConstructor
public class GenericSpecification<T> implements Specification<T> {

    private final SearchCriteria criteria;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Path<?> path = resolvePath(root, criteria.getField());

        return switch (criteria.getOperator()) {
            case EQUAL -> cb.equal(path, criteria.getValue());

            case NOT_EQUAL -> cb.notEqual(path, criteria.getValue());

            case GREATER_THAN -> cb.greaterThan((Path<Comparable>) path, (Comparable) criteria.getValue());

            case GREATER_THAN_OR_EQUAL ->
                    cb.greaterThanOrEqualTo((Path<Comparable>) path, (Comparable) criteria.getValue());

            case LESS_THAN -> cb.lessThan((Path<Comparable>) path, (Comparable) criteria.getValue());

            case LESS_THAN_OR_EQUAL -> cb.lessThanOrEqualTo((Path<Comparable>) path, (Comparable) criteria.getValue());

            case IS_NULL -> cb.isNull(path);

            case IS_NOT_NULL -> cb.isNotNull(path);

            case IN -> path.in((Collection<?>) criteria.getValue());

            case NOT_IN -> cb.not(path.in((Collection<?>) criteria.getValue()));

            case LIKE -> cb.like(
                    cb.lower(path.as(String.class)),
                    criteria.getValue().toString().toLowerCase()
            );

            case NOT_LIKE -> cb.notLike(
                    cb.lower(path.as(String.class)),
                    criteria.getValue().toString().toLowerCase()
            );

            case BETWEEN -> cb.between(
                    (Path<Comparable>) path,
                    (Comparable) criteria.getValue(),
                    (Comparable) criteria.getValueTo()
            );
        };
    }

    /**
     * Resolves a potentially nested property path (e.g., "category.name") into a JPA {@link Path}.
     * <p>
     * For nested paths, reuses existing JOINs on the same attribute to avoid
     * duplicate JOINs in the generated SQL query.
     * </p>
     *
     * @param root  the query root
     * @param field the dot-separated field path
     * @return the resolved JPA path
     */
    private Path<?> resolvePath(Root<T> root, String field) {
        String[] parts = field.split("\\.");

        if (parts.length == 1) {
            return root.get(field);
        }

        // Navigate through joins for nested paths, reusing existing joins
        From<?, ?> currentFrom = root;
        for (int i = 0; i < parts.length - 1; i++) {
            currentFrom = getOrCreateJoin(currentFrom, parts[i]);
        }

        return currentFrom.get(parts[parts.length - 1]);
    }

    /**
     * Gets an existing JOIN on the given attribute or creates a new LEFT JOIN if none exists.
     * <p>
     * This prevents the generation of duplicate JOINs when multiple conditions
     * reference the same relationship (e.g., "category.name" and "category.status").
     * Uses LEFT JOIN to avoid excluding entities where the relationship is null.
     * </p>
     *
     * @param from      the current FROM clause element
     * @param attribute the attribute to join on
     * @return the existing or newly created Join
     */
    private Join<?, ?> getOrCreateJoin(From<?, ?> from, String attribute) {
        Set<? extends Join<?, ?>> existingJoins = from.getJoins();

        // Reuse existing join on the same attribute
        for (Join<?, ?> join : existingJoins) {
            if (join.getAttribute().getName().equals(attribute)) {
                return join;
            }
        }

        // Create new LEFT JOIN (LEFT to not exclude entities where relationship is null)
        return from.join(attribute, JoinType.LEFT);
    }
}
