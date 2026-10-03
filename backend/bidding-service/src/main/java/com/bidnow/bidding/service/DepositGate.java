package com.bidnow.bidding.service;

import com.bidnow.bidding.constant.BiddingErrorCodes;
import com.bidnow.bidding.dto.BidContext;
import com.bidnow.bidding.dto.DepositLockCommand;
import com.bidnow.bidding.exception.ConflictException;
import com.bidnow.bidding.exception.InsufficientBalanceException;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.DownstreamError;
import com.bidnow.bidding.feign.WalletServiceClient;
import com.bidnow.common.exception.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Ensures the bidder's auction deposit is locked before a bid is applied (implicit registration on
 * the first bid). A Redis flag skips the wallet round-trip on later bids; without it the idempotent
 * wallet lock is simply called again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositGate {

    private static final Duration FLAG_RETENTION_AFTER_END = Duration.ofHours(24);
    private static final Duration MIN_FLAG_TTL = Duration.ofMinutes(1);

    private final WalletServiceClient walletServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    static String flagKey(UUID auctionId, UUID userId) {
        return "bidding:deposit:" + auctionId + ":" + userId;
    }

    public void ensureLocked(UUID userId, BidContext ctx) {
        String key = flagKey(ctx.getAuctionId(), userId);
        if (flagPresent(key)) {
            return;
        }
        try {
            walletServiceClient.lockDeposit(new DepositLockCommand(userId, ctx.getAuctionId(), ctx.getDepositAmount()));
        } catch (FeignException ex) {
            throw toBiddingException(DownstreamError.of(ex, objectMapper), userId, ctx.getAuctionId());
        }
        setFlag(key, flagTtl(ctx));
    }

    private RuntimeException toBiddingException(DownstreamError error, UUID userId, UUID auctionId) {
        String code = error.errorCode() == null ? "" : error.errorCode();
        return switch (code) {
            case "INSUFFICIENT_BALANCE" -> new InsufficientBalanceException(error.errors());
            case "WALLET_NOT_ACTIVE" -> new ForbiddenException("Your wallet is not active", BiddingErrorCodes.WALLET_NOT_ACTIVE);
            case "WALLET_NOT_FOUND" -> new ForbiddenException("No wallet found for this account", BiddingErrorCodes.WALLET_NOT_FOUND);
            case "DEPOSIT_LOCK_CLOSED" -> new ConflictException(
                    "Your deposit for this auction is no longer active", BiddingErrorCodes.DEPOSIT_LOCK_CLOSED);
            default -> {
                log.error("wallet-service deposit lock failed for user {} on auction {} (status {}, code {})",
                        userId, auctionId, error.status(), error.errorCode());
                yield new ServiceUnavailableException("Wallet service is unavailable");
            }
        };
    }

    private Duration flagTtl(BidContext ctx) {
        Duration ttl = Duration.between(clock.instant(), ctx.getEndTime().toInstant().plus(FLAG_RETENTION_AFTER_END));
        return ttl.compareTo(MIN_FLAG_TTL) < 0 ? MIN_FLAG_TTL : ttl;
    }

    private boolean flagPresent(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RuntimeException ex) {
            log.warn("Redis read failed for deposit flag {} - calling wallet-service: {}", key, ex.getMessage());
            return false;
        }
    }

    private void setFlag(String key, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("Failed to set deposit flag {}: {}", key, ex.getMessage());
        }
    }
}
