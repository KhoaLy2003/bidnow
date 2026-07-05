# BDD Cucumber Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Cucumber BDD integration tests to `identity-service` and `user-service`, backed by Testcontainers (PostgreSQL + Kafka) and WireMock, with shared infrastructure in a new `bdd-support` module.

**Architecture:** A new `bdd-support` Maven module (regular artifact, not test-jar) provides `PostgresContainerSupport`, `KafkaContainerSupport`, `WireMockSupport`, `BddRestClient`, and `ScenarioContext`. Each service adds `bdd-support` as a `test`-scope dependency and supplies its own `CucumberTest` runner, `CucumberSpringConfig`, `application-bdd.yml`, feature files, and step definitions. Containers are started on first use and destroyed by Testcontainers' Ryuk reaper when the JVM exits — no `withReuse(true)`.

**Tech Stack:** Cucumber 7.18.0, JUnit Platform Suite, Testcontainers (PostgreSQL 16, Kafka/Confluent 7.6.0), WireMock 3.5.4, RestAssured 5.4.0, Spring Boot 3.2.4, Java 17, Maven.

## Global Constraints

- Cucumber version: `7.18.0` for all `io.cucumber:*` artifacts
- WireMock: `org.wiremock:wiremock:3.5.4`
- No `.withReuse(true)` on any Testcontainers container — Ryuk handles cleanup
- No git commands at any step
- `bdd-support` is a regular Maven artifact (not test-jar); services depend on it at `<scope>test</scope>`
- All BDD test classes live under `src/test/java/com/bidnow/{service}/bdd/`
- Feature files live under `src/test/resources/features/`
- Run BDD tests: `mvn test -pl {service-name} -Dtest=CucumberTest`
- Run all tests: `mvn test -pl {service-name}`

---

## File Map

### New files — `bdd-support` module
| File | Responsibility |
|---|---|
| `backend/bdd-support/pom.xml` | Module POM: Cucumber, Testcontainers, WireMock, RestAssured deps |
| `backend/bdd-support/src/main/java/com/bidnow/bdd/container/PostgresContainerSupport.java` | Singleton PostgreSQL Testcontainer + property map |
| `backend/bdd-support/src/main/java/com/bidnow/bdd/container/KafkaContainerSupport.java` | Singleton Kafka Testcontainer + property map |
| `backend/bdd-support/src/main/java/com/bidnow/bdd/wiremock/WireMockSupport.java` | WireMock in-process server: start, reset, baseUrl |
| `backend/bdd-support/src/main/java/com/bidnow/bdd/client/BddRestClient.java` | RestAssured wrapper; reads `local.server.port` |
| `backend/bdd-support/src/main/java/com/bidnow/bdd/context/ScenarioContext.java` | `@ScenarioScope` bean holding last HTTP response, auth token, current email |

### Modified files
| File | Change |
|---|---|
| `backend/pom.xml` | Add `<module>bdd-support</module>` |
| `backend/identity-service/pom.xml` | Add `bdd-support` test dependency |
| `backend/user-service/pom.xml` | Add `bdd-support` test dependency |

### New files — `identity-service` BDD
| File | Responsibility |
|---|---|
| `backend/identity-service/src/test/java/com/bidnow/identity/bdd/CucumberTest.java` | JUnit Platform Suite runner |
| `backend/identity-service/src/test/java/com/bidnow/identity/bdd/config/CucumberSpringConfig.java` | `@CucumberContextConfiguration` + `@DynamicPropertySource` |
| `backend/identity-service/src/test/resources/application-bdd.yml` | Test overrides (Eureka off, JWT secret, Feign → WireMock) |
| `backend/identity-service/src/test/resources/features/register.feature` | Registration scenarios |
| `backend/identity-service/src/test/resources/features/login.feature` | Login scenarios |
| `backend/identity-service/src/test/resources/features/otp.feature` | OTP verification scenarios |
| `backend/identity-service/src/test/java/com/bidnow/identity/bdd/steps/AuthSteps.java` | All auth step definitions + `@Before` WireMock reset |
| `backend/identity-service/src/test/java/com/bidnow/identity/bdd/steps/CommonSteps.java` | Shared assertion steps (status code, field presence) |

