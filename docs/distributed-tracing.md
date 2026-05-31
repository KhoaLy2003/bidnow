# Distributed Tracing & Structured Logging - Team Documentation

## Overview

The distributed tracing system gives every inbound request a single `traceId` that follows it across all microservices — through the API Gateway, HTTP Feign calls, and Kafka messages. Every log line automatically includes `traceId` and `spanId`, so you can grep a single ID to reconstruct the full execution path of a request across services.

**Stack:** Micrometer Tracing (Brave bridge) + logstash-logback-encoder

**What you get without writing any code:**
- `traceId` and `spanId` in every log line
- Trace context propagated automatically across HTTP (Feign, WebClient) and Kafka
- Structured JSON logs ready for ingestion by any log aggregation tool (Loki, ELK, Datadog, etc.)

---

## Architecture

```
Client Request
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  api-gateway (port 8080)                                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Micrometer creates root Span → assigns traceId     │    │
│  │  WebFlux observability propagates via HTTP headers  │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP  (traceparent / b3 headers)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Downstream Service  (identity / user / auction / etc.)      │
│  ┌──────────────────────┐   ┌──────────────────────────┐   │
│  │  Feign Client        │   │  Kafka Producer          │   │
│  │  auto-propagates     │   │  observation-enabled:    │   │
│  │  traceId to next     │   │  true  → carries trace   │   │
│  │  service             │   │  context in record header│   │
│  └──────────────────────┘   └──────────────────────────┘   │
│                                                              │
│  SLF4J/Logback  ←── MDC has traceId, spanId automatically   │
│  logstash-logback-encoder outputs structured JSON            │
└─────────────────────────────────────────────────────────────┘
                           │ Kafka
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Consuming Service (e.g., media-service)                     │
│  listener.observation-enabled: true → restores traceId      │
│  from Kafka record headers into MDC                          │
└─────────────────────────────────────────────────────────────┘
```

Every service writes logs like this:

```json
{
  "timestamp": "2026-05-21T04:35:12.345Z",
  "level": "INFO",
  "thread": "reactor-http-nio-3",
  "logger": "c.b.identity.service.impl.AuthServiceImpl",
  "message": ">>> AuthServiceImpl.login (email=user@example.com)",
  "traceId": "4bf92f3577b34da6",
  "spanId": "00f067aa0ba902b7",
  "service": "identity-service",
  "environment": "local"
}
```

---

## Core Components

### Common Module (`backend/common/`)

| Component | Path | Purpose |
|-----------|------|---------|
| `micrometer-tracing-bridge-brave` | `common/pom.xml` | Brave tracer wired to Micrometer; auto-instruments Feign, RestTemplate, Kafka |
| `logstash-logback-encoder` | `common/pom.xml` | Serializes log events as JSON; reads `traceId`/`spanId` from MDC |
| `logback-spring.xml` | `common/src/main/resources/` | Shared Logback config used by all servlet services |
| `@Loggable` | `annotation/Loggable.java` | Method/class annotation for AOP entry-exit logging |
| `LoggingAspect` | `aop/LoggingAspect.java` | Intercepts `@Loggable` methods; logs params, result, elapsed time |
| `@MaskPii` | `annotation/MaskPii.java` | Field annotation; redacts sensitive data before it reaches logs |

### API Gateway

| Component | Path | Purpose |
|-----------|------|---------|
| `micrometer-tracing-bridge-brave` | `api-gateway/pom.xml` | Tracing for the reactive (WebFlux) gateway |
| `logstash-logback-encoder` | `api-gateway/pom.xml` | JSON logs for the gateway |
| `logback-spring.xml` | `api-gateway/src/main/resources/` | Same structure as common's config |

### Test

| Component | Path | Purpose |
|-----------|------|---------|
| `TracingAutoConfigTest` | `common/src/test/java/.../TracingAutoConfigTest.java` | Smoke test: verifies Brave bridge wires up and populates MDC `traceId` |

---

## Configuration

### Every Service: `application.yml`

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% in dev; set to 0.1 (10%) in production
```

### Services with Kafka (producers + consumers)

```yaml
spring:
  kafka:
    template:
      observation-enabled: true   # propagates traceId on Kafka send
    listener:
      observation-enabled: true   # restores traceId from Kafka record headers
```

### API Gateway only (`application.yml`)

```yaml
spring:
  webflux:
    observability:
      enabled: true   # required for reactive gateway; servlet services don't need this
```

### `logback-spring.xml` (shared — already in `common` and `api-gateway`)

The JSON appender (`JSON_CONSOLE`) outputs all fields listed in the architecture section. The `<mdc/>` provider is what automatically includes `traceId` and `spanId` — Micrometer Tracing puts them in MDC whenever a span is active.

You do **not** need to modify `logback-spring.xml` when adding a new service — copy it verbatim from `common/src/main/resources/`.

---

## The `@Loggable` Annotation

`@Loggable` tells `LoggingAspect` to log a method's entry (with parameter values), exit (with return value and elapsed time), and any exception thrown. Because logs go through SLF4J, the `traceId` and `spanId` from MDC appear in every log line automatically.

### Placement

```java
// Option A: on a single method
@Loggable
public UserResponse getUserById(UUID id) { ... }

