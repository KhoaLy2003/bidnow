package com.bidnow.wallet.service.impl;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.util.AuditContextHolder;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.AdminTransactionResponse;
import com.bidnow.wallet.dto.response.AdminWalletDetailResponse;
import com.bidnow.wallet.dto.response.AdminWalletResponse;
import com.bidnow.wallet.dto.response.ManualRefundResponse;
import com.bidnow.wallet.dto.response.WalletStatsResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletAdminServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DepositLockRepository depositLockRepository;

    private WalletAdminServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID platformUserId = UUID.randomUUID();
    private UUID platformWalletId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private UUID userWalletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WalletAdminServiceImpl(walletRepository, transactionRepository, depositLockRepository, objectMapper);
        ReflectionTestUtils.setField(service, "platformUserId", platformUserId.toString());
    }

    @AfterEach
    void clearAuditContext() {
        AuditContextHolder.clear();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private Wallet wallet(UUID id, UUID owner, String total, String available, String locked, WalletStatus status) {
        return Wallet.builder()
                .id(id).userId(owner)
                .totalBalance(bd(total)).availableBalance(bd(available)).lockedBalance(bd(locked))
                .currency("USD").status(status)
                .build();
    }

    private Transaction tx(TransactionType type, UUID walletId, String amount, String before, String after) {
        return Transaction.builder()
                .id(UUID.randomUUID()).walletId(walletId).type(type)
                .amount(bd(amount)).availableBalanceBefore(bd(before)).availableBalanceAfter(bd(after))
                .referenceId(UUID.randomUUID()).description("t").status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.of(2026, 9, 28, 10, 0))
                .build();
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_aggregatesPlatformBalanceWalletsLockedAndLocks() {
        when(walletRepository.findByUserId(platformUserId))
                .thenReturn(Optional.of(wallet(platformWalletId, platformUserId, "950.00", "950.00", "0.00", WalletStatus.ACTIVE)));
        when(walletRepository.countByStatusAndUserIdNot(WalletStatus.ACTIVE, platformUserId)).thenReturn(12L);
        when(walletRepository.sumLockedBalance()).thenReturn(bd("300.00"));
        when(depositLockRepository.countByStatus(DepositLockStatus.LOCKED)).thenReturn(4L);

        WalletStatsResponse stats = service.getStats();

        assertThat(stats.getPlatformWalletBalance()).isEqualByComparingTo("950.00");
        assertThat(stats.getTotalActiveWallets()).isEqualTo(12L);
        assertThat(stats.getTotalLockedBalance()).isEqualByComparingTo("300.00");
        assertThat(stats.getTotalActiveDepositLocks()).isEqualTo(4L);
    }

    @Test
    void getStats_platformWalletMissing_reportsZeroBalance() {
        when(walletRepository.findByUserId(platformUserId)).thenReturn(Optional.empty());
        when(walletRepository.countByStatusAndUserIdNot(WalletStatus.ACTIVE, platformUserId)).thenReturn(0L);
        when(walletRepository.sumLockedBalance()).thenReturn(BigDecimal.ZERO);
        when(depositLockRepository.countByStatus(DepositLockStatus.LOCKED)).thenReturn(0L);

        assertThat(service.getStats().getPlatformWalletBalance()).isEqualByComparingTo("0");
    }

    // ── getWalletDetail ───────────────────────────────────────────────────────

    @Test
    void getWalletDetail_mapsWalletRecentTransactionsAndActiveLocks() {
        Wallet w = wallet(userWalletId, userId, "200.00", "150.00", "50.00", WalletStatus.SUSPENDED);
        Transaction t = tx(TransactionType.DEPOSIT, userWalletId, "200.00", "0.00", "200.00");
        DepositLock lock = DepositLock.builder()
                .id(UUID.randomUUID()).walletId(userWalletId).auctionId(UUID.randomUUID())
                .amount(bd("50.00")).status(DepositLockStatus.LOCKED)
                .lockedAt(LocalDateTime.of(2026, 9, 28, 9, 0))
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(w));
        when(transactionRepository.findTop20ByWalletIdOrderByCreatedAtDesc(userWalletId)).thenReturn(List.of(t));
        when(depositLockRepository.findByWalletIdAndStatus(userWalletId, DepositLockStatus.LOCKED)).thenReturn(List.of(lock));

        AdminWalletDetailResponse detail = service.getWalletDetail(userId);

        assertThat(detail.getWallet().getWalletId()).isEqualTo(userWalletId);
        assertThat(detail.getWallet().getUserId()).isEqualTo(userId);
        assertThat(detail.getWallet().getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(detail.getWallet().getStatus()).isEqualTo("SUSPENDED");
        assertThat(detail.getRecentTransactions()).hasSize(1);
        assertThat(detail.getRecentTransactions().get(0).getId()).isEqualTo(t.getId());
        assertThat(detail.getRecentTransactions().get(0).getUserId()).isEqualTo(userId);
        assertThat(detail.getRecentTransactions().get(0).getType()).isEqualTo("DEPOSIT");
        assertThat(detail.getActiveDepositLocks()).hasSize(1);
        assertThat(detail.getActiveDepositLocks().get(0).getAuctionId()).isEqualTo(lock.getAuctionId());
        assertThat(detail.getActiveDepositLocks().get(0).getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void getWalletDetail_walletMissing_throwsNotFound() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWalletDetail(userId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WALLET_NOT_FOUND");
    }

    // ── listTransactions ──────────────────────────────────────────────────────

    @Test
    void listTransactions_pagesSortedNewestFirstAndResolvesUserIds() {
        UUID otherWalletId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Transaction a = tx(TransactionType.FORFEIT, userWalletId, "50.00", "0.00", "0.00");
        Transaction b = tx(TransactionType.FORFEIT, otherWalletId, "20.00", "0.00", "0.00");
        when(transactionRepository.findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(1, 50), 52));
        when(walletRepository.findAllById(any())).thenReturn(List.of(
                wallet(userWalletId, userId, "0", "0", "0", WalletStatus.ACTIVE),
                wallet(otherWalletId, otherUserId, "0", "0", "0", WalletStatus.ACTIVE)));

        PageResponse<AdminTransactionResponse> result = service.listTransactions(TransactionType.FORFEIT, 1, 50);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(ArgumentMatchers.<Specification<Transaction>>any(), page.capture());
        assertThat(page.getValue()).isEqualTo(PageRequest.of(1, 50, Sort.by("createdAt").descending()));
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getUserId()).isEqualTo(userId);
        assertThat(result.getData().get(1).getUserId()).isEqualTo(otherUserId);
        assertThat(result.getPagination().getTotal()).isEqualTo(52);
    }

    // ── freeze / unfreeze ─────────────────────────────────────────────────────

    @Test
    void freezeWallet_active_setsSuspendedAndRecordsAuditStates() {
        Wallet w = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        AdminWalletResponse result = service.freezeWallet(userId);

        assertThat(w.getStatus()).isEqualTo(WalletStatus.SUSPENDED);
        verify(walletRepository).save(w);
        assertThat(result.getStatus()).isEqualTo("SUSPENDED");
        assertThat(((Wallet) AuditContextHolder.getOldState()).getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(((Wallet) AuditContextHolder.getNewState()).getStatus()).isEqualTo(WalletStatus.SUSPENDED);
    }

    @Test
    void freezeWallet_alreadySuspended_isIdempotentWithoutSave() {
        Wallet w = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.SUSPENDED);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        AdminWalletResponse result = service.freezeWallet(userId);

        assertThat(result.getStatus()).isEqualTo("SUSPENDED");
        verify(walletRepository, never()).save(any());
    }

    @Test
    void freezeWallet_platformWallet_rejected() {
        assertThatThrownBy(() -> service.freezeWallet(platformUserId))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("INVALID_INPUT");
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void freezeWallet_walletMissing_throwsNotFound() {
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.freezeWallet(userId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WALLET_NOT_FOUND");
    }

    @Test
    void unfreezeWallet_suspended_setsActive() {
        Wallet w = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.SUSPENDED);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(w));

        AdminWalletResponse result = service.unfreezeWallet(userId);

        assertThat(w.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        verify(walletRepository).save(w);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    // ── refundTransaction ─────────────────────────────────────────────────────

    private void stubRefund(Transaction original, Wallet user, Wallet platform) {
        when(transactionRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(walletRepository.findIdByUserId(platformUserId)).thenReturn(Optional.of(platformWalletId));
        when(walletRepository.findByIdForUpdate(userWalletId)).thenReturn(Optional.of(user));
        when(walletRepository.findByIdForUpdate(platformWalletId)).thenReturn(Optional.of(platform));
    }

    @Test
    void refundTransaction_forfeit_movesAmountFromPlatformToUserWithAuditedLedgerRows() throws Exception {
        UUID adminId = UUID.randomUUID();
        Transaction original = tx(TransactionType.FORFEIT, userWalletId, "50.00", "600.00", "600.00");
        Wallet user = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.SUSPENDED);
        Wallet platform = wallet(platformWalletId, platformUserId, "1000.00", "1000.00", "0.00", WalletStatus.ACTIVE);
        stubRefund(original, user, platform);
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ManualRefundResponse result = service.refundTransaction(adminId, original.getId(), "Customer dispute");

        assertThat(platform.getAvailableBalance()).isEqualByComparingTo("950.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("950.00");
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(user.getTotalBalance()).isEqualByComparingTo("150.00");
        verify(walletRepository).save(platform);
        verify(walletRepository).save(user);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        Transaction debit = captor.getAllValues().get(0);
        Transaction credit = captor.getAllValues().get(1);
        assertThat(debit.getWalletId()).isEqualTo(platformWalletId);
        assertThat(debit.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(debit.getAmount()).isEqualByComparingTo("50.00");
        assertThat(debit.getAvailableBalanceBefore()).isEqualByComparingTo("1000.00");
        assertThat(debit.getAvailableBalanceAfter()).isEqualByComparingTo("950.00");
        assertThat(debit.getReferenceId()).isEqualTo(original.getId());
        assertThat(credit.getWalletId()).isEqualTo(userWalletId);
        assertThat(credit.getAvailableBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(credit.getAvailableBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(credit.getReferenceId()).isEqualTo(original.getId());
        JsonNode meta = objectMapper.readTree(credit.getMetadata());
        assertThat(meta.get("adminId").asText()).isEqualTo(adminId.toString());
        assertThat(meta.get("reason").asText()).isEqualTo("Customer dispute");
        assertThat(meta.get("originalTransactionId").asText()).isEqualTo(original.getId().toString());
        assertThat(meta.get("direction").asText()).isEqualTo("CREDIT");
        assertThat(objectMapper.readTree(debit.getMetadata()).get("direction").asText()).isEqualTo("DEBIT");

        assertThat(result.getRefundTransactionId()).isEqualTo(credit.getId());
        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void refundTransaction_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refundTransaction(UUID.randomUUID(), id, "r"))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("TRANSACTION_NOT_FOUND");
    }

    @Test
    void refundTransaction_nonRefundableKinds_rejectedWithoutLocking() {
        when(walletRepository.findIdByUserId(platformUserId)).thenReturn(Optional.of(platformWalletId));
        List<Transaction> nonRefundable = List.of(
                tx(TransactionType.DEPOSIT, userWalletId, "100.00", "0.00", "100.00"),
                tx(TransactionType.HOLD, userWalletId, "50.00", "100.00", "50.00"),
                tx(TransactionType.HOLD_CANCEL, userWalletId, "50.00", "50.00", "100.00"),
                tx(TransactionType.REFUND, userWalletId, "50.00", "0.00", "50.00"),
                tx(TransactionType.PAYMENT, userWalletId, "500.00", "0.00", "500.00"),      // seller proceeds
                tx(TransactionType.FORFEIT, platformWalletId, "50.00", "0.00", "50.00"));   // platform credit
        for (Transaction t : nonRefundable) {
            when(transactionRepository.findById(t.getId())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.refundTransaction(UUID.randomUUID(), t.getId(), "r"))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("errorCode").isEqualTo("TRANSACTION_NOT_REFUNDABLE");
        }
        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void refundTransaction_winnerPaymentFundsHeld_isRefunded() {
        Transaction original = tx(TransactionType.PAYMENT, userWalletId, "500.00", "150.00", "150.00");
        Wallet user = wallet(userWalletId, userId, "150.00", "150.00", "0.00", WalletStatus.ACTIVE);
        Wallet platform = wallet(platformWalletId, platformUserId, "1000.00", "1000.00", "0.00", WalletStatus.ACTIVE);
        stubRefund(original, user, platform);
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        ManualRefundResponse result = service.refundTransaction(UUID.randomUUID(), original.getId(), "dispute");

        assertThat(user.getAvailableBalance()).isEqualByComparingTo("650.00");
        assertThat(user.getTotalBalance()).isEqualByComparingTo("650.00");
        assertThat(platform.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(platform.getTotalBalance()).isEqualByComparingTo("500.00");
        verify(transactionRepository, times(2)).save(any());
        assertThat(result.getAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void refundTransaction_alreadyRefunded_throws409AndChangesNothing() {
        Transaction original = tx(TransactionType.PAYMENT, userWalletId, "500.00", "600.00", "100.00");
        Wallet user = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE);
        Wallet platform = wallet(platformWalletId, platformUserId, "1000.00", "1000.00", "0.00", WalletStatus.ACTIVE);
        stubRefund(original, user, platform);
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.refundTransaction(UUID.randomUUID(), original.getId(), "r"))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("TRANSACTION_ALREADY_REFUNDED");
        assertThat(platform.getAvailableBalance()).isEqualByComparingTo("1000.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void refundTransaction_platformCannotCover_throwsInsufficientBalance() {
        Transaction original = tx(TransactionType.PAYMENT, userWalletId, "500.00", "600.00", "100.00");
        Wallet user = wallet(userWalletId, userId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE);
        Wallet platform = wallet(platformWalletId, platformUserId, "10.00", "10.00", "0.00", WalletStatus.ACTIVE);
        stubRefund(original, user, platform);
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.refundTransaction(UUID.randomUUID(), original.getId(), "r"))
                .isInstanceOfSatisfying(InsufficientBalanceException.class, ex -> {
                    assertThat(ex.getAvailableBalance()).isEqualByComparingTo("10.00");
                    assertThat(ex.getRequired()).isEqualByComparingTo("500.00");
                });
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void refundTransaction_locksPlatformFirstWhenPlatformIdIsLower() {
        platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Transaction original = tx(TransactionType.FORFEIT, userWalletId, "50.00", "0.00", "0.00");
        stubRefund(original,
                wallet(userWalletId, userId, "0.00", "0.00", "0.00", WalletStatus.ACTIVE),
                wallet(platformWalletId, platformUserId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE));
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.refundTransaction(UUID.randomUUID(), original.getId(), "r");

        InOrder order = inOrder(walletRepository, transactionRepository);
        order.verify(walletRepository).findByIdForUpdate(platformWalletId);
        order.verify(walletRepository).findByIdForUpdate(userWalletId);
        order.verify(transactionRepository).existsByTypeAndReferenceId(TransactionType.REFUND, original.getId());
    }

    @Test
    void refundTransaction_locksUserFirstWhenUserIdIsLower() {
        userWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Transaction original = tx(TransactionType.FORFEIT, userWalletId, "50.00", "0.00", "0.00");
        stubRefund(original,
                wallet(userWalletId, userId, "0.00", "0.00", "0.00", WalletStatus.ACTIVE),
                wallet(platformWalletId, platformUserId, "100.00", "100.00", "0.00", WalletStatus.ACTIVE));
        when(transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.refundTransaction(UUID.randomUUID(), original.getId(), "r");

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdForUpdate(userWalletId);
        order.verify(walletRepository).findByIdForUpdate(platformWalletId);
    }
}
