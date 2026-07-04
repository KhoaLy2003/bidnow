package com.bidnow.wallet.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
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
import com.bidnow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

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

    @Override
    @Transactional
    public DepositResponse deposit(UUID userId, DepositRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException(
                    "Wallet is not active for userId: " + userId, ErrorCodes.INVALID_INPUT);
        }

        BigDecimal balanceBefore = wallet.getAvailableBalance();
        BigDecimal balanceAfter = balanceBefore.add(request.getAmount());

        wallet.setAvailableBalance(balanceAfter);
        wallet.setTotalBalance(wallet.getTotalBalance().add(request.getAmount()));
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .availableBalanceBefore(balanceBefore)
                .availableBalanceAfter(balanceAfter)
                .status(TransactionStatus.COMPLETED)
                .description("Mock deposit")
                .build();
        transactionRepository.save(transaction);

        eventPublisher.publishEvent(
                new DepositCompletedApplicationEvent(this, userId, wallet.getId(),
                        request.getAmount(), balanceAfter));

        log.info("Deposit completed for userId={}, amount={}, newBalance={}",
                userId, request.getAmount(), balanceAfter);

        return DepositResponse.builder()
                .transactionId(transaction.getId())
                .newBalance(balanceAfter)
                .status(transaction.getStatus().name())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactions(UUID userId, TransactionType type,
                                                              LocalDate startDate, LocalDate endDate,
                                                              int page, int size) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for userId: " + userId, ErrorCodes.NOT_FOUND));

        LocalDateTime from = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime to = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

        Specification<Transaction> spec = SpecificationBuilder.<Transaction>forEntity()
                .with("walletId", SearchOperator.EQUAL, wallet.getId())
                .withIfPresent("type", SearchOperator.EQUAL, type)
                .withBetweenIfPresent("createdAt", from, to)
                .build();

        Page<Transaction> result = transactionRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.of(result.map(this::toTransactionResponse));
    }

    private TransactionResponse toTransactionResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .availableBalanceBefore(tx.getAvailableBalanceBefore())
                .availableBalanceAfter(tx.getAvailableBalanceAfter())
                .description(tx.getDescription())
                .status(tx.getStatus().name())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
