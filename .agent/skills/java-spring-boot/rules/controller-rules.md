# Controller Rules

## Standards

### Class Definition
- Use `@RestController` annotation
- Use `@RequestMapping` for base path
- Use constructor injection with `@RequiredArgsConstructor`
- Add OpenAPI documentation with `@Tag`
- Use `@Validated` for method-level validation

### Method Design
- Keep controllers thin - delegate all business logic to services
- Use appropriate HTTP methods (GET, POST, PUT, PATCH, DELETE) and status codes (200, 201, 204, 400, 401, 403, 404, 409)
- **Use `PATCH` for partial updates** (only modifying sent fields) and `PUT` for full replacements
- Validate request parameters and body using `@Valid` and JSR-380 annotations
- **Never inject `BindingResult` into controller methods** — Spring Boot automatically throws `MethodArgumentNotValidException` when validation fails. Let the `GlobalExceptionHandler` handle and format it
- Return standardized `ApiResponse<T>` format for all REST endpoints
- Use `@CurrentUserId` for authenticated user injection

### API Versioning Guidelines
- **Always version REST APIs** to maintain backward compatibility for frontend clients and external consumers
- Standard method: **URL Path Versioning** (e.g., `/api/v1/entities`)
- Alternative method: **Header Versioning** (e.g., `X-API-Version: 1` or `Accept: application/vnd.company.v1+json`)
- Maintain only the last two major versions actively, deprecating older versions via headers (`X-API-Deprecated: true`)

### Documentation
- Add `@Operation` for method documentation
- Use `@ApiResponse` for response documentation
- Add `@Schema` descriptions for parameters
- Provide meaningful examples

### Error Handling
- Let `GlobalExceptionHandler` handle exceptions
- Validate input at controller level
- Use appropriate HTTP status codes
- Provide clear error messages

## Example Template

