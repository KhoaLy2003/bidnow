# DTO Rules

## Standards

### Request DTOs
- Suffix with `Request` (e.g., `CreateEntityRequest`, `UpdateEntityRequest`)
- Use Jakarta Bean Validation annotations
- Use `@Schema` for OpenAPI documentation
- Initialize collections to avoid null pointer exceptions
- Use `@Valid` for nested object validation

### Response DTOs
- Suffix with `Response` (e.g., `EntityResponse`, `EntityDetailResponse`)
- Include only necessary fields for the specific use case
- Use composition for nested objects
- Add `@Schema` descriptions for API documentation

### Common Patterns
- Use `@Data` for getters/setters/equals/hashCode
- Use `@Builder` for flexible object creation
- Use `@NoArgsConstructor/@AllArgsConstructor` for constructors
- Separate DTOs for different operations (create, update, summary, detail)

### Pagination DTOs
- **Offset-based Pagination**: Use `PageResponse<T>` for consistent API responses.
  - The `data` field holds the list of items for the current page.
  - The `pagination` field (of type `PaginationMeta`) provides metadata such as `page`, `limit`, `total`, `totalPages`, `hasNext`, and `hasPrev`.
- **Cursor-based Pagination**: Use `CursorInfo` record for defining pagination cursors, typically for fetching items "before" or "after" a specific point.
  - `CursorInfo` contains `createdAt` (Instant) and `id` (UUID) to uniquely identify a point in the dataset for sequential retrieval.

### Validation Rules
- Use appropriate validation annotations
- Provide meaningful error messages
- Group related validations
- Use custom validators for complex business rules

## Example Templates

### Request DTO Template
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEntityRequest {
    
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s]+$", message = "Name can only contain letters, numbers, and spaces")
    @Schema(description = "Entity name", example = "Sample Entity")
    private String name;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Schema(description = "Entity description", example = "This is a sample entity description")
    private String description;
    
    @NotNull(message = "Status is required")
    @Schema(description = "Entity status", example = "ACTIVE")
    private EntityStatus status;
    
    @Valid
    @NotEmpty(message = "At least one category is required")
    @Schema(description = "List of category IDs")
    private List<UUID> categoryIds = new ArrayList<>();
    
    @Valid
    @Schema(description = "Entity configuration")
    private EntityConfigRequest config;
    
    @Email(message = "Contact email must be valid")
    @Schema(description = "Contact email", example = "contact@example.com")
    private String contactEmail;
    
    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    @DecimalMax(value = "999999.99", message = "Price cannot exceed 999,999.99")
    @Schema(description = "Entity price", example = "99.99")
    private BigDecimal price;
}
```

### Response DTO Template
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityDetailResponse {
    
    @Schema(description = "Entity unique identifier")
    private UUID id;
    
    @Schema(description = "Entity name")
    private String name;
    
    @Schema(description = "Entity description")
    private String description;
    
    @Schema(description = "Entity status")
    private EntityStatus status;
    
    @Schema(description = "Entity creation timestamp")
    private Instant createdAt;
    
    @Schema(description = "Entity last update timestamp")
    private Instant updatedAt;
    
    @Schema(description = "Entity creator information")
    private UserSummaryResponse createdBy;
    
    @Schema(description = "Associated categories")
    private List<CategorySummaryResponse> categories;
    
    @Schema(description = "Entity configuration")
    private EntityConfigResponse config;
    
    @Schema(description = "Entity statistics")
    private EntityStatsResponse stats;
}
```

### Summary DTO Template
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitySummaryResponse {
    
    @Schema(description = "Entity unique identifier")
    private UUID id;
    
    @Schema(description = "Entity name")
    private String name;
    
    @Schema(description = "Entity status")
    private EntityStatus status;
    
    @Schema(description = "Entity creation timestamp")
    private Instant createdAt;
    
    @Schema(description = "Creator username")
    private String createdByUsername;
    
    @Schema(description = "Number of associated items")
    private Integer itemCount;
}
```

### Update DTO Template
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEntityRequest {
    
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s]+$", message = "Name can only contain letters, numbers, and spaces")
    @Schema(description = "Entity name", example = "Updated Entity Name")
    private String name;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Schema(description = "Entity description")
    private String description;
    
    @Schema(description = "Entity status")
    private EntityStatus status;
    
    @Valid
    @Schema(description = "List of category IDs")
    private List<UUID> categoryIds;
    
    @Valid
    @Schema(description = "Entity configuration")
    private EntityConfigRequest config;
}
```

## Validation Patterns

### Custom Validation Annotation
```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEntityNameValidator.class)
public @interface UniqueEntityName {
    String message() default "Entity name already exists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
public class UniqueEntityNameValidator implements ConstraintValidator<UniqueEntityName, String> {
    
    @Autowired
    private EntityService entityService;
    
    @Override
    public boolean isValid(String name, ConstraintValidatorContext context) {
        if (name == null || name.trim().isEmpty()) {
            return true; // Let @NotBlank handle this
        }
        return !entityService.existsByName(name);
    }
}
```

### Conditional Validation
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConditionalValidation
public class ConditionalEntityRequest {
    
    @NotNull
    private EntityType type;
    
    @NotBlank
    @ConditionalNotNull(dependsOn = "type", values = {"PREMIUM", "ENTERPRISE"})
    private String licenseKey;
    
    @Min(1)
    @ConditionalNotNull(dependsOn = "type", values = {"SUBSCRIPTION"})
    private Integer subscriptionMonths;
}
```

## Common DTO Patterns

### Search/Filter DTO
(Response for this request would typically be `PageResponse<EntitySummaryResponse>`)
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitySearchRequest {
    
    @Size(max = 100, message = "Search term cannot exceed 100 characters")
    @Schema(description = "Search term for name or description")
    private String searchTerm;
    
    @Schema(description = "Filter by status")
    private EntityStatus status;
    
    @Schema(description = "Filter by category IDs")
    private List<UUID> categoryIds;
    
    @Schema(description = "Created after date")
    private LocalDate createdAfter;
    
    @Schema(description = "Created before date")
    private LocalDate createdBefore;
    
    @Min(0)
    @Schema(description = "Page number", example = "0")
    private Integer page = 0;
    
    @Min(1)
    @Max(100)
    @Schema(description = "Page size", example = "20")
    private Integer size = 20;
    
    @Schema(description = "Sort field", example = "createdAt")
    private String sortBy = "createdAt";
    
    @Schema(description = "Sort direction", example = "DESC")
    private String sortDirection = "DESC";
}
```

### Bulk Operation DTO
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkEntityUpdateRequest {
    
    @NotEmpty(message = "Entity IDs list cannot be empty")
    @Size(max = 100, message = "Cannot update more than 100 entities at once")
    @Schema(description = "List of entity IDs to update")
    private List<UUID> entityIds;
    
    @NotNull(message = "Status is required")
    @Schema(description = "New status for all entities")
    private EntityStatus status;
    
    @Schema(description = "Update reason")
    private String reason;
}
```