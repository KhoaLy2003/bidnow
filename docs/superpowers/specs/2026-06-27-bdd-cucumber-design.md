# BDD Testing with Cucumber — Design Spec

**Date:** 2026-06-27
**Scope:** identity-service, user-service (auction-service, bidding-service, wallet-service to follow the same pattern later)

---

## 1. Goals

- Replace or complement existing Mockito unit tests with Cucumber BDD integration tests that describe business behaviour in plain Gherkin
- Tests run per-service in isolation — no other services need to be running
- Use Testcontainers for real infrastructure (PostgreSQL, Kafka) with automatic cleanup after each test run
- Use WireMock to simulate downstream Feign HTTP calls
- Cover happy paths and key error cases for each feature

---

## 2. Approach

**Shared `bdd-support` Maven module** provides all infrastructure utilities. Each service adds it as a single `test`-scoped dependency and contributes only its own feature files and step definitions.

This avoids duplicating ~150 lines of Testcontainers/WireMock boilerplate across every service and gives a single place to upgrade library versions.

---

## 3. Module Structure

```
backend/
├── bdd-support/                                      ← NEW Maven module
│   ├── pom.xml
│   └── src/main/java/com/bidnow/bdd/
│       ├── container/
│       │   ├── PostgresContainerSupport.java
│       │   └── KafkaContainerSupport.java
│       ├── wiremock/
│       │   └── WireMockSupport.java
│       └── client/
│           └── BddRestClient.java
│
├── identity-service/src/test/
│   ├── java/com/bidnow/identity/bdd/
│   │   ├── CucumberTest.java
│   │   ├── config/CucumberSpringConfig.java
│   │   └── steps/
│   │       ├── AuthSteps.java
│   │       └── CommonSteps.java
│   └── resources/
│       ├── features/
│       │   ├── register.feature
│       │   ├── login.feature
│       │   └── otp.feature
│       └── application-bdd.yml
│
└── user-service/src/test/
    ├── java/com/bidnow/user/bdd/
    │   ├── CucumberTest.java
    │   ├── config/CucumberSpringConfig.java
    │   └── steps/
    │       ├── UserProfileSteps.java
    │       └── CommonSteps.java
    └── resources/
        ├── features/
        │   ├── get-profile.feature
        │   └── update-profile.feature
        └── application-bdd.yml
```

---

## 4. Infrastructure Layer (`bdd-support`)

### 4.1 Testcontainers — Lifecycle

Both containers use the static singleton pattern. A single container instance is shared across all Cucumber scenarios in one test run. **No `.withReuse(true)`** is used — Testcontainers' built-in Ryuk reaper automatically stops and removes all containers when the JVM exits. Containers are never kept alive between test runs.

```java
// PostgresContainerSupport.java
public class PostgresContainerSupport {
    public static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static { POSTGRES.start(); }

    public static Map<String, String> properties() {
        return Map.of(
            "spring.datasource.url",      POSTGRES.getJdbcUrl(),
            "spring.datasource.username", POSTGRES.getUsername(),
            "spring.datasource.password", POSTGRES.getPassword()
        );
    }
}

// KafkaContainerSupport.java
public class KafkaContainerSupport {
    public static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static { KAFKA.start(); }

    public static Map<String, String> properties() {
        return Map.of("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
    }
}
```

**Lifecycle:**
1. Test run starts → static block fires → container created
2. All Cucumber scenarios execute against the container
3. Test run ends → JVM exits → Ryuk stops and removes all containers

### 4.2 WireMock

WireMock starts once as an in-process server (dynamic port). Stubs are reset before each scenario via a `@Before` hook in step definitions to prevent bleed between scenarios.

```java
public class WireMockSupport {
    public static final WireMockServer SERVER =
        new WireMockServer(wireMockConfig().dynamicPort());

    static { SERVER.start(); }

    public static void reset()    { SERVER.resetAll(); }
    public static String baseUrl() { return "http://localhost:" + SERVER.port(); }
}
```

