package com.bidnow.wallet.service.impl;

import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.domain.entity.AuctionCancellation;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.PaymentHold;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;
import com.bidnow.wallet.kafka.PaymentApplicationEvent;
import com.bidnow.wallet.repository.AuctionCancellationRepository;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.PaymentHoldRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentHoldRepository paymentHoldRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DepositLockRepository depositLockRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuctionCancellationRepository auctionCancellationRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID auctionId;
    private UUID winnerUserId;
    private UUID sellerUserId;
    private UUID winnerWalletId;
    private UUID sellerWalletId;

    @BeforeEach
    void setUp() {
        auctionId = UUID.randomUUID();
        winnerUserId = UUID.randomUUID();
        sellerUserId = UUID.randomUUID();
        winnerWalletId = UUID.randomUUID();
        sellerWalletId = UUID.randomUUID();
    }

    private Wallet wallet(UUID id, UUID userId, String total, String available, String locked) {
        return Wallet.builder()
                .id(id)
                .userId(userId)
                .totalBalance(new BigDecimal(total))
                .availableBalance(new BigDecimal(available))
                .lockedBalance(new BigDecimal(locked))
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
    }

    private DepositLock depositLock(String amount, DepositLockStatus status) {
        return DepositLock.builder()
                .id(UUID.randomUUID())
                .walletId(winnerWalletId)
                .auctionId(auctionId)
                .amount(new BigDecimal(amount))
                .status(status)
                .build();
    }

    private PaymentHold hold(String total, String applied, String remaining, boolean fundsHeld,
                             UUID depositLockId, LocalDateTime deadline, PaymentHoldStatus status) {
        return PaymentHold.builder()
                .id(UUID.randomUUID())
                .auctionId(auctionId)
                .winnerWalletId(winnerWalletId)
                .winnerUserId(winnerUserId)
                .sellerUserId(sellerUserId)
                .totalAmount(new BigDecimal(total))
                .depositApplied(new BigDecimal(applied))
                .depositLockId(depositLockId)
                .remainingAmount(new BigDecimal(remaining))
                .fundsHeld(fundsHeld)
                .status(status)
                .deadline(deadline)
                .build();
    }

    private PaymentHold capturedHold() {
        ArgumentCaptor<PaymentHold> captor = ArgumentCaptor.forClass(PaymentHold.class);
        verify(paymentHoldRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private PaymentEvent capturedEvent() {
        ArgumentCaptor<PaymentApplicationEvent> captor = ArgumentCaptor.forClass(PaymentApplicationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue().getPayment();
    }

    // ── createPaymentHold ─────────────────────────────────────────────────────

    @Test
    void createPaymentHold_sufficientBalance_holdsRemainingAndRecordsHold() {
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "600.00", "50.00");
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.of(winner));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId)).thenReturn(Optional.of(lock));
        LocalDateTime before = LocalDateTime.now();

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("500.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("650.00");
        verify(walletRepository).save(winner);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(TransactionType.HOLD);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getWalletId()).isEqualTo(winnerWalletId);
        assertThat(tx.getAmount()).isEqualByComparingTo("450.00");
        assertThat(tx.getAvailableBalanceBefore()).isEqualByComparingTo("600.00");
        assertThat(tx.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(tx.getReferenceId()).isEqualTo(auctionId);

        PaymentHold saved = capturedHold();
        assertThat(saved.getAuctionId()).isEqualTo(auctionId);
        assertThat(saved.getWinnerWalletId()).isEqualTo(winnerWalletId);
        assertThat(saved.getWinnerUserId()).isEqualTo(winnerUserId);
        assertThat(saved.getSellerUserId()).isEqualTo(sellerUserId);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("500.00");
        assertThat(saved.getDepositApplied()).isEqualByComparingTo("50.00");
        assertThat(saved.getRemainingAmount()).isEqualByComparingTo("450.00");
        assertThat(saved.getDepositLockId()).isEqualTo(lock.getId());
        assertThat(saved.isFundsHeld()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(PaymentHoldStatus.PENDING_PAYMENT);
        assertThat(saved.getDeadline()).isBetween(before.plusHours(48).minusMinutes(1), before.plusHours(48).plusMinutes(1));

        PaymentEvent event = capturedEvent();
        assertThat(event.getPaymentType()).isEqualTo("REQUIRED");
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getUserId()).isEqualTo(winnerUserId);
        assertThat(event.getSellerId()).isEqualTo(sellerUserId);
        assertThat(event.getAmount()).isEqualByComparingTo("500.00");
        assertThat(event.getDepositAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getRemaining()).isEqualByComparingTo("450.00");
        assertThat(event.getDeadline()).isNotNull();
        assertThat(event.getInsufficientFunds()).isFalse();
    }

    @Test
    void createPaymentHold_insufficientBalance_createsUnfundedHoldWithoutMovingMoney() {
        Wallet winner = wallet(winnerWalletId, winnerUserId, "150.00", "100.00", "50.00");
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.of(winner));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId))
                .thenReturn(Optional.of(depositLock("50.00", DepositLockStatus.LOCKED)));

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("50.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        PaymentHold saved = capturedHold();
        assertThat(saved.isFundsHeld()).isFalse();
        assertThat(saved.getRemainingAmount()).isEqualByComparingTo("450.00");
        assertThat(capturedEvent().getInsufficientFunds()).isTrue();
    }

    @Test
    void createPaymentHold_depositCoversTotal_remainingZeroNoHoldTransaction() {
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "0.00", "600.00");
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.of(winner));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId))
                .thenReturn(Optional.of(depositLock("600.00", DepositLockStatus.LOCKED)));

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        PaymentHold saved = capturedHold();
        assertThat(saved.getDepositApplied()).isEqualByComparingTo("500.00");
        assertThat(saved.getRemainingAmount()).isEqualByComparingTo("0.00");
        assertThat(saved.isFundsHeld()).isTrue();
    }

    @Test
    void createPaymentHold_noLockedDeposit_appliesZeroAndHoldsFullTotal() {
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "600.00", "0.00");
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.of(winner));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId))
                .thenReturn(Optional.empty());

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("500.00");
        PaymentHold saved = capturedHold();
        assertThat(saved.getDepositApplied()).isEqualByComparingTo("0.00");
        assertThat(saved.getRemainingAmount()).isEqualByComparingTo("500.00");
        assertThat(saved.getDepositLockId()).isNull();
        assertThat(saved.isFundsHeld()).isTrue();
    }

    @Test
    void createPaymentHold_auctionAlreadyCancelled_createsNothing() {
        when(walletRepository.findByUserIdForUpdate(winnerUserId))
                .thenReturn(Optional.of(wallet(winnerWalletId, winnerUserId, "600.00", "600.00", "0.00")));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(auctionCancellationRepository.existsById(auctionId)).thenReturn(true);

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        verify(paymentHoldRepository, never()).saveAndFlush(any());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(depositLockRepository, eventPublisher);
    }

    @Test
    void createPaymentHold_winnerDepositAlreadyReleased_createsNothing() {
        when(walletRepository.findByUserIdForUpdate(winnerUserId))
                .thenReturn(Optional.of(wallet(winnerWalletId, winnerUserId, "600.00", "600.00", "0.00")));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(false);
        when(depositLockRepository.findByWalletIdAndAuctionId(winnerWalletId, auctionId))
                .thenReturn(Optional.of(depositLock("50.00", DepositLockStatus.RELEASED)));

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        verify(paymentHoldRepository, never()).saveAndFlush(any());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createPaymentHold_holdAlreadyExists_isNoOp() {
        when(walletRepository.findByUserIdForUpdate(winnerUserId))
                .thenReturn(Optional.of(wallet(winnerWalletId, winnerUserId, "600.00", "600.00", "0.00")));
        when(paymentHoldRepository.existsByAuctionId(auctionId)).thenReturn(true);

        paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId, new BigDecimal("500.00"));

        verify(paymentHoldRepository, never()).saveAndFlush(any());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(depositLockRepository, eventPublisher);
    }

    @Test
    void createPaymentHold_winnerWalletMissing_throwsNotFound() {
        when(walletRepository.findByUserIdForUpdate(winnerUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPaymentHold(auctionId, winnerUserId, sellerUserId,
                new BigDecimal("500.00")))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WALLET_NOT_FOUND");
        verify(paymentHoldRepository, never()).saveAndFlush(any());
    }

    // ── cancelPaymentHold ─────────────────────────────────────────────────────

    @Test
    void cancelPaymentHold_fundsHeld_returnsHeldFundsAndCancels() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, UUID.randomUUID(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "150.00", "500.00");
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));
        when(walletRepository.findByIdForUpdate(winnerWalletId)).thenReturn(Optional.of(winner));

        paymentService.cancelPaymentHold(auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("600.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("50.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("650.00");
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(TransactionType.HOLD_CANCEL);
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("450.00");
        assertThat(txCaptor.getValue().getAvailableBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(txCaptor.getValue().getAvailableBalanceAfter()).isEqualByComparingTo("600.00");
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.CANCELLED);
        verify(paymentHoldRepository).save(h);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelPaymentHold_notFunded_cancelsWithoutTouchingWallet() {
        PaymentHold h = hold("500.00", "50.00", "450.00", false, UUID.randomUUID(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        paymentService.cancelPaymentHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.CANCELLED);
        verify(paymentHoldRepository).save(h);
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void cancelPaymentHold_completedHold_leavesItUntouched() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.COMPLETED);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        paymentService.cancelPaymentHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.COMPLETED);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void cancelPaymentHold_noHold_isNoOp() {
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.empty());

        paymentService.cancelPaymentHold(auctionId);

        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void cancelPaymentHold_noMarker_writesMarker() {
        when(auctionCancellationRepository.existsById(auctionId)).thenReturn(false);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.empty());

        paymentService.cancelPaymentHold(auctionId);

        ArgumentCaptor<AuctionCancellation> captor = ArgumentCaptor.forClass(AuctionCancellation.class);
        verify(auctionCancellationRepository).save(captor.capture());
        assertThat(captor.getValue().getAuctionId()).isEqualTo(auctionId);
    }

    @Test
    void cancelPaymentHold_markerExists_doesNotWriteAgain() {
        when(auctionCancellationRepository.existsById(auctionId)).thenReturn(true);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.empty());

        paymentService.cancelPaymentHold(auctionId);

        verify(auctionCancellationRepository, never()).save(any());
    }

    // ── getPendingPayments ────────────────────────────────────────────────────

    @Test
    void getPendingPayments_mapsHoldsWithHoursLeftAndExpiredFlag() {
        PaymentHold open = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10).plusMinutes(30), PaymentHoldStatus.PENDING_PAYMENT);
        PaymentHold overdue = hold("300.00", "0.00", "300.00", false, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByWinnerUserIdAndStatusOrderByDeadlineAsc(winnerUserId,
                PaymentHoldStatus.PENDING_PAYMENT)).thenReturn(List.of(overdue, open));

        List<PendingPaymentResponse> result = paymentService.getPendingPayments(winnerUserId);

        assertThat(result).hasSize(2);
        PendingPaymentResponse first = result.get(0);
        assertThat(first.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(first.isFundsHeld()).isFalse();
        assertThat(first.getHoursLeft()).isZero();
        assertThat(first.isExpired()).isTrue();
        PendingPaymentResponse second = result.get(1);
        assertThat(second.getAuctionId()).isEqualTo(auctionId);
        assertThat(second.getTotalAmount()).isEqualByComparingTo("500.00");
        assertThat(second.getDepositApplied()).isEqualByComparingTo("50.00");
        assertThat(second.getRemaining()).isEqualByComparingTo("450.00");
        assertThat(second.isFundsHeld()).isTrue();
        assertThat(second.getHoursLeft()).isEqualTo(10);
        assertThat(second.isExpired()).isFalse();
        assertThat(second.getDeadline()).isEqualTo(open.getDeadline());
    }

    // ── confirmPayment ────────────────────────────────────────────────────────

    private DepositLock stubConfirm(PaymentHold h, Wallet winner, Wallet seller, DepositLock lock) {
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));
        when(walletRepository.findIdByUserId(sellerUserId)).thenReturn(Optional.of(sellerWalletId));
        when(walletRepository.findByIdForUpdate(winnerWalletId)).thenReturn(Optional.of(winner));
        when(walletRepository.findByIdForUpdate(sellerWalletId)).thenReturn(Optional.of(seller));
        if (lock != null) {
            when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));
        }
        return lock;
    }

    @Test
    void confirmPayment_fundsHeld_settlesWinnerAndSellerAtomically() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", true, lock.getId(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "150.00", "500.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, lock);

        ConfirmPaymentResponse result = paymentService.confirmPayment(winnerUserId, auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("150.00");
        assertThat(seller.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(seller.getTotalBalance()).isEqualByComparingTo("500.00");
        assertThat(lock.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
        assertThat(lock.getReleasedAt()).isNotNull();
        verify(depositLockRepository).save(lock);
        verify(walletRepository).save(winner);
        verify(walletRepository).save(seller);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txCaptor.capture());
        Transaction winnerTx = txCaptor.getAllValues().get(0);
        assertThat(winnerTx.getType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(winnerTx.getWalletId()).isEqualTo(winnerWalletId);
        assertThat(winnerTx.getAmount()).isEqualByComparingTo("500.00");
        assertThat(winnerTx.getAvailableBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(winnerTx.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(winnerTx.getReferenceId()).isEqualTo(auctionId);
        Transaction sellerTx = txCaptor.getAllValues().get(1);
        assertThat(sellerTx.getType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(sellerTx.getWalletId()).isEqualTo(sellerWalletId);
        assertThat(sellerTx.getAmount()).isEqualByComparingTo("500.00");
        assertThat(sellerTx.getAvailableBalanceBefore()).isEqualByComparingTo("0.00");
        assertThat(sellerTx.getAvailableBalanceAfter()).isEqualByComparingTo("500.00");

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.COMPLETED);
        assertThat(h.getCompletedAt()).isNotNull();
        verify(paymentHoldRepository).save(h);

        PaymentEvent event = capturedEvent();
        assertThat(event.getPaymentType()).isEqualTo("COMPLETED");
        assertThat(event.getUserId()).isEqualTo(winnerUserId);
        assertThat(event.getSellerId()).isEqualTo(sellerUserId);
        assertThat(event.getAmount()).isEqualByComparingTo("500.00");

        assertThat(result.getAuctionId()).isEqualTo(auctionId);
        assertThat(result.getAmountPaid()).isEqualByComparingTo("500.00");
        assertThat(result.getDepositApplied()).isEqualByComparingTo("50.00");
        assertThat(result.getRemainingPaid()).isEqualByComparingTo("450.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(result.getLockedBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void confirmPayment_notFundedButToppedUp_paysRemainingFromAvailable() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", false, lock.getId(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "550.00", "500.00", "50.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, lock);

        paymentService.confirmPayment(winnerUserId, auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("50.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("50.00");
        assertThat(seller.getTotalBalance()).isEqualByComparingTo("500.00");
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txCaptor.capture());
        assertThat(txCaptor.getAllValues().get(0).getAvailableBalanceBefore()).isEqualByComparingTo("500.00");
        assertThat(txCaptor.getAllValues().get(0).getAvailableBalanceAfter()).isEqualByComparingTo("50.00");
    }

    @Test
    void confirmPayment_notFundedAndInsufficient_throwsAndChangesNothing() {
        PaymentHold h = hold("500.00", "50.00", "450.00", false, UUID.randomUUID(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "150.00", "100.00", "50.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, null);

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOfSatisfying(InsufficientBalanceException.class, ex -> {
                    assertThat(ex.getAvailableBalance()).isEqualByComparingTo("100.00");
                    assertThat(ex.getRequired()).isEqualByComparingTo("450.00");
                });
        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPayment_deadlinePassed_throwsExpired() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_DEADLINE_EXPIRED");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void confirmPayment_notPending_throwsConflict() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.COMPLETED);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_NOT_PENDING");
    }

    @Test
    void confirmPayment_otherUsersHold_throwsNotFound() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> paymentService.confirmPayment(UUID.randomUUID(), auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_HOLD_NOT_FOUND");
    }

    @Test
    void confirmPayment_noHold_throwsNotFound() {
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_HOLD_NOT_FOUND");
    }

    @Test
    void confirmPayment_sellerWalletMissing_throwsNotFound() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));
        when(walletRepository.findIdByUserId(sellerUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("SELLER_WALLET_NOT_FOUND");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void confirmPayment_depositLockNoLongerLocked_throwsConflictAndChangesNothing() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.RELEASED);
        PaymentHold h = hold("500.00", "50.00", "450.00", true, lock.getId(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "150.00", "450.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, lock);

        assertThatThrownBy(() -> paymentService.confirmPayment(winnerUserId, auctionId))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("DEPOSIT_LOCK_CLOSED");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPayment_depositExceedsTotal_refundsExcessToWinner() {
        DepositLock lock = depositLock("600.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "500.00", "0.00", true, lock.getId(),
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "0.00", "600.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, lock);

        ConfirmPaymentResponse result = paymentService.confirmPayment(winnerUserId, auctionId);

        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("100.00");
        assertThat(seller.getTotalBalance()).isEqualByComparingTo("500.00");
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(3)).save(txCaptor.capture());
        Transaction refund = txCaptor.getAllValues().get(1);
        assertThat(refund.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(refund.getWalletId()).isEqualTo(winnerWalletId);
        assertThat(refund.getAmount()).isEqualByComparingTo("100.00");
        assertThat(refund.getAvailableBalanceBefore()).isEqualByComparingTo("0.00");
        assertThat(refund.getAvailableBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void confirmPayment_locksHoldFirstThenWalletsInAscendingIdOrder() {
        winnerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        sellerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, null);

        paymentService.confirmPayment(winnerUserId, auctionId);

        InOrder order = inOrder(paymentHoldRepository, walletRepository);
        order.verify(paymentHoldRepository).findByAuctionIdForUpdate(auctionId);
        order.verify(walletRepository).findByIdForUpdate(sellerWalletId);
        order.verify(walletRepository).findByIdForUpdate(winnerWalletId);
    }

    @Test
    void confirmPayment_locksWinnerFirstWhenWinnerIdIsLower() {
        winnerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        sellerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00");
        Wallet seller = wallet(sellerWalletId, sellerUserId, "0.00", "0.00", "0.00");
        stubConfirm(h, winner, seller, null);

        paymentService.confirmPayment(winnerUserId, auctionId);

        InOrder order = inOrder(paymentHoldRepository, walletRepository);
        order.verify(paymentHoldRepository).findByAuctionIdForUpdate(auctionId);
        order.verify(walletRepository).findByIdForUpdate(winnerWalletId);
        order.verify(walletRepository).findByIdForUpdate(sellerWalletId);
    }

    // ── forfeitExpiredHold ────────────────────────────────────────────────────

    private final UUID platformUserId = UUID.randomUUID();
    private UUID platformWalletId = UUID.randomUUID();

    private void usePlatform() {
        ReflectionTestUtils.setField(paymentService, "platformUserId", platformUserId.toString());
        when(walletRepository.findIdByUserId(platformUserId)).thenReturn(Optional.of(platformWalletId));
    }

    private void stubForfeit(PaymentHold h, Wallet winner, Wallet platform) {
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));
        usePlatform();
        when(walletRepository.findByIdForUpdate(winnerWalletId)).thenReturn(Optional.of(winner));
        when(walletRepository.findByIdForUpdate(platformWalletId)).thenReturn(Optional.of(platform));
    }

    private List<Transaction> savedTransactions(int count) {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void forfeitExpiredHold_fundsHeldWithDeposit_returnsRemainingAndMovesDepositToPlatform() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", true, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "150.00", "500.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "1000.00", "1000.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("600.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("600.00");
        assertThat(platform.getAvailableBalance()).isEqualByComparingTo("1050.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("1050.00");
        verify(walletRepository).save(winner);
        verify(walletRepository).save(platform);

        List<Transaction> txs = savedTransactions(3);
        assertThat(txs.get(0).getType()).isEqualTo(TransactionType.HOLD_CANCEL);
        assertThat(txs.get(0).getWalletId()).isEqualTo(winnerWalletId);
        assertThat(txs.get(0).getAmount()).isEqualByComparingTo("450.00");
        assertThat(txs.get(0).getAvailableBalanceBefore()).isEqualByComparingTo("150.00");
        assertThat(txs.get(0).getAvailableBalanceAfter()).isEqualByComparingTo("600.00");
        assertThat(txs.get(1).getType()).isEqualTo(TransactionType.FORFEIT);
        assertThat(txs.get(1).getWalletId()).isEqualTo(winnerWalletId);
        assertThat(txs.get(1).getAmount()).isEqualByComparingTo("50.00");
        assertThat(txs.get(1).getAvailableBalanceBefore()).isEqualByComparingTo("600.00");
        assertThat(txs.get(1).getAvailableBalanceAfter()).isEqualByComparingTo("600.00");
        assertThat(txs.get(2).getType()).isEqualTo(TransactionType.FORFEIT);
        assertThat(txs.get(2).getWalletId()).isEqualTo(platformWalletId);
        assertThat(txs.get(2).getAmount()).isEqualByComparingTo("50.00");
        assertThat(txs.get(2).getAvailableBalanceBefore()).isEqualByComparingTo("1000.00");
        assertThat(txs.get(2).getAvailableBalanceAfter()).isEqualByComparingTo("1050.00");
        txs.forEach(tx -> assertThat(tx.getReferenceId()).isEqualTo(auctionId));

        assertThat(lock.getStatus()).isEqualTo(DepositLockStatus.FORFEITED);
        assertThat(lock.getReleasedAt()).isNotNull();
        verify(depositLockRepository).save(lock);
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.FORFEITED);
        assertThat(h.getCompletedAt()).isNotNull();
        verify(paymentHoldRepository).save(h);

        PaymentEvent event = capturedEvent();
        assertThat(event.getPaymentType()).isEqualTo("FAILED");
        assertThat(event.getAuctionId()).isEqualTo(auctionId);
        assertThat(event.getUserId()).isEqualTo(winnerUserId);
        assertThat(event.getSellerId()).isEqualTo(sellerUserId);
        assertThat(event.getAmount()).isEqualByComparingTo("500.00");
        assertThat(event.getDepositAmount()).isEqualByComparingTo("50.00");
        assertThat(event.getRemaining()).isEqualByComparingTo("450.00");
    }

    @Test
    void forfeitExpiredHold_notFunded_forfeitsDepositWithoutHoldCancel() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", false, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "150.00", "100.00", "50.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("100.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("50.00");
        List<Transaction> txs = savedTransactions(2);
        assertThat(txs).extracting(Transaction::getType).containsOnly(TransactionType.FORFEIT);
    }

    @Test
    void forfeitExpiredHold_depositLargerThanBid_forfeitsWholeDeposit() {
        DepositLock lock = depositLock("600.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "500.00", "0.00", true, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "600.00", "0.00", "600.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("0.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("600.00");
        assertThat(capturedEvent().getDepositAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void forfeitExpiredHold_noDepositLock_releasesHeldFundsAndForfeitsNothing() {
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(winner.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(winner.getLockedBalance()).isEqualByComparingTo("0.00");
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("500.00");
        List<Transaction> txs = savedTransactions(1);
        assertThat(txs.get(0).getType()).isEqualTo(TransactionType.HOLD_CANCEL);
        verify(walletRepository, never()).save(platform);
        verifyNoInteractions(depositLockRepository);
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.FORFEITED);
        assertThat(capturedEvent().getDepositAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void forfeitExpiredHold_depositNoLongerLocked_forfeitsNothingButClosesHold() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.RELEASED);
        PaymentHold h = hold("500.00", "50.00", "450.00", false, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "100.00", "100.00", "0.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        verify(transactionRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(depositLockRepository, never()).save(any());
        assertThat(lock.getStatus()).isEqualTo(DepositLockStatus.RELEASED);
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.FORFEITED);
        verify(paymentHoldRepository).save(h);
        assertThat(capturedEvent().getDepositAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void forfeitExpiredHold_notYetDue_isNoOp() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.PENDING_PAYMENT);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository, depositLockRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_alreadyCompleted_isNoOp() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.COMPLETED);
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.COMPLETED);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository, depositLockRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_lockedByAnotherInstance_isNoOp() {
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.empty());

        paymentService.forfeitExpiredHold(auctionId);

        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository, depositLockRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_platformWalletMissing_throwsAndChangesNothing() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));
        ReflectionTestUtils.setField(paymentService, "platformUserId", platformUserId.toString());
        when(walletRepository.findIdByUserId(platformUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.forfeitExpiredHold(auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WALLET_NOT_FOUND");
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.PENDING_PAYMENT);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(transactionRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_locksHoldThenWinnerFirstWhenWinnerIdIsLower() {
        winnerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        stubForfeit(h, wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00"),
                wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00"));

        paymentService.forfeitExpiredHold(auctionId);

        InOrder order = inOrder(paymentHoldRepository, walletRepository);
        order.verify(paymentHoldRepository).findByAuctionIdForUpdateSkipLocked(auctionId);
        order.verify(walletRepository).findByIdForUpdate(winnerWalletId);
        order.verify(walletRepository).findByIdForUpdate(platformWalletId);
    }

    @Test
    void forfeitExpiredHold_locksPlatformFirstWhenPlatformIdIsLower() {
        winnerWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PaymentHold h = hold("500.00", "0.00", "500.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        stubForfeit(h, wallet(winnerWalletId, winnerUserId, "500.00", "0.00", "500.00"),
                wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00"));

        paymentService.forfeitExpiredHold(auctionId);

        InOrder order = inOrder(paymentHoldRepository, walletRepository);
        order.verify(paymentHoldRepository).findByAuctionIdForUpdateSkipLocked(auctionId);
        order.verify(walletRepository).findByIdForUpdate(platformWalletId);
        order.verify(walletRepository).findByIdForUpdate(winnerWalletId);
    }

    @Test
    void forfeitExpiredHold_readsDepositLockOnlyAfterBothWalletLocks() {
        DepositLock lock = depositLock("50.00", DepositLockStatus.LOCKED);
        PaymentHold h = hold("500.00", "50.00", "450.00", true, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "650.00", "150.00", "500.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        paymentService.forfeitExpiredHold(auctionId);

        InOrder order = inOrder(paymentHoldRepository, walletRepository, depositLockRepository);
        order.verify(paymentHoldRepository).findByAuctionIdForUpdateSkipLocked(auctionId);
        order.verify(walletRepository, times(2)).findByIdForUpdate(any());
        order.verify(depositLockRepository).findById(lock.getId());
    }

    @Test
    void forfeitExpiredHold_alreadyCancelled_isNoOp() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.CANCELLED);
        when(paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId)).thenReturn(Optional.of(h));

        paymentService.forfeitExpiredHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.CANCELLED);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository, depositLockRepository, eventPublisher);
    }

    @Test
    void forfeitExpiredHold_depositLockOfAnotherWallet_throwsAndChangesNothing() {
        DepositLock lock = DepositLock.builder()
                .id(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .auctionId(auctionId)
                .amount(new BigDecimal("50.00"))
                .status(DepositLockStatus.LOCKED)
                .build();
        PaymentHold h = hold("500.00", "50.00", "450.00", false, lock.getId(),
                LocalDateTime.now().minusMinutes(1), PaymentHoldStatus.PENDING_PAYMENT);
        Wallet winner = wallet(winnerWalletId, winnerUserId, "100.00", "100.00", "0.00");
        Wallet platform = wallet(platformWalletId, platformUserId, "0.00", "0.00", "0.00");
        stubForfeit(h, winner, platform);
        when(depositLockRepository.findById(lock.getId())).thenReturn(Optional.of(lock));

        assertThatThrownBy(() -> paymentService.forfeitExpiredHold(auctionId))
                .isInstanceOf(IllegalStateException.class);

        verify(transactionRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        assertThat(winner.getTotalBalance()).isEqualByComparingTo("100.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("0.00");
        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.PENDING_PAYMENT);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelPaymentHold_forfeitedHold_leavesItUntouched() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(10), PaymentHoldStatus.FORFEITED);
        when(paymentHoldRepository.findByAuctionIdForUpdate(auctionId)).thenReturn(Optional.of(h));

        paymentService.cancelPaymentHold(auctionId);

        assertThat(h.getStatus()).isEqualTo(PaymentHoldStatus.FORFEITED);
        verify(paymentHoldRepository, never()).save(any());
        verifyNoInteractions(walletRepository, transactionRepository);
    }
}
