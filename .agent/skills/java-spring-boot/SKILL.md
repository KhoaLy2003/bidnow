# Java Spring Boot Development Skill

## 1. Summary & Description

This skill defines the development standards, patterns, and conventions for building enterprise-grade Java Spring Boot applications. It focuses on creating scalable, maintainable, and secure REST APIs with proper layered architecture, comprehensive error handling, and modern Java practices.

**Core Technologies:**

- Java 17+
- Spring Boot 3.x
- Spring Security with OAuth2/JWT
- Spring Data JPA with relational databases
- Redis for caching (optional)
- WebSocket for real-time features (optional)
- MapStruct for object mapping
- Lombok for boilerplate reduction
- OpenAPI/Swagger for documentation

## 2. Project Context

This skill is designed for building modern web applications and REST APIs using Spring Boot. It can be applied to various domains including:

**Application Types:**

- REST API backends
- Microservices
- Web applications with API layers
- Real-time applications with WebSocket support

**Common Use Cases:**

- Social media platforms
- E-commerce systems
- Content management systems
- Business applications
- SaaS platforms

## 3. Architecture Overview

### 3.1 Package Structure

```
com.company.application/
├── common/                    # Shared components
│   ├── annotation/           # Custom annotations
│   ├── config/              # Configuration classes
│   ├── controller/          # Common controllers
│   ├── dto/                 # Shared DTOs
│   ├── entity/              # Base entities
│   ├── exception/           # Global exception handling
│   ├── interceptor/         # Request/WebSocket interceptors
│   ├── logging/             # Logging aspects
│   ├── resolver/            # Argument resolvers
│   ├── service/             # Common services
│   └── util/                # Utility classes
└── feature/                  # Feature modules
    ├── user/                # User management
    ├── product/             # Product management
    ├── order/               # Order processing
    ├── notification/        # Notification system
    ├── payment/             # Payment processing
    ├── reporting/           # Reporting features
    ├── integration/         # External API integrations
    └── [domain-specific]/   # Other business domains
```

### 3.2 Layer Architecture (per feature)

```
feature/<feature_name>/
├── controller/              # REST endpoints
├── service/                 # Business logic
├── repository/              # Data access
├── entity/                  # JPA entities
├── dto/                     # Data transfer objects
├── mapper/                  # MapStruct mappers
├── event/                   # Domain events
├── listener/                # Event listeners
└── exception/               # Feature-specific exceptions
```

## 4. Request Workflow

### 4.1 Standard REST Request Flow

```
Client Request
    ↓
Security Filter (JWT validation)
    ↓
Controller (validation, delegation)
    ↓
Service (business logic, transactions)
    ↓
Repository (data access)
    ↓
Database/Cache
    ↓
Response (DTO mapping, standardized format)
```

### 4.2 Caching Strategy

```
Request → Service → Cache Check → Cache Hit? → Return Cached Data
                        ↓ (Cache Miss)
                   Database/External API → Cache Store → Return Data
```

## 5. Code Generation Rules

This section provides references to detailed code generation rules for each component type. Each rule file contains comprehensive standards, patterns, and examples.

### 5.1 Component Rules Reference

| Component                  | Rule File                                                                | Description                                                   |
| -------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------- |
| **Entity**                 | [entity-rules.md](./rules/entity-rules.md)                             | JPA entities, base entity patterns, relationships, validation |
| **Repository**             | [repository-rules.md](./rules/repository-rules.md)                     | Data access layer, query methods, specifications              |
| **Service**                | [service-rules.md](./rules/service-rules.md)                           | Business logic, transactions, caching, event publishing       |
| **DTO**                    | [dto-rules.md](./rules/dto-rules.md)                                   | Request/response objects, validation, documentation           |
| **Mapper**                 | [mapper-rules.md](./rules/mapper-rules.md)                             | MapStruct mappers, object transformations                     |
| **Controller**             | [controller-rules.md](./rules/controller-rules.md)                     | REST endpoints, validation, documentation, error handling     |
| **Configuration**          | [config-rules.md](./rules/config-rules.md)                             | Spring configuration, security, caching, WebSocket            |
| **Exception**              | [exception-rules.md](./rules/exception-rules.md)                       | Custom exceptions, global error handling                      |
| **Utility**                | [util-rules.md](./rules/util-rules.md)                                 | Helper classes, common utilities, validation                  |
| **Constants**              | [constant-rules.md](./rules/constant-rules.md)                         | Application constants, enums, configuration values            |
| **Validation & Security**  | [validation-security-rules.md](./rules/validation-security-rules.md)   | Input validation, authentication, authorization, data security |
| **Logging & Observability** | [logging-observability-rules.md](./rules/logging-observability-rules.md) | Logging, health checks, metrics, monitoring, tracing          |
| **Testing**                | [testing-rules.md](./rules/testing-rules.md)                             | Unit, WebMvcTest, DataJpaTest slices, Testcontainers standards|
| **Database Migration**     | [migration-rules.md](./rules/migration-rules.md)                         | Liquibase changelogs, zero-downtime, expand-contract patterns |

### 5.2 Key Principles

- **Separation of Concerns**: Each layer has a specific responsibility
- **Dependency Injection**: Use constructor injection for better testability
- **Immutability**: Prefer immutable objects where possible
- **Validation**: Validate at appropriate layers (controller for input, service for business rules)
- **Error Handling**: Use specific exceptions and global error handling
- **Documentation**: Document APIs with OpenAPI annotations
- **Testing**: Write comprehensive unit and integration tests

