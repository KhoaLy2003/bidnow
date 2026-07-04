# Auction Service BDD Cucumber Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add BDD Cucumber testing to `auction-service`, covering all three controllers (public/seller, admin, categories) via business-flow-organized feature files, using the same infrastructure pattern as `user-service` and `identity-service`.

**Architecture:** Reuse the existing `bdd-support` module (Cucumber 7.18, Testcontainers Postgres+Kafka, WireMock, RestAssured). Extend it with a `RedisContainerSupport` class for the Redis cache. The auction-service gets a `bdd` Spring profile, a BDD-specific Liquibase changelog that layers seed data on top of production migrations, and four business-flow feature files each backed by a dedicated step-definition class.

**Tech Stack:** Cucumber 7.18.0, JUnit Platform Suite, Spring Boot Test (RANDOM_PORT), Testcontainers (PostgreSQL 16, Kafka, Redis 7 via GenericContainer), WireMock 3.5.4, RestAssured — all from the existing `bdd-support` module.

## Global Constraints

- All new Java files go under the `test` source root, not `main`
- Follow the exact package naming: `com.bidnow.auction.bdd` for the runner/config, `com.bidnow.auction.bdd.steps` for step classes
- Fixed UUIDs used throughout (never `gen_random_uuid()` in test seed data):
  - `SELLER_A = 550e8400-e29b-41d4-a716-446655440001`
  - `SELLER_B = 550e8400-e29b-41d4-a716-446655440002`
  - `TEST_CATEGORY = a0000000-0000-0000-0000-000000000001`
  - `AUCTION_DRAFT = b0000000-0000-0000-0000-000000000001`
  - `AUCTION_SCHEDULED = b0000000-0000-0000-0000-000000000002`
  - `AUCTION_ACTIVE = b0000000-0000-0000-0000-000000000003`
  - `AUCTION_CANCELLED = b0000000-0000-0000-0000-000000000004`
- The `init_jobrunr_tables.sql` is already in `db.changelog-master.xml` — do NOT include it again in `db.changelog-bdd.xml`
- No changes to production source files except `auction-service/pom.xml` and `bdd-support/pom.xml` (neither needs the extra pom change — GenericContainer is a transitive dep already)
- Never break existing unit tests (`AuctionControllerTest`, `AuctionServiceImplTest`, etc.)

---

## File Map

| Action | Path |
|---|---|
| Create | `backend/bdd-support/src/main/java/com/bidnow/bdd/container/RedisContainerSupport.java` |
| Modify | `backend/auction-service/pom.xml` |
| Create | `backend/auction-service/src/test/resources/application-bdd.yml` |
| Create | `backend/auction-service/src/test/resources/db/changelog/db.changelog-bdd.xml` |
| Create | `backend/auction-service/src/test/resources/db/changelog/test-data.sql` |
| Create | `backend/auction-service/src/test/java/com/bidnow/auction/bdd/CucumberTest.java` |
| Create | `backend/auction-service/src/test/java/com/bidnow/auction/bdd/config/CucumberSpringConfig.java` |
| Create | `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/CommonSteps.java` |
| Create | `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/AuctionCategorySteps.java` |
| Create | `backend/auction-service/src/test/resources/features/auction-categories.feature` |
| Create | `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/PublicAuctionSteps.java` |
| Create | `backend/auction-service/src/test/resources/features/browse-auctions.feature` |
| Create | `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/SellerAuctionSteps.java` |
| Create | `backend/auction-service/src/test/resources/features/seller-auction-lifecycle.feature` |
| Create | `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/AdminAuctionSteps.java` |
| Create | `backend/auction-service/src/test/resources/features/admin-moderation.feature` |

---

### Task 1: Add RedisContainerSupport to bdd-support

**Files:**
- Create: `backend/bdd-support/src/main/java/com/bidnow/bdd/container/RedisContainerSupport.java`

