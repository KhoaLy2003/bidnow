# Exception Rules

## Standards

### Exception Hierarchy

- **Create specific business exceptions instead of using generic `RuntimeException` or `Exception` directly** to prevent swallowing error context and ensure precise HTTP response mapping.
- Extend `RuntimeException` for unchecked custom business exceptions to keep method signatures clean (avoiding legacy `throws` clutter).
- Use `@ResponseStatus` to map to HTTP status codes
- Create specific exceptions for different error types
- Provide meaningful error messages
- Include cause when wrapping exceptions

### Global Exception Handling

- Use `@ControllerAdvice` for centralized exception handling
- Return standardized error response format
- Log exceptions appropriately
- Handle validation errors specifically
- Provide user-friendly error messages

### Custom Exceptions

- Create domain-specific exceptions
- Include relevant context information
- Use builder pattern for complex exceptions
- Implement proper toString() methods

## Example Templates

### Custom Exception Templates

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("%s not found with id: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    private final String field;
    private final Object rejectedValue;

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String field, Object rejectedValue, String message) {
        super(message);
        this.field = field;
        this.rejectedValue = rejectedValue;
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getField() {
        return field;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }
}

@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    private final String conflictType;
    private final Map<String, Object> conflictDetails;

    public ConflictException(String message) {
        super(message);
        this.conflictType = "GENERAL";
        this.conflictDetails = new HashMap<>();
    }

    public ConflictException(String conflictType, String message, Map<String, Object> details) {
        super(message);
        this.conflictType = conflictType;
        this.conflictDetails = details != null ? details : new HashMap<>();
    }

    public String getConflictType() {
        return conflictType;
    }

    public Map<String, Object> getConflictDetails() {
        return conflictDetails;
    }
}

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends RuntimeException {

    private final String resource;
    private final String action;
    private final String userId;

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(String resource, String action, String userId) {
        super(String.format("Access denied for user %s to %s on resource %s", userId, action, resource));
        this.resource = resource;
        this.action = action;
        this.userId = userId;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    public String getUserId() {
        return userId;
    }
}
```

### Business Exception Templates

```java
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessRuleViolationException extends RuntimeException {

    private final String ruleCode;
    private final Map<String, Object> context;

    public BusinessRuleViolationException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
        this.context = new HashMap<>();
    }

    public BusinessRuleViolationException(String ruleCode, String message, Map<String, Object> context) {
        super(message);
        this.ruleCode = ruleCode;
        this.context = context != null ? context : new HashMap<>();
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Map<String, Object> getContext() {
        return context;
    }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends RuntimeException {

    private final Map<String, List<String>> fieldErrors;
    private final List<String> globalErrors;

    public ValidationException(String message) {
        super(message);
        this.fieldErrors = new HashMap<>();
        this.globalErrors = new ArrayList<>();
    }

    public ValidationException(String message, Map<String, List<String>> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null ? fieldErrors : new HashMap<>();
        this.globalErrors = new ArrayList<>();
    }

    public ValidationException(String message, BindingResult bindingResult) {
        super(message);
        this.fieldErrors = new HashMap<>();
        this.globalErrors = new ArrayList<>();

        if (bindingResult != null) {
            bindingResult.getFieldErrors().forEach(error ->
                fieldErrors.computeIfAbsent(error.getField(), k -> new ArrayList<>())
                    .add(error.getDefaultMessage()));

            bindingResult.getGlobalErrors().forEach(error ->
                globalErrors.add(error.getDefaultMessage()));
        }
    }

    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }

    public List<String> getGlobalErrors() {
        return globalErrors;
    }
}
```

### Global Exception Handler Template

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorDetails details = ErrorDetails.builder()
            .code("RESOURCE_NOT_FOUND")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("Resource not found", details));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());

        ErrorDetails details = ErrorDetails.builder()
            .code("BAD_REQUEST")
            .message(ex.getMessage())
            .field(ex.getField())
            .rejectedValue(ex.getRejectedValue())
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Invalid request", details));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());

        ErrorDetails details = ErrorDetails.builder()
            .code("CONFLICT")
            .message(ex.getMessage())
            .conflictType(ex.getConflictType())
            .details(ex.getConflictDetails())
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("Conflict detected", details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorDetails details = ErrorDetails.builder()
            .code("ACCESS_DENIED")
            .message("Access denied")
            .resource(ex.getResource())
            .action(ex.getAction())
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Access denied", details));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleValidation(ValidationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        ValidationErrorDetails details = ValidationErrorDetails.builder()
            .code("VALIDATION_FAILED")
            .message(ex.getMessage())
            .fieldErrors(ex.getFieldErrors())
            .globalErrors(ex.getGlobalErrors())
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed", details));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        log.warn("Method argument validation failed: {}", ex.getMessage());

        Map<String, List<String>> fieldErrors = new HashMap<>();
        List<String> globalErrors = new ArrayList<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.computeIfAbsent(error.getField(), k -> new ArrayList<>())
                .add(error.getDefaultMessage()));

        ex.getBindingResult().getGlobalErrors().forEach(error ->
            globalErrors.add(error.getDefaultMessage()));

        ValidationErrorDetails details = ValidationErrorDetails.builder()
            .code("VALIDATION_FAILED")
            .message("Request validation failed")
            .fieldErrors(fieldErrors)
            .globalErrors(globalErrors)
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed", details));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleBusinessRuleViolation(
            BusinessRuleViolationException ex) {

        log.warn("Business rule violation: {} - {}", ex.getRuleCode(), ex.getMessage());

        ErrorDetails details = ErrorDetails.builder()
            .code(ex.getRuleCode())
            .message(ex.getMessage())
            .details(ex.getContext())
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error("Business rule violation", details));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("JPA Entity not found: {}", ex.getMessage());

        ErrorDetails details = ErrorDetails.builder()
            .code("ENTITY_NOT_FOUND")
            .message("Requested entity not found")
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("Entity not found", details));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        log.error("Data integrity violation: {}", ex.getMessage());

        String userMessage = "Data integrity constraint violated";
        String code = "DATA_INTEGRITY_VIOLATION";

        // Parse specific constraint violations
        if (ex.getCause() instanceof ConstraintViolationException) {
            ConstraintViolationException cve = (ConstraintViolationException) ex.getCause();
            String constraintName = cve.getConstraintName();

            if (constraintName != null) {
                if (constraintName.contains("unique")) {
                    userMessage = "Duplicate entry detected";
                    code = "DUPLICATE_ENTRY";
                } else if (constraintName.contains("foreign")) {
                    userMessage = "Referenced entity not found";
                    code = "FOREIGN_KEY_VIOLATION";
                }
            }
        }

        ErrorDetails details = ErrorDetails.builder()
            .code(code)
            .message(userMessage)
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(userMessage, details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorDetails details = ErrorDetails.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message("An unexpected error occurred")
            .timestamp(Instant.now())
            .build();

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Internal server error", details));
    }
}
```

### Error Details DTOs

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDetails {
    private String code;
    private String message;
    private String field;
    private Object rejectedValue;
    private String resource;
    private String action;
    private String conflictType;
    private Map<String, Object> details;
    private Instant timestamp;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorDetails {
    private String code;
    private String message;
    private Map<String, List<String>> fieldErrors;
    private List<String> globalErrors;
    private Instant timestamp;
}
```

## Exception Builder Pattern

### Exception Builder

```java
public class ExceptionBuilder {

    public static ResourceNotFoundExceptionBuilder resourceNotFound() {
        return new ResourceNotFoundExceptionBuilder();
    }

    public static BadRequestExceptionBuilder badRequest() {
        return new BadRequestExceptionBuilder();
    }

    public static ConflictExceptionBuilder conflict() {
        return new ConflictExceptionBuilder();
    }

    public static class ResourceNotFoundExceptionBuilder {
        private String resourceType;
        private String resourceId;
        private String message;

        public ResourceNotFoundExceptionBuilder resource(String type, String id) {
            this.resourceType = type;
            this.resourceId = id;
            return this;
        }

        public ResourceNotFoundExceptionBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ResourceNotFoundException build() {
            if (message != null) {
                return new ResourceNotFoundException(message);
            }
            return new ResourceNotFoundException(resourceType, resourceId);
        }

        public ResourceNotFoundException throwIt() {
            throw build();
        }
    }

    public static class BadRequestExceptionBuilder {
        private String field;
        private Object rejectedValue;
        private String message;

        public BadRequestExceptionBuilder field(String field, Object rejectedValue) {
            this.field = field;
            this.rejectedValue = rejectedValue;
            return this;
        }

        public BadRequestExceptionBuilder message(String message) {
            this.message = message;
            return this;
        }

        public BadRequestException build() {
            if (field != null) {
                return new BadRequestException(field, rejectedValue, message);
            }
            return new BadRequestException(message);
        }

        public BadRequestException throwIt() {
            throw build();
        }
    }
}

// Usage examples:
// ExceptionBuilder.resourceNotFound().resource("User", userId).throwIt();
// ExceptionBuilder.badRequest().field("email", email).message("Invalid email format").throwIt();
```