## 6. Code Review Checklist

### 6.1 Architecture & Design

- [ ] Follows feature-based package structure
- [ ] Proper separation of concerns (Controller → Service → Repository)
- [ ] DTOs used instead of exposing entities
- [ ] Appropriate use of design patterns
- [ ] No business logic in controllers
- [ ] Proper transaction boundaries

### 6.2 Code Quality

- [ ] Follows naming conventions
- [ ] Proper error handling with custom exceptions
- [ ] Input validation on all request DTOs
- [ ] Null safety and defensive programming
- [ ] No code duplication
- [ ] Appropriate use of Lombok annotations

### 6.3 Security

- [ ] Proper authentication and authorization
- [ ] Input sanitization and validation
- [ ] No sensitive data in logs
- [ ] Secure configuration management
- [ ] SQL injection prevention
- [ ] CORS configuration if needed

### 6.4 Performance

- [ ] Appropriate caching strategy
- [ ] Lazy loading for JPA relationships
- [ ] Proper indexing for database queries
- [ ] Pagination for large datasets
- [ ] Connection pooling configuration
- [ ] Avoid N+1 query problems

### 6.5 Testing

- [ ] Unit tests for service layer
- [ ] Integration tests for critical flows
- [ ] Test coverage > 80%
- [ ] Mock external dependencies
- [ ] Test error scenarios
- [ ] Performance tests for critical endpoints

### 6.6 Documentation

- [ ] OpenAPI documentation complete
- [ ] README updated if needed
- [ ] Code comments for complex logic
- [ ] API documentation examples
- [ ] Database migration scripts

## 7. Folder Structure Tree

```
project-root/
├── src/
│   ├── main/
│   │   ├── java/com/company/application/
│   │   │   ├── Application.java
│   │   │   ├── common/
│   │   │   │   ├── annotation/
│   │   │   │   │   └── CurrentUserId.java
│   │   │   │   ├── config/
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── CacheConfig.java
│   │   │   │   │   ├── WebSocketConfig.java
│   │   │   │   │   └── OpenApiConfig.java
│   │   │   │   ├── controller/
│   │   │   │   │   └── HealthController.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ApiResponse.java
│   │   │   │   │   └── PageResponse.java
│   │   │   │   ├── entity/
│   │   │   │   │   └── BaseEntity.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   └── BadRequestException.java
│   │   │   │   ├── logging/
│   │   │   │   │   ├── LogExecutionTime.java
│   │   │   │   │   └── LoggingAspect.java
│   │   │   │   ├── service/
│   │   │   │   │   └── AuthenticationService.java
│   │   │   │   └── util/
│   │   │   │       ├── PaginationUtils.java
│   │   │   │       └── SecurityUtils.java
│   │   │   └── feature/
│   │   │       ├── user/
│   │   │       │   ├── controller/UserController.java
│   │   │       │   ├── service/UserService.java
│   │   │       │   ├── repository/UserRepository.java
│   │   │       │   ├── entity/User.java
│   │   │       │   ├── dto/
│   │   │       │   │   ├── CreateUserRequest.java
│   │   │       │   │   └── UserResponse.java
│   │   │       │   └── mapper/UserMapper.java
│   │   │       ├── product/
│   │   │       │   ├── controller/ProductController.java
│   │   │       │   ├── service/ProductService.java
│   │   │       │   ├── repository/ProductRepository.java
│   │   │       │   ├── entity/Product.java
│   │   │       │   ├── dto/
│   │   │       │   │   ├── CreateProductRequest.java
│   │   │       │   │   └── ProductResponse.java
│   │   │       │   ├── mapper/ProductMapper.java
│   │   │       │   └── event/ProductCreatedEvent.java
│   │   │       └── [other features...]
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/changelog/
│   │           ├── db.changelog-master.xml
│   │           └── migrations/
│   │               └── 001-initial-schema.sql
│   └── test/
│       └── java/com/company/application/
│           ├── feature/
│           │   ├── user/
│           │   │   └── service/UserServiceTest.java
│           │   └── product/
│           │       └── service/ProductServiceTest.java
│           └── integration/
│               └── ProductIntegrationTest.java
├── pom.xml
├── CODING_CONVENTIONS.md
└── README.md
```

| Version | Date       | Changes                                                 |
| ------- | ---------- | ------------------------------------------------------- |
| 1.2.0   | 2026-05-17 | Migrated database migration rules from Flyway to Liquibase formatted SQL, added changelogs, rollback rules, and non-blocking index patterns |
| 1.1.0   | 2026-05-17 | Updated all rule files to fix critical production risks (safe transactional defaults, JPA builder fixes, rate limit race condition, SecureRandom, etc.) and created explicit testing-rules.md & migration-rules.md guidelines |
| 1.0.0   | 2026-01-18 | Initial comprehensive skill definition with Spring Boot 3.x, Java 17+, separated detailed rules into individual files for better maintainability |

---

**Skill Maintainer:** Development Team
**Last Updated:** May 17, 2026
**Next Review:** August 17, 2026

## Usage Notes

This skill provides a comprehensive framework for Java Spring Boot development. The main SKILL.md file provides an overview and references to detailed rule files. Each rule file contains:

- **Standards**: Core principles and requirements
- **Templates**: Code examples and patterns
- **Best Practices**: Recommended approaches
- **Common Patterns**: Reusable solutions

When implementing a new feature or component, refer to the appropriate rule file for detailed guidance and examples. The patterns shown can be adapted to any business domain while maintaining consistency and quality.