**Interfaces:**
- Produces: `RedisContainerSupport.properties()` → `Map<String, String>` with keys `spring.data.redis.host` and `spring.data.redis.port` — consumed by Task 2's `CucumberSpringConfig`

- [ ] **Step 1: Create RedisContainerSupport**

```java
// backend/bdd-support/src/main/java/com/bidnow/bdd/container/RedisContainerSupport.java
package com.bidnow.bdd.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class RedisContainerSupport {

    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    public static Map<String, String> properties() {
        return Map.of(
                "spring.data.redis.host", REDIS.getHost(),
                "spring.data.redis.port", String.valueOf(REDIS.getMappedPort(6379))
        );
    }
}
```

- [ ] **Step 2: Build bdd-support to make the class available**

Run from `backend/` directory:
```bash
mvn install -pl bdd-support -DskipTests
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add backend/bdd-support/src/main/java/com/bidnow/bdd/container/RedisContainerSupport.java
git commit -m "feat(bdd): add RedisContainerSupport for Testcontainers Redis"
```

---

### Task 2: Wire BDD infrastructure into auction-service

**Files:**
- Modify: `backend/auction-service/pom.xml`
- Create: `backend/auction-service/src/test/resources/application-bdd.yml`
- Create: `backend/auction-service/src/test/resources/db/changelog/db.changelog-bdd.xml`
- Create: `backend/auction-service/src/test/resources/db/changelog/test-data.sql`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/bdd/CucumberTest.java`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/bdd/config/CucumberSpringConfig.java`
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/CommonSteps.java`

**Interfaces:**
- Consumes: `RedisContainerSupport.properties()` from Task 1
- Produces: Spring Boot BDD context that all step classes in Tasks 3–6 inject into

- [ ] **Step 1: Add bdd-support test dependency to auction-service pom.xml**

In `backend/auction-service/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.bidnow</groupId>
    <artifactId>bdd-support</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create application-bdd.yml**

```yaml
# backend/auction-service/src/test/resources/application-bdd.yml
spring:
  cloud:
    discovery:
      enabled: false
  jpa:
    show-sql: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-bdd.xml
  kafka:
    consumer:
      auto-offset-reset: earliest
  cache:
    type: redis

eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false

management:
  tracing:
    enabled: false

org:
  jobrunr:
    background-job-server:
      enabled: false
    dashboard:
      enabled: false
```

- [ ] **Step 3: Create db.changelog-bdd.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <!-- Production migrations (includes JobRunr tables, schema, and category seeds) -->
    <include file="db/changelog/db.changelog-master.xml"/>

    <!-- BDD-only seed data with fixed UUIDs -->
    <include file="db/changelog/test-data.sql"/>

</databaseChangeLog>
```

- [ ] **Step 4: Create test-data.sql**

```sql
-- liquibase formatted sql

-- changeset bidnow:bdd-test-category
-- comment: Fixed-UUID category for BDD test data references
INSERT INTO auction_categories (id, name, slug, description, display_order, is_active)
VALUES ('a0000000-0000-0000-0000-000000000001'::uuid,
        'BDD Test Category', 'bdd-test-category', 'Category for BDD tests', 99, TRUE)
ON CONFLICT (id) DO NOTHING;

-- changeset bidnow:bdd-test-auctions
-- comment: Seed four auctions in different statuses for BDD scenario coverage
INSERT INTO auction_items (id, seller_id, title, description, category_id,
                           starting_price, bid_increment, deposit_amount,
                           current_price, total_bids, current_winner_id,
                           status, start_time, end_time, original_end_time,
                           created_at, updated_at)
