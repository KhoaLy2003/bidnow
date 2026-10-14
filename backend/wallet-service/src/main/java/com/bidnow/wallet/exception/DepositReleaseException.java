package com.bidnow.wallet.exception;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** Thrown after a refund sweep in which some locks failed, so the Kafka error handler redelivers the record. */
@Getter
public class DepositReleaseException extends RuntimeException {

    private final UUID auctionId;
    private final List<UUID> failedLockIds;

    public DepositReleaseException(UUID auctionId, List<UUID> failedLockIds) {
        super("Failed to release " + failedLockIds.size() + " deposit lock(s) for auction "
                + auctionId + ": " + failedLockIds);
        this.auctionId = auctionId;
        this.failedLockIds = List.copyOf(failedLockIds);
    }
}