```java
@RestController
@RequestMapping("/api/entities")
@RequiredArgsConstructor
@Tag(name = "Entities", description = "Entity management endpoints")
@Validated
public class SampleEntityController {
    
    private final SampleEntityService entityService;
    
    @GetMapping("/{entityId}")
    @Operation(summary = "Get entity by ID", description = "Retrieve detailed entity information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Entity found"),
        @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<ApiResponse<EntityDetailResponse>> getEntity(
            @PathVariable @Schema(description = "Entity ID") UUID entityId) {
        
        EntityDetailResponse entity = entityService.getEntityById(entityId);
        return ResponseEntity.ok(ApiResponse.success("Entity retrieved successfully", entity));
    }
    
    @GetMapping
    @Operation(summary = "Get entities", description = "Retrieve paginated list of entities")
    @ApiResponse(responseCode = "200", description = "Entities retrieved successfully")
    public ResponseEntity<ApiResponse<PageResponse<EntitySummaryResponse>>> getEntities(
            @RequestParam(defaultValue = "0") @Min(0) 
            @Schema(description = "Page number", example = "0") int page,
            
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) 
            @Schema(description = "Page size", example = "20") int size,
            
            @RequestParam(required = false) 
            @Schema(description = "Filter by status") EntityStatus status,
            
            @RequestParam(required = false) 
            @Schema(description = "Search term") String search) {
        
        PageResponse<EntitySummaryResponse> entities = entityService.getEntities(page, size, status, search);
        return ResponseEntity.ok(ApiResponse.success("Entities retrieved successfully", entities));
    }
    
    @PostMapping
    @Operation(summary = "Create new entity", description = "Create a new entity")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Entity created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Entity already exists")
    })
    public ResponseEntity<ApiResponse<EntityResponse>> createEntity(
            @Valid @RequestBody CreateEntityRequest request,
            @CurrentUserId UUID userId) {
        
        EntityResponse entity = entityService.createEntity(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Entity created successfully", entity));
    }
    
    @PutMapping("/{entityId}")
    @Operation(summary = "Update entity (Full)", description = "Update an existing entity using full replacement (PUT semantics)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Entity updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Entity not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<EntityResponse>> updateEntity(
            @PathVariable @Schema(description = "Entity ID") UUID entityId,
            @Valid @RequestBody UpdateEntityRequest request,
            @CurrentUserId UUID userId) {
        
        EntityResponse entity = entityService.updateEntity(entityId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Entity updated successfully", entity));
    }

    @PatchMapping("/{entityId}")
    @Operation(summary = "Partially update entity", description = "Modify specific fields of an entity (PATCH semantics)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Entity partially updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Entity not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<EntityResponse>> patchEntity(
            @PathVariable @Schema(description = "Entity ID") UUID entityId,
            @Valid @RequestBody PatchEntityRequest request,
            @CurrentUserId UUID userId) {
        
        EntityResponse entity = entityService.patchEntity(entityId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Entity partially updated successfully", entity));
    }
    
    @DeleteMapping("/{entityId}")
    @Operation(summary = "Delete entity", description = "Soft delete an entity")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Entity deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Entity not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<Void>> deleteEntity(
            @PathVariable @Schema(description = "Entity ID") UUID entityId,
            @CurrentUserId UUID userId) {
        
        entityService.deleteEntity(entityId, userId);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/bulk-update")
    @Operation(summary = "Bulk update entities", description = "Update multiple entities at once")
    @ApiResponse(responseCode = "200", description = "Entities updated successfully")
    public ResponseEntity<ApiResponse<BulkUpdateResponse>> bulkUpdateEntities(
            @Valid @RequestBody BulkEntityUpdateRequest request,
            @CurrentUserId UUID userId) {
        
        BulkUpdateResponse result = entityService.bulkUpdateEntities(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Entities updated successfully", result));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search entities", description = "Search entities with advanced filters")
    @ApiResponse(responseCode = "200", description = "Search completed successfully")
    public ResponseEntity<ApiResponse<List<EntitySummaryResponse>>> searchEntities(
            @Valid @ModelAttribute EntitySearchRequest searchRequest) {
        
        List<EntitySummaryResponse> entities = entityService.searchEntities(searchRequest);
        return ResponseEntity.ok(ApiResponse.success("Search completed successfully", entities));
    }
    
    @GetMapping("/{entityId}/exists")
    @Operation(summary = "Check entity existence", description = "Check if entity exists")
    @ApiResponse(responseCode = "200", description = "Check completed")
    public ResponseEntity<ApiResponse<Boolean>> checkEntityExists(
            @PathVariable @Schema(description = "Entity ID") UUID entityId) {
        
        boolean exists = entityService.existsById(entityId);
        return ResponseEntity.ok(ApiResponse.success("Check completed", exists));
    }
}
```

## Controller Patterns

### File Upload Controller
```java
@RestController
@RequestMapping("/api/entities/{entityId}/files")
@RequiredArgsConstructor
@Tag(name = "Entity Files", description = "Entity file management")
public class EntityFileController {
    
    private final EntityFileService fileService;
    
    @PostMapping
    @Operation(summary = "Upload file", description = "Upload file for entity")
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @PathVariable UUID entityId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description,
            @CurrentUserId UUID userId) {
        
        FileUploadRequest request = FileUploadRequest.builder()
            .file(file)
            .description(description)
            .entityId(entityId)
            .build();
            
        FileUploadResponse response = fileService.uploadFile(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("File uploaded successfully", response));
    }
    
    @GetMapping("/{fileId}/download")
    @Operation(summary = "Download file", description = "Download entity file")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable UUID entityId,
            @PathVariable UUID fileId,
            @CurrentUserId UUID userId) {
        
        FileDownloadResponse file = fileService.downloadFile(entityId, fileId, userId);
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                   "attachment; filename=\"" + file.getFilename() + "\"")
            .body(file.getResource());
    }
}
```