### New files — `user-service` BDD
| File | Responsibility |
|---|---|
| `backend/user-service/src/test/java/com/bidnow/user/bdd/CucumberTest.java` | JUnit Platform Suite runner |
| `backend/user-service/src/test/java/com/bidnow/user/bdd/config/CucumberSpringConfig.java` | `@CucumberContextConfiguration` + `@DynamicPropertySource` |
| `backend/user-service/src/test/resources/application-bdd.yml` | Test overrides (Eureka off) |
| `backend/user-service/src/test/resources/features/get-profile.feature` | Get profile scenarios |
| `backend/user-service/src/test/resources/features/update-profile.feature` | Update profile scenarios |
| `backend/user-service/src/test/java/com/bidnow/user/bdd/steps/UserProfileSteps.java` | All user profile step definitions |
| `backend/user-service/src/test/java/com/bidnow/user/bdd/steps/CommonSteps.java` | Shared assertion steps |

---

## Task 1: Create `bdd-support` module

**Files:**
- Create: `backend/bdd-support/pom.xml`
- Create: `backend/bdd-support/src/main/java/com/bidnow/bdd/container/PostgresContainerSupport.java`
- Create: `backend/bdd-support/src/main/java/com/bidnow/bdd/container/KafkaContainerSupport.java`
- Create: `backend/bdd-support/src/main/java/com/bidnow/bdd/wiremock/WireMockSupport.java`
- Create: `backend/bdd-support/src/main/java/com/bidnow/bdd/client/BddRestClient.java`
- Create: `backend/bdd-support/src/main/java/com/bidnow/bdd/context/ScenarioContext.java`
- Modify: `backend/pom.xml`

**Interfaces:**
- Produces:
  - `PostgresContainerSupport.properties()` → `Map<String, String>` (DB URL/user/pass)
  - `KafkaContainerSupport.properties()` → `Map<String, String>` (bootstrap-servers)
  - `WireMockSupport.SERVER` → `WireMockServer` (running, dynamic port)
  - `WireMockSupport.reset()` → void
  - `WireMockSupport.baseUrl()` → `String`
  - `BddRestClient.given()` → `io.restassured.specification.RequestSpecification`
  - `ScenarioContext.lastResponse` → `io.restassured.response.Response`
  - `ScenarioContext.authToken` → `String`
  - `ScenarioContext.currentEmail` → `String`

- [ ] **Step 1: Add `bdd-support` to the root module list**

Open `backend/pom.xml`. In the `<modules>` block, add `bdd-support` after `common` and before `discovery-service`:

```xml
<modules>
    <module>common</module>
    <module>bdd-support</module>
    <module>discovery-service</module>
    <module>api-gateway</module>
    <module>identity-service</module>
    <module>user-service</module>
    <module>auction-service</module>
    <module>bidding-service</module>
    <module>wallet-service</module>
    <module>media-service</module>
</modules>
```

- [ ] **Step 2: Create `bdd-support/pom.xml`**

Create `backend/bdd-support/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.bidnow</groupId>
        <artifactId>bidnow-backend</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>bdd-support</artifactId>
    <name>bdd-support</name>
    <description>Shared BDD test infrastructure (Cucumber, Testcontainers, WireMock)</description>

    <properties>
        <cucumber.version>7.18.0</cucumber.version>
        <wiremock.version>3.5.4</wiremock.version>
    </properties>

    <dependencies>
        <!-- Cucumber -->
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-java</artifactId>
            <version>${cucumber.version}</version>
        </dependency>
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-spring</artifactId>
            <version>${cucumber.version}</version>
        </dependency>
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-junit-platform-engine</artifactId>
            <version>${cucumber.version}</version>
        </dependency>

        <!-- JUnit Platform Suite (for @Suite runner) -->
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-suite</artifactId>
        </dependency>

        <!-- Testcontainers -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
        </dependency>

        <!-- WireMock -->
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock</artifactId>
            <version>${wiremock.version}</version>
        </dependency>

        <!-- RestAssured -->
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
        </dependency>

        <!-- Spring Boot Test — compile scope so @LocalServerPort is available in src/main -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-test</artifactId>
            <scope>compile</scope>
        </dependency>

        <!-- Spring Context for @Component, @Value -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create `PostgresContainerSupport`**

Create `backend/bdd-support/src/main/java/com/bidnow/bdd/container/PostgresContainerSupport.java`:

```java
package com.bidnow.bdd.container;

import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