VALUES
    ('b0000000-0000-0000-0000-000000000001'::uuid,
     '550e8400-e29b-41d4-a716-446655440001'::uuid,
     'BDD Draft Auction', 'A draft auction for BDD tests',
     'a0000000-0000-0000-0000-000000000001'::uuid,
     100.00, 10.00, 20.00, 100.00, 0, NULL,
     'DRAFT',
     NOW() + INTERVAL '1 day', NOW() + INTERVAL '8 days', NOW() + INTERVAL '8 days',
     NOW(), NOW()),

    ('b0000000-0000-0000-0000-000000000002'::uuid,
     '550e8400-e29b-41d4-a716-446655440001'::uuid,
     'BDD Scheduled Auction', 'A scheduled auction for BDD tests',
     'a0000000-0000-0000-0000-000000000001'::uuid,
     200.00, 20.00, 40.00, 200.00, 0, NULL,
     'SCHEDULED',
     NOW() + INTERVAL '1 day', NOW() + INTERVAL '8 days', NOW() + INTERVAL '8 days',
     NOW(), NOW()),

    ('b0000000-0000-0000-0000-000000000003'::uuid,
     '550e8400-e29b-41d4-a716-446655440001'::uuid,
     'BDD Active Auction', 'An active auction for BDD tests',
     'a0000000-0000-0000-0000-000000000001'::uuid,
     300.00, 30.00, 60.00, 330.00, 1,
     '550e8400-e29b-41d4-a716-446655440002'::uuid,
     'ACTIVE',
     NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
     NOW(), NOW()),

    ('b0000000-0000-0000-0000-000000000004'::uuid,
     '550e8400-e29b-41d4-a716-446655440001'::uuid,
     'BDD Cancelled Auction', 'A cancelled auction for BDD tests',
     'a0000000-0000-0000-0000-000000000001'::uuid,
     150.00, 15.00, 30.00, 150.00, 0, NULL,
     'CANCELLED',
     NOW() - INTERVAL '2 days', NOW() + INTERVAL '5 days', NOW() + INTERVAL '5 days',
     NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
```

- [ ] **Step 5: Create CucumberTest.java**

```java
// backend/auction-service/src/test/java/com/bidnow/auction/bdd/CucumberTest.java
package com.bidnow.auction.bdd;

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
                value = "com.bidnow.auction.bdd"
        )
})
public class CucumberTest {
}
```

- [ ] **Step 6: Create CucumberSpringConfig.java**

```java
// backend/auction-service/src/test/java/com/bidnow/auction/bdd/config/CucumberSpringConfig.java
package com.bidnow.auction.bdd.config;

import com.bidnow.auction.AuctionApplication;
import com.bidnow.bdd.container.KafkaContainerSupport;
import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.bdd.container.RedisContainerSupport;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(
        classes = AuctionApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("bdd")
public class CucumberSpringConfig {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        KafkaContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        RedisContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("spring.cloud.openfeign.client.config.user-service.url",
                WireMockSupport::baseUrl);
    }
}
```

- [ ] **Step 7: Create CommonSteps.java**

```java
// backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/CommonSteps.java
package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;

import static org.assertj.core.api.Assertions.assertThat;

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

- [ ] **Step 8: Verify the project compiles**

Run from `backend/`:
```bash
mvn test-compile -pl auction-service
```
Expected: `BUILD SUCCESS` with no compilation errors.

- [ ] **Step 9: Commit**

```bash
git add backend/auction-service/pom.xml \
        backend/auction-service/src/test/resources/ \
        backend/auction-service/src/test/java/com/bidnow/auction/bdd/
git commit -m "feat(bdd): scaffold BDD infrastructure for auction-service"
```

---

### Task 3: Auction categories feature

**Files:**
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/AuctionCategorySteps.java`
- Create: `backend/auction-service/src/test/resources/features/auction-categories.feature`

**Interfaces:**
- Consumes: `BddRestClient`, `ScenarioContext` from bdd-support; `CommonSteps` step definitions from Task 2
- Produces: step definitions for `auction-categories.feature`

- [ ] **Step 1: Create auction-categories.feature**

```gherkin
# backend/auction-service/src/test/resources/features/auction-categories.feature
Feature: Auction Categories

  Scenario: Any user can retrieve active auction categories
    When a request is made to get all auction categories
    Then the response status should be 200
    And the response field "data" should be present