### 4.3 `BddRestClient`

Spring `@Component` injected into step definition classes. Wraps RestAssured with the service's random port:

```java
@Component
public class BddRestClient {
    @LocalServerPort int port;

    public RequestSpecification given() {
        return RestAssured.given()
            .baseUri("http://localhost").port(port)
            .contentType(ContentType.JSON);
    }
}
```

### 4.4 `ScenarioContext`

Cucumber-Spring `@ScenarioScoped` bean. Fresh instance per scenario — no state bleed between scenarios. Holds the last HTTP response and current auth token for chaining steps.

```java
@Component
@ScenarioScoped
public class ScenarioContext {
    public Response lastResponse;
    public String authToken;
}
```

---

## 5. Cucumber Wiring (per service)

### 5.1 Runner

```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameters({
    @ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/report.html"),
    @ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.bidnow.identity.bdd")       // adjust per service
})
public class CucumberTest {}
```

### 5.2 Spring Context Config

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("bdd")
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach(registry::add);
        KafkaContainerSupport.properties().forEach(registry::add);
        registry.add("wiremock.url", WireMockSupport::baseUrl);
    }
}
```

`@CucumberContextConfiguration` must appear exactly once per service test classpath.

### 5.3 `application-bdd.yml` (per service)

Overrides Feign client base URLs to point at WireMock, and enables Liquibase to run migrations against the Testcontainers database:

```yaml
spring:
  profiles:
    active: bdd
  liquibase:
    enabled: true

feign:
  client:
    config:
      user-service:              # adjust per Feign client name
        url: ${wiremock.url}
```

### 5.4 Step Definition Pattern

```java
@Component
@RequiredArgsConstructor
public class AuthSteps {
    private final BddRestClient client;
    private final ScenarioContext ctx;

    @Before
    public void resetWireMock() { WireMockSupport.reset(); }

    @When("user registers with email {string} and password {string}")
    public void register(String email, String password) {
        ctx.lastResponse = client.given()
            .body(Map.of("email", email, "password", password))
            .post("/api/v1/auth/register");
    }

    @Then("the response status should be {int}")
    public void assertStatus(int status) {
        ctx.lastResponse.then().statusCode(status);
    }
}
```

**Rules:**
- Every step class is a Spring `@Component` — no manual instantiation
- `@Before` hook for WireMock reset lives in each service's primary step class
- `ScenarioContext` carries state between `Given/When/Then` steps within one scenario
- user-service tests inject `X-User-Id` and `X-User-Roles` headers directly (the gateway sets these in production; WireMock is not needed for auth in user-service)

---

## 6. Maven Dependencies

### `bdd-support/pom.xml` (main dependencies — not test-scoped, so consumers can use them at test scope)

| Dependency | Purpose |
|---|---|
| `io.cucumber:cucumber-java` | Cucumber Java bindings |
| `io.cucumber:cucumber-spring` | Spring context integration |
| `io.cucumber:cucumber-junit-platform-engine` | JUnit Platform engine |
| `org.junit.platform:junit-platform-suite` | `@Suite` runner |
| `org.testcontainers:postgresql` | PostgreSQL container |
| `org.testcontainers:kafka` | Kafka container |
| `org.wiremock:wiremock` | WireMock 3.x |
| `io.rest-assured:rest-assured` | HTTP client for step definitions |

### Per service `pom.xml` addition

```xml
<dependency>
    <groupId>com.bidnow</groupId>
    <artifactId>bdd-support</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

---

## 7. Feature File Design

### identity-service

#### `register.feature`
```gherkin
Feature: User Registration

  Scenario: Successful registration
    When user registers with email "alice@example.com" and password "P@ssw0rd"
    Then the response status should be 201
    And the response should contain field "message"

  Scenario: Duplicate email is rejected
    Given a user already exists with email "alice@example.com"
    When user registers with email "alice@example.com" and password "P@ssw0rd"
    Then the response status should be 409

  Scenario: Invalid email format is rejected
    When user registers with email "not-an-email" and password "P@ssw0rd"
    Then the response status should be 400

  Scenario: Weak password is rejected
    When user registers with email "bob@example.com" and password "123"
    Then the response status should be 400
```