public class PostgresContainerSupport {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    public static Map<String, String> properties() {
        return Map.of(
                "spring.datasource.url",      POSTGRES.getJdbcUrl(),
                "spring.datasource.username", POSTGRES.getUsername(),
                "spring.datasource.password", POSTGRES.getPassword()
        );
    }
}
```

- [ ] **Step 4: Create `KafkaContainerSupport`**

Create `backend/bdd-support/src/main/java/com/bidnow/bdd/container/KafkaContainerSupport.java`:

```java
package com.bidnow.bdd.container;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class KafkaContainerSupport {

    public static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static {
        KAFKA.start();
    }

    public static Map<String, String> properties() {
        return Map.of("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
    }
}
```

- [ ] **Step 5: Create `WireMockSupport`**

Create `backend/bdd-support/src/main/java/com/bidnow/bdd/wiremock/WireMockSupport.java`:

```java
package com.bidnow.bdd.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class WireMockSupport {

    public static final WireMockServer SERVER =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        SERVER.start();
    }

    public static void reset() {
        SERVER.resetAll();
    }

    public static String baseUrl() {
        return "http://localhost:" + SERVER.port();
    }
}
```

- [ ] **Step 6: Create `BddRestClient`**

Create `backend/bdd-support/src/main/java/com/bidnow/bdd/client/BddRestClient.java`:

```java
package com.bidnow.bdd.client;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BddRestClient {

    @Value("${local.server.port}")
    private int port;

    public RequestSpecification given() {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON);
    }
}
```

- [ ] **Step 7: Create `ScenarioContext`**

Create `backend/bdd-support/src/main/java/com/bidnow/bdd/context/ScenarioContext.java`:

```java
package com.bidnow.bdd.context;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
@ScenarioScope
public class ScenarioContext {
    private Response lastResponse;
    private String authToken;
    private String currentEmail;
}
```

- [ ] **Step 8: Verify `bdd-support` compiles**

Run from `backend/`:
```
mvn clean compile -pl bdd-support
```

Expected: `BUILD SUCCESS`. If you see `ClassNotFoundException` for WireMock or Testcontainers, check pom.xml dependency spelling.

---

## Task 2: Wire BDD into `identity-service`

**Files:**
- Modify: `backend/identity-service/pom.xml`
- Create: `backend/identity-service/src/test/java/com/bidnow/identity/bdd/CucumberTest.java`
- Create: `backend/identity-service/src/test/java/com/bidnow/identity/bdd/config/CucumberSpringConfig.java`
- Create: `backend/identity-service/src/test/resources/application-bdd.yml`
- Create: `backend/identity-service/src/test/resources/features/register.feature`
- Create: `backend/identity-service/src/test/resources/features/login.feature`
- Create: `backend/identity-service/src/test/resources/features/otp.feature`

**Interfaces:**
- Consumes: `PostgresContainerSupport.properties()`, `KafkaContainerSupport.properties()`, `WireMockSupport.baseUrl()`
- Produces: Spring Boot test context for identity-service at a random HTTP port

- [ ] **Step 1: Add `bdd-support` dependency to identity-service**

In `backend/identity-service/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.bidnow</groupId>
    <artifactId>bdd-support</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create `application-bdd.yml`**

Create `backend/identity-service/src/test/resources/application-bdd.yml`:

```yaml
spring:
  cloud:
    discovery:
      enabled: false
  jpa:
    show-sql: false

jwt:
  secret: bdd-test-secret-key-min-32-chars-bdd-only
  expiration: 86400000
  refresh-expiration: 604800000

eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false

management:
  tracing:
    enabled: false
```

Note: `spring.datasource.*`, `spring.kafka.bootstrap-servers`, and `spring.cloud.openfeign.client.config.user-service.url` are all set dynamically via `@DynamicPropertySource` in `CucumberSpringConfig` — they do not appear here.

- [ ] **Step 3: Create `CucumberSpringConfig`**

Create `backend/identity-service/src/test/java/com/bidnow/identity/bdd/config/CucumberSpringConfig.java`:

```java
package com.bidnow.identity.bdd.config;

import com.bidnow.bdd.container.KafkaContainerSupport;
import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.bdd.wiremock.WireMockSupport;
import com.bidnow.identity.IdentityApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(
        classes = IdentityApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("bdd")
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach(registry::add);
        KafkaContainerSupport.properties().forEach(registry::add);
        registry.add("spring.cloud.openfeign.client.config.user-service.url",
                WireMockSupport::baseUrl);
    }
}
```

- [ ] **Step 4: Create `CucumberTest` runner**

