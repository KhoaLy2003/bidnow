package com.bidnow.media.notification.batch;

/** What to do with a bid alert: send it now, leave it to the window flush, or ignore a redelivery. */
public enum BidBatchOutcome {
    IMMEDIATE,
    BATCHED,
    DUPLICATE
}
