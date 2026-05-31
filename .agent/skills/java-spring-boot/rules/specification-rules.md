# Specification Rules

## Standards

### Core API Usage
- Use `SpecificationBuilder.<T>forEntity()` to initialize.
- Prefer `.withIfPresent()` variants over manual null checks.
- Use dot notation for nested property paths (e.g., `"relation.property"`).
- Always terminate with `.build()`.

### Operators
- `EQUAL` / `NOT_EQUAL`: Standard equality.
- `LIKE` / `NOT_LIKE`: Case-insensitive substring match (automatically handled by `GenericSpecification`).
- `GREATER_THAN` / `LESS_THAN` (and OR_EQUAL): For numeric and date comparisons.
- `BETWEEN`: Requires both `from` and `to` values.
- `IN` / `NOT_IN`: Requires a non-empty collection.
- `IS_NULL` / `IS_NOT_NULL`: No value parameter needed.

### Design Principles
- **Lean Services**: Keep filtering logic readable using the fluent builder.
- **Join Reuse**: `GenericSpecification` handles join reuse; avoid manual joins in Specifications.
- **Validation**: Ensure values passed to `with()` are of the correct type expected by the entity field.

## Example Template

```java
@Service
@RequiredArgsConstructor
public class EntityService {

    private final EntityRepository entityRepository;

    public Page<Entity> findEntities(EntityFilter filter, Pageable pageable) {
        Specification<Entity> spec = SpecificationBuilder.<Entity>forEntity()
            // Optional basic filters
            .withIfPresent("status", SearchOperator.EQUAL, filter.getStatus())
            .withIfPresent("category.id", SearchOperator.EQUAL, filter.getCategoryId())
            
            // Text search
            .withLikeIfPresent("name", filter.getSearch())
            
            // Range filters
            .withBetweenIfPresent("price", filter.getMinPrice(), filter.getMaxPrice())
            
            // Complex OR logic
            .orGroup(or -> or
                .withLike("name", filter.getSearch())
                .withLike("description", filter.getSearch())
            )
            .build();

        return entityRepository.findAll(spec, pageable);
    }
}
```

## Advanced Patterns

### Global Search Across Fields
```java
public Specification<T> buildGlobalSearch(String query) {
    return SpecificationBuilder.<T>forEntity()
        .orGroup(or -> or
            .withLike("fieldOne", "%" + query + "%")
            .withLike("fieldTwo", "%" + query + "%")
            .withLike("nestedRelation.fieldName", "%" + query + "%")
        )
        .build();
}
```

### Filtering by Nested Collection Properties
```java
// Filters entities that have a specific relationship attribute
builder.with("relatedEntities.attribute", SearchOperator.EQUAL, targetValue);
```

### Date Range Patterns
```java
builder.withBetweenIfPresent("timestampField", startTime, endTime);
```
