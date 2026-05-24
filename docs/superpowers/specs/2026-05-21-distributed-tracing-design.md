# Distributed Tracing & Log Correlation — Design Spec

**Date:** 2026-05-21  
**Author:** khoalnd  
**Status:** Approved

---

## Problem

BidNow is a Spring Boot 3.2 microservices system with 7 services communicating over HTTP (Feign) and Kafka. When a bug or latency issue spans multiple services (e.g., bid placement → wallet escrow → auction update), there is no way to correlate log lines across services. Debugging requires manually scanning logs in each service and guessing the timeline.

---

## Goal

Every request that enters the system gets a unique `traceId`. That ID travels through every HTTP hop and every Kafka message. Every log line from every service includes `traceId` and `spanId` as structured JSON fields. A developer can filter their log aggregator on a single `traceId` to reconstruct the full request path in timestamp order.

The implementation must also be designed so that adding a Zipkin/Jaeger visual trace explorer later requires only one Maven dependency and one config property — no instrumentation rework.

---

## Chosen Approach: Micrometer Tracing (Brave bridge)

Spring Boot 3.2 replaced Spring Cloud Sleuth with **Micrometer Tracing**. Adding `micrometer-tracing-bridge-brave` to the classpath is sufficient to activate:

- Automatic trace context generation on every inbound HTTP request
- B3 multi-header propagation on outbound HTTP calls (including Feign)
- Kafka record header propagation when `observation-enabled` is set
- MDC population (`traceId`, `spanId`) before each log statement

This approach requires **zero changes to existing Java classes**. All instrumentation is handled by Spring Boot auto-configuration.

---

## Architecture

```
Client
  │
  ▼
API Gateway (8080) ─── generates traceId, root spanId
  │   injects B3 headers: X-B3-TraceId, X-B3-SpanId, X-B3-Sampled
  ▼
identity-service / user-service / auction-service / bidding-service / wallet-service / media-service
  │   each creates a child spanId under the same traceId
  │   Feign calls propagate B3 headers automatically
  │
  ├── Kafka producer ── writes traceId/spanId to Kafka record headers
  │
  └── Kafka consumer ── reads record headers, continues the same trace
```

All services log to stdout in JSON format. Log lines include `traceId` and `spanId` automatically via MDC.

### B3 Header Format

| Header | Description |
|--------|-------------|
| `X-B3-TraceId` | 32-char hex, same for entire request chain |
| `X-B3-SpanId` | 16-char hex, unique per service hop |
| `X-B3-ParentSpanId` | spanId of the caller |
| `X-B3-Sampled` | `1` to sample, `0` to drop |

---

## Changes Required

### 1. Root `backend/pom.xml`

**No changes required.** The root POM already declares `spring-boot-starter-parent:3.2.4`, whose BOM manages `micrometer-tracing` (1.2.4) transitively. It also already includes `spring-boot-starter-actuator` as a global dependency — the actuator is required for Micrometer to activate. No new entries are needed here.

### 2. `backend/common/pom.xml`

Add two dependencies (inherited by all services transitively):

```xml
<!-- Brave tracer bridge: activates HTTP + Kafka + Feign auto-instrumentation -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- JSON structured logging with MDC fields (traceId, spanId) -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### 3. `backend/api-gateway/pom.xml`

The gateway excludes `spring-boot-starter-web` (it is WebFlux). Add explicitly:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

Spring Boot 3.2 auto-configures reactive context propagation for WebFlux when the bridge is on the classpath. Specifically, `reactor-core` 3.5+ combined with `micrometer-context-propagation` (pulled in by the bridge) enables `Hooks.enableAutomaticContextPropagation()`, which bridges reactive context to ThreadLocal MDC — so `logstash-logback-encoder` will include `traceId`/`spanId` in gateway log lines despite the reactive execution model.

### 4. Shared Logback Configuration

Create `backend/common/src/main/resources/logback-spring.xml`. All services pick this up automatically from the classpath (no existing service has a custom logback config).

```xml
<configuration>
    <springProperty scope="context" name="serviceName" source="spring.application.name"/>
    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${serviceName}"}</customFields>
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <message>message</message>
                <logger>logger</logger>
                <level>level</level>
            </fieldNames>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="JSON_STDOUT"/>
    </root>
</configuration>
```

`LogstashEncoder` automatically reads `traceId` and `spanId` from MDC (populated by Micrometer) and includes them in every JSON log object.

### 5. Per-Service `application.yml` additions

Add to every service's `application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # sample all requests; reduce to 0.1 in production
```

For services that produce or consume Kafka messages (auction-service, bidding-service, wallet-service, media-service):

```yaml
spring:
  kafka:
    template:
      observation-enabled: true
    listener:
      observation-enabled: true
```

---

## Log Shape

Every log line is a single JSON object:

```json
{
  "timestamp": "2026-05-21T10:23:41.123Z",
  "level": "INFO",
  "service": "bidding-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "logger": "com.bidnow.bidding.service.BidService",
  "message": "Bid placed: auctionId=abc-123 amount=500 userId=u-42"
}
```

To correlate a full request chain, filter by `traceId` in ELK/CloudWatch and sort by `timestamp`.

---

## Kafka Trace Flow

Kafka trace context travels in **Kafka record headers**, not in the message payload. Event DTOs (`BidPlacedEvent`, `AuctionCreatedEvent`, etc.) are unchanged.

When `spring.kafka.template.observation-enabled=true` is set, `KafkaTemplate` automatically writes B3 headers to each record before producing. When `spring.kafka.listener.observation-enabled=true` is set, the listener container reads those headers and resumes the trace context before invoking the consumer method.

---

## Future Zipkin/Jaeger Path

To enable visual trace exploration:

**Step 1 — Add to `backend/common/pom.xml`:**
```xml
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

**Step 2 — Add to each service's `application.yml`:**
```yaml
management:
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

**Step 3 — Add to `docker-compose.yml`:**
```yaml
zipkin:
  image: openzipkin/zipkin:latest
  ports:
    - "9411:9411"
```

No instrumentation changes are required. Jaeger also accepts B3 format via its Zipkin-compatible endpoint.

---

## What Is NOT in Scope

- Custom `@NewSpan` business method annotations (auto-instrumentation only)
- Zipkin/Jaeger deployment (future work)
- Frontend trace propagation
- Sampling strategy tuning for production (start at 1.0, tune later)
- PII scrubbing in structured logs (already handled by `@MaskPii` annotation in common)

---

## Services Affected

| Service | HTTP tracing | Kafka tracing | logback-spring.xml |
|---------|-------------|--------------|-------------------|
| api-gateway | auto (WebFlux) | n/a | yes (own dep) |
| identity-service | auto (WebMVC) | n/a | via common |
| user-service | auto (WebMVC) | n/a | via common |
| auction-service | auto (WebMVC) | producer | via common |
| bidding-service | auto (WebMVC) | producer | via common |
| wallet-service | auto (WebMVC) | producer + consumer | via common |
| media-service | auto (WebMVC) | consumer | via common |
