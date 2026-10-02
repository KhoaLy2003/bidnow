package com.bidnow.wallet.repository;

import com.bidnow.bdd.container.PostgresContainerSupport;
import com.bidnow.wallet.domain.entity.PaymentHold;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.domain.enums.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reminder query against real Postgres + the Liquibase schema. Needs Docker; run explicitly:
 * mvn -q -pl wallet-service -am test -Dtest=PaymentHoldReminderQueryPostgresIT -Dsurefire.failIfNoSpecifiedTests=false
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentHoldReminderQueryPostgresIT {

    /** JPA-only context: keeps WalletApplication's component scan (Kafka, security config) out of the slice. */
    @SpringBootConfiguration
    @EntityScan(basePackageClasses = PaymentHold.class)
    @EnableJpaRepositories(basePackageClasses = PaymentHoldRepository.class)
    static class JpaOnly {
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 12, 0);
    private static final LocalDateTime REMIND_BEFORE = NOW.plusHours(24);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private PaymentHoldRepository repository;

    private UUID walletId;

    @BeforeEach
    void winnerWallet() {
        walletId = em.persistAndFlush(Wallet.builder().userId(UUID.randomUUID())
                .totalBalance(BigDecimal.ZERO).availableBalance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO)
                .currency("USD").status(WalletStatus.ACTIVE).build()).getId();
    }

    private UUID hold(LocalDateTime deadline, PaymentHoldStatus status, LocalDateTime reminderSentAt) {
        UUID auctionId = UUID.randomUUID();
        em.persistAndFlush(PaymentHold.builder()
                .auctionId(auctionId).winnerWalletId(walletId).winnerUserId(UUID.randomUUID())
                .sellerUserId(UUID.randomUUID()).totalAmount(new BigDecimal("100.00"))
                .depositApplied(new BigDecimal("10.00")).remainingAmount(new BigDecimal("90.00"))
                .fundsHeld(true).status(status).deadline(deadline).reminderSentAt(reminderSentAt)
                .build());
        return auctionId;
    }

    private List<UUID> due(int limit) {
        return repository.findDueForReminderAuctionIds(PaymentHoldStatus.PENDING_PAYMENT, NOW, REMIND_BEFORE,
                PageRequest.of(0, limit));
    }

    @Test
    void due_isDeadlineWithinTheNext24Hours_earliestFirst() {
        UUID atBoundary = hold(REMIND_BEFORE, PaymentHoldStatus.PENDING_PAYMENT, null);
        UUID soon = hold(NOW.plusHours(1), PaymentHoldStatus.PENDING_PAYMENT, null);
        hold(REMIND_BEFORE.plusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT, null); // 24h01m left: not yet
        hold(NOW.minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT, null);          // expired: forfeit's job

        assertThat(due(100)).containsExactly(soon, atBoundary);
    }

    @Test
    void remindedOrClosedHolds_areNotDue() {
        hold(NOW.plusHours(1), PaymentHoldStatus.PENDING_PAYMENT, NOW.minusHours(1));
        hold(NOW.plusHours(1), PaymentHoldStatus.COMPLETED, null);
        hold(NOW.plusHours(1), PaymentHoldStatus.FORFEITED, null);
        hold(NOW.plusHours(1), PaymentHoldStatus.CANCELLED, null);

        assertThat(due(100)).isEmpty();
    }

    @Test
    void pageSize_limitsTheBatch() {
        hold(NOW.plusHours(1), PaymentHoldStatus.PENDING_PAYMENT, null);
        hold(NOW.plusHours(2), PaymentHoldStatus.PENDING_PAYMENT, null);
        hold(NOW.plusHours(3), PaymentHoldStatus.PENDING_PAYMENT, null);

        assertThat(due(2)).hasSize(2);
    }
}
