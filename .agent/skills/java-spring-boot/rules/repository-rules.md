# Repository Rules

## Standards

### Interface Definition
- Extend `JpaRepository<Entity, ID>` for basic CRUD operations
- Use `@Repository` annotation (optional with Spring Data JPA)
- Follow naming conventions: `EntityRepository`

### Query Methods
- Use Spring Data JPA method naming conventions
- Use `@Query` for complex queries
- Always filter out soft-deleted records in queries
- Use `@Param` for named parameters
- Use `JOIN FETCH` for eager loading when needed

### Modifying Operations
- Use `@Modifying` annotation for update/delete operations
- **Always set `clearAutomatically = true`** to prevent stale L1 cache reads after bulk updates
- **Always set `flushAutomatically = true`** to flush pending changes before executing the bulk query
- Use `@Transactional` on modifying methods
- Implement soft delete operations

### Performance Considerations
- Use pagination for large result sets — **never use unbounded `findAll()` without `Pageable`**
- Implement proper indexing strategy (declare via `@Table(indexes = {...})` on entity)
- Use projections for read-only queries
- Avoid N+1 query problems — use `@EntityGraph` or `JOIN FETCH`
- Use `@Lock` for pessimistic locking on high-contention operations
- Use `@QueryHints` with `HINT_FETCH_SIZE` for batch result processing

## Example Template

```java
@Repository
public interface SampleEntityRepository extends JpaRepository<SampleEntity, UUID> {
    
    // Method naming convention queries
    List<SampleEntity> findByStatusAndDeletedAtIsNull(EntityStatus status);
    
    Optional<SampleEntity> findByNameAndDeletedAtIsNull(String name);
    
    // Custom queries with JPQL
    @Query("SELECT e FROM SampleEntity e WHERE e.deletedAt IS NULL AND e.status = :status")
    Page<SampleEntity> findByStatusAndNotDeleted(@Param("status") EntityStatus status, Pageable pageable);
    
    @Query("SELECT e FROM SampleEntity e JOIN FETCH e.parent WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<SampleEntity> findByIdWithParent(@Param("id") UUID id);
    
    // Native queries when needed — use CONCAT for ILIKE to avoid driver-dependent % handling
    @Query(value = "SELECT * FROM entities WHERE name ILIKE CONCAT('%', :searchTerm, '%') AND deleted_at IS NULL",
           nativeQuery = true)
    List<SampleEntity> searchByName(@Param("searchTerm") String searchTerm);
    
    // Modifying operations — clearAutomatically prevents stale L1 cache reads after bulk updates
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SampleEntity e SET e.deletedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void softDeleteById(@Param("id") UUID id);
    
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SampleEntity e SET e.status = :status WHERE e.id IN :ids")
    void updateStatusByIds(@Param("status") EntityStatus status, @Param("ids") List<UUID> ids);
    
    // Projections for performance
    @Query("SELECT new com.example.dto.EntitySummary(e.id, e.name, e.status) " +
           "FROM SampleEntity e WHERE e.deletedAt IS NULL")
    List<EntitySummary> findAllSummaries();
    
    // Aggregation queries
    @Query("SELECT COUNT(e) FROM SampleEntity e WHERE e.status = :status AND e.deletedAt IS NULL")
    long countByStatus(@Param("status") EntityStatus status);
    
    // Exists queries for performance
    boolean existsByNameAndDeletedAtIsNull(String name);
}
```

## Repository Interface Patterns

### Basic CRUD with Soft Delete
```java
public interface BaseRepository<T extends BaseEntity> extends JpaRepository<T, UUID> {
    
    @Query("SELECT e FROM #{#entityName} e WHERE e.deletedAt IS NULL")
    List<T> findAllActive();
    
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<T> findActiveById(@Param("id") UUID id);
    
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE #{#entityName} e SET e.deletedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void softDeleteById(@Param("id") UUID id);
}
```

### EntityGraph Pattern (preferred over JOIN FETCH in named queries)
```java
public interface SampleEntityRepository extends JpaRepository<SampleEntity, UUID> {

    // ✅ @EntityGraph avoids N+1 without hardcoding JOIN FETCH in every query
    @EntityGraph(attributePaths = {"parent", "categories"})
    Optional<SampleEntity> findWithGraphById(UUID id);

    // Useful for list endpoints that need related data without full JOIN FETCH JPQL
    @EntityGraph(attributePaths = {"createdBy"})
    Page<SampleEntity> findByStatusAndDeletedAtIsNull(EntityStatus status, Pageable pageable);
}
```

### Pessimistic Locking Pattern
```java
public interface SampleEntityRepository extends JpaRepository<SampleEntity, UUID> {

    // Acquires a DB-level exclusive lock — use for high-contention operations
    // like inventory decrement, balance deduction, seat reservation
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM SampleEntity e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<SampleEntity> findByIdForUpdate(@Param("id") UUID id);

    // PESSIMISTIC_READ for shared read lock (prevents concurrent writes, allows reads)
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT e FROM SampleEntity e WHERE e.id = :id")
    Optional<SampleEntity> findByIdForRead(@Param("id") UUID id);
}
```

### Search and Filter Patterns
```java
// Specification-based queries
public interface SearchableRepository<T> extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {
    
    default Page<T> findWithFilters(SearchCriteria criteria, Pageable pageable) {
        Specification<T> spec = Specification.where(null);
        
        if (criteria.getName() != null) {
            spec = spec.and((root, query, cb) -> 
                cb.like(cb.lower(root.get("name")), "%" + criteria.getName().toLowerCase() + "%"));
        }
        
        if (criteria.getStatus() != null) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(root.get("status"), criteria.getStatus()));
        }
        
        // Always exclude soft-deleted
        spec = spec.and((root, query, cb) -> cb.isNull(root.get("deletedAt")));
        
        return findAll(spec, pageable);
    }
}
```