Create `backend/identity-service/src/test/java/com/bidnow/identity/bdd/CucumberTest.java`:

```java
package com.bidnow.identity.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameters({
        @ConfigurationParameter(
                key = PLUGIN_PROPERTY_NAME,
                value = "pretty, html:target/cucumber-reports/report.html"
        ),
        @ConfigurationParameter(
                key = GLUE_PROPERTY_NAME,
                value = "com.bidnow.identity.bdd"
        )
})
public class CucumberTest {
}
```

- [ ] **Step 5: Create `register.feature`**

Create `backend/identity-service/src/test/resources/features/register.feature`:

```gherkin
Feature: User Registration

  Scenario: Successful registration returns 202 with user data
    When user registers with name "Alice" email "alice@example.com" and password "P@ssw0rd1"
    Then the response status should be 202
    And the response field "data.email" should equal "alice@example.com"
    And the response field "data.accountStatus" should equal "PENDING_VERIFICATION"

  Scenario: Duplicate email is rejected with 400
    Given a user is already registered with email "alice@example.com"
    When user registers with name "Alice" email "alice@example.com" and password "P@ssw0rd1"
    Then the response status should be 400

  Scenario: Invalid email format is rejected with 400
    When user registers with name "Bob" email "not-an-email" and password "P@ssw0rd1"
    Then the response status should be 400

  Scenario: Short password is rejected with 400
    When user registers with name "Bob" email "bob@example.com" and password "short"
    Then the response status should be 400
```

- [ ] **Step 6: Create `login.feature`**

Create `backend/identity-service/src/test/resources/features/login.feature`:

```gherkin
Feature: User Login

  Scenario: Successful login returns access token
    Given a verified user exists with email "verified@example.com" and password "P@ssw0rd1"
    When user logs in with email "verified@example.com" and password "P@ssw0rd1"
    Then the response status should be 200
    And the response field "data.accessToken" should be present

  Scenario: Wrong password is rejected with 401
    Given a verified user exists with email "verified@example.com" and password "P@ssw0rd1"
    When user logs in with email "verified@example.com" and password "WrongPassword"
    Then the response status should be 401

  Scenario: Unverified account cannot login and returns 400
    Given an unverified user exists with email "pending@example.com" and password "P@ssw0rd1"
    When user logs in with email "pending@example.com" and password "P@ssw0rd1"
    Then the response status should be 400
```

- [ ] **Step 7: Create `otp.feature`**

Create `backend/identity-service/src/test/resources/features/otp.feature`:

```gherkin
Feature: OTP Verification

  Scenario: Valid OTP activates the account
    Given a user is registered and waiting for OTP with email "otp-valid@example.com"
    When user submits the correct OTP for email "otp-valid@example.com"
    Then the response status should be 200

  Scenario: Expired OTP is rejected with 400
    Given a user is registered and waiting for OTP with email "otp-expired@example.com"
    And the OTP for "otp-expired@example.com" has expired in the database
    When user submits the correct OTP for email "otp-expired@example.com"
    Then the response status should be 400

  Scenario: Incorrect OTP is rejected with 400
    Given a user is registered and waiting for OTP with email "otp-wrong@example.com"
    When user submits an incorrect OTP "000000" for email "otp-wrong@example.com"
    Then the response status should be 400
```

- [ ] **Step 8: Verify feature files parse correctly**

Run:
```
mvn test -pl identity-service -Dtest=CucumberTest
```

Expected: Cucumber runs but all steps are **undefined** (yellow/pending), not red. Output shows step snippets like:
```
You can implement missing steps with the snippets below:
@When("user registers with name {string} email {string} and password {string}")
```

This confirms Cucumber found the feature files and the Spring context boots. If you see `ApplicationContext` failures instead, check `application-bdd.yml` profile name and `@ActiveProfiles("bdd")`.

---

## Task 3: Write `identity-service` step definitions

**Files:**
- Create: `backend/identity-service/src/test/java/com/bidnow/identity/bdd/steps/AuthSteps.java`
- Create: `backend/identity-service/src/test/java/com/bidnow/identity/bdd/steps/CommonSteps.java`

**Interfaces:**
- Consumes:
  - `BddRestClient.given()` → `RequestSpecification`
  - `ScenarioContext.lastResponse` / `.authToken` / `.currentEmail`
  - `WireMockSupport.reset()` + `WireMockSupport.SERVER`
  - `JdbcTemplate` (Spring bean) for DB reads/writes in Given steps
  - `PasswordEncoder` (Spring bean from `SecurityConfig`) for hashing passwords in Given steps

