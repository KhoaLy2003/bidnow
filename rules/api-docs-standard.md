# API Documentation Standard (Swagger/OpenAPI)

This document defines the rules and best practices for documenting APIs in the BidNow project using SpringDoc OpenAPI.

## 1. General Rules
- **Language**: All documentation (summaries, descriptions, examples) MUST be in **English**.
- **Aggregation**: Access all API documentation through the API Gateway at `http://localhost:8080/swagger-ui.html`.
- **Security**: Use the "Authorize" button in Swagger UI to provide a JWT token (Bearer token) for protected endpoints.

## 2. Controller Documentation
Every REST controller must be annotated to provide context.

### Class Level
- Use `@Tag` to group endpoints and provide a high-level description.
```java
@Tag(name = "User Management", description = "Endpoints for creating, updating, and managing users")
public class UserController { ... }
```

### Method Level
- Use `@Operation` to describe the purpose and details of the endpoint.
- Use `@Parameter` for path variables and request parameters.
- Use `@ApiResponse` for non-obvious response codes (e.g., 404, 403, 400).

```java
@GetMapping("/{id}")
@Operation(summary = "Get user by ID", description = "Fetches user details from the database.")
public ResponseEntity<UserResponse> getUser(
    @Parameter(description = "ID of the user", example = "123") @PathVariable Long id
) { ... }
```

## 3. DTO Documentation (Data Models)
All Request and Response DTOs must be documented to help frontend developers understand the data structure.

- Use `@Schema` at the class level to describe the DTO.
- Use `@Schema` on fields to provide:
    - `description`: Clear explanation of the field.
    - `example`: A realistic value.
    - `allowableValues`: If the field is an enum or has restricted values.
    - `requiredMode`: Specify if the field is required.

```java
@Schema(description = "User details response")
public class UserResponse {
    @Schema(description = "Full name of the user", example = "John Doe")
    private String name;
}
```

## 4. Security Documentation
For endpoints requiring authentication, add the security requirement:

```java
@Operation(summary = "Update profile")
@SecurityRequirement(name = "bearerAuth")
@PutMapping("/me")
public ResponseEntity<Void> updateProfile(...) { ... }
```

## 5. UI Customization
The Swagger UI is customized via the `OpenApiConfig` class in the `common` module. Global metadata changes should be made there.