```

- [ ] **Step 2: Create AuctionCategorySteps.java**

```java
// backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/AuctionCategorySteps.java
package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuctionCategorySteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;

    @When("a request is made to get all auction categories")
    public void getCategories() {
        ctx.setLastResponse(client.given().get("/api/v1/categories"));
    }
}
```

- [ ] **Step 3: Run the categories feature to verify end-to-end wiring**

Run from `backend/`:
```bash
mvn test -pl auction-service -Dtest=CucumberTest -DfailIfNoTests=false
```
Expected: 1 scenario passes. If Docker is not available the containers will fail to start — ensure Docker Desktop is running.

- [ ] **Step 4: Commit**

```bash
git add backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/AuctionCategorySteps.java \
        backend/auction-service/src/test/resources/features/auction-categories.feature
git commit -m "feat(bdd): add auction-categories BDD scenario"
```

---

### Task 4: Public browse feature

**Files:**
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/PublicAuctionSteps.java`
- Create: `backend/auction-service/src/test/resources/features/browse-auctions.feature`

**Interfaces:**
- Consumes: `BddRestClient`, `ScenarioContext`, `WireMockSupport.SERVER` from bdd-support
- Produces: step definitions for `browse-auctions.feature`

- [ ] **Step 1: Create browse-auctions.feature**

```gherkin
# backend/auction-service/src/test/resources/features/browse-auctions.feature
Feature: Browse Auctions

  Scenario: Public user browses auctions with no filters
    When a public request is made to browse auctions
    Then the response status should be 200
    And the response field "data.pagination" should be present

  Scenario: Public user browses auctions with filters
    When a public request is made to browse auctions with params "minPrice=50&maxPrice=500&page=0&size=5"
    Then the response status should be 200

  Scenario: Browse request with size exceeding cap returns 400
    When a public request is made to browse auctions with params "size=200"
    Then the response status should be 400

  Scenario: Public user gets a known active auction by ID
    When a public request is made to get auction "b0000000-0000-0000-0000-000000000003"
    Then the response status should be 200
    And the response field "data.title" should equal "BDD Active Auction"

  Scenario: Public user gets an unknown auction returns 404
    When a public request is made to get auction "00000000-0000-0000-0000-000000000099"
    Then the response status should be 404

  Scenario: Public user gets category auction counts
    When a public request is made to get category auction counts
    Then the response status should be 200
    And the response field "data" should be present
```

- [ ] **Step 2: Create PublicAuctionSteps.java**

```java
// backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/PublicAuctionSteps.java
package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import io.cucumber.java.en.When;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

@RequiredArgsConstructor
public class PublicAuctionSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;

    @Before
    public void setUpWireMock() {
        WireMockSupport.reset();
        WireMockSupport.SERVER.stubFor(
                get(urlMatching("/api/v1/users/internal/.*/summary"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"status\":200,\"message\":null," +
                                        "\"data\":{\"id\":\"550e8400-e29b-41d4-a716-446655440001\"," +
                                        "\"name\":\"Test Seller\",\"avatarUrl\":null}}"))
        );
    }

    @When("a public request is made to browse auctions")
    public void browseAuctions() {
        ctx.setLastResponse(client.given().get("/api/v1/auctions/public"));
    }

    @When("a public request is made to browse auctions with params {string}")
    public void browseAuctionsWithParams(String params) {
        RequestSpecification spec = client.given();
        for (String param : params.split("&")) {
            String[] kv = param.split("=", 2);
            spec.param(kv[0], kv[1]);
        }
        ctx.setLastResponse(spec.get("/api/v1/auctions/public"));
    }

    @When("a public request is made to get auction {string}")
    public void getAuctionById(String id) {
        ctx.setLastResponse(client.given().get("/api/v1/auctions/public/" + id));
    }

    @When("a public request is made to get category auction counts")
    public void getCategoryCounts() {
        ctx.setLastResponse(client.given().get("/api/v1/auctions/public/category-counts"));
    }
}
```