- [ ] **Step 1: Create `CommonSteps`**

Create `backend/identity-service/src/test/java/com/bidnow/identity/bdd/steps/CommonSteps.java`:

```java
package com.bidnow.identity.bdd.steps;

import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

@Component
@RequiredArgsConstructor
public class CommonSteps {

    private final ScenarioContext ctx;

    @Then("the response status should be {int}")
    public void assertStatus(int expectedStatus) {
        assertThat(ctx.getLastResponse().statusCode())
                .as("Expected HTTP status %d but got %d. Body: %s",
                        expectedStatus,
                        ctx.getLastResponse().statusCode(),
                        ctx.getLastResponse().asString())
                .isEqualTo(expectedStatus);
    }

    @Then("the response field {string} should equal {string}")
    public void assertFieldEquals(String jsonPath, String expectedValue) {
        String actual = ctx.getLastResponse().jsonPath().getString(jsonPath);
        assertThat(actual)
                .as("Expected field '%s' to equal '%s' but was '%s'", jsonPath, expectedValue, actual)
                .isEqualTo(expectedValue);
    }

    @Then("the response field {string} should be present")
    public void assertFieldPresent(String jsonPath) {
        Object value = ctx.getLastResponse().jsonPath().get(jsonPath);
        assertThat(value)
                .as("Expected field '%s' to be present in response: %s",
                        jsonPath, ctx.getLastResponse().asString())
                .isNotNull();
    }
}
```

- [ ] **Step 2: Create `AuthSteps`**

Create `backend/identity-service/src/test/java/com/bidnow/identity/bdd/steps/AuthSteps.java`:

```java
package com.bidnow.identity.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@Component
@RequiredArgsConstructor
public class AuthSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Before
    public void resetWireMock() {
        WireMockSupport.reset();
        stubUserServiceCreateProfile();
    }

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("a user is already registered with email {string}")
    public void aUserIsAlreadyRegisteredWithEmail(String email) {
        String hash = passwordEncoder.encode("P@ssw0rd1");
        jdbcTemplate.update("""
                INSERT INTO identity_users
                    (email, password_hash, display_name, is_email_verified, is_active,
                     account_status, otp_failed_attempts, failed_login_attempts,
                     created_at, updated_at)
                VALUES (?, ?, 'Test User', false, false,
                        'PENDING_VERIFICATION', 0, 0, NOW(), NOW())
                ON CONFLICT (email) DO NOTHING
                """, email, hash);
    }

    @Given("a verified user exists with email {string} and password {string}")
    public void aVerifiedUserExistsWithEmailAndPassword(String email, String password) {
        String hash = passwordEncoder.encode(password);
        jdbcTemplate.update("""
                INSERT INTO identity_users
                    (email, password_hash, display_name, is_email_verified, is_active,
                     account_status, otp_failed_attempts, failed_login_attempts,
                     created_at, updated_at)
                VALUES (?, ?, 'Test User', true, true,
                        'ACTIVE', 0, 0, NOW(), NOW())
                ON CONFLICT (email) DO NOTHING
                """, email, hash);
    }

    @Given("an unverified user exists with email {string} and password {string}")
    public void anUnverifiedUserExistsWithEmailAndPassword(String email, String password) {
        String hash = passwordEncoder.encode(password);
        jdbcTemplate.update("""
                INSERT INTO identity_users
                    (email, password_hash, display_name, is_email_verified, is_active,
                     account_status, otp_failed_attempts, failed_login_attempts,
                     created_at, updated_at)
                VALUES (?, ?, 'Test User', false, false,
                        'PENDING_VERIFICATION', 0, 0, NOW(), NOW())
                ON CONFLICT (email) DO NOTHING
                """, email, hash);
    }

    @Given("a user is registered and waiting for OTP with email {string}")
    public void aUserIsRegisteredAndWaitingForOtp(String email) {
        ctx.setCurrentEmail(email);
        client.given()
                .body(Map.of("name", "OTP User", "email", email, "password", "P@ssw0rd1"))
                .post("/api/v1/auth/register");
    }

    @Given("the OTP for {string} has expired in the database")
    public void theOtpHasExpired(String email) {
        jdbcTemplate.update("""
                UPDATE identity_users
                SET otp_expires_at = NOW() - INTERVAL '1 minute'
                WHERE email = ?
                """, email);
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("user registers with name {string} email {string} and password {string}")
    public void userRegisters(String name, String email, String password) {
        ctx.setLastResponse(
                client.given()
                        .body(Map.of("name", name, "email", email, "password", password))
                        .post("/api/v1/auth/register")
        );
    }

    @When("user logs in with email {string} and password {string}")
    public void userLogsIn(String email, String password) {
        ctx.setLastResponse(
                client.given()
                        .body(Map.of("email", email, "password", password))
                        .post("/api/v1/auth/login")
        );
        String token = ctx.getLastResponse().jsonPath().getString("data.accessToken");
        if (token != null) {
            ctx.setAuthToken(token);
        }
    }

    @When("user submits the correct OTP for email {string}")
    public void userSubmitsCorrectOtp(String email) {
        String otp = jdbcTemplate.queryForObject(
                "SELECT verification_otp FROM identity_users WHERE email = ?",
                String.class, email);
        ctx.setLastResponse(
                client.given()
                        .body(Map.of("email", email, "otp", otp))
                        .post("/api/v1/auth/verify-otp")
        );
    }

    @When("user submits an incorrect OTP {string} for email {string}")
    public void userSubmitsIncorrectOtp(String otp, String email) {
        ctx.setLastResponse(
                client.given()
                        .body(Map.of("email", email, "otp", otp))
                        .post("/api/v1/auth/verify-otp")
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void stubUserServiceCreateProfile() {
        WireMockSupport.SERVER.stubFor(
                post(urlEqualTo("/api/v1/users/internal/profiles"))
                        .willReturn(aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {"status":201,"message":"User profile created successfully","data":null}
                                        """))
        );
    }
}
```

