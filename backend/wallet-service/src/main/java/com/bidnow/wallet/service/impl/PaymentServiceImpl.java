package com.bidnow.wallet.service.impl;

import com.bidnow.common.dto.event.PaymentEvent;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.wallet.constant.WalletErrorCodes;
import com.bidnow.wallet.domain.entity.AuctionCancellation;
import com.bidnow.wallet.domain.entity.DepositLock;
import com.bidnow.wallet.domain.entity.PaymentHold;
import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.entity.Wallet;
import com.bidnow.wallet.domain.enums.DepositLockStatus;
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.domain.enums.TransactionStatus;
import com.bidnow.wallet.domain.enums.TransactionType;
import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;
import com.bidnow.wallet.exception.ConflictException;
import com.bidnow.wallet.exception.InsufficientBalanceException;
import com.bidnow.wallet.kafka.PaymentApplicationEvent;
import com.bidnow.wallet.repository.AuctionCancellationRepository;
import com.bidnow.wallet.repository.DepositLockRepository;
import com.bidnow.wallet.repository.PaymentHoldRepository;
import com.bidnow.wallet.repository.TransactionRepository;
import com.bidnow.wallet.repository.WalletRepository;
import com.bidnow.wallet.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentHoldRepository paymentHoldRepository;
    private final WalletRepository walletRepository;
    private final DepositLockRepository depositLockRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionCancellationRepository auctionCancellationRepository;

    @Value("${wallet.payment.deadline-hours:48}")
    private long deadlineHours = 48;

    @Value("${wallet.platform-user-id}")
    private String platformUserId;

    @Override
    @Transactional
    public void createPaymentHold(UUID auctionId, UUID winnerUserId, UUID sellerUserId, BigDecimal totalAmount) {
        // Winner wallet row lock is the first read; duplicates serialize here.
        Wallet winner = walletRepository.findByUserIdForUpdate(winnerUserId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for userId: " + winnerUserId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
        if (paymentHoldRepository.existsByAuctionId(auctionId)) {
            log.debug("Payment hold already exists for auctionId={}", auctionId);
            return;
        }
        if (auctionCancellationRepository.existsById(auctionId)) {
            log.warn("Auction {} was already cancelled; no payment hold created", auctionId);
            return;
        }

        Optional<DepositLock> lock = depositLockRepository.findByWalletIdAndAuctionId(winner.getId(), auctionId);
        if (lock.isPresent() && lock.get().getStatus() != DepositLockStatus.LOCKED) {
            // On the end path the winner is excluded from the refund sweep, so a released or forfeited
            // winner deposit means the auction was cancelled first.
            log.warn("Winner deposit for auction {} is {}; treating auction as cancelled, no payment hold created",
                    auctionId, lock.get().getStatus());
            return;
        }
        if (lock.isEmpty()) {
            log.warn("No LOCKED deposit for winner walletId={}, auctionId={}; deposit applied = 0",
                    winner.getId(), auctionId);
        }
        BigDecimal deposit = lock.map(DepositLock::getAmount).orElse(BigDecimal.ZERO);
        BigDecimal applied = deposit.min(totalAmount);
        BigDecimal remaining = totalAmount.subtract(applied);

        boolean fundsHeld;
        if (remaining.signum() == 0) {
            fundsHeld = true;
        } else if (winner.getAvailableBalance().compareTo(remaining) >= 0) {
            BigDecimal balanceBefore = winner.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(remaining);
            winner.setAvailableBalance(balanceAfter);
            winner.setLockedBalance(winner.getLockedBalance().add(remaining));
            walletRepository.save(winner);
            transactionRepository.save(Transaction.builder()
                    .walletId(winner.getId())
                    .type(TransactionType.HOLD)
                    .amount(remaining)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Payment hold for auction " + auctionId)
                    .build());
            fundsHeld = true;
        } else {
            fundsHeld = false;
        }

        LocalDateTime deadline = LocalDateTime.now().plusHours(deadlineHours);
        paymentHoldRepository.saveAndFlush(PaymentHold.builder()
                .auctionId(auctionId)
                .winnerWalletId(winner.getId())
                .winnerUserId(winnerUserId)
                .sellerUserId(sellerUserId)
                .totalAmount(totalAmount)
                .depositApplied(applied)
                .depositLockId(lock.map(DepositLock::getId).orElse(null))
                .remainingAmount(remaining)
                .fundsHeld(fundsHeld)
                .status(PaymentHoldStatus.PENDING_PAYMENT)
                .deadline(deadline)
                .build());

        eventPublisher.publishEvent(new PaymentApplicationEvent(this, PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(winnerUserId)
                .sellerId(sellerUserId)
                .amount(totalAmount)
                .depositAmount(applied)
                .remaining(remaining)
                .deadline(deadline.atZone(ZoneId.systemDefault()).toInstant())
                .insufficientFunds(!fundsHeld)
                .paymentType("REQUIRED")
                .build()));

        log.info("Payment hold created for auctionId={}, winner={}, total={}, remaining={}, fundsHeld={}",
                auctionId, winnerUserId, totalAmount, remaining, fundsHeld);
    }

    @Override
    @Transactional
    public ConfirmPaymentResponse confirmPayment(UUID callerUserId, UUID auctionId) {
        // Lock order: hold row first, then both wallets in ascending id order.
        PaymentHold hold = paymentHoldRepository.findByAuctionIdForUpdate(auctionId)
                .filter(h -> h.getWinnerUserId().equals(callerUserId))
                .orElseThrow(() -> new NotFoundException("No payment hold for auction " + auctionId,
                        WalletErrorCodes.PAYMENT_HOLD_NOT_FOUND));
        if (hold.getStatus() != PaymentHoldStatus.PENDING_PAYMENT) {
            throw new ConflictException("Payment for auction " + auctionId + " is " + hold.getStatus(),
                    WalletErrorCodes.PAYMENT_NOT_PENDING);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(hold.getDeadline())) {
            throw new BadRequestException("Payment deadline for auction " + auctionId + " has passed",
                    WalletErrorCodes.PAYMENT_DEADLINE_EXPIRED);
        }

        UUID sellerWalletId = walletRepository.findIdByUserId(hold.getSellerUserId())
                .orElseThrow(() -> new NotFoundException("Seller wallet not found for userId: " + hold.getSellerUserId(),
                        WalletErrorCodes.SELLER_WALLET_NOT_FOUND));
        Wallet winner;
        Wallet seller;
        if (hold.getWinnerWalletId().compareTo(sellerWalletId) < 0) {
            winner = lockWallet(hold.getWinnerWalletId());
            seller = lockWallet(sellerWalletId);
        } else {
            seller = lockWallet(sellerWalletId);
            winner = lockWallet(hold.getWinnerWalletId());
        }

        BigDecimal total = hold.getTotalAmount();
        BigDecimal applied = hold.getDepositApplied();
        BigDecimal remaining = hold.getRemainingAmount();

        if (!hold.isFundsHeld() && winner.getAvailableBalance().compareTo(remaining) < 0) {
            throw new InsufficientBalanceException(winner.getAvailableBalance(), remaining);
        }
        DepositLock lock = null;
        if (hold.getDepositLockId() != null) {
            lock = depositLockRepository.findById(hold.getDepositLockId())
                    .filter(l -> l.getStatus() == DepositLockStatus.LOCKED)
                    .orElseThrow(() -> new ConflictException("Deposit for auction " + auctionId + " is no longer locked",
                            WalletErrorCodes.DEPOSIT_LOCK_CLOSED));
        }

        // Winner: pay the remaining part.
        BigDecimal winnerBefore = winner.getAvailableBalance();
        if (hold.isFundsHeld()) {
            winner.setLockedBalance(winner.getLockedBalance().subtract(remaining));
        } else {
            winner.setAvailableBalance(winner.getAvailableBalance().subtract(remaining));
        }
        winner.setTotalBalance(winner.getTotalBalance().subtract(remaining));

        // Winner: consume the deposit; any excess over the price goes back to available.
        BigDecimal excess = BigDecimal.ZERO;
        if (lock != null) {
            excess = lock.getAmount().subtract(applied);
            if (excess.signum() < 0) {
                throw new IllegalStateException("Deposit lock " + lock.getId() + " amount " + lock.getAmount()
                        + " is less than deposit applied " + applied);
            }
            winner.setLockedBalance(winner.getLockedBalance().subtract(lock.getAmount()));
            winner.setTotalBalance(winner.getTotalBalance().subtract(applied));
            lock.setStatus(DepositLockStatus.RELEASED);
            lock.setReleasedAt(now);
            depositLockRepository.save(lock);
        }
        BigDecimal winnerAfterPayment = winner.getAvailableBalance();
        transactionRepository.save(Transaction.builder()
                .walletId(winner.getId())
                .type(TransactionType.PAYMENT)
                .amount(total)
                .availableBalanceBefore(winnerBefore)
                .availableBalanceAfter(winnerAfterPayment)
                .referenceId(auctionId)
                .status(TransactionStatus.COMPLETED)
                .description("Payment for auction " + auctionId)
                .build());
        if (excess.signum() > 0) {
            winner.setAvailableBalance(winnerAfterPayment.add(excess));
            transactionRepository.save(Transaction.builder()
                    .walletId(winner.getId())
                    .type(TransactionType.REFUND)
                    .amount(excess)
                    .availableBalanceBefore(winnerAfterPayment)
                    .availableBalanceAfter(winner.getAvailableBalance())
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Deposit excess refund for auction " + auctionId)
                    .build());
        }
        walletRepository.save(winner);

        // Seller: credit exactly the winning bid.
        BigDecimal sellerBefore = seller.getAvailableBalance();
        seller.setAvailableBalance(sellerBefore.add(total));
        seller.setTotalBalance(seller.getTotalBalance().add(total));
        walletRepository.save(seller);
        transactionRepository.save(Transaction.builder()
                .walletId(seller.getId())
                .type(TransactionType.PAYMENT)
                .amount(total)
                .availableBalanceBefore(sellerBefore)
                .availableBalanceAfter(seller.getAvailableBalance())
                .referenceId(auctionId)
                .status(TransactionStatus.COMPLETED)
                .description("Sale proceeds for auction " + auctionId)
                .build());

        hold.setStatus(PaymentHoldStatus.COMPLETED);
        hold.setCompletedAt(now);
        paymentHoldRepository.save(hold);

        eventPublisher.publishEvent(new PaymentApplicationEvent(this, PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(hold.getWinnerUserId())
                .sellerId(hold.getSellerUserId())
                .amount(total)
                .depositAmount(applied)
                .remaining(remaining)
                .paymentType("COMPLETED")
                .build()));

        log.info("Payment confirmed for auctionId={}, winner={}, seller={}, amount={}",
                auctionId, hold.getWinnerUserId(), hold.getSellerUserId(), total);

        return ConfirmPaymentResponse.builder()
                .auctionId(auctionId)
                .amountPaid(total)
                .depositApplied(applied)
                .remainingPaid(remaining)
                .availableBalance(winner.getAvailableBalance())
                .lockedBalance(winner.getLockedBalance())
                .build();
    }

    @Override
    @Transactional
    public void forfeitExpiredHold(UUID auctionId) {
        // Lock order: hold row (skipped if another instance holds it), then wallets in ascending id order.
        Optional<PaymentHold> maybeHold = paymentHoldRepository.findByAuctionIdForUpdateSkipLocked(auctionId);
        if (maybeHold.isEmpty()) {
            return;
        }
        PaymentHold hold = maybeHold.get();
        LocalDateTime now = LocalDateTime.now();
        if (hold.getStatus() != PaymentHoldStatus.PENDING_PAYMENT || !now.isAfter(hold.getDeadline())) {
            return;
        }

        UUID platformWalletId = walletRepository.findIdByUserId(UUID.fromString(platformUserId))
                .orElseThrow(() -> new NotFoundException("Platform wallet not found for userId: " + platformUserId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
        Wallet winner;
        Wallet platform;
        if (hold.getWinnerWalletId().compareTo(platformWalletId) < 0) {
            winner = lockWallet(hold.getWinnerWalletId());
            platform = lockWallet(platformWalletId);
        } else {
            platform = lockWallet(platformWalletId);
            winner = lockWallet(hold.getWinnerWalletId());
        }

        boolean winnerChanged = false;
        BigDecimal remaining = hold.getRemainingAmount();
        if (hold.isFundsHeld() && remaining.signum() > 0) {
            BigDecimal balanceBefore = winner.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.add(remaining);
            winner.setAvailableBalance(balanceAfter);
            winner.setLockedBalance(winner.getLockedBalance().subtract(remaining));
            transactionRepository.save(Transaction.builder()
                    .walletId(winner.getId())
                    .type(TransactionType.HOLD_CANCEL)
                    .amount(remaining)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Payment hold released on forfeit for auction " + auctionId)
                    .build());
            winnerChanged = true;
        }

        BigDecimal forfeited = BigDecimal.ZERO;
        if (hold.getDepositLockId() != null) {
            Optional<DepositLock> maybeLock = depositLockRepository.findById(hold.getDepositLockId())
                    .filter(l -> l.getStatus() == DepositLockStatus.LOCKED);
            if (maybeLock.isPresent()) {
                DepositLock lock = maybeLock.get();
                if (!winner.getId().equals(lock.getWalletId())) {
                    throw new IllegalStateException("Deposit lock " + lock.getId() + " belongs to wallet "
                            + lock.getWalletId() + ", not winner wallet " + winner.getId());
                }
                forfeited = lock.getAmount();
                if (forfeited.signum() > 0) {
                    winner.setLockedBalance(winner.getLockedBalance().subtract(forfeited));
                    winner.setTotalBalance(winner.getTotalBalance().subtract(forfeited));
                    transactionRepository.save(Transaction.builder()
                            .walletId(winner.getId())
                            .type(TransactionType.FORFEIT)
                            .amount(forfeited)
                            .availableBalanceBefore(winner.getAvailableBalance())
                            .availableBalanceAfter(winner.getAvailableBalance())
                            .referenceId(auctionId)
                            .status(TransactionStatus.COMPLETED)
                            .description("Deposit forfeited for auction " + auctionId)
                            .build());
                    BigDecimal platformBefore = platform.getAvailableBalance();
                    platform.setAvailableBalance(platformBefore.add(forfeited));
                    platform.setTotalBalance(platform.getTotalBalance().add(forfeited));
                    walletRepository.save(platform);
                    transactionRepository.save(Transaction.builder()
                            .walletId(platform.getId())
                            .type(TransactionType.FORFEIT)
                            .amount(forfeited)
                            .availableBalanceBefore(platformBefore)
                            .availableBalanceAfter(platform.getAvailableBalance())
                            .referenceId(auctionId)
                            .status(TransactionStatus.COMPLETED)
                            .description("Forfeited deposit from auction " + auctionId)
                            .build());
                    winnerChanged = true;
                }
                lock.setStatus(DepositLockStatus.FORFEITED);
                lock.setReleasedAt(now);
                depositLockRepository.save(lock);
            }
        }
        if (winnerChanged) {
            walletRepository.save(winner);
        }

        hold.setStatus(PaymentHoldStatus.FORFEITED);
        hold.setCompletedAt(now);
        paymentHoldRepository.save(hold);

        eventPublisher.publishEvent(new PaymentApplicationEvent(this, PaymentEvent.builder()
                .auctionId(auctionId)
                .userId(hold.getWinnerUserId())
                .sellerId(hold.getSellerUserId())
                .amount(hold.getTotalAmount())
                .depositAmount(forfeited)
                .remaining(remaining)
                .paymentType("FAILED")
                .build()));

        log.info("Payment hold forfeited for auctionId={}, winner={}, forfeited={}",
                auctionId, hold.getWinnerUserId(), forfeited);
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet not found: " + walletId,
                        WalletErrorCodes.WALLET_NOT_FOUND));
    }

    @Override
    @Transactional
    public void cancelPaymentHold(UUID auctionId) {
        if (!auctionCancellationRepository.existsById(auctionId)) {
            auctionCancellationRepository.save(AuctionCancellation.builder()
                    .auctionId(auctionId)
                    .cancelledAt(LocalDateTime.now())
                    .build());
        }

        // Lock order: hold row, then wallet row.
        Optional<PaymentHold> maybeHold = paymentHoldRepository.findByAuctionIdForUpdate(auctionId);
        if (maybeHold.isEmpty()) {
            return;
        }
        if (maybeHold.get().getStatus() == PaymentHoldStatus.COMPLETED) {
            log.error("Auction {} cancelled after its payment was COMPLETED; manual reconciliation required", auctionId);
            return;
        }
        if (maybeHold.get().getStatus() == PaymentHoldStatus.FORFEITED) {
            log.warn("Auction {} cancelled after its payment hold was FORFEITED; deposit stays with the platform", auctionId);
            return;
        }
        if (maybeHold.get().getStatus() != PaymentHoldStatus.PENDING_PAYMENT) {
            return;
        }
        PaymentHold hold = maybeHold.get();

        if (hold.isFundsHeld() && hold.getRemainingAmount().signum() > 0) {
            Wallet winner = walletRepository.findByIdForUpdate(hold.getWinnerWalletId())
                    .orElseThrow(() -> new NotFoundException("Wallet not found: " + hold.getWinnerWalletId(),
                            WalletErrorCodes.WALLET_NOT_FOUND));
            BigDecimal remaining = hold.getRemainingAmount();
            BigDecimal balanceBefore = winner.getAvailableBalance();
            BigDecimal balanceAfter = balanceBefore.add(remaining);
            winner.setAvailableBalance(balanceAfter);
            winner.setLockedBalance(winner.getLockedBalance().subtract(remaining));
            walletRepository.save(winner);
            transactionRepository.save(Transaction.builder()
                    .walletId(winner.getId())
                    .type(TransactionType.HOLD_CANCEL)
                    .amount(remaining)
                    .availableBalanceBefore(balanceBefore)
                    .availableBalanceAfter(balanceAfter)
                    .referenceId(auctionId)
                    .status(TransactionStatus.COMPLETED)
                    .description("Payment hold cancelled for auction " + auctionId)
                    .build());
        }

        hold.setStatus(PaymentHoldStatus.CANCELLED);
        paymentHoldRepository.save(hold);
        log.info("Payment hold cancelled for auctionId={}", auctionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingPaymentResponse> getPendingPayments(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        return paymentHoldRepository
                .findByWinnerUserIdAndStatusOrderByDeadlineAsc(userId, PaymentHoldStatus.PENDING_PAYMENT)
                .stream()
                .map(h -> PendingPaymentResponse.builder()
                        .auctionId(h.getAuctionId())
                        .totalAmount(h.getTotalAmount())
                        .depositApplied(h.getDepositApplied())
                        .remaining(h.getRemainingAmount())
                        .fundsHeld(h.isFundsHeld())
                        .deadline(h.getDeadline())
                        .hoursLeft(Math.max(0, Duration.between(now, h.getDeadline()).toHours()))
                        .expired(now.isAfter(h.getDeadline()))
                        .build())
                .toList();
    }
}
