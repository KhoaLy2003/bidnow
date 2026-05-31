# Constant Rules

## Standards

### Constant Definition
- Use `public static final` for constants
- Use descriptive, UPPER_SNAKE_CASE naming
- Group related constants in dedicated classes
- Make constant classes final with private constructor
- Document purpose and usage where needed

### Organization
- Create separate constant classes by domain
- Use nested classes for sub-categories
- Provide meaningful constant values
- Use enums for related constant groups
- Keep constants close to their usage context

### Best Practices
- Avoid magic numbers and strings
- Use constants for static configuration values (header names, property keys)
- Provide default values where appropriate
- **Prefer Enums over raw String constants** for status values, categories, notification types, priorities, or any state engine values. Raw strings provide no compile-time checks, leading to typos and inconsistent database representation
- Use `@ConfigurationProperties` for dynamic/configurable constants rather than static `public static final` fields

## Example Templates

### API Constants
```java
public final class ApiConstants {
    
    // API Versioning
    public static final String API_VERSION_1 = "/api/v1";
    public static final String API_VERSION_2 = "/api/v2";
    public static final String CURRENT_API_VERSION = API_VERSION_1;
    
    // Common Paths
    public static final String HEALTH_PATH = "/health";
    public static final String DOCS_PATH = "/docs";
    public static final String SWAGGER_PATH = "/swagger-ui";
    
    // Resource Paths
    public static final String USERS_PATH = "/users";
    public static final String POSTS_PATH = "/posts";
    public static final String COMMENTS_PATH = "/comments";
    public static final String CATEGORIES_PATH = "/categories";
    public static final String FILES_PATH = "/files";
    
    // HTTP Headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String ACCEPT_HEADER = "Accept";
    public static final String USER_AGENT_HEADER = "User-Agent";
    public static final String X_REQUEST_ID_HEADER = "X-Request-ID";
    
    // Content Types
    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_XML = "application/xml";
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";
    public static final String TEXT_PLAIN = "text/plain";
    
    // Authentication
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String BASIC_PREFIX = "Basic ";
    
    private ApiConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
```

### Cache Constants
```java
public final class CacheConstants {
    
    // Cache Names
    public static final String USER_CACHE = "users";
    public static final String POST_CACHE = "posts";
    public static final String CATEGORY_CACHE = "categories";
    public static final String SETTINGS_CACHE = "settings";
    
    // Cache Keys
    public static final String USER_BY_ID_KEY = "user:id:";
    public static final String USER_BY_USERNAME_KEY = "user:username:";
    public static final String POST_BY_ID_KEY = "post:id:";
    public static final String POST_LIST_KEY = "post:list:";
    public static final String CATEGORY_LIST_KEY = "category:list";
    
    // TTL Values (in seconds)
    public static final long SHORT_TTL = 300;      // 5 minutes
    public static final long MEDIUM_TTL = 1800;    // 30 minutes
    public static final long LONG_TTL = 3600;      // 1 hour
    public static final long VERY_LONG_TTL = 86400; // 24 hours
    
    // Cache Sizes
    public static final int SMALL_CACHE_SIZE = 100;
    public static final int MEDIUM_CACHE_SIZE = 1000;
    public static final int LARGE_CACHE_SIZE = 10000;
    
    private CacheConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
```

### Validation Constants
```java
public final class ValidationConstants {
    
    // String Length Limits
    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 50;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_CONTENT_LENGTH = 5000;
    
    // Numeric Limits
    public static final int MIN_AGE = 13;
    public static final int MAX_AGE = 120;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;
    
    // File Upload Limits
    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    public static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;  // 5MB
    public static final int MAX_FILES_PER_REQUEST = 10;
    
    // Regular Expressions
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]{3,50}$";
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$";
    public static final String PHONE_PATTERN = "^\\+?[1-9]\\d{1,14}$";
    public static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    
    // Validation Messages
    public static final String REQUIRED_FIELD_MESSAGE = "This field is required";
    public static final String INVALID_EMAIL_MESSAGE = "Please provide a valid email address";
    public static final String INVALID_USERNAME_MESSAGE = "Username must be 3-50 characters and contain only letters, numbers, and underscores";
    public static final String PASSWORD_TOO_SHORT_MESSAGE = "Password must be at least 8 characters long";
    public static final String FILE_TOO_LARGE_MESSAGE = "File size exceeds maximum allowed size";
    
    private ValidationConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
```