- [ ] **Step 3: Run tests — expect all 7 scenarios pass (1 from Task 3 + 6 new)**

```bash
mvn test -pl auction-service -Dtest=CucumberTest -DfailIfNoTests=false
```
Expected: 7 scenarios pass.

- [ ] **Step 4: Commit**

```bash
git add backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/PublicAuctionSteps.java \
        backend/auction-service/src/test/resources/features/browse-auctions.feature
git commit -m "feat(bdd): add browse-auctions BDD scenarios"
```

---

### Task 5: Seller auction lifecycle feature

**Files:**
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/SellerAuctionSteps.java`
- Create: `backend/auction-service/src/test/resources/features/seller-auction-lifecycle.feature`

**Interfaces:**
- Consumes: `BddRestClient`, `ScenarioContext`, `JdbcTemplate` from Spring context
- Produces: step definitions for `seller-auction-lifecycle.feature`. The `@Before` hook in this class resets all four seeded auctions to their baseline state before every scenario (including scenarios in other feature files).

- [ ] **Step 1: Create seller-auction-lifecycle.feature**

JSON request bodies go in the feature file as DocStrings (`"""`), not in Java code.

```gherkin
# backend/auction-service/src/test/resources/features/seller-auction-lifecycle.feature
Feature: Seller Auction Lifecycle

  Scenario: Seller creates a draft auction
    When seller "550e8400-e29b-41d4-a716-446655440001" creates a new auction with body:
      """
      {
        "title": "BDD New Auction",
        "description": "BDD test auction description",
        "categoryId": "a0000000-0000-0000-0000-000000000001",
        "startingPrice": 100.00,
        "bidIncrement": 10.00,
        "depositAmount": 20.00,
        "startTime": "2027-01-01T00:00:00Z",
        "endTime": "2027-01-08T00:00:00Z",
        "imageUrls": ["https://example.com/image1.jpg"]
      }
      """
    Then the response status should be 201
    And the response field "data.status" should equal "DRAFT"

  Scenario: Seller creates auction with missing title returns 400
    When seller "550e8400-e29b-41d4-a716-446655440001" creates a new auction with body:
      """
      {
        "description": "BDD test auction description",
        "categoryId": "a0000000-0000-0000-0000-000000000001",
        "startingPrice": 100.00,
        "bidIncrement": 10.00,
        "depositAmount": 20.00,
        "startTime": "2027-01-01T00:00:00Z",
        "endTime": "2027-01-08T00:00:00Z",
        "imageUrls": ["https://example.com/image1.jpg"]
      }
      """
    Then the response status should be 400

  Scenario: Seller updates a DRAFT auction
    When seller "550e8400-e29b-41d4-a716-446655440001" updates auction "b0000000-0000-0000-0000-000000000001" with body:
      """
      {"title": "Updated BDD Title"}
      """
    Then the response status should be 200
    And the response field "data.title" should equal "Updated BDD Title"

  Scenario: Seller cannot update a SCHEDULED auction
    When seller "550e8400-e29b-41d4-a716-446655440001" updates auction "b0000000-0000-0000-0000-000000000002" with body:
      """
      {"title": "Should Fail"}
      """
    Then the response status should be 400

  Scenario: Seller cannot update an ACTIVE auction
    When seller "550e8400-e29b-41d4-a716-446655440001" updates auction "b0000000-0000-0000-0000-000000000003" with body:
      """
      {"title": "Should Fail"}
      """
    Then the response status should be 400

  Scenario: Seller deletes a DRAFT auction
    When seller "550e8400-e29b-41d4-a716-446655440001" deletes auction "b0000000-0000-0000-0000-000000000001"
    Then the response status should be 204

  Scenario: Seller cannot delete an ACTIVE auction
    When seller "550e8400-e29b-41d4-a716-446655440001" deletes auction "b0000000-0000-0000-0000-000000000003"
    Then the response status should be 400

  Scenario: Seller publishes a DRAFT auction
    When seller "550e8400-e29b-41d4-a716-446655440001" publishes auction "b0000000-0000-0000-0000-000000000001"
    Then the response status should be 200

  Scenario: Seller cannot publish an auction with a past end time
    When seller "550e8400-e29b-41d4-a716-446655440001" creates a new auction with body:
      """
      {
        "title": "BDD Past End Time Auction",
        "description": "BDD test auction with past end time",
        "categoryId": "a0000000-0000-0000-0000-000000000001",
        "startingPrice": 100.00,
        "bidIncrement": 10.00,
        "depositAmount": 20.00,
        "startTime": "2020-01-01T00:00:00Z",
        "endTime": "2020-01-08T00:00:00Z",
        "imageUrls": ["https://example.com/image1.jpg"]
      }
      """
    Then the response status should be 201
    When seller "550e8400-e29b-41d4-a716-446655440001" publishes the last created auction
    Then the response status should be 400

  Scenario: Seller cancels their own auction
    When seller "550e8400-e29b-41d4-a716-446655440001" cancels auction "b0000000-0000-0000-0000-000000000001" with body:
      """
      {"reason": "BDD test cancellation"}
      """
    Then the response status should be 204

  Scenario: Non-owner cannot modify another seller's auction
    When seller "550e8400-e29b-41d4-a716-446655440002" updates auction "b0000000-0000-0000-0000-000000000001" with body:
      """
      {"title": "Unauthorized"}
      """
    Then the response status should be 403

  Scenario: Unauthenticated request to seller endpoint returns 401
    When an unauthenticated request is made to get my auctions
    Then the response status should be 401

  Scenario: Seller lists their own auctions
    When seller "550e8400-e29b-41d4-a716-446655440001" requests their own auctions
    Then the response status should be 200
    And the response field "data.pagination" should be present
```

- [ ] **Step 2: Create SellerAuctionSteps.java**

```java
// backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/SellerAuctionSteps.java
package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class SellerAuctionSteps {

    private static final String DRAFT_ID     = "b0000000-0000-0000-0000-000000000001";
    private static final String SCHEDULED_ID = "b0000000-0000-0000-0000-000000000002";
    private static final String ACTIVE_ID    = "b0000000-0000-0000-0000-000000000003";
    private static final String CANCELLED_ID = "b0000000-0000-0000-0000-000000000004";

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;

    @Before
    public void resetAuctions() {
        // Pattern: inline fixed UUIDs directly (no user input — same style as user-service @Before hooks)
        jdbcTemplate.update(
                "DELETE FROM auction_status_history " +
                "WHERE auction_id IN ('b0000000-0000-0000-0000-000000000001'::uuid," +
                " 'b0000000-0000-0000-0000-000000000002'::uuid," +
                " 'b0000000-0000-0000-0000-000000000003'::uuid," +
                " 'b0000000-0000-0000-0000-000000000004'::uuid)"
        );
        jdbcTemplate.update(
                "UPDATE auction_items SET status = 'DRAFT', deleted_at = NULL, " +
                "cancellation_reason = NULL, cancelled_by = NULL, cancelled_at = NULL, " +
                "rejection_reason = NULL, rejected_by = NULL, rejected_at = NULL, " +
                "winner_id = NULL, completed_at = NULL " +
                "WHERE id = 'b0000000-0000-0000-0000-000000000001'::uuid"
        );
        jdbcTemplate.update(
                "UPDATE auction_items SET status = 'SCHEDULED', " +
                "rejection_reason = NULL, rejected_by = NULL, rejected_at = NULL, " +
                "cancellation_reason = NULL, cancelled_by = NULL, cancelled_at = NULL " +
                "WHERE id = 'b0000000-0000-0000-0000-000000000002'::uuid"
        );
        jdbcTemplate.update(
                "UPDATE auction_items SET status = 'ACTIVE', " +
                "cancellation_reason = NULL, cancelled_by = NULL, cancelled_at = NULL, " +
                "winner_id = NULL, completed_at = NULL, " +
                "total_bids = 1, current_winner_id = '550e8400-e29b-41d4-a716-446655440002'::uuid " +
                "WHERE id = 'b0000000-0000-0000-0000-000000000003'::uuid"
        );
        jdbcTemplate.update(
                "UPDATE auction_items SET status = 'CANCELLED' " +
                "WHERE id = 'b0000000-0000-0000-0000-000000000004'::uuid"
        );
    }

    // ── Create ───────────────────────────────────────────────────────────────

    // DocString body comes from the feature file (the trailing String param receives the """ block)
    @When("seller {string} creates a new auction with body:")
    public void sellerCreatesAuction(String sellerId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .body(body)
                        .post("/api/v1/auctions")
        );
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @When("seller {string} updates auction {string} with body:")
    public void sellerUpdatesAuction(String sellerId, String auctionId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .body(body)
                        .put("/api/v1/auctions/" + auctionId)
        );
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @When("seller {string} deletes auction {string}")
    public void sellerDeletesAuction(String sellerId, String auctionId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .delete("/api/v1/auctions/" + auctionId)
        );
    }

    // ── Publish ──────────────────────────────────────────────────────────────

    @When("seller {string} publishes auction {string}")
    public void sellerPublishesAuction(String sellerId, String auctionId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .post("/api/v1/auctions/" + auctionId + "/publish")
        );
    }

    @When("seller {string} publishes the last created auction")
    public void sellerPublishesLastCreatedAuction(String sellerId) {
        String auctionId = ctx.getLastResponse().jsonPath().getString("data.id");
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .post("/api/v1/auctions/" + auctionId + "/publish")
        );
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    @When("seller {string} cancels auction {string} with body:")
    public void sellerCancelsAuction(String sellerId, String auctionId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .body(body)
                        .post("/api/v1/auctions/" + auctionId + "/cancel")
        );
    }

    // ── My Auctions ──────────────────────────────────────────────────────────

    @When("an unauthenticated request is made to get my auctions")
    public void unauthenticatedGetMyAuctions() {
        ctx.setLastResponse(client.given().get("/api/v1/auctions/me"));
    }

    @When("seller {string} requests their own auctions")
    public void sellerRequestsOwnAuctions(String sellerId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .get("/api/v1/auctions/me")
        );
    }
}
```

- [ ] **Step 3: Run tests — expect all 20 scenarios pass (7 prior + 13 new)**

```bash
mvn test -pl auction-service -Dtest=CucumberTest -DfailIfNoTests=false
```
Expected: 20 scenarios pass. If any fail, check the scenario output — the most likely cause is a mismatch in the `@Before` reset SQL (column name or UUID cast).

- [ ] **Step 4: Commit**

```bash
git add backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/SellerAuctionSteps.java \
        backend/auction-service/src/test/resources/features/seller-auction-lifecycle.feature