#### `login.feature`
```gherkin
Feature: User Login

  Scenario: Successful login returns token
    Given a verified user exists with email "alice@example.com" and password "P@ssw0rd"
    When user logs in with email "alice@example.com" and password "P@ssw0rd"
    Then the response status should be 200
    And the response should contain field "accessToken"

  Scenario: Wrong password is rejected
    Given a verified user exists with email "alice@example.com" and password "P@ssw0rd"
    When user logs in with email "alice@example.com" and password "WrongPass"
    Then the response status should be 401

  Scenario: Unverified account cannot login
    Given an unverified user exists with email "bob@example.com"
    When user logs in with email "bob@example.com" and password "P@ssw0rd"
    Then the response status should be 403
```

#### `otp.feature`
```gherkin
Feature: OTP Verification

  Scenario: Valid OTP verifies account
    Given a user registered with email "alice@example.com"
    When user submits the correct OTP for email "alice@example.com"
    Then the response status should be 200

  Scenario: Expired OTP is rejected
    Given a user registered with email "alice@example.com"
    And the OTP for "alice@example.com" has expired
    When user submits the correct OTP for email "alice@example.com"
    Then the response status should be 400

  Scenario: Invalid OTP is rejected
    Given a user registered with email "alice@example.com"
    When user submits an incorrect OTP for email "alice@example.com"
    Then the response status should be 400
```

> **Note:** OTP values are generated server-side. Step definitions for OTP scenarios read the current OTP directly from the Testcontainers PostgreSQL database (via JDBC) rather than using hardcoded literal values. The "expired OTP" step manipulates the OTP's `expiresAt` column in the database to simulate expiry.

### user-service

#### `get-profile.feature`
```gherkin
Feature: Get User Profile

  Scenario: Authenticated user retrieves their profile
    Given the user-service receives a valid user id "user-123"
    When user requests profile for id "user-123"
    Then the response status should be 200
    And the response should contain field "email"

  Scenario: Non-existent profile returns 404
    Given no profile exists for user id "user-999"
    When user requests profile for id "user-999"
    Then the response status should be 404

  Scenario: Unauthenticated request is rejected
    When an unauthenticated user requests profile for id "user-123"
    Then the response status should be 401
```

#### `update-profile.feature`
```gherkin
Feature: Update User Profile

  Scenario: User updates display name successfully
    Given the user-service receives a valid user id "user-123"
    When user updates profile with display name "Alice"
    Then the response status should be 200
    And the response should contain field "displayName" with value "Alice"

  Scenario: Empty display name is rejected
    Given the user-service receives a valid user id "user-123"
    When user updates profile with display name ""
    Then the response status should be 400
```

---

## 8. Expansion Pattern

When adding BDD tests for auction-service, bidding-service, and wallet-service, the steps are:

1. Add `bdd-support` as a `test`-scoped dependency in the service `pom.xml`
2. Create `CucumberTest.java` + `CucumberSpringConfig.java` in `src/test/java/.../bdd/`
3. Add `application-bdd.yml` in `src/test/resources/` with any Feign URL overrides
4. Write feature files in `src/test/resources/features/`
5. Write step definitions in `src/test/java/.../bdd/steps/`

No changes to `bdd-support` are needed unless new shared infrastructure is required (e.g., a Redis container for auction-service caching).

---

## 9. Running Tests

```bash
# Run BDD tests for a specific service
mvn test -pl identity-service -Dtest=CucumberTest

# Run BDD tests for all services
mvn test -Dtest=CucumberTest

# Run all tests (unit + BDD) for a service
mvn test -pl identity-service
```

HTML reports are written to `target/cucumber-reports/report.html` per service after each run.