### Database Constants
```java
public final class DatabaseConstants {
    
    // Table Names
    public static final String USERS_TABLE = "users";
    public static final String POSTS_TABLE = "posts";
    public static final String COMMENTS_TABLE = "comments";
    public static final String CATEGORIES_TABLE = "categories";
    public static final String FILES_TABLE = "files";
    
    // Column Names
    public static final String ID_COLUMN = "id";
    public static final String CREATED_AT_COLUMN = "created_at";
    public static final String UPDATED_AT_COLUMN = "updated_at";
    public static final String DELETED_AT_COLUMN = "deleted_at";
    public static final String VERSION_COLUMN = "version";
    
    // Index Names
    public static final String IDX_USERS_EMAIL = "idx_users_email";
    public static final String IDX_USERS_USERNAME = "idx_users_username";
    public static final String IDX_POSTS_AUTHOR = "idx_posts_author_id";
    public static final String IDX_POSTS_CREATED_AT = "idx_posts_created_at";
    public static final String IDX_COMMENTS_POST = "idx_comments_post_id";
    
    // Constraint Names
    public static final String UK_USERS_EMAIL = "uk_users_email";
    public static final String UK_USERS_USERNAME = "uk_users_username";
    public static final String FK_POSTS_AUTHOR = "fk_posts_author_id";
    public static final String FK_COMMENTS_POST = "fk_comments_post_id";
    
    // Query Limits
    public static final int MAX_BATCH_SIZE = 1000;
    public static final int DEFAULT_FETCH_SIZE = 100;
    public static final int MAX_IN_CLAUSE_SIZE = 1000;
    
    private DatabaseConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
```

### Security Constants
```java
public final class SecurityConstants {
    
    // Roles
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MODERATOR = "MODERATOR";
    public static final String ROLE_GUEST = "GUEST";
    
    // Permissions
    public static final String PERMISSION_READ = "READ";
    public static final String PERMISSION_WRITE = "WRITE";
    public static final String PERMISSION_DELETE = "DELETE";
    public static final String PERMISSION_ADMIN = "ADMIN";
    
    // JWT Claims
    public static final String CLAIM_USER_ID = "sub";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";
    
    // Security Headers
    public static final String X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String X_XSS_PROTECTION = "X-XSS-Protection";
    public static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    
    // CORS
    public static final String CORS_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String CORS_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String CORS_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String CORS_MAX_AGE = "Access-Control-Max-Age";
    
    // Rate Limiting
    public static final int DEFAULT_RATE_LIMIT = 100;
    public static final int ADMIN_RATE_LIMIT = 1000;
    public static final long RATE_LIMIT_WINDOW_SECONDS = 3600; // 1 hour
    
    private SecurityConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
```

### Business Constants
```java
public final class BusinessConstants {
    
    // ❌ ANTI-PATTERN — Avoid defining status/priority/type values as String constants
    // Use type-safe Java Enums instead (e.g., EntityStatus enum defined below).
    // String constants are retained here only to show the deprecated pattern.
    
    // Deprecated Status Values — Use EntityStatus enum instead
    @Deprecated
    public static final class Status {
        public static final String ACTIVE = "ACTIVE";
        public static final String INACTIVE = "INACTIVE";
        public static final String PENDING = "PENDING";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String DELETED = "DELETED";
        
        private Status() {
            throw new UnsupportedOperationException("Utility class cannot be instantiated");
        }
    }
    
    // Deprecated Priority Levels — Use a specific Priority enum instead
    @Deprecated
    public static final class Priority {
        public static final String LOW = "LOW";
        public static final String MEDIUM = "MEDIUM";
        public static final String HIGH = "HIGH";
        public static final String CRITICAL = "CRITICAL";
        
        private Priority() {
            throw new UnsupportedOperationException("Utility class cannot be instantiated");
        }
    }
    
    // Deprecated Content Types — Use a specific ContentType enum instead
    @Deprecated
    public static final class ContentType {
        public static final String TEXT = "TEXT";
        public static final String IMAGE = "IMAGE";
        public static final String VIDEO = "VIDEO";
        public static final String AUDIO = "AUDIO";
        public static final String DOCUMENT = "DOCUMENT";
        
        private ContentType() {
            throw new UnsupportedOperationException("Utility class cannot be instantiated");
        }
    }
    
    // Deprecated Notification Types — Use a specific NotificationType enum instead
    @Deprecated
    public static final class NotificationType {
        public static final String COMMENT = "COMMENT";
        public static final String LIKE = "LIKE";
        public static final String FOLLOW = "FOLLOW";
        public static final String MENTION = "MENTION";
        public static final String SYSTEM = "SYSTEM";
        
        private NotificationType() {
            throw new UnsupportedOperationException("Utility class cannot be instantiated");
        }
    }
    
    // Default Values
    public static final String DEFAULT_AVATAR_URL = "/images/default-avatar.png";
    public static final String DEFAULT_COVER_URL = "/images/default-cover.png";
    public static final String DEFAULT_TIMEZONE = "UTC";
    public static final String DEFAULT_LANGUAGE = "en";
    public static final String DEFAULT_THEME = "light";
    
    private BusinessConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
```