git commit -m "feat(bdd): add seller-auction-lifecycle BDD scenarios"
```

---

### Task 6: Admin moderation feature

**Files:**
- Create: `backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/AdminAuctionSteps.java`
- Create: `backend/auction-service/src/test/resources/features/admin-moderation.feature`

**Interfaces:**
- Consumes: `BddRestClient`, `ScenarioContext` from bdd-support; reset state provided by `SellerAuctionSteps.@Before` (runs before every scenario)
- Produces: step definitions for `admin-moderation.feature`

- [ ] **Step 1: Create admin-moderation.feature**

JSON bodies for reject/cancel go as DocStrings in the feature file.

```gherkin
# backend/auction-service/src/test/resources/features/admin-moderation.feature
Feature: Admin Auction Moderation

  Scenario: Admin lists all auctions
    When an admin lists all auctions
    Then the response status should be 200
    And the response field "data.pagination" should be present

  Scenario: Admin gets auction detail with status history
    When an admin gets auction detail for "b0000000-0000-0000-0000-000000000001"
    Then the response status should be 200
    And the response field "data.id" should equal "b0000000-0000-0000-0000-000000000001"

  Scenario: Admin rejects a SCHEDULED auction
    When an admin rejects auction "b0000000-0000-0000-0000-000000000002" with body:
      """
      {"reason": "Policy violation"}
      """
    Then the response status should be 200
    And the response field "data.status" should equal "REJECTED"

  Scenario: Admin cannot reject a non-SCHEDULED auction
    When an admin rejects auction "b0000000-0000-0000-0000-000000000003" with body:
      """
      {"reason": "Wrong status"}
      """
    Then the response status should be 400

  Scenario: Admin cancels an ACTIVE auction
    When an admin cancels auction "b0000000-0000-0000-0000-000000000003" with body:
      """
      {"reason": "Policy violation"}
      """
    Then the response status should be 200
    And the response field "data.status" should equal "CANCELLED"

  Scenario: Admin force-closes an ACTIVE auction with bids
    When an admin force-closes auction "b0000000-0000-0000-0000-000000000003"
    Then the response status should be 200
    And the response field "data.status" should equal "COMPLETED"

  Scenario: Non-admin user cannot access admin endpoints
    When a non-admin user lists all auctions
    Then the response status should be 403
