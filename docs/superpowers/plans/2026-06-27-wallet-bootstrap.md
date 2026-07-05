# Wallet Service Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap the wallet-service with a PostgreSQL schema, Kafka-driven wallet auto-creation on user registration, a `GET /api/v1/wallet` endpoint, and a startup platform wallet initializer.

**Architecture:** A Kafka consumer listens to `user-registered-topic` and calls `WalletService.createWalletIfAbsent`; idempotency is enforced by a `findByUserId` guard backed by a DB `UNIQUE` constraint. `WalletInitializer` seeds the platform's system wallet on startup using the same idempotent method. `WalletController` exposes a single authenticated GET endpoint returning the caller's wallet.

**Tech Stack:** Spring Boot 3.2.4, Spring Data JPA (PostgreSQL), Spring Kafka, Spring Security, Liquibase, JUnit 5 + Mockito

## Global Constraints

- All dependencies (web, jpa, security, kafka, liquibase, postgresql) are already transitively available via the `common` module — do not add them to `wallet-service/pom.xml`
- Database schema is managed exclusively by Liquibase — never use `ddl-auto: create` or `update`
- Use `NotFoundException` from `com.bidnow.common.exception.NotFoundException` — do not create a service-specific exception class
- Use `ErrorCodes.NOT_FOUND` from `com.bidnow.common.constant.ErrorCodes` as the error code string
- All API responses must be wrapped in `BaseResponse` from `com.bidnow.common.dto.BaseResponse`; use `BaseResponse.success(data)` static factory
- `@AuthenticatedUserId` from `com.bidnow.common.annotation.AuthenticatedUserId` injects the user UUID from the `X-User-Id` header set by the API gateway
- Kafka consumer group ID: `wallet-service-group`; topic: `user-registered-topic`
- `NotFoundException` constructor signature: `NotFoundException(String message, String errorCode)`

---

### Task 1: Database Migration & application.yml

**Files:**
- Create: `backend/wallet-service/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `backend/wallet-service/src/main/resources/db/changelog/migrations/01-init-wallets.sql`
- Modify: `backend/wallet-service/src/main/resources/application.yml`

**Interfaces:**
- Produces: `wallets` table with columns `id`, `user_id`, `total_balance`, `available_balance`, `locked_balance`, `currency`, `status`, `created_at`, `updated_at`; Kafka consumer config; `wallet.platform-user-id` property

- [ ] **Step 1: Create Liquibase master changelog**

Create `backend/wallet-service/src/main/resources/db/changelog/db.changelog-master.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <include file="db/changelog/migrations/01-init-wallets.sql"/>

</databaseChangeLog>
```

- [ ] **Step 2: Create the wallets migration**

Create `backend/wallet-service/src/main/resources/db/changelog/migrations/01-init-wallets.sql`:

```sql
-- liquibase formatted sql

-- changeset bidnow:wallet_001
CREATE TABLE wallets
(
    id                UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id           UUID UNIQUE  NOT NULL,
    total_balance     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    locked_balance    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency          VARCHAR(3)   NOT NULL DEFAULT 'USD',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_balances_non_negative
        CHECK (total_balance >= 0 AND available_balance >= 0 AND locked_balance >= 0),
    CONSTRAINT chk_balance_invariant
        CHECK (total_balance = available_balance + locked_balance)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);
```

- [ ] **Step 3: Update application.yml**

Replace `backend/wallet-service/src/main/resources/application.yml` with:

```yaml
server:
  port: 8085
  forward-headers-strategy: framework

spring:
  application:
    name: wallet-service
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
    enabled: true
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    template:
      observation-enabled: true
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: wallet-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
        reconnect.backoff.ms: 5000
        reconnect.backoff.max.ms: 30000
    listener:
      observation-enabled: true

wallet:
  platform-user-id: ${PLATFORM_USER_ID}

eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

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

springdoc:
  api-docs:
    path: /api/v1/wallets/v3/api-docs
  swagger-ui:
    enabled: false
```

- [ ] **Step 4: Commit**

```bash
git add backend/wallet-service/src/main/resources/
git commit -m "feat(wallet): add wallets migration and kafka/wallet config"
```

---

### Task 2: Domain Layer — Enum, Entity, Repository

**Files:**
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/domain/enums/WalletStatus.java`
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/domain/entity/Wallet.java`
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/repository/WalletRepository.java`

