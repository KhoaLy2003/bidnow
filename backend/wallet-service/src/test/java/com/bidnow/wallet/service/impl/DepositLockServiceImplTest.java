package com.bidnow.wallet.service.impl;

import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.RefundReason;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.kafka.DepositRefundedApplicationEvent;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositLockServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DepositLockRepository depositLockRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DepositLockServiceImpl depositLockService;

    private UUID userId;
    private UUID walletId;
    private UUID auctionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        auctionId = UUID.randomUUID();
    }

    private Wallet wallet(String total, String available, String locked, WalletStatus status) {
        return Wallet.builder()
                .id(walletId)
                .userId(userId)
                .totalBalance(new BigDecimal(total))
                .availableBalance(new BigDecimal(available))
                .lockedBalance(new BigDecimal(locked))
                .currency("USD")
                .status(status)
                .build();
    }

    private DepositLock lock(String amount, DepositLockStatus status) {
        return DepositLock.builder()
                .id(UUID.randomUUID())
                .walletId(walletId)
                .auctionId(auctionId)
                .amount(new BigDecimal(amount))
                .status(status)
                .lockedAt(LocalDateTime.of(2026, 9, 28, 10, 0))
                .build();
    }

    private DepositLockRequest request(String amount) {
        return new DepositLockRequest(userId, auctionId, new BigDecimal(amount));
    }

    // ── getDepositLock ────────────────────────────────────────────────────────

    @Test
    void getDepositLock_noRow_returnsNotLocked() {
        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());

        DepositLockStatusResponse result = depositLockService.getDepositLock(userId, auctionId);

        assertThat(result.isLocked()).isFalse();
        assertThat(result.getAmount()).isNull();
        assertThat(result.getStatus()).isNull();
    }

    @Test
    void getDepositLock_lockedRow_returnsLockedWithAmount() {
        DepositLock existing = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.of(existing));

        DepositLockStatusResponse result = depositLockService.getDepositLock(userId, auctionId);

        assertThat(result.isLocked()).isTrue();
        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getStatus()).isEqualTo("LOCKED");
        assertThat(result.getLockedAt()).isEqualTo(existing.getLockedAt());
    }

    @Test
    void getDepositLock_releasedRow_returnsNotLockedWithStatus() {
        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId))
                .thenReturn(Optional.of(lock("50.00", DepositLockStatus.RELEASED)));

        DepositLockStatusResponse result = depositLockService.getDepositLock(userId, auctionId);

        assertThat(result.isLocked()).isFalse();
        assertThat(result.getStatus()).isEqualTo("RELEASED");
    }

    @Test
    void getDepositLock_walletMissing_throwsNotFound() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositLockService.getDepositLock(userId, auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.WALLET_NOT_FOUND);
    }

    // ── lockDeposit ───────────────────────────────────────────────────────────

    @Test
    void lockDeposit_firstBid_movesFundsAndRecordsHoldAndLock() {
        Wallet w = wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());
        when(depositLockRepository.saveAndFlush(any(DepositLock.class))).thenAnswer(inv -> {
            DepositLock saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        DepositLockResponse result = depositLockService.lockDeposit(request("50.00"));

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("50.00");
        assertThat(w.getTotalBalance()).isEqualByComparingTo("200.00");
        verify(walletRepository).save(w);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction hold = txCaptor.getValue();
        assertThat(hold.getWalletId()).isEqualTo(walletId);
        assertThat(hold.getType()).isEqualTo(TransactionType.HOLD);
        assertThat(hold.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(hold.getAmount()).isEqualByComparingTo("50.00");
        assertThat(hold.getAvailableBalanceBefore()).isEqualByComparingTo("200.00");
        assertThat(hold.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(hold.getReferenceId()).isEqualTo(auctionId);

        ArgumentCaptor<DepositLock> lockCaptor = ArgumentCaptor.forClass(DepositLock.class);
        verify(depositLockRepository).saveAndFlush(lockCaptor.capture());
        DepositLock savedLock = lockCaptor.getValue();
        assertThat(savedLock.getWalletId()).isEqualTo(walletId);
        assertThat(savedLock.getAuctionId()).isEqualTo(auctionId);
        assertThat(savedLock.getAmount()).isEqualByComparingTo("50.00");
        assertThat(savedLock.getStatus()).isEqualTo(DepositLockStatus.LOCKED);
        assertThat(savedLock.getLockedAt()).isNotNull();

        assertThat(result.isAlreadyLocked()).isFalse();
        assertThat(result.getLockId()).isEqualTo(savedLock.getId());
        assertThat(result.getStatus()).isEqualTo("LOCKED");
        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(result.getLockedBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void lockDeposit_takesRowLockAndNeverReadsWalletUnlocked() {
        Wallet w = wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());
        when(depositLockRepository.saveAndFlush(any(DepositLock.class))).thenAnswer(inv -> inv.getArgument(0));

        depositLockService.lockDeposit(request("50.00"));

        verify(walletRepository).findByUserIdForUpdate(userId);
        verify(walletRepository, never()).findByUserId(any());
    }

    @Test
    void lockDeposit_alreadyLocked_isIdempotentAndChangesNothing() {
        Wallet w = wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE);
        DepositLock existing = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.of(existing));

        DepositLockResponse result = depositLockService.lockDeposit(request("50.00"));

        assertThat(result.isAlreadyLocked()).isTrue();
        assertThat(result.getLockId()).isEqualTo(existing.getId());
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(result.getLockedBalance()).isEqualByComparingTo("50.00");
        assertThat(w.getAvailableBalance()).isEqualByComparingTo("150.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_insufficientBalance_throwsWithAmountsAndSavesNothing() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("30.00", "30.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOfSatisfying(InsufficientBalanceException.class, ex -> {
                    assertThat(ex.getAvailableBalance()).isEqualByComparingTo("30.00");
                    assertThat(ex.getRequired()).isEqualByComparingTo("50.00");
                });
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_walletMissing_throwsNotFound() {
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.WALLET_NOT_FOUND);
    }

    @Test
    void lockDeposit_zeroDeposit_insertsLockWithoutHoldOrBalanceChange() {
        Wallet w = wallet("0.00", "0.00", "0.00", WalletStatus.ACTIVE);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());
        when(depositLockRepository.saveAndFlush(any(DepositLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DepositLockResponse result = depositLockService.lockDeposit(request("0.00"));

        assertThat(result.isAlreadyLocked()).isFalse();
        assertThat(result.getAmount()).isEqualByComparingTo("0.00");

        ArgumentCaptor<DepositLock> lockCaptor = ArgumentCaptor.forClass(DepositLock.class);
        verify(depositLockRepository).saveAndFlush(lockCaptor.capture());
        DepositLock savedLock = lockCaptor.getValue();
        assertThat(savedLock.getAmount()).isEqualByComparingTo("0.00");
        assertThat(savedLock.getStatus()).isEqualTo(DepositLockStatus.LOCKED);
        assertThat(savedLock.getLockedAt()).isNotNull();

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(w.getTotalBalance()).isEqualByComparingTo("0.00");

        assertThat(result.getAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(result.getLockedBalance()).isEqualByComparingTo("0.00");

        verify(transactionRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void lockDeposit_releasedRow_throwsConflict() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId))
                .thenReturn(Optional.of(lock("50.00", DepositLockStatus.RELEASED)));

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.DEPOSIT_LOCK_CLOSED);
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_forfeitedRow_throwsConflict() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("150.00", "150.00", "0.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId))
                .thenReturn(Optional.of(lock("50.00", DepositLockStatus.FORFEITED)));

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.DEPOSIT_LOCK_CLOSED);
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_suspendedWalletWithoutLock_throwsForbidden() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("200.00", "200.00", "0.00", WalletStatus.SUSPENDED)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositLockService.lockDeposit(request("50.00")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(WalletErrorCodes.WALLET_NOT_ACTIVE);
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockDeposit_suspendedWalletWithExistingLock_stillReturnsAlreadyLocked() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("200.00", "150.00", "50.00", WalletStatus.SUSPENDED)));
        when(depositLockRepository.findByWalletIdAndAuctionId(walletId, auctionId))
                .thenReturn(Optional.of(lock("50.00", DepositLockStatus.LOCKED)));

        DepositLockResponse result = depositLockService.lockDeposit(request("50.00"));

        assertThat(result.isAlreadyLocked()).isTrue();
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).saveAndFlush(any());
    }

    // ── releaseDeposit ────────────────────────────────────────────────────────

    @Test
    void releaseDeposit_lockedLock_refundsMarksReleasedAndPublishesEvent() {
        Wallet w = wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE);
        DepositLock l = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST);

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("200.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(w.getTotalBalance()).isEqualByComparingTo("200.00");
        verify(walletRepository).save(w);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction refund = txCaptor.getValue();
        assertThat(refund.getWalletId()).isEqualTo(walletId);
        assertThat(refund.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(refund.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(refund.getAmount()).isEqualByComparingTo("50.00");
        assertThat(refund.getAvailableBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(refund.getAvailableBalanceAfter()).isEqualByComparingTo("200.00");
        assertThat(refund.getReferenceId()).isEqualTo(auctionId);
        assertThat(refund.getDescription()).contains(auctionId.toString()).contains("AUCTION_LOST");

        assertThat(l.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
        assertThat(l.getReleasedAt()).isNotNull();
        verify(depositLockRepository).save(l);

        ArgumentCaptor<DepositRefundedApplicationEvent> eventCaptor =
                ArgumentCaptor.forClass(DepositRefundedApplicationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DepositRefundedApplicationEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getWalletId()).isEqualTo(walletId);
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getReason()).isEqualTo(RefundReason.AUCTION_LOST);
        assertThat(event.getRefundedAt()).isNotNull();
    }

    @Test
    void releaseDeposit_readsWalletOnlyThroughRowLock() {
        DepositLock l = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_CANCELLED);

        verify(walletRepository).findByIdForUpdate(walletId);
        verify(walletRepository, never()).findById(any());
        verify(walletRepository, never()).findByUserId(any());

        InOrder order = inOrder(walletRepository, depositLockRepository);
        order.verify(walletRepository).findByIdForUpdate(walletId);
        order.verify(depositLockRepository).findById(l.getId());
    }

    @Test
    void releaseDeposit_alreadyReleased_isSilentNoOp() {
        Wallet w = wallet("200.00", "200.00", "0.00", WalletStatus.ACTIVE);
        DepositLock l = lock("50.00", DepositLockStatus.RELEASED);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST);

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("200.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_forfeited_isSilentNoOp() {
        when(walletRepository.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet("150.00", "150.00", "0.00", WalletStatus.ACTIVE)));
        DepositLock l = lock("50.00", DepositLockStatus.FORFEITED);
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST);

        assertThat(l.getStatus()).isEqualTo(DepositLockStatus.FORFEITED);
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_lockMissing_isSilentNoOp() {
        UUID lockId = UUID.randomUUID();
        when(walletRepository.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE)));
        when(depositLockRepository.findById(lockId)).thenReturn(Optional.empty());

        depositLockService.releaseDeposit(lockId, walletId, RefundReason.AUCTION_LOST);

        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_walletMissing_isSilentNoOp() {
        UUID lockId = UUID.randomUUID();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.empty());

        depositLockService.releaseDeposit(lockId, walletId, RefundReason.AUCTION_LOST);

        verify(depositLockRepository, never()).findById(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_zeroAmount_releasesWithoutTransactionOrEvent() {
        Wallet w = wallet("0.00", "0.00", "0.00", WalletStatus.ACTIVE);
        DepositLock l = lock("0.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST);

        assertThat(l.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
        assertThat(l.getReleasedAt()).isNotNull();
        verify(depositLockRepository).save(l);
        assertThat(w.getAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseDeposit_suspendedWallet_isStillRefunded() {
        Wallet w = wallet("200.00", "150.00", "50.00", WalletStatus.SUSPENDED);
        DepositLock l = lock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_CANCELLED);

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("200.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(l.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
    }

    @Test
    void releaseDeposit_lockBelongsToAnotherWallet_throwsAndChangesNothing() {
        Wallet w = wallet("200.00", "150.00", "50.00", WalletStatus.ACTIVE);
        DepositLock l = DepositLock.builder()
                .id(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .auctionId(auctionId)
                .amount(new BigDecimal("50.00"))
                .status(DepositLockStatus.LOCKED)
                .lockedAt(LocalDateTime.of(2026, 9, 28, 10, 0))
                .build();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(w));
        when(depositLockRepository.findById(l.getId())).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> depositLockService.releaseDeposit(l.getId(), walletId, RefundReason.AUCTION_LOST))
                .isInstanceOf(IllegalStateException.class);

        assertThat(w.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(w.getLockedBalance()).isEqualByComparingTo("50.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(depositLockRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
