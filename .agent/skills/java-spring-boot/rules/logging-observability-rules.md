# Logging & Observability Rules

## Standards

### 1. General Principles
- **Logback + SLF4J**: Always use SLF4J as the logging facade with Logback as the implementation engine.
- **Structured JSON Logging**: All logs in non-local environments (Dev, Test, Staging, Prod) must be formatted as structured JSON for indexing in observability platforms (ELK, Splunk, Datadog).
- **Correlation ID (Tracing)**: Every request entering the system must be tagged with a unique tracing ID (Correlation ID) injected into the MDC (Mapped Diagnostic Context) to trace execution flow across microservices.
- **Log Rotation**: Configure standard log rotation to trigger on daily intervals or when file size exceeds 100MB to protect system disk space.
- **PII & Credential Masking**: Never log raw sensitive data (passwords, tokens, PCI-DSS card info, personal data). Apply masking filters to interceptors and log formatters.

### 2. Standard Log Levels Definition
Log levels categorize and prioritize logs. They must follow this hierarchy:
```
TRACE < DEBUG < INFO < WARN < ERROR < FATAL
```

#### **TRACE**
- **Purpose**: Extremely verbose step-by-step diagnostics.
- **Usage**: Detailed transaction steps, execution flow tracking, or heavy data dumps in localized dev environments.
- **Rule**: Never enable TRACE in production configurations as it severely degrades application performance.

#### **DEBUG**
- **Purpose**: Granular logic insights for troubleshooting.
- **Usage**: Logging key variable values, decision-making branches (if/else), and entity transformations.
- **Rule**: Enabled only in local or dev environments.

#### **INFO**
- **Purpose**: Core application operational events.
- **Usage**: Application boot/shutdown logs, database connection initialization, external integration startup, and major business events (e.g., `user.registrations`, `orders.created`, `payments.processed`).
- **Rule**: This is the default level for Staging and Production.

#### **WARN**
- **Purpose**: Non-fatal operational anomalies requiring attention.
- **Usage**: Deprecated API invocations, fallback executions, transient retry attempts, and warning thresholds (e.g., high memory consumption or rate limit bounds).

#### **ERROR**
- **Purpose**: System-wide failures affecting specific operations.
- **Usage**: Uncaught exceptions, payment failures, API contract violations, and external system timeouts.
- **Rule**: Every error log **must** include context (MDC Correlation ID, User ID) and a full exception stack trace.

#### **FATAL / CRITICAL**
- **Purpose**: System crash or total block of a core infrastructure capability.
- **Usage**: Out-of-memory errors, lost database connections (unrecoverable), corrupted start configuration, or expired key security certificates.

### 3. Microservice Observability Strategy
- **Distributed Trace Context**: Every HTTP header or broker message must carry context:
  - `trace_id`: Correlates all service logs belonging to the original client request.
  - `span_id`: Identifies the individual workspace unit execution.
  - `service_name`: The service origin tag.
- **Adaptive Sampling**: In extreme traffic environments, sample successful `INFO` level logs (e.g., log 10%), while retaining 100% of `WARN` and `ERROR` events to control storage volume.

---

## Code Templates & Patterns

### 1. Logging Aspect (Automated Logging)
Use Spring AOP to automatically log execution details, entry inputs, and uncaught service layer errors:
```java
@Aspect
@Component
@Slf4j
public class LoggingAspect {
    
    @Around("@annotation(LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("Method {} executed successfully in {} ms", methodName, executionTime);
            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Method {} failed after {} ms: {}", methodName, executionTime, e.getMessage(), e);
            throw e;
        }
    }
    
    @AfterThrowing(pointcut = "@within(org.springframework.stereotype.Service)", throwing = "ex")
    public void logServiceExceptions(JoinPoint joinPoint, Exception ex) {
        String methodName = joinPoint.getSignature().toShortString();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        log.error("Exception in service {}.{}: {}", className, methodName, ex.getMessage(), ex);
    }
    
    @Before("@within(org.springframework.web.bind.annotation.RestController)")
    public void logControllerEntry(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        
        log.info("Controller method {} called with {} arguments", methodName, args.length);
        
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null && !isSensitiveType(arg.getClass())) {
                log.debug("Argument {}: {}", i, arg);
            }
        }
    }
    
    private boolean isSensitiveType(Class<?> type) {
        String typeName = type.getName().toLowerCase();
        return type == HttpServletRequest.class || 
               type == HttpServletResponse.class ||
               typeName.contains("password") ||
               typeName.contains("credential") ||
               typeName.contains("card") ||
               typeName.contains("token");
    }
}
```

```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {
}
```

