# Service Rules

## Standards

### Class Definition
- Use `@Service` annotation
- Use constructor injection with `@RequiredArgsConstructor`
- Add `@Slf4j` for logging
- Add `@Transactional(readOnly = true)` at **class level** as the safe default
- Explicitly annotate write methods with `@Transactional` (no `readOnly`) to opt into write mode
- This prevents accidental write locks on read methods added by other developers

### Transaction Management
- Use `@Transactional(readOnly = true)` for read-only operations
- Keep transaction boundaries at service level
- Use appropriate propagation levels when needed
- Handle rollback scenarios properly

### Caching
- Use Spring Cache annotations (`@Cacheable`, `@CacheEvict`, `@CachePut`)
- Define cache names as constants
- Use meaningful cache keys
- Handle cache failures gracefully

### Error Handling
- Throw specific business exceptions
- Validate input parameters
- Check business rules and constraints
- Log important operations and errors

### Event Publishing
- Use `ApplicationEventPublisher` for domain events
- Publish events after successful operations
- Keep events focused and specific

## Example Template

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)  // Safe default — read methods won't acquire write locks
public class SampleEntityService {
    
    private final SampleEntityRepository repository;
    private final SampleEntityMapper mapper;
    private final RelatedService relatedService;
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional(readOnly = true)
    @Cacheable(value = "entityDetail", key = "#entityId")
    public EntityDetailResponse getEntityById(UUID entityId) {
        SampleEntity entity = repository.findActiveById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Entity not found with id: " + entityId));
        
        return mapper.toDetailResponse(entity);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<EntitySummaryResponse> getEntities(int page, int size, EntityStatus status) {
        Pageable pageable = PaginationUtils.createPageable(page, size);
        Page<SampleEntity> entities;
        
        if (status != null) {
            entities = repository.findByStatusAndNotDeleted(status, pageable);
        } else {
            entities = repository.findAllActive(pageable);
        }
        
        List<EntitySummaryResponse> content = entities.getContent().stream()
            .map(mapper::toSummaryResponse)
            .collect(Collectors.toList());
            
        return PaginationUtils.toPageResponse(entities, content);
    }
    
    @Transactional  // Opt into write transaction explicitly
    @CacheEvict(value = {"entityDetail", "entityList"}, allEntries = true)
    public EntityResponse createEntity(CreateEntityRequest request, UUID userId) {
        validateCreateRequest(request);
        
        SampleEntity entity = mapper.toEntity(request);
        entity.setCreatedBy(userId);
        
        SampleEntity savedEntity = repository.save(entity);
        
        // Publish domain event
        eventPublisher.publishEvent(new EntityCreatedEvent(savedEntity.getId(), userId));
        
        log.info("Entity created with id: {} by user: {}", savedEntity.getId(), userId);
        
        return mapper.toResponse(savedEntity);
    }
    
    @Transactional  // Opt into write transaction explicitly
    @CacheEvict(value = "entityDetail", key = "#entityId")
    public EntityResponse updateEntity(UUID entityId, UpdateEntityRequest request, UUID userId) {
        SampleEntity entity = findEntityAndValidateOwnership(entityId, userId);
        
        validateUpdateRequest(request, entity);
        
        mapper.updateEntityFromRequest(request, entity);
        SampleEntity savedEntity = repository.save(entity);
        
        eventPublisher.publishEvent(new EntityUpdatedEvent(savedEntity.getId(), userId));
        
        log.info("Entity updated: {} by user: {}", entityId, userId);
        
        return mapper.toResponse(savedEntity);
    }
    
    @Transactional  // Opt into write transaction explicitly
    @CacheEvict(value = {"entityDetail", "entityList"}, key = "#entityId")
    public void deleteEntity(UUID entityId, UUID userId) {
        SampleEntity entity = findEntityAndValidateOwnership(entityId, userId);
        
        entity.softDelete();
        repository.save(entity);
        
        eventPublisher.publishEvent(new EntityDeletedEvent(entityId, userId));
        
        log.info("Entity deleted: {} by user: {}", entityId, userId);
    }
    
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return repository.existsByNameAndDeletedAtIsNull(name);
    }
    
    // Private helper methods
    private SampleEntity findEntityAndValidateOwnership(UUID entityId, UUID userId) {
        SampleEntity entity = repository.findActiveById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Entity not found"));
            
        if (!entity.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("You don't have permission to modify this entity");
        }
        
        return entity;
    }
    
    private void validateCreateRequest(CreateEntityRequest request) {
        if (existsByName(request.getName())) {
            throw new ConflictException("Entity with name '" + request.getName() + "' already exists");
        }
        
        // Additional business validations
        if (request.getName().length() < 3) {
            throw new BadRequestException("Entity name must be at least 3 characters long");
        }
    }
    
    private void validateUpdateRequest(UpdateEntityRequest request, SampleEntity entity) {
        if (!entity.getName().equals(request.getName()) && existsByName(request.getName())) {
            throw new ConflictException("Entity with name '" + request.getName() + "' already exists");
        }
        
        // Additional business validations
    }
}
```

## Service Patterns

### Read-Only Service Pattern
```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EntityQueryService {
    
    private final SampleEntityRepository repository;
    private final SampleEntityMapper mapper;
    
    // ✅ Deterministic cache key — hashCode() is non-deterministic and causes cache poisoning in Redis
    @Cacheable(value = "entitySearch", key = "#criteria.status + ':' + #criteria.name + ':' + #criteria.page")
    public List<EntitySummaryResponse> searchEntities(SearchCriteria criteria) {
        return repository.findWithFilters(criteria).stream()
            .map(mapper::toSummaryResponse)
            .collect(Collectors.toList());
    }
    
    @Cacheable(value = "entityStats", key = "'global'")
    public EntityStatsResponse getEntityStatistics() {
        return EntityStatsResponse.builder()
            .totalCount(repository.countByDeletedAtIsNull())
            .activeCount(repository.countByStatus(EntityStatus.ACTIVE))
            .build();
    }
}
```

### Command Service Pattern
```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional  // Command service is write-only by nature, write default is fine here
public class EntityCommandService {
    
    private final SampleEntityRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    
    @CacheEvict(value = "entityList", allEntries = true)
    public void bulkUpdateStatus(List<UUID> entityIds, EntityStatus status, UUID userId) {
        List<SampleEntity> entities = repository.findAllById(entityIds);
        
        entities.forEach(entity -> {
            validateOwnership(entity, userId);
            entity.setStatus(status);
        });
        
        repository.saveAll(entities);
        
        eventPublisher.publishEvent(new BulkEntityUpdatedEvent(entityIds, status, userId));
        
        log.info("Bulk updated {} entities to status {} by user {}", entityIds.size(), status, userId);
    }
}
```