**Interfaces:**
- Consumes: `wallets` table from Task 1; `BaseEntity` from `com.bidnow.common.entity.BaseEntity`
- Produces:
  - `WalletStatus` enum: `ACTIVE`, `SUSPENDED`
  - `Wallet` entity fields: `id (UUID)`, `userId (UUID)`, `totalBalance (BigDecimal)`, `availableBalance (BigDecimal)`, `lockedBalance (BigDecimal)`, `currency (String)`, `status (WalletStatus)`
  - `WalletRepository.findByUserId(UUID userId): Optional<Wallet>`

- [ ] **Step 1: Create WalletStatus enum**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/domain/enums/WalletStatus.java`:

```java
package com.bidnow.wallet.domain.enums;

public enum WalletStatus {
    ACTIVE,
    SUSPENDED
}
```

- [ ] **Step 2: Create Wallet entity**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/domain/entity/Wallet.java`:

```java
package com.bidnow.wallet.domain.entity;

import com.bidnow.common.entity.BaseEntity;
import com.bidnow.wallet.domain.enums.WalletStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(name = "total_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBalance;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "locked_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal lockedBalance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletStatus status;
}
```

- [ ] **Step 3: Create WalletRepository**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/repository/WalletRepository.java`:

```java
package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByUserId(UUID userId);
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/wallet-service/src/main/java/com/bidnow/wallet/domain/ backend/wallet-service/src/main/java/com/bidnow/wallet/repository/
git commit -m "feat(wallet): add Wallet entity, WalletStatus enum, and WalletRepository"
```

---

### Task 3: Service Layer

**Files:**
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/dto/response/WalletResponse.java`
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java`
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java`
- Create: `backend/wallet-service/src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java`

**Interfaces:**
- Consumes: `WalletRepository.findByUserId(UUID): Optional<Wallet>`, `WalletRepository.save(Wallet): Wallet`, `WalletStatus.ACTIVE`, `NotFoundException(String message, String errorCode)`, `ErrorCodes.NOT_FOUND`
- Produces:
  - `WalletService.createWalletIfAbsent(UUID userId): void`
  - `WalletService.getMyWallet(UUID userId): WalletResponse`
  - `WalletResponse` fields: `totalBalance (BigDecimal)`, `availableBalance (BigDecimal)`, `lockedBalance (BigDecimal)`, `currency (String)`, `status (String)`

- [ ] **Step 1: Create WalletResponse DTO**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/dto/response/WalletResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WalletResponse {
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private String currency;
    private String status;
}
```

- [ ] **Step 2: Create WalletService interface**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/service/WalletService.java`:

```java
package com.bidnow.wallet.service;

import com.bidnow.wallet.dto.response.WalletResponse;

import java.util.UUID;

public interface WalletService {
    void createWalletIfAbsent(UUID userId);
    WalletResponse getMyWallet(UUID userId);
}
```

- [ ] **Step 3: Write failing tests**