- [ ] **Step 3: Run the BDD tests**

```
mvn test -pl identity-service -Dtest=CucumberTest
```

Expected: All scenarios pass (green). If a scenario fails:
- `400` when expecting `202` on register → check `RegisterRequest` field names (`name`, `email`, `password`)
- `500` on verify-otp → check WireMock stub is matched (enable WireMock verbose logging by adding `.withBody(...)` debug)
- Spring context fails to start → check `application-bdd.yml` is on the test classpath and `@ActiveProfiles("bdd")` is set

- [ ] **Step 4: Run the full identity-service test suite**

```
mvn test -pl identity-service
```

Expected: `BUILD SUCCESS`. All existing Mockito unit tests AND the new Cucumber tests pass together.

---

## Task 4: Wire BDD into `user-service`

**Files:**
- Modify: `backend/user-service/pom.xml`
- Create: `backend/user-service/src/test/java/com/bidnow/user/bdd/CucumberTest.java`
- Create: `backend/user-service/src/test/java/com/bidnow/user/bdd/config/CucumberSpringConfig.java`
- Create: `backend/user-service/src/test/resources/application-bdd.yml`
- Create: `backend/user-service/src/test/resources/features/get-profile.feature`
- Create: `backend/user-service/src/test/resources/features/update-profile.feature`

**Interfaces:**
- Consumes: `PostgresContainerSupport.properties()`, `KafkaContainerSupport.properties()`
- Produces: Spring Boot test context for user-service at a random HTTP port

- [ ] **Step 1: Add `bdd-support` dependency to user-service**

In `backend/user-service/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.bidnow</groupId>
    <artifactId>bdd-support</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create `application-bdd.yml`**

Create `backend/user-service/src/test/resources/application-bdd.yml`:

```yaml
spring:
  cloud:
    discovery:
      enabled: false
  jpa:
    show-sql: false
  kafka:
    consumer:
      auto-offset-reset: earliest

eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false

management:
  tracing:
    enabled: false
```

Note: `spring.datasource.*` and `spring.kafka.bootstrap-servers` are set by `@DynamicPropertySource`.

- [ ] **Step 3: Create `CucumberSpringConfig`**

Create `backend/user-service/src/test/java/com/bidnow/user/bdd/config/CucumberSpringConfig.java`:

```java
package com.bidnow.user.bdd.config;