### 2. Structured JSON Event Logger
Use structured log models to dump analytical event JSON strings:
```java
@Component
@Slf4j
public class StructuredLogger {
    
    private final ObjectMapper objectMapper;
    
    public StructuredLogger(ObjectMapper objectMapper) {
        // Reuse singleton bean
        this.objectMapper = objectMapper;
    }
    
    public void logUserAction(String action, UUID userId, Map<String, Object> details) {
        try {
            LogEvent event = LogEvent.builder()
                .timestamp(Instant.now())
                .level("INFO")
                .category("USER_ACTION")
                .action(action)
                .userId(userId)
                .details(details)
                .correlationId(MDC.get("correlationId"))
                .build();
                
            log.info("USER_ACTION: {}", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to log user action JSON", e);
        }
    }
    
    public void logSecurityEvent(String eventType, String username, String ipAddress, Map<String, Object> details) {
        try {
            SecurityEvent event = SecurityEvent.builder()
                .timestamp(Instant.now())
                .eventType(eventType)
                .username(username)
                .ipAddress(ipAddress)
                .details(details)
                .correlationId(MDC.get("correlationId"))
                .build();
                
            log.warn("SECURITY_EVENT: {}", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to log security event JSON", e);
        }
    }
}
```

```java
@Data
@Builder
public class LogEvent {
    private Instant timestamp;
    private String level;
    private String category;
    private String action;
    private UUID userId;
    private Map<String, Object> details;
    private String correlationId;
}

@Data
@Builder
public class SecurityEvent {
    private Instant timestamp;
    private String eventType;
    private String username;
    private String ipAddress;
    private Map<String, Object> details;
    private String correlationId;
}
```

### 3. MDC Tracing Filter (Servlet Layer)
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        try {
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

---

## Health Checks & Diagnostics

### Custom Actuator Health Indicators
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("validationQuery", "SELECT 1")
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "Connection validation failed")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "Connection failed")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### Comprehensive Kubernetes Probe Controller
```java
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Application health and container probe endpoints")
public class HealthController {
    
    private final HealthEndpoint healthEndpoint;
    private final DataSource dataSource;
    private final Environment environment;
    
    public HealthController(HealthEndpoint healthEndpoint, 
                            DataSource dataSource, 
                            Environment environment) {
        this.healthEndpoint = healthEndpoint;
        this.dataSource = dataSource;
        this.environment = environment;
    }
    
    @GetMapping("/readiness")
    @Operation(summary = "Kubernetes readiness probe")
    public ResponseEntity<Map<String, String>> readiness() {
        boolean isReady = checkDatabaseConnection();
        Map<String, String> response = new HashMap<>();
        response.put("status", isReady ? "UP" : "DOWN");
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.status(isReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .body(response);
    }
    
    @GetMapping("/liveness")
    @Operation(summary = "Kubernetes liveness probe")
    public ResponseEntity<Map<String, String>> liveness() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }
    
    private boolean checkDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## Metrics & Monitoring

### Micrometer Custom Business Metrics
```java
@Component
public class BusinessMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter userRegistrationCounter;
    private final Counter orderCreatedCounter;
    
    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.userRegistrationCounter = Counter.builder("user.registrations")
            .description("Number of user registrations")
            .tag("type", "registration")
            .register(meterRegistry);
            
        this.orderCreatedCounter = Counter.builder("orders.created")
            .description("Number of orders created")
            .register(meterRegistry);
    }
    
    public void recordUserRegistration(String registrationType) {
        userRegistrationCounter.increment(Tags.of("registration_type", registrationType));
    }
    
    public void recordOrderCreated(String orderType, BigDecimal amount) {
        orderCreatedCounter.increment(Tags.of("order_type", orderType));
        meterRegistry.counter("orders.value", "order_type", orderType)
            .increment(amount.doubleValue());
    }
}
```

---

## Logback XML Schema (`logback-spring.xml`)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <!-- Structured JSON Logback Encoder -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <mdc/>
                <message/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>
    
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>3GB</totalSizeCap>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <mdc/>
                <message/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>
    
    <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="FILE"/>
        <queueSize>1024</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <includeCallerData>true</includeCallerData>
    </appender>
    
    <!-- Logging Levels per Profile -->
    <springProfile name="dev">
        <logger name="com.company.application" level="DEBUG"/>
        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    
    <springProfile name="prod">
        <logger name="com.company.application" level="WARN"/>
        <logger name="org.springframework" level="WARN"/>
        <root level="WARN">
            <appender-ref ref="ASYNC_FILE"/>
        </root>
    </springProfile>
</configuration>
```