Create `backend/wallet-service/src/test/java/com/bidnow/wallet/service/impl/WalletServiceImplTest.java`:

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void createWalletIfAbsent_walletAlreadyExists_doesNotSave() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(new Wallet()));

        walletService.createWalletIfAbsent(userId);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void createWalletIfAbsent_noWallet_savesWithZeroBalancesAndActiveStatus() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.createWalletIfAbsent(userId);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        Wallet saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTotalBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getLockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void getMyWallet_walletFound_returnsMappedResponse() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .totalBalance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("80.00"))
                .lockedBalance(new BigDecimal("20.00"))
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getMyWallet(userId);

        assertThat(response.getTotalBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(response.getLockedBalance()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getMyWallet_walletNotFound_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getMyWallet(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(userId.toString());
    }
}
```

- [ ] **Step 4: Run tests to confirm they fail**

```bash
mvn test -pl wallet-service -Dtest=WalletServiceImplTest
```

Expected: FAIL — `WalletServiceImpl` does not exist yet.

- [ ] **Step 5: Implement WalletServiceImpl**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/service/impl/WalletServiceImpl.java`:

```java
package com.bidnow.wallet.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public void createWalletIfAbsent(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            log.debug("Wallet already exists for userId={}, skipping creation", userId);
            return;
        }
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        walletRepository.save(wallet);
        log.info("Created wallet for userId={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));
        return WalletResponse.builder()
                .totalBalance(wallet.getTotalBalance())
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .build();
    }
}
```

- [ ] **Step 6: Run tests to confirm they pass**

```bash
mvn test -pl wallet-service -Dtest=WalletServiceImplTest
```

Expected: All 4 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/wallet-service/src/main/java/com/bidnow/wallet/dto/ backend/wallet-service/src/main/java/com/bidnow/wallet/service/ backend/wallet-service/src/test/java/com/bidnow/wallet/service/
git commit -m "feat(wallet): implement WalletService with idempotent wallet creation and balance retrieval"
```

---

### Task 4: Kafka Consumer

**Files:**
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/kafka/UserRegisteredEventConsumer.java`
- Create: `backend/wallet-service/src/test/java/com/bidnow/wallet/kafka/UserRegisteredEventConsumerTest.java`

**Interfaces:**
- Consumes: `WalletService.createWalletIfAbsent(UUID userId)`, `UserRegisteredEvent` from `com.bidnow.common.dto.event.UserRegisteredEvent` (fields: `userId (UUID)`, `email (String)`)
- Produces: Kafka `@KafkaListener` on topic `user-registered-topic`, group `wallet-service-group`

- [ ] **Step 1: Write failing test**

Create `backend/wallet-service/src/test/java/com/bidnow/wallet/kafka/UserRegisteredEventConsumerTest.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegisteredEventConsumerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private UserRegisteredEventConsumer consumer;

    @Test
    void consumeUserRegistered_delegatesToWalletService() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(userId)
                .email("test@example.com")
                .build();

        consumer.consumeUserRegistered(event);

        verify(walletService).createWalletIfAbsent(userId);
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
mvn test -pl wallet-service -Dtest=UserRegisteredEventConsumerTest
```

Expected: FAIL — `UserRegisteredEventConsumer` does not exist yet.

- [ ] **Step 3: Implement UserRegisteredEventConsumer**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/kafka/UserRegisteredEventConsumer.java`:

```java
package com.bidnow.wallet.kafka;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.dto.event.UserRegisteredEvent;
import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Loggable
public class UserRegisteredEventConsumer {

    private final WalletService walletService;

    @Transactional
    @KafkaListener(topics = "user-registered-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for userId={}", event.getUserId());
        walletService.createWalletIfAbsent(event.getUserId());
    }
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
mvn test -pl wallet-service -Dtest=UserRegisteredEventConsumerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/wallet-service/src/main/java/com/bidnow/wallet/kafka/ backend/wallet-service/src/test/java/com/bidnow/wallet/kafka/
git commit -m "feat(wallet): add Kafka consumer for user-registered-topic"
```

---

### Task 5: Startup Initializer

**Files:**
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/initializer/WalletInitializer.java`
- Create: `backend/wallet-service/src/test/java/com/bidnow/wallet/initializer/WalletInitializerTest.java`

**Interfaces:**
- Consumes: `WalletService.createWalletIfAbsent(UUID userId)`, `ApplicationReadyEvent` from `org.springframework.boot.context.event.ApplicationReadyEvent`; property `wallet.platform-user-id` (String UUID)
- Produces: `WalletInitializer.initPlatformWallet()` triggered on `ApplicationReadyEvent`

- [ ] **Step 1: Write failing test**

Create `backend/wallet-service/src/test/java/com/bidnow/wallet/initializer/WalletInitializerTest.java`:

```java
package com.bidnow.wallet.initializer;

import com.bidnow.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletInitializerTest {

    @Mock
    private WalletService walletService;

    private WalletInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new WalletInitializer(walletService);
    }

    @Test
    void initPlatformWallet_delegatesToWalletServiceWithConfiguredUUID() {
        UUID platformUserId = UUID.randomUUID();
        ReflectionTestUtils.setField(initializer, "platformUserId", platformUserId.toString());

        initializer.initPlatformWallet();

        verify(walletService).createWalletIfAbsent(platformUserId);
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
mvn test -pl wallet-service -Dtest=WalletInitializerTest
```

Expected: FAIL — `WalletInitializer` does not exist yet.

- [ ] **Step 3: Implement WalletInitializer**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/initializer/WalletInitializer.java`:

```java
package com.bidnow.wallet.initializer;

import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletInitializer {

    private final WalletService walletService;

    @Value("${wallet.platform-user-id}")
    private String platformUserId;

    @EventListener(ApplicationReadyEvent.class)
    public void initPlatformWallet() {
        UUID id = UUID.fromString(platformUserId);
        log.info("Seeding platform wallet for userId={}", id);
        walletService.createWalletIfAbsent(id);
        log.info("Platform wallet seed complete");
    }
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
mvn test -pl wallet-service -Dtest=WalletInitializerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/wallet-service/src/main/java/com/bidnow/wallet/initializer/ backend/wallet-service/src/test/java/com/bidnow/wallet/initializer/
git commit -m "feat(wallet): add WalletInitializer to seed platform wallet on startup"
```

---

### Task 6: REST Controller & Security

**Files:**
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/config/SecurityConfig.java`
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/controller/WalletController.java`
- Create: `backend/wallet-service/src/test/java/com/bidnow/wallet/controller/WalletControllerTest.java`

**Interfaces:**
- Consumes: `WalletService.getMyWallet(UUID userId): WalletResponse`, `@AuthenticatedUserId` from `com.bidnow.common.annotation.AuthenticatedUserId`, `BaseResponse.success(T data)`, `RoleHeaderFilter` from `com.bidnow.common.security.RoleHeaderFilter`, `SecurityConstants.PUBLIC_ENDPOINTS` from `com.bidnow.common.constant.SecurityConstants`
- Produces: `GET /api/v1/wallet` → `200 BaseResponse<WalletResponse>`; `NotFoundException` propagates to `GlobalExceptionHandler` as `404`

- [ ] **Step 1: Create SecurityConfig**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/config/SecurityConfig.java`:

```java
package com.bidnow.wallet.config;

import com.bidnow.common.constant.SecurityConstants;
import com.bidnow.common.security.RoleHeaderFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityConstants.PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(new RoleHeaderFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 2: Write failing tests**

Create `backend/wallet-service/src/test/java/com/bidnow/wallet/controller/WalletControllerTest.java`:

```java
package com.bidnow.wallet.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController walletController;

    @Test
    void getMyWallet_walletExists_returns200WithWalletData() {
        UUID userId = UUID.randomUUID();
        WalletResponse walletResponse = WalletResponse.builder()
                .totalBalance(new BigDecimal("50.00"))
                .availableBalance(new BigDecimal("50.00"))
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status("ACTIVE")
                .build();
        when(walletService.getMyWallet(userId)).thenReturn(walletResponse);

        ResponseEntity<BaseResponse<WalletResponse>> response = walletController.getMyWallet(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getTotalBalance())
                .isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.getBody().getData().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getMyWallet_walletNotFound_propagatesNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(walletService.getMyWallet(userId)).thenThrow(
                new NotFoundException("Wallet not found for userId: " + userId, "NOT_FOUND"));

        assertThatThrownBy(() -> walletController.getMyWallet(userId))
                .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 3: Run tests to confirm they fail**

```bash
mvn test -pl wallet-service -Dtest=WalletControllerTest
```

Expected: FAIL — `WalletController` does not exist yet.

- [ ] **Step 4: Implement WalletController**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/controller/WalletController.java`:

```java
package com.bidnow.wallet.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<BaseResponse<WalletResponse>> getMyWallet(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(walletService.getMyWallet(userId)));
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
mvn test -pl wallet-service -Dtest=WalletControllerTest
```

Expected: All 2 tests PASS.

- [ ] **Step 6: Run all wallet-service tests**

```bash
mvn test -pl wallet-service
```

Expected: All 8 tests PASS (4 service + 1 consumer + 1 initializer + 2 controller).

- [ ] **Step 7: Commit**

```bash
git add backend/wallet-service/src/main/java/com/bidnow/wallet/config/ backend/wallet-service/src/main/java/com/bidnow/wallet/controller/ backend/wallet-service/src/test/java/com/bidnow/wallet/controller/
git commit -m "feat(wallet): add WalletController and SecurityConfig"
```
