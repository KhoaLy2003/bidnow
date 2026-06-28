# BDD Testing — BidNow Backend

Behaviour-Driven Development tests for BidNow microservices using **Cucumber 7.18**, **Testcontainers**, **WireMock**,
and **RestAssured**. Each service boots as a full Spring Boot application against a real PostgreSQL container; HTTP
calls go through RestAssured against the live server port.

## Module structure

```
backend/
├── bdd-support/                          # Shared BDD infrastructure (this module)
│   └── src/main/java/com/bidnow/bdd/
│       ├── client/BddRestClient.java     # RestAssured wrapper pre-configured with server port
│       ├── config/BddSupportAutoConfiguration.java  # Spring Boot auto-config entry point
│       ├── container/
│       │   ├── PostgresContainerSupport.java  # Singleton Postgres (shared across tests)
│       │   └── KafkaContainerSupport.java     # Singleton Kafka (shared across tests)
│       ├── context/ScenarioContext.java   # Per-scenario state (last response, auth token)
│       └── wiremock/WireMockSupport.java  # Singleton WireMock server
│
├── identity-service/src/test/
│   ├── java/com/bidnow/identity/bdd/
│   │   ├── CucumberTest.java             # @Suite runner
│   │   ├── config/CucumberSpringConfig.java  # Spring context + @DynamicPropertySource
│   │   └── steps/
│   │       ├── AuthSteps.java            # register / OTP / login step definitions
│   │       └── CommonSteps.java          # shared Then assertions
│   └── resources/
│       ├── application-bdd.yml           # BDD-profile overrides (no Eureka, test JWT secret)
│       └── features/
│           ├── register.feature          # 4 scenarios
│           ├── otp.feature               # 3 scenarios
│           └── login.feature             # 3 scenarios
│
└── user-service/src/test/
    ├── java/com/bidnow/user/bdd/
    │   ├── CucumberTest.java
    │   ├── config/CucumberSpringConfig.java
    │   └── steps/
    │       ├── UserProfileSteps.java
    │       └── CommonSteps.java
    └── resources/
        ├── application-bdd.yml
        └── features/
            ├── get-profile.feature       # 3 scenarios
            └── update-profile.feature    # 2 scenarios
```

## Running the tests

```bash
# From backend/ — run all BDD tests in a single service
mvn test -pl identity-service -Dtest=CucumberTest
mvn test -pl user-service -Dtest=CucumberTest

# Run all services
mvn test -pl identity-service,user-service -Dtest=CucumberTest

# Run the full backend build including BDD tests
mvn verify
```

HTML reports are generated after each run:

```
identity-service/target/cucumber-reports/report.html
user-service/target/cucumber-reports/report.html
```

## Test coverage

| Service          | Feature          | Scenarios |
|------------------|------------------|-----------|
| identity-service | Registration     | 4         |
| identity-service | OTP Verification | 3         |
| identity-service | Login            | 3         |
| user-service     | Get Profile      | 3         |
| user-service     | Update Profile   | 2         |
| **Total**        |                  | **15**    |

## How it works

### Infrastructure (singleton containers)

Testcontainers containers are started once per JVM via static fields in `PostgresContainerSupport` and
`KafkaContainerSupport`. Ryuk (the Testcontainers reaper) cleans them up on JVM exit — no explicit teardown code is
needed.

`WireMockSupport` follows the same singleton pattern. `WireMockSupport.reset()` is called in each step class's `@Before`
hook to clear recorded interactions and re-register stubs between scenarios.

### Spring context wiring

Each service's `CucumberSpringConfig` is annotated `@CucumberContextConfiguration` and starts the full application
context with `@SpringBootTest(webEnvironment = RANDOM_PORT)`. The `@DynamicPropertySource` method overrides datasource
and Kafka bootstrap URLs to point at the running containers:

```java
@DynamicPropertySource
static void overrideProperties(DynamicPropertyRegistry registry) {
    PostgresContainerSupport.properties().forEach((k, v) -> registry.add(k, () -> v));
    KafkaContainerSupport.properties().forEach((k, v) -> registry.add(k, () -> v));
    // identity-service only:
    registry.add("spring.cloud.openfeign.client.config.user-service.url", WireMockSupport::baseUrl);
}
```

`bdd-support` beans (`BddRestClient`, `ScenarioContext`) are discovered via `BddSupportAutoConfiguration`, a Spring Boot
auto-configuration registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
This avoids having to add an extra `@ComponentScan` in each service's test config.

### BddRestClient

`BddRestClient` is `@Lazy` because `local.server.port` is not available until after the Spring context finishes
starting. The lazy proxy defers `@Value` injection to the first method call, by which time the port is set.

```java
RequestSpecification given() {
    return RestAssured.given()
        .baseUri("http://localhost")
        .port(port)
        .contentType(ContentType.JSON);
}
```

### ScenarioContext

`@ScenarioScope` (from `cucumber-spring`) means a new instance is created for each scenario and destroyed when it ends.
Steps classes share the same context instance within a scenario via constructor injection.

### Authentication in user-service tests

The production path injects `X-User-Id` and `X-User-Roles` headers at the API gateway. BDD tests replicate this by
injecting the headers directly:

```java
client.given()
    .header("X-User-Id", userId)
    .header("X-User-Roles", "USER")
    .get("/api/v1/users/profiles/me");
```

Unauthenticated scenarios omit `X-User-Id` entirely, which triggers the `authenticationEntryPoint` returning 401.

## Adding a new service

1. Add `bdd-support` as a test-scoped dependency in the service's `pom.xml`:
   ```xml
   <dependency>
       <groupId>com.bidnow</groupId>
       <artifactId>bdd-support</artifactId>
       <version>${project.version}</version>
       <scope>test</scope>
   </dependency>
   ```

2. Create `src/test/resources/application-bdd.yml` — disable Eureka, service discovery, and distributed tracing; set a
   valid Base64 JWT secret if the service issues tokens.

3. Create `CucumberSpringConfig` (annotated `@CucumberContextConfiguration @SpringBootTest @ActiveProfiles("bdd")`) with
   a `@DynamicPropertySource` that wires container URLs.

4. Create `CucumberTest` (annotated `@Suite @IncludeEngines("cucumber") @SelectClasspathResource("features")`) pointing
   glue at your BDD package.

5. Write `.feature` files under `src/test/resources/features/`.

6. Write step definition classes — **do not annotate them with `@Component`**; Cucumber 7.18's `SpringFactory` rejects
   it and will throw an error on startup.

## Key constraints

| Constraint                                                          | Reason                                                                                                    |
|---------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Step classes must NOT have `@Component`                             | Cucumber 7.18's `SpringFactory.checkNoComponentAnnotations()` rejects it                                  |
| JWT secret must be valid Base64                                     | `JwtService` calls `Decoders.BASE64.decode(secret)` on startup                                            |
| Use `wiremock-standalone`, not `wiremock`                           | Standalone bundles Jetty internally; bare `wiremock` conflicts with Spring Boot's Tomcat on the classpath |
| `@DynamicPropertySource` lambda: `(k, () -> v)` not `registry::add` | `add()` takes `(String, Supplier<Object>)`, not `(String, String)`                                        |