// Option B: on a class — logs every public method in the class
@Loggable(logResult = false)
public class AuthServiceImpl implements AuthService { ... }
```

Method-level annotation takes precedence over class-level when both are present.

### Annotation Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `level` | `Level` | `INFO` | Log level for entry/exit messages. Exceptions always log at `ERROR`. |
| `logParameters` | boolean | `true` | Include parameter names and values in the entry log. |
| `logResult` | boolean | `true` | Include the return value in the exit log. |

### Log Output

```
INFO  >>> AuthServiceImpl.login (email=user@example.com, password=***)
INFO  <<< AuthServiceImpl.login completed in 142ms | result: {id=uuid, ...}
ERROR <<< AuthServiceImpl.login threw NotFoundException in 5ms: User not found
```

### When to Use `logResult = false`

Set `logResult = false` on methods that return objects containing sensitive data (tokens, credentials, payment details) or large payloads that would pollute logs. `AuthServiceImpl` is a good example — it returns tokens so result logging is disabled at the class level.

---

## The `@MaskPii` Annotation

`@MaskPii` is placed on **fields** of request/response classes. `LoggingAspect` inspects the field annotations when serializing objects for log output.

```java
public class LoginRequest {
    private String email;

    @MaskPii(fully = true)
    private String password;           // logs as: ***

    @MaskPii(fully = false, keepLast = 4)
    private String cardNumber;         // logs as: ************1234
}
```

| Option | Default | Behaviour |
|--------|---------|-----------|
| `fully = true` | `true` | Replaces the entire value with `***` |
| `fully = false, keepLast = N` | — | Masks all but the last N characters |

Simple types (String, Number, Boolean, enums) are logged as-is. `@MaskPii` only applies to fields on complex objects that `LoggingAspect` inspects via reflection.

---

## How to Enable Tracing in a New Service

### Step 1: Add dependencies to the service's `pom.xml`

The `common` module already declares `micrometer-tracing-bridge-brave` and `logstash-logback-encoder`. If your service depends on `common`, both are already on the classpath — no extra `pom.xml` changes needed.

If the service does **not** depend on `common` (unusual), add these to its `pom.xml`:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>${logstash-logback-encoder.version}</version>
</dependency>
```

### Step 2: Copy `logback-spring.xml`

Copy `backend/common/src/main/resources/logback-spring.xml` to `src/main/resources/` of the new service. No changes needed — it reads `spring.application.name` automatically.

### Step 3: Add sampling config to `application.yml`

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```

### Step 4: Enable Kafka observation (if the service uses Kafka)

```yaml
spring:
  kafka:
    template:
      observation-enabled: true    # if the service produces messages
    listener:
      observation-enabled: true    # if the service consumes messages
```

### Step 5: Apply `@Loggable` to service classes

```java
@Service
@Loggable          // logs all methods at INFO level
public class AuctionServiceImpl implements AuctionService { ... }
```

Or annotate individual methods for finer control:

```java
@Loggable(level = Loggable.Level.DEBUG, logResult = false)
public void processPayment(PaymentRequest request) { ... }
```

---

## Reading and Correlating Logs

### Following a request across services

When debugging an issue, find the `traceId` in any log line for the request. Then search all service logs for that ID:

```bash
# docker compose logs or your log tool — filter by traceId value
docker compose logs | grep "4bf92f3577b34da6"
```

In a structured log aggregation tool (Loki, ELK, Datadog), filter by `traceId = "4bf92f3577b34da6"` to see the full execution path ordered by timestamp.

### Local JSON log output

Logs are printed to stdout as JSON (one object per line). For human-readable output locally, pipe through `jq`:

```bash
docker compose logs -f identity-service | jq '{ts: .timestamp, level: .level, msg: .message, trace: .traceId}'
```

### Switching to plain text locally (optional)

The `logback-spring.xml` includes a `PLAIN_CONSOLE` appender that uses the classic pattern. To use it for a service during local development, change the `<root>` appender-ref in that service's `logback-spring.xml`:

```xml
<root level="INFO">
    <appender-ref ref="PLAIN_CONSOLE"/>  <!-- swap from JSON_CONSOLE -->
</root>
```

Do not commit this change — JSON logging should remain active in all environments.

---

## Sampling in Production

The default sampling probability is `1.0` (100% of requests are traced). In production, this is expensive. Reduce it:

```yaml
# production application.yml
management:
  tracing:
    sampling:
      probability: 0.1   # trace 10% of requests
```

Errors are still logged regardless of sampling. If you need 100% tracing for a debug session in production, temporarily set `probability: 1.0` and redeploy; revert afterwards.

---

## Best Practices

1. **Apply `@Loggable` at the service implementation class level** — annotating the interface has no effect because AOP proxies the concrete class.
2. **Set `logResult = false` on methods returning tokens, passwords, or large collections** to avoid bloating logs.
3. **Mark sensitive request fields with `@MaskPii`** before a new DTO is used in a `@Loggable` context.
4. **Never log credentials, JWTs, or PII in manual `log.info()` calls** — these bypass `@MaskPii` inspection.
5. **Do not wrap `@Loggable` methods inside another `@Loggable` method unnecessarily** — the parent log already captures total elapsed time.
6. **Keep `logback-spring.xml` identical across services** — customizations belong in `application.yml` log levels, not in the XML config.
7. **Reduce `sampling.probability` before going to production** — 100% tracing is fine for development, costly in high-traffic production environments.
