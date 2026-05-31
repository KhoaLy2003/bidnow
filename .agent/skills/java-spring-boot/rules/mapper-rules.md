# Mapper Rules

## Standards

### Interface Definition

- Use `@Mapper(componentModel = "spring")` for Spring integration
- Use `uses` parameter to reference other mappers
- Follow naming convention: `EntityMapper`
- Keep mappers focused on single entity type

### Mapping Annotations

- Use `@Mapping` for field-level mapping configuration
- Ignore audit fields when mapping from requests
- Use `@Named` methods for complex mapping logic
- Use `@Context` for passing additional parameters
- Use `@MappingTarget` for update operations

### Best Practices

- **Never perform manual, verbose field mapping inside the main business service or controller methods.** Copying properties between DTOs and Entities using inline getters/setters pollutes the core logic flow. Always delegate conversion tasks to a designated MapStruct mapper interface or a dedicated mapping helper method.
- Keep mapping logic simple and declarative
- Use custom methods for complex transformations
- Handle null values appropriately
- Use qualifiers for disambiguation
- Test mapper behavior thoroughly

## Example Template

```java
@Mapper(componentModel = "spring", uses = {UserMapper.class, CategoryMapper.class})
public interface SampleEntityMapper {

    // Entity creation from request
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdBy", source = "userId", qualifiedByName = "userIdToUser")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "categories", source = "categoryIds", qualifiedByName = "categoryIdsToCategories")
    SampleEntity toEntity(CreateEntityRequest request, @Context UUID userId);

    // Entity to response mapping
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "userToSummary")
    @Mapping(target = "categories", source = "categories", qualifiedByName = "categoriesToSummaries")
    @Mapping(target = "stats", source = "entity", qualifiedByName = "entityToStats")
    EntityDetailResponse toDetailResponse(SampleEntity entity);

    // Entity to summary response
    @Mapping(target = "createdByUsername", source = "createdBy.username")
    @Mapping(target = "itemCount", source = "entity", qualifiedByName = "calculateItemCount")
    EntitySummaryResponse toSummaryResponse(SampleEntity entity);

    // Update entity from request
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "categories", source = "categoryIds", qualifiedByName = "categoryIdsToCategories")
    void updateEntityFromRequest(UpdateEntityRequest request, @MappingTarget SampleEntity entity);

    // Collection mappings
    List<EntitySummaryResponse> toSummaryResponseList(List<SampleEntity> entities);

    List<EntityDetailResponse> toDetailResponseList(List<SampleEntity> entities);

    // Custom mapping methods
    @Named("entityToStats")
    default EntityStatsResponse mapEntityToStats(SampleEntity entity) {
        if (entity == null) {
            return null;
        }

        return EntityStatsResponse.builder()
            .viewCount(entity.getViews().size())
            .likeCount(entity.getLikes().size())
            .commentCount(entity.getComments().size())
            .lastActivityAt(calculateLastActivity(entity))
            .build();
    }

    @Named("calculateItemCount")
    default Integer calculateItemCount(SampleEntity entity) {
        if (entity == null || entity.getItems() == null) {
            return 0;
        }
        return entity.getItems().size();
    }

    @Named("categoryIdsToCategories")
    default List<Category> mapCategoryIdsToCategories(List<UUID> categoryIds) {
        // ❌ Do NOT call repositories or services here — mappers must be pure transformation
        // Resolve entities in the service layer before calling the mapper.
        // If categoryIds need to be resolved, accept resolved List<Category> via @Context:
        //
        //   @Mapping(target = "categories", source = "categories") // already resolved in service
        //   EntityDetailResponse toDetailResponse(SampleEntity entity);
        //
        // ✅ This method should only be used when the source already contains resolved objects,
        // not raw IDs that require a database lookup.
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new ArrayList<>();
        }
        // The service is responsible for resolving IDs to Category objects before this point
        throw new UnsupportedOperationException("Resolve Category entities in service layer before calling mapper");
    }

    // Helper methods
    default Instant calculateLastActivity(SampleEntity entity) {
        Instant lastComment = entity.getComments().stream()
            .map(Comment::getCreatedAt)
            .max(Instant::compareTo)
            .orElse(null);

        Instant lastLike = entity.getLikes().stream()
            .map(Like::getCreatedAt)
            .max(Instant::compareTo)
            .orElse(null);

        return Stream.of(entity.getUpdatedAt(), lastComment, lastLike)
            .filter(Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(entity.getCreatedAt());
    }
}
```

## Advanced Mapping Patterns

### Conditional Mapping