### Admin Controller Pattern
```java
@RestController
@RequestMapping("/api/admin/entities")
@RequiredArgsConstructor
@Tag(name = "Admin - Entities", description = "Administrative entity operations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEntityController {
    
    private final AdminEntityService adminService;
    
    @GetMapping("/stats")
    @Operation(summary = "Get entity statistics", description = "Get comprehensive entity statistics")
    public ResponseEntity<ApiResponse<EntityStatsResponse>> getEntityStatistics() {
        EntityStatsResponse stats = adminService.getEntityStatistics();
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved", stats));
    }
    
    @PostMapping("/{entityId}/restore")
    @Operation(summary = "Restore deleted entity", description = "Restore a soft-deleted entity")
    public ResponseEntity<ApiResponse<Void>> restoreEntity(@PathVariable UUID entityId) {
        adminService.restoreEntity(entityId);
        return ResponseEntity.ok(ApiResponse.success("Entity restored successfully"));
    }
    
    @DeleteMapping("/{entityId}/permanent")
    @Operation(summary = "Permanently delete entity", description = "Permanently delete an entity")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteEntity(@PathVariable UUID entityId) {
        adminService.permanentlyDeleteEntity(entityId);
        return ResponseEntity.ok(ApiResponse.success("Entity permanently deleted"));
    }
}
```

### Async Controller Pattern
```java
@RestController
@RequestMapping("/api/entities/async")
@RequiredArgsConstructor
@Tag(name = "Async Entity Operations", description = "Asynchronous entity operations")
public class AsyncEntityController {
    
    private final AsyncEntityService asyncService;
    
    @PostMapping("/bulk-import")
    @Operation(summary = "Bulk import entities", description = "Start bulk import process")
    public ResponseEntity<ApiResponse<AsyncOperationResponse>> bulkImportEntities(
            @RequestParam("file") MultipartFile file,
            @CurrentUserId UUID userId) {
        
        AsyncOperationResponse operation = asyncService.startBulkImport(file, userId);
        return ResponseEntity.accepted()
            .body(ApiResponse.success("Import started", operation));
    }
    
    @GetMapping("/operations/{operationId}")
    @Operation(summary = "Get operation status", description = "Get async operation status")
    public ResponseEntity<ApiResponse<AsyncOperationStatus>> getOperationStatus(
            @PathVariable UUID operationId,
            @CurrentUserId UUID userId) {
        
        AsyncOperationStatus status = asyncService.getOperationStatus(operationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Status retrieved", status));
    }
}
```

### WebSocket Controller Pattern
```java
@Controller
@RequiredArgsConstructor
@Slf4j
public class EntityWebSocketController {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final EntityNotificationService notificationService;
    
    @MessageMapping("/entities/{entityId}/subscribe")
    @SendToUser("/queue/entity-updates")
    public void subscribeToEntityUpdates(@DestinationVariable UUID entityId, Principal principal) {
        log.info("User {} subscribed to entity {} updates", principal.getName(), entityId);
        
        // Send initial state
        EntityUpdateMessage initialState = notificationService.getEntityState(entityId);
        messagingTemplate.convertAndSendToUser(
            principal.getName(), 
            "/queue/entity-updates", 
            initialState
        );
    }
    
    @EventListener
    public void handleEntityUpdated(EntityUpdatedEvent event) {
        EntityUpdateMessage message = EntityUpdateMessage.builder()
            .entityId(event.getEntityId())
            .updateType("UPDATED")
            .timestamp(Instant.now())
            .data(event.getData())
            .build();
            
        messagingTemplate.convertAndSend(
            "/topic/entity-updates/" + event.getEntityId(), 
            message
        );
    }
}
```

### Validation in Controller (Modern Patterns)
```java
@RestController
@RequestMapping("/api/entities")
@RequiredArgsConstructor
public class ValidatedEntityController {
    
    private final EntityService entityService;
    
    // ✅ Let JSR-380 validation work automatically.
    // If validation fails, Spring Boot automatically throws MethodArgumentNotValidException,
    // which our GlobalExceptionHandler catches and formats into a standardized error response.
    // This keeps the controller completely free of manual validation boilerplate.
    @PostMapping
    public ResponseEntity<ApiResponse<EntityResponse>> createEntity(
            @Valid @RequestBody CreateEntityRequest request,
            @CurrentUserId UUID userId) {
        
        EntityResponse entity = entityService.createEntity(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Entity created successfully", entity));
    }
}
```