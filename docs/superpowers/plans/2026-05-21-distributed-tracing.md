# Distributed Tracing & Log Correlation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Micrometer Tracing (Brave bridge) to all BidNow microservices so every log line carries a `traceId` and `spanId`, enabling cross-service request correlation with zero code changes to existing Java classes.

**Architecture:** `micrometer-tracing-bridge-brave` is added to the `common` module (transitively inherited by all services) and explicitly to `api-gateway` (WebFlux). A single `logback-spring.xml` in `common/src/main/resources/` provides JSON-structured logging with automatic MDC injection for all services. Each service's `application.yml` activates sampling and, where Kafka is in use, enables Kafka observation.

**Tech Stack:** Spring Boot 3.2.4, Micrometer Tracing 1.2.x (managed by Spring Boot BOM), Brave bridge, `logstash-logback-encoder:7.4`, Spring Kafka observation support.

---

## File Map

| Action | File | Purpose |
|--------|------|---------|
| Modify | `backend/common/pom.xml` | Add `micrometer-tracing-bridge-brave` + `logstash-logback-encoder` |
| Modify | `backend/api-gateway/pom.xml` | Same two deps (gateway doesn't inherit common) |
| **Create** | `backend/common/src/main/resources/logback-spring.xml` | Shared JSON logging config with MDC traceId/spanId |
| **Create** | `backend/common/src/test/java/com/bidnow/common/TracingAutoConfigTest.java` | Smoke test: verifies Tracer bean + span traceId |
| Modify | `backend/api-gateway/src/main/resources/application.yml` | Add `management.tracing.sampling.probability: 1.0` |
| Modify | `backend/auction-service/src/main/resources/application.yml` | Same |
| Modify | `backend/bidding-service/src/main/resources/application.yml` | Same |
| Modify | `backend/wallet-service/src/main/resources/application.yml` | Same |
| Modify | `backend/identity-service/src/main/resources/application.yml` | Tracing sampling + Kafka template observation |
| Modify | `backend/user-service/src/main/resources/application.yml` | Tracing sampling + Kafka template + listener observation |
| Modify | `backend/media-service/src/main/resources/application.yml` | Tracing sampling + Kafka template + listener observation |

---

## Task 1: Write the failing smoke test

**Files:**
- Create: `backend/common/src/test/java/com/bidnow/common/TracingAutoConfigTest.java`

Before the dependency exists, this test will fail to compile because `io.micrometer.tracing.Tracer` is not on the classpath.

- [ ] **Step 1.1: Create the test file**

```java
package com.bidnow.common;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TracingAutoConfigTest.MinimalConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
class TracingAutoConfigTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            LiquibaseAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    static class MinimalConfig {}

    @Autowired
    private Tracer tracer;

    @Test
    void tracerBeanIsAvailable() {
        assertThat(tracer).isNotNull();
    }

    @Test
    void spanHasTraceId() {
        Span span = tracer.nextSpan().name("smoke-test").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            assertThat(tracer.currentSpan().context().traceId())
                    .isNotBlank()
                    .hasSize(32);
        } finally {
            span.end();
        }
    }
}
```

- [ ] **Step 1.2: Run the test to confirm it fails**

Run from `backend/`:
```
mvn test -pl common -Dtest=TracingAutoConfigTest
```

Expected: **BUILD FAILURE** — `cannot find symbol: class Tracer` (the dependency is missing). This confirms the test is correctly anchored to the dependency.

---

## Task 2: Add dependencies to `common`

**Files:**
- Modify: `backend/common/pom.xml`

- [ ] **Step 2.1: Add tracing + logstash deps to common/pom.xml**

In `backend/common/pom.xml`, add these two entries inside the existing `<dependencies>` block, after the last `<dependency>`:

```xml
        <!-- Distributed tracing: auto-instruments HTTP, Feign, and Kafka -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>

        <!-- JSON structured logging; auto-embeds traceId/spanId from MDC -->
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>7.4</version>
        </dependency>
```

No version needed for `micrometer-tracing-bridge-brave` — Spring Boot 3.2.4's BOM manages it.

- [ ] **Step 2.2: Run the test to verify it now passes**

```
mvn test -pl common -Dtest=TracingAutoConfigTest
```

Expected output:
```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 2.3: Commit**

```
git add backend/common/pom.xml backend/common/src/test/java/com/bidnow/common/TracingAutoConfigTest.java
git commit -m "feat(tracing): add Micrometer Tracing + logstash-encoder to common"
```

---

## Task 3: Add dependencies to `api-gateway`

**Files:**
- Modify: `backend/api-gateway/pom.xml`

The gateway uses WebFlux and does not inherit `common`, so the deps must be declared here explicitly.

- [ ] **Step 3.1: Add deps to api-gateway/pom.xml**

In `backend/api-gateway/pom.xml`, add inside the `<dependencies>` block after the last `<dependency>` (the `jjwt-jackson` entry):

```xml
        <!-- Distributed tracing for WebFlux reactive gateway -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>

        <!-- JSON structured logging -->
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>7.4</version>
        </dependency>
```

- [ ] **Step 3.2: Build api-gateway to verify it compiles**

```
mvn clean install -pl common,api-gateway -DskipTests
```

Expected: `BUILD SUCCESS` for both modules.

- [ ] **Step 3.3: Commit**

```
git add backend/api-gateway/pom.xml
git commit -m "feat(tracing): add tracing + logstash deps to api-gateway"
```

---

## Task 4: Create shared Logback JSON configuration

**Files:**
- Create: `backend/common/src/main/resources/logback-spring.xml`

All services pick this file up automatically from the classpath because `common` is a transitive dependency of every service. No existing service has its own `logback-spring.xml`, so there is no conflict. The `LogstashEncoder` reads `traceId` and `spanId` from Logback's MDC (populated by Micrometer Tracing before each log statement) and includes them in every JSON log object.

- [ ] **Step 4.1: Create logback-spring.xml**

Create `backend/common/src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="serviceName" source="spring.application.name" defaultValue="unknown"/>

    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${serviceName}"}</customFields>
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <message>message</message>
                <logger>logger</logger>
                <level>level</level>
                <levelValue>[ignore]</levelValue>
                <version>[ignore]</version>
            </fieldNames>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_STDOUT"/>
    </root>
</configuration>
```

Note: `media-service/application.yml` has `logging.level.*` entries. Spring Boot applies `logging.level.*` properties as overrides even when a custom `logback-spring.xml` is present, so those levels continue to work.

- [ ] **Step 4.2: Build common to verify no classpath conflict**

```
mvn clean install -pl common -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4.3: Run smoke test again to confirm it still passes**

```
mvn test -pl common -Dtest=TracingAutoConfigTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 4.4: Commit**

```
git add backend/common/src/main/resources/logback-spring.xml
git commit -m "feat(tracing): add shared logback JSON config with traceId/spanId"
```

---

## Task 5: Add tracing sampling config to HTTP-only services

**Files:**
- Modify: `backend/api-gateway/src/main/resources/application.yml:68-76`
- Modify: `backend/auction-service/src/main/resources/application.yml:29-34`
- Modify: `backend/bidding-service/src/main/resources/application.yml:29-34`
- Modify: `backend/wallet-service/src/main/resources/application.yml:29-34`

These services do not yet have Kafka code wired, so only `management.tracing` is needed.

- [ ] **Step 5.1: Update api-gateway/application.yml**

Replace the existing `management:` block (lines 68–76):

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 5.2: Update auction-service/application.yml**

Replace the existing `management:` block (lines 29–34):

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 5.3: Update bidding-service/application.yml**

Replace the existing `management:` block (lines 29–34):

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 5.4: Update wallet-service/application.yml**

Replace the existing `management:` block (lines 29–34):

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 5.5: Build all four services**

```
mvn clean install -pl common,api-gateway,auction-service,bidding-service,wallet-service -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5.6: Commit**

```
git add backend/api-gateway/src/main/resources/application.yml
git add backend/auction-service/src/main/resources/application.yml
git add backend/bidding-service/src/main/resources/application.yml
git add backend/wallet-service/src/main/resources/application.yml
git commit -m "feat(tracing): enable tracing sampling in HTTP-only services"
```

---

## Task 6: Add tracing + Kafka observation config to Kafka services

**Files:**
- Modify: `backend/identity-service/src/main/resources/application.yml:39-47`
- Modify: `backend/user-service/src/main/resources/application.yml:46-53`
- Modify: `backend/media-service/src/main/resources/application.yml:75-85`

`identity-service` has a Kafka producer only. `user-service` and `media-service` have both producer and consumer.

- [ ] **Step 6.1: Update identity-service/application.yml**

Replace the existing `management:` block (lines 39–47) and add `template.observation-enabled` to the existing `spring.kafka` block.

The full updated `management:` block:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

The updated `spring.kafka` block (add `template.observation-enabled: true`):

```yaml
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    template:
      observation-enabled: true
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

- [ ] **Step 6.2: Update user-service/application.yml**

The full updated `management:` block:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

The updated `spring.kafka` block (add `template` and `listener` observation):

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    template:
      observation-enabled: true
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: user-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
        reconnect.backoff.ms: 5000
        reconnect.backoff.max.ms: 30000
    listener:
      observation-enabled: true
```

- [ ] **Step 6.3: Update media-service/application.yml**

The full updated `management:` block:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      kafka:
        enabled: true
      show-details: always
```

The updated `spring.kafka` block:

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    template:
      observation-enabled: true
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: media-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
        reconnect.backoff.ms: 5000
        reconnect.backoff.max.ms: 30000
    listener:
      observation-enabled: true
```

- [ ] **Step 6.4: Build all three Kafka services**

```
mvn clean install -pl common,identity-service,user-service,media-service -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6.5: Commit**

```
git add backend/identity-service/src/main/resources/application.yml
git add backend/user-service/src/main/resources/application.yml
git add backend/media-service/src/main/resources/application.yml
git commit -m "feat(tracing): enable tracing + Kafka observation in Kafka services"
```

---

## Task 7: End-to-end smoke verification

This task verifies the full system produces correlated JSON logs with `traceId` propagated across service boundaries.

**Prerequisites:** Docker, all env vars set (see `backend/CLAUDE.md`).

- [ ] **Step 7.1: Start infrastructure**

```
docker compose up -d
```

Wait ~10 seconds for Kafka and DB to be healthy.

- [ ] **Step 7.2: Start discovery-service and identity-service**

Terminal 1:
```
mvn spring-boot:run -pl discovery-service
```

Wait until you see `Started DiscoveryServiceApplication`.

Terminal 2:
```
mvn spring-boot:run -pl identity-service
```

Wait until you see `Started IdentityApplication`.

- [ ] **Step 7.3: Make a test request**

```
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"wrong"}' | jq .
```

The request will return a 401 — that's expected. What matters is that both the **api-gateway** and **identity-service** terminal windows show JSON logs.

- [ ] **Step 7.4: Verify log format**

In the api-gateway terminal, you should see a line like:
```json
{"timestamp":"2026-05-21T10:23:41.123Z","level":"INFO","service":"api-gateway","traceId":"4bf92f3577b34da6a3ce929d0e0e4736","spanId":"00f067aa0ba902b7","logger":"...","message":"..."}
```

In the identity-service terminal, find a log line from the same request. The `traceId` value must be identical to the one in the api-gateway log. The `spanId` will differ (each service hop gets its own span).

Copy the `traceId` from the api-gateway log and grep both terminal outputs for it — every line should share the same value.

- [ ] **Step 7.5: Verify Kafka trace propagation (optional — requires full stack)**

Start the full stack and place a bid. In the bidding-service log and the media-service log (which consumes Kafka events), verify the `traceId` is the same across both, showing the trace was carried through the Kafka message headers.

---

## Notes for Future Zipkin Integration

When ready to add visual trace exploration:

1. Add to `backend/common/pom.xml`:
```xml
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

2. Add to every service `application.yml` under `management:`:
```yaml
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

3. Add to `docker-compose.yml`:
```yaml
  zipkin:
    image: openzipkin/zipkin:latest
    ports:
      - "9411:9411"
```

No other changes required. The B3 headers already being propagated are exactly what Zipkin expects.