```java
@Mapper(componentModel = "spring")
public interface ConditionalEntityMapper {

    @Mapping(target = "sensitiveData",
             expression = "java(mapSensitiveData(entity, currentUserId))")
    @Mapping(target = "adminOnlyField",
             expression = "java(mapAdminField(entity, userRole))")
    EntityResponse toResponse(SampleEntity entity,
                             @Context UUID currentUserId,
                             @Context UserRole userRole);

    default String mapSensitiveData(SampleEntity entity, UUID currentUserId) {
        if (entity.getCreatedBy().getId().equals(currentUserId)) {
            return entity.getSensitiveData();
        }
        return null;
    }

    default String mapAdminField(SampleEntity entity, UserRole userRole) {
        if (userRole == UserRole.ADMIN) {
            return entity.getAdminOnlyField();
        }
        return null;
    }
}
```

### Nested Object Mapping

```java
@Mapper(componentModel = "spring", uses = {AddressMapper.class, ContactMapper.class})
public interface UserMapper {

    @Mapping(target = "profile.address", source = "address")
    @Mapping(target = "profile.contact", source = "contact")
    @Mapping(target = "preferences", source = "user", qualifiedByName = "mapPreferences")
    UserDetailResponse toDetailResponse(User user);

    @Named("mapPreferences")
    default UserPreferencesResponse mapPreferences(User user) {
        return UserPreferencesResponse.builder()
            .theme(user.getPreferredTheme())
            .language(user.getPreferredLanguage())
            .notifications(mapNotificationSettings(user))
            .build();
    }

    default NotificationSettingsResponse mapNotificationSettings(User user) {
        return NotificationSettingsResponse.builder()
            .emailEnabled(user.isEmailNotificationsEnabled())
            .pushEnabled(user.isPushNotificationsEnabled())
            .frequency(user.getNotificationFrequency())
            .build();
    }
}
```

### Inheritance Mapping

```java
@Mapper(componentModel = "spring")
public interface VehicleMapper {

    @SubclassMapping(source = Car.class, target = CarResponse.class)
    @SubclassMapping(source = Truck.class, target = TruckResponse.class)
    @SubclassMapping(source = Motorcycle.class, target = MotorcycleResponse.class)
    VehicleResponse toResponse(Vehicle vehicle);

    @InheritInverseConfiguration
    @SubclassMapping(source = CarRequest.class, target = Car.class)
    @SubclassMapping(source = TruckRequest.class, target = Truck.class)
    @SubclassMapping(source = MotorcycleRequest.class, target = Motorcycle.class)
    Vehicle toEntity(VehicleRequest request);
}
```

### Custom Qualifier Pattern

```java
@Qualifier
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ToSummary {
}

@Qualifier
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ToDetail {
}

@Mapper(componentModel = "spring")
public interface QualifiedMapper {

    @ToSummary
    @Mapping(target = "fullName", expression = "java(user.getFirstName() + \" \" + user.getLastName())")
    UserSummaryResponse toSummaryResponse(User user);

    @ToDetail
    @Mapping(target = "fullProfile", source = "user", qualifiedByName = "mapFullProfile")
    UserDetailResponse toDetailResponse(User user);

    @Named("mapFullProfile")
    default UserProfileResponse mapFullProfile(User user) {
        // Complex profile mapping logic
        return UserProfileResponse.builder()
            .bio(user.getBio())
            .avatar(user.getAvatarUrl())
            .socialLinks(user.getSocialLinks())
            .build();
    }
}
```

## Testing Mappers

### Mapper Test Template

```java
@ExtendWith(MockitoExtension.class)
class SampleEntityMapperTest {

    @InjectMocks
    private SampleEntityMapperImpl mapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Test
    void shouldMapCreateRequestToEntity() {
        // Given
        CreateEntityRequest request = CreateEntityRequest.builder()
            .name("Test Entity")
            .description("Test Description")
            .categoryIds(List.of(UUID.randomUUID()))
            .build();

        UUID userId = UUID.randomUUID();

        // When
        SampleEntity result = mapper.toEntity(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(request.getName());
        assertThat(result.getDescription()).isEqualTo(request.getDescription());
        assertThat(result.getStatus()).isEqualTo(EntityStatus.ACTIVE);
        assertThat(result.getId()).isNull(); // Should be ignored
        assertThat(result.getCreatedAt()).isNull(); // Should be ignored
    }

    @Test
    void shouldMapEntityToDetailResponse() {
        // Given
        SampleEntity entity = SampleEntity.builder()
            .id(UUID.randomUUID())
            .name("Test Entity")
            .description("Test Description")
            .status(EntityStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        // When
        EntityDetailResponse result = mapper.toDetailResponse(entity);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(entity.getId());
        assertThat(result.getName()).isEqualTo(entity.getName());
        assertThat(result.getDescription()).isEqualTo(entity.getDescription());
        assertThat(result.getStatus()).isEqualTo(entity.getStatus());
    }
}
```