```

- [ ] **Step 2: Create AdminAuctionSteps.java**

```java
// backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/AdminAuctionSteps.java
package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminAuctionSteps {

    private static final String ADMIN_ID = "550e8400-e29b-41d4-a716-000000000001";

    private final BddRestClient client;
    private final ScenarioContext ctx;

    @When("an admin lists all auctions")
    public void adminListsAllAuctions() {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .get("/api/v1/admin/auctions")
        );
    }

    @When("an admin gets auction detail for {string}")
    public void adminGetsAuctionDetail(String auctionId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .get("/api/v1/admin/auctions/" + auctionId)
        );
    }

    // DocString body from the feature file (""" block)
    @When("an admin rejects auction {string} with body:")
    public void adminRejectsAuction(String auctionId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .body(body)
                        .post("/api/v1/admin/auctions/" + auctionId + "/reject")
        );
    }

    @When("an admin cancels auction {string} with body:")
    public void adminCancelsAuction(String auctionId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .body(body)
                        .post("/api/v1/admin/auctions/" + auctionId + "/cancel")
        );
    }

    @When("an admin force-closes auction {string}")
    public void adminForceClosesAuction(String auctionId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .post("/api/v1/admin/auctions/" + auctionId + "/force-close")
        );
    }

    @When("a non-admin user lists all auctions")
    public void nonAdminListsAllAuctions() {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", "550e8400-e29b-41d4-a716-446655440001")
                        .header("X-User-Roles", "USER")
                        .get("/api/v1/admin/auctions")
        );
    }
}
```

- [ ] **Step 3: Run full test suite — expect all 27 scenarios pass (20 prior + 7 new)**

```bash
mvn test -pl auction-service -Dtest=CucumberTest -DfailIfNoTests=false
```
Expected: 27 scenarios pass. Check scenario output if any fail. Common issues:
- If admin reject/cancel/force-close returns 404: verify the ACTIVE/SCHEDULED UUIDs match `test-data.sql` exactly
- If the `@Before` reset didn't run: ensure `SellerAuctionSteps` is in the glue path (`com.bidnow.auction.bdd`)

- [ ] **Step 4: Run existing unit tests to confirm no regressions**

```bash
mvn test -pl auction-service -Dtest="AuctionControllerTest,AuctionServiceImplTest,AdminAuctionServiceImplTest,AuctionClosureServiceTest,AuctionClosureJobTest,AuctionStartupRecoveryServiceTest"
```
Expected: all existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/AdminAuctionSteps.java \
        backend/auction-service/src/test/resources/features/admin-moderation.feature
git commit -m "feat(bdd): add admin-moderation BDD scenarios"
```
