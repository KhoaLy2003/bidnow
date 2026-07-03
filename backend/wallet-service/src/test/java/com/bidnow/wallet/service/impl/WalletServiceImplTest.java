package com.bidnow.wallet.service.impl;

import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.request.DepositRequest;
import com.bidnow.wallet.dto.response.DepositResponse;
import com.bidnow.wallet.dto.response.TransactionResponse;
import com.bidnow.wallet.dto.response.WalletResponse;
import com.bidnow.wallet.kafka.DepositCompletedApplicationEvent;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WalletServiceImpl walletService;

    // ── existing tests ────────────────────────────────────────────────────────

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

    // ── deposit tests ─────────────────────────────────────────────────────────

    @Test
    void deposit_happyPath_creditsBalancesAndRecordsTransaction() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(walletId)
                .userId(userId)
                .totalBalance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("100.00"))
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepositResponse response = walletService.deposit(userId, new DepositRequest(new BigDecimal("500.00")));

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(wallet.getTotalBalance()).isEqualByComparingTo(new BigDecimal("600.00"));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction saved = txCaptor.getValue();
        assertThat(saved.getWalletId()).isEqualTo(walletId);
        assertThat(saved.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(saved.getAvailableBalanceBefore()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(saved.getAvailableBalanceAfter()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        verify(eventPublisher).publishEvent(any(DepositCompletedApplicationEvent.class));

        assertThat(response.getNewBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void deposit_suspendedWallet_throwsBadRequestException() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.SUSPENDED)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"))))
                .isInstanceOf(BadRequestException.class);

        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deposit_walletNotFound_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deposit(userId, new DepositRequest(new BigDecimal("100.00"))))
                .isInstanceOf(NotFoundException.class);
    }

    // ── getTransactions tests ─────────────────────────────────────────────────

    @Test
    void getTransactions_noFilters_returnsPagedResultsSortedByCreatedAtDesc() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(walletId)
                .userId(userId)
                .totalBalance(new BigDecimal("600.00"))
                .availableBalance(new BigDecimal("600.00"))
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(walletId)
                .type(TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .availableBalanceBefore(new BigDecimal("100.00"))
                .availableBalanceAfter(new BigDecimal("600.00"))
                .status(TransactionStatus.COMPLETED)
                .description("Mock deposit")
                .createdAt(LocalDateTime.now())
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        PageResponse<TransactionResponse> result =
                walletService.getTransactions(userId, null, null, null, 0, 10);

        assertThat(result.getData()).hasSize(1);
        TransactionResponse dto = result.getData().get(0);
        assertThat(dto.getType()).isEqualTo("DEPOSIT");
        assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(dto.getAvailableBalanceBefore()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(dto.getAvailableBalanceAfter()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(dto.getStatus()).isEqualTo("COMPLETED");
        assertThat(dto.getDescription()).isEqualTo("Mock deposit");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by("createdAt").descending());
    }

    @Test
    void getTransactions_walletNotFound_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getTransactions(userId, null, null, null, 0, 10))
                .isInstanceOf(NotFoundException.class);

        verify(transactionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getTransactions_emptyWallet_returnsEmptyPage() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        PageResponse<TransactionResponse> result =
                walletService.getTransactions(userId, null, null, null, 0, 10);

        assertThat(result.getData()).isEmpty();
        assertThat(result.getPagination().getTotal()).isZero();
    }
}