import com.bidnow.bdd.container.KafkaContainerSupport;
import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.user.UserApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(
        classes = UserApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("bdd")
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach(registry::add);
        KafkaContainerSupport.properties().forEach(registry::add);
    }
}
```

Note: user-service has no Feign clients, so no WireMock URL override is needed.

- [ ] **Step 4: Create `CucumberTest` runner**

Create `backend/user-service/src/test/java/com/bidnow/user/bdd/CucumberTest.java`:

```java
package com.bidnow.user.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameters({
        @ConfigurationParameter(
                key = PLUGIN_PROPERTY_NAME,
                value = "pretty, html:target/cucumber-reports/report.html"
        ),
        @ConfigurationParameter(
                key = GLUE_PROPERTY_NAME,
                value = "com.bidnow.user.bdd"
        )
})
public class CucumberTest {
}
```

- [ ] **Step 5: Create `get-profile.feature`**

Create `backend/user-service/src/test/resources/features/get-profile.feature`:

```gherkin
Feature: Get User Profile

  Scenario: Authenticated user retrieves their own profile
    Given a user profile exists with id "550e8400-e29b-41d4-a716-446655440001" and email "alice@example.com"
    When user with id "550e8400-e29b-41d4-a716-446655440001" requests their profile
    Then the response status should be 200
    And the response field "data.userId" should equal "550e8400-e29b-41d4-a716-446655440001"

  Scenario: Non-existent profile returns 404
    When user with id "00000000-0000-0000-0000-000000000099" requests their profile
    Then the response status should be 404

  Scenario: Request without X-User-Id header returns 401
    When an unauthenticated request is made to get profile
    Then the response status should be 401
```

- [ ] **Step 6: Create `update-profile.feature`**

Create `backend/user-service/src/test/resources/features/update-profile.feature`:

```gherkin
Feature: Update User Profile

  Scenario: User updates display name successfully
    Given a user profile exists with id "550e8400-e29b-41d4-a716-446655440002" and email "bob@example.com"
    When user with id "550e8400-e29b-41d4-a716-446655440002" updates display name to "Bob Updated"
    Then the response status should be 200
    And the response field "data.displayName" should equal "Bob Updated"

  Scenario: Display name exceeding 100 characters is rejected
    Given a user profile exists with id "550e8400-e29b-41d4-a716-446655440002" and email "bob@example.com"
    When user with id "550e8400-e29b-41d4-a716-446655440002" updates display name to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    Then the response status should be 400
```

- [ ] **Step 7: Verify feature files parse**

```
mvn test -pl user-service -Dtest=CucumberTest
```

Expected: Steps undefined (yellow), Spring context boots. No `ApplicationContext` failures.

---

## Task 5: Write `user-service` step definitions

**Files:**
- Create: `backend/user-service/src/test/java/com/bidnow/user/bdd/steps/UserProfileSteps.java`
- Create: `backend/user-service/src/test/java/com/bidnow/user/bdd/steps/CommonSteps.java`

**Interfaces:**
- Consumes:
  - `BddRestClient.given()` → `RequestSpecification`
  - `ScenarioContext.lastResponse`
  - `JdbcTemplate` (Spring bean) for inserting test profiles in Given steps
- Produces: passing Cucumber scenarios for user-service

- [ ] **Step 1: Create `CommonSteps`**

Create `backend/user-service/src/test/java/com/bidnow/user/bdd/steps/CommonSteps.java`:

```java
package com.bidnow.user.bdd.steps;

import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

@Component
@RequiredArgsConstructor
public class CommonSteps {

    private final ScenarioContext ctx;

    @Then("the response status should be {int}")
    public void assertStatus(int expectedStatus) {
        assertThat(ctx.getLastResponse().statusCode())
                .as("Expected HTTP status %d but got %d. Body: %s",
                        expectedStatus,
                        ctx.getLastResponse().statusCode(),
                        ctx.getLastResponse().asString())
                .isEqualTo(expectedStatus);
    }

    @Then("the response field {string} should equal {string}")
    public void assertFieldEquals(String jsonPath, String expectedValue) {
        String actual = ctx.getLastResponse().jsonPath().getString(jsonPath);
        assertThat(actual)
                .as("Expected field '%s' to equal '%s' but was '%s'", jsonPath, expectedValue, actual)
                .isEqualTo(expectedValue);
    }