### Configuration Constants
```java
public final class ConfigConstants {
    
    // Profile Names
    public static final String PROFILE_DEV = "dev";
    public static final String PROFILE_TEST = "test";
    public static final String PROFILE_STAGING = "staging";
    public static final String PROFILE_PROD = "prod";
    
    // Property Keys
    public static final String PROP_APP_NAME = "app.name";
    public static final String PROP_APP_VERSION = "app.version";
    public static final String PROP_APP_DESCRIPTION = "app.description";
    public static final String PROP_SERVER_PORT = "server.port";
    public static final String PROP_DATABASE_URL = "spring.datasource.url";
    public static final String PROP_REDIS_HOST = "spring.data.redis.host";
    public static final String PROP_JWT_SECRET = "security.jwt.secret";
    
    // Feature Flags
    public static final String FEATURE_CACHING_ENABLED = "feature.caching.enabled";
    public static final String FEATURE_WEBSOCKET_ENABLED = "feature.websocket.enabled";
    public static final String FEATURE_FILE_UPLOAD_ENABLED = "feature.file-upload.enabled";
    public static final String FEATURE_NOTIFICATIONS_ENABLED = "feature.notifications.enabled";
    
    // Default Configuration Values
    public static final int DEFAULT_SERVER_PORT = 8080;
    public static final int DEFAULT_REDIS_PORT = 6379;
    public static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
    public static final int DEFAULT_READ_TIMEOUT = 60000;
    
    private ConfigConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
```

## Enum Constants Pattern

### Status Enum
```java
public enum EntityStatus {
    ACTIVE("Active", "Entity is active and visible"),
    INACTIVE("Inactive", "Entity is inactive but not deleted"),
    PENDING("Pending", "Entity is pending approval"),
    SUSPENDED("Suspended", "Entity is temporarily suspended"),
    DELETED("Deleted", "Entity is soft deleted");
    
    private final String displayName;
    private final String description;
    
    EntityStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isActive() {
        return this == ACTIVE;
    }
    
    public boolean isVisible() {
        return this == ACTIVE || this == INACTIVE;
    }
    
    public static EntityStatus fromString(String status) {
        if (status == null) {
            return null;
        }
        
        try {
            return EntityStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

### Error Code Enum
```java
public enum ErrorCode {
    // Validation Errors (1000-1999)
    VALIDATION_FAILED(1000, "Validation failed"),
    REQUIRED_FIELD_MISSING(1001, "Required field is missing"),
    INVALID_FORMAT(1002, "Invalid format"),
    VALUE_OUT_OF_RANGE(1003, "Value is out of allowed range"),
    
    // Authentication Errors (2000-2999)
    AUTHENTICATION_FAILED(2000, "Authentication failed"),
    INVALID_CREDENTIALS(2001, "Invalid credentials"),
    TOKEN_EXPIRED(2002, "Token has expired"),
    TOKEN_INVALID(2003, "Token is invalid"),
    
    // Authorization Errors (3000-3999)
    ACCESS_DENIED(3000, "Access denied"),
    INSUFFICIENT_PERMISSIONS(3001, "Insufficient permissions"),
    RESOURCE_FORBIDDEN(3002, "Resource access forbidden"),
    
    // Resource Errors (4000-4999)
    RESOURCE_NOT_FOUND(4000, "Resource not found"),
    RESOURCE_ALREADY_EXISTS(4001, "Resource already exists"),
    RESOURCE_CONFLICT(4002, "Resource conflict"),
    
    // System Errors (5000-5999)
    INTERNAL_SERVER_ERROR(5000, "Internal server error"),
    DATABASE_ERROR(5001, "Database error"),
    EXTERNAL_SERVICE_ERROR(5002, "External service error"),
    CONFIGURATION_ERROR(5003, "Configuration error");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }
}
```