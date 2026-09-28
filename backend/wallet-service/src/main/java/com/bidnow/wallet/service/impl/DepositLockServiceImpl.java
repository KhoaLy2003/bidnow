package com.bidnow.wallet.service.impl;

import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.domain.enums.WalletStatus;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.DepositLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositLockServiceImpl implements DepositLockService {

    private final WalletRepository walletRepository;
    private final DepositLockRepository depositLockRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public DepositLockStatusResponse getDepositLock(UUID userId, UUID auctionId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> walletNotFound(userId));
        return depositLockRepository.findByWalletIdAndAuctionId(wallet.getId(), auctionId)
                .map(lock -> DepositLockStatusResponse.builder()
                        .locked(lock.getStatus() == DepositLockStatus.LOCKED)
                        .amount(lock.getAmount())
                        .status(lock.getStatus().name())
                        .lockedAt(lock.getLockedAt())
                        .build())
                .orElseGet(() -> DepositLockStatusResponse.builder().locked(false).build());
    }

    @Override
    @Transactional
    public DepositLockResponse lockDeposit(DepositLockRequest request) {
        UUID userId = request.getUserId();
        UUID auctionId = request.getAuctionId();
        BigDecimal amount = request.getDepositAmount();

        // Row lock first: concurrent first bids for this user serialize here.
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> walletNotFound(userId));

        Optional<DepositLock> existing = depositLockRepository.findByWalletIdAndAuctionId(wallet.getId(), auctionId);
        if (existing.isPresent()) {
            DepositLock lock = existing.get();
            if (lock.getStatus() != DepositLockStatus.LOCKED) {
                throw new ConflictException("Deposit lock for auction " + auctionId + " is already "
                        + lock.getStatus(), WalletErrorCodes.DEPOSIT_LOCK_CLOSED);
            }
            log.debug("Deposit already locked for userId={}, auctionId={}", userId, auctionId);
            return toResponse(lock, wallet, true);
        }

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new ForbiddenException("Wallet is not active for userId: " + userId,
                    WalletErrorCodes.WALLET_NOT_ACTIVE);
        }
        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(wallet.getAvailableBalance(), amount);
        }

        if (amount.signum() > 0) {
            BigDecimal balanceBefore = wallet.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(amount);
            wallet.setAvailableBalance(balanceAfter);
            wallet.setLockedBalance(wallet.getLockedBalance().add(amount));
            walletRepository.save(wallet);

            transactionRepository.save(Transaction.builder()
                    .walletId(wallet.getId())
                    .type(TransactionType.HOLD)
                    .amount(amount)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Deposit lock for auction " + auctionId)
                    .build());
        }

        // saveAndFlush so a unique-constraint violation surfaces here, not at commit.
        DepositLock lock = depositLockRepository.saveAndFlush(DepositLock.builder()
                .walletId(wallet.getId())
                .auctionId(auctionId)
                .amount(amount)
                .status(DepositLockStatus.LOCKED)
                .lockedAt(LocalDateTime.now())
                .build());

        log.info("Deposit locked for userId={}, auctionId={}, amount={}", userId, auctionId, amount);
        return toResponse(lock, wallet, false);
    }

    private DepositLockResponse toResponse(DepositLock lock, Wallet wallet, boolean alreadyLocked) {
        return DepositLockResponse.builder()
                .lockId(lock.getId())
                .amount(lock.getAmount())
                .status(lock.getStatus().name())
                .alreadyLocked(alreadyLocked)
                .availableBalance(wallet.getAvailableBalance())
                .lockedBalance(wallet.getLockedBalance())
                .build();
    }

    private NotFoundException walletNotFound(UUID userId) {
        return new NotFoundException("Wallet not found for userId: " + userId, WalletErrorCodes.WALLET_NOT_FOUND);
    }
}
