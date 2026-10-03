package com.bidnow.wallet.service.impl;

import com.bidnow.common.annotation.Audit;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.enums.AuditAction;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.common.util.AuditContextHolder;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.response.AdminDepositLockResponse;
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
import com.bidnow.wallet.service.WalletAdminService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletAdminServiceImpl implements WalletAdminService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final DepositLockRepository depositLockRepository;
    private final ObjectMapper objectMapper;

    @Value("${wallet.platform-user-id}")
    private String platformUserId;

    @Override
    @Transactional(readOnly = true)
    public WalletStatsResponse getStats() {
        UUID platformUser = UUID.fromString(platformUserId);
        BigDecimal platformBalance = walletRepository.findByUserId(platformUser)
                .map(Wallet::getTotalBalance)
                .orElse(BigDecimal.ZERO);
        return WalletStatsResponse.builder()
                .platformWalletBalance(platformBalance)
                .totalActiveWallets(walletRepository.countByStatusAndUserIdNot(WalletStatus.ACTIVE, platformUser))
                .totalLockedBalance(walletRepository.sumLockedBalance())
                .totalActiveDepositLocks(depositLockRepository.countByStatus(DepositLockStatus.LOCKED))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> listTransactions(TransactionType type, int page, int size) {
        Specification<Transaction> spec = SpecificationBuilder.<Transaction>forEntity()
                .withIfPresent("type", SearchOperator.EQUAL, type)
                .build();
        Page<Transaction> result = transactionRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<UUID> walletIds = result.getContent().stream().map(Transaction::getWalletId).distinct().toList();
        Map<UUID, UUID> userIdByWalletId = walletRepository.findAllById(walletIds).stream()
                .collect(Collectors.toMap(Wallet::getId, Wallet::getUserId));
        return PageResponse.of(result.map(tx -> toTransactionResponse(tx, userIdByWalletId.get(tx.getWalletId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminWalletDetailResponse getWalletDetail(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> walletNotFound(userId));
        List<AdminTransactionResponse> recent = transactionRepository
                .findTop20ByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(tx -> toTransactionResponse(tx, wallet.getUserId()))
                .toList();
        List<AdminDepositLockResponse> locks = depositLockRepository
                .findByWalletIdAndStatus(wallet.getId(), DepositLockStatus.LOCKED).stream()
                .map(this::toDepositLockResponse)
                .toList();
        return AdminWalletDetailResponse.builder()
                .wallet(toWalletResponse(wallet))
                .recentTransactions(recent)
                .activeDepositLocks(locks)
                .build();
    }

    @Override
    @Transactional
    @Audit(action = AuditAction.ADMIN_ACTION, entityType = "Wallet", reason = "Admin froze wallet")
    public AdminWalletResponse freezeWallet(UUID userId) {
        if (UUID.fromString(platformUserId).equals(userId)) {
            throw new BadRequestException("The platform wallet cannot be frozen", ErrorCodes.INVALID_INPUT);
        }
        return changeStatus(userId, WalletStatus.SUSPENDED);
    }

    @Override
    @Transactional
    @Audit(action = AuditAction.ADMIN_ACTION, entityType = "Wallet", reason = "Admin unfroze wallet")
    public AdminWalletResponse unfreezeWallet(UUID userId) {
        return changeStatus(userId, WalletStatus.ACTIVE);
    }

    private AdminWalletResponse changeStatus(UUID userId, WalletStatus target) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> walletNotFound(userId));
        AuditContextHolder.setOldState(Wallet.builder().id(wallet.getId()).status(wallet.getStatus()).build());
        if (wallet.getStatus() != target) {
            wallet.setStatus(target);
            walletRepository.save(wallet);
            log.info("Wallet {} of user {} set to {}", wallet.getId(), userId, target);
        }
        AuditContextHolder.setNewState(Wallet.builder().id(wallet.getId()).status(wallet.getStatus()).build());
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional
    @Audit(action = AuditAction.ADMIN_ACTION, entityType = "Transaction", reason = "Admin manual refund")
    public ManualRefundResponse refundTransaction(UUID adminId, UUID transactionId, String reason) {
        AuditContextHolder.setOldState(null);
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId,
                        WalletErrorCodes.TRANSACTION_NOT_FOUND));
        UUID platformWalletId = walletRepository.findIdByUserId(UUID.fromString(platformUserId))
                .orElseThrow(() -> new NotFoundException("Platform wallet not found for userId: " + platformUserId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
        if (!isRefundable(original, platformWalletId)) {
            throw new BadRequestException("Transaction " + transactionId + " is not refundable",
                    WalletErrorCodes.TRANSACTION_NOT_REFUNDABLE);
        }

        // Lock both wallets in ascending id order; the duplicate check runs under the locks.
        Wallet user;
        Wallet platform;
        if (original.getWalletId().compareTo(platformWalletId) < 0) {
            user = lockWallet(original.getWalletId());
            platform = lockWallet(platformWalletId);
        } else {
            platform = lockWallet(platformWalletId);
            user = lockWallet(original.getWalletId());
        }
        if (transactionRepository.existsByTypeAndReferenceId(TransactionType.REFUND, original.getId())) {
            throw new ConflictException("Transaction " + transactionId + " was already refunded",
                    WalletErrorCodes.TRANSACTION_ALREADY_REFUNDED);
        }
        BigDecimal amount = original.getAmount();
        if (platform.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(platform.getAvailableBalance(), amount);
        }

        BigDecimal platformBefore = platform.getAvailableBalance();
        platform.setAvailableBalance(platformBefore.subtract(amount));
        platform.setTotalBalance(platform.getTotalBalance().subtract(amount));
        walletRepository.save(platform);
        transactionRepository.save(refundRow(platform, platformBefore, amount, original, adminId, reason, "DEBIT"));

        BigDecimal userBefore = user.getAvailableBalance();
        user.setAvailableBalance(userBefore.add(amount));
        user.setTotalBalance(user.getTotalBalance().add(amount));
        walletRepository.save(user);
        Transaction credit = transactionRepository.save(
                refundRow(user, userBefore, amount, original, adminId, reason, "CREDIT"));

        AuditContextHolder.setNewState(credit);
        log.info("Admin {} refunded transaction {} ({}) to user {}", adminId, transactionId, amount, user.getUserId());
        return ManualRefundResponse.builder()
                .refundTransactionId(credit.getId())
                .amount(amount)
                .userId(user.getUserId())
                .availableBalance(user.getAvailableBalance())
                .build();
    }

    private boolean isRefundable(Transaction tx, UUID platformWalletId) {
        return (tx.getType() == TransactionType.PAYMENT || tx.getType() == TransactionType.FORFEIT)
                && !tx.getWalletId().equals(platformWalletId)
                && tx.getAvailableBalanceAfter().compareTo(tx.getAvailableBalanceBefore()) <= 0;
    }

    private Transaction refundRow(Wallet wallet, BigDecimal before, BigDecimal amount, Transaction original,
                                  UUID adminId, String reason, String direction) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("adminId", String.valueOf(adminId));
        meta.put("reason", reason);
        meta.put("originalTransactionId", original.getId().toString());
        meta.put("direction", direction);
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize refund metadata", e);
        }
        return Transaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.REFUND)
                .amount(amount)
                .availableBalanceBefore(before)
                .availableBalanceAfter(wallet.getAvailableBalance())
                .referenceId(original.getId())
                .status(TransactionStatus.COMPLETED)
                .description("Manual refund of transaction " + original.getId())
                .metadata(metadata)
                .build();
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet not found: " + walletId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
    }

    AdminWalletResponse toWalletResponse(Wallet wallet) {
        return AdminWalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .totalBalance(wallet.getTotalBalance())
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .build();
    }

    AdminTransactionResponse toTransactionResponse(Transaction tx, UUID userId) {
        return AdminTransactionResponse.builder()
                .id(tx.getId())
                .walletId(tx.getWalletId())
                .userId(userId)
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .availableBalanceBefore(tx.getAvailableBalanceBefore())
                .availableBalanceAfter(tx.getAvailableBalanceAfter())
                .referenceId(tx.getReferenceId())
                .description(tx.getDescription())
                .status(tx.getStatus().name())
                .metadata(tx.getMetadata())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private AdminDepositLockResponse toDepositLockResponse(DepositLock lock) {
        return AdminDepositLockResponse.builder()
                .id(lock.getId())
                .auctionId(lock.getAuctionId())
                .amount(lock.getAmount())
                .status(lock.getStatus().name())
                .lockedAt(lock.getLockedAt())
                .build();
    }

    private NotFoundException walletNotFound(UUID userId) {
        return new NotFoundException("Wallet not found for userId: " + userId, WalletErrorCodes.WALLET_NOT_FOUND);
    }
}