    @Then("the response field {string} should be present")
    public void assertFieldPresent(String jsonPath) {
        Object value = ctx.getLastResponse().jsonPath().get(jsonPath);
        assertThat(value)
                .as("Expected field '%s' to be present in response: %s",
                        jsonPath, ctx.getLastResponse().asString())
                .isNotNull();
    }
}
```

- [ ] **Step 2: Create `UserProfileSteps`**

Create `backend/user-service/src/test/java/com/bidnow/user/bdd/steps/UserProfileSteps.java`:

```java
package com.bidnow.user.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserProfileSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;

    @Before
    public void cleanProfiles() {
        jdbcTemplate.update("DELETE FROM user_profiles WHERE email LIKE '%@example.com'");
    }

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("a user profile exists with id {string} and email {string}")
    public void aUserProfileExistsWithIdAndEmail(String userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO user_profiles (user_id, email, display_name, created_at, updated_at)
                VALUES (CAST(? AS uuid), ?, ?, NOW(), NOW())
                ON CONFLICT (user_id) DO NOTHING
                """, userId, email, "Test User");
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("user with id {string} requests their profile")
    public void userRequestsProfile(String userId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", userId)
                        .header("X-User-Roles", "USER")
                        .get("/api/v1/users/me")
        );
    }

    @When("an unauthenticated request is made to get profile")
    public void unauthenticatedGetProfile() {
        ctx.setLastResponse(
                client.given()
                        .get("/api/v1/users/me")
        );
    }

    @When("user with id {string} updates display name to {string}")
    public void userUpdatesDisplayName(String userId, String displayName) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", userId)
                        .header("X-User-Roles", "USER")
                        .body(Map.of("displayName", displayName))
                        .put("/api/v1/users/me")
        );
    }
}
```

- [ ] **Step 3: Run the BDD tests**

```
mvn test -pl user-service -Dtest=CucumberTest
```

Expected: All scenarios pass. If `404` when expecting `200` on get-profile, check the table name in the INSERT statement matches the Liquibase migration (`user_profiles`). Run:

```
mvn test -pl user-service -Dtest=CucumberTest -Dspring.jpa.show-sql=true
```

to see SQL generated by JPA and compare against your table name.

- [ ] **Step 4: Run the full user-service test suite**

```
mvn test -pl user-service
```

Expected: `BUILD SUCCESS`. All existing unit tests AND new Cucumber tests pass.

---

## Task 6: Verify both services together

- [ ] **Step 1: Build `bdd-support` and run all BDD tests**

```
mvn clean install -pl common,bdd-support -DskipTests
mvn test -pl identity-service,user-service -Dtest=CucumberTest
```

Expected: Both services pass. Containers start once per JVM per service (two separate JVM runs, so four containers total — Postgres + Kafka for each service).

- [ ] **Step 2: Confirm HTML reports were generated**

After the test run, verify these files exist:
- `backend/identity-service/target/cucumber-reports/report.html`
- `backend/user-service/target/cucumber-reports/report.html`

Open either file in a browser to confirm all scenarios are green.

- [ ] **Step 3: Run full backend build**

```
mvn clean install -DskipTests
```

Expected: `BUILD SUCCESS` for all 9 modules (common, bdd-support, discovery-service, api-gateway, identity-service, user-service, auction-service, bidding-service, wallet-service, media-service).

---

## Self-Review Notes

1. **Register returns 202 (not 201)** — feature file correctly uses 202. `RegisterRequest` requires `name` (not `displayName`) — step definition uses `"name"` key in the body map. ✓
2. **OTP read from DB** — `AuthSteps.userSubmitsCorrectOtp()` queries `verification_otp` column from `identity_users` table. OTP expiry step updates `otp_expires_at` directly via JDBC. ✓
3. **Feign URL override** — `DynamicPropertySource` sets `spring.cloud.openfeign.client.config.user-service.url` before Spring context starts. WireMock stub for `/api/v1/users/internal/profiles` is registered in `@Before`. ✓
4. **user-service auth** — `RoleHeaderFilter` requires non-blank `X-User-Id` header to populate SecurityContext. Steps pass `X-User-Id` + `X-User-Roles: USER` for authenticated requests; unauthenticated step omits header and expects 401. ✓
5. **DB table names** — identity-service uses `identity_users` (from `User` entity `@Table`); user-service `UserProfile` entity table name must match Liquibase migration — verify with `mvn test -Dspring.jpa.show-sql=true` if 404s occur. ✓
6. **`@Before` in AuthSteps** — resets WireMock AND re-registers the user-service stub each scenario. This prevents test isolation issues when `verifyOtp` calls Feign. ✓
7. **`@Before` in UserProfileSteps** — deletes test profiles before each scenario to prevent unique constraint conflicts from re-runs. ✓
