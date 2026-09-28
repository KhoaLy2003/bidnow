package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.RefundReason;
import com.bidnow.wallet.exception.DepositReleaseException;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.DepositLockService;
import com.bidnow.wallet.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleEventConsumerTest {

    @Mock
    private DepositLockRepository depositLockRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DepositLockService depositLockService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private AuctionLifecycleEventConsumer consumer;

    private final UUID auctionId = UUID.randomUUID();
    private final UUID winnerUserId = UUID.randomUUID();
    private final UUID winnerWalletId = UUID.randomUUID();
    private final UUID walletA = UUID.randomUUID();
    private final UUID walletB = UUID.randomUUID();

    private DepositLock lockFor(UUID walletId) {
        return DepositLock.builder()
                .id(UUID.randomUUID())
                .walletId(walletId)
                .auctionId(auctionId)
                .amount(new BigDecimal("50.00"))
                .status(DepositLockStatus.LOCKED)
                .build();
    }

    private AuctionEndedEvent ended(UUID winnerId, List<UUID> loserIds) {
        return AuctionEndedEvent.builder().auctionId(auctionId).winnerId(winnerId).loserIds(loserIds).build();
    }

    @Test
    void onAuctionEnded_withWinner_releasesLosersOnly() {
        DepositLock winnerLock = lockFor(winnerWalletId);
        DepositLock lockA = lockFor(walletA);
        DepositLock lockB = lockFor(walletB);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(winnerLock, lockA, lockB));
        when(walletRepository.findByUserId(winnerUserId))
                .thenReturn(Optional.of(Wallet.builder().id(winnerWalletId).userId(winnerUserId).build()));

        consumer.onAuctionEnded(ended(winnerUserId, List.of()));

        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_LOST);
        verify(depositLockService).releaseDeposit(lockB.getId(), walletB, RefundReason.AUCTION_LOST);
        verify(depositLockService, never()).releaseDeposit(eq(winnerLock.getId()), any(), any());
        verify(depositLockService, times(2)).releaseDeposit(any(), any(), any());
    }

    @Test
    void onAuctionEnded_noWinner_releasesAllLocks() {
        DepositLock lockA = lockFor(walletA);
        DepositLock lockB = lockFor(walletB);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA, lockB));

        consumer.onAuctionEnded(ended(null, null));

        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_LOST);
        verify(depositLockService).releaseDeposit(lockB.getId(), walletB, RefundReason.AUCTION_LOST);
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void onAuctionEnded_ignoresLoserIdsAndUsesDepositLocks() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));

        consumer.onAuctionEnded(ended(null, List.of(UUID.randomUUID(), UUID.randomUUID())));

        verify(depositLockService, times(1)).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_LOST);
    }

    @Test
    void onAuctionEnded_noLocks_doesNothing() {
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of());

        consumer.onAuctionEnded(ended(winnerUserId, List.of()));

        verifyNoInteractions(depositLockService);
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void onAuctionCancelled_releasesAllLocksWithCancelledReason() {
        DepositLock lockA = lockFor(walletA);
        DepositLock lockB = lockFor(walletB);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA, lockB));

        consumer.onAuctionCancelled(AuctionCancelledEvent.builder().auctionId(auctionId).build());

        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_CANCELLED);
        verify(depositLockService).releaseDeposit(lockB.getId(), walletB, RefundReason.AUCTION_CANCELLED);
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void oneReleaseFails_othersStillReleased_thenThrowsWithFailedLockIds() {
        DepositLock lockA = lockFor(walletA);
        DepositLock lockB = lockFor(walletB);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA, lockB));
        doThrow(new RuntimeException("db down"))
                .when(depositLockService).releaseDeposit(eq(lockA.getId()), any(), any());

        assertThatThrownBy(() -> consumer.onAuctionCancelled(
                AuctionCancelledEvent.builder().auctionId(auctionId).build()))
                .isInstanceOfSatisfying(DepositReleaseException.class, ex -> {
                    assertThat(ex.getAuctionId()).isEqualTo(auctionId);
                    assertThat(ex.getFailedLockIds()).containsExactly(lockA.getId());
                });
        verify(depositLockService).releaseDeposit(lockB.getId(), walletB, RefundReason.AUCTION_CANCELLED);
    }

    // ── payment hold integration ──────────────────────────────────────────────

    private final UUID sellerUserId = UUID.randomUUID();

    private AuctionEndedEvent endedWithWinner() {
        return AuctionEndedEvent.builder()
                .auctionId(auctionId)
                .winnerId(winnerUserId)
                .sellerId(sellerUserId)
                .winningBidAmount(new BigDecimal("500.00"))
                .build();
    }

    @Test
    void onAuctionEnded_withWinner_createsPaymentHold() {
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of());

        consumer.onAuctionEnded(endedWithWinner());

        verify(paymentService).createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));
    }

    @Test
    void onAuctionEnded_noWinner_createsNoPaymentHold() {
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of());

        consumer.onAuctionEnded(ended(null, null));

        verifyNoInteractions(paymentService);
    }

    @Test
    void onAuctionEnded_releaseFails_stillCreatesHoldThenThrowsReleaseFailure() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));
        when(walletRepository.findByUserId(winnerUserId)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("db down"))
                .when(depositLockService).releaseDeposit(eq(lockA.getId()), any(), any());

        assertThatThrownBy(() -> consumer.onAuctionEnded(endedWithWinner()))
                .isInstanceOf(DepositReleaseException.class);
        verify(paymentService).createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));
    }

    @Test
    void onAuctionEnded_holdFails_releasesLosersThenRethrowsHoldFailure() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));
        when(walletRepository.findByUserId(winnerUserId)).thenReturn(Optional.empty());
        IllegalStateException holdFailure = new IllegalStateException("hold failed");
        doThrow(holdFailure).when(paymentService).createPaymentHold(any(), any(), any(), any());

        assertThatThrownBy(() -> consumer.onAuctionEnded(endedWithWinner())).isSameAs(holdFailure);
        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_LOST);
    }

    @Test
    void onAuctionCancelled_cancelsPaymentHold() {
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of());

        consumer.onAuctionCancelled(AuctionCancelledEvent.builder().auctionId(auctionId).build());

        verify(paymentService).cancelPaymentHold(auctionId);
    }

    @Test
    void onAuctionCancelled_voidsHoldBeforeReleasingDeposits() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));

        consumer.onAuctionCancelled(AuctionCancelledEvent.builder().auctionId(auctionId).build());

        InOrder order = inOrder(paymentService, depositLockService);
        order.verify(paymentService).cancelPaymentHold(auctionId);
        order.verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_CANCELLED);
    }

    @Test
    void onAuctionCancelled_holdCancelFails_releasesDepositsThenRethrows() {
        DepositLock lockA = lockFor(walletA);
        when(depositLockRepository.findByAuctionIdAndStatus(auctionId, DepositLockStatus.LOCKED))
                .thenReturn(List.of(lockA));
        IllegalStateException cancelFailure = new IllegalStateException("cancel failed");
        doThrow(cancelFailure).when(paymentService).cancelPaymentHold(auctionId);

        assertThatThrownBy(() -> consumer.onAuctionCancelled(
                AuctionCancelledEvent.builder().auctionId(auctionId).build())).isSameAs(cancelFailure);
        verify(depositLockService).releaseDeposit(lockA.getId(), walletA, RefundReason.AUCTION_CANCELLED);
    }
}
