# Entity Rules

## Standards

### Base Entity Pattern
- Extend a common `BaseEntity` class for audit fields (id, createdAt, updatedAt, deletedAt)
- Use UUID as primary key type for better distribution and security
- Implement soft delete pattern via `deletedAt` field
- Use JPA auditing for automatic timestamp management
- **Always add `@Version` for optimistic locking** — prevents silent data overwrites on concurrent updates

### Annotations
- `@Entity` - Mark as JPA entity
- `@Table(name = "table_name")` - Explicit table naming with snake_case
- `@Table(indexes = {...})` - Declare DB indexes inline for documentation and schema generation
- `@Column(name = "column_name")` - Explicit column mapping
- `@Getter/@Setter` - Lombok for accessor methods
- `@NoArgsConstructor` - **Required** for Hibernate proxy creation
- ⚠️ **Do NOT combine `@Builder` + `@AllArgsConstructor` at class level with JPA** — use a named `@Builder` constructor instead (see example below)

### Relationships
- Use `FetchType.LAZY` for associations by default
- Use `@JoinColumn` for foreign key mapping
- Implement cascade operations carefully
- Use `mappedBy` for bidirectional relationships

### equals() and hashCode()
- **Never use Lombok `@EqualsAndHashCode` on JPA entities** — it triggers lazy-load of all fields and breaks collections
- Implement `equals()`/`hashCode()` based on `id` only, handling the `null` id case (transient entity)

### Validation
- Apply Jakarta Bean Validation annotations at entity level
- Use `@NotNull`, `@NotBlank`, `@Size`, etc.
- Validate business rules in entity methods

## Example Template

```java
@Entity
@Table(
    name = "entities",
    indexes = {
        @Index(name = "idx_entities_status", columnList = "status"),
        @Index(name = "idx_entities_deleted_at", columnList = "deleted_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_entities_name", columnNames = "name")
    }
)
@Getter
@Setter
@NoArgsConstructor  // Required by Hibernate for proxy creation
public class SampleEntity extends BaseEntity {
    
    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EntityStatus status = EntityStatus.ACTIVE;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ParentEntity parent;
    
    @OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChildEntity> children = new ArrayList<>();
    
    // ✅ Use a named @Builder constructor — safe with JPA, Hibernate proxy unaffected
    @Builder
    public SampleEntity(String name, String description, EntityStatus status) {
        this.name = name;
        this.description = description;
        this.status = status != null ? status : EntityStatus.ACTIVE;
    }
    
    // Business methods
    public void activate() {
        this.status = EntityStatus.ACTIVE;
    }
    
    public boolean isActive() {
        return status == EntityStatus.ACTIVE;
    }
    
    // ✅ equals/hashCode based on id only — avoids lazy-load on comparison
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SampleEntity other)) return false;
        return getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
```

## Base Entity Template

```java
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Optimistic locking — prevents silent last-write-wins on concurrent updates
    // Throws OptimisticLockingFailureException when two transactions modify the same row
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
    }
}
```

## Anti-Patterns to Avoid

### ❌ `@Builder` + `@AllArgsConstructor` at class level
```java
// ❌ WRONG — Lombok @Builder generates an all-args constructor that bypasses
// @NoArgsConstructor. Hibernate requires no-args for proxy creation.
// Causes InstantiationException at runtime with lazy associations.
@Builder
@NoArgsConstructor
@AllArgsConstructor  // ❌ This + @Builder = runtime proxy failure
public class SampleEntity extends BaseEntity { ... }
```

### ❌ Lombok `@EqualsAndHashCode` on entities
```java
// ❌ WRONG — Triggers lazy-load of every field during equals/hashCode calls.
// Causes LazyInitializationException outside transaction boundaries.
@EqualsAndHashCode  // ❌ Never use on JPA entities
public class SampleEntity extends BaseEntity { ... }
```

### ❌ Missing `@Version` for concurrent access
```java
// ❌ Without @Version, concurrent transactions silently overwrite each other (last-write-wins)
// User A reads entity (name=Alice), User B reads entity (name=Alice)
// User A saves (name=Alice, email=new@email.com)
// User B saves (name=Bob) — silently overwrites User A's email